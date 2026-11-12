package dev.elsewhere.boss.vorlagh;

import dev.elsewhere.overworld_bosses.Geo;
import dev.elsewhere.overworld_bosses.OwbEntities;
import dev.elsewhere.overworld_bosses.OwbItems;
import dev.elsewhere.overworld_bosses.OwbSounds;
import dev.elsewhere.stub.overworld_bosses.BossArena;
import dev.elsewhere.stub.overworld_bosses.BossAttack;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import dev.elsewhere.stub.overworld_bosses.SignatureGear;
import dev.elsewhere.stub.overworld_bosses.Telegraph;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vorlagh, the Tidewrack Kraken (BOSSES.md #5, Tier III). A kraken in the open water at the centre of the Wreck
 * Ring with five independent tentacles (separate {@link VorlaghTentacle} entities, each with its own hitbox and
 * 60 HP). The body is invulnerable in phase 1 until 3 tentacles are severed. Slam lines break the deck, grabs drag a
 * player to the beak; phase 2 adds ink clouds, a whirlpool and boarding Tidewrack Drowned; phase 3 telegraphed
 * lightning and tentacle regrowth every 40 s (bal-vorlagh-regrow). Channeling (lightning, channeling tridents) deals x2.
 */
public class VorlaghEntity extends ElsewhereBoss {
	public static final String ID = "vorlagh";
	public static final int TENTACLES = 5;
	public static final int ACT_INK = 1, ACT_ROAR = 2, ACT_BITE = 3;

	private final VorlaghTentacle[] tentacles = new VorlaghTentacle[TENTACLES];
	private final ServerBossEvent tentacleBar;
	private int regrowTimer;
	private final List<Vec3> strikeSpots = new ArrayList<>();

	public VorlaghEntity(EntityType<? extends VorlaghEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.tentacleBar = new ServerBossEvent(Mth.createInsecureUUID(this.random), Component.translatable("bar.elsewhere.vorlagh.tentacles", 5, 5),
			BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 520.0)
			.add(Attributes.ARMOR, 14.0)
			.add(Attributes.ARMOR_TOUGHNESS, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ATTACK_DAMAGE, 14.0);
	}

	@Override
	public String bossId() {
		return ID;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.6F, 0.3F};
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	protected @Nullable SoundEvent roarSound() {
		return OwbSounds.VORLAGH_GROAN;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	// ---- tentacles -------------------------------------------------------------------------------------------------

	public VorlaghTentacle[] tentacles() {
		return this.tentacles;
	}

	public int severedCount() {
		int n = 0;
		for (VorlaghTentacle t : this.tentacles) if (t == null || !t.isAlive() || t.isSevered()) n++;
		return n;
	}

	private void ensureTentacles(ServerLevel level) {
		for (int i = 0; i < TENTACLES; i++) {
			if (this.tentacles[i] != null && this.tentacles[i].isAlive()) continue;
			VorlaghTentacle t = OwbEntities.VORLAGH_TENTACLE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (t == null) continue;
			t.bind(this, i);
			t.setPos(anchor(i));
			addMinion(level, t);
			this.tentacles[i] = t;
		}
	}

	/** Where tentacle i rises: on the inner edge of the deck ring, around the body. */
	public Vec3 anchor(int i) {
		double a = Math.PI * 2 * i / TENTACLES + Math.PI / 2;
		BlockPos c = this.home != null ? this.home : blockPosition();
		double deckY = this.arena != null ? this.arena.floorY : c.getY() + 3;
		return new Vec3(c.getX() + 0.5 + Math.cos(a) * 6.5, deckY - 1.5, c.getZ() + 0.5 + Math.sin(a) * 6.5);
	}

	public @Nullable VorlaghTentacle readyTentacle(@Nullable LivingEntity near) {
		List<VorlaghTentacle> ready = new ArrayList<>();
		for (VorlaghTentacle t : this.tentacles) if (t != null && t.isAlive() && !t.isSevered() && t.isIdle()) ready.add(t);
		if (ready.isEmpty()) return null;
		if (near != null) ready.sort((x, y) -> Double.compare(x.distanceToSqr(near), y.distanceToSqr(near)));
		return ready.get(near != null ? 0 : this.random.nextInt(ready.size()));
	}

	// ---- attacks -------------------------------------------------------------------------------------------------

	@Override
	protected void registerAttacks() {
		this.attacks.add(BossAttack.<VorlaghEntity>of(ID, "tentacle_slam")
			.cooldown(50).windup(0).active(0).silent().weight(3)
			.when(b -> b.getTarget() != null && b.readyTentacle(b.getTarget()) != null)
			.onFire(b -> {
				VorlaghTentacle t = b.readyTentacle(b.getTarget());
				if (t != null) t.startSlam(b.getTarget(), b.num("tentacle_slam", "damage", 44.0F));
			}));
		this.attacks.add(BossAttack.<VorlaghEntity>of(ID, "grab")
			.cooldown(220).windup(0).active(0).silent()
			.when(b -> b.getTarget() instanceof ServerPlayer && b.readyTentacle(b.getTarget()) != null && b.noGrab())
			.onFire(b -> {
				VorlaghTentacle t = b.readyTentacle(b.getTarget());
				if (t != null) t.startGrab((ServerPlayer) b.getTarget());
			}));
		this.attacks.add(BossAttack.<VorlaghEntity>of(ID, "ink_cloud")
			.cooldown(160).windup(14).active(0).phases(2, 3)
			.when(b -> b.getTarget() != null)
			.onStart(b -> b.setAction(ACT_INK))
			.onFire(b -> {
				if (!(b.level() instanceof ServerLevel sl) || b.getTarget() == null) return;
				InkGlob g = new InkGlob(OwbEntities.INK_GLOB, sl);
				g.setOwner(b);
				Vec3 from = b.position().add(0, 4.0, 0).add(Geo.flatDir(b.position(), b.getTarget().position()).scale(3));
				g.setPos(from);
				Vec3 d = b.getTarget().position().subtract(from);
				g.shoot(d.x, d.y + d.horizontalDistance() * 0.15, d.z, 1.2F, 1.0F);
				g.addTag(BossDamage.MINION_TAG);
				sl.addFreshEntity(g);
				b.playSound(SoundEvents.SQUID_SQUIRT, 3.0F, 0.5F);
			}));
		this.attacks.add(BossAttack.<VorlaghEntity>of(ID, "whirlpool")
			.cooldown(500).windup(30).active(100).phases(2, 3)
			.when(b -> b.arena != null)
			.onStart(b -> b.setAction(ACT_ROAR))
			.onActive((b, t) -> b.tickWhirlpool()));
		this.attacks.add(BossAttack.<VorlaghEntity>of(ID, "boarders")
			.cooldown(400).windup(20).active(0).phases(2, 3)
			.when(b -> b.liveMinions() - TENTACLES < b.minionCap(3))
			.onFire(VorlaghEntity::spawnBoarders));
		this.attacks.add(BossAttack.<VorlaghEntity>of(ID, "lightning")
			.cooldown(100).windup(30).active(0).phases(3)
			.when(b -> b.getTarget() != null)
			.onStart(b -> {
				b.strikeSpots.clear();
				if (b.level() instanceof ServerLevel sl) {
					for (ServerPlayer p : b.playersInArena(sl)) b.strikeSpots.add(p.position());
					for (int i = 0; i < 2; i++) {
						double a = b.random.nextDouble() * Math.PI * 2, r = 7 + b.random.nextDouble() * 9;
						BlockPos c = b.home != null ? b.home : b.blockPosition();
						b.strikeSpots.add(new Vec3(c.getX() + Math.cos(a) * r, b.arena != null ? b.arena.floorY + 1 : b.getY(), c.getZ() + Math.sin(a) * r));
					}
				}
			})
			.onWindup((b, t) -> {
				if (t % 3 == 0 && b.level() instanceof ServerLevel sl) {
					for (Vec3 s : b.strikeSpots) {
						sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, s.y + 0.2, s.z, 8, 0.6, 0.1, 0.6, 0.05);
						Telegraph.ring(sl, s.add(0, 0.1, 0), 1.5, Telegraph.YELLOW);
					}
				}
			})
			.onFire(b -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				for (Vec3 s : b.strikeSpots) {
					LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(sl, EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setPos(s);
						bolt.setVisualOnly(true);
						sl.addFreshEntity(bolt);
					}
					for (ServerPlayer p : b.playersInArena(sl)) {
						if (p.position().distanceToSqr(s) <= 2.25) {
							BossDamage.hit(b, p, 5.0F, BossRegistry.BOSS_MAGIC, false);
							p.igniteForSeconds(3.0F);
						}
					}
				}
			}));
	}

	public boolean noGrab() {
		for (VorlaghTentacle t : this.tentacles) if (t != null && t.isGrabbing()) return false;
		return true;
	}

	private void tickWhirlpool() {
		if (!(level() instanceof ServerLevel sl) || this.arena == null) return;
		Vec3 c = this.arena.centerVec();
		for (ServerPlayer p : sl.players()) {
			if (!p.isInWater() || p.position().distanceToSqr(c) > 30 * 30) continue;
			Vec3 pull = new Vec3(c.x - p.getX(), 0, c.z - p.getZ()).normalize().scale(0.08);
			Vec3 swirl = new Vec3(-pull.z, 0, pull.x).scale(0.8);
			Geo.push(p, p.getDeltaMovement().add(pull).add(swirl));
		}
		if (this.tickCount % 4 == 0) {
			for (int i = 0; i < 12; i++) {
				double a = this.tickCount * 0.3 + i * Math.PI / 6, r = 8 + (i % 4) * 3;
				sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x + Math.cos(a) * r, this.arena.floorY - 0.5, c.z + Math.sin(a) * r, 1, 0.3, 0.1, 0.3, 0.05); // P13: 1 per stream, was 2 (60/s, was 120)
			}
		}
	}

	private void spawnBoarders() {
		if (!(level() instanceof ServerLevel sl) || this.arena == null) return;
		for (int i = 0; i < 2; i++) {
			TidewrackDrowned d = OwbEntities.TIDEWRACK_DROWNED.create(sl, EntitySpawnReason.MOB_SUMMONED);
			if (d == null) continue;
			double a = this.random.nextDouble() * Math.PI * 2;
			BlockPos c = this.arena.center;
			d.setPos(c.getX() + 0.5 + Math.cos(a) * 16, this.arena.floorY + 1, c.getZ() + 0.5 + Math.sin(a) * 16);
			d.finalizeSpawn(sl, sl.getCurrentDifficultyAt(c), EntitySpawnReason.MOB_SUMMONED, null);
			addMinion(sl, d);
		}
	}

	// ---- per tick, phases, damage -----------------------------------------------------------------------------------

	@Override
	protected void bossTick(ServerLevel level) {
		ensureTentaclesPresent(level);
		if (this.home != null) {
			Vec3 h = Vec3.atBottomCenterOf(this.home);
			if (distanceToSqr(h) > 0.25) setPos(h);
			setDeltaMovement(Vec3.ZERO);
		}
		if (getTarget() != null) getLookControl().setLookAt(getTarget(), 10, 10);
		if (getPhase() >= 3 && ++this.regrowTimer >= 800) {
			this.regrowTimer = 0;
			for (VorlaghTentacle t : this.tentacles) {
				if (t != null && t.isAlive() && t.isSevered()) {
					t.regrow();
					level.sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + 3, t.getZ(), 30, 0.5, 2, 0.5, 0.05);
					break;
				}
			}
		}
		if (this.tickCount % 10 == 0) updateTentacleBar(level);
	}

	private void ensureTentaclesPresent(ServerLevel level) {
		boolean missing = false;
		for (VorlaghTentacle t : this.tentacles) if (t == null || !t.isAlive()) missing = true;
		if (missing) ensureTentacles(level);
	}

	private void updateTentacleBar(ServerLevel level) {
		float hp = 0, max = 0;
		int standing = 0;
		for (VorlaghTentacle t : this.tentacles) {
			if (t == null) continue;
			max += t.getMaxHealth();
			if (!t.isSevered()) {
				hp += t.getHealth();
				standing++;
			}
		}
		this.tentacleBar.setProgress(max > 0 ? hp / max : 0);
		this.tentacleBar.setName(Component.translatable("bar.elsewhere.vorlagh.tentacles", standing, TENTACLES));
		for (ServerPlayer p : this.bossEvent.getPlayers()) if (!this.tentacleBar.getPlayers().contains(p)) this.tentacleBar.addPlayer(p);
		for (ServerPlayer p : List.copyOf(this.tentacleBar.getPlayers())) if (!this.bossEvent.getPlayers().contains(p)) this.tentacleBar.removePlayer(p);
	}

	/** Phase 1: the body is invulnerable until 3 tentacles are severed. */
	@Override
	protected boolean isDamageGated(ServerLevel level, DamageSource source) {
		return getPhase() == 1 && severedCount() < 3;
	}

	/** Channeling deals x2: lightning, or a trident enchanted with Channeling. */
	@Override
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT)) return amount * 2.0F;
		if (source.getDirectEntity() instanceof ThrownTrident trident) {
			var ch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING);
			if (EnchantmentHelper.getItemEnchantmentLevel(ch, trident.getWeaponItem()) > 0) return amount * 2.0F;
		}
		return amount;
	}

	@Override
	protected void onFightStart(ServerLevel level) {
		this.regrowTimer = 0;
		ensureTentacles(level);
		for (VorlaghTentacle t : this.tentacles) if (t != null) t.regrow();
	}

	@Override
	protected void onFightReset(ServerLevel level) {
		for (VorlaghTentacle t : this.tentacles) if (t != null) t.releaseGrab();
		this.tentacleBar.removeAllPlayers();
	}

	@Override
	protected void onFightWon(ServerLevel level) {
		for (VorlaghTentacle t : this.tentacles) if (t != null) t.releaseGrab();
		this.tentacleBar.removeAllPlayers();
		level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 4, getZ(), 200, 3, 3, 3, 0.1);
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		this.tentacleBar.removeAllPlayers();
	}

	@Override
	protected BossArena defaultArena() {
		BlockPos p = blockPosition();
		return new BossArena(p, 20, 14, 16, p.getY() + 2, 5);
	}

	@Override
	protected List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("undertow", () -> new ItemStack(OwbItems.UNDERTOW)),
			dev.elsewhere.boss.kit.DropRule.other("kraken_ink_bomb", () -> new ItemStack(OwbItems.KRAKEN_INK_BOMB)),
			dev.elsewhere.boss.kit.DropRule.other("abyssal_eye", () -> new ItemStack(OwbItems.ABYSSAL_EYE)))));
		if (FabricLoader.getInstance().isModLoaded("testmeter")) out.add(new ItemStack(OwbItems.KRAKEN_CALAMARI, 2));
		return out;
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(OwbItems.SIREN_CONCH);
	}

	@Override
	protected @Nullable Item tierSignature() {
		return SignatureGear.PHASEWALKER_GREAVES;
	}
}
