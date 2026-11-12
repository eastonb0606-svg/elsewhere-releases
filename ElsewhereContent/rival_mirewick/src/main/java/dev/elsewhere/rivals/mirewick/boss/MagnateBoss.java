package dev.elsewhere.rivals.mirewick.boss;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.HumanBossHealth;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.kit.GroundPatch;
import dev.elsewhere.duelist.kit.Timers;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EquipmentSlot;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.api.Oligarchy;
import dev.elsewhere.kingdom_court.api.Succession;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Magnate of the Lantern Council, built on the duelist kit ({@link HumanBoss}: strafe, crit, shield, swap, bows, eat and
 * drink, pearl, temporary blocks, Totem, duel yield, telegraphs) with its own signature moves ({@link MagnateProfiles}) and the
 * council rules (RIVAL-BOSSES "Council rules"): harming one convenes the others (each with 2 escorts and its own target), allies'
 * fire and walls obstruct each other, the Almoner heals only whoever paid him last, the richest in a fight flees at 40 percent
 * and returns at +10 percent, and the Chair holds the First Lantern. The court lane applies the Chair's ruler bonus and the Totem
 * status through {@link Leaders.LeaderBoss#onHolder}, and rolls the faction file's drops; this class only adds the Chair table.
 */
public class MagnateBoss extends HumanBoss implements Leaders.LeaderBoss {
	public final Magnate m;
	public boolean chair, lanternLit = true, convened, escorted, fleeing, riteBroken;
	public long lastConvene, fleeUntil, lanternOutUntil, braceUntil;
	public int castsLeft = 3, payments, hopsDone;
	public boolean secondThrust;
	public final Map<Integer, Long> braceHit = new java.util.HashMap<>();
	// Marrow: the debt-hook and its tether
	public long garnishUntil, pulledAt = -1000, shoutUntil, doubleDebtUntil;
	@Nullable UUID tetherTarget;
	int tetherHits, leechedTick;
	float leechedFraction;
	final List<Long> hitsOnMe = new ArrayList<>();
	// Hilde: her wall, the open moment after the maul, the Sluice Open ring
	public Vec3 wallAt;
	public long wallUntil, openUntil, staggerImmuneUntil;
	// Garrick: the Mire Flood and the first-strike eel pie
	public long floodUntil;
	Vec3 floodAt;
	// Wren: reed patches, the dive, nets, the Reed Screen
	final List<Object[]> reeds = new ArrayList<>();
	public final List<DuelistProjectile> nets = new ArrayList<>();
	public long diveUntil, screenUntil, lastHurtAt;
	Vec3 screenAt;
	float regenFraction;
	@Nullable Vec3 waterSpot;
	// Cuthbert: the hired hands and Paid Passage
	public final List<UUID> hands = new ArrayList<>();
	public int pairsHired;
	boolean passageUsed;
	// the Chair
	public long rallyUntil, rallyBonusUntil;
	boolean lastStandDone;
	private final List<Long> chairHits = new ArrayList<>();
	public final CouncilRules.Ledger ledger = new CouncilRules.Ledger();
	/** Fire patches laid by Magnates, kept so allies do not walk through each other's Lantern Fire. */
	private static final List<Object[]> FIRES = new ArrayList<>();

	public MagnateBoss(EntityType<? extends HumanBoss> type, Level level, Magnate m) {
		super(type, level, MagnateProfiles.of(m));
		this.m = m;
		// RIVALS-DECISIONS 13: health is capped at 300, so the fight is kept dangerous with armour, knockback resistance and damage
		bonus(Attributes.ARMOR, 6.0, "mirewick_cap_armor");
		bonus(Attributes.ARMOR_TOUGHNESS, 3.0, "mirewick_cap_tough");
		bonus(Attributes.KNOCKBACK_RESISTANCE, 0.4, "mirewick_cap_kb");
		if (m == Magnate.TOLLIVER) {
			// Reed Wader: no slowdown in water, and her path cost prefers it
			AttributeInstance we = getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
			if (we != null) we.setBaseValue(1.0);
			setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0F);
		}
		clampHealth();                                                                  // spawn and load: the cap once, after every constructor modifier
	}

	public static void init() {
		DuelistContent.init();
		for (Magnate mg : Magnate.values()) {
			final Magnate fm = mg;
			mg.type = DuelistContent.registerBoss("mirewick_" + mg.key, (t, l) -> new MagnateBoss(t, l, fm), MagnateProfiles.of(mg));
			Leaders.register(MirewickContent.FACTION, mg.courtSlot, mg.type);
		}
		Oligarchy.registerLeanings(MirewickContent.FACTION, List.of(Oligarchy.Leaning.HAWK, Oligarchy.Leaning.DOVE, Oligarchy.Leaning.MISER, Oligarchy.Leaning.HAWK, Oligarchy.Leaning.MISER));
		Succession.registerHeirNames(MirewickContent.FACTION, Heirs.courtNameList());
	}

	/** Spawns a Magnate (a boss: no soldier caps). */
	public static @Nullable MagnateBoss spawn(ServerLevel level, Magnate mg, Vec3 pos) {
		MagnateBoss b = mg.type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (b == null) return null;
		b.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360F, 0F);
		b.setHome(BlockPos.containing(pos));
		level.addFreshEntity(b);
		return b;
	}

	public static List<MagnateBoss> all(ServerLevel level) {
		List<MagnateBoss> out = new ArrayList<>();
		out.addAll(level.getEntities(EntityTypeTest.forClass(MagnateBoss.class), e -> e.isAlive() && !e.yielded()));
		return out;
	}

	// ---- loadout --------------------------------------------------------------------------------------------------------

	@Override
	protected void applyLoadout() {
		super.applyLoadout();
		if (!(level() instanceof ServerLevel sl)) return;
		var mats = sl.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		var pats = sl.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			Magnate.Piece p = m.pieces[i];
			ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(p.item())));
			if (p.dye() >= 0) st.set(DataComponents.DYED_COLOR, new DyedItemColor(p.dye()));
			var mh = mats.get(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(p.material())));
			var ph = pats.get(ResourceKey.create(Registries.TRIM_PATTERN, Identifier.withDefaultNamespace(p.pattern())));
			if (mh.isPresent() && ph.isPresent()) st.set(DataComponents.TRIM, new ArmorTrim(mh.get(), ph.get()));
			setItemSlot(slots[i], st);
			setDropChance(slots[i], 0.0F);
		}
		dev.elsewhere.rivals.mirewick.look.MirewickRulerLook.dress(this);   // 3.0 B10 (s-rulerarmor): purple, crimson and gold layer, no cyan diamond
		// the court names an heir (same surname, new first name) before this first-tick loadout runs: keep that name
		if (getCustomName() == null) setCustomName(Component.translatable(m.nameKey()));
		if (chair && lanternLit && m != Magnate.SORREL && getOffhandItem().isEmpty()) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN));
	}

	// ---- the Chair ----------------------------------------------------------------------------------------------------------

	/** The court calls this when the Chair changes: the holder carries the First Lantern, the Totem and the ruler bonus. */
	@Override
	public void onHolder(boolean holder) {
		chair = holder;
		bonus(Attributes.ARMOR, holder ? 8.0 : 6.0, "mirewick_cap_armor");
		bonus(Attributes.ARMOR_TOUGHNESS, holder ? 4.0 : 3.0, "mirewick_cap_tough");
		if (holder && lanternLit && getOffhandItem().isEmpty() && m != Magnate.SORREL) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN));
		if (!holder && getOffhandItem().is(Items.LANTERN)) setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		clampHealth();                                                                  // a Chair change: one check (the court's ruler bonus moved the base just before)
	}

	/** True when another lit Chair within 24 blocks grants this Magnate -10 percent damage taken. */
	float lanternMitigation(ServerLevel level) {
		for (MagnateBoss o : all(level)) if (o != this && o.chair && o.lanternLit && o.distanceToSqr(this) < 24 * 24) return 0.90F;
		return 1.0F;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource src, float dmg) {
		Entity att = src.getEntity();
		// no friendly fire: the kingdom's own (non-hostile) soldiers and their arrows never hurt a Magnate (same rule as KingdomSoldier and AzmarBoss)
		if (att instanceof KingdomSoldier ks && !dev.elsewhere.kingdom.Factions.hostile(ks.factionId(), MirewickContent.FACTION)) return false;
		if (dmg > 0 && att instanceof ServerPlayer sp && !isDuel() && !yielded()) convene(level, sp);
		if (dmg > 0 && telegraph.active() && "dusk_rite".equals(telegraph.id)) riteBroken = true;
		long gnow = level.getGameTime();
		if (dmg > 0) {
			lastHurtAt = gnow;
			hitsOnMe.add(gnow);
			hitsOnMe.removeIf(t -> gnow - t > 80);
			if (garnishUntil > gnow && hitsOnMe.stream().filter(t -> gnow - t <= 40).count() >= 3) breakGarnish(level);   // three hits in 40 ticks cut the tether
			boolean arrow = src.is(DamageTypeTags.IS_PROJECTILE);
			float mult = 1F;
			if (m == Magnate.MARROW && arrow) mult *= 0.70F;                                                            // Oilcloth Coat
			if (m == Magnate.SORREL && fromBehind(att)) mult *= 1.25F;                                                  // her weak side
			if (m == Magnate.TOLLIVER && arrow) {
				if (inReed(level)) mult *= 0.60F;                                                                       // Water Hide
				if (inScreen()) mult *= 0.70F;                                                                          // Reed Screen
			}
			if (shoutUntil > gnow) mult *= 1.10F;
			dmg *= mult;
		}
		if (chair && lanternLit && dmg > 0) {
			long now = gnow;
			chairHits.add(now);
			chairHits.removeIf(t -> now - t > 80);
			if (chairHits.size() >= 3) {
				lanternOutUntil = now + 400;
				chairHits.clear();
				for (MagnateBoss o : all(level)) o.rallyBonusUntil = 0;                      // the damage bonus goes out with the Lantern
				lanternLit = false;
				for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 48 * 48) p.sendOverlayMessage(Component.translatable("message.elsewhere.mirewick_chair_lantern_out"));
			}
		}
		return super.hurtServer(level, src, dmg * lanternMitigation(level));
	}

	// ---- identity slots: movement, defence, recovery, last stand (RIVAL-BOSSES v3 cards) --------------------------------------------

	static final Identifier SCREEN_SPEED = MirewickContent.id("mirewick_screen_speed");

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!isAlive()) return;
		long now = level.getGameTime();
		upkeep(level, now);
		if (tickCount % 10 == 0) squabble(level, now);
		if (passive) return;
		LivingEntity t = getTarget();
		if (t == null || yielded() || isStaggered() || inLastStand() || isHealing() || fleeing) return;
		steer(level, t, now);
		act(level, t, now);
	}

	void bonus(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v, String id) {
		AttributeInstance ai = getAttribute(a);
		if (ai != null) ai.addOrUpdateTransientModifier(new AttributeModifier(MirewickContent.id(id), v, AttributeModifier.Operation.ADD_VALUE));
		if (a.is(Attributes.MAX_HEALTH)) clampHealth();                                 // a max-health change made here is capped at once
	}

	/**
	 * The hard 300 cap on a human boss's max health, after every modifier (court bonus, Chair, heirs, Risen, Health Boost, the Test Meter).
	 * RIVALS-DECISIONS 13, 17 and 19: one check when something changes, never on a timer. It runs on spawn and load (constructor), on a
	 * Chair change ({@link #onHolder}) and whenever the max-health attribute changes for any reason ({@link #onAttributeUpdated}).
	 * It uses the shared duelist cap ({@link HumanBossHealth}): one transient modifier {@code elsewhere:human_hp_cap}, the same id the
	 * court's HpCap writes, so the base value the court sets is never touched and the cap lifts again when the boosts go.
	 * Healing cannot pass it: vanilla clamps health to max health. Counted as the boss stat {@code hp_cap_check} for the self-test.
	 */
	void clampHealth() {
		bump("hp_cap_check");
		HumanBossHealth.enforce(this);
	}

	/**
	 * Vanilla calls this from {@code refreshDirtyAttributes} (the entity's tick and effect changes) once per changed attribute. The cap
	 * writes only the max-health instance, which is the one being visited and is already in the update set, so the write cannot
	 * disturb that loop and does not call back here. Server only: the client receives the capped attribute.
	 */
	@Override
	protected void onAttributeUpdated(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		super.onAttributeUpdated(attribute);
		if (attribute.is(Attributes.MAX_HEALTH) && !level().isClientSide()) clampHealth();
	}

	public boolean doubleDebtActive() {
		return level().getGameTime() < doubleDebtUntil;
	}

	public float pikeReach(ServerLevel level) {
		return floodUntil > level.getGameTime() ? 6.5F : 5.5F;
	}

	public void faceNow(LivingEntity t) {
		getLookControl().setLookAt(t, 60F, 60F);
	}

	/** RIVALS-DECISIONS 13 compensation: cooldowns x0.8, defensive behaviours x(1/1.3) (30 percent more often). */
	public static final float COOLDOWN_SCALE = 0.8F, DEFENCE_SCALE = 1F / 1.3F;
	private static final java.util.Set<String> DEFENSIVE = java.util.Set.of("sig:pike_brace", "sig:dike_wall", "sig:pay_the_hands", "sig:heron_flight", "sig:reed_dive", "dodge", "guard", "withdraw", "brace");

	@Override
	public void setCooldown(String id, int ticks) {
		super.setCooldown(id, ticks <= 0 ? ticks : Math.round(ticks * (DEFENSIVE.contains(id) ? DEFENCE_SCALE : COOLDOWN_SCALE)));
	}
	@Override
	public float damageMultiplier() {
		float f = super.damageMultiplier() * 1.25F;
		return level().getGameTime() < rallyBonusUntil ? f * 1.15F : f;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource s) {
		long now = level.getGameTime();
		if (!s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (rallyUntil > now) return true;
			if (m == Magnate.MARROW && s.is(DamageTypeTags.IS_FIRE)) return true;            // Oilcloth Coat
			if (m == Magnate.TOLLIVER && diveUntil > now && s.is(DamageTypeTags.IS_PROJECTILE)) return true;
		}
		return super.isInvulnerableTo(level, s);
	}

	@Override
	public boolean hurtRaw(ServerLevel level, LivingEntity victim, float raw, DamageCaps kind, DamageSource src) {
		long now = level.getGameTime();
		if (m == Magnate.THWAITE && kind == DamageCaps.MINOR) raw = distanceTo(victim) < 2.0 ? 14F : 16F;      // the belt hammer inside 2 blocks
		boolean tethered = garnishUntil > now && victim.getUUID().equals(tetherTarget);
		if (m == Magnate.MARROW && kind == DamageCaps.MINOR && tethered) raw *= 1.0F + Math.min(0.30F, 0.10F * tetherHits);
		boolean hit = super.hurtRaw(level, victim, raw, kind, src);
		if (hit && m == Magnate.MARROW && tethered && kind == DamageCaps.MINOR) {
			tetherHits++;
			leech(level);
		}
		return hit;
	}

	/** Interest Leech: 1 percent of max HP per hit on the tethered target, 5 percent per tether (double in Double Debt). */
	void leech(ServerLevel level) {
		float k = doubleDebtActive() ? 2F : 1F, cap = 0.05F * k;
		if (leechedFraction >= cap) return;
		float add = Math.min(0.01F * k, cap - leechedFraction);
		leechedFraction += add;
		heal(getMaxHealth() * add);
		Entity t = tetherTarget == null ? null : level.getEntity(tetherTarget);
		if (t != null) for (int i = 1; i <= 6; i++) level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET), t.getX() + (getX() - t.getX()) * i / 6.0, t.getY() + 1.0 + (getY() - t.getY()) * i / 6.0, t.getZ() + (getZ() - t.getZ()) * i / 6.0, 1, 0.05, 0.05, 0.05, 0.0);
		bump("interest_leech");
	}

	public void startGarnish(ServerLevel level, LivingEntity t) {
		tetherTarget = t.getUUID();
		garnishUntil = level.getGameTime() + 60;
		tetherHits = 0;
		leechedFraction = 0F;
		hitsOnMe.clear();
		setEnabled(dev.elsewhere.duelist.kit.Move.STRAFE, false);              // she closes to cutlass range while the tether holds
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.mirewick.Sfx3.pick("magnate", "clock", SoundEvents.CHAIN_PLACE), SoundSource.HOSTILE, 1.5F, dev.elsewhere.rivals.mirewick.Sfx3.enabled() ? 1.0F : 0.7F);
	}

	void breakGarnish(ServerLevel level) {
		garnishUntil = 0;
		tetherTarget = null;
		setEnabled(dev.elsewhere.duelist.kit.Move.STRAFE, true);
		level.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
	}

	boolean fromBehind(@Nullable Entity att) {
		if (att == null) return false;
		Vec3 look = getViewVector(1.0F).multiply(1, 0, 1);
		Vec3 to = att.position().subtract(position()).multiply(1, 0, 1);
		if (look.lengthSqr() < 1e-4 || to.lengthSqr() < 1e-4) return false;
		return look.normalize().dot(to.normalize()) < -0.34;                    // more than 110 degrees off her facing
	}

	public boolean inReed(ServerLevel level) {
		if (isInWater()) return true;
		long now = level.getGameTime();
		for (Object[] r : reeds) if ((long) r[2] > now && position().distanceToSqr((Vec3) r[0]) <= (double) r[1] * (double) r[1]) return true;
		return false;
	}

	boolean inScreen() {
		return screenAt != null && level().getGameTime() < screenUntil && position().distanceToSqr(screenAt) <= 64.0;
	}

	/** Wren's own Reed Patch: a particle patch with no block edits, carried where there is no water. */
	void carryPatch(ServerLevel level) {
		long now = level.getGameTime();
		reeds.removeIf(r -> (long) r[2] <= now);
		if (!reeds.isEmpty()) return;
		Vec3 c = position();
		reeds.add(new Object[] {c, 3.0, now + 400});
		GroundPatch.spawn(level, this, MagnateProfiles.ground(level, c), 3.0, 0, 400, 20, 0F, List.of(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TALL_GRASS.defaultBlockState()));
	}

	/** Reed Dive: slips 8 blocks along water or her Reed Patch at 0.45 blocks per tick, untargetable by arrows for 30 ticks if she ends in water. */
	public void reedDive(ServerLevel level, LivingEntity t) {
		Vec3 dir = MagnateProfiles.dirTo(this, t).scale(-1);
		if (waterSpot != null) {
			Vec3 toW = waterSpot.subtract(position()).multiply(1, 0, 1);
			if (toW.length() > 3.0 && toW.normalize().dot(dir) > -0.2) dir = toW.normalize();
		}
		final Vec3 d = dir;
		getNavigation().stop();
		for (int i = 0; i < 18; i++) Timers.after(level.getServer(), i, () -> {
			if (!isAlive()) return;
			Vec3 v = getDeltaMovement();
			setDeltaMovement(d.x * 0.45, v.y, d.z * 0.45);
			needsSync = true;
		});
		Timers.after(level.getServer(), 19, () -> {
			if (!isAlive()) return;
			if (isInWater()) diveUntil = level.getGameTime() + 30;
			else carryPatch(level);
		});
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 20, 0.4, 0.2, 0.4, 0.1);
	}

	List<KingdomSoldier> handList(ServerLevel level) {
		List<KingdomSoldier> out = new ArrayList<>();
		hands.removeIf(id -> !(level.getEntity(id) instanceof KingdomSoldier s) || !s.isAlive());
		for (UUID id : hands) if (level.getEntity(id) instanceof KingdomSoldier s) out.add(s);
		return out;
	}

	public int livingHands(ServerLevel level) {
		return handList(level).size();
	}

	/** Pay the Hands: two Free Company brutes walk in from the nearest edge (refused by the 15/30 caps: nothing is paid). */
	public void hirePair(ServerLevel level, LivingEntity t) {
		int made = 0;
		double a0 = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < 2; i++) {
			double ang = a0 + i * 0.6, r = 14 + random.nextInt(4);
			Vec3 at = MagnateProfiles.ground(level, position().add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
			KingdomSoldier s = MagnateProfiles.spawnHand(level, at, t);
			if (s != null) {
				hands.add(s.getUUID());
				made++;
			}
		}
		if (made > 0) pairsHired++;
		else setCooldown("sig:pay_the_hands", 100);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET), getX(), getY() + 2.0, getZ(), 12, 0.4, 0.3, 0.4, 0.1);
	}

	// ---- movement slot ----------------------------------------------------------------------------------------------------------

	/** Runs after the kit's own movement each tick and replaces it with this Magnate's card movement. */
	void steer(ServerLevel level, LivingEntity t, long now) {
		double dist = distanceTo(t);
		boolean busy = telegraph.active();
		switch (m) {
			case MARROW -> {
				setSprinting(false);                                                      // she never sprints
				if (garnishUntil > now && !busy) getNavigation().moveTo(t, 1.0);
			}
			case THWAITE -> {
				setSprinting(false);
				if (busy) return;
				boolean hammer = getMainHandItem().is(Items.IRON_PICKAXE);
				if (dist < 2.0 && !hammer) {
					setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
					holdUntil = now + 7;
				} else if (dist > 3.2 && hammer && now >= holdUntil) {
					setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SPEAR));
					holdUntil = now + 7;
				}
				if (holdUntil > now) getNavigation().stop();
				else if (dist < 4.5) {                                                    // backpedal: holds 4.5 to 5.5 blocks, never strafing
					getNavigation().stop();
					getMoveControl().strafe(-1.0F, 0F);
				} else if (dist <= 5.5) getNavigation().stop();
			}
			case SORREL -> {
				setSprinting(false);
				getLookControl().setLookAt(t, 5F, 30F);                                  // she turns at 5 degrees per tick
				if (openUntil > now) {
					getNavigation().stop();
					return;
				}
				if (busy) return;
				if (wallUntil > now && wallAt != null) {
					Vec3 away = wallAt.subtract(t.position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
					Vec3 goal = wallAt.add(away.scale(1.8));
					if (position().distanceToSqr(goal.x, position().y, goal.z) > 1.5) getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
					else getNavigation().stop();
				}
			}
			case TOLLIVER -> {
				if (busy) return;
				boolean reed = inReed(level);
				if (tickCount % 20 == 0) waterSpot = findWater(level);
				if (waterSpot == null && tickCount % 40 == 0 && dist < 24) carryPatch(level);
				if (reed && dist > 4.0 && dist < 16.0) getNavigation().stop();             // she fights from the water
				else if (!reed && waterSpot != null && dist > 4.0 && tickCount % 5 == 0) getNavigation().moveTo(waterSpot.x, waterSpot.y, waterSpot.z, 1.0);
			}
			case AMES -> {
				setSprinting(false);
				if (busy) return;
				List<KingdomSoldier> hs = handList(level);
				if (hs.isEmpty()) {
					if (dist < 12.0) {
						getNavigation().stop();
						getMoveControl().strafe(-1.0F, 0F);                               // backs away to 12 blocks
					} else getNavigation().stop();
				} else {
					KingdomSoldier h = hs.get(0);
					for (KingdomSoldier o : hs) if (o.distanceToSqr(t) < h.distanceToSqr(t)) h = o;
					Vec3 line = h.position().subtract(t.position()).multiply(1, 0, 1);
					line = line.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : line.normalize();
					Vec3 goal = h.position().add(line.scale(3.0));
					double gd = Math.sqrt(goal.distanceToSqr(t.position()));
					if (gd < 8.0) goal = t.position().add(line.scale(9.0));
					else if (gd > 12.0) goal = t.position().add(line.scale(11.0));
					if (position().distanceToSqr(goal.x, position().y, goal.z) > 2.0) getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
					else getNavigation().stop();
				}
			}
		}
	}

	@Nullable Vec3 findWater(ServerLevel level) {
		BlockPos c = blockPosition();
		Vec3 best = null;
		double bd = Double.MAX_VALUE;
		for (int dx = -20; dx <= 20; dx += 2) for (int dz = -20; dz <= 20; dz += 2) for (int dy = -3; dy <= 2; dy++) {
			BlockPos p = c.offset(dx, dy, dz);
			if (!level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) continue;
			double d = p.distSqr(c);
			if (d < bd) {
				bd = d;
				best = Vec3.atBottomCenterOf(p);
			}
		}
		return best;
	}

	long holdUntil;

	/** Per-tick upkeep that must run even while the kit is mid wind-up: brace, tether, flood, regen, nets, speed screens. */
	void upkeep(ServerLevel level, long now) {
		if (braceUntil > now) MagnateProfiles.bracePunish(level, this);
		else if (braceUntil != 0) MagnateProfiles.braceEnd(this);
		if (m == Magnate.MARROW) {
			if (tickCount % 5 == 0 && hasEffect(MobEffects.SLOWNESS)) removeEffect(MobEffects.SLOWNESS);         // oil patches
			if (garnishUntil != 0 && (now >= garnishUntil || !(tetherTarget != null && level.getEntity(tetherTarget) instanceof LivingEntity tt && tt.isAlive()))) breakGarnish(level);
			if (garnishUntil > now && tetherTarget != null && level.getEntity(tetherTarget) instanceof LivingEntity tt) {
				Vec3 d = position().subtract(tt.position()).multiply(1, 0, 1);
				if (d.length() > 5.0) {                                                    // you cannot move more than 5 blocks from her
					Vec3 pull = d.normalize().scale(0.35);
					MagnateProfiles.kick(tt, new Vec3(pull.x, tt.getDeltaMovement().y, pull.z));
				}
				if (tickCount % 4 == 0) level.sendParticles(ParticleTypes.WAX_ON, (getX() + tt.getX()) / 2, getY() + 1.2, (getZ() + tt.getZ()) / 2, 2, Math.abs(d.x) / 4, 0.1, Math.abs(d.z) / 4, 0.0);
			}
		}
		if (m == Magnate.THWAITE && floodUntil > now && tickCount % 10 == 0 && floodAt != null) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(floodAt, floodAt).inflate(8.0, 3.0, 8.0))) {
				if (e.position().distanceToSqr(floodAt.x, e.getY(), floodAt.z) > 64.0) continue;
				if (e instanceof net.minecraft.world.entity.player.Player p && !p.isCreative() && !p.isSpectator()) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
				else if (e instanceof MagnateBoss || (e instanceof KingdomSoldier s && s.factionId().equals(MirewickContent.FACTION))) e.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0));
			}
		}
		if (m == Magnate.SORREL && tickCount % 5 == 0) setEnabled(dev.elsewhere.duelist.kit.Move.SHIELD, openUntil <= now);
		if (m == Magnate.TOLLIVER) {
			if (tickCount % 20 == 0 && inReed(level) && now - lastHurtAt > 60 && regenFraction < 0.10F) {            // Reed Breath
				heal(getMaxHealth() * 0.01F);
				regenFraction += 0.01F;
			}
			if (tickCount % 5 == 0) {
				AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
				if (sp != null) {
					if (inScreen()) sp.addOrUpdateTransientModifier(new AttributeModifier(SCREEN_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
					else sp.removeModifier(SCREEN_SPEED);
				}
			}
			for (var it = nets.iterator(); it.hasNext();) {
				DuelistProjectile p = it.next();
				if (p.isRemoved()) {
					it.remove();
					continue;
				}
				if (!p.hitSomething) continue;
				it.remove();
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.0), x -> x != this && x.isAlive() && (x instanceof net.minecraft.world.entity.player.Player))) {
					netted(level, e, now + 80);
					break;
				}
			}
		}
	}

	/** Netted: Slowness II and no sprint for 80 ticks; one weapon swing cuts the net. */
	void netted(ServerLevel level, LivingEntity e, long until) {
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
		bump("netted");
		level.playSound(null, e.blockPosition(), SoundEvents.COBWEB_PLACE, SoundSource.HOSTILE, 1.0F, 1.0F);
		netLoop(level, e, until);
	}

	private void netLoop(ServerLevel level, LivingEntity e, long until) {
		Timers.after(level.getServer(), 2, () -> {
			if (!e.isAlive() || level.getGameTime() >= until) return;
			if (e.isSwinging()) {
				e.removeEffect(MobEffects.SLOWNESS);
				return;
			}
			if (e instanceof net.minecraft.world.entity.player.Player p) p.setSprinting(false);
			netLoop(level, e, until);
		});
	}

	// ---- actions that are not signature moves: shove, flasks, Paid Passage ----------------------------------------------------------

	void act(ServerLevel level, LivingEntity t, long now) {
		if (m != Magnate.AMES || !idle() || fleeing) return;
		double dist = distanceTo(t);
		if (!passageUsed && dist <= 5.0 && livingHands(level) == 0 && pairsHired > 0 && hasLineOfSight(t)) {
			passageUsed = true;
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ENDER_PEARL));
			startTelegraph("paid_passage", "Paid Passage", 16, Telegraph.Weight.ORDINARY, 0F, style().cue(false, true), true, () -> {
				setItemSlot(EquipmentSlot.MAINHAND, profile.melee.get());
				if (MagnateProfiles.hop(this, 12, 20)) {
					hurtServer(level, level.damageSources().fall(), 2F);
					bump("pearl_passage");
				}
			});
			return;
		}
		if (dist <= 3.0 && now >= shoveReady) {                                        // lamp-pole shove
			shoveReady = now + 100;
			startTelegraph("lamp_shove", "Shove", 8, Telegraph.Weight.ORDINARY, 6F, style().cue(false, true), true, () -> {
				if (distanceTo(t) > 3.8 || !t.isAlive()) return;
				if (hurtRaw(level, t, 6F, DamageCaps.MINOR, level.damageSources().mobAttack(this))) t.knockback(4.0, -(t.getX() - getX()), -(t.getZ() - getZ()), level.damageSources().mobAttack(this), 0.0F);
				bump("shove");
			});
			return;
		}
		if (dist >= 5.0 && dist <= 12.0 && now >= flaskReady && hasLineOfSight(t)) {   // oil flask: slows, never damages, never ignites
			flaskReady = now + 100;
			startTelegraph("oil_flask", "Oil Flask", 14, Telegraph.Weight.ORDINARY, 0F, style().cue(false, true), true, () -> {
				if (!t.isAlive()) return;
				DuelistProjectile.splashAt(level, this, t, new ItemStack(Items.SPLASH_POTION), new DuelistProjectile.Splash(List.of(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0)), 2.0, 0F), 1.0F);
				bump("oil_flask");
			});
		}
	}

	long shoveReady, flaskReady;

	// ---- last stands (RIVAL-BOSSES v3: each its own; the Chair's replaces them) ---------------------------------------------------------

	@Override
	protected void onPhaseChange(ServerLevel level, int newPhase) {
		super.onPhaseChange(level, newPhase);
		if (newPhase >= 2 && !lastStandDone) {
			lastStandDone = true;
			if (chair) lanternRally(level);
			else lastStandMove(level);
		}
	}

	/** Lantern Rally: 30 ticks invulnerable and uninterruptible, then 60 absorption and +15 percent damage and Speed I for 200 ticks to every other Magnate within 48. */
	void lanternRally(ServerLevel level) {
		long now = level.getGameTime();
		rallyUntil = now + 30;
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.mirewick.Sfx3.pick("magnate", "bell", SoundEvents.BELL_RESONATE), SoundSource.HOSTILE, 2.0F, dev.elsewhere.rivals.mirewick.Sfx3.enabled() ? 0.9F : 0.8F);
		bump("lantern_rally");
		startTelegraph("lantern_rally", "Lantern Rally", 30, Telegraph.Weight.HEAVY, 0F, style().cue(false, true), true, () -> {
			setAbsorptionAmount(60F);
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 2.0, getZ(), 24, 0.5, 0.5, 0.5, 0.05);
			for (MagnateBoss o : all(level)) {
				if (o == this || o.distanceToSqr(this) > 48 * 48) continue;
				o.rallyBonusUntil = level.getGameTime() + 200;
				o.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
			}
		});
	}

	void lastStandMove(ServerLevel level) {
		long now = level.getGameTime();
		var cue = style().cue(false, true);
		switch (m) {
			case MARROW -> {
				shoutUntil = now + 24;                                                  // +10 percent damage taken during the shout
				bump("double_debt");
				startTelegraph("double_debt", "Double Debt", 24, Telegraph.Weight.SIGNATURE, 0F, cue, true, () -> {
					doubleDebtUntil = level.getGameTime() + 240;
					setCooldown("sig:call_the_debt", 0);
				});
			}
			case THWAITE -> {
				bump("mire_flood");
				startTelegraph("mire_flood", "Mire Flood", 24, Telegraph.Weight.SIGNATURE, 0F, cue, true, () -> {
					floodUntil = level.getGameTime() + 200;
					floodAt = position();
					GroundPatch.spawn(level, this, MagnateProfiles.ground(level, position()), 8.0, 0, 200, 20, 0F, List.of(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()));
				});
			}
			case SORREL -> {
				bump("sluice_open");
				startTelegraph("sluice_open", "Sluice Open", 30, Telegraph.Weight.HEAVY, 0F, cue, true, () -> {
					staggerImmuneUntil = level.getGameTime() + 200;
					LivingEntity t = getTarget();
					if (t != null) MagnateProfiles.buildWalls(this, t, 2);
					GroundPatch.spawn(level, this, MagnateProfiles.ground(level, position()), 4.0, 0, 200, 20, 0F, List.of(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0)), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()));
				});
			}
			case TOLLIVER -> {
				bump("duck_blind");
				startTelegraph("duck_blind", "Duck Blind", 30, Telegraph.Weight.SIGNATURE, 0F, cue, true, () -> {
					screenAt = position();
					screenUntil = level.getGameTime() + 200;
					GroundPatch.spawn(level, this, MagnateProfiles.ground(level, position()), 8.0, 0, 200, 20, 0F, List.of(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TALL_GRASS.defaultBlockState()));
				});
			}
			case AMES -> {
				bump("scrip_scatter");
				startTelegraph("scrip_scatter", "Scrip Scatter", 24, Telegraph.Weight.SIGNATURE, 0F, cue, true, () -> {
					for (KingdomSoldier h : handList(level)) {
						h.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 300, 0));
						h.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 0));
					}
					LivingEntity t = getTarget();
					KingdomSoldier extra = t == null ? null : MagnateProfiles.spawnHand(level, MagnateProfiles.ground(level, position().add(random.nextInt(9) - 4, 0, 14)), t);
					if (extra != null) hands.add(extra.getUUID());
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET), getX(), getY() + 2.0, getZ(), 40, 3.0, 1.0, 3.0, 0.2);
				});
			}
		}
	}

	// ---- convene, escorts ---------------------------------------------------------------------------------------------------

	/** Harming a Magnate pulls in the others within 64 blocks: they walk in (never teleport), each with 2 escorts and its own nearest target. */
	void convene(ServerLevel level, ServerPlayer attacker) {
		long now = level.getGameTime();
		if (now - lastConvene < 60) return;
		lastConvene = now;
		convened = true;
		if (getTarget() == null) setTarget(attacker);
		escort(level, attacker);
		for (MagnateBoss o : all(level)) {
			if (o == this || o.distanceToSqr(this) > 64 * 64) continue;
			o.convened = true;
			o.lastConvene = now;
			if (o.getTarget() == null) o.setTarget(attacker);
			o.escort(level, attacker);
			if (o.distanceToSqr(this) > 25) o.getNavigation().moveTo(getX(), getY(), getZ(), 1.0);
		}
	}

	void escort(ServerLevel level, LivingEntity target) {
		if (escorted) return;
		escorted = true;
		Identifier[] ids = {MirewickContent.id("mirewick_dikeguard"), MirewickContent.id(m.role == Magnate.Role.RANGED ? "mirewick_punt_crossbow" : "mirewick_pikeman")};
		for (Identifier id : ids) {
			Vec3 p = position().add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4);
			KingdomSoldier s = KingdomApi.spawnSoldier(level, id, MirewickContent.FACTION, p, null);
			if (s != null) s.setTarget(target);
		}
	}

	// ---- per-tick council rules -------------------------------------------------------------------------------------------

	@Override
	protected void extraTick(ServerLevel level, @Nullable LivingEntity target) {
		long now = level.getGameTime();
		if (tickCount % 20 == 0) {
			lanternLit = chair && now >= lanternOutUntil;
			if (chair && !lanternLit) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.4, getZ(), 3, 0.2, 0.2, 0.2, 0.01);
			if (convened && target == null && tickCount % 200 == 0) convened = false;
		}
		// the council does not path through an ally's Lantern Fire
		if (tickCount % 5 == 0) {
			FIRES.removeIf(f -> (long) f[3] <= now);
			for (Object[] f : FIRES) {
				if (f[0] == this || ((ServerLevel) f[4]) != level) continue;
				Vec3 c = (Vec3) f[1];
				if (position().distanceToSqr(c.x, position().y, c.z) < (double) f[2] * (double) f[2]) {
					Vec3 away = position().subtract(c).multiply(1, 0, 1).normalize().scale(0.35);
					setDeltaMovement(away.x, getDeltaMovement().y, away.z);
				}
			}
		}
		if (fleeing) flee(level, now);
	}

	/** Records a Lantern Fire patch so allies avoid it ({@code [owner, centre, radius, expiry tick, level]}). */
	public static void noteFire(MagnateBoss owner, ServerLevel level, Vec3 centre, double radius, int ticks) {
		FIRES.add(new Object[] {owner, centre, radius, level.getGameTime() + ticks, level});
	}

	public static int firesActive() {
		return FIRES.size();
	}

	long vault(ServerLevel level, MagnateBoss b) {
		try {
			Ruler r = Ruler.byKey(b.m.courtSlot);
			if (r != null && Leaders.of(b) != null) return Oligarchy.vault(level.getServer(), MirewickContent.FACTION, r);
		} catch (RuntimeException ignored) {
		}
		return CouncilRules.vault(b.m, level.getGameTime() / 24000L / 7L, 0, 0);
	}

	void squabble(ServerLevel level, long now) {
		List<MagnateBoss> council = all(level);
		// the Almoner's ledger: a Magnate pays the first time he falls under 70 percent and at each further 20-point step
		for (MagnateBoss a : council) {
			if (a.m != Magnate.AMES || a == this || a.distanceToSqr(this) > 64 * 64) continue;
			if (a.ledger.offer(getUUID(), getHealth() / getMaxHealth())) pay(level);
		}
		ledger.clearIfGone(id -> council.stream().anyMatch(b -> b.getUUID().equals(id)));
		// the richest in the fight leaves at 40 percent and walks home to the Counting-House
		if (!fleeing && !isDuel() && getHealth() < getMaxHealth() * CouncilRules.FLEE_AT && convened) {
			Map<Magnate, Integer> inFight = new EnumMap<>(Magnate.class);
			for (MagnateBoss b : council) if (b.convened && b.distanceToSqr(this) < 64 * 64) inFight.put(b.m, (int) Math.min(Integer.MAX_VALUE, vault(level, b)));
			if (CouncilRules.richest(inFight) == m) {
				fleeing = true;
				fleeUntil = now + CouncilRules.FLEE_TICKS;
				setTarget(null);
				for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 48 * 48) p.sendOverlayMessage(Component.translatable("message.elsewhere.mirewick_flees", Component.translatable(m.nameKey())));
			}
		}
	}

	void pay(ServerLevel level) {
		payments++;
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET), getX(), getY() + 2.0, getZ(), 10, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, blockPosition(), dev.elsewhere.rivals.mirewick.Sfx3.pick("magnate", "coins", SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.HOSTILE, 1.0F, dev.elsewhere.rivals.mirewick.Sfx3.enabled() ? 1.0F : 1.2F);
		for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 48 * 48) p.sendOverlayMessage(Component.translatable("message.elsewhere.mirewick_almoner_paid", Component.translatable(m.nameKey())));
	}

	void flee(ServerLevel level, long now) {
		setTarget(null);
		if (now >= fleeUntil) {
			fleeing = false;
			heal((float) (getMaxHealth() * CouncilRules.FLEE_RETURN_HEAL));
			return;
		}
		if (tickCount % 10 == 0) {
			Vec3 goal = home.distSqr(blockPosition()) > 100 ? Vec3.atBottomCenterOf(home) : null;
			if (goal == null) {
				var p = level.getNearestPlayer(this, 64);
				Vec3 away = p == null ? new Vec3(1, 0, 0) : position().subtract(p.position()).multiply(1, 0, 1).normalize();
				goal = position().add(away.scale(24));
			}
			getNavigation().moveTo(goal.x, goal.y, goal.z, 1.1);
		}
	}

	/** The Almoner heals only whoever paid him last. Null when nobody has paid (or the payer is gone or out of reach). */
	public @Nullable MagnateBoss healTargetBoss(ServerLevel level) {
		UUID id = ledger.lastPayer();
		if (id == null) return null;
		for (MagnateBoss b : all(level)) if (b.getUUID().equals(id) && b.distanceToSqr(this) < 40 * 40) return b;
		return null;
	}

	public int others(ServerLevel level, double r) {
		int n = 0;
		for (MagnateBoss b : all(level)) if (b != this && b.distanceToSqr(this) <= r * r) n++;
		return n;
	}

	/** Garrick's thrust is blocked by anyone standing in the line (an ally in the pike's way). */
	public boolean allyInLine(ServerLevel level, LivingEntity t) {
		Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
		for (MagnateBoss b : all(level)) {
			if (b == this) continue;
			Vec3 rel = b.position().subtract(position());
			double along = rel.dot(dir), lat = Math.abs(rel.x * dir.z - rel.z * dir.x);
			if (along > 0.5 && along < 5.5 && lat < 1.0) return true;
		}
		return false;
	}

	// ---- defeat: the Chair table -------------------------------------------------------------------------------------------

	private boolean chairAtDeath;

	@Override
	public void die(DamageSource source) {
		chairAtDeath = chair;                         // the court clears the Chair flag inside die(); the Chair table needs it
		super.die(source);
	}

	@Override
	protected void onDefeated(ServerLevel level, List<ServerPlayer> participants) {
		DuelBlocks.clearOwner(level, getUUID());
		if (!chair && !chairAtDeath) return;
		MagnateDrops.chairTable(level, this, participants);
	}

	/** 3.0 (s-bossdrops): every boss always drops XP (HumanBoss leaves it at 0; each boss sets its own number). Vanilla's always-drop path, so the Chaperon reads it too. */
	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return dev.elsewhere.rivals.mirewick.rewards.MirewickRewards.xp(this, chair || chairAtDeath);
	}

	@Override
	protected boolean isAlwaysExperienceDropper() {
		return dev.elsewhere.rivals.mirewick.rewards.MirewickRewards.xp(this, chair || chairAtDeath) > 0;
	}
}
