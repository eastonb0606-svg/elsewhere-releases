package dev.elsewhere.rivals.azmar.boss;

import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import dev.elsewhere.rivals.azmar.AzmarFx;
import dev.elsewhere.rivals.azmar.AzmarGear;
import dev.elsewhere.rivals.azmar.AzmarItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Amir Tariq Vashti, Amir of Spears, the Horned Scarab (RIVAL-BOSSES A4, identity card azmar_amir). Lord, tier 3, 182 HP (hard cap 300) on a war camel
 * (50 HP, armour 4, gold-and-lapis caparison, its own bar). The one conventional, mounted fight: opts into no generic behaviour.
 * Movement: Mounted Lance Arcs (14-block passes at 0.50 blocks per tick, turn radius 6, never still over 10 ticks; on foot 0.30, +10 percent).
 * Camel Charge (raw 30, kb 2), Javelin Volley (5 javelins, two per volley, three in P2 or on foot), Dismount Lunge (raw 26).
 * Last stand: Final Charge at 20 percent, once (24-tick wind-up, 18 blocks, raw 34, kb 3, camel Exhausted 200 ticks; on foot a raw-34 lunge from 8 blocks).
 */
public final class AzmarAmir extends AzmarBoss {
	public static final SignatureMove CAMEL_CHARGE = new SignatureMove() {
		public String id() { return "camel_charge"; }
		public String label() { return "Camel Charge"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.HEAVY; }
		public int windup() { return 20; }
		public int cooldown() { return 200; }
		public float raw() { return 30F; }
		public double minRange() { return 6; }
		public double maxRange() { return 26; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarAmir a && a.mounted() && a.chargeLeft == 0 && a.recover == 0 && !a.finalPending; }
		public void begin(HumanBoss b, LivingEntity t) { if (b instanceof AzmarAmir a) a.chargeDir = t.position().subtract(b.position()).multiply(1, 0, 1).normalize(); }
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarAmir a) a.startCharge(14, 20, 30F, 2.0F); }
	};

	public static final SignatureMove JAVELINS = new SignatureMove() {
		public String id() { return "javelin_volley"; }
		public String label() { return "Javelin Volley"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 16; }
		public int cooldown() { return 180; }
		public float raw() { return 18F; }
		public double minRange() { return 6; }
		public double maxRange() { return 16; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarAmir a && a.javelinsLeft > 0 && a.chargeLeft == 0 && !a.finalPending; }
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarAmir a && b.level() instanceof ServerLevel l) a.javelins(l, t); }
	};

	public static final SignatureMove LUNGE = new SignatureMove() {
		public String id() { return "dismount_lunge"; }
		public String label() { return "Dismount Lunge"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 14; }
		public int cooldown() { return 140; }
		public float raw() { return 26F; }
		public double minRange() { return 2.5; }
		public double maxRange() { return 6; }
		public Telegraph.Cue cue(HumanBoss b) { return b.style().cue(true, false); }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarAmir a && !a.mounted() && !a.finalPending && a.recover == 0; }
		public void begin(HumanBoss b, LivingEntity t) { if (b instanceof AzmarAmir a) a.chargeDir = t.position().subtract(b.position()).multiply(1, 0, 1).normalize(); }
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarAmir a && b.level() instanceof ServerLevel l) a.lunge(l, t, 26F, 3.8, 1.1); }
	};

	/** The card's telegraph: carnelian sand plume, the camel's dash. */
	public static final TelegraphStyle STYLE = TelegraphStyle.of("dust", 0xB0362B, "entity.camel.dash", TelegraphStyle.Shape.RING, 1.0F);

	public static final DuelistProfile PROFILE = DuelistProfile.builder("azmar_amir")
		.hp(182).walkSpeed(0.28).swing(18F, 24, 10).reach(4.0)
		.melee(() -> new ItemStack(AzmarItems.SCARAB_SPEAR))
		.phases(0.5, 0.2)
		.caps(18F, 38F)
		.telegraph(STYLE)
		.signature(CAMEL_CHARGE).signature(JAVELINS).signature(LUNGE)
		.barColor(BossEvent.BossBarColor.RED)
		.skin("kingdom/azmar/amir").build();

	public static final int UNSEAT_STUN = 30, EXHAUST_TICKS = 200, FINAL_WINDUP = 24, RECOVERY = 28;
	public static final double MOUNTED_SPEED = 0.50, FOOT_SPEED = 0.30, TURN_RADIUS = 6.0, PASS_LENGTH = 14.0;
	public Vec3 chargeDir = Vec3.ZERO;
	int chargeLeft, recover;
	private double chargeSpeed, chargeKb;
	private float chargeRaw;
	boolean finalPending;
	private boolean finalUsed, finalMounted;
	private long exhaustedUntil;
	private Camel camel;
	private boolean started, unseated;
	// lance arcs
	private Vec3 heading;
	private boolean locked;
	private double lockedLeft;
	private int side = 1, stuck;
	private Vec3 lastPos = Vec3.ZERO;
	public int charges, volleys, lunges, charged, javelinsLeft = 5, javelinsThrown, finals, passes, lastVolley, stillTicks;
	public float lastChargeRaw;
	private long nextJavelin;
	private final net.minecraft.server.level.ServerBossEvent camelBar = new net.minecraft.server.level.ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("War Camel"),
		BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

	public AzmarAmir(EntityType<? extends HumanBoss> type, Level level) {
		super(type, level, PROFILE);
		phase.onEnter(2, () -> finalPending = true);
	}

	public boolean mounted() {
		return camel != null && camel.isAlive() && getVehicle() == camel;
	}

	public Camel camel() {
		return camel;
	}

	public boolean exhausted() {
		return level().getGameTime() < exhaustedUntil;
	}

	/** True while charging or recovering from a charge (tests). */
	public boolean chargeLeftForTest() {
		return chargeLeft > 0 || recover > 0;
	}

	public String debug() {
		return "pending=" + finalPending + " used=" + finalUsed + " idle=" + idle() + " recover=" + recover + " charge=" + chargeLeft + " phase=" + phaseIndex() + " hp=" + getHealth() + "/" + getMaxHealth() + " passive=" + passive + " mounted=" + mounted() + " act=" + action() + " tgt=" + (getTarget() != null);
	}

	public boolean finalUsed() {
		return finalUsed;
	}

	@Override
	protected void applyLoadout() {
		if (level() instanceof ServerLevel l) {
			AzmarGear.wear(this, EquipmentSlot.HEAD, AzmarGear.dress(l, new ItemStack(AzmarItems.HORNED_SCARAB_CROWN), "dune", "copper", -1));
			AzmarGear.wear(this, EquipmentSlot.CHEST, AzmarGear.dress(l, new ItemStack(Items.IRON_CHESTPLATE), "wayfinder", "gold", -1));
			AzmarGear.wear(this, EquipmentSlot.LEGS, AzmarGear.dress(l, new ItemStack(Items.CHAINMAIL_LEGGINGS), "dune", "gold", -1));
			AzmarGear.wear(this, EquipmentSlot.FEET, AzmarGear.dress(l, new ItemStack(Items.IRON_BOOTS), "wayfinder", "lapis", -1));
		}
		super.applyLoadout();
		setDropChance(EquipmentSlot.MAINHAND, 0F);
	}

	private void mount(ServerLevel level) {
		Camel c = EntityTypes.CAMEL.create(level, EntitySpawnReason.COMMAND);
		if (c == null) return;
		c.snapTo(getX(), getY(), getZ(), getYRot(), 0F);
		c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0);
		c.setHealth(50.0F);
		c.getAttribute(Attributes.ARMOR).setBaseValue(4.0);
		c.setItemSlot(EquipmentSlot.SADDLE, dev.elsewhere.rivals.azmar.look.AzmarRulerLook.caparison());   // K29: the visible gold-and-lapis caparison (camel_saddle layer)
		c.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));         // golden barding: armour value only (a camel draws no body armour)
		c.setCustomName(Component.literal("War Camel"));
		c.addTag(dev.elsewhere.rivals.azmar.AzmarSoldiers.ALLY_TAG);
		c.setPersistenceRequired();
		level.addFreshEntity(c);
		startRiding(c, true, false);
		camel = c;
	}

	// ---- charges and lunges ---------------------------------------------------------------------------------------------------

	/** A straight dash: {@code blocks} over {@code ticks}; the camel lowers its head first (the wind-up already ran). Stops at a pit or a gap. */
	void startCharge(int blocks, int ticks, float raw, float kb) {
		if (!mounted()) return;
		chargeLeft = ticks;
		chargeSpeed = (double) blocks / ticks;
		chargeRaw = raw;
		chargeKb = kb;
		lastChargeRaw = raw;
		charges++;
		if (level() instanceof ServerLevel l) l.playSound(null, blockPosition(), SoundEvents.CAMEL_DASH, SoundSource.HOSTILE, 1.5F, 1.0F);
	}

	private void tickCharge(ServerLevel level, LivingEntity target) {
		chargeLeft--;
		double mul = exhausted() ? 0.7 : 1.0;
		if (!groundAhead(camel, chargeDir, 1.8)) {                                         // a pit or a boat-width gap stops the camel
			chargeLeft = 0;
			recover = RECOVERY;
			camel.setDeltaMovement(0, camel.getDeltaMovement().y, 0);
			return;
		}
		Vec3 cur = camel.getDeltaMovement();
		camel.setDeltaMovement(chargeDir.x * chargeSpeed * mul, cur.y, chargeDir.z * chargeSpeed * mul);
		faceDir(camel, chargeDir);
		camel.needsSync = true;
		level.sendParticles(AzmarFx.GOLD_DUST, camel.getX(), camel.getY() + 0.3, camel.getZ(), 3, 0.3, 0.1, 0.3, 0.02);
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xB0362B, 1.3F), camel.getX(), camel.getY() + 0.2, camel.getZ(), 5, 0.4, 0.1, 0.4, 0.02);
		if (target != null && distanceTo(target) <= 3.2) {
			if (hurtRaw(level, target, chargeRaw, DamageCaps.SIGNATURE, level.damageSources().mobAttack(this))) {
				Vec3 kb = target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.7 * chargeKb);
				target.setDeltaMovement(target.getDeltaMovement().add(kb.x, 0.3, kb.z));
				target.needsSync = true;
				charged++;
			}
			chargeLeft = 0;
		}
		if (chargeLeft <= 0) recover = RECOVERY;
	}

	void javelins(ServerLevel level, LivingEntity t) {
		volleys++;
		int n = Math.min(javelinsLeft, (phaseIndex() >= 1 || !mounted()) ? 3 : 2);
		lastVolley = n;
		javelinsLeft -= n;
		for (int i = 0; i < n; i++) {
			javelinsThrown++;
			dev.elsewhere.duelist.kit.Timers.after(level.getServer(), i * 8, () -> {
				if (isAlive() && t.isAlive()) DuelistProjectile.throwAt(level, this, t, new ItemStack(Items.COPPER_SPEAR), 18F, 1.5F);
			});
		}
	}

	/** A leaping lunge: the hit lands 6 ticks after the take-off if the target is within {@code reach}. */
	void lunge(ServerLevel level, LivingEntity t, float raw, double reach, double speed) {
		lunges++;
		lastChargeRaw = raw;
		Vec3 d = t.position().subtract(position()).multiply(1, 0, 1).normalize();
		setDeltaMovement(d.x * speed, 0.4, d.z * speed);
		needsSync = true;
		dev.elsewhere.duelist.kit.Timers.after(level.getServer(), 6, () -> {
			if (isAlive() && t.isAlive() && distanceTo(t) <= reach) hurtRaw(level, t, raw, DamageCaps.SIGNATURE, level.damageSources().mobAttack(this));
		});
		recover = 20;                                                                       // open 20 ticks
	}

	/** Final Charge: 24-tick wind-up, then 18 blocks at raw 34 and knockback 3 (camel Exhausted afterwards); on foot a raw-34 lunge from 8 blocks. */
	private void beginFinal(ServerLevel level, LivingEntity target) {
		finalPending = false;
		finalUsed = true;
		finals++;
		boolean mountedNow = mounted();
		finalMounted = mountedNow;
		chargeDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
		if (camel != null) camel.setDeltaMovement(0, camel.getDeltaMovement().y, 0);
		level.playSound(null, blockPosition(), SoundEvents.CAMEL_DASH, SoundSource.HOSTILE, 1.4F, 0.7F);
		startTelegraph("final_charge", "Final Charge", FINAL_WINDUP, Telegraph.Weight.HEAVY, 34F, style().cue(true, false), true, () -> {
			if (!isAlive()) return;
			if (mounted()) {
				startCharge(18, 20, 34F, 3.0F);
				exhaustedUntil = level.getGameTime() + EXHAUST_TICKS + 20;
			} else if (target.isAlive()) {
				chargeDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
				lunge(level, target, 34F, 8.5, 1.3);
			}
		});
	}

	// ---- Mounted Lance Arcs ---------------------------------------------------------------------------------------------------

	/** Walks forward over solid ground? (solid block within 3 below the point {@code dist} ahead). */
	private boolean groundAhead(net.minecraft.world.entity.Entity e, Vec3 dir, double dist) {
		if (!(level() instanceof ServerLevel l)) return true;
		net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(e.getX() + dir.x * dist, e.getY(), e.getZ() + dir.z * dist);
		for (int dy = 0; dy <= 3; dy++) {
			net.minecraft.core.BlockPos q = p.below(dy);
			if (!l.getBlockState(q).getCollisionShape(l, q).isEmpty() || !l.getFluidState(q).isEmpty()) return true;
		}
		return false;
	}

	private static void faceDir(net.minecraft.world.entity.Entity e, Vec3 d) {
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		e.setYRot(yaw);
		if (e instanceof LivingEntity le) {
			le.yBodyRot = yaw;
			le.setYHeadRot(yaw);
		}
	}

	/** Movement slot: the Amir rides long passes through the fight at 0.50 blocks per tick (camel, turn radius 6), or runs them on foot at 0.30 plus 10 percent. */
	@Override
	protected void move(LivingEntity t, double dist) {
		if (!(level() instanceof ServerLevel level)) return;
		if (chargeLeft > 0 && mounted()) {
			tickCharge(level, t);
			return;
		}
		if (recover > 0) {
			if (mounted()) camel.setDeltaMovement(0, camel.getDeltaMovement().y, 0);
			return;
		}
		net.minecraft.world.entity.Entity e = mounted() ? camel : this;
		double speed = mounted() ? MOUNTED_SPEED : FOOT_SPEED * 1.10;
		if (mounted() && exhausted()) speed *= 0.7;
		if (heading == null) heading = Vec3.directionFromRotation(0, e.getYRot()).multiply(1, 0, 1).normalize();
		Vec3 ep = e.position(), tp = t.position();
		Vec3 aim;
		if (locked) {                                                                         // the pass goes on through the fight
			aim = ep.add(heading.scale(6));
			lockedLeft -= speed;
			if (lockedLeft <= 0 || !groundAhead(e, heading, 2.0)) {
				locked = false;
				side = -side;
			}
		} else {
			Vec3 to = tp.subtract(ep).multiply(1, 0, 1);
			double d = to.length();
			Vec3 dir = d < 1.0E-4 ? heading : to.scale(1 / d);
			Vec3 perp = new Vec3(-dir.z, 0, dir.x);
			double off = d > 8 ? Math.min(5.0, (d - 8) * 0.6 + 2) * side : 0;               // arc in from the side, converge to a pass
			aim = tp.add(perp.scale(off));
			if (d < 5.0 && !to.equals(Vec3.ZERO)) {
				locked = true;
				lockedLeft = PASS_LENGTH;
				passes++;
			}
		}
		Vec3 want = aim.subtract(ep).multiply(1, 0, 1);
		if (want.lengthSqr() < 1.0E-4) want = heading;
		double turn = speed / TURN_RADIUS;                                                    // radians per tick for the camel's turn radius
		double ang = Math.atan2(heading.z, heading.x), des = Math.atan2(want.z, want.x);
		double diff = des - ang;
		while (diff > Math.PI) diff -= 2 * Math.PI;
		while (diff < -Math.PI) diff += 2 * Math.PI;
		ang += Math.max(-turn, Math.min(turn, diff));
		heading = new Vec3(Math.cos(ang), 0, Math.sin(ang));
		if (!groundAhead(e, heading, 1.6)) {                                                  // never gallop off an edge: turn away
			heading = heading.scale(-1);
			locked = false;
		}
		if (e.position().distanceToSqr(lastPos) < 0.02) stuck++;
		else stuck = 0;
		lastPos = e.position();
		if (stuck >= 6) {                                                                     // a hill or a wall: slide along it
			stuck = 0;
			double a2 = Math.atan2(heading.z, heading.x) + side * Math.PI / 2;
			heading = new Vec3(Math.cos(a2), 0, Math.sin(a2));
			locked = true;
			lockedLeft = 4;
			side = -side;
		}
		Vec3 cur = e.getDeltaMovement();
		double dy = cur.y;
		if (e.horizontalCollision && e.onGround()) dy = 0.5;
		e.setDeltaMovement(heading.x * speed, dy, heading.z * speed);
		faceDir(e, heading);
		e.needsSync = true;
		if (e.getDeltaMovement().horizontalDistanceSqr() < 0.0004) stillTicks++;
		else stillTicks = 0;
	}

	/** Runs every AI step even while scripted (passive): mounting, the camel's death, the step-down and the Final Charge. */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		LivingEntity t = getTarget();
		if (t != null && !t.isAlive()) t = null;
		tickState(level, t);
		super.customServerAiStep(level);
	}

	@Override
	protected void extraTick(ServerLevel level, LivingEntity target) {}

	private void tickState(ServerLevel level, LivingEntity target) {
		if (!started) {
			started = true;
			mount(level);
		}
		long now = level.getGameTime();
		if (chargeLeft > 0 && !mounted()) chargeLeft = 0;                                   // the camel is gone: no dash
		if (recover > 0 && chargeLeft == 0) recover--;
		if (javelinsLeft >= 5) nextJavelin = now + 120;
		else if (now >= nextJavelin) {
			javelinsLeft++;
			nextJavelin = now + 120;                                                          // a javelin picked up every 6 s
		}
		if (camel != null && camel.isAlive()) camelBar.setProgress(Math.max(0F, camel.getHealth() / camel.getMaxHealth()));
		if (!unseated && camel != null && (!camel.isAlive() || getVehicle() != camel) && !(phaseIndex() >= 1 && camel.isAlive())) {   // the camel died: unseated, stunned 30 ticks, on foot
			unseated = true;
			heading = null;
			locked = false;
			if (getVehicle() != null) stopRiding();
			stun(level, UNSEAT_STUN);
			addEffect(new MobEffectInstance(MobEffects.SLOWNESS, UNSEAT_STUN, 6));
			level.playSound(null, blockPosition(), SoundEvents.CAMEL_DEATH, SoundSource.HOSTILE, 1.0F, 0.9F);
		}
		if (phaseIndex() >= 1 && mounted() && !unseated && !finalPending && !finalUsed) {                                    // 50 percent HP and the camel still lives: he steps down
			stopRiding();
			unseated = true;
			heading = null;
			locked = false;
		}
		if (finalPending && target != null && idle() && chargeLeft == 0 && recover == 0) beginFinal(level, target);
	}

	private long stunUntil;

	public boolean stunned() {
		return level().getGameTime() < stunUntil;
	}

	private void stun(ServerLevel level, int ticks) {
		boolean was = passive;
		stunUntil = level.getGameTime() + ticks;
		passive = true;
		dev.elsewhere.duelist.kit.Timers.after(level.getServer(), ticks, () -> passive = was);
	}

	@Override
	public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer p) {
		super.startSeenByPlayer(p);
		camelBar.addPlayer(p);
	}

	@Override
	public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer p) {
		super.stopSeenByPlayer(p);
		camelBar.removePlayer(p);
	}

	@Override
	public void die(net.minecraft.world.damagesource.DamageSource source) {
		camelBar.removeAllPlayers();
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		camelBar.removeAllPlayers();
		if (reason.shouldDestroy() && camel != null && camel.isAlive()) camel.discard();
		super.remove(reason);
	}
}
