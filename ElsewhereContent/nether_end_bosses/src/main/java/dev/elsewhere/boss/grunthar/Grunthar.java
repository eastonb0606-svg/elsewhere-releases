package dev.elsewhere.boss.grunthar;

import dev.elsewhere.boss.kit.BossRewards;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.NetherEndSounds;
import dev.elsewhere.stub.nether_end_bosses.AttackSpec;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import dev.elsewhere.stub.nether_end_bosses.BossFight;
import dev.elsewhere.stub.nether_end_bosses.Decals;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.LinkedBodies;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Grunthar the Gilded Tyrant (170 base HP, armor 14) riding Ashtusk. Two linked entities share this phase machine:
 * 1 Mounted (Ashtusk alive), 2 Dismounted (Ashtusk dead or both under 50%: berserk, Enforcers, Greed stuns),
 * 3 Molten Hoard (Grunthar under 30%: lava drips mark spots for 1.5 s, then lava falls for 5 s, magma patches).
 */
public class Grunthar extends ElsewhereBoss implements LinkedBodies {
	public static final int FLAG_THROWING = FLAG_CUSTOM;
	public static final int FLAG_RAISED_AXE = FLAG_CUSTOM << 1;
	public static final int FLAG_GREED = FLAG_CUSTOM << 2;
	public static final int MAX_GREED = 3;

	private @Nullable UUID mountId;
	private @Nullable ServerBossEvent mountBar;
	private int greedCount;
	private @Nullable ItemEntity greedTarget;
	private final List<Vec3> lavaSpots = new ArrayList<>();

	public Grunthar(EntityType<? extends Grunthar> type, Level level) {
		super(type, level);
		this.xpReward = 150;
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		this.setDropChance(EquipmentSlot.MAINHAND, 0);
		this.setDropChance(EquipmentSlot.HEAD, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 170.0)
			.add(Attributes.ARMOR, 14.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 10.0)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	public void setMount(Ashtusk mount) {
		this.mountId = mount.getUUID();
	}

	public @Nullable Ashtusk mount() {
		if (this.getVehicle() instanceof Ashtusk a) return a;
		if (mountId != null && this.level() instanceof ServerLevel level && level.getEntity(mountId) instanceof Ashtusk a && a.isAlive()) return a;
		return null;
	}

	@Override
	public List<ElsewhereBoss> linkedBodies() {
		Ashtusk a = mount();
		return a == null ? List.of() : List.of(a);
	}

	@Override
	public String bossId() {
		return "grunthar";
	}

	@Override
	public int tier() {
		return 1;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	@Override
	public BossEvent.BossBarOverlay barOverlay() {
		return BossEvent.BossBarOverlay.NOTCHED_6;
	}

	@Override
	public Holder<SoundEvent> music() {
		return NetherEndSounds.MUSIC_GRUNTHAR;
	}

	@Override
	protected int computePhase() {
		float ratio = this.getHealth() / this.getMaxHealth();
		Ashtusk a = mount();
		int phase;
		if (ratio < 0.30F) phase = 3;
		else if (a == null || !a.isAlive() || ratio < 0.5F && a.getHealth() / a.getMaxHealth() < 0.5F) phase = 2;
		else phase = 1;
		return Math.max(phase, getPhase());
	}

	@Override
	protected List<AttackSpec> defaultAttacks() {
		return List.of(
			new AttackSpec("gold_bombs", "Gold Bombs", 14, 90, AttackSpec.phases(1, 2, 3), 4, 34.0F),
			new AttackSpec("axe_cleave", "Golden Axe", 16, 40, AttackSpec.phases(2, 3), 5, 28.0F),
			new AttackSpec("call_enforcers", "Call the Enforcers", 30, 500, AttackSpec.phases(2), 3, 0.0F),
			new AttackSpec("molten_hoard", "Molten Hoard", 30, 260, AttackSpec.phases(3), 4, 8.0F),
			new AttackSpec("greed", "Greed!", 0, 0, 0, 0, 0.0F));
	}

	@Override
	protected boolean canStart(AttackSpec spec) {
		ServerPlayer t = nearestTarget();
		if (t == null) return false;
		return switch (spec.id()) {
			case "axe_cleave" -> !this.isPassenger() && t.distanceTo(this) < 4.0;
			case "call_enforcers" -> fight() != null && fight().minionCount((ServerLevel) this.level()) < fight().minionCap(2);
			default -> true;
		};
	}

	// ---- per-tick behaviour ----

	@Override
	protected void bossTick(ServerLevel level) {
		BossFight fight = fight();
		if (fight != null) {
			Ashtusk a = mount();
			if (mountBar == null && a != null) mountBar = fight.addBar(a.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
			if (mountBar != null) mountBar.setProgress(a == null ? 0 : Math.max(0, a.getHealth() / a.getMaxHealth()));
		}
		if (getPhase() >= 2 && current == null && greedCount < MAX_GREED && this.tickCount % 10 == 0) {
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(12), e -> e.getItem().is(Items.GOLD_BLOCK))) {
				greedTarget = item;
				forceAttack("greed");
				return;
			}
		}
		if (this.isPassenger() || current != null) return;
		ServerPlayer target = nearestTarget();
		if (target != null) {
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.tickCount % 10 == 0) this.getNavigation().moveTo(target, getPhase() >= 2 ? 1.25 : 1.0);
		}
		if (getPhase() >= 2 && this.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 3.1, this.getZ(), 1, 0.3, 0.1, 0.3, 0);
		}
	}

	@Override
	protected boolean tickAttack(ServerLevel level, AttackSpec spec, int t) {
		return switch (spec.id()) {
			case "gold_bombs" -> goldBombs(level, spec, t);
			case "axe_cleave" -> axeCleave(level, spec, t);
			case "call_enforcers" -> callEnforcers(level, spec, t);
			case "molten_hoard" -> moltenHoard(level, spec, t);
			case "greed" -> greed(level, t);
			default -> true;
		};
	}

	private boolean goldBombs(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			setFlag(FLAG_THROWING, true);
			this.playSound(SoundEvents.PIGLIN_ANGRY, 2.0F, 0.6F);
		}
		if (t < spec.windup()) return false;
		int count = getPhase() == 3 ? 3 : 2;
		for (int i = 0; i < count; i++) {
			ServerPlayer target = nextTarget();
			if (target == null) break;
			Vec3 from = this.getEyePosition();
			Vec3 to = target.position().add((this.random.nextDouble() - 0.5) * 3, 0, (this.random.nextDouble() - 0.5) * 3);
			GoldBomb bomb = new GoldBomb(GruntharContent.GOLD_BOMB, this, level);
			bomb.setPos(from);
			bomb.setDamage(spec.damage());
			double dx = to.x - from.x, dz = to.z - from.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			bomb.shoot(dx, to.y - from.y + dist * 0.35, dz, (float) Math.min(1.4, 0.5 + dist * 0.06), 2.0F);
			level.addFreshEntity(bomb);
		}
		this.playSound(SoundEvents.SNOWBALL_THROW, 2.0F, 0.5F);
		this.swingForAttack(net.minecraft.world.InteractionHand.OFF_HAND);
		setFlag(FLAG_THROWING, false);
		return true;
	}

	private boolean axeCleave(ServerLevel level, AttackSpec spec, int t) {
		ServerPlayer target = nearestTarget();
		if (target == null) return true;
		if (t == 0) {
			setFlag(FLAG_RAISED_AXE, true);
			this.getNavigation().stop();
			this.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 2.0F, 0.7F);
		}
		this.getLookControl().setLookAt(target, 60.0F, 30.0F);
		if (t < spec.windup()) {
			if (t == 2) Decals.cone(level, this.position(), this.getYRot(), 55, 4.0, Decals.RED);
			return false;
		}
		setFlag(FLAG_RAISED_AXE, false);
		this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		Vec3 look = Vec3.directionFromRotation(0, this.getYRot());
		for (ServerPlayer p : targets()) {
			Vec3 to = p.position().subtract(this.position());
			if (to.horizontalDistance() < 4.2 && (to.horizontalDistance() < 1.2 || look.dot(new Vec3(to.x, 0, to.z).normalize()) > 0.55)) {
				hit(level, p, spec.damage());
			}
		}
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
		return true;
	}

	private boolean callEnforcers(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) this.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 3.0F, 0.5F);
		if (t < spec.windup()) {
			if (t % 10 == 0) this.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 3.0F, 0.5F);
			return false;
		}
		BossFight fight = fight();
		if (fight == null) return true;
		int players = Math.max(1, fight.activePlayers().size());
		Vec3 c = homeCenter();
		for (int i = 0; i < players; i++) {
			PiglinBrute brute = EntityTypes.PIGLIN_BRUTE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (brute == null) continue;
			brute.snapTo(c.x + (this.random.nextDouble() - 0.5) * 10, c.y, c.z + (this.random.nextDouble() - 0.5) * 6 - 2, 0, 0);
			GruntharContent.makeEnforcer(brute);
			fight.minion(brute);
			level.addFreshEntity(brute);
		}
		for (int i = 0; i < 2; i++) {
			Piglin piglin = EntityTypes.PIGLIN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (piglin == null) continue;
			piglin.snapTo(c.x + (i == 0 ? -8 : 8), c.y, c.z - 6, 0, 0);
			piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			piglin.setDropChance(EquipmentSlot.MAINHAND, 0);
			piglin.setImmuneToZombification(true);
			piglin.setPersistenceRequired();
			ServerPlayer target = nextTarget();
			if (target != null) piglin.setTarget(target);
			fight.minion(piglin);
			level.addFreshEntity(piglin);
		}
		return true;
	}

	/** Lava drips mark the spots for 1.5 s, then lava falls for 5 s; magma patches stay. */
	private boolean moltenHoard(ServerLevel level, AttackSpec spec, int t) {
		Vec3 c = homeCenter();
		if (t == 0) {
			lavaSpots.clear();
			for (ServerPlayer p : targets()) lavaSpots.add(new Vec3(p.getX(), c.y, p.getZ()));
			while (lavaSpots.size() < 6) {
				double a = this.random.nextDouble() * Mth.TWO_PI, r = 2 + this.random.nextDouble() * 9;
				lavaSpots.add(new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
			}
			this.playSound(SoundEvents.LAVA_AMBIENT, 4.0F, 0.6F);
		}
		int fallStart = spec.windup();
		if (t < fallStart) {
			if (t % 4 == 0) for (Vec3 s : lavaSpots) {
				Decals.disc(level, s, 1.5, Decals.YELLOW);
				level.sendParticles(ParticleTypes.DRIPPING_LAVA, s.x, c.y + 9, s.z, 3, 0.4, 0.1, 0.4, 0);
			}
			return false;
		}
		int k = t - fallStart;
		if (k == 0) {
			for (Vec3 s : lavaSpots) {
				BlockPos floor = BlockPos.containing(s.x, c.y - 1, s.z);
				if (!level.getBlockState(floor).isAir()) level.setBlock(floor, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
			}
		}
		if (k % 2 == 0) for (Vec3 s : lavaSpots) {
			for (int y = 0; y < 10; y += 2) level.sendParticles(ParticleTypes.FALLING_LAVA, s.x, c.y + y, s.z, 2, 0.35, 0.3, 0.35, 0);
			level.sendParticles(ParticleTypes.LAVA, s.x, c.y + 0.2, s.z, 1, 0.3, 0, 0.3, 0);
		}
		if (k % 10 == 0) {
			for (ServerPlayer p : targets()) {
				for (Vec3 s : lavaSpots) {
					if (Mth.square(p.getX() - s.x) + Mth.square(p.getZ() - s.z) < 2.25) {
						hit(level, p, spec.damage(), StubFramework.BOSS_FIRE, null);
						p.igniteForSeconds(4);
						break;
					}
				}
			}
		}
		return k >= 100;
	}

	/** Greed: drop a gold block and he stops for 3 s to pick it up (max 3 times). */
	private boolean greed(ServerLevel level, int t) {
		if (t == 0) {
			greedCount++;
			greedPickTick = -1;
			setFlag(FLAG_GREED, true);
			if (fight() != null) fight().flash(Component.translatable("boss.elsewhere.greed"), 60);
			this.playSound(SoundEvents.PIGLIN_ADMIRING_ITEM, 3.0F, 0.6F);
		}
		if (greedPickTick < 0) {
			if (greedTarget == null || !greedTarget.isAlive() || t > 100) {
				setFlag(FLAG_GREED, false);
				return true;
			}
			if (this.distanceTo(greedTarget) > 2.0) {
				this.getNavigation().moveTo(greedTarget, 1.3);
				return false;
			}
			greedTarget.discard();
			greedTarget = null;
			greedPickTick = t;
			this.playSound(SoundEvents.ITEM_PICKUP, 2.0F, 0.6F);
		}
		this.getNavigation().stop();
		if (t % 5 == 0) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 3.2, this.getZ(), 2, 0.3, 0.2, 0.3, 0);
		if (t - greedPickTick >= 60) {
			setFlag(FLAG_GREED, false);
			return true;
		}
		return false;
	}

	private int greedPickTick = -1;

	public int greedCount() {
		return greedCount;
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		if (phase >= 2 && this.isPassenger()) {
			// dismount leap
			this.stopRiding();
			ServerPlayer target = nearestTarget();
			Vec3 dir = target != null ? target.position().subtract(this.position()).normalize() : Vec3.directionFromRotation(0, this.getYRot());
			this.setDeltaMovement(dir.x * 0.6, 0.9, dir.z * 0.6);
			this.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 3.0F, 0.5F);
		}
		if (phase >= 2) {
			this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36); // berserk
		}
	}

	/** Ashtusk died first: Grunthar drops to the ground and fights on. */
	void onMountDied(ServerLevel level) {
		if (mountBar != null) mountBar.setProgress(0);
		if (this.isPassenger()) this.stopRiding();
	}

	@Override
	protected void onBossDeath(ServerLevel level) {
		// The fight's phase machine lives in Grunthar: his death ends it; a riderless Ashtusk collapses.
		Ashtusk a = mount();
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.5, this.getZ(), 30, 1, 1, 1, 0.2);
		for (int i = 0; i < 12; i++) {
			ItemEntity nugget = new ItemEntity(level, this.getX(), this.getY() + 1, this.getZ(), new ItemStack(Items.GOLD_NUGGET, 1 + this.random.nextInt(3)));
			nugget.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.6, 0.4, (this.random.nextDouble() - 0.5) * 0.6);
			level.addFreshEntity(nugget); // the hoard spills
		}
		if (a != null && a.isAlive()) a.kill(level);
		if (fight != null) fight.victory(level, null);
	}

	@Override
	public BossArena createArena(BlockPos origin) {
		return GruntharArena.arena(origin);
	}

	@Override
	public void rebuildArena(ServerLevel level, BlockPos origin) {
		dev.elsewhere.nether_end_bosses.fit3.NetherFit.rebuild(level, "grunthar", origin); // 3.0: the generator that built this arena (2.x or fit3)
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK);
	}

	@Override
	public List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			DropRule.signature("tyrants_tusk", () -> new ItemStack(GruntharContent.TYRANTS_TUSK)),
			DropRule.other("ashtusk_harness", () -> new ItemStack(GruntharContent.ASHTUSK_HARNESS)))));
		out.add(new ItemStack(GruntharContent.ASHTUSK_GLAND));
		return out;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (mountId != null) output.putString("Mount", mountId.toString());
		output.putInt("Greed", greedCount);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getString("Mount").ifPresent(s -> mountId = UUID.fromString(s));
		greedCount = input.getIntOr("Greed", 0);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}
}
