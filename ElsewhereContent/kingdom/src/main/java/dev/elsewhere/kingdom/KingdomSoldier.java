package dev.elsewhere.kingdom;

import dev.elsewhere.kingdom.ai.FormationGoal;
import dev.elsewhere.kingdom.ai.GatedGoal;
import dev.elsewhere.kingdom.ai.LeaderMarchGoal;
import dev.elsewhere.kingdom.ai.MountedChargeGoal;
import dev.elsewhere.kingdom.ai.RetreatGoal;
import dev.elsewhere.kingdom.ai.RoutGoal;
import dev.elsewhere.kingdom.ai.SoldierMeleeGoal;
import dev.elsewhere.kingdom.ai.SoldierTargetGoal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import dev.elsewhere.kingdom.item.KingdomItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A player-sized kingdom soldier. Extends {@link Monster} (a {@link net.minecraft.world.entity.PathfinderMob}) so the
 * vanilla bow, crossbow and spear goals can drive the ranged and lancer roles; it never natural-spawns (MobCategory.MISC)
 * and is always persistent.
 * <p>What it does like a player: shield blocking through the real vanilla {@code BlocksAttacks} path (axes disable the
 * shield for the item's disable time), attack cooldown from the held weapon's attack speed (players' formula), jump
 * crits for 1.5x with vanilla crit particles and sound, sprint-knockback hits (+0.5 knockback), strafing in melee,
 * backing off at low health. Unit type ({@link UnitType}), faction ({@link Factions}) and squad ({@link Squad}) are saved
 * with the entity.
 * <p>Subclass it for Lords/the King (keep the {@code (EntityType, Level)} constructor and add goals in
 * {@link #registerGoals()} after {@code super.registerGoals()}).
 */
public class KingdomSoldier extends Monster implements CrossbowAttackMob {
	private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(KingdomSoldier.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_TABARD = SynchedEntityData.defineId(KingdomSoldier.class, EntityDataSerializers.INT);
	// 3.0 polish P3: synced only on spawn / unit or faction change (never per tick), read by the client nameplate
	private static final EntityDataAccessor<Byte> DATA_RANK = SynchedEntityData.defineId(KingdomSoldier.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<String> DATA_FACTION = SynchedEntityData.defineId(KingdomSoldier.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(KingdomSoldier.class, EntityDataSerializers.BOOLEAN);
	// 3.0 polish P9 (s-p9-idle): idle/work pose id (RoutinePoses), set only when the routine changes it; the client animates it
	private static final EntityDataAccessor<Byte> DATA_IDLE = SynchedEntityData.defineId(KingdomSoldier.class, EntityDataSerializers.BYTE);

	/** Server-wide counters (the self-test and benchmark read them). */
	public static long totalCrits, totalBlocks, totalSprintHits, totalAttacks;

	private Identifier unitId;
	private Identifier factionId = Factions.ALDERMARK;
	private @Nullable UUID squadId;
	private boolean equipped, mountSpawned;
	private int routTicks;

	// combat state (not saved)
	/** The squad brain's current instruction (tactics framework); null when none. Not saved. */
	public transient dev.elsewhere.kingdom.tactics.Order tacticsOrder;
	public int shieldDisabledTicks, shieldLowerTicks, forcedShieldTicks, critCooldown, retreatCooldown;
	public boolean pendingCrit, meleeThreat, blockRoll, wallUp;
	private boolean threatSeen;
	/** Per-soldier counters (tests/benchmarks): crits, blocked hits, melee hits, ranged shots, ticks spent in a spear charge. */
	public int crits, blocks, attacks, shots, spearTicks;
	private long activeUntil;
	private int lastProjectileHitTick = -1000;
	/** Tick of the last hit this soldier's shield stopped. */
	public int lastBlockTick = -1000;
	private final Map<UUID, Integer> provokedBy = new HashMap<>();
	// 0.2.0 state
	/** Rolled once per attack cycle (rare crits / sprint hits for rank and file). */
	public boolean critRolled, sprintIntent, sprintRolled;
	/** Retreat decision once health drops below {@code retreat_health} (null = not decided yet). */
	public @Nullable Boolean retreatDecided;
	/** Cooldowns: ability (war cry, heal), shield bash. */
	public int abilityCooldown, bashCooldown;
	/** Herald: horn already blown this life. */
	public boolean hornUsed;
	/** Game time until which a standard-bearer's +10% damage aura applies. */
	public long standardBuffUntil;
	/** Night routine: resting (crouched, no targeting) until woken. */
	private boolean resting;
	/** Set while the LOD system turns this soldier into a virtual record (keeps its squad slot). */
	boolean virtualizing;
	/** Counters for tests: war cries, heals given, horns blown, shield bashes. */
	public static long totalWarCries, totalHeals, totalHorns, totalBashes;
	/** Set by the squad brain: where this member should stand, and the squad heading in degrees. */
	public @Nullable Vec3 formationTarget;
	public float formationYaw;
	/** Next tick a leader/free soldier may start a new path search (throttle). */
	public int nextRepathTick;
	/**
	 * Phase 0..19 for staggered work. Must have the same parity as the entity id: vanilla only evaluates goals (canUse) on
	 * ticks where tickCount + id is even (Mob.serverAiStep), and the goals gate on (tickCount + phase) % 10 == 0, so with
	 * the other parity a soldier never picks a target, marches, shoots or charges. Set random in the constructor (no id
	 * there), aligned in {@link #customServerAiStep}.
	 */
	public int phase;

	public KingdomSoldier(EntityType<? extends KingdomSoldier> type, Level level) {
		super(type, level);
		this.unitId = KingdomEntities.defaultUnit(type);
		this.setPersistenceRequired();
		this.xpReward = 5;
		// Entity ids are assigned after construction (client crash "Tried to access entity ID before ID assignment"),
		// so stagger with the entity's own random instead.
		this.phase = this.getRandom().nextInt(20);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.ATTACK_SPEED, 4.0)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_SKIN, UnitType.DEFAULT_SKIN.toString());
		b.define(DATA_TABARD, 0x2E4C9E);
		b.define(DATA_CHARGING, false);
		b.define(DATA_RANK, (byte) 0);
		b.define(DATA_FACTION, "");
		b.define(DATA_IDLE, (byte) 0);
	}

	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
		return new dev.elsewhere.kingdom.ai.RangedNav(this, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new RoutGoal(this));
		this.goalSelector.addGoal(2, new RetreatGoal(this));
		this.goalSelector.addGoal(3, new GatedGoal(this, new SpearUseGoal<>(this, 1.0, 1.0, 10.0F, 2.0F), s -> s.role() == UnitType.Role.LANCER));
		// 3.0 fix (s-intreq): vanilla's goal only counts Items.BOW, so a bowman with any other bow (Azmar's composite bow) never drew
		this.goalSelector.addGoal(3, new GatedGoal(this, new RangedBowAttackGoal<KingdomSoldier>(this, 0.85, 25, 14.0F) {
			@Override
			protected boolean isHoldingBow() {
				return bowHand() != null;
			}
		}, s -> s.role() == UnitType.Role.ARCHER));
		this.goalSelector.addGoal(3, new GatedGoal(this, new RangedCrossbowAttackGoal<>(this, 1.0, 8.0F), s -> s.role() == UnitType.Role.CROSSBOW));
		this.goalSelector.addGoal(3, new dev.elsewhere.kingdom.tactics.OrderGoal(this));
		this.goalSelector.addGoal(3, new MountedChargeGoal(this));
		this.goalSelector.addGoal(3, new SoldierMeleeGoal(this));
		this.goalSelector.addGoal(4, new dev.elsewhere.kingdom.ai.RestGoal(this));
		this.goalSelector.addGoal(5, new FormationGoal(this));
		this.goalSelector.addGoal(6, new LeaderMarchGoal(this));
		this.goalSelector.addGoal(8, new GatedGoal(this, new LookAtPlayerGoal(this, Player.class, 8.0F), s -> true));
		this.targetSelector.addGoal(2, new SoldierTargetGoal(this));
	}

	// ---- identity -------------------------------------------------------------------------------------------------

	public Identifier unitId() {
		return unitId;
	}

	public UnitType unit() {
		return Factions.unit(factionId, unitId);
	}

	public UnitType.Role role() {
		return unit().role();
	}

	public boolean isOfficer() {
		return unit().officer();
	}

	public Identifier factionId() {
		return factionId;
	}

	public @Nullable UUID squadId() {
		return squadId;
	}

	/** The squad this soldier belongs to (server side), or null. */
	public @Nullable Squad squad() {
		if (squadId == null || !(level() instanceof ServerLevel sl)) return null;
		return KingdomState.get(sl).squad(squadId);
	}

	public boolean isSquadLeader() {
		Squad s = squad();
		return s != null && getUUID().equals(s.leader());
	}

	void setSquadId(@Nullable UUID id) {
		this.squadId = id;
	}

	/** Changes the unit type and re-applies its loadout (armor, hands, stats; a mount is added if the unit has one). */
	public void setUnit(Identifier unit) {
		this.unitId = unit;
		this.equipped = false;
		this.mountSpawned = false;
		applyLoadout();
	}

	/** Sets the faction (tabard colour, who it fights). */
	public void setFaction(Identifier faction) {
		this.factionId = faction;
		this.entityData.set(DATA_TABARD, Factions.get(faction).tabardColor);
		if (equipped) this.entityData.set(DATA_SKIN, unit().skin().toString()); // a re-flagged soldier wears its new kingdom's skin, never the old one's (A3)
		syncNameplate();
	}

	/** Rank for the client nameplate ({@link dev.elsewhere.kingdom.plate.Nameplate}): 0 rank and file, 1 officer, 2 captain, 3 lord, 4 sovereign. */
	public int rank() {
		return this.entityData.get(DATA_RANK);
	}

	/** Faction id as the client sees it ("" before the first sync). */
	public String syncedFaction() {
		return this.entityData.get(DATA_FACTION);
	}

	/** 3.0 P9: the synced idle/work pose ({@code RoutinePoses.Pose} id, 0 = none); see {@code idle.IdlePoses}. */
	public int idlePose() {
		return this.entityData.get(DATA_IDLE);
	}

	/** 3.0 P9: sets the idle/work pose; {@code SynchedEntityData} sends it only when the value changes. */
	public void setIdlePose(int pose) {
		this.entityData.set(DATA_IDLE, (byte) pose);
	}

	/**
	 * The rank this soldier's nameplate shows (server side, read on every resync). Rulers override it (kingdom_court
	 * {@code CourtRuler}: lord, or sovereign when crowned) and resync through {@link #setFaction} when it changes.
	 */
	protected int nameplateRank() {
		return dev.elsewhere.kingdom.plate.Nameplate.rankOf(unitId, unit());
	}

	/** Spawn / unit / faction / crown change only; the entity data sends it to watchers once (never per tick). */
	private void syncNameplate() {
		this.entityData.set(DATA_RANK, (byte) nameplateRank());
		this.entityData.set(DATA_FACTION, factionId.toString());
	}

	public Identifier skin() {
		Identifier id = Identifier.tryParse(this.entityData.get(DATA_SKIN));
		return id != null ? id : UnitType.DEFAULT_SKIN;
	}

	public int tabardColor() {
		return this.entityData.get(DATA_TABARD);
	}

	public boolean isChargingCrossbow() {
		return this.entityData.get(DATA_CHARGING);
	}

	// ---- loadout --------------------------------------------------------------------------------------------------

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
		SpawnGroupData r = super.finalizeSpawn(level, difficulty, reason, data);
		applyLoadout();
		return r;
	}

	@Override
	protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
		// loadout comes from the unit type only
	}

	private static ItemStack stack(Optional<Identifier> id, Optional<Integer> dye) {
		if (id.isEmpty()) return ItemStack.EMPTY;
		Item item = BuiltInRegistries.ITEM.getValue(id.get());
		if (item == null || item == Items.AIR) return ItemStack.EMPTY;
		ItemStack s = new ItemStack(item);
		if (dye.isPresent() && id.get().getPath().startsWith("leather_")) s.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.get()));
		return s;
	}

	/** Drop chance per armor piece (rare, so inventories don't fill with chainmail) and for weapons/shields. Always damaged. */
	public static final float ARMOR_DROP_CHANCE = 0.02F, HAND_DROP_CHANCE = 0.04F;

	/** Trim, heraldry and enchantments for one loadout piece (needs the level's registries). */
	private ItemStack dress(ItemStack s, UnitType u) {
		if (s.isEmpty()) return s;
		var reg = level().registryAccess();
		if (u.trim().isPresent() && s.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR)) {
			UnitType.Trim t = u.trim().get();
			var mat = reg.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL)
				.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_MATERIAL, t.material()));
			if (!t.patterns().isEmpty() && mat.isPresent()) {
				Identifier pid = t.patterns().get(Math.floorMod(getUUID().hashCode(), t.patterns().size()));
				var pat = reg.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN)
					.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_PATTERN, pid));
				if (pat.isEmpty()) pat = reg.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN).get(net.minecraft.world.item.equipment.trim.TrimPatterns.SENTRY);
				if (pat.isPresent()) s.set(DataComponents.TRIM, new net.minecraft.world.item.equipment.trim.ArmorTrim(mat.get(), pat.get()));
			}
		}
		if (Factions.ALDERMARK.equals(factionId) && (s.is(Items.SHIELD) || s.getItem() instanceof net.minecraft.world.item.BannerItem))
			dev.elsewhere.kingdom.item.KingdomItems.applyHeraldry(s, reg.lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN));
		return s;
	}

	/** Writes the unit type's stats (health, speed, damage, follow range, knockback resistance, armor, armor toughness) into the attributes. Does not touch current health. */
	private void applyStats(UnitType u) {
		UnitType.Stats st = u.stats();
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(st.health());
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(st.speed());
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(st.attackDamage());
		getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(st.followRange());
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(st.knockbackResistance());
		getAttribute(Attributes.ARMOR).setBaseValue(st.armor());
		getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(st.armorToughness());
	}

	/**
	 * Re-applies the unit stats on load so existing worlds pick up changed unit JSONs (2.1 health ladder). Current health is clamped down to the
	 * new maximum and never raised. Court rulers, diplomats and duelists manage their own health, so they are skipped.
	 */
	private void reapplyStatsOnLoad() {
		String cn = getClass().getName();
		if (cn.startsWith("dev.elsewhere.kingdom_court") || cn.startsWith("dev.elsewhere.duelist")) return;
		float hp = getHealth();
		applyStats(unit());
		setHealth(Math.min(hp, getMaxHealth()));
	}

	/** Applies the unit type's armor, hands and stats (idempotent per unit; called on spawn and on the first tick). */
	public void applyLoadout() {
		UnitType u = unit();
		Optional<Integer> dye = u.armorDye();
		setItemSlot(EquipmentSlot.HEAD, dress(stack(u.armor().head(), dye), u));
		setItemSlot(EquipmentSlot.CHEST, dress(stack(u.armor().chest(), dye), u));
		setItemSlot(EquipmentSlot.LEGS, dress(stack(u.armor().legs(), dye), u));
		setItemSlot(EquipmentSlot.FEET, dress(stack(u.armor().feet(), dye), u));
		ItemStack main = dress(stack(u.mainHand(), Optional.empty()), u);
		if (u.ability() == UnitType.Ability.CAPTAIN && !main.isEmpty())
			level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS).ifPresent(h -> main.enchant(h, 1));
		setItemSlot(EquipmentSlot.MAINHAND, main);
		setItemSlot(EquipmentSlot.OFFHAND, dress(stack(u.offHand(), Optional.empty()), u));
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			setDropChance(s, ARMOR_DROP_CHANCE);
		setDropChance(EquipmentSlot.MAINHAND, HAND_DROP_CHANCE);
		setDropChance(EquipmentSlot.OFFHAND, HAND_DROP_CHANCE);
		this.xpReward = u.officer() ? 10 : 5;
		applyStats(u);
		setHealth(getMaxHealth());
		this.entityData.set(DATA_SKIN, u.skin().toString());
		this.entityData.set(DATA_TABARD, Factions.get(factionId).tabardColor);
		syncNameplate();
		this.equipped = true;
	}

	/** Spawns the unit's mount (tamed, saddled, armored) under this soldier. Called on the first server tick. */
	protected void spawnMount(ServerLevel level) {
		this.mountSpawned = true;
		UnitType u = unit();
		if (u.mount().isEmpty() || isPassenger()) return;
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(u.mount().get());
		if (t == null) t = EntityTypes.HORSE;
		Entity e = t.create(level, EntitySpawnReason.JOCKEY);
		if (!(e instanceof Mob mount)) return;
		mount.snapTo(getX(), getY(), getZ(), getYRot(), 0F);
		mount.setPersistenceRequired();
		if (mount instanceof AbstractHorse h) {
			h.setTamed(true);
			h.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
			ItemStack armor = stack(u.mountArmor(), Optional.empty());
			if (!armor.isEmpty()) h.setItemSlot(EquipmentSlot.BODY, armor);
			h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
			h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0);
			h.setHealth(30F);
		}
		level.addFreshEntity(mount);
		this.startRiding(mount, true, false);
	}

	// ---- per-tick combat state ------------------------------------------------------------------------------------

	/** True when this soldier runs its full AI (a player is within {@code perf.activeRadius} and under the per-player cap). */
	public boolean isActive() {
		return KingdomPerf.forceActive || level().getGameTime() < activeUntil;
	}

	void setActiveUntil(long t) {
		this.activeUntil = t;
	}

	public boolean isRouted() {
		return routTicks > 0;
	}

	/** Makes the soldier flee for {@code ticks} (no attacking). */
	public void rout(int ticks) {
		this.routTicks = Math.max(routTicks, ticks);
		this.setTarget(null);
		this.stopUsingItem();
	}

	/** Raises the shield (if any) for the given ticks regardless of threats, e.g. for a scripted shield wall. */
	public void raiseShield(int ticks) {
		this.forcedShieldTicks = Math.max(forcedShieldTicks, ticks);
	}

	/** True when the soldier decided to retreat at low health (rolled once with the unit's {@code retreat_chance}). */
	public boolean wantsRetreat() {
		return Boolean.TRUE.equals(retreatDecided);
	}

	public boolean isResting() {
		return resting;
	}

	/** Night routine: crouch and rest (no targeting) until woken by an attack or by the squad. */
	public void setResting(boolean r) {
		if (r == resting) return;
		this.resting = r;
		setPose(r ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
		if (r) getNavigation().stop();
	}

	public boolean hasShield() {
		return getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/** Player-style attack cooldown (ticks for a full-strength hit with the held weapon). */
	public float attackDelay() {
		return (float) (20.0 / Math.max(0.1, getAttributeValue(Attributes.ATTACK_SPEED)));
	}

	public float attackStrength(float partial) {
		return Math.clamp((attackStrengthTicker + partial) / attackDelay(), 0F, 1F);
	}

	/** Tests/scripts: makes the next hit a full-strength one. */
	public void chargeAttack() {
		attackStrengthTicker = 1000;
	}

	public boolean attackReady() {
		return attackStrength(0.5F) >= 0.95F;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (((phase ^ getId()) & 1) != 0) phase ^= 1; // keep the goal gates on goal-evaluation ticks (see phase)
		super.customServerAiStep(level);
		if (!equipped) applyLoadout();
		if (!mountSpawned) {
			if (unit().mounted()) spawnMount(level);
			else mountSpawned = true;
		}
		attackStrengthTicker++;
		if (shieldDisabledTicks > 0) shieldDisabledTicks--;
		if (shieldLowerTicks > 0) shieldLowerTicks--;
		if (forcedShieldTicks > 0) forcedShieldTicks--;
		if (critCooldown > 0) critCooldown--;
		if (retreatCooldown > 0) retreatCooldown--;
		if (routTicks > 0) routTicks--;
		if (abilityCooldown > 0) abilityCooldown--;
		if (bashCooldown > 0) bashCooldown--;
		if ((tickCount + phase) % 5 == 0) {
			float th = getMaxHealth() * unit().combat().retreatHealth();
			if (getHealth() < th) {
				if (retreatDecided == null) retreatDecided = random.nextFloat() < unit().combat().retreatChance();
			} else retreatDecided = null;
		}
		if (unit().ability() != UnitType.Ability.NONE && !isRouted()) KingdomAbilities.tick(level, this);
		if (pendingCrit && onGround() && getDeltaMovement().y <= 0 && tickCount % 4 == 0) pendingCrit = false;
		updateShield();
		if (isUsingItem() && getUseItem().has(DataComponents.KINETIC_WEAPON)) spearTicks++;
		if ((tickCount + phase) % 100 == 0 && !provokedBy.isEmpty()) provokedBy.values().removeIf(t -> t < tickCount);
	}

	private void updateShield() {
		if (!hasShield()) return;
		boolean ranged = false;
		LivingEntity t = getTarget();
		if (t != null && t.isUsingItem()) {
			Item it = t.getUseItem().getItem();
			ranged = (it instanceof BowItem || it instanceof CrossbowItem) && distanceToSqr(t) < 40 * 40;
		}
		boolean hitByArrow = tickCount - lastProjectileHitTick < 40;
		boolean threat = meleeThreat || ranged || hitByArrow || wallUp;
		if (threat && (!threatSeen || (tickCount + phase) % 40 == 0)) blockRoll = random.nextFloat() < unit().combat().blockChance(); // roll at onset, re-roll every 2 s
		threatSeen = threat;
		boolean want = KingdomConfig.useShields && shieldDisabledTicks <= 0 && shieldLowerTicks <= 0 && !pendingCrit && routTicks <= 0
			&& (forcedShieldTicks > 0 || (threat && (blockRoll || wallUp || hitByArrow)));
		boolean usingShield = isUsingItem() && getUseItem().has(DataComponents.BLOCKS_ATTACKS);
		if (want && !isUsingItem()) {
			startUsingItem(InteractionHand.OFF_HAND);
			if ((ranged || hitByArrow) && t != null) getLookControl().setLookAt(t, 30F, 30F);
		} else if (!want && usingShield) {
			stopUsingItem();
		}
	}

	// ---- combat ---------------------------------------------------------------------------------------------------

	public boolean canCritNow(Entity target) {
		return fallDistance > 0.0 && !onGround() && !onClimbable() && !isInWater() && !isPassenger() && target instanceof LivingEntity && !isSprinting();
	}

	/**
	 * Lowers the shield, swings and hits like a player: damage scaled by attack strength, x1.5 when falling (crit,
	 * vanilla crit particles + sound), +0.5 knockback when sprinting. Used by the melee goals.
	 */
	public boolean strike(ServerLevel level, LivingEntity target) {
		if (isUsingItem() && getUseItem().has(DataComponents.BLOCKS_ATTACKS)) stopUsingItem();
		swingForAttack(InteractionHand.MAIN_HAND);
		return doHurtTarget(level, target);
	}

	/** True when the held weapon has a minimum reach ({@code attack_range}: the 26.x spears, 2.0 x mob factor 0.5 = 1 block). */
	public boolean hasMinReach() {
		net.minecraft.world.item.component.AttackRange r = getActiveItem().get(DataComponents.ATTACK_RANGE);
		return r != null && r.effectiveMinRange(this) > 0F;
	}

	/**
	 * 3.0 fix (s-intreq): where {@code t} stands against the held weapon's reach band, with the same boxes as
	 * {@link #isWithinMeleeAttackRange}: -1 inside the minimum reach (a spear can't jab there), 0 in the band, 1 beyond it.
	 * {@code slack} narrows the band on both sides (0 = the exact rule). A weapon without {@code attack_range} never returns -1.
	 */
	public int reachBand(LivingEntity t, double slack) {
		net.minecraft.world.item.component.AttackRange r = getActiveItem().get(DataComponents.ATTACK_RANGE);
		if (r == null) return isWithinMeleeAttackRange(t) ? 0 : 1;
		net.minecraft.world.phys.AABB hit = t.getBoundingBox();
		double min = r.effectiveMinRange(this);
		if (min > 0.0 && getAttackBoundingBox(min + slack).intersects(hit)) return -1;
		return getAttackBoundingBox(Math.max(0.0, r.effectiveMaxRange(this) - slack)).intersects(hit) ? 0 : 1;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean crit = KingdomConfig.useCrits && canCritNow(target);
		boolean sprintHit = !crit && isSprinting();
		float strength = attackStrength(0.5F);
		float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
		ItemStack weapon = getWeaponItem();
		DamageSource src = weapon.getDamageSource(this);
		dmg = EnchantmentHelper.modifyDamage(level, weapon, target, src, dmg);
		dmg += weapon.getItem().getAttackDamageBonus(target, dmg, src);
		dmg *= 0.2F + strength * strength * 0.8F;
		if (crit) dmg *= 1.5F;
		if (standardBuffUntil >= level.getGameTime()) dmg *= 1.1F;
		if (target.isPassenger() || (target instanceof AbstractHorse h && h.isVehicle())) dmg *= unit().combat().mountedBonus();
		dmg *= (float) KingdomConfig.soldierDamageScale;
		Vec3 old = target.getDeltaMovement();
		boolean hurt = target.hurtServer(level, src, dmg);
		totalAttacks++;
		attacks++;
		if (hurt) {
			causeExtraKnockback(target, getKnockback(target, src) + (sprintHit ? 0.5F : 0F), old, src, dmg, true);
			if (sprintHit) {
				setSprinting(false);
				totalSprintHits++;
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, getSoundSource(), 1F, 1F);
			}
			if (crit) {
				crits++;
				totalCrits++;
				critCooldown = unit().combat().critCooldown();
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, getSoundSource(), 1F, 1F);
				level.getChunkSource().sendToTrackingPlayers(target, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
			} else if (strength > 0.9F) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, getSoundSource(), 0.7F, 1F);
			}
			if (target instanceof LivingEntity lt) weapon.hurtEnemy(lt, this);
			EnchantmentHelper.doPostAttackEffects(level, target, src);
			setLastHurtMob(target);
		}
		postPiercingAttack();
		attackStrengthTicker = 0;
		pendingCrit = false;
		critRolled = false;
		sprintRolled = false;
		shieldLowerTicks = Math.max(shieldLowerTicks, 3);
		return hurt;
	}

	/** Axes (any weapon with {@code disable_blocking_for_seconds}) disable the shield like a player's. */
	@Override
	protected void blockUsingItem(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked) {
		ItemStack with = getItemBlockingWith();
		BlocksAttacks ba = with != null ? with.get(DataComponents.BLOCKS_ATTACKS) : null;
		super.blockUsingItem(level, attacker, source, damage, fullyBlocked);
		float secs = attacker.getSecondsToDisableBlocking();
		if (secs > 0F && ba != null) {
			ba.disable(level, this, secs, with);
			shieldDisabledTicks = Math.max(1, Math.round(secs * ba.disableCooldownScale() * 20F));
		}
	}

	@Override
	public float applyItemBlocking(ServerLevel level, DamageSource source, float damage) {
		float r = super.applyItemBlocking(level, source, damage);
		if (r > 0F) {
			lastBlockTick = tickCount;
			blocks++;
			totalBlocks++;
		}
		return r;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity src = source.getEntity();
		if (src instanceof KingdomSoldier s && s != this && !Factions.hostile(s.factionId, factionId)) return false; // no friendly fire
		if (resting) {
			Squad sq = squad();
			if (sq != null) sq.wake(level);
			else setResting(false);
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			lastProjectileHitTick = tickCount;
			if (src != null) getLookControl().setLookAt(src, 30F, 30F);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void setLastHurtByMob(@Nullable LivingEntity mob) {
		super.setLastHurtByMob(mob);
		if (mob == null || mob == this || !(level() instanceof ServerLevel sl)) return;
		if (mob instanceof KingdomSoldier s && !Factions.hostile(s.factionId, factionId)) return;
		provokedBy.put(mob.getUUID(), tickCount + 600);
		Squad sq = squad();
		if (sq != null) sq.provoke(mob.getUUID(), sl.getGameTime() + 600);
		if (KingdomTargeting.canAttack(this, mob) && !isRouted()) {
			LivingEntity cur = getTarget();
			if (cur == null || !cur.isAlive() || distanceToSqr(mob) < distanceToSqr(cur) * 0.6) setTarget(mob);
		}
	}

	/** True when this soldier was attacked by {@code e} in the last 30 s. */
	public boolean isProvokedBy(Entity e) {
		Integer t = provokedBy.get(e.getUUID());
		return t != null && t >= tickCount;
	}

	// ---- ranged ---------------------------------------------------------------------------------------------------

	@Override
	public boolean canUseNonMeleeWeapon(ItemStack item) {
		return item.getItem() instanceof BowItem || item.getItem() instanceof CrossbowItem;
	}

	/** The hand holding a bow of any kind (vanilla or a modded {@link BowItem}), main hand first; null when none. */
	public @Nullable InteractionHand bowHand() {
		if (getMainHandItem().getItem() instanceof BowItem) return InteractionHand.MAIN_HAND;
		if (getOffhandItem().getItem() instanceof BowItem) return InteractionHand.OFF_HAND;
		return null;
	}

	/**
	 * 3.0 fix (s-intreq): vanilla's bow goal draws with the hand holding {@code Items.BOW}, which is the off hand for any other bow
	 * (Azmar's composite bow), so the draw never started. An archer asked to use a hand that holds neither a bow nor a shield uses
	 * its bow hand instead; shields, spears, crossbows and every other role are untouched.
	 */
	@Override
	public void startUsingItem(InteractionHand hand) {
		ItemStack in = getItemInHand(hand);
		if (!(in.getItem() instanceof BowItem) && !in.has(DataComponents.BLOCKS_ATTACKS) && role() == UnitType.Role.ARCHER) {
			InteractionHand b = bowHand();
			if (b != null) hand = b;
		}
		super.startUsingItem(hand);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		shots++;
		if (isHolding(Items.CROSSBOW)) {
			performCrossbowAttack(this, 1.6F);
			return;
		}
		InteractionHand bowHand = bowHand(); // any BowItem (3.0 fix, s-intreq), not only Items.BOW
		ItemStack bow = getItemInHand(bowHand != null ? bowHand : InteractionHand.MAIN_HAND);
		ItemStack projectile = getProjectile(bow);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectile, power, bow);
		double xd = target.getX() - getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - getZ();
		double dist = Math.sqrt(xd * xd + zd * zd);
		if (level() instanceof ServerLevel sl) Projectile.spawnProjectileUsingShoot(arrow, sl, projectile, xd, yd + dist * 0.2F, zd, 1.6F, 14 - sl.getDifficulty().getId() * 4);
		playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
	}

	@Override
	public void setChargingCrossbow(boolean charging) {
		this.entityData.set(DATA_CHARGING, charging);
	}

	@Override
	public void onCrossbowAttackPerformed() {
		this.noActionTime = 0;
	}

	// ---- misc -----------------------------------------------------------------------------------------------------

	/** 3.0 swarm-as-one (armies key {@code swarm}): in R2 the push query runs every 2nd tick; off or in R0-R1 it is vanilla. */
	@Override
	protected void pushEntities() {
		if (dev.elsewhere.kingdom.armies.swarm.SwarmPush.mayPushNow(this)) super.pushEntities();
	}

	/** 3.0 swarm-as-one: allied soldiers in R1-R2 don't push each other (enemies, mounts, players and mobs still do). */
	@Override
	protected void doPush(Entity entity) {
		if (!dev.elsewhere.kingdom.armies.swarm.SwarmPush.skip(this, entity)) super.doPush(entity);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public void remove(RemovalReason reason) {
		// only the live entity leaves its squad (a detached copy with the same UUID must not)
		if (reason == RemovalReason.DISCARDED && !virtualizing && level() instanceof ServerLevel sl && squadId != null && sl.getEntity(getUUID()) == this) KingdomState.get(sl).leave(this);
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putString("KingdomUnit", unitId.toString());
		out.putString("KingdomFaction", factionId.toString());
		if (squadId != null) out.putString("KingdomSquad", squadId.toString());
		out.putInt("KingdomRout", routTicks);
		out.putBoolean("KingdomEquipped", equipped);
		out.putBoolean("KingdomMounted", mountSpawned);
		out.putBoolean("KingdomHorn", hornUsed);
		out.putBoolean("KingdomResting", resting);
	}

	/** Captains: 25% Survey Map fragment (K-D's {@code elsewhere:lore_survey_fragment}, when that module is present). */
	public static final Identifier SURVEY_FRAGMENT = KingdomContent.id("lore_survey_fragment");
	public static final float SURVEY_FRAGMENT_CHANCE = 0.25F;

	/** Base loot is per faction (faction file {@code economy.currency}): a soldier of a faction with its own currency drops that instead of Aldermark Crowns. */
	@Override
	public net.minecraft.world.entity.item.@org.jspecify.annotations.Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack) {
		if (!stack.isEmpty() && KingdomItems.ALDERMARK_CROWN != null && stack.is(KingdomItems.ALDERMARK_CROWN)) {
			FactionDef d = Factions.def(Factions.ledgerOf(factionId));
			Identifier cur = d == null ? null : d.economy().currency().orElse(null);
			if (cur != null) {
				var item = BuiltInRegistries.ITEM.getOptional(cur);
				if (item.isPresent() && item.get() != KingdomItems.ALDERMARK_CROWN) stack = new ItemStack(item.get(), stack.getCount());
			}
		}
		return super.spawnAtLocation(level, stack);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (killedByPlayer && unit().ability() == UnitType.Ability.CAPTAIN && random.nextFloat() < SURVEY_FRAGMENT_CHANCE) {
			BuiltInRegistries.ITEM.getOptional(SURVEY_FRAGMENT).ifPresent(i -> spawnAtLocation(level, new ItemStack(i)));
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		Identifier u = Identifier.tryParse(in.getStringOr("KingdomUnit", ""));
		if (u != null) unitId = u;
		Identifier f = Identifier.tryParse(in.getStringOr("KingdomFaction", ""));
		if (f != null) setFaction(f);
		squadId = in.getString("KingdomSquad").map(s -> {
			try {
				return UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
		routTicks = in.getIntOr("KingdomRout", 0);
		equipped = in.getBooleanOr("KingdomEquipped", equipped);
		mountSpawned = in.getBooleanOr("KingdomMounted", mountSpawned);
		hornUsed = in.getBooleanOr("KingdomHorn", false);
		if (in.getBooleanOr("KingdomResting", false)) setResting(true);
		if (equipped) {
			this.entityData.set(DATA_SKIN, unit().skin().toString());
			reapplyStatsOnLoad();
		}
	}
}
