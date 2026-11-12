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
import dev.elsewhere.duelist.kit.GroundPatch;
import dev.elsewhere.duelist.kit.Kit;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarFx;
import dev.elsewhere.rivals.azmar.AzmarGear;
import dev.elsewhere.rivals.azmar.AzmarItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Grand Wazir Idris Halvane, Keeper of the Ibis Glyphs (RIVAL-BOSSES A2 and B1, B2, B4; identity card azmar_wazir). Lord, tier 3, 161 HP (hard cap 300, decisions 13).
 * Glyph Pacing: he stays 8 to 12 blocks behind the Hands (2 Naffatun and 1 Sand-Dancer), pacing a 3-block line while he draws glyph circles,
 * and moves only to keep line of sight, to re-form or to veil. Counter-Brew picks a bottle from the B4 score table and draws a 2-block glyph circle
 * 16 ticks before the splash (a shield does not stop it, leaving the circle does). Glyph Flare orders the Hands (amber Screen, blue Ring,
 * red Charge, white Withdraw; they obey 40 ticks later). Veil of the Ibis: invisible 60 ticks, then an 8-tick Speed II flank dash.
 * Last stand, Seal of the Ibis, on entering the 25 percent phase. Opts into the kit's {@code drink} only (Ibis Draught).
 */
public final class AzmarWazir extends AzmarBoss {
	public enum Bottle {
		SLOWNESS("slowness", AzmarFx.LAPIS, false), WEAKNESS("weakness", AzmarFx.AMBER, false), HARMING("harming", AzmarFx.CARNELIAN, false),
		POISON("poison", AzmarFx.GREEN, false), SLOW_CLOUD("slow_cloud", AzmarFx.LAPIS, true), MIST("poison_mist", AzmarFx.GREEN, true);
		public final String id;
		public final int color;
		public final boolean cloud;

		Bottle(String id, int color, boolean cloud) {
			this.id = id;
			this.color = color;
			this.cloud = cloud;
		}
	}

	public enum Flare {
		AMBER(Formation.SCREEN, GuardSignals.Tell.FLARE_AMBER, AzmarFx.AMBER), BLUE(Formation.RING, GuardSignals.Tell.FLARE_BLUE, AzmarFx.LAPIS),
		RED(null, GuardSignals.Tell.FLARE_RED, AzmarFx.CARNELIAN), WHITE(Formation.WITHDRAW, GuardSignals.Tell.FLARE_WHITE, AzmarFx.LINEN);
		public final Formation formation;
		public final GuardSignals.Tell tell;
		public final int color;

		Flare(Formation f, GuardSignals.Tell t, int c) {
			formation = f;
			tell = t;
			color = c;
		}
	}

	public static final SignatureMove COUNTER_BREW = new SignatureMove() {
		public String id() { return "counter_brew"; }
		public String label() { return "Counter-Brew"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 16; }
		public int cooldown() { return 60; }
		public float raw() { return 16F; }
		public double minRange() { return 2; }
		public double maxRange() { return 16; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarWazir w && w.canThrow(b.level().getGameTime()); }
		public void begin(HumanBoss b, LivingEntity t) { if (b instanceof AzmarWazir w && b.level() instanceof ServerLevel l) w.beginBrew(l, t); }
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarWazir w && b.level() instanceof ServerLevel l) w.throwBrew(l); }
	};

	/** A coloured ibis glyph thrown up in smoke: 10 ticks to throw, the Hands obey 40 ticks later. */
	public static final SignatureMove GLYPH_FLARE = new SignatureMove() {
		public String id() { return "glyph_flare"; }
		public String label() { return "Glyph Flare"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.ORDINARY; }
		public int windup() { return 10; }
		public int cooldown() { return 140; }
		public float raw() { return 0F; }
		public double maxRange() { return 40; }
		public int priority() { return 12; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarWazir w && w.nextFlare(t) != null; }
		public void begin(HumanBoss b, LivingEntity t) { if (b instanceof AzmarWazir w) w.chosenFlare = w.nextFlare(t); }
		public void fire(HumanBoss b, LivingEntity t) {
			if (b instanceof AzmarWazir w && b.level() instanceof ServerLevel l && w.chosenFlare != null) w.flare(l, w.chosenFlare, t);
		}
	};

	public static final SignatureMove VEIL = new SignatureMove() {
		public String id() { return "veil_of_the_ibis"; }
		public String label() { return "Veil of the Ibis"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 16; }
		public int cooldown() { return 230; }
		public float raw() { return 0F; }
		public double minRange() { return 5; }
		public double maxRange() { return 20; }
		public int priority() { return 5; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarWazir w && !w.veiled(); }
		public void begin(HumanBoss b, LivingEntity t) {
			if (b.level() instanceof ServerLevel l) AzmarFx.glyphCircle(l, b.position(), 1.2, AzmarFx.TURQUOISE);   // he vanishes behind a glyph
		}
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarWazir w && b.level() instanceof ServerLevel l) w.veil(l); }
	};

	public static final DuelistProfile PROFILE = DuelistProfile.builder("azmar_wazir")
		.hp(161).walkSpeed(0.30).swing(12F, 20, 8).reach(3.0)
		.melee(() -> new ItemStack(AzmarItems.GILDED_KHOPESH))
		.potion(HumanBoss::healingPotion)
		.drink(new Behaviours.Drink(0.15F, new Behaviours.HealRule(0.6F, 3.0, 2, 120)))   // Ibis Draught: 2 potions, 32 ticks (kit), 15 percent at once, any hit cancels (120-tick lockout)
		.phases(0.5, 0.25)
		.caps(18F, 38F)
		.signature(COUNTER_BREW).signature(GLYPH_FLARE).signature(VEIL)
		.telegraph(TelegraphStyle.of("glow", 0x2BB3A3, "block.enchantment_table.use", TelegraphStyle.Shape.RING, 1.0F))
		.barColor(BossEvent.BossBarColor.BLUE)
		.skin("kingdom/azmar/wazir").build();

	public static final int HANDS = 3, SEAL_WRITE = 30, SEAL_TICKS = 200;
	public static final double SEAL_RADIUS = 6.0;
	public static final float SEAL_ABSORB = 16F;
	private static final net.minecraft.resources.Identifier SEAL_CAP = AzmarContent.id("azmar_wazir_seal_absorb");

	public TacticReader reader;
	public GuardSignals signals;
	public final List<KingdomSoldier> hands = new ArrayList<>();
	private final Deque<Long> thrown = new ArrayDeque<>();
	private final List<Bottle> history = new ArrayList<>();
	private final long[] lastThrown = new long[Bottle.values().length];
	private final Deque<Long> hitTicks = new ArrayDeque<>(), jumpTicks = new ArrayDeque<>();
	private final Map<UUID, Integer> handHurt = new HashMap<>();
	private Bottle pending, queued;
	private Vec3 pendingAt = Vec3.ZERO;
	private Flare chosenFlare;
	private boolean started, reformed, wasOnGround = true;
	/** Hands Reform repeat interval: a defensive back-step, 300 / 1.3 (RIVALS-DECISIONS 13). */
	public static final int REFORM_TICKS = Math.round(300 * AzmarBoss.DEFENCE_SCALE);
	private long windowStart, reformAt, redUntil, redStartAt, backstepAt, veilAt, reformUntil, stillSince = -1, sealWriteAt, sealUntil;
	private float windowHealth, windowDealt;
	private int dashLeft, cleansed;
	private Vec3 dashTo = Vec3.ZERO, reformPoint = Vec3.ZERO, sealCentre = Vec3.ZERO;
	private Vec3 paceCentre, paceAxis = new Vec3(1, 0, 0);
	private int paceSide = 1;
	private double paceAngle;
	private long paceFlipAt;
	private final Set<UUID> sealed = new HashSet<>();
	public int brews, flares, veils, counters, reforms, refusals, seals;
	public Bottle lastBottle;
	public Flare lastFlare;
	private final DuelistEvents.Hit hitListener;

	public AzmarWazir(EntityType<? extends HumanBoss> type, Level level) {
		super(type, level, PROFILE);
		hitListener = (boss, victim, amount, kind, landed) -> {
			if (boss == this && landed) windowDealt += amount;
		};
		DuelistEvents.HIT.add(hitListener);
	}

	@Override
	protected void applyLoadout() {
		if (level() instanceof ServerLevel l) {
			AzmarGear.wear(this, EquipmentSlot.HEAD, AzmarGear.dress(l, new ItemStack(AzmarItems.IBIS_CREST), "dune", "gold", -1));
			AzmarGear.wear(this, EquipmentSlot.CHEST, AzmarGear.dress(l, new ItemStack(Items.LEATHER_CHESTPLATE), "wayfinder", "gold", 0x1F3F8F));
			AzmarGear.wear(this, EquipmentSlot.LEGS, AzmarGear.dress(l, new ItemStack(Items.LEATHER_LEGGINGS), "dune", "gold", 0x1F3F8F));
			AzmarGear.wear(this, EquipmentSlot.FEET, AzmarGear.dress(l, new ItemStack(Items.LEATHER_BOOTS), "wayfinder", "gold", 0xEDE3CC));
		}
		super.applyLoadout();
		setDropChance(EquipmentSlot.MAINHAND, 0F);
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPLASH_POTION));   // the faience satchel's bottle, raised for Counter-Brew
		setDropChance(EquipmentSlot.OFFHAND, 0F);
	}

	// ---- bottles --------------------------------------------------------------------------------------------------------------

	/** Never more than 3 bottles in 200 ticks, and never a second bottle while one is raised. A bottle is booked at its splash tick when its wind-up starts. */
	public boolean canThrow(long now) {
		if (pending != null) return false;
		thrown.removeIf(t -> now - t > 200);
		long due = now + COUNTER_BREW.windup();
		int inWindow = 0;
		for (long t : thrown) if (due - t <= 200) inWindow++;
		return inWindow < 3;
	}

	private long pendingSince;
	private float pendingHp = 1F;
	/** Health share (0..1) when {@link #lastBottle} was chosen. */
	public float lastBottleHp = 1F;

	private boolean thirdInARow(Bottle b) {
		int n = history.size();
		return n >= 2 && history.get(n - 1) == b && history.get(n - 2) == b;
	}

	/** Scores every bottle from what the player showed (B4). Ties go to the least recently thrown. */
	public Bottle pick(LivingEntity t, long now) {
		boolean mobile = reader != null && reader.has(LoadoutClass.MOBILE);
		boolean ranged = reader != null && reader.has(LoadoutClass.RANGED);
		jumpTicks.removeIf(j -> now - j > 200);
		hitTicks.removeIf(h -> now - h > 100);
		boolean melee = guardsAlive().stream().anyMatch(g -> g.distanceTo(t) < 4);
		int[] score = new int[Bottle.values().length];
		if (mobile || jumpTicks.size() >= 2) score[Bottle.SLOWNESS.ordinal()] += 3;
		if (hitTicks.size() >= 3) score[Bottle.WEAKNESS.ordinal()] += 3;
		if (ranged) score[Bottle.HARMING.ordinal()] += 3;
		if (cleansed >= 2) score[Bottle.HARMING.ordinal()] += 4;
		if (stillSince >= 0 && now - stillSince >= 60) score[Bottle.HARMING.ordinal()] += 2;
		if (getHealth() <= getMaxHealth() * 0.5F) score[Bottle.POISON.ordinal()] += 1;
		if (melee) score[Bottle.SLOW_CLOUD.ordinal()] += 3;
		if (melee && phaseIndex() >= 1) score[Bottle.MIST.ordinal()] += 2;
		Bottle best = null;
		for (Bottle b : Bottle.values()) {
			if (thirdInARow(b)) continue;                                                        // never the same bottle three times in a row
			if (b == Bottle.POISON && getHealth() > getMaxHealth() * 0.5F) continue;             // poison only after 50 percent HP
			if (b == Bottle.MIST && phaseIndex() < 1) continue;                                  // Poison Mist joins the table in P2
			if (best == null || score[b.ordinal()] > score[best.ordinal()] || (score[b.ordinal()] == score[best.ordinal()] && lastThrown[b.ordinal()] < lastThrown[best.ordinal()])) best = b;
		}
		return best == null ? Bottle.HARMING : best;
	}

	private List<KingdomSoldier> guardsAlive() {
		hands.removeIf(g -> !g.isAlive());
		return hands;
	}

	/** Wind-up start: picks the bottle (or the queued counter's) and draws its glyph circle on the ground where the target stands. */
	void beginBrew(ServerLevel level, LivingEntity t) {
		Bottle b = queued != null && !thirdInARow(queued) ? queued : pick(t, level.getGameTime());
		queued = null;
		pending = b;
		pendingHp = getHealth() / Math.max(1F, getMaxHealth());   // the health share the bottle was chosen at (the self-test checks the Poison rule against it)
		pendingSince = level.getGameTime();
		thrown.add(level.getGameTime() + COUNTER_BREW.windup());          // booked at its splash tick
		pendingAt = t.position();
		AzmarFx.glyphCircle(level, pendingAt, 2.0, b.color);
		level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 1.2F);
	}

	/** The splash lands on the circle, not on wherever the target went: leaving the circle is the counter. */
	void throwBrew(ServerLevel level) {
		Bottle b = pending != null ? pending : Bottle.HARMING;
		pending = null;
		long now = level.getGameTime();
		lastThrown[b.ordinal()] = now;
		history.add(b);
		if (history.size() > 8) history.remove(0);
		lastBottle = b;
		lastBottleHp = pendingHp;
		brews++;
		splash(level, b, pendingAt);
	}

	/** Bursts {@code b} at {@code at}: effects (and Harming's raw 16 magic damage, never lethal) to players within 2 blocks. Shields do not stop it. No block is touched. */
	public void splash(ServerLevel level, Bottle b, Vec3 at) {
		level.levelEvent(2002, BlockPos.containing(at), b.color);
		level.playSound(null, BlockPos.containing(at), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
		switch (b) {
			case SLOW_CLOUD -> GroundPatch.spawn(level, this, at, 3.0, 4, 60, 20, 0F, List.of(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1)), ParticleTypes.SNEEZE);
			case MIST -> GroundPatch.spawn(level, this, at, 3.0, 4, 80, 20, 0F, List.of(new MobEffectInstance(MobEffects.POISON, 100, 0)), ParticleTypes.HAPPY_VILLAGER);   // Poison cannot kill
			default -> {
				List<MobEffectInstance> eff = switch (b) {
					case SLOWNESS -> List.of(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0));
					case WEAKNESS -> List.of(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
					case POISON -> List.of(new MobEffectInstance(MobEffects.POISON, 120, 0));
					default -> List.<MobEffectInstance>of();
				};
				for (Player p : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(2.0, 2.5, 2.0))) {
					if (p.isSpectator() || p.isCreative()) continue;
					double dx = p.getX() - at.x, dz = p.getZ() - at.z;
					if (dx * dx + dz * dz > 4.0) continue;
					for (MobEffectInstance e : eff) p.addEffect(new MobEffectInstance(e), this);
					if (b == Bottle.HARMING) {
						float cap = p.getHealth() - 1F;
						if (cap > 0F) {
							float amt = Math.min(Math.min(16F * damageMultiplier(), PROFILE.capSignature) * Kit.MAGIC_RAW_FACTOR, cap);
							p.hurtServer(level, level.damageSources().indirectMagic(this, this), amt);
						}
					}
				}
			}
		}
	}

	// ---- flares and the Hands --------------------------------------------------------------------------------------------------------

	/** The flare that fits the moment, or null (the Hands are down, an order is on its way, or nothing is worth ordering). */
	public Flare nextFlare(LivingEntity t) {
		if (signals == null || guardsAlive().isEmpty() || signals.ordering() || redStartAt > 0) return null;
		long now = level().getGameTime();
		double hp = 0;
		for (KingdomSoldier g : hands) hp += g.getHealth() / g.getMaxHealth();
		hp /= hands.size();
		Formation cur = signals.current();
		if (hp < 0.5 && cur != Formation.WITHDRAW && signals.ready(Formation.WITHDRAW, now)) return Flare.WHITE;
		if (reader != null && reader.has(LoadoutClass.RANGED) && cur != Formation.SCREEN && signals.ready(Formation.SCREEN, now)) return Flare.AMBER;
		if (hands.size() >= 2 && hp > 0.6 && reader != null && (reader.has(LoadoutClass.LIGHT) || reader.has(LoadoutClass.HEAVY) || reader.has(LoadoutClass.REACH))) return Flare.RED;
		if (reader != null && (reader.has(LoadoutClass.MOBILE) || reader.has(LoadoutClass.SHIELD)) && cur != Formation.RING && signals.ready(Formation.RING, now)) return Flare.BLUE;
		if (cur != Formation.SCREEN && signals.ready(Formation.SCREEN, now)) return Flare.AMBER;
		return null;
	}

	/** Throws a coloured ibis glyph straight up in smoke; the Hands obey 40 ticks later (red: a 60-tick rush starting 40 ticks later). */
	public void flare(ServerLevel level, Flare f, LivingEntity target) {
		lastFlare = f;
		flares++;
		Vec3 top = position().add(0, 2.2, 0);
		for (int i = 0; i < 14; i++) level.sendParticles(new DustParticleOptions(f.color, 1.6F), top.x, top.y + i * 0.22, top.z, 2, 0.06, 0.02, 0.06, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y + 1.5, top.z, 8, 0.15, 0.3, 0.15, 0.01);
		AzmarFx.glyphCircle(level, position(), 0.8, f.color);
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.azmar.Sfx3.pick("azmar", "firesand", SoundEvents.FIREWORK_ROCKET_LAUNCH), SoundSource.HOSTILE, dev.elsewhere.rivals.azmar.Sfx3.enabled() ? 1.4F : 1.0F, 1.0F);
		if (f == Flare.RED) {
			redStartAt = level.getGameTime() + GuardSignals.FLARE_DELAY;
			redUntil = redStartAt + 60;
		} else if (f.formation != null && signals != null) {
			signals.signal(level, f.formation, f.tell, target);
		}
	}

	private void spawnHands(ServerLevel level) {
		reader = new TacticReader(this);
		signals = new GuardSignals(this);
		fillHands(level);
	}

	private int spawned;
	private static final String[] HAND_UNITS = {"azmar_naffatun", "azmar_naffatun", "azmar_sand_dancer"};

	/** Spawns the Hands not yet spawned (once each: a dead Hand is never replaced). */
	private void fillHands(ServerLevel level) {
		while (spawned < HANDS) {
			double a = spawned * 2.1 + 0.5;
			KingdomSoldier s = KingdomApi.spawnSoldier(level, AzmarContent.id(HAND_UNITS[spawned]), AzmarContent.FACTION, position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5), null);
			if (s == null) break;
			spawned++;
			s.setPersistenceRequired();
			hands.add(s);
			reader.watch(s);
		}
		signals.setGuards(hands, hands.isEmpty() ? null : hands.get(hands.size() - 1));
	}

	private Vec3 handsCentre() {
		if (guardsAlive().isEmpty()) return null;
		Vec3 c = Vec3.ZERO;
		for (KingdomSoldier g : hands) c = c.add(g.position());
		return c.scale(1.0 / hands.size());
	}

	// ---- Glyph Pacing (the Movement slot) -------------------------------------------------------------------------------------------

	/** Stays 8 to 12 blocks from the target, behind the Hands, pacing a 3-block line; walks to a new line only to keep line of sight or distance. */
	@Override
	protected void move(LivingEntity t, double dist) {
		if (dashLeft > 0) return;
		long now = now();
		boolean los = hasLineOfSight(t);
		if (paceCentre == null || dist > 12.5 || dist < 7.5 || !los) {
			if (!los) paceAngle += 0.6;
			else if (dist >= 7.5 && dist <= 12.5) paceAngle = 0;
			Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
			if (away.lengthSqr() < 1e-3) away = new Vec3(1, 0, 0);
			away = away.normalize();
			double c = Math.cos(paceAngle), s = Math.sin(paceAngle);
			Vec3 dir = new Vec3(away.x * c - away.z * s, 0, away.x * s + away.z * c);
			paceCentre = t.position().add(dir.scale(10.0));
			paceAxis = new Vec3(-dir.z, 0, dir.x);
			paceFlipAt = now + 30;
		}
		if (now >= paceFlipAt) {
			paceSide = -paceSide;
			paceFlipAt = now + 30;
		}
		Vec3 goal = paceCentre.add(paceAxis.scale(1.5 * paceSide));
		getNavigation().moveTo(goal.x, getY(), goal.z, 0.8);
	}

	// ---- Veil of the Ibis -----------------------------------------------------------------------------------------------------------

	public boolean veiled() {
		return veilAt > 0 || dashLeft > 0;
	}

	/** Invisible for 60 ticks (armour and crest stay visible), then a Speed II dash of 8 ticks to the target's flank. A hit ends the veil. */
	void veil(ServerLevel level) {
		addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
		veilAt = level.getGameTime() + 60;
		veils++;
		AzmarFx.glyphCircle(level, position(), 1.2, AzmarFx.TURQUOISE);
		level.playSound(null, blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 1.0F, 1.5F);
	}

	private void startDash(ServerLevel level, LivingEntity t) {
		Vec3 look = t.getLookAngle().multiply(-1, 0, -1);
		if (look.lengthSqr() < 1e-3) look = new Vec3(1, 0, 0);
		dashTo = t.position().add(look.normalize().scale(2.2));
		dashLeft = 8;
		addEffect(new MobEffectInstance(MobEffects.SPEED, 8, 1, true, false));
		AzmarFx.glyphCircle(level, position(), 1.0, AzmarFx.TURQUOISE);
	}

	private void ensureStarted(ServerLevel level) {
		if (started) return;
		started = true;
		spawnHands(level);
		windowStart = level.getGameTime();
		windowHealth = getHealth();
	}

	// ---- the tick (always runs: even a scripted, passive Wazir keeps his Hands, flares, veil and seal) -----------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!isAlive()) return;
		long now = level.getGameTime();
		ensureStarted(level);
		signals.tick(level);
		if (now % 10 == 0) guardsAlive();                             // lag rule: deaths are rare, a 10-tick cadence
		if (spawned < HANDS && now % 40 == 0) fillHands(level);        // a spawn the 15/30 caps refused is retried until all three stand once
		LivingEntity target = getTarget();
		// what the player showed: sprint-jumps, standing still, hits on a guard
		if (target instanceof Player p) {
			boolean ground = p.onGround();
			if (wasOnGround && !ground && p.isSprinting() && p.getDeltaMovement().y > 0.2) jumpTicks.add(now);
			wasOnGround = ground;
			if (p.getDeltaMovement().horizontalDistanceSqr() < 1e-4) {
				if (stillSince < 0) stillSince = now;
			} else stillSince = -1;
		}
		if (now % 10 == 0) for (KingdomSoldier g : hands) {     // lag rule: a rarely-changing read runs on a 10-tick cadence, not every tick
			int ts = g.getLastHurtByMobTimestamp();
			Integer prev = handHurt.put(g.getUUID(), ts);
			if (prev != null && prev != ts && g.getLastHurtByMob() instanceof Player) hitTicks.add(now);
		}
		// the circle of a bottle in the air stays lit until it lands
		if (pending != null && "counter_brew".equals(action()) && now % 4 == 0) AzmarFx.glyphCircle(level, pendingAt, 2.0, pending.color);
		if (pending != null && !"counter_brew".equals(action()) && now - pendingSince > COUNTER_BREW.windup() + 4) pending = null;   // a staggered wind-up drops its bottle (the booking stays: stricter)
		// idle: he traces glyphs on the ground as he paces
		if (target != null && idle() && now % 40 == 0 && !veiled()) AzmarFx.glyphCircle(level, position(), 1.0, AzmarFx.TURQUOISE);
		// the veil: after 60 ticks the Speed II dash to flank (a hit has already cancelled it)
		if (veilAt > 0 && now >= veilAt) {
			veilAt = 0;
			if (target != null && target.isAlive() && hasEffect(MobEffects.INVISIBILITY)) startDash(level, target);
		}
		if (dashLeft > 0) {
			Vec3 d = dashTo.subtract(position()).multiply(1, 0, 1);
			if (d.lengthSqr() < 1.0) dashLeft = 0;
			else {
				Vec3 v = d.normalize().scale(0.75);
				setDeltaMovement(v.x, getDeltaMovement().y, v.z);
				needsSync = true;
				dashLeft--;
			}
		}
		// the Hands sprint-guard him while he drinks
		if (isHealing() && target != null) {
			Vec3 mid = position().add(target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(2.0));
			for (KingdomSoldier g : hands) {
				g.setTarget(target);
				if (now % 5 == 0) g.getNavigation().moveTo(mid.x, mid.y, mid.z, 1.4);
			}
		}
		// the red flare: the Sand-Dancer and the Naffatun rush for 60 ticks, then withdraw
		if (redStartAt > 0 && target != null) {
			if (now >= redStartAt && now < redUntil) {
				for (KingdomSoldier g : hands) {
					g.setTarget(target);
					if (now % 10 == 0) g.getNavigation().moveTo(target, 1.35);
				}
			} else if (now >= redUntil) {
				redStartAt = 0;
				if (!hands.isEmpty()) flare(level, Flare.WHITE, target);
			}
		}
		// the Hands re-form 8 blocks behind him within 40 ticks
		if (reformUntil > now) {
			for (KingdomSoldier g : hands) {
				g.setTarget(null);
				if (now % 5 == 0) g.getNavigation().moveTo(reformPoint.x, reformPoint.y, reformPoint.z, 1.3);
			}
		}
		tickSeal(level, now);
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int newPhase) {
		super.onPhaseChange(level, newPhase);
		if (newPhase >= 2 && seals == 0 && sealWriteAt == 0) {
			sealWriteAt = level.getGameTime() + SEAL_WRITE;
			startTelegraph("seal_of_ibis", "Seal of the Ibis", SEAL_WRITE, Telegraph.Weight.HEAVY, 0F, style().cue(false, true), true, () -> {});
			level.playSound(null, blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 1.5F, 0.7F);
		}
	}

	public boolean sealActive() {
		return sealUntil > 0;
	}

	public Vec3 sealCentre() {
		return sealCentre;
	}

	private void tickSeal(ServerLevel level, long now) {
		if (sealWriteAt > 0) {
			if (now < sealWriteAt) {                                      // the glyphs are written: a ring grows round him
				if (now % 3 == 0) AzmarFx.glyphCircle(level, position(), 1.0 + SEAL_RADIUS * (1.0 - (sealWriteAt - now) / (double) SEAL_WRITE), AzmarFx.TURQUOISE);
			} else {
				sealWriteAt = 0;
				sealCentre = position();
				sealUntil = now + SEAL_TICKS;
				seals++;
				var cap = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);   // absorption is clamped to this attribute (0 by default)
				if (cap != null) cap.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SEAL_CAP, SEAL_ABSORB, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
				setAbsorptionAmount(SEAL_ABSORB);                         // 30 points, 10 percent
				level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.8F);
			}
		}
		if (sealUntil <= 0) return;
		if (now >= sealUntil || !isAlive()) {
			endSeal(level);
			return;
		}
		if (now % 10 == 0) {
			DustParticleOptions d = new DustParticleOptions(AzmarFx.TURQUOISE, 1.2F);
			for (int i = 0; i < 32; i++) { // P13: 32 points on the 6-block ring (1.2 blocks apart), was 48
				double a = i * Math.PI * 2 / 32 + now * 0.02;
				level.sendParticles(d, sealCentre.x + Math.cos(a) * SEAL_RADIUS, sealCentre.y + 0.1, sealCentre.z + Math.sin(a) * SEAL_RADIUS, 1, 0, 0, 0, 0.0);
			}
		}
		if (now % 20 == 0) AzmarFx.glyphCircle(level, sealCentre, SEAL_RADIUS * 0.5, AzmarFx.TURQUOISE);
		if (now % 10 == 0) {                                             // lag rule: refreshed every 10 ticks (effects last 30); leaving the seal ends them
			Set<UUID> inside = new HashSet<>();
			for (Player p : level.getEntitiesOfClass(Player.class, new AABB(sealCentre, sealCentre).inflate(SEAL_RADIUS, 4, SEAL_RADIUS))) {
				if (p.isSpectator() || p.isCreative()) continue;
				double dx = p.getX() - sealCentre.x, dz = p.getZ() - sealCentre.z;
				if (dx * dx + dz * dz > SEAL_RADIUS * SEAL_RADIUS) continue;
				inside.add(p.getUUID());
				p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0, true, true));
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, true, true));
			}
			for (UUID u : new ArrayList<>(sealed)) if (!inside.contains(u)) clearSealed(level, u);
			sealed.clear();
			sealed.addAll(inside);
		}
	}

	private void clearSealed(ServerLevel level, UUID u) {
		if (level.getPlayerByUUID(u) instanceof Player p) {
			p.removeEffect(MobEffects.WEAKNESS);
			p.removeEffect(MobEffects.SLOWNESS);
		}
	}

	private void endSeal(ServerLevel level) {
		for (UUID u : sealed) clearSealed(level, u);
		sealed.clear();
		sealUntil = 0;
		if (getAbsorptionAmount() > 0F) setAbsorptionAmount(0F);
		var cap = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
		if (cap != null) cap.removeModifier(SEAL_CAP);
	}

	// ---- reads, counters, re-forming, bad trades -----------------------------------------------------------------------------------------

	@Override
	protected void extraTick(ServerLevel level, LivingEntity target) {
		ensureStarted(level);
		long now = level.getGameTime();
		Player tp = target instanceof Player p ? p : null;
		if (tp != null) reader.setMarked(isMarked(tp.getUUID(), now));
		reader.tick(level, tp);
		if (target == null) return;
		if (now % 10 == 0) for (KingdomSoldier g : hands) if (reformUntil <= now && (g.getTarget() == null || !g.getTarget().isAlive())) g.setTarget(target);
		// a queued counter bottle goes out as soon as the 3-in-200 limit and his hands are free
		if (queued != null && idle() && canThrow(now) && !thirdInARow(queued) && distanceTo(target) <= 16) fireSignature(COUNTER_BREW, target);
		// counters of repeated tactics (own table)
		if (tp != null && idle() && reader.readDone(now) && !reader.satisfied()) {
			TacticReader.Counter c = reader.pollCounter();
			if (c != null) runCounter(level, c, tp);
		}
		// re-forming: 2 of the 3 dead, or you within 3 blocks of him
		boolean twoDead = HANDS - guardsAlive().size() >= 2 && !reformed;
		if ((twoDead || distanceTo(target) < 3.0) && idle() && now >= reformAt) {
			reformAt = now + REFORM_TICKS;   // defensive back-step: 300 / 1.3 = 231
			if (twoDead) reformed = true;
			reforms++;
			flare(level, Flare.AMBER, target);                           // amber flare, then he back-steps 8 blocks (a draught follows from the kit if he is hurt and clear)
			Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : away.normalize().scale(8);
			AzmarFx.glyphCircle(level, position(), 1.0, AzmarFx.TURQUOISE);
			hop(level, position().add(away));
			Vec3 behind = position().add(away.normalize().scale(8));
			reformPoint = behind;
			reformUntil = now + 40;
			AzmarFx.glyphCircle(level, behind, 1.5, AzmarFx.AMBER);     // where the Hands re-form: 8 blocks behind him
		}
		// bad trade: more than 10 percent of his HP lost for less than 3 percent dealt in 120 ticks: Veil away and a white flare
		if (now - windowStart >= 120) {
			float lost = windowHealth - getHealth();
			if (lost > getMaxHealth() * 0.10F && windowDealt < getMaxHealth() * 0.03F && idle() && !veiled()) {
				refusals++;
				addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
				Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : away.normalize().scale(8);
				if (now >= backstepAt && hop(level, position().add(away))) backstepAt = now + 100;
				flare(level, Flare.WHITE, target);
			}
			windowStart = now;
			windowHealth = getHealth();
			windowDealt = 0F;
		}
	}

	/** His counter to the third repeat of a tactic (B4). */
	private void runCounter(ServerLevel level, TacticReader.Counter c, Player p) {
		counters++;
		Bottle b = switch (c.tactic()) {
			case JUMP_CRIT_OPENER -> Bottle.SLOWNESS;                  // T1: a Slowness bottle at your landing
			case COMBO -> Bottle.WEAKNESS;                             // T2: Weakness on the third combo
			case RANGED_POKE -> Bottle.HARMING;                        // T3: an amber flare (Screen) then Harming
			case HIGH_GROUND -> Bottle.HARMING;                        // T5: Harming at your pillar
			case SHIELD_TURTLE -> Bottle.HARMING;                      // T6: a splash at your feet (shields cannot stop it)
			case HEAL_RUN -> Bottle.HARMING;                           // T7: Harming at the healer
			case PEARL_IN -> null;                                     // T4: the Sand-Dancer waits at the landing point
		};
		switch (c.tactic()) {
			case RANGED_POKE -> {
				Flare f = Flare.AMBER;
				if (signals.ready(Formation.SCREEN, level.getGameTime())) flare(level, f, p);
			}
			case PEARL_IN -> {
				for (KingdomSoldier g : hands) if (g.unitId().equals(AzmarContent.id("azmar_sand_dancer"))) { g.setTarget(p); g.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 1.3); }
			}
			default -> {}
		}
		if (b != null) queued = b;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hit = super.hurtServer(level, source, damage);
		if (hit && damage > 0F) {
			if (hasEffect(MobEffects.INVISIBILITY)) removeEffect(MobEffects.INVISIBILITY);   // the veil ends the instant he is hit
			veilAt = 0;
			dashLeft = 0;
			if (source.getEntity() instanceof Player) hitTicks.add(level.getGameTime());
		}
		return hit;
	}

	/** Called by the self-test and the combat events when the player cleanses (milk or an effect-removing potion). */
	public void noteCleanse() {
		cleansed++;
	}

	@Override
	public void die(DamageSource source) {
		DuelistEvents.HIT.remove(hitListener);
		if (level() instanceof ServerLevel l) endSeal(l);
		if (reader != null) reader.close();
		for (KingdomSoldier g : hands) if (g.isAlive()) g.discard();
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		DuelistEvents.HIT.remove(hitListener);
		if (level() instanceof ServerLevel l && sealUntil > 0) endSeal(l);
		if (reader != null) reader.close();
		if (reason.shouldDestroy()) for (KingdomSoldier g : hands) if (g.isAlive()) g.discard();
		super.remove(reason);
	}
}
