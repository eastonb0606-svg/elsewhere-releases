package dev.elsewhere.boss.neferkhet;

import dev.elsewhere.overworld_bosses.Geo;
import dev.elsewhere.overworld_bosses.OwbEntities;
import dev.elsewhere.overworld_bosses.OwbItems;
import dev.elsewhere.overworld_bosses.OwbSounds;
import dev.elsewhere.overworld_bosses.Summons;
import dev.elsewhere.overworld_bosses.world.Blueprints;
import dev.elsewhere.stub.overworld_bosses.BossArena;
import dev.elsewhere.stub.overworld_bosses.BossAttack;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import dev.elsewhere.stub.overworld_bosses.SignatureGear;
import dev.elsewhere.stub.overworld_bosses.Telegraph;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Neferkhet, the Sand-Bound King (BOSSES.md #3, Tier I). Holds the centre of the Sun Chamber. Four sun idols on the
 * pillars lock charging Sun Lance beams onto players (break line of sight behind a pillar); floor tiles bubble and
 * collapse into scarab pits; in phase 2 the sarcophagi release Wrapped Sentinels and the Curse of the Ankh marks a
 * player until they stand in the central sunbeam; in phase 3 the roof seals (eclipse), darkness pulses and the four
 * lances fire clockwise in sequence.
 */
public class NeferkhetEntity extends ElsewhereBoss {
	public static final String ID = "neferkhet";
	public static final int ACT_LANCE = 1, ACT_SINK = 2, ACT_CURSE = 3, ACT_SWING = 4, ACT_ECLIPSE = 5, ACT_SARCO = 6;

	private static final EntityDataAccessor<Boolean> DATA_ECLIPSE = SynchedEntityData.defineId(NeferkhetEntity.class, EntityDataSerializers.BOOLEAN);

	private int nextIdol;
	private int lanceTargetIndex;
	private final List<BlockPos> sinkTiles = new ArrayList<>();
	private @Nullable UUID cursed;
	private Display.@Nullable TextDisplay ankhMark;
	private int lanceSequence = -1;
	private final List<LivingEntity> sequenceTargets = new ArrayList<>();

	public NeferkhetEntity(EntityType<? extends NeferkhetEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 360.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.MOVEMENT_SPEED, 0.18)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 12.0);
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
		return BossEvent.BossBarColor.YELLOW;
	}

	@Override
	protected @Nullable SoundEvent roarSound() {
		return OwbSounds.NEFERKHET_CHANT;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ECLIPSE, false);
	}

	public boolean isEclipse() {
		return this.entityData.get(DATA_ECLIPSE);
	}

	/** Centre of the chamber floor (arena origin). */
	private BlockPos origin() {
		return this.arena != null ? this.arena.center : blockPosition();
	}

	// ---- attacks -------------------------------------------------------------------------------------------------

	@Override
	protected void registerAttacks() {
		this.attacks.add(BossAttack.<NeferkhetEntity>of(ID, "sun_lance")
			.cooldown(120).windup(10).active(0).phases(1, 2).danger(BossAttack.Danger.BIG)
			.when(b -> b.getTarget() != null)
			.onStart(b -> b.setAction(ACT_LANCE))
			.onFire(b -> {
				if (b.level() instanceof ServerLevel sl) {
					LivingEntity t = b.nextLanceTarget(sl);
					if (t != null) b.fireLance(sl, b.nextIdol++ % 4, t);
				}
			}));

		this.attacks.add(BossAttack.<NeferkhetEntity>of(ID, "quicksand")
			.cooldown(300).windup(30).active(0)
			.when(b -> b.getTarget() != null)
			.onStart(b -> {
				b.setAction(ACT_SINK);
				b.sinkTiles.clear();
				BlockPos c = b.getTarget().blockPosition().below();
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.sinkTiles.add(new BlockPos(c.getX() + dx, b.floorY(), c.getZ() + dz));
				b.playSound(SoundEvents.SAND_BREAK, 2.0F, 0.6F);
			})
			.onWindup((b, t) -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
				for (BlockPos p : b.sinkTiles) {
					if (t % 4 == 0) sl.sendParticles(dust, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 4, 0.35, 0.05, 0.35, 0.0);
				}
				if (t % 4 == 0) {
					Vec3 c = Vec3.atCenterOf(b.sinkTiles.get(4)).add(0, 0.55, 0);
					Telegraph.ring(sl, c, 1.9, Telegraph.YELLOW);
				}
			})
			.onFire(NeferkhetEntity::collapseFloor));

		this.attacks.add(BossAttack.<NeferkhetEntity>of(ID, "staff_swing")
			.cooldown(50).windup(16).active(0).weight(2)
			.when(b -> b.getTarget() != null && b.distanceTo(b.getTarget()) < 4.0)
			.onStart(b -> b.setAction(ACT_SWING))
			.onWindup((b, t) -> {
				if (b.getTarget() != null) Geo.face(b, Geo.flatDir(b.position(), b.getTarget().position()));
			})
			.onFire(b -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				b.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
				for (ServerPlayer p : b.playersInArena(sl)) {
					if (p.distanceTo(b) < 4.2) {
						BossDamage.hit(b, p, b.num("staff_swing", "damage", 24.0F), BossRegistry.BOSS_ATTACK, false);
						Vec3 away = Geo.flatDir(b.position(), p.position());
						Geo.push(p, new Vec3(away.x * 0.9, 0.35, away.z * 0.9));
					}
				}
			}));

		this.attacks.add(BossAttack.<NeferkhetEntity>of(ID, "sarcophagi")
			.cooldown(400).windup(30).active(0).phases(2, 3)
			.when(b -> b.liveMinions() < b.minionCap(4))
			.onStart(b -> {
				b.setAction(ACT_SARCO);
				b.playSound(SoundEvents.STONE_BREAK, 3.0F, 0.5F);
			})
			.onFire(NeferkhetEntity::openSarcophagi));

		this.attacks.add(BossAttack.<NeferkhetEntity>of(ID, "curse_of_the_ankh")
			.cooldown(360).windup(20).active(0).phases(2, 3).danger(BossAttack.Danger.INTERACT)
			.when(b -> b.cursed == null && b.getTarget() instanceof ServerPlayer)
			.onStart(b -> b.setAction(ACT_CURSE))
			.onFire(b -> {
				if (b.getTarget() instanceof ServerPlayer p && b.level() instanceof ServerLevel sl) b.curse(sl, p);
			}));

		this.attacks.add(BossAttack.<NeferkhetEntity>of(ID, "eclipse_lances")
			.cooldown(170).windup(20).active(4 * 22).phases(3).danger(BossAttack.Danger.BIG)
			.when(b -> b.getTarget() != null)
			.onStart(b -> {
				b.setAction(ACT_LANCE);
				b.sequenceTargets.clear();
			})
			.onFire(b -> b.lanceSequence = 0)
			.onActive((b, t) -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				if (t % 22 == 1 && b.lanceSequence >= 0 && b.lanceSequence < 4) {
					LivingEntity target = b.nextLanceTarget(sl);
					if (target != null) b.fireLance(sl, b.lanceSequence, target);
					b.lanceSequence++;
				}
			})
			.onEnd(b -> b.lanceSequence = -1));
	}

	private int floorY() {
		return this.arena != null ? this.arena.floorY : (int) Math.floor(getY()) - 1;
	}

	private @Nullable LivingEntity nextLanceTarget(ServerLevel sl) {
		List<ServerPlayer> ps = playersInArena(sl);
		if (ps.isEmpty()) return getTarget();
		return ps.get(this.lanceTargetIndex++ % ps.size());
	}

	/** Spawns a charging lance from idol i at the target (2 s charge, fires if line of sight holds). */
	public SunLance fireLance(ServerLevel sl, int idol, LivingEntity target) {
		Vec3 from = Vec3.atCenterOf(Blueprints.sunIdolPos(origin(), idol));
		SunLance lance = new SunLance(OwbEntities.SUN_LANCE, sl);
		lance.setPos(from);
		lance.setup(this, target, num("sun_lance", "damage", 38.0F));
		lance.addTag(BossDamage.MINION_TAG);
		sl.addFreshEntity(lance);
		sl.playSound(null, from.x, from.y, from.z, OwbSounds.NEFERKHET_LANCE_CHARGE, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 1.0F);
		return lance;
	}

	private void collapseFloor() {
		if (!(level() instanceof ServerLevel sl)) return;
		playSound(SoundEvents.GRAVEL_BREAK, 3.0F, 0.5F);
		for (BlockPos p : this.sinkTiles) {
			if (this.arena != null && !this.arena.containsBlock(p)) continue;
			for (int d = 0; d < 3; d++) {
				BlockPos q = p.below(d);
				if (!sl.getBlockState(q).isAir()) {
					sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(q)), q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.1);
					sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		if (!this.sinkTiles.isEmpty() && liveMinions() < minionCap(8)) {
			BlockPos c = this.sinkTiles.get(4).below(2);
			for (int i = 0; i < 3; i++) {
				Silverfish scarab = EntityTypes.SILVERFISH.create(sl, EntitySpawnReason.MOB_SUMMONED);
				if (scarab == null) continue;
				scarab.setPos(c.getX() + 0.5 + (i - 1) * 0.6, c.getY(), c.getZ() + 0.5);
				scarab.setCustomName(Component.translatable("entity.elsewhere.neferkhet_scarab"));
				addMinion(sl, scarab);
			}
		}
	}

	private void openSarcophagi() {
		if (!(level() instanceof ServerLevel sl)) return;
		BlockPos o = origin();
		for (int[] sc : Blueprints.SARCOPHAGI) {
			int sx = sc[0] < 0 ? -1 : 1;
			for (int ax = 0; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					BlockPos lid = o.offset(sc[0] + sx * ax, 1, az);
					if (!sl.getBlockState(lid).isAir()) {
						sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(lid)), lid.getX() + 0.5, lid.getY() + 0.5, lid.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
						sl.setBlock(lid, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
			for (int i = 0; i < 2 && liveMinions() < minionCap(4); i++) {
				WrappedSentinel s = OwbEntities.WRAPPED_SENTINEL.create(sl, EntitySpawnReason.MOB_SUMMONED);
				if (s == null) continue;
				BlockPos at = o.offset(sc[0] - sx * 2, 0, i * 2 - 1);
				s.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				s.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
				addMinion(sl, s);
			}
		}
	}

	// ---- the curse --------------------------------------------------------------------------------------------------

	private void curse(ServerLevel sl, ServerPlayer p) {
		this.cursed = p.getUUID();
		playSound(OwbSounds.NEFERKHET_CHANT, 2.5F, 1.4F);
		p.sendOverlayMessage(Component.translatable("message.elsewhere.neferkhet.cursed"));
		Display.TextDisplay mark = EntityTypes.TEXT_DISPLAY.create(sl, EntitySpawnReason.MOB_SUMMONED);
		if (mark != null) {
			mark.setText(Component.literal("☥").withStyle(net.minecraft.ChatFormatting.GOLD));
			mark.setBillboardConstraints(Display.BillboardConstraints.CENTER);
			mark.setPos(p.getX(), p.getY() + 2.6, p.getZ());
			mark.addTag(BossDamage.MINION_TAG);
			sl.addFreshEntity(mark);
			this.ankhMark = mark;
		}
	}

	public @Nullable UUID cursedPlayer() {
		return this.cursed;
	}

	private void tickCurse(ServerLevel sl) {
		if (this.cursed == null) return;
		ServerPlayer p = sl.getServer().getPlayerList().getPlayer(this.cursed);
		if (p == null || !p.isAlive() || this.arena == null || !this.arena.contains(p)) {
			clearCurse();
			return;
		}
		if (this.ankhMark != null) this.ankhMark.setPos(p.getX(), p.getY() + 2.6, p.getZ());
		if (this.tickCount % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1), this);
			p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
		}
		// Cleanse: stand in the central sunbeam (not during the eclipse: the roof is sealed, the curse just ends).
		BlockPos c = origin();
		double dx = p.getX() - (c.getX() + 0.5), dz = p.getZ() - (c.getZ() + 0.5);
		if (dx * dx + dz * dz <= 2.25 || isEclipse()) {
			p.removeEffect(MobEffects.HUNGER);
			p.removeEffect(MobEffects.WITHER);
			sl.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
			p.sendOverlayMessage(Component.translatable("message.elsewhere.neferkhet.cleansed"));
			clearCurse();
		}
	}

	private void clearCurse() {
		this.cursed = null;
		if (this.ankhMark != null) this.ankhMark.discard();
		this.ankhMark = null;
	}

	// ---- per tick, phases -----------------------------------------------------------------------------------------

	@Override
	protected void bossTick(ServerLevel level) {
		tickCurse(level);
		if (this.tickCount % 8 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), getX(), getY() + 3, getZ(), 2, 0.5, 1.5, 0.5, 0.0);
		if (isEclipse() && this.tickCount % 200 == 0) {
			for (ServerPlayer p : playersInArena(level)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
		}
		// Holds the centre: walks back if pushed out, faces its target.
		LivingEntity t = getTarget();
		if (t != null) getLookControl().setLookAt(t, 20.0F, 20.0F);
		if (this.home != null && currentAttackId() == null && this.tickCount % 20 == 0) {
			if (distanceToSqr(Vec3.atBottomCenterOf(this.home)) > 4.0) getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 1.0);
			else if (t != null && t.distanceTo(this) < 6) getNavigation().moveTo(t, 0.9);
			else getNavigation().stop();
		}
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int phase) {
		if (phase == 3) {
			this.entityData.set(DATA_ECLIPSE, true);
			setAction(ACT_ECLIPSE);
			Blueprints.sunChamberEclipse(Summons.levelSink(level), origin());
			playSound(SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.5F);
			for (ServerPlayer p : playersInArena(level)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
		}
	}

	@Override
	protected void onFightStart(ServerLevel level) {
		this.entityData.set(DATA_ECLIPSE, false);
		this.nextIdol = 0;
	}

	@Override
	protected void onFightReset(ServerLevel level) {
		clearCurse();
		this.entityData.set(DATA_ECLIPSE, false);
		for (SunLance l : level.getEntitiesOfClass(SunLance.class, getBoundingBox().inflate(40))) l.discard();
	}

	@Override
	protected void onFightWon(ServerLevel level) {
		clearCurse();
		for (SunLance l : level.getEntitiesOfClass(SunLance.class, getBoundingBox().inflate(40))) l.discard();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), getX(), getY() + 3, getZ(), 200, 0.6, 2.0, 0.6, 0.2);
	}

	@Override
	protected BossArena defaultArena() {
		BlockPos p = blockPosition();
		return new BossArena(p, 15, 5, 18, p.getY() - 1, 4);
	}

	// ---- loot -------------------------------------------------------------------------------------------------------

	@Override
	protected List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("sunfire_khopesh", () -> new ItemStack(OwbItems.SUNFIRE_KHOPESH)),
			dev.elsewhere.boss.kit.DropRule.other("pharaohs_nemes", () -> new ItemStack(OwbItems.PHARAOHS_NEMES)),
			dev.elsewhere.boss.kit.DropRule.extraLife("ankh_of_neferkhet", () -> new ItemStack(OwbItems.ANKH_OF_NEFERKHET)))));
		if (FabricLoader.getInstance().isModLoaded("testmeter")) out.add(new ItemStack(OwbItems.ROYAL_HONEY, 2));
		return out;
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK);
	}

	@Override
	protected @Nullable Item tierSignature() {
		return SignatureGear.CHALLENGERS_CLEAVER;
	}
}
