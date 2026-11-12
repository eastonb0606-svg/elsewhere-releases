package dev.elsewhere.rivals.azmar.boss;

import dev.elsewhere.duelist.DuelistEvents;
import dev.elsewhere.duelist.adapt.Formation;
import dev.elsewhere.duelist.adapt.GuardSignals;
import dev.elsewhere.duelist.adapt.LoadoutClass;
import dev.elsewhere.duelist.adapt.TacticReader;
import dev.elsewhere.duelist.boss.Behaviours;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.Move;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarFx;
import dev.elsewhere.rivals.azmar.AzmarGear;
import dev.elsewhere.rivals.azmar.AzmarItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sultana Nahira Zahr-Qadir, "the Unsetting Sun", the Scarab-Queen (RIVAL-BOSSES A1 and B1 to B3, exact). Ruler, tier 4, 225 HP
 * (RIVALS-DECISIONS 13: max health capped at 300, compensated in AzmarBoss; Mirage Step 120 x 0.77 = 92 ticks).
 * Identity card: twin khopeshes with the hook, Arc Dance, Mirage Step, Sun Elixir (the only generic: drink), Jackal Screen, Scarab Shell.
 * She reads the nearest player's loadout (10 s, or at once while a falcon marks them), counts repeated tactics (the third inside 40 s
 * triggers its counter) and commands four Jackal Guards with visible signals. Bad trades are refused, terrain is chosen. No Totem:
 * at 25 percent she rolls up for 24 ticks (invulnerable) into a gold Scarab Shell that absorbs 45 damage (20 percent of base HP) for at most 200 ticks.
 */
public final class AzmarSultana extends AzmarBoss {
	public static final int GUARDS = 4, REINFORCE_TICKS = 400, MIRAGE_COOLDOWN = 92, RIPOSTE_WINDUP = 14, SHELL_ROLL = 24, SHELL_LIFE = 200;
	public static final float RIPOSTE_RAW = 24F, SHELL_ABSORB = 45F;
	/** Arc Dance: 0.22 blocks per tick, flip every 20 to 30 ticks. */
	public static final double ARC_SPEED = 0.22;

	/** Screen and Step: 20-tick tell (banner overhead, one short horn), then the guard locks into the jackal-shield wall (B3). */
	public static final SignatureMove SCREEN_AND_STEP = new SignatureMove() {
		public String id() { return "screen_and_step"; }
		public String label() { return "Screen and Step"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 20; }
		public int cooldown() { return 100; }
		public float raw() { return 0F; }
		public double minRange() { return 4; }
		public double maxRange() { return 24; }
		public boolean canUse(HumanBoss b, LivingEntity t) {
			return b instanceof AzmarSultana s && s.signals != null && s.shellUntil == 0 && s.phaseIndex() < 2 && !s.guards.isEmpty()
				&& s.signals.ready(Formation.SCREEN, b.level().getGameTime()) && s.reader != null && s.reader.readDone(b.level().getGameTime())
				&& (s.reader.has(LoadoutClass.RANGED) || s.reader.has(LoadoutClass.MOBILE) || s.reader.has(LoadoutClass.SHIELD));
		}
		public void fire(HumanBoss b, LivingEntity t) {
			if (b instanceof AzmarSultana s && b.level() instanceof ServerLevel l) s.signals.signal(l, Formation.SCREEN, GuardSignals.Tell.BANNER_UP, t);
		}
	};

	/** Counter-Riposte: only ever started by a read counter (never scheduled). 14-tick tell, then she dodges the opener and strikes: raw 24, stagger 10 ticks. */
	public static final SignatureMove COUNTER_RIPOSTE = new SignatureMove() {
		public String id() { return "counter_riposte"; }
		public String label() { return "Counter-Riposte"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return RIPOSTE_WINDUP; }
		public int cooldown() { return 0; }
		public float raw() { return RIPOSTE_RAW; }
		public double maxRange() { return 8; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return false; }
		public Telegraph.Cue cue(HumanBoss b) { return b.style().cue(true, false); }   // both khopeshes lowered
		public void fire(HumanBoss b, LivingEntity t) {
			if (!(b instanceof AzmarSultana s) || !(b.level() instanceof ServerLevel l) || !t.isAlive()) return;
			s.hop(l, t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)));   // she dodges the opener and lands at your back
			s.startDodge(8);
			AzmarFx.goldDust(l, s.position().add(0, 1, 0), 18);
			if (s.distanceTo(t) <= 5.0 && s.hurtRaw(l, t, RIPOSTE_RAW, DamageCaps.SIGNATURE, l.damageSources().mobAttack(s))) {
				t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 4));   // 10-tick stagger
				s.ripostes++;
			}
		}
	};

	/** Halo Flare: once at 50 percent. 24-tick tell, a winged-scarab halo flaring behind her, then Blindness 40 ticks to anyone facing her within 10 blocks. */
	public static final SignatureMove HALO_FLARE = new SignatureMove() {
		public String id() { return "halo_flare"; }
		public String label() { return "Halo Flare"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.HEAVY; }
		public int windup() { return 24; }
		public int cooldown() { return 6000; }
		public float raw() { return 0F; }
		public double minRange() { return 0; }
		public double maxRange() { return 10; }
		public int minPhase() { return 1; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b.phaseIndex() == 1 && b.stat("halo_flare") == 0; }
		public void fire(HumanBoss b, LivingEntity t) {
			if (!(b.level() instanceof ServerLevel level)) return;
			for (Player p : level.getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(10))) {
				if (p.isSpectator() || p.isCreative()) continue;
				Vec3 toBoss = b.position().subtract(p.position()).normalize();
				if (p.getLookAngle().dot(toBoss) > 0.3 && p.hasLineOfSight(b)) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
			}
			AzmarFx.goldDust(level, b.position().add(0, 1.5, 0), 40);
			level.playSound(null, b.blockPosition(), dev.elsewhere.rivals.azmar.Sfx3.pick("azmar", "brass", SoundEvents.BEACON_ACTIVATE), SoundSource.HOSTILE, dev.elsewhere.rivals.azmar.Sfx3.enabled() ? 2.0F : 1.5F, dev.elsewhere.rivals.azmar.Sfx3.enabled() ? 1.0F : 1.4F);
		}
	};

	public static final DuelistProfile PROFILE = DuelistProfile.builder("azmar_sultana")
		.ruler().hp(225).walkSpeed(0.26).swing(14F, 16, 8).reach(3.0).caps(18F, 40F)
		.melee(() -> new ItemStack(AzmarItems.TWIN_KHOPESH))
		.potion(AzmarSultana::sunElixir)
		.drink(new Behaviours.Drink(0.15F, new Behaviours.HealRule(0.60F, 4.0, 1, 120)))   // Sun Elixir: one flask, 15 percent at once
		.phases(0.5, 0.25)
		.signature(SCREEN_AND_STEP).signature(COUNTER_RIPOSTE).signature(HALO_FLARE)
		.telegraph(TelegraphStyle.of("dust_color_transition", 0xD4A537, "block.beacon.activate", TelegraphStyle.Shape.HALO, 1.4F))
		.barColor(BossEvent.BossBarColor.YELLOW)
		.identity("azmar_sultana")
		.skin("kingdom/azmar/sultana").build();

	/** The Sun Elixir: a gold flask with no vanilla potion effect, so the drink heals exactly the card's 15 percent and nothing more. */
	public static ItemStack sunElixir() {
		ItemStack s = new ItemStack(Items.POTION);
		s.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(java.util.Optional.empty(), java.util.Optional.of(0xD4A537), List.of(), java.util.Optional.empty()));
		s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sun Elixir"));
		return s;
	}

	private static final net.minecraft.resources.Identifier SHELL_MOD = AzmarContent.id("azmar_scarab_shell");

	public TacticReader reader;
	public GuardSignals signals;
	public final List<KingdomSoldier> guards = new ArrayList<>();
	private KingdomSoldier hookGuard;
	private boolean started, freshStart, screenedRanged, withdrew, shellUsed;
	/** The random Mirage Step trigger is 1 in this many ticks: defensive, 40 / 1.3 (RIVALS-DECISIONS 13). */
	public static final int MIRAGE_ODDS = 31;
	private long mirageReadyAt, nextReinforce, hideUntil, keepRangeUntil, windowStart, reactAt, drinkUntil;
	/** Roll-up ends (invulnerable until), and the game tick the shell ends (0 = no shell). */
	public long shellApplyAt, shellUntil;
	private float windowHealth, windowDealt;
	public int ripostes, mirages, feints, counters, shellBroke;
	public String lastCounter = "";
	// Arc Dance state
	private int arcSide = 1;
	private long arcFlipAt;
	private final DuelistEvents.Hit hitListener;

	@Override
	protected boolean rulerTier() {
		return true;
	}

	public AzmarSultana(EntityType<? extends HumanBoss> type, Level level) {
		super(type, level, PROFILE);
		setEnabled(Move.DRINK, false);                       // the Sun Elixir is taken only after she refuses a bad trade
		hitListener = (boss, victim, amount, kind, landed) -> {
			if (boss == this && landed) windowDealt += amount;
		};
		DuelistEvents.HIT.add(hitListener);
	}

	@Override
	protected void applyLoadout() {
		if (level() instanceof ServerLevel l) {
			AzmarGear.wear(this, EquipmentSlot.HEAD, AzmarGear.dress(l, new ItemStack(AzmarItems.NEMES_OF_THE_URAEUS), "dune", "gold", -1));
			AzmarGear.wear(this, EquipmentSlot.CHEST, AzmarGear.dress(l, new ItemStack(Items.DIAMOND_CHESTPLATE), "wayfinder", "lapis", -1));
			AzmarGear.wear(this, EquipmentSlot.LEGS, AzmarGear.dress(l, new ItemStack(Items.CHAINMAIL_LEGGINGS), "dune", "gold", -1));
			AzmarGear.wear(this, EquipmentSlot.FEET, AzmarGear.dress(l, new ItemStack(Items.GOLDEN_BOOTS), "wayfinder", "lapis", -1));
		}
		super.applyLoadout();
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(AzmarItems.GILDED_KHOPESH));   // the twin: a second gilded khopesh in the off hand
		setDropChance(EquipmentSlot.MAINHAND, 0F);
		setDropChance(EquipmentSlot.OFFHAND, 0F);
	}

	// ---- movement: Arc Dance ---------------------------------------------------------------------------------------------------------

	private static double wrap(double a) {
		while (a > Math.PI) a -= Math.PI * 2;
		while (a < -Math.PI) a += Math.PI * 2;
		return a;
	}

	private boolean safeAt(Vec3 p) {
		BlockPos feet = BlockPos.containing(p.x, getY(), p.z);
		if (!level().getFluidState(feet).isEmpty()) return false;
		for (int d = 1; d <= 2; d++) if (level().getBlockState(feet.below(d)).isSolid()) return true;
		return false;
	}

	/**
	 * Arc Dance (card slot "movement"): she never circles at a fixed radius. She glides at 0.22 blocks per tick in arcs, flips side every 20 to 30 ticks
	 * toward the target's back, closing to striking range when she is beside or behind your facing and swinging wide when she is in front.
	 */
	@Override
	protected void move(LivingEntity t, double dist) {
		if (!(level() instanceof ServerLevel level)) return;
		long now = level.getGameTime();
		Vec3 rel = position().subtract(t.position()).multiply(1, 0, 1);
		double r = Math.max(0.01, rel.length());
		double ang = Math.atan2(rel.z, rel.x);
		Vec3 look = t.getLookAngle().multiply(1, 0, 1);
		double behind = look.lengthSqr() < 1e-4 ? ang : Math.atan2(-look.z, -look.x);
		if (now >= arcFlipAt) {
			arcFlipAt = now + 20 + random.nextInt(11);
			double delta = wrap(behind - ang);
			arcSide = Math.abs(delta) > 0.6 ? (delta > 0 ? 1 : -1) : (random.nextBoolean() ? 1 : -1);
		}
		double speed = ARC_SPEED;
		Vec3 dir;
		boolean screening = signals != null && signals.current() == Formation.SCREEN && !guards.isEmpty();
		if (screening) {                                    // she walks in the wall's shadow at 80 percent speed
			speed *= 0.8;
			dir = r > 6.0 ? rel.scale(-1).normalize() : Vec3.ZERO;
		} else {
			boolean strike = Math.abs(wrap(ang - behind)) < 1.2;
			double rDes = strike ? 2.5 : 4.5;
			Vec3 tangent = new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(arcSide);
			Vec3 inward = rel.scale(-1.0 / r);
			double radial = Math.max(-1.0, Math.min(1.0, (r - rDes) * 0.6));
			dir = tangent.scale(0.85).add(inward.scale(radial));
			if (dir.lengthSqr() < 1e-4) dir = tangent;
			dir = dir.normalize();
			Vec3 next = position().add(dir.scale(1.2));
			if (!safeAt(next)) {                            // never step off a ledge or into water: turn the arc
				arcSide = -arcSide;
				arcFlipAt = now + 20;
				dir = tangent.scale(-1).normalize();
				if (!safeAt(position().add(dir.scale(1.2)))) dir = Vec3.ZERO;
			}
		}
		getNavigation().stop();
		Vec3 m = getDeltaMovement();
		setDeltaMovement(dir.x * speed, m.y, dir.z * speed);
		if (horizontalCollision && onGround()) getJumpControl().jump();
		if (dir.lengthSqr() > 0 && now % 2 == 0) AzmarFx.linenTrail(level, position());
	}

	// ---- the guard -------------------------------------------------------------------------------------------------------------------

	private void spawnGuards(ServerLevel level) {
		reader = new TacticReader(this);
		signals = new GuardSignals(this);
		for (int i = 0; i < GUARDS; i++) addGuard(level, i);
		signals.setGuards(guards, hookGuard);
		nextReinforce = level.getGameTime() + REINFORCE_TICKS;
	}

	private void addGuard(ServerLevel level, int i) {
		double a = i * Math.PI / 2 + 0.4;
		Vec3 p = position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
		KingdomSoldier g = KingdomApi.spawnSoldier(level, AzmarContent.id("azmar_jackal_guard"), AzmarContent.FACTION, p, null);
		if (g == null) return;
		g.setPersistenceRequired();
		guards.add(g);
		reader.watch(g);
		if (hookGuard == null || !hookGuard.isAlive()) hookGuard = g;
	}

	private int living() {
		guards.removeIf(g -> !g.isAlive());
		if (hookGuard != null && !hookGuard.isAlive()) hookGuard = guards.isEmpty() ? null : guards.get(0);
		signals.setGuards(guards, hookGuard);
		return guards.size();
	}

	/** Mirage Step: an 8-tick i-frame scarab-shell dust step. A real step flashes gold; 20 percent are made without the flash (feints). */
	public boolean mirage(ServerLevel level, Vec3 to) {
		long now = level.getGameTime();
		long cd = phaseIndex() >= 2 ? MIRAGE_COOLDOWN / 2 : MIRAGE_COOLDOWN;   // the clean duel chains them
		if (now < mirageReadyAt || isStaggered() || inLastStand()) return false;
		boolean flash = reader == null || !reader.rollFeint(random);   // no reader yet (scripted call before first sight): always a real step
		Vec3 from = position();
		if (!hop(level, to)) return false;
		startDodge(8);
		mirageReadyAt = now + cd;
		mirages++;
		if (!flash) feints++;
		AzmarFx.mirage(level, from, flash);
		AzmarFx.mirage(level, position(), flash);
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.azmar.Sfx3.pick("azmar", "sistrum", SoundEvents.AMETHYST_BLOCK_RESONATE), SoundSource.HOSTILE, dev.elsewhere.rivals.azmar.Sfx3.enabled() ? 1.4F : 1.0F, dev.elsewhere.rivals.azmar.Sfx3.enabled() ? 1.0F : 1.6F);   // the sistrum rattle
		return true;
	}

	/** She refuses a bad trade: Mirage Step 8 blocks away, Screen, and (once, below 60 percent) the Sun Elixir becomes available for 200 ticks. */
	public void refuseBadTrade(ServerLevel level, LivingEntity target) {
		Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize().scale(8);
		mirage(level, position().add(away));
		if (signals != null) signals.signal(level, Formation.SCREEN, GuardSignals.Tell.BANNER_UP, target);
		if (healUses() < 1 && getHealth() < getMaxHealth() * 0.60F) {
			setEnabled(Move.DRINK, true);
			drinkUntil = level.getGameTime() + 200;
		}
	}

	private void runCounter(ServerLevel level, TacticReader.Counter c) {
		Player p = level.getPlayerByUUID(c.player());
		if (p == null) return;
		counters++;
		lastCounter = c.tactic().name();
		Vec3 behind = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
		switch (c.tactic()) {
			case JUMP_CRIT_OPENER -> {                      // T1: Mirage Step at the crouch; you land in front of the hook guard, she ripostes
				Vec3 front = hookGuard != null ? hookGuard.position() : position();
				mirage(level, front);
				fireSignature(COUNTER_RIPOSTE, p);
			}
			case COMBO -> fireSignature(COUNTER_RIPOSTE, p);   // T2: dodge the first hit, riposte raw 24
			case RANGED_POKE -> {                          // T3: Screen and Step 20 ticks faster, keeps 10 blocks
				signals.signal(level, Formation.SCREEN, GuardSignals.Tell.BANNER_UP, p);
				keepRangeUntil = level.getGameTime() + 400;
				Vec3 away = position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(10.0 - distanceTo(p));
				if (away.lengthSqr() > 1) mirage(level, position().add(away));
			}
			case PEARL_IN -> {                             // T4: two guards at the landing point, one thrusts on arrival
				int n = 0;
				for (KingdomSoldier g : guards) {
					if (n++ >= 2) break;
					g.setTarget(p);
					g.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 1.2);
				}
			}
			case HIGH_GROUND -> {                          // T5: she and the guard retreat out of your line of sight for 120 ticks
				hideUntil = level.getGameTime() + 120;
				signals.signal(level, Formation.WITHDRAW, GuardSignals.Tell.BANNER_LOWERED, p);
				Vec3 away = position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(14);
				mirage(level, position().add(away));
				setTarget(null);
				getNavigation().stop();
			}
			case SHIELD_TURTLE -> signals.signal(level, Formation.HOOK, GuardSignals.Tell.SHIELD_TAPS, p);   // T6: hook signal, she hits from the side
			case HEAL_RUN -> {                             // T7: the guard sprints the heal and she presses it
				for (KingdomSoldier g : guards) {
					g.setTarget(p);
					g.getNavigation().moveTo(p, 1.4);
				}
				mirage(level, behind);
			}
		}
	}

	// ---- Scarab Shell (last stand, replaces the Totem) -------------------------------------------------------------------------------

	/** The 25 percent phase: a 24-tick roll-up (invulnerable), then the gold shell takes the next 45 damage for at most 200 ticks. No heal. */
	@Override
	protected void onPhaseChange(ServerLevel level, int newPhase) {
		super.onPhaseChange(level, newPhase);
		if (newPhase < 2 || shellUsed) return;
		shellUsed = true;
		shellApplyAt = level.getGameTime() + SHELL_ROLL;
		startTelegraph("scarab_shell", "Scarab Shell", SHELL_ROLL, Telegraph.Weight.HEAVY, 0F, style().cue(true, false), true, () -> {
			AzmarFx.goldDust(level, position().add(0, 1, 0), 40);
			level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 1.4F);
		});
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (shellApplyAt > level.getGameTime() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;   // rolled up
		return super.isInvulnerableTo(level, source);
	}

	private void tickShell(ServerLevel level) {
		long now = level.getGameTime();
		if (shellApplyAt > 0 && now >= shellApplyAt && shellUntil == 0) {
			AttributeInstance cap = getAttribute(Attributes.MAX_ABSORPTION);
			if (cap != null) cap.addOrUpdateTransientModifier(new AttributeModifier(SHELL_MOD, SHELL_ABSORB, AttributeModifier.Operation.ADD_VALUE));
			setAbsorptionAmount(SHELL_ABSORB);
			shellUntil = now + SHELL_LIFE;
			shellApplyAt = 0;
			if (signals != null) signals.signal(level, Formation.WITHDRAW, GuardSignals.Tell.BANNER_LOWERED, null);
		} else if (shellUntil > 0 && (now >= shellUntil || getAbsorptionAmount() <= 0F)) {
			if (getAbsorptionAmount() <= 0F) shellBroke++;
			setAbsorptionAmount(0F);
			AttributeInstance cap = getAttribute(Attributes.MAX_ABSORPTION);
			if (cap != null) cap.removeModifier(SHELL_MOD);
			shellUntil = -1;                                // spent for good (-1: no shell now, and never again)
			AzmarFx.goldDust(level, position().add(0, 1, 0), 30);
			level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F);
		}
	}

	// ---- the brain -------------------------------------------------------------------------------------------------------------------

	@Override
	protected void extraTick(ServerLevel level, LivingEntity target) {
		long now = level.getGameTime();
		if (!started) {
			started = true;
			freshStart = true;
			spawnGuards(level);
			windowStart = now;
			windowHealth = getHealth();
		}
		tickShell(level);
		if (drinkUntil > 0 && (now >= drinkUntil || healUses() >= 1)) {
			drinkUntil = 0;
			setEnabled(Move.DRINK, false);
		}
		if (hideUntil > now) {                              // waiting out of sight
			setTarget(null);
			getNavigation().stop();
			signals.tick(level);
			return;
		}
		Player tp = target instanceof Player p ? p : null;
		// RIVALS-DECISIONS 17: state that changes rarely is refreshed on a 10/20-tick cadence, never polled per tick
		if (now % 10 == 3 || freshStart) {
			if (tp != null) reader.setMarked(isMarked(tp.getUUID(), now));
			living();
		}
		if (now % 20 == 5 || freshStart) signals.screenCooldownOverride = alive(level, AzmarBosses.WAZIR, position()) ? 240 : -1;   // 160-block scan
		freshStart = false;
		reader.tick(level, tp);
		int phaseIdx = phaseIndex();
		if (phaseIdx >= 2) {                                // the Unsetting Sun: Withdraw, no adaptation, a clean duel with Mirage chains
			if (!withdrew) {
				withdrew = true;
				signals.signal(level, Formation.WITHDRAW, GuardSignals.Tell.BANNER_LOWERED, target);
				for (KingdomSoldier g : guards) { g.setTarget(null); g.getNavigation().moveTo(getX() + (random.nextDouble() * 20 - 10), getY(), getZ() + (random.nextDouble() * 20 - 10), 1.0); }
			}
			signals.tick(level);
			if (target != null && idle() && distanceTo(target) > 4 && now >= mirageReadyAt && random.nextInt(MIRAGE_ODDS) == 0) mirage(level, target.position().add(target.getLookAngle().multiply(-1, 0, -1).normalize().scale(1.8)));
			return;
		}
		if (target != null && now % 10 == 0) for (KingdomSoldier g : guards) if (g.getTarget() == null || !g.getTarget().isAlive()) g.setTarget(target);
		signals.tick(level);
		if (target == null) return;
		// first ranged weapon or pearl: Screen and Step at once
		if (tp != null && !screenedRanged && idle() && reader.readDone(now) && (reader.has(LoadoutClass.RANGED) || reader.has(LoadoutClass.MOBILE))) {
			screenedRanged = signals.ready(Formation.SCREEN, now);
			if (screenedRanged) fireSignature(SCREEN_AND_STEP, tp);
		}
		// ranged class: a full draw is dodged after the 6-tick reaction (Mirage Step, once per 120 ticks)
		if (tp != null && reader.readDone(now) && reader.has(LoadoutClass.RANGED) && reactAt == 0 && now >= mirageReadyAt && tp.isUsingItem()
			&& (tp.getUseItem().is(Items.BOW) || tp.getUseItem().is(Items.CROSSBOW)) && tp.getTicksUsingItem() >= 20) reactAt = now + 6;
		if (reactAt > 0 && now >= reactAt) {
			reactAt = 0;
			if (tp != null && idle()) {
				Vec3 line = position().subtract(tp.position()).multiply(1, 0, 1);
				if (line.lengthSqr() > 1e-4) mirage(level, position().add(new Vec3(-line.z, 0, line.x).normalize().scale(random.nextBoolean() ? 5 : -5)));
			}
		}
		// phase 2: reinforce the guard, 1 per 400 ticks up to 4
		if (phaseIdx >= 1 && guards.size() < GUARDS && now >= nextReinforce) {
			addGuard(level, guards.size());
			signals.setGuards(guards, hookGuard);
			nextReinforce = now + REINFORCE_TICKS;
		}
		// halo tell: the winged-scarab halo is drawn behind her while the move winds up
		if ("halo_flare".equals(action())) AzmarFx.wingedHalo(level, position().add(0, 0.2, 0).subtract(Vec3.directionFromRotation(0, yBodyRot).scale(0.6)), yBodyRot, 1.0);
		if (tp != null && idle() && reader.readDone(now) && !reader.satisfied()) {
			TacticReader.Counter c = reader.pollCounter();
			if (c != null) runCounter(level, c);
		}
		// bad trade: more than 12 percent of her HP lost for less than 4 percent dealt inside 120 ticks, she refuses it
		if (now - windowStart >= 120) {
			float lost = windowHealth - getHealth();
			if (lost > getMaxHealth() * 0.12F && windowDealt < getMaxHealth() * 0.04F && idle()) refuseBadTrade(level, target);
			windowStart = now;
			windowHealth = getHealth();
			windowDealt = 0F;
		}
		// terrain: never fight in deep water
		if (isInWater() && random.nextInt(20) == 0) mirage(level, position().add(random.nextDouble() * 12 - 6, 0, random.nextDouble() * 12 - 6));
		if (keepRangeUntil > now && distanceTo(target) < 8.0 && idle() && random.nextInt(40) == 0) {
			Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(4);
			mirage(level, position().add(away));
		}
	}

	@Override
	public void die(DamageSource source) {
		DuelistEvents.HIT.remove(hitListener);
		if (reader != null) reader.close();
		if (level() instanceof ServerLevel) for (KingdomSoldier g : guards) if (g.isAlive()) g.discard();   // the guard dissolves with its queen
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		DuelistEvents.HIT.remove(hitListener);
		if (reader != null) reader.close();
		if (reason.shouldDestroy()) for (KingdomSoldier g : guards) if (g.isAlive()) g.discard();
		super.remove(reason);
	}
}
