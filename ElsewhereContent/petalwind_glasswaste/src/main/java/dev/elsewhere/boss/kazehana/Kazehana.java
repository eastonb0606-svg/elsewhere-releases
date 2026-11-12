package dev.elsewhere.boss.kazehana;

import dev.elsewhere.petalwind.PetalwindItems;
import dev.elsewhere.petalwind.WindchimeBlock;
import dev.elsewhere.petalwind_glasswaste.BossDamage;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.Telegraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kazehana, the Gale Ronin (BOSSES.md #8, Tier I). A duelist on the Petal Pagoda roof: he waits and bows; bow back
 * (sneak twice) to begin. Phase 1 Iaido (Draw Cut, Petal Parry, 3-hit combo), phase 2 Gale (wind charges toward the
 * edges, wind-bell stuns, 2 Blossom Breezes), phase 3 Cherry Storm (petal clouds and 3 mirror copies; the finishing
 * Draw Cut crosses the whole roof). HP 340 base / 520 effective, armor 10, toughness 4.
 */
public class Kazehana extends BossBase {
	public static final float BASE_HP = 340, ARMOR = 10, TOUGHNESS = 4;
	public static final float DRAW_CUT_RAW = 36, COMBO_RAW = 20;
	public static final int DRAW_CUT_INTERVAL = 120, DRAW_CUT_WINDUP = 20, DRAW_CUT_LINE_TICKS = 16, PARRY_TICKS = 20, STUN_TICKS = 60;

	public static final int S_STANCE = 0, S_BOW = 1, S_IAIDO = 2, S_DASH = 3, S_COMBO = 4, S_PARRY = 5, S_STUNNED = 6, S_GALE = 7;
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Kazehana.class, EntityDataSerializers.INT);
	public static final byte EV_BOW = 70, EV_IAIDO = 71, EV_DASH = 72, EV_COMBO = 73, EV_PARRY = 74, EV_STUN = 75, EV_PHASE = 76, EV_GALE = 77;

	public final AnimationState bowAnimation = new AnimationState();
	public final AnimationState iaidoAnimation = new AnimationState();
	public final AnimationState dashAnimation = new AnimationState();
	public final AnimationState comboAnimation = new AnimationState();
	public final AnimationState parryAnimation = new AnimationState();
	public final AnimationState stunAnimation = new AnimationState();
	public final AnimationState phaseAnimation = new AnimationState();
	public final AnimationState galeAnimation = new AnimationState();

	private int stateTicks, drawCutCooldown = 80, comboCooldown = 40, comboHits, parryCooldown, galeCooldown = 60, mirageCooldown;
	private Vec3 cutFrom = Vec3.ZERO, cutTo = Vec3.ZERO;
	private boolean finishingCut;
	private final Set<UUID> cutHit = new HashSet<>();
	private final Deque<Integer> recentHits = new ArrayDeque<>();
	private boolean breezesSummoned;
	private final List<UUID> mirages = new ArrayList<>();
	private final java.util.Map<UUID, int[]> sneakTracker = new java.util.HashMap<>();
	private final java.util.Map<BlockPos, Integer> bellCooldown = new java.util.HashMap<>();
	/** Counters the self-test reads. */
	public int drawCutsDone, stunsTaken, parriesDone, galesCast;

	public Kazehana(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.PINK);
		this.setDormant(true);
		this.arenaRadius = PetalPagoda.ARENA_RADIUS;
		this.arenaDown = 4;
		this.arenaUp = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS).add(Attributes.ATTACK_DAMAGE, COMBO_RAW).add(Attributes.MOVEMENT_SPEED, 0.32)
			.add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.7).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, S_BOW);
	}

	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	private void setState(int s, byte event) {
		this.entityData.set(DATA_STATE, s);
		stateTicks = 0;
		if (event != 0 && this.level() instanceof ServerLevel level) level.broadcastEntityEvent(this, event);
	}

	@Override
	public String bossId() {
		return "kazehana";
	}

	@Override
	public int tier() {
		return 1;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.6F, 0.3F};
	}

	@Override
	protected boolean startsDormant() {
		return true;
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.BELL_RESONATE;
	}

	// ---- waiting: bow, and start when a player bows back (sneaks twice) -----------------------------------------
	@Override
	protected void idleTick(ServerLevel level) {
		super.idleTick(level);
		if (state() != S_BOW) setState(S_BOW, EV_BOW);
		if (this.tickCount % 60 == 0) level.broadcastEntityEvent(this, EV_BOW);
		ServerPlayer nearest = null;
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, arenaBox(), this::eligible)) {
			int[] t = sneakTracker.computeIfAbsent(p.getUUID(), u -> new int[] {0, -1000, 0});
			boolean sneaking = p.isShiftKeyDown();
			if (sneaking && t[0] == 0) {
				if (this.tickCount - t[1] <= 30) t[2]++;
				else t[2] = 1;
				t[1] = this.tickCount;
			}
			t[0] = sneaking ? 1 : 0;
			if (t[2] >= 2) beginDuel(level, p);
			if (nearest == null || p.distanceToSqr(this) < nearest.distanceToSqr(this)) nearest = p;
		}
		if (nearest != null) this.getLookControl().setLookAt(nearest);
	}

	public void beginDuel(ServerLevel level, ServerPlayer challenger) {
		sneakTracker.clear();
		setDormant(false);
		startFight(level);
		this.setTarget(challenger);
		setState(S_STANCE, (byte) 0);
		level.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.6F);
		announce("The duel begins", 60);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isDormant()) {
			if (source.getEntity() instanceof ServerPlayer p && !p.isCreative()) beginDuel(level, p);
			return false;
		}
		if (state() == S_PARRY && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof LivingEntity attacker) {
			// Petal Parry: the glowing blade reflects the hit back at the attacker.
			attacker.hurtServer(level, BossDamage.source(level, this), damage);
			level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.5F, 1.3F);
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 2, getZ(), 15, 0.6, 0.6, 0.6, 0.1);
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.is(DamageTypes.PLAYER_ATTACK)) {
			recentHits.addLast(this.tickCount);
			while (!recentHits.isEmpty() && this.tickCount - recentHits.peekFirst() > 40) recentHits.pollFirst();
			if (recentHits.size() >= 3 && parryCooldown <= 0 && state() == S_STANCE) {
				recentHits.clear();
				setState(S_PARRY, EV_PARRY);
				parryCooldown = 120;
				parriesDone++;
				announce("Petal Parry", 30);
			}
		}
		return hurt;
	}

	// ---- the fight -------------------------------------------------------------------------------------------------
	@Override
	protected void fightTick(ServerLevel level) {
		stateTicks++;
		if (parryCooldown > 0) parryCooldown--;
		if (comboCooldown > 0) comboCooldown--;
		if (drawCutCooldown > 0) drawCutCooldown--;
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || !insideArena(target) || this.tickCount % 100 == 0) {
			ServerPlayer pick = pickTarget(level);
			if (pick != null) target = pick;
			this.setTarget(target);
		}
		if (this.tickCount % 5 == 0) shadowRing(level);
		if (phase() >= 2) galeTick(level);
		if (phase() >= 3) stormTick(level);
		switch (state()) {
			case S_IAIDO -> iaido(level);
			case S_DASH -> dash(level);
			case S_COMBO -> combo(level, target);
			case S_PARRY -> {
				this.getNavigation().stop();
				if (stateTicks >= PARRY_TICKS) setState(S_STANCE, (byte) 0);
			}
			case S_STUNNED -> {
				this.getNavigation().stop();
				if (this.tickCount % 4 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 3.2, getZ(), 2, 0.3, 0.1, 0.3, 0);
				if (stateTicks >= STUN_TICKS) setState(S_STANCE, (byte) 0);
			}
			case S_GALE -> {
				this.getNavigation().stop();
				if (stateTicks >= 15) setState(S_STANCE, (byte) 0);
			}
			default -> stance(level, target);
		}
	}

	private void stance(ServerLevel level, LivingEntity target) {
		if (state() != S_STANCE) setState(S_STANCE, (byte) 0);
		if (target == null) return;
		this.getLookControl().setLookAt(target, 30, 30);
		double d = this.distanceTo(target);
		if (drawCutCooldown <= 0 && d < 16) {
			startDrawCut(level, target, phase() >= 3 && this.random.nextInt(3) == 0);
			return;
		}
		if (comboCooldown <= 0 && d < 3.4) {
			comboHits = 0;
			setState(S_COMBO, EV_COMBO);
			return;
		}
		if (d > 6) this.getNavigation().moveTo(target, 1.1);
		else if (d < 4) {
			this.getNavigation().stop();
			this.getMoveControl().strafe(-0.6F, (this.tickCount / 40) % 2 == 0 ? 0.4F : -0.4F);
		} else {
			this.getNavigation().stop();
			this.getMoveControl().strafe(0.0F, (this.tickCount / 50) % 2 == 0 ? 0.5F : -0.5F);
			if (comboCooldown <= 0 && this.random.nextInt(30) == 0) this.getNavigation().moveTo(target, 1.3);
		}
	}

	/** Draw Cut: sheathe, a white line flashes along the floor for 0.8 s, then he dashes along it. */
	public void startDrawCut(ServerLevel level, LivingEntity target, boolean finishing) {
		this.getNavigation().stop();
		finishingCut = finishing;
		Vec3 from = this.position();
		Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
		if (dir.lengthSqr() < 0.01) dir = this.getLookAngle().multiply(1, 0, 1);
		dir = dir.normalize();
		double length = finishing ? arenaRadius * 2.0 : Math.min(14, this.distanceTo(target) + 5);
		if (finishing && arenaCenter() != null) {
			Vec3 c = Vec3.atBottomCenterOf(arenaCenter());
			from = c.subtract(dir.scale(arenaRadius));
			this.teleportTo(from.x, this.getY(), from.z);
		}
		cutFrom = new Vec3(from.x, this.getY(), from.z);
		cutTo = cutFrom.add(dir.scale(length));
		Telegraph.line(this, level, cutFrom, cutTo, 1.6, Telegraph.WHITE, DRAW_CUT_LINE_TICKS);
		setState(S_IAIDO, EV_IAIDO);
		announce(finishing ? "Finishing Draw Cut" : "Draw Cut", 40);
		level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 1.5F, 1.6F);
	}

	private void iaido(ServerLevel level) {
		this.getNavigation().stop();
		this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
		Vec3 dir = cutTo.subtract(cutFrom).normalize();
		this.setYRot((float) (Mth.atan2(dir.z, dir.x) * (180F / Math.PI)) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		if (stateTicks == 4) level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.6, getZ(), 8, 0.4, 0.2, 0.4, 0.02);
		if (stateTicks >= DRAW_CUT_WINDUP) {
			cutHit.clear();
			setState(S_DASH, EV_DASH);
			level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.7F);
		}
	}

	private void dash(ServerLevel level) {
		int dashTicks = finishingCut ? 8 : 6;
		double t = Math.min(1.0, stateTicks / (double) dashTicks);
		Vec3 p = cutFrom.lerp(cutTo, t);
		this.teleportTo(p.x, this.getY(), p.z);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, getY() + 1.5, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CHERRY_LEAVES, p.x, getY() + 1.2, p.z, 6, 0.5, 0.5, 0.5, 0.05);
		AABB sweep = new AABB(cutFrom, cutTo).inflate(1.0, 2.5, 1.0).move(0, 0, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != this && !(e instanceof PetalMirage) && e.isAlive())) {
			if (cutHit.contains(e.getUUID()) || distanceToSegment(e.position(), cutFrom, p) > 1.6) continue;
			cutHit.add(e.getUUID());
			BossDamage.hit(level, this, e, DRAW_CUT_RAW);
		}
		if (stateTicks >= dashTicks) {
			drawCutsDone++;
			drawCutCooldown = DRAW_CUT_INTERVAL;
			comboCooldown = 20;
			setState(S_STANCE, (byte) 0);
		}
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a).multiply(1, 0, 1);
		Vec3 ap = p.subtract(a).multiply(1, 0, 1);
		double len2 = ab.lengthSqr();
		double t = len2 < 1.0E-6 ? 0 : Mth.clamp(ap.dot(ab) / len2, 0, 1);
		return ap.subtract(ab.scale(t)).length();
	}

	private void combo(ServerLevel level, LivingEntity target) {
		this.getNavigation().stop();
		if (target != null) this.getLookControl().setLookAt(target, 60, 60);
		if (stateTicks % 10 == 5 && comboHits < 3) {
			comboHits++;
			level.broadcastEntityEvent(this, EV_COMBO);
			Vec3 look = this.getLookAngle().multiply(1, 0, 1).normalize();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.0, 0.5, 3.0),
				e -> e != this && !(e instanceof PetalMirage) && e.isAlive())) {
				Vec3 to = e.position().subtract(this.position()).multiply(1, 0, 1);
				if (to.length() < 3.8 && (to.lengthSqr() < 1 || to.normalize().dot(look) > 0.2)) BossDamage.hit(level, this, e, COMBO_RAW);
			}
			level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.2F);
		}
		if (stateTicks >= 34) {
			comboCooldown = 50;
			setState(S_STANCE, (byte) 0);
		}
	}

	// ---- phase 2: Gale -----------------------------------------------------------------------------------------
	private void galeTick(ServerLevel level) {
		if (--galeCooldown > 0 || state() != S_STANCE) return;
		galeCooldown = 110;
		galesCast++;
		setState(S_GALE, EV_GALE);
		announce("Gale", 30);
		Vec3 center = arenaCenter() != null ? Vec3.atBottomCenterOf(arenaCenter()) : this.position();
		for (ServerPlayer p : fighters(level)) {
			WindCharge charge = new WindCharge(level, p.getX(), p.getY() + 0.1, p.getZ(), new Vec3(0, -1, 0));
			charge.setOwner(this);
			dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, charge);
			Vec3 out = p.position().subtract(center).multiply(1, 0, 1);
			out = out.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : out.normalize();
			p.push(out.x * 0.9, 0.35, out.z * 0.9);
		}
		level.playSound(null, blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.8F);
	}

	/** A wind bell rang somewhere: if it is on this roof and we are in phase 2+, Kazehana is stunned for 3 s. */
	public void onBellRung(ServerLevel level, BlockPos bell) {
		if (!isFighting() || phase() < 2 || arenaCenter() == null) return;
		if (!arenaBox().inflate(2).contains(Vec3.atCenterOf(bell))) return;
		int last = bellCooldown.getOrDefault(bell, -10000);
		if (this.tickCount - last < 200 || state() == S_DASH) return;
		bellCooldown.put(bell.immutable(), this.tickCount);
		stunsTaken++;
		setState(S_STUNNED, EV_STUN);
		announce("Stunned by the wind bell", 40);
		level.playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 1.0F);
	}

	// ---- phase 3: Cherry Storm -----------------------------------------------------------------------------------
	private void stormTick(ServerLevel level) {
		mirages.removeIf(id -> {
			var e = level.getEntity(id);
			return e == null || !e.isAlive();
		});
		if (--mirageCooldown > 0 || !mirages.isEmpty()) return;
		mirageCooldown = 400;
		announce("Cherry Storm", 40);
		Vec3 c = arenaCenter() != null ? Vec3.atBottomCenterOf(arenaCenter()) : this.position();
		for (int i = 0; i < 4; i++) {
			double a = Math.PI / 2 * i + 0.4;
			AreaEffectCloud cloud = new AreaEffectCloud(level, c.x + Math.cos(a) * 5, this.getY(), c.z + Math.sin(a) * 5);
			cloud.setRadius(3.0F);
			cloud.setDuration(220);
			cloud.setCustomParticle(ParticleTypes.CHERRY_LEAVES);
			dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, cloud);
		}
		for (int i = 0; i < 3; i++) {
			PetalMirage m = ModEntities.PETAL_MIRAGE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (m == null) continue;
			double a = Math.PI * 2 / 3 * i + this.random.nextDouble();
			m.snapTo(c.x + Math.cos(a) * 4, this.getY(), c.z + Math.sin(a) * 4);
			m.setOwner(this);
			dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, m);
			mirages.add(m.getUUID());
		}
		level.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x, getY() + 2, c.z, 200, arenaRadius / 2.0, 2, arenaRadius / 2.0, 0.05);
	}

	/** Only the real Kazehana has a shadow ring at his feet. */
	private void shadowRing(ServerLevel level) {
		DustParticleOptions dust = new DustParticleOptions(0x1A0F1E, 1.4F);
		for (int i = 0; i < 10; i++) {
			double a = Math.PI * 2 * i / 10;
			level.sendParticles(dust, getX() + Math.cos(a) * 0.9, getY() + 0.05, getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
		}
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		level.broadcastEntityEvent(this, EV_PHASE);
		setState(S_STANCE, (byte) 0);
		if (phase == 2 && !breezesSummoned) {
			breezesSummoned = true;
			for (int i = 0; i < 2; i++) {
				var breeze = ModEntities.BLOSSOM_BREEZE.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (breeze == null) continue;
				breeze.snapTo(getX() + (i == 0 ? 3 : -3), getY() + 0.5, getZ());
				dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, breeze);
			}
		}
		if (phase == 3) mirageCooldown = 0;
	}

	@Override
	protected void onReset(ServerLevel level) {
		setState(S_BOW, EV_BOW);
		breezesSummoned = false;
		drawCutCooldown = 80;
		discardMirages(level);
	}

	private void discardMirages(ServerLevel level) {
		for (UUID id : mirages) {
			var e = level.getEntity(id);
			if (e != null) e.discard();
		}
		mirages.clear();
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) discardMirages(level);
		super.die(source);
	}

	@Override
	protected List<ItemStack> rollDrops(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(PetalwindItems.BLOSSOM_SEAL, 2 + player.getRandom().nextInt(3)));
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("kaze_katana", () -> new ItemStack(PetalwindItems.KAZE_KATANA)),
			dev.elsewhere.boss.kit.DropRule.other("petalstep_sandals", () -> new ItemStack(PetalwindItems.PETALSTEP_SANDALS)))));
		return out;
	}

	@Override
	protected ItemStack summonItem() {
		return new ItemStack(dev.elsewhere.petalwind_glasswaste.PGStructures.BLOSSOM_SEAL_CHARM);
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case EV_BOW -> bowAnimation.start(this.tickCount);
			case EV_IAIDO -> iaidoAnimation.start(this.tickCount);
			case EV_DASH -> dashAnimation.start(this.tickCount);
			case EV_COMBO -> comboAnimation.start(this.tickCount);
			case EV_PARRY -> parryAnimation.start(this.tickCount);
			case EV_STUN -> stunAnimation.start(this.tickCount);
			case EV_PHASE -> phaseAnimation.start(this.tickCount);
			case EV_GALE -> galeAnimation.start(this.tickCount);
			default -> super.handleEntityEvent(id);
		}
	}

	public static void registerBellListener() {
		WindchimeBlock.RING_LISTENERS.add((level, pos) -> {
			for (Kazehana k : level.getEntitiesOfClass(Kazehana.class, new AABB(pos).inflate(40))) k.onBellRung(level, pos);
		});
	}

	/** Self-test hook: forces a Draw Cut at a target. */
	public void testDrawCut(ServerLevel level, LivingEntity target) {
		startDrawCut(level, target, false);
	}

	public boolean isPlayerTargetable(Player p) {
		return insideArena(p);
	}
}
