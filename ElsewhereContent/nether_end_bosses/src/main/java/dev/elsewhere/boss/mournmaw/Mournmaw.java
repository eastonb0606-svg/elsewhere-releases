package dev.elsewhere.boss.mournmaw;

import dev.elsewhere.boss.kit.BossRewards;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.NetherEndSounds;
import dev.elsewhere.stub.nether_end_bosses.AttackSpec;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import dev.elsewhere.stub.nether_end_bosses.BossFight;
import dev.elsewhere.stub.nether_end_bosses.Decals;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Mournmaw, the Grieving Ghast (Tier II, 520 base HP, armor 8). Cries dormant above the Weeping Basin until a player
 * comes near with a dried ghast. 1 Lament (100-60%): Wail, Tear Rain, Soul Fireballs, strafing high. 2 Descent
 * (60-30%): drops low, Chain Whip, Mourners rise. 3 Grief Storm (30-0%): spiral tear rain. Mercy: a dried ghast placed
 * in the basin during phase 3 that survives 30 s calms her (the fight ends with the mercy drops).
 */
public class Mournmaw extends ElsewhereBoss {
	public static final int FLAG_CHAINS_GLOW = FLAG_CUSTOM;
	public static final int FLAG_INHALE = FLAG_CUSTOM << 1;
	public static final int FLAG_MERCY = FLAG_CUSTOM << 2;
	public static final int MERCY_TICKS = 600;

	private final List<Vec3> marks = new ArrayList<>();
	private int mercyTicks = -1;
	private BlockPos mercyPos;

	public Mournmaw(EntityType<? extends Mournmaw> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 250;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 520.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FLYING_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override public String bossId() { return "mournmaw"; }
	@Override public int tier() { return 2; }
	@Override public BossEvent.BossBarColor barColor() { return BossEvent.BossBarColor.BLUE; }
	@Override public BossEvent.BossBarOverlay barOverlay() { return BossEvent.BossBarOverlay.NOTCHED_10; }
	@Override public Holder<SoundEvent> music() { return NetherEndSounds.MUSIC_MOURNMAW; }
	@Override protected float[] phaseThresholds() { return new float[] {0.6F, 0.3F}; }
	@Override protected SoundEvent roarSound() { return SoundEvents.GHAST_HURT; }

	@Override
	protected List<AttackSpec> defaultAttacks() {
		return List.of(
			new AttackSpec("wail", "Wail", 30, 260, AttackSpec.phases(1, 3), 2, 0.0F),
			new AttackSpec("tear_rain", "Tear Rain", 30, 90, AttackSpec.phases(1, 2), 4, 16.0F),
			new AttackSpec("soul_fireball", "Soul Fireballs", 20, 60, AttackSpec.phases(1, 2, 3), 4, 24.0F),
			new AttackSpec("chain_whip", "Chain Whip", 24, 70, AttackSpec.phases(2, 3), 4, 42.0F),
			new AttackSpec("mourners", "Mourners Rise", 30, 400, AttackSpec.phases(2), 3, 0.0F),
			new AttackSpec("grief_storm", "Grief Storm", 20, 160, AttackSpec.phases(3), 5, 16.0F));
	}

	@Override
	protected boolean canStart(AttackSpec spec) {
		if (spec.id().equals("mourners")) return fight() != null && fight().minionCount((ServerLevel) this.level()) < fight().minionCap(3);
		return true;
	}

	/** Dormant: weeping above the basin. Wakes when a player comes within 20 blocks carrying a dried ghast. */
	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && isDormant()) {
			this.setDeltaMovement(Vec3.ZERO);
			if (this.tickCount % 6 == 0) level.sendParticles(ParticleTypes.FALLING_WATER, this.getX(), this.getY() + 2.5, this.getZ(), 3, 2.0, 0.3, 2.0, 0);
			if (this.tickCount % 20 == 0 && home() != null) {
				for (ServerPlayer p : level.players()) {
					if (!p.isSpectator() && p.distanceToSqr(homeCenter()) < 20 * 20 && p.getInventory().contains(new ItemStack(Items.DRIED_GHAST))) {
						startFight(level, home(), p, ItemStack.EMPTY);
						this.playSound(SoundEvents.GHAST_SCREAM, 5.0F, 0.6F);
						break;
					}
				}
			}
		}
	}

	@Override
	public void onFightReset(ServerLevel level) {
		// she goes back to weeping above the basin
		setFlag(FLAG_DORMANT, true);
		setFlag(FLAG_MERCY, false);
		setPhase(1);
		cancelAttack();
		this.setHealth(this.getMaxHealth());
		this.fight = null;
		Vec3 h = homeCenter();
		this.snapTo(h.x, h.y + 12, h.z, this.getYRot(), 0);
	}

	@Override
	protected void bossTick(ServerLevel level) {
		Vec3 home = homeCenter();
		int phase = getPhase();
		double height = phase == 1 ? 13.0 : 6.0;
		double radius = phase == 1 ? 8.0 : 4.0;
		double a = this.tickCount * 0.012;
		Vec3 goal = new Vec3(home.x + Math.cos(a) * radius, home.y + height, home.z + Math.sin(a) * radius);
		Vec3 v = goal.subtract(this.position()).scale(0.03);
		if (v.length() > 0.3) v = v.normalize().scale(0.3);
		this.setDeltaMovement(v);
		ServerPlayer target = nearestTarget();
		if (target != null) {
			double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
		}
		if (this.tickCount % 5 == 0) level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1, this.getZ(), 2, 3, 2, 3, 0.01);
		tickMercy(level);
	}

	@Override
	public void travel(Vec3 input) {
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected boolean tickAttack(ServerLevel level, AttackSpec spec, int t) {
		return switch (spec.id()) {
			case "wail" -> wail(level, spec, t);
			case "tear_rain" -> tearRain(level, spec, t, false);
			case "grief_storm" -> tearRain(level, spec, t, true);
			case "soul_fireball" -> soulFireball(level, spec, t);
			case "chain_whip" -> chainWhip(level, spec, t);
			case "mourners" -> mourners(level, spec, t);
			default -> true;
		};
	}

	private boolean wail(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			setFlag(FLAG_INHALE, true);
			this.playSound(SoundEvents.GHAST_WARN, 4.0F, 0.5F);
		}
		if (t < spec.windup()) {
			// particles sucked in
			for (int i = 0; i < 6; i++) {
				Vec3 at = this.position().add((this.random.nextDouble() - 0.5) * 16, this.random.nextDouble() * 8, (this.random.nextDouble() - 0.5) * 16);
				Vec3 vel = this.position().add(0, 4, 0).subtract(at).scale(0.08);
				level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 0, vel.x, vel.y, vel.z, 1.0);
			}
			return false;
		}
		setFlag(FLAG_INHALE, false);
		this.playSound(SoundEvents.GHAST_SCREAM, 6.0F, 0.4F);
		for (ServerPlayer p : targets()) {
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
			p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 160, 1));
		}
		return true;
	}

	/** Soul circles mark the ground, then tears fall there and leave soul fire. Grief Storm spirals the marks. */
	private boolean tearRain(ServerLevel level, AttackSpec spec, int t, boolean spiral) {
		Vec3 c = homeCenter();
		if (t == 0) {
			marks.clear();
			this.playSound(SoundEvents.GHAST_AMBIENT, 4.0F, 0.5F);
			if (spiral) {
				for (int i = 0; i < 18; i++) {
					double a = i * 0.7, r = 2 + i * 0.75;
					marks.add(new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
				}
			} else {
				for (ServerPlayer p : targets()) marks.add(new Vec3(p.getX(), c.y, p.getZ()));
				for (int i = 0; i < 6; i++) {
					double a = this.random.nextDouble() * Mth.TWO_PI, r = 2 + this.random.nextDouble() * 12;
					marks.add(new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
				}
			}
		}
		if (t < spec.windup()) {
			if (t % 5 == 0) for (Vec3 m : marks) Decals.circle(level, m, 1.5, Decals.CYAN);
			return false;
		}
		int k = t - spec.windup();
		int perTick = spiral ? 1 : marks.size();
		int start = spiral ? k / 2 : 0;
		if (spiral && k % 2 != 0) return false;
		for (int i = start; i < Math.min(marks.size(), start + perTick); i++) {
			Vec3 m = marks.get(i);
			SoulTear tear = new SoulTear(MournmawContent.SOUL_TEAR, this, level);
			tear.setPos(m.x, c.y + 16, m.z);
			tear.setDamage(spec.damage());
			tear.setDeltaMovement(0, -1.2, 0);
			level.addFreshEntity(tear);
		}
		return !spiral || start + 1 >= marks.size();
	}

	private boolean soulFireball(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) this.playSound(SoundEvents.GHAST_WARN, 3.0F, 0.8F);
		if (t < spec.windup()) return false;
		ServerPlayer target = nextTarget();
		if (target != null) {
			Vec3 from = this.position().add(0, 4, 0);
			Vec3 dir = target.getEyePosition().subtract(from).normalize();
			SoulFireball ball = new SoulFireball(MournmawContent.SOUL_FIREBALL, this, dir, level);
			ball.setPos(from.add(dir.scale(4.5)));
			ball.setDamage(spec.damage());
			level.addFreshEntity(ball);
			this.playSound(SoundEvents.GHAST_SHOOT, 3.0F, 0.7F);
		}
		return true;
	}

	/** Chains glow for 1.2 s, then a wide sweep around her (42 raw: get out of range or behind cover). */
	private boolean chainWhip(ServerLevel level, AttackSpec spec, int t) {
		Vec3 base = new Vec3(this.getX(), homeCenter().y, this.getZ());
		if (t == 0) {
			setFlag(FLAG_CHAINS_GLOW, true);
			this.playSound(SoundEvents.CHAIN_PLACE, 4.0F, 0.5F);
		}
		if (t < spec.windup()) {
			if (t % 5 == 0) Decals.circle(level, base, 9.0, Decals.RED);
			return false;
		}
		setFlag(FLAG_CHAINS_GLOW, false);
		this.playSound(SoundEvents.CHAIN_BREAK, 4.0F, 0.5F);
		for (ServerPlayer p : targets()) {
			if (p.position().distanceTo(base) < 9.0 && p.getY() < this.getY() + 2) {
				hit(level, p, spec.damage());
				Vec3 away = p.position().subtract(base).normalize();
				p.push(away.x * 1.2, 0.4, away.z * 1.2);
				p.syncVelocity = true;
			}
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, base.x, base.y + 1, base.z, 60, 6, 0.5, 6, 0.05);
		return true;
	}

	private boolean mourners(ServerLevel level, AttackSpec spec, int t) {
		if (t < spec.windup()) {
			if (t % 6 == 0) level.sendParticles(ParticleTypes.SOUL, homeCenter().x, homeCenter().y + 0.3, homeCenter().z, 20, 8, 0.1, 8, 0.02);
			return false;
		}
		BossFight fight = fight();
		if (fight == null) return true;
		int n = 1 + fight.activePlayers().size();
		Vec3 c = homeCenter();
		for (int i = 0; i < n; i++) {
			Skeleton s = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (s == null) continue;
			double a = this.random.nextDouble() * Mth.TWO_PI;
			s.snapTo(c.x + Math.cos(a) * 9, c.y, c.z + Math.sin(a) * 9, 0, 0);
			s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SOUL_LANTERN));
			s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
			s.setDropChance(EquipmentSlot.HEAD, 0);
			s.setDropChance(EquipmentSlot.MAINHAND, 0);
			s.setCustomName(Component.translatable("entity.elsewhere.mourner"));
			s.setPersistenceRequired();
			ServerPlayer target = nextTarget();
			if (target != null) s.setTarget(target);
			fight.minion(s);
			level.addFreshEntity(s);
		}
		return true;
	}

	// ---- mercy ending ----

	private void tickMercy(ServerLevel level) {
		if (getPhase() < 3 || this.tickCount % 10 != 0 && mercyTicks < 0) return;
		BlockPos o = home();
		if (o == null) return;
		if (mercyTicks < 0) {
			for (BlockPos p : BlockPos.betweenClosed(o.offset(-3, -1, -3), o.offset(3, 1, 3))) {
				if (level.getBlockState(p).is(Blocks.DRIED_GHAST)) {
					mercyPos = p.immutable();
					mercyTicks = 0;
					setFlag(FLAG_MERCY, true);
					if (fight() != null) fight().flash(Component.translatable("boss.elsewhere.mercy"), 80);
					break;
				}
			}
			return;
		}
		mercyTicks++;
		if (!level.getBlockState(mercyPos).is(Blocks.DRIED_GHAST)) {
			mercyTicks = -1;
			setFlag(FLAG_MERCY, false);
			if (fight() != null) fight().flash(Component.translatable("boss.elsewhere.mercy_failed"), 80);
			return;
		}
		if (mercyTicks % 20 == 0) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mercyPos.getX() + 0.5, mercyPos.getY() + 1, mercyPos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
		if (mercyTicks >= MERCY_TICKS) calm(level);
	}

	/** Forces the mercy timer to finish (self-test). */
	public void calm(ServerLevel level) {
		this.playSound(SoundEvents.GHAST_AMBIENT, 4.0F, 1.4F);
		level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 4, this.getZ(), 12, 2, 2, 2, 0);
		if (mercyPos != null && level.getBlockState(mercyPos).is(Blocks.DRIED_GHAST)) level.removeBlock(mercyPos, false);
		BossFight f = fight;
		if (f != null) f.victory(level, "mercy");
		this.discard();
	}

	public int mercyTicks() {
		return mercyTicks;
	}

	@Override
	public List<ItemStack> rollVariantDrops(String variant, ServerPlayer player, RandomSource random, boolean firstKill, List<ItemStack> normal) {
		if (!"mercy".equals(variant)) return normal;
		List<ItemStack> out = new ArrayList<>();
		out.addAll(BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			DropRule.extraLife("mournmaws_blessing", () -> new ItemStack(MournmawContent.MOURNMAWS_BLESSING)),
			DropRule.signature("lament_harness", () -> new ItemStack(MournmawContent.LAMENT_HARNESS)))));
		out.add(new ItemStack(MournmawContent.MOURNMAWS_TEAR));
		return out;
	}

	@Override
	public BossArena createArena(BlockPos origin) {
		return new BossArena(origin, MournmawArena.RADIUS, origin.getY() - 2, origin.getY() + 24, false, Vec3.atBottomCenterOf(origin.south(10)));
	}

	@Override
	public void rebuildArena(ServerLevel level, BlockPos origin) {
		dev.elsewhere.nether_end_bosses.fit3.NetherFit.rebuild(level, "mournmaw", origin); // 3.0: the generator that built this arena (2.x or fit3)
	}

	@Override
	public List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			DropRule.signature("lament_harness", () -> new ItemStack(MournmawContent.LAMENT_HARNESS)),
			DropRule.other("soulwick_lantern", () -> new ItemStack(MournmawContent.SOULWICK_LANTERN)))));
		out.add(new ItemStack(MournmawContent.MOURNMAWS_TEAR));
		return out;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}
}
