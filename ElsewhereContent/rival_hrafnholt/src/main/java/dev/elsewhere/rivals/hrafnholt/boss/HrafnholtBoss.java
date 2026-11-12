package dev.elsewhere.rivals.hrafnholt.boss;

import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.Timers;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One of the six Hrafnholt bosses (or its Risen champion) on the duelist plumbing, built from its v3 identity card. This class is the
 * boss's own code: the Movement slot ({@link #move}: Glacier March, Flank Circle via the kit, Door Hold, Verse Skip, Straight Charge,
 * Lane Runner), the Defence slot (Splintering Heater, Spear Parry, Ward Wall, Unflinching, Wolf Screen), the Recovery slot (Ward Leech,
 * Wolf Tend), Reach control (leaps and rushes) and the Last stand (Hearth Ember, Standard Plant, Door Barred, Death-Verse, Pelt Shed,
 * Rope Snares), each fired once at the card's HP threshold. The signature moves are in {@link HrafnholtProfiles}.
 * Risen mode: a promoted Hird-Captain (captain HP plus 15 percent, its 2 kept moves, repeat-kill drops).
 * Drops for a court-tracked leader are rolled by the court (its faction-file drops lines); a boss summoned by hand rolls the same table itself.
 */
public class HrafnholtBoss extends HumanBoss implements Leaders.LeaderBoss {
	private static final EntityDataAccessor<Boolean> DATA_RISEN = SynchedEntityData.defineId(HrafnholtBoss.class, EntityDataSerializers.BOOLEAN);
	static final Identifier RISEN_HP_MOD = HrafnholtContent.id("hrafnholt_risen_hp");
	static final Identifier SUCCESSOR_HP_MOD = HrafnholtContent.id("hrafnholt_successor_hp");
	static final Identifier RAGE_SPEED_MOD = HrafnholtContent.id("hrafnholt_rage_speed");
	static final Identifier SHED_SPEED_MOD = HrafnholtContent.id("hrafnholt_shed_speed");
	static final Identifier STANDARD_SPEED_MOD = HrafnholtContent.id("hrafnholt_standard_speed");
	static final Identifier STANDARD_ABS_MOD = HrafnholtContent.id("hrafnholt_standard_abs");
	static final Identifier ROOT_KB_MOD = HrafnholtContent.id("hrafnholt_root_kb");
	public static final String WOLF_TAG = "elsewhere_hrafnholt_boss_hound";
	public static final String STANDARD_TAG = "elsewhere_hrafnholt_raven_standard";

	public final Hrafn h;
	/** True for the separate {@code hrafnholt_risen_<slot>} types; the seat types turn Risen through {@link #onRisen(int)}. */
	public final boolean risenType;
	public boolean holder, rage, singing;
	public double oathMul = 1.0, successorMul = 1.0;
	public long wardUntil, hornAt, lastOathUpdate;
	public float stored;
	public boolean[] hornDone = new boolean[2];
	public int wolvesSpawned, wavesDone;
	public final List<UUID> wolves = new ArrayList<>();
	/** Test counters. */
	public int hornsBlown, howls, wardBursts, leeches;

	// last stand, shared
	private boolean lsPending, lsDone;
	private long lsInvulnUntil;
	// Jarl: Splintering Heater and Hearth Ember
	private float shieldHp = 120F;
	public boolean splintered;
	private float pool;
	private long poolUntil;
	private final ServerBossEvent poolBar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.elsewhere.hearth_ember"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
	// Hersir: Spear Parry and the Raven Standard
	private long parryStart = -1000, parryReadyAt;
	private long riposteAt;
	private @Nullable UUID riposteTarget;
	private @Nullable UUID standardId;
	private long standardUntil;
	// Hallwarden: Door Hold, Ward Wall, Door Barred
	private @Nullable Vec3 doorMark;
	private int farTicks;
	private boolean marching;
	private long rootedUntil;
	// Skald: Verse Skip, the axe belt, Verse of Winter and Death-Verse
	private final List<Long> axeBack = new ArrayList<>();
	private long verseUntil, nextVerseSwing;
	private double verseMul = 1.0;
	private boolean deathVerse;
	// Chief: Straight Charge, Furrow Run, Pelt Shed
	private @Nullable Vec3 aim;
	private double runLeft;
	private int pauseLeft;
	private long shedUntil;
	// Huntmaster: Lane Runner, bow, Wolf Tend, Rope Snares
	private int laneSide = 1, laneFlip = 50, drawLeft;
	private long nextShot, tendReadyAt, tendAt;
	private boolean drawingBow;
	private final List<Vec3> snares = new ArrayList<>();
	private long snaresUntil;

	// motion
	private float heading = Float.NaN;
	private int stuck;
	private @Nullable Rush rush;
	private @Nullable Leap leap;
	private @Nullable Runnable pending;

	public HrafnholtBoss(EntityType<? extends HumanBoss> type, Level level, Hrafn h, boolean risenType) {
		super(type, level, HrafnholtProfiles.of(h, risenType));
		this.h = h;
		this.risenType = risenType;
		this.holder = h == Hrafn.JARL && !risenType;
		poolBar.setVisible(false);
		if (risenType) this.entityData.set(DATA_RISEN, true);
		// RIVALS-DECISIONS 13 compensation for the 300 HP cap: +6 armor and +3 toughness (Jarl +8 and +4), knockback resistance +0.4
		boolean jarl = h == Hrafn.JARL;
		addMod(Attributes.ARMOR, "hrafn_cap_armor", jarl ? 8.0 : 6.0);
		addMod(Attributes.ARMOR_TOUGHNESS, "hrafn_cap_tough", jarl ? 4.0 : 3.0);
		addMod(Attributes.KNOCKBACK_RESISTANCE, "hrafn_cap_kb", 0.4);
		if (h == Hrafn.CHIEF) {                                                                  // Unflinching: knockback-immune
			AttributeInstance kb = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (kb != null) kb.setBaseValue(1.0);
		}
	}

	private void addMod(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, String id, double v) {
		AttributeInstance i = getAttribute(a);
		if (i != null && !i.hasModifier(HrafnholtContent.id(id))) i.addPermanentModifier(new AttributeModifier(HrafnholtContent.id(id), v, AttributeModifier.Operation.ADD_VALUE));
	}

	// ---- state ---------------------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_RISEN, false);
	}

	public boolean isRisen() {
		return this.entityData.get(DATA_RISEN);
	}

	/** True when this boss may use the named signature move (a Risen champion keeps only its 2). */
	public boolean moveAllowed(String id) {
		return !isRisen() || h.risenMoves.contains(id);
	}

	public ServerLevel sl() {
		return (ServerLevel) level();
	}

	public boolean rageOn() {
		return h == Hrafn.CHIEF || rage;
	}

	/** Chief: the wolf-coat is off. */
	public boolean shed() {
		return h == Hrafn.CHIEF && shedUntil > now();
	}

	public boolean rooted() {
		return rootedUntil > now();
	}

	public boolean verseActive() {
		return verseUntil > now();
	}

	public float emberPool() {
		return pool;
	}

	public int snareCount() {
		return snares.size();
	}

	public boolean verseRage() {
		return deathVerse && verseUntil > now();
	}

	/** True from the moment the last stand is due until its wind-up has run: moves must not start. */
	public boolean lastStandBusy() {
		return lsPending || lsInvulnUntil > now();
	}

	/**
	 * RIVALS-DECISIONS 13, defensive behaviour 30 percent more often, applied to this boss's own Defence slot: the Jarl's Splintering Heater and the Hallwarden's
	 * shield are raised through the shared block roll ({@link #defenceChance}); Hersir's Spear Parry repeats every 60/1.3 = 46 ticks; the Huntmaster's Wolf Screen
	 * triggers from 1.3 times as far (6.5 blocks instead of 5). The Chief (Unflinching, a passive) and the Skald (no Defence slot) have no choice to scale.
	 */
	public int parryInterval() {
		return defenceInterval(60);
	}

	public double screenRadius() {
		return 5.0 * DEFENCE_FREQUENCY;
	}

	/** The probability of this boss's block roll, 1.3 times the base 1/3, at most 0.95; zero for a boss with no shield or parry choice. */
	public float blockRollChance() {
		return h == Hrafn.JARL || h == Hrafn.HALLWARDEN ? defenceChance(1F / 3F) : 0F;
	}

	/** The cooldown a signature uses after it fires: its P2 number from phase 1 on, with the card's own overrides (Pelt Shed, Door Barred). */
	public int cooldownFor(String id, int cd, int cd2) {
		int c = phaseIndex() >= 1 ? cd2 : cd;
		if (id.equals("furrow_run") && shed()) c = 40;
		if (id.equals("bash_chain") && rooted()) c = cd / 2;
		return Math.round(c * 0.8F);   // signature cooldowns -20 percent (wind-ups unchanged)
	}

	// ---- court hooks ---------------------------------------------------------------------------------------------------------

	@Override
	public void onHolder(boolean isHolder) {
		holder = isHolder;
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp == null) return;
		if (isHolder && h.champion()) {
			if (!hp.hasModifier(SUCCESSOR_HP_MOD)) {
				hp.addPermanentModifier(new AttributeModifier(SUCCESSOR_HP_MOD, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				setHealth(Math.min(getMaxHealth(), getHealth() + (float) (hp.getBaseValue() * 0.05)));
			}
			successorMul = 1.05;
			dev.elsewhere.duelist.boss.HumanBossHealth.enforce(this);   // RIVALS-DECISIONS 13: one check after a max-health modifier changes, no polling
		} else {
			hp.removeModifier(SUCCESSOR_HP_MOD);
			successorMul = 1.0;
			dev.elsewhere.duelist.boss.HumanBossHealth.enforce(this);
		}
		lastOathUpdate = -1000;
	}

	/** The court promoted a Hird-Captain into this seat: captain HP (the court already added its 15 percent), only the 2 kept moves, repeat-kill drops. */
	@Override
	public void onRisen(int movesAllowed) {
		if (isRisen()) return;
		this.entityData.set(DATA_RISEN, true);
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp != null && hp.getBaseValue() > 0) {
			hp.removeModifier(RISEN_HP_MOD);
			hp.addPermanentModifier(new AttributeModifier(RISEN_HP_MOD, Hrafn.CAPTAIN_HP / hp.getBaseValue() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			dev.elsewhere.duelist.boss.HumanBossHealth.enforce(this);
			setHealth(getMaxHealth());
		}
		equipRisen();
		setCustomName(Component.translatable(h.risenNameKey()));
	}

	// ---- loadout -------------------------------------------------------------------------------------------------------------

	@Override
	protected void applyLoadout() {
		super.applyLoadout();
		if (isRisen()) {
			equipRisen();
			return;
		}
		equipArmor(h.pieces);
		if (h == Hrafn.CHIEF) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
		else if (h == Hrafn.HALLWARDEN || h == Hrafn.JARL) { /* the kit's shield is already in the off-hand */ }
		setCustomName(Component.translatable(h.nameKey()));
	}

	void equipRisen() {
		Hrafn.Piece[] ps = {new Hrafn.Piece("iron_helmet", "ward", "gold", -1), new Hrafn.Piece("iron_chestplate", "rib", "gold", -1),
			new Hrafn.Piece("iron_leggings", "rib", "gold", -1), new Hrafn.Piece("iron_boots", "ward", "gold", -1)};
		equipArmor(ps);
		setItemSlot(EquipmentSlot.MAINHAND, h == Hrafn.HERSIR ? new ItemStack(Items.IRON_SPEAR) : new ItemStack(Items.IRON_AXE));
		if (profile.shield != null) setItemSlot(EquipmentSlot.OFFHAND, HumanBoss.shieldStack(profile.shield.arcDegrees(), profile.shield.raiseDelay()));
		else if (h == Hrafn.CHIEF) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
		else setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		setCustomName(Component.translatable(h.risenNameKey()));
	}

	private void equipArmor(Hrafn.@Nullable Piece[] pieces) {
		if (!(level() instanceof ServerLevel sl)) return;
		var mats = sl.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		var pats = sl.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			Hrafn.Piece p = pieces == null || i >= pieces.length ? null : pieces[i];
			if (p == null) {
				setItemSlot(slots[i], ItemStack.EMPTY);
				continue;
			}
			ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(p.item())));
			if (p.dye() >= 0) st.set(DataComponents.DYED_COLOR, new DyedItemColor(p.dye()));
			var mh = mats.get(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(p.material())));
			var ph = pats.get(ResourceKey.create(Registries.TRIM_PATTERN, Identifier.withDefaultNamespace(p.pattern())));
			if (mh.isPresent() && ph.isPresent()) st.set(DataComponents.TRIM, new ArmorTrim(mh.get(), ph.get()));
			setItemSlot(slots[i], st);
			setDropChance(slots[i], 0.0F);
		}
	}

	// ---- damage --------------------------------------------------------------------------------------------------------------

	/** Hird-oath (ruler only: the Jarl and a champion who took the High Seat), Winter Rage (Strength I), Risen +15 percent. Signatures stay capped in hurtRaw. */
	@Override
	public float damageMultiplier() {
		double m = super.damageMultiplier() * oathMul * successorMul * 1.25;   // +25 percent (the 300 HP cap's compensation)
		if (isRisen()) m *= 1.0 + Hrafn.RISEN_BONUS;
		if (rageOn()) m *= 1.15;
		return (float) m;
	}

	private boolean meleeHit(DamageSource src) {
		return src.getEntity() instanceof LivingEntity && !src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) && src.getDirectEntity() == src.getEntity();
	}

	/** True when {@code src} comes from inside the frontal arc ({@code degrees} wide). */
	private boolean fromFront(DamageSource src, double degrees) {
		Vec3 from = src.getSourcePosition();
		if (from == null) return false;
		Vec3 d = from.subtract(position()).multiply(1, 0, 1);
		if (d.lengthSqr() < 1e-4) return true;
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
		return fwd.dot(d.normalize()) >= Math.cos(Math.toRadians(degrees / 2.0));
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (lsInvulnUntil > level.getGameTime() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource src, float dmg) {
		// no friendly fire: the kingdom's own (non-hostile) soldiers and their arrows never hurt a Hrafnholt boss (same rule as KingdomSoldier and AzmarBoss)
		if (src.getEntity() instanceof dev.elsewhere.kingdom.KingdomSoldier s && !dev.elsewhere.kingdom.Factions.hostile(s.factionId(), dev.elsewhere.rivals.hrafnholt.HrafnholtContent.FACTION)) return false;
		long now = level.getGameTime();
		boolean melee = meleeHit(src);
		if (dmg > 0 && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && !isInvulnerableTo(level, src)) {
			// Hersir, Spear Parry: a melee hit inside the 6-tick window is negated and ripostes within 8 ticks
			if (h == Hrafn.HERSIR && melee && now >= parryStart + 4 && now < parryStart + 10) {
				riposteAt = now + 6;
				riposteTarget = src.getEntity().getUUID();
				parryStart = -1000;
				level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.2F, 1.4F);
				level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.4, getZ(), 10, 0.4, 0.3, 0.4, 0.2);
				bump("parried");
				return false;
			}
			// a player's mid-air Crit staggers Hersir and Gudrun for 16 ticks
			if ((h == Hrafn.HERSIR || h == Hrafn.CHIEF) && src.getEntity() instanceof Player p && !p.onGround() && p.fallDistance > 0.0F && !p.isInWater() && !p.isSprinting()) {
				stopMotion();
				addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 16, 6, false, false));
				bump("staggered");
			}
			// the Skald takes +15 percent while singing; Gudrun +25 percent while the pelt is shed
			if (singing) dmg *= 1.15F;
			if (shed()) dmg *= 1.25F;
			// Door Barred: front damage -50 percent while rooted (sides and back full)
			if (rooted() && fromFront(src, 120)) dmg *= 0.5F;
			// Hearth Ember: the next 140 damage is absorbed
			if (pool > 0F) {
				float soak = Math.min(pool, dmg);
				pool -= soak;
				dmg -= soak;
				level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.2, getZ(), 6, 0.4, 0.4, 0.4, 0.02);
				if (pool <= 0F) endPool();
				else poolBar.setProgress(pool / 75F);
				if (dmg <= 0.01F) return true;
			}
		}
		return super.hurtServer(level, src, dmg);
	}

	/** Splintering Heater (Jarl): each blocked hit costs the shield its raw damage out of 120; Hearth-Ward (Hallwarden) stores 40 percent of what the shield blocks. */
	@Override
	protected void blockUsingItem(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked) {
		if (wardUntil > now()) stored = Math.min(30F, stored + damage * 0.40F);
		if (h == Hrafn.JARL && !splintered) {
			shieldHp -= damage;
			if (shieldHp <= 0F) splinter();
		}
		super.blockUsingItem(level, attacker, source, damage, fullyBlocked);
	}

	/** The Jarl's shield splinters for good: a 24-tick shatter (particles and sound), no armour loss. */
	void splinter() {
		if (splintered) return;
		splintered = true;
		disableShield(1_000_000);
		stopUsingItem();
		setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		ServerLevel level = sl();
		level.playSound(null, blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 1.6F, 0.6F);
		for (int i = 0; i < 24; i += 4) Timers.after(level.getServer(), i, () -> level.sendParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY() + 1.2, getZ(), 6, 0.5, 0.5, 0.5, 0.1));
		bump("splintered");
	}

	// ---- per-tick ------------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() || !isAlive()) return;
		ServerLevel level = sl();
		long now = level.getGameTime();
		if (h == Hrafn.HALLWARDEN) tickShieldKnockback();
		if (lsPending && !lsDone && idle() && !moving()) beginLastStand(level);
		if (rush != null || leap != null) {
			if (rush != null) tickRush(level);
			else tickLeap(level);
		}
		if (pool > 0F && now >= poolUntil) endPool();
		if (tickCount % 10 == 0 && pool > 0F) poolBar.setProgress(pool / 75F);
		if (h == Hrafn.HERSIR) tickHersir(level, now);
		if (h == Hrafn.HUNTMASTER && !snares.isEmpty()) tickSnares(level, now);
		if (verseUntil != 0 && now >= verseUntil) endVerse();
		if (shedUntil != 0 && now >= shedUntil) endShed();
	}

	/** Ward Wall (knockback-immune while the shield is up) and Door Barred (rooted) share one knockback-resistance modifier. */
	private void tickShieldKnockback() {
		AttributeInstance kb = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb == null || h == Hrafn.CHIEF) return;
		boolean want = rooted() || (h == Hrafn.HALLWARDEN && shieldUp());
		if (want && !kb.hasModifier(ROOT_KB_MOD)) kb.addTransientModifier(new AttributeModifier(ROOT_KB_MOD, 1.0, AttributeModifier.Operation.ADD_VALUE));
		else if (!want && kb.hasModifier(ROOT_KB_MOD)) kb.removeModifier(ROOT_KB_MOD);
	}

	@Override
	protected void extraTick(ServerLevel level, @Nullable LivingEntity target) {
		long now = level.getGameTime();
		if (holder && now - lastOathUpdate >= 40) {
			lastOathUpdate = now;
			oathMul = 1.0 + hirdOath(level);
		} else if (!holder) oathMul = 1.0;
		if (rageOn() && tickCount % 10 == 0) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.6, getZ(), 1, 0.3, 0.3, 0.3, 0.0);
		if (h == Hrafn.CHIEF && tickCount % 100 == 0) addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 0, false, false));
		if (wardUntil > now && shieldDisabledTicks() > 0) stored = 0F;      // an axe on the shield loses the stored charge
		if (!wolves.isEmpty() && tickCount % 5 == 0) tendWolves(level, target);
		if (h == Hrafn.JARL && target != null && tickCount % 5 == 0 && hornDue() && idle() && !moving() && !lastStandBusy() && ready("sig:hearth_horn")) {
			for (SignatureMove m : profile.signatures) if (m.id().equals("hearth_horn")) {   // the horn is due: blow it now rather than waiting on the random pick
				fireSignature(m, target);
				break;
			}
		}
		if (h == Hrafn.HUNTMASTER) huntmasterTick(level, target, now);
		if (h == Hrafn.SKALD) skaldTick(level, target, now);
		if (target != null && idle() && pending != null) {
			Runnable r = pending;
			pending = null;
			r.run();
		}
	}

	/** Hird-oath: +10 percent damage per living champion (court count), up to +50 percent. */
	double hirdOath(ServerLevel level) {
		try {
			CourtState st = CourtState.get(level.getServer(), HrafnholtContent.FACTION);
			int n = st.livingLords() - (h.champion() ? 1 : 0);
			return Math.min(0.50, 0.10 * Math.max(0, n));
		} catch (RuntimeException e) {
			return 0.0;
		}
	}

	public void schedule(Runnable r) {
		pending = r;
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int newPhase) {
		boolean risen = isRisen();
		switch (h) {
			case JARL -> {
				if (newPhase == 1) splinter();                                             // 60 percent: the shield splinters if it has not already
				if (newPhase >= 2) requestLastStand();
			}
			case HERSIR, HALLWARDEN, SKALD, CHIEF, HUNTMASTER -> {
				if (newPhase >= 2) requestLastStand();
				else if (newPhase == 1 && h == Hrafn.HUNTMASTER && wolvesSpawned > 0) schedule(() -> secondWave(level));
			}
		}
	}

	// ---- Jarl: Hearth-Horn and Winter Rage -----------------------------------------------------------------------------------------------

	/** Hearth-Horn is due at 66 percent and again at 33 percent HP. */
	public boolean hornDue() {
		if (h != Hrafn.JARL || isRisen()) return false;
		double f = getHealth() / getMaxHealth();
		return (f <= 0.33 && !hornDone[1]) || (f <= 0.66 && !hornDone[0]);
	}

	public void markHornTaken() {
		double f = getHealth() / getMaxHealth();
		int i = f <= 0.33 && !hornDone[1] ? 1 : 0;
		for (int k = 0; k <= i; k++) hornDone[k] = true;
	}

	/** Winter Rage: the shield is gone for good, Strength I, 20 percent faster, Cleave comes in pairs. */
	void enterRage(ServerLevel level) {
		if (rage) return;
		rage = true;
		splinter();
		addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1_000_000, 0, false, false));
		AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
		if (sp != null && !sp.hasModifier(RAGE_SPEED_MOD)) sp.addPermanentModifier(new AttributeModifier(RAGE_SPEED_MOD, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.hrafnholt.Sfx3.pick("hrafnholt", "warcry", SoundEvents.RAVAGER_ROAR), SoundSource.HOSTILE, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 2.0F : 1.5F, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 0.9F : 0.6F);
		bump("rage");
	}

	/** Two Huskarls walk in (cap-checked); every Huskarl within 32 blocks gets Strength I for 160 ticks. */
	public int summonHuskarls(ServerLevel level, int n) {
		Identifier huskarl = HrafnholtContent.id("hrafnholt_huskarl");
		int spawned = 0;
		for (int i = 0; i < n; i++) {
			if (KingdomPerf.room(level, position()) < 1) break;
			Vec3 p = position().add(Math.cos(i * Math.PI) * 3, 0, Math.sin(i * Math.PI) * 3 + 2);
			KingdomSoldier s = KingdomApi.spawnSoldier(level, huskarl, HrafnholtContent.FACTION, p, null);
			if (s == null) continue;
			spawned++;
			LivingEntity t = getTarget();
			if (t != null) s.setTarget(t);
		}
		for (KingdomSoldier s : level.getEntitiesOfClass(KingdomSoldier.class, getBoundingBox().inflate(32), x -> x.isAlive() && HrafnholtContent.FACTION.equals(x.factionId())))
			s.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
		return spawned;
	}

	/** Two Huskarls flank left and right of the boss (Hersir's Horn of Three Calls). */
	public int summonFlank(ServerLevel level, int n) {
		Identifier huskarl = HrafnholtContent.id("hrafnholt_huskarl");
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
		Vec3 left = new Vec3(-fwd.z, 0, fwd.x);
		int spawned = 0;
		for (int i = 0; i < n; i++) {
			if (KingdomPerf.room(level, position()) < 1) break;
			Vec3 p = position().add(left.scale(i == 0 ? 3 : -3)).add(fwd.scale(1.5));
			KingdomSoldier s = KingdomApi.spawnSoldier(level, huskarl, HrafnholtContent.FACTION, p, null);
			if (s == null) continue;
			spawned++;
			s.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
			LivingEntity t = getTarget();
			if (t != null) s.setTarget(t);
		}
		return spawned;
	}

	// ---- Last stands ------------------------------------------------------------------------------------------------------------------

	private void requestLastStand() {
		if (!lsDone && !isRisen()) lsPending = true;   // a Risen champion keeps only its 2 moves, no last stand
	}

	private int lastStandTicks() {
		return switch (h) {
			case JARL, SKALD, CHIEF -> 30;
			default -> 24;
		};
	}

	private String lastStandLabel() {
		return switch (h) {
			case JARL -> "Hearth Ember";
			case HERSIR -> "Standard Plant";
			case HALLWARDEN -> "Door Barred";
			case SKALD -> "Death-Verse";
			case CHIEF -> "Pelt Shed";
			case HUNTMASTER -> "Rope Snares";
		};
	}

	/** Fires once at the card's HP threshold: a telegraphed wind-up of at least 20 ticks, then the stand's own effect. Never heals. */
	private void beginLastStand(ServerLevel level) {
		lsPending = false;
		lsDone = true;
		if (h == Hrafn.HUNTMASTER) {                                                         // a backward leap to 14 blocks first
			LivingEntity t = getTarget();
			Vec3 away = t == null ? Vec3.directionFromRotation(0, getYRot() + 180F).multiply(1, 0, 1).normalize() : position().subtract(t.position()).multiply(1, 0, 1).normalize();
			startLeap(position().add(away.scale(14.0)), 0.9, 0.7, 0, false, () -> snareWindup(level));
			return;
		}
		int ticks = lastStandTicks();
		if (h == Hrafn.JARL || h == Hrafn.HALLWARDEN) lsInvulnUntil = level.getGameTime() + ticks + 1;
		if (h == Hrafn.SKALD) singing = true;                                                  // +15 percent damage taken during the song
		if (h == Hrafn.CHIEF) shedUntil = level.getGameTime() + ticks + 160;                    // +25 percent damage taken while it lasts
		startTelegraph("last_stand", lastStandLabel(), ticks, Telegraph.Weight.HEAVY, 0F, style().cue(h == Hrafn.HALLWARDEN, h != Hrafn.HALLWARDEN), true, () -> fireLastStand(level));
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.hrafnholt.Sfx3.pick("hrafnholt", "horn", SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value()), SoundSource.HOSTILE, 2F, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 0.85F : 0.7F);
		bump("last_stand");
	}

	private void snareWindup(ServerLevel level) {
		startTelegraph("last_stand", lastStandLabel(), 24, Telegraph.Weight.HEAVY, 0F, style().cue(true, false), true, () -> fireLastStand(level));
	}

	private void fireLastStand(ServerLevel level) {
		if (!isAlive()) return;
		long now = level.getGameTime();
		switch (h) {
			case JARL -> {
				for (LivingEntity e : foes(level, position(), 5.0)) {                      // the 5-block frost ring: raw 12, knockback 3, Slowness I 60
					HrafnholtProfiles.hit(this, e, 12F, DamageCaps.MINOR);
					HrafnholtProfiles.push(this, e, 3.0);
					HrafnholtProfiles.slow(e, 60, 0);
				}
				HrafnholtProfiles.ring(level, position(), 5.0, ParticleTypes.SNOWFLAKE);
				level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
				pool = 75F;                                                              // the Ember Pool: 25 percent of max HP, no heal
				poolUntil = now + 400;
				poolBar.setProgress(1F);
				poolBar.setVisible(true);
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(64))) poolBar.addPlayer(p);
				enterRage(level);
			}
			case HERSIR -> plantStandard(level);
			case HALLWARDEN -> {
				for (LivingEntity e : foes(level, position(), 4.0)) {                      // the 4-block ring: raw 14, knockback 4
					HrafnholtProfiles.hit(this, e, 14F, DamageCaps.MINOR);
					HrafnholtProfiles.push(this, e, 4.0);
				}
				HrafnholtProfiles.ring(level, position(), 4.0, ParticleTypes.FLAME);
				level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.5F);
				rootedUntil = now + 200;                                                    // rooted: front -50 percent, Bash Chain cooldown halved
				forceShield(200);
			}
			case SKALD -> {
				singing = false;
				startVerse(1.25, 200);
				deathVerse = true;
			}
			case CHIEF -> {
				for (LivingEntity e : foes(level, position(), 6.0)) HrafnholtProfiles.push(this, e, 2.0);
				AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
				if (sp != null && !sp.hasModifier(SHED_SPEED_MOD)) sp.addTransientModifier(new AttributeModifier(SHED_SPEED_MOD, 0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
				HrafnholtProfiles.ring(level, position(), 4.0, ParticleTypes.CLOUD);
			}
			case HUNTMASTER -> {
				snares.clear();
				for (int i = 0; i < 3; i++) {                                                // three rope rings, 3 blocks round her
					double a = i * Math.PI * 2 / 3 + random.nextDouble() * 0.5;
					snares.add(position().add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0));
				}
				snaresUntil = now + 200;
				for (UUID id : wolves) if (level.getEntity(id) instanceof Wolf w && w.isAlive()) w.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
			}
		}
	}

	private void endPool() {
		pool = 0F;
		poolBar.setVisible(false);
		poolBar.removeAllPlayers();
	}

	private void endShed() {
		shedUntil = 0;
		AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
		if (sp != null) sp.removeModifier(SHED_SPEED_MOD);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer p) {
		super.startSeenByPlayer(p);
		if (pool > 0F) poolBar.addPlayer(p);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer p) {
		super.stopSeenByPlayer(p);
		poolBar.removePlayer(p);
	}

	/** Visible rope rings: particles every 5 ticks; a player inside one gets Slowness II 40 ticks, no damage. */
	private void tickSnares(ServerLevel level, long now) {
		if (now >= snaresUntil) {
			snares.clear();
			return;
		}
		for (Vec3 c : snares) {
			if (now % 5 == 0) for (int i = 0; i < 16; i++) {
				double a = i * Math.PI * 2 / 16;
				level.sendParticles(ParticleTypes.CRIT, c.x + Math.cos(a) * 1.5, c.y + 0.1, c.z + Math.sin(a) * 1.5, 1, 0, 0, 0, 0.0);
			}
			if (now % 10 == 0) for (LivingEntity e : foes(level, c, 1.5)) {
				if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
			}
		}
	}

	// ---- Hersir: Spear Parry, Raven Standard ----------------------------------------------------------------------------------------------

	private void tickHersir(ServerLevel level, long now) {
		LivingEntity t = getTarget();
		if (idle() && !moving() && t != null && t.isAlive() && now >= parryReadyAt && distanceTo(t) <= 4.2 && !lsPending) {
			parryStart = now;                                                               // 4-tick raise, then a 6-tick window; once per 60 ticks
			parryReadyAt = now + parryInterval();
			level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.8F, 1.3F);
		}
		if (now >= parryStart + 4 && now < parryStart + 10 && now % 2 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.3, getZ(), 2, 0.3, 0.2, 0.3, 0.05);
		if (riposteTarget != null && now >= riposteAt) {
			var e = level.getEntity(riposteTarget);
			riposteTarget = null;
			if (e instanceof LivingEntity le && le.isAlive() && distanceTo(le) <= 4.6) {
				swingForAttack(InteractionHand.MAIN_HAND);
				HrafnholtProfiles.hit(this, le, 14F, DamageCaps.MINOR);
				bump("riposte");
			}
		}
		if (standardId != null) tickStandard(level, now);
	}

	/** The Raven Standard: an entity (60 HP, hittable, not a block) for 400 ticks. Within 12 blocks Huskarls get Strength I; within 6 she gets +15 percent speed; she has a 44-point absorption. */
	private void plantStandard(ServerLevel level) {
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
		Vec3 p = position().add(fwd.scale(1.5));
		ArmorStand st = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (st == null) return;
		st.snapTo(p.x, p.y, p.z, getYRot(), 0F);
		st.addTag(STANDARD_TAG);
		st.setCustomName(Component.translatable("entity.elsewhere.hrafnholt_raven_standard"));
		st.setCustomNameVisible(true);
		st.setItemSlot(EquipmentSlot.HEAD, new ItemStack(dev.elsewhere.rivals.hrafnholt.HrafnholtItems.RAVEN_BANNER));
		st.setNoGravity(false);
		level.addFreshEntity(st);
		standardId = st.getUUID();
		standardUntil = level.getGameTime() + 400;
		HrafnholtStandards.register(st, this, 60F);
		AttributeInstance ab = getAttribute(Attributes.MAX_ABSORPTION);
		if (ab != null && !ab.hasModifier(STANDARD_ABS_MOD)) ab.addTransientModifier(new AttributeModifier(STANDARD_ABS_MOD, 44.0, AttributeModifier.Operation.ADD_VALUE));
		setAbsorptionAmount(44F);
		level.playSound(null, blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.2F, 0.8F);
		bump("standard_planted");
	}

	public boolean standardUp() {
		return standardId != null;
	}

	/** Called by the standard's hit handler when its 60 HP are gone. */
	void standardCut(boolean cut) {
		if (standardId == null) return;
		standardId = null;
		AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
		if (sp != null) sp.removeModifier(STANDARD_SPEED_MOD);
		setAbsorptionAmount(0F);
		AttributeInstance ab = getAttribute(Attributes.MAX_ABSORPTION);
		if (ab != null) ab.removeModifier(STANDARD_ABS_MOD);
		if (cut) bump("standard_cut");
	}

	private void tickStandard(ServerLevel level, long now) {
		var e = standardId == null ? null : level.getEntity(standardId);
		if (e == null || !e.isAlive() || now >= standardUntil) {
			if (e != null && e.isAlive()) e.discard();
			HrafnholtStandards.forget(standardId);
			standardCut(false);
			return;
		}
		AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
		boolean near = e.distanceTo(this) <= 6.0;
		if (sp != null) {
			if (near && !sp.hasModifier(STANDARD_SPEED_MOD)) sp.addTransientModifier(new AttributeModifier(STANDARD_SPEED_MOD, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			else if (!near) sp.removeModifier(STANDARD_SPEED_MOD);
		}
		if (now % 20 == 0) {
			Identifier huskarl = HrafnholtContent.id("hrafnholt_huskarl");
			for (KingdomSoldier s : level.getEntitiesOfClass(KingdomSoldier.class, e.getBoundingBox().inflate(12), x -> x.isAlive() && huskarl.equals(x.unitId()) && HrafnholtContent.FACTION.equals(x.factionId())))
				s.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0));
			level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 2.2, e.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
		}
	}

	// ---- Hallwarden: Door Mark, Ward Leech ---------------------------------------------------------------------------------------------

	public void doorMarkHere() {
		doorMark = position();
		marching = false;
		farTicks = 0;
	}

	/** Ward Leech: each shockwave that hits a player heals him 1.5 percent of max HP, at most 3 times a fight. */
	public void wardLeech() {
		if (leeches >= 3) return;
		leeches++;
		heal(getMaxHealth() * 0.015F);
		sl().sendParticles(ParticleTypes.HEART, getX(), getY() + 1.8, getZ(), 3, 0.3, 0.3, 0.3, 0.0);
	}

	// ---- Skald: axe belt, Verse Skip, Verse of Winter -------------------------------------------------------------------------------------

	public int axesOnBelt() {
		long now = now();
		axeBack.removeIf(t -> t <= now);
		return 4 - axeBack.size();
	}

	public void axeThrown() {
		axeBack.add(now() + 80);                                                              // a stuck axe returns to the belt 80 ticks later
	}

	/** Verse Skip: hop back 5 blocks (8 ticks), throw, then re-enter with the sprint-jump. Not in Death-Verse (no retreat, no throws). */
	public void beginSkip(LivingEntity t) {
		if (!isAlive() || !t.isAlive() || verseRage() || lastStandBusy() || !idle() || moving()) return;
		Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot() + 180F).multiply(1, 0, 1) : away.normalize();
		startLeap(position().add(away.scale(5.0)), 0.42, 0.55, 0, false, () -> {
			LivingEntity tg = getTarget();
			if (tg == null || !tg.isAlive() || verseRage() || !idle() || axesOnBelt() <= 0) return;
			for (SignatureMove m : profile.signatures) if (m.id().equals("rune_throw") && moveAllowed("rune_throw") && hasLineOfSight(tg) && distanceTo(tg) >= 4.0) {
				fireSignature(m, tg);
				return;
			}
		});
		bump("skip");
	}

	/** +20 percent (Verse of Winter) or +25 percent (Death-Verse) attack speed for {@code ticks}: swings come on a shorter cadence. */
	public void startVerse(double mul, int ticks) {
		verseMul = mul;
		verseUntil = now() + ticks;
		sl().sendParticles(ParticleTypes.NOTE, getX(), getY() + 2.2, getZ(), 8, 0.4, 0.3, 0.4, 0.5);
		bump("verse");
	}

	private void endVerse() {
		verseUntil = 0;
		verseMul = 1.0;
		deathVerse = false;
	}

	private void skaldTick(ServerLevel level, @Nullable LivingEntity t, long now) {
		if (verseUntil <= now || t == null || !idle() || moving() || now < nextVerseSwing || distanceTo(t) > profile.reach + 0.3 || !hasLineOfSight(t)) return;
		nextVerseSwing = now + Math.round(profile.swingInterval / verseMul);
		startTelegraph("swing", "", Math.max(8, profile.swingWindup - 2), Telegraph.Weight.ORDINARY, profile.swingRaw, style().cue(false, true), false, () -> {
			if (isAlive() && t.isAlive() && distanceTo(t) <= profile.reach + 0.8) strike(level, t, false);
		});
	}

	// ---- Huntmaster: Lane Runner bow, wolves --------------------------------------------------------------------------------------------

	/** Shows the bow in hand while drawing and the hunting axe otherwise. */
	public void drawBow(boolean on) {
		drawingBow = on;
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(on ? Items.BOW : Items.IRON_AXE));
		if (on) startUsingItem(InteractionHand.MAIN_HAND);
		else stopUsingItem();
	}

	/** A hornbow arrow: raw damage as armour-reduced base damage, spread 3.0 (+0.12 per block past 12, cap 8.0) plus {@code extraSpread}; {@code pin} adds Slowness I 2 s. */
	public void shootArrow(LivingEntity t, float raw, float extraSpread, boolean pin) {
		if (!isAlive() || !t.isAlive()) return;
		ServerLevel level = sl();
		double dist = distanceTo(t);
		float spread = (float) Math.min(8.0, 3.0 + Math.max(0, dist - 12) * 0.12) + extraSpread;
		Arrow a = new Arrow(level, this, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		Vec3 from = getEyePosition();
		Vec3 to = t.getEyePosition().add(t.getDeltaMovement().scale(Math.min(8.0, dist / 3.0)));
		Vec3 dir = to.subtract(from);
		a.setPos(from.x, from.y - 0.1, from.z);
		a.setBaseDamage(raw / 3.0);
		a.pickup = AbstractArrow.Pickup.DISALLOWED;
		if (pin) a.addTag(HrafnholtGear.HORNBOW_TAG);
		a.shoot(dir.x, dir.y + Math.sqrt(dir.x * dir.x + dir.z * dir.z) * 0.04, dir.z, 3.0F, spread);
		level.addFreshEntity(a);
		swingForAttack(InteractionHand.MAIN_HAND);
		level.playSound(null, blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 0.8F);
		bump("arrow");
	}

	private void huntmasterTick(ServerLevel level, @Nullable LivingEntity t, long now) {
		if (t == null) return;
		double dist = distanceTo(t);
		// ordinary hornbow shots on the move: draw 17, raw 14, one per 30 ticks, spread +2.0
		if (drawLeft > 0) {
			if (--drawLeft == 0) {
				if (t.isAlive() && hasLineOfSight(t) && !telegraph.active()) shootArrow(t, 14F, 2.0F, false);
				drawBow(false);
			}
		} else if (idle() && !moving() && !lsPending && now >= nextShot && dist >= 5.0 && dist <= 30.0 && hasLineOfSight(t) && !drawingBow) {
			drawLeft = 17;
			nextShot = now + 30 + 17;
			drawBow(true);
		}
		// Wolf Tend: feed the nearest wounded wolf (24-tick animation, heals 25 percent of its max) once per 200 ticks
		if (tendAt != 0 && now >= tendAt) {
			tendAt = 0;
			for (UUID id : wolves) if (level.getEntity(id) instanceof Wolf w && w.isAlive() && w.getHealth() < w.getMaxHealth() && w.distanceTo(this) < 8) {
				w.heal(w.getMaxHealth() * 0.25F);
				level.sendParticles(ParticleTypes.HEART, w.getX(), w.getY() + 1, w.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
				break;
			}
		} else if (tendAt == 0 && now >= tendReadyAt && idle() && !moving()) {
			for (UUID id : wolves) if (level.getEntity(id) instanceof Wolf w && w.isAlive() && w.getHealth() < w.getMaxHealth() * 0.9F && w.distanceTo(this) < 8) {
				tendAt = now + 24;
				tendReadyAt = now + 200;
				swingForAttack(InteractionHand.OFF_HAND);
				level.playSound(null, blockPosition(), SoundEvents.WOLF_GROWL_BABY.value(), SoundSource.HOSTILE, 0.6F, 1.8F);
				break;
			}
		}
	}

	// ---- wolves (Eydis) ---------------------------------------------------------------------------------------------------------

	public void spawnWolves(ServerLevel level, int n) {
		for (int i = 0; i < n; i++) {
			Wolf w = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (w == null) continue;
			double a = random.nextDouble() * Math.PI * 2;
			w.snapTo(getX() - Math.sin(getYRot() * Math.PI / 180.0) * 2 + Math.cos(a), getY(), getZ() + Math.cos(getYRot() * Math.PI / 180.0) * 2 + Math.sin(a), getYRot(), 0F);
			w.setCustomName(Component.translatable("entity.elsewhere.hrafnholt_hound"));
			w.addTag(WOLF_TAG);
			w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0);
			w.setHealth(30.0F);
			w.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0);   // raw 10 bite after armour
			w.setPersistenceRequired();
			level.addFreshEntity(w);
			wolves.add(w.getUUID());
			wolvesSpawned++;
		}
	}

	void secondWave(ServerLevel level) {
		if (wavesDone >= 1) return;
		wavesDone++;
		int alive = 0;
		for (UUID id : wolves) if (level.getEntity(id) instanceof Wolf w && w.isAlive()) alive++;
		spawnWolves(level, Math.max(1, 2 - alive));                       // one wolf respawns once at 50 percent HP
		level.playSound(null, blockPosition(), SoundEvents.WOLF_GROWL_BABY.value(), SoundSource.HOSTILE, 1.5F, 0.5F);
	}

	void tendWolves(ServerLevel level, @Nullable LivingEntity target) {
		Wolf nearest = null;
		double nd = 1e9;
		for (int i = wolves.size() - 1; i >= 0; i--) {
			var e = level.getEntity(wolves.get(i));
			if (!(e instanceof Wolf w) || !w.isAlive()) {
				if (e == null || !e.isAlive()) wolves.remove(i);
				continue;
			}
			if (target != null && target.isAlive()) {
				if (w.getTarget() != target) w.setTarget(target);
				double d = w.distanceToSqr(target);
				if (d < nd) {
					nd = d;
					nearest = w;
				}
			} else {
				if (w.getTarget() != null) w.setTarget(null);
				if (w.distanceToSqr(this) > 36) w.getNavigation().moveTo(this, 1.2);
			}
			if (w.distanceToSqr(this) > 40 * 40) w.teleportTo(getX(), getY(), getZ());
		}
		// Wolf Screen: when you close inside 5 blocks the nearer wolf cuts across your path and body-blocks (1 block wide)
		if (nearest != null && target != null && distanceTo(target) < screenRadius()) {
			Vec3 between = position().add(target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.6));
			nearest.getNavigation().moveTo(between.x, between.y, between.z, 1.5);
		}
	}

	public int wolvesAlive() {
		int n = 0;
		if (level() instanceof ServerLevel l) for (UUID id : wolves) if (l.getEntity(id) instanceof Wolf w && w.isAlive()) n++;
		return n;
	}

	void releaseWolves() {
		if (level() instanceof ServerLevel l) for (UUID id : wolves) {
			var e = l.getEntity(id);
			if (e != null) e.discard();
		}
		wolves.clear();
	}

	// ---- Movement slot ------------------------------------------------------------------------------------------------------------------

	/**
	 * The card's movement. Jarl: Glacier March. Hersir: Flank Circle (the kit's strafe, her numbers). Hallwarden: Door Hold. Skald: plain approach
	 * (the Verse Skip loop is {@link #beginSkip}). Gudrun: Straight Charge. Huntmaster: Lane Runner.
	 */
	@Override
	protected void move(LivingEntity t, double dist) {
		if (moving()) return;                                                                // a rush or a leap owns the legs
		if (lastStandBusy() || (lsInvulnUntil > now() && h != Hrafn.HUNTMASTER)) {
			plant();
			return;
		}
		switch (h) {
			case JARL -> glacierMarch(t, dist);
			case HALLWARDEN -> doorHold(t, dist);
			case CHIEF -> straightCharge(t, dist);
			case HUNTMASTER -> laneRunner(t, dist);
			default -> super.move(t, dist);
		}
	}

	/** Stops all walking (navigation and the move control). */
	void plant() {
		getNavigation().stop();
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		Vec3 v = getDeltaMovement();
		if (onGround()) setDeltaMovement(0, v.y, 0);
	}

	/** Turns the heading toward {@code want} by at most {@code maxTurn} degrees and runs {@code speed} blocks per tick along it. Falls back to path-finding if blocked for 8 ticks. */
	void steer(Vec3 want, double speed, double maxTurn, @Nullable LivingEntity pathTarget) {
		if (Float.isNaN(heading)) heading = getYRot();
		if (want.lengthSqr() > 1e-6) {
			float wantYaw = (float) (Mth.atan2(-want.x, want.z) * Mth.RAD_TO_DEG);
			heading += Mth.clamp(Mth.wrapDegrees(wantYaw - heading), (float) -maxTurn, (float) maxTurn);
		}
		setYRot(heading);
		setYHeadRot(heading);
		yBodyRot = heading;
		if (speed <= 0) {
			plant();
			return;
		}
		if (horizontalCollision && getDeltaMovement().horizontalDistanceSqr() < 0.0004) stuck++;
		else stuck = 0;
		if (stuck > 8 && pathTarget != null) {                                           // a wall in the way: let the path-finder walk round it
			getNavigation().moveTo(pathTarget, 1.0);
			if (stuck > 40) stuck = 0;
			return;
		}
		getNavigation().stop();
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		double rad = heading * Mth.DEG_TO_RAD;
		Vec3 v = getDeltaMovement();
		setDeltaMovement(-Math.sin(rad) * speed, v.y, Math.cos(rad) * speed);
	}

	/** Glacier March: straight at you at 0.20 blocks per tick (Winter Rage 0.26), turns at most 4 degrees per tick, never strafes, retreats or jumps; shield up while marching. */
	private void glacierMarch(LivingEntity t, double dist) {
		Vec3 want = t.position().subtract(position()).multiply(1, 0, 1);
		boolean close = dist <= profile.reach - 0.4;
		double speed = close ? 0 : rage ? 0.26 : 0.20;
		if (!splintered && !rage && dist > profile.reach + 1.5 && shieldDisabledTicks() == 0) forceShield(4);
		steer(want, speed, 4.0, t);
	}

	/** Door Hold: holds the Door Mark, moves at most 3 blocks from it at 0.10 blocks per tick turning 8 degrees per tick; marches at 0.12 after 200 ticks of you staying beyond 12 blocks. */
	private void doorHold(LivingEntity t, double dist) {
		if (doorMark == null) doorMark = position();
		if (rooted()) {
			steer(t.position().subtract(position()), 0, 0.0, null);                     // Door Barred: rooted, no turning either
			return;
		}
		if (dist > 12.0) farTicks++;
		else farTicks = 0;
		Vec3 want = t.position().subtract(position()).multiply(1, 0, 1);
		if (!marching && farTicks >= 200) marching = true;
		if (marching) {
			if (dist <= 12.0 && ready("sig:door_breaker")) doorMarkHere();               // a Door-Breaker is ready: plant a new Door Mark here
			else {
				if (shieldDisabledTicks() == 0) forceShield(4);
				steer(want, 0.12, 8.0, t);
				return;
			}
		}
		Vec3 next = position().add(want.normalize().scale(0.3));
		boolean inRange = next.subtract(doorMark).multiply(1, 0, 1).length() <= 3.0;
		double speed = dist > profile.reach - 0.3 && inRange ? 0.10 : 0;
		if (speed == 0 && shieldDisabledTicks() == 0 && !telegraph.active()) forceShield(4);   // Ward Wall: shield up whenever static
		steer(want, speed, 8.0, null);
	}

	/** Straight Charge: picks a heading, runs up to 14 blocks at 0.30 (Frenzy up to 0.36; Pelt Shed +30 percent), re-aims only in a 10-tick pause at the end of a run. */
	private void straightCharge(LivingEntity t, double dist) {
		if (pauseLeft > 0) {
			pauseLeft--;
			plant();
			if (pauseLeft == 0) aim = null;
			return;
		}
		if (aim == null) {
			Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
			aim = d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize() : d.normalize();
			runLeft = 14.0;
		}
		double frenzy = Mth.clamp(1.0 - getHealth() / getMaxHealth(), 0.0, 1.0);
		double speed = 0.30 + 0.06 * frenzy;
		if (shed()) speed *= 1.30;
		heading = (float) (Mth.atan2(-aim.x, aim.z) * Mth.RAD_TO_DEG);
		boolean blocked = horizontalCollision && getDeltaMovement().horizontalDistanceSqr() < 0.0004 || isInWater();
		if (dist <= profile.reach - 0.6 || blocked || runLeft <= 0) {
			pauseLeft = 10;                                                                // a wall, water or the end of the run: stand and swing until the pause ends
			plant();
			return;
		}
		runLeft -= speed;
		steer(aim, speed, 360.0, null);
	}

	/** Lane Runner: runs parallel to you at 9 to 11 blocks at full sprint, swapping lanes (which side the wolves are on) every 40 to 60 ticks; the axe only inside 4 blocks. */
	private void laneRunner(LivingEntity t, double dist) {
		if (--laneFlip <= 0) {
			laneSide = -laneSide;
			laneFlip = 40 + random.nextInt(21);
			bump("lane_swap");
		}
		Vec3 r = position().subtract(t.position()).multiply(1, 0, 1);
		if (r.lengthSqr() < 1e-4) r = new Vec3(1, 0, 0);
		Vec3 rn = r.normalize();
		Vec3 tangent = new Vec3(-rn.z, 0, rn.x).scale(laneSide);
		double err = dist - 10.0;                                                           // pull toward the 9 to 11 block band
		Vec3 dir = tangent.add(rn.scale(-Mth.clamp(err / 4.0, -1.0, 1.0)));
		if (dist <= 3.4) {                                                                  // cornered: the axe comes out
			steer(t.position().subtract(position()), 0, 30.0, null);
			return;
		}
		steer(dir, 0.27, 30.0, t);
	}

	// ---- motion: charges and leaps ------------------------------------------------------------------------------------------------

	/** A straight run in a locked direction that ends on the first victim it touches, a wall or its length. */
	public static final class Rush {
		public Vec3 dir;
		public double speed;
		public int ticks, recovery;
		public boolean through;
		public Consumer<LivingEntity> onContact = e -> {};
		public Runnable onEnd = () -> {};
	}

	/** A jump to a spot: up impulse, a steady horizontal speed, {@code onLand} on touchdown. */
	public static final class Leap {
		public Vec3 dest;
		public int age, recovery;
		public double speed;
		public boolean staggerable;
		public Runnable onLand = () -> {};
	}

	public boolean moving() {
		return rush != null || leap != null;
	}

	public void startRush(Vec3 dir, double speed, int ticks, int recovery, Consumer<LivingEntity> onContact, Runnable onEnd) {
		Rush r = new Rush();
		r.dir = new Vec3(dir.x, 0, dir.z).normalize();
		r.speed = speed;
		r.ticks = ticks;
		r.recovery = recovery;
		r.onContact = onContact;
		r.onEnd = onEnd;
		rush = r;
		setSprinting(true);
	}

	/** Furrow Run: a pass that keeps running after it hits (raw once per pass), cannot turn. */
	public void startPass(Vec3 dir, double speed, int ticks, int recovery, Consumer<LivingEntity> onContact, Runnable onEnd) {
		startRush(dir, speed, ticks, recovery, onContact, onEnd);
		rush.through = true;
	}

	public void startLeap(Vec3 dest, double up, int recovery, boolean staggerable, Runnable onLand) {
		startLeap(dest, up, 0.6, recovery, staggerable, onLand);
	}

	public void startLeap(Vec3 dest, double up, double speed, int recovery, boolean staggerable, Runnable onLand) {
		Leap l = new Leap();
		l.staggerable = staggerable;
		l.dest = dest;
		l.speed = speed;
		l.recovery = recovery;
		l.onLand = onLand;
		leap = l;
		Vec3 d = dest.subtract(position()).multiply(1, 0, 1);
		Vec3 v = d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(Math.min(0.9, d.length() / 11.0));
		setDeltaMovement(v.x, up, v.z);
		needsSync = true;
	}

	private void tickRush(ServerLevel level) {
		Rush r = rush;
		if (--r.ticks < 0 || isStaggered()) {
			endRush(r);
			return;
		}
		getNavigation().stop();
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		setDeltaMovement(r.dir.x * r.speed, getDeltaMovement().y, r.dir.z * r.speed);
		float yaw = (float) (Math.atan2(-r.dir.x, r.dir.z) * 180.0 / Math.PI);
		setYRot(yaw);
		setYHeadRot(yaw);
		yBodyRot = yaw;
		if (tickCount % 2 == 0) level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 2, 0.2, 0.05, 0.2, 0.01);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.7, 0.1, 0.7), this::victim)) {
			r.onContact.accept(e);
			if (!r.through) {
				endRush(r);
				return;
			}
			r.onContact = x -> {};                                                         // raw 22 once per pass
		}
		if (horizontalCollision && r.ticks < 200) endRush(r);
	}

	private void endRush(Rush r) {
		rush = null;
		setSprinting(false);
		if (r.recovery > 0) addEffect(new MobEffectInstance(MobEffects.SLOWNESS, r.recovery, 6, false, false));
		r.onEnd.run();
	}

	private void tickLeap(ServerLevel level) {
		Leap l = leap;
		l.age++;
		getNavigation().stop();
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		Vec3 d = l.dest.subtract(position()).multiply(1, 0, 1);
		if (l.age <= 30 && !onGround()) {
			double step = Math.min(0.9, d.length() / 4.0);
			Vec3 v = d.lengthSqr() < 0.04 ? Vec3.ZERO : d.normalize().scale(Math.max(0.05, Math.min(0.9, d.length() > 3 ? l.speed : step)));
			setDeltaMovement(v.x, getDeltaMovement().y, v.z);
		}
		if ((l.age > 3 && onGround()) || l.age > 44) {
			leap = null;
			if (l.recovery > 0) addEffect(new MobEffectInstance(MobEffects.SLOWNESS, l.recovery, 6, false, false));
			l.onLand.run();
		}
	}

	/** Cancels a leap or charge (a stagger). */
	public void stopMotion() {
		rush = null;
		leap = null;
	}

	boolean victim(LivingEntity e) {
		if (e == this || e instanceof HumanBoss || !e.isAlive()) return false;
		if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
		return e == getTarget();
	}

	public List<LivingEntity> foes(ServerLevel level, Vec3 c, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c.x - radius, c.y - 1.0, c.z - radius, c.x + radius, c.y + 2.5, c.z + radius),
			e -> victim(e) && e.position().subtract(c).multiply(1, 0, 1).lengthSqr() <= radius * radius);
	}

	// ---- drops and cleanup ---------------------------------------------------------------------------------------------------------

	@Override
	protected void onDefeated(ServerLevel level, List<ServerPlayer> participants) {
		DuelBlocks.clearOwner(level, getUUID());
		releaseWolves();
		endPool();
		if (standardId != null) {
			var e = level.getEntity(standardId);
			if (e != null) e.discard();
			HrafnholtStandards.forget(standardId);
			standardId = null;
		}
		if (Leaders.of(this) != null) return;       // the court rolls the faction file's drops (DropRule, Seal, Hacksilver) for its own leaders
		try {
			CourtState st = CourtState.get(level.getServer(), HrafnholtContent.FACTION);
			Ruler slot = Ruler.byKey(h.courtSlot);
			if (slot == null) return;
			ServerPlayer killer = participants.isEmpty() ? null : participants.get(0);
			for (ItemStack s : CourtDrops.rollRival(level, st, slot, position(), killer)) {
				ItemEntity ie = new ItemEntity(level, getX(), getY() + 0.5, getZ(), s);
				level.addFreshEntity(ie);
			}
		} catch (RuntimeException e) {
			HrafnholtContent.LOG.warn("boss drops failed for {}: {}", h.key, e.toString());
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy()) {
			releaseWolves();
			poolBar.removeAllPlayers();
			if (standardId != null && level() instanceof ServerLevel l) {
				var e = l.getEntity(standardId);
				if (e != null) e.discard();
				HrafnholtStandards.forget(standardId);
			}
		}
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putBoolean("HrafnRisen", isRisen());
		out.putBoolean("HrafnRage", rage);
		out.putBoolean("HrafnHolder", holder);
		out.putBoolean("HrafnLsDone", lsDone);
		out.putBoolean("HrafnSplinter", splintered);
		out.putFloat("HrafnShieldHp", shieldHp);
		out.putInt("HrafnWolves", wolvesSpawned);
		out.putInt("HrafnWaves", wavesDone);
		out.putBoolean("HrafnHorn0", hornDone[0]);
		out.putBoolean("HrafnHorn1", hornDone[1]);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		if (in.getBooleanOr("HrafnRisen", false)) this.entityData.set(DATA_RISEN, true);
		rage = in.getBooleanOr("HrafnRage", false);
		holder = in.getBooleanOr("HrafnHolder", holder);
		lsDone = in.getBooleanOr("HrafnLsDone", false);
		splintered = in.getBooleanOr("HrafnSplinter", false);
		shieldHp = in.getFloatOr("HrafnShieldHp", 120F);
		wolvesSpawned = in.getIntOr("HrafnWolves", 0);
		wavesDone = in.getIntOr("HrafnWaves", 0);
		hornDone[0] = in.getBooleanOr("HrafnHorn0", false);
		hornDone[1] = in.getBooleanOr("HrafnHorn1", false);
		if (splintered) setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}
}
