package dev.elsewhere.boss.hrolf;

import dev.elsewhere.ironpeak.Ironpeak;
import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.ironpeak.IronpeakItems;
import dev.elsewhere.ironpeak.mob.IronclanRaider;
import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import dev.elsewhere.stub.verdigris_ironpeak.Telegraph;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hrolf Grimhorn, Champion of the Proving Grounds (Tier III; design in NOTES.md). Rider: vindicator x1.5, HP 300,
 * armor 12, toughness 4. Mount: Grimhorn (HP 180). Phases run on their combined health (480):
 * 1 Mounted (100-50%): Grimhorn rams, Hrolf swings from the saddle; Slow Falling is denied in the arena.
 * 2 Duel (50-20%): horn blast dismount (Slowness II 4 s, radius 12), overhead axe crack line (42 raw), shield-break
 *   kick (20 raw, shield disabled 5 s), war-cry summons 2 Ironclan Raiders; Grimhorn rams whoever is closest.
 * 3 Last Rank (20-0%): if Grimhorn lives, Hrolf remounts and they charge every 4 s; if not, Hrolf berserks
 *   (attacks 25% faster, takes 20% more damage).
 */
public class HrolfBoss extends StubBoss {
	public static final int A_SWING = 1, A_HORN = 2, A_OVERHEAD = 3, A_KICK = 4, A_WARCRY = 5;
	public static final float SWING_RAW = 14, OVERHEAD_RAW = 42, KICK_RAW = 20;
	private @Nullable GrimhornEntity mount;
	private int swingCd = 30, overheadCd = 60, kickCd = 100, warcryCd = 200;
	private Vec3 lineDir = Vec3.ZERO;
	private boolean berserk;

	public HrolfBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.RED);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		setDropChance(EquipmentSlot.MAINHAND, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 300).add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 4)
			.add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
			.add(Attributes.FOLLOW_RANGE, 40).add(Attributes.STEP_HEIGHT, 1.1);
	}

	private java.util.@Nullable UUID mountId;

	public void setMount(GrimhornEntity g) {
		mount = g;
		mountId = g.getUUID();
		g.rider = this;
	}

	/** C36: the link survives a restart (mount UUID in NBT); relinked from the world once the mount is loaded. */
	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
		super.addAdditionalSaveData(out);
		if (mountId != null) out.putString("MountUUID", mountId.toString());
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
		super.readAdditionalSaveData(in);
		String id = in.getStringOr("MountUUID", "");
		try {
			mountId = id.isEmpty() ? null : java.util.UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			mountId = null;
		}
	}

	/** Test hook: drop the in-memory link as a reload does (the saved UUID stays). */
	public void forgetMount() {
		if (mount != null) mount.rider = null;
		mount = null;
	}

	/** Relinks Hrolf and Grimhorn after a reload; true once linked. */
	public boolean relinkMount(ServerLevel level) {
		if (mount != null && mount.isAlive()) return true;
		if (mountId == null) return false;
		if (level.getEntity(mountId) instanceof GrimhornEntity g && g.isAlive()) {
			setMount(g);
			return true;
		}
		return false;
	}

	public @Nullable GrimhornEntity mount() {
		return mount;
	}

	@Override
	public String bossId() {
		return "hrolf";
	}

	@Override
	public int tier() {
		return 3;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.50F, 0.20F};
	}

	@Override
	protected float phaseFraction() {
		float mountHp = mount != null && mount.isAlive() ? mount.getHealth() : 0;
		float mountMax = mount != null ? mount.getMaxHealth() : 180;
		return (getHealth() + mountHp) / (getMaxHealth() + mountMax);
	}

	@Override
	protected @Nullable SoundEvent phaseSound() {
		return VerdigrisIronpeakSounds.HROLF_HORN.value();
	}

	@Override
	protected int sealColor() {
		return 0xFF8040;
	}

	public @Nullable ServerPlayer nearestFighter(LivingEntity from) {
		ServerPlayer best = null;
		double bd = Double.MAX_VALUE;
		for (ServerPlayer p : fighters()) {
			double d = p.distanceToSqr(from);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	@Override
	protected void tickMovement(ServerLevel level, @Nullable LivingEntity target) {
		if (isPassenger()) {
			if (target != null) getLookControl().setLookAt(target, 30, 30);
			return;
		}
		super.tickMovement(level, target);
	}

	@Override
	protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
		super.actuallyHurt(level, source, berserk ? dmg * 1.2F : dmg);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (mount != null && source.getEntity() == mount) return false;
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		if (phase == 1) {
			startAttack(A_HORN, Component.translatable("attack.elsewhere.hrolf.horn"), 30);
		} else if (phase == 2) {
			if (mount != null && mount.isAlive()) {
				startRiding(mount, true, true);
				mount.chargeEvery = 80;
				Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.hrolf.remount"));
			} else {
				berserk = true;
				var speed = getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) speed.setBaseValue(0.38);
				Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.hrolf.berserk"));
			}
		}
	}

	@Override
	protected void chooseAttack(ServerLevel level, LivingEntity target) {
		double d = distanceToSqr(target);
		if (isPassenger()) {
			if (swingCd <= 0 && d < 4.5 * 4.5) {
				startAttack(A_SWING, null, 10);
				swingCd = berserk ? 22 : 30;
			}
			return;
		}
		if (warcryCd <= 0) {
			startAttack(A_WARCRY, Component.translatable("attack.elsewhere.hrolf.warcry"), 20);
			warcryCd = 500;
		} else if (kickCd <= 0 && d < 3 * 3 && target.isBlocking()) {
			startAttack(A_KICK, Component.translatable("attack.elsewhere.hrolf.kick"), 15);
			kickCd = 120;
		} else if (overheadCd <= 0 && d < 9 * 9) {
			lineDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
			startAttack(A_OVERHEAD, Component.translatable("attack.elsewhere.hrolf.overhead"), 20);
			overheadCd = berserk ? 75 : 100;
		} else if (swingCd <= 0 && d < 3.2 * 3.2) {
			startAttack(A_SWING, null, 10);
			swingCd = berserk ? 22 : 30;
		}
	}

	@Override
	protected boolean tickAttack(ServerLevel level, int attack, int tick, @Nullable LivingEntity target) {
		switch (attack) {
			case A_SWING -> {
				if (tick == 8 && target != null && distanceToSqr(target) < 4.5 * 4.5) hitPlayer(level, target, SWING_RAW);
				return tick < 12;
			}
			case A_HORN -> {
				if (tick == 1) stopRiding();
				if (tick < 20 && tick % 4 == 0) Telegraph.circle(level, position(), 12, Telegraph.YELLOW);
				if (tick == 20) {
					level.playSound(null, blockPosition(), VerdigrisIronpeakSounds.HROLF_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
					for (ServerPlayer p : fighters()) if (p.distanceToSqr(this) < 12 * 12) {
						p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
						Fx.push(p, position(), 0.8, 0.3);
					}
				}
				return tick < 30;
			}
			case A_OVERHEAD -> {
				Vec3 a = position(), b = a.add(lineDir.scale(9));
				if (tick < 20 && tick % 3 == 0) Telegraph.line(level, a, b, 0.6, tick < 10 ? Telegraph.YELLOW : Telegraph.RED);
				if (tick == 20) {
					level.playSound(null, blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 2.0F, 0.7F);
					for (double t = 0; t <= 9; t += 0.7) {
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), a.x + lineDir.x * t, a.y + 0.2, a.z + lineDir.z * t, 6, 0.2, 0.1, 0.2, 0.15);
					}
					for (ServerPlayer p : fighters()) {
						Vec3 rel = p.position().subtract(a);
						double along = rel.dot(lineDir), side = rel.subtract(lineDir.scale(along)).horizontalDistance();
						if (along > -0.5 && along < 9.5 && side < 1.2) hitPlayer(level, p, OVERHEAD_RAW);
					}
				}
				return tick < 28;
			}
			case A_KICK -> {
				if (tick == 6 && target instanceof ServerPlayer p && distanceToSqr(p) < 3.5 * 3.5) {
					ItemStack using = p.getUseItem();
					if (p.isBlocking() && !using.isEmpty()) {
						p.getCooldowns().addCooldown(using, 100);
						p.stopUsingItem();
						level.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
					}
					hitPlayer(level, p, KICK_RAW);
					Fx.push(p, position(), 1.2, 0.3);
				}
				return tick < 12;
			}
			case A_WARCRY -> {
				if (tick == 10) {
					level.playSound(null, blockPosition(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 3.0F, 0.6F);
					int cap = 2 + 2 * Math.max(0, participants.size() - 1);
					for (int i = 0; i < 2 && liveMinions(level) < cap; i++) {
						IronclanRaider r = IronpeakEntities.IRONCLAN_RAIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
						if (r == null) continue;
						r.snapTo(getX() + (i == 0 ? 2 : -2), getY(), getZ() + 1, getYRot(), 0);
						r.finalizeSpawn(level, level.getCurrentDifficultyAt(r.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
						r.summoned = true;
						r.setPersistenceRequired();
						ServerPlayer t = randomFighter();
						if (t != null) r.setTarget(t);
						level.addFreshEntity(r);
						trackMinion(r);
					}
				}
				return tick < 20;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	protected void tickAlways(ServerLevel level) {
		if (mount == null && tickCount % 10 == 0) relinkMount(level);
		swingCd--;
		overheadCd--;
		kickCd--;
		warcryCd--;
		// Slow Falling is denied in this arena.
		for (ServerPlayer p : fighters()) if (p.hasEffect(MobEffects.SLOW_FALLING)) p.removeEffect(MobEffects.SLOW_FALLING);
		if (mount != null && !mount.isAlive() && isPassenger()) stopRiding();
	}

	@Override
	protected void clearMinions(ServerLevel level) {
		super.clearMinions(level);
	}

	@Override
	public void resetFight(ServerLevel level) {
		if (mount != null) mount.discard();
		super.resetFight(level);
	}

	@Override
	protected void onDefeated(ServerLevel level) {
		if (mount != null && mount.isAlive()) mount.discard();
	}

	@Override
	protected List<ItemStack> personalLoot(ServerPlayer player, boolean firstKill, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>(dev.elsewhere.verdigris_ironpeak.ArsenalLoot.roll(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("avalanche_horn", () -> new ItemStack(IronpeakItems.AVALANCHE_HORN)),
			dev.elsewhere.boss.kit.DropRule.other("champions_belt", () -> new ItemStack(IronpeakItems.CHAMPIONS_BELT)))));
		loot.add(new ItemStack(IronpeakItems.SKYIRON, 2 + random.nextInt(3)));
				Ironpeak.setVictory(player, "hrolf");
		return loot;
	}

	public boolean isBerserk() {
		return berserk;
	}

	public static BlockPos spawnOffset() {
		return new BlockPos(0, 1, -6);
	}
}
