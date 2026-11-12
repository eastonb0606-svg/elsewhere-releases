package dev.elsewhere.duelist.boss;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.DuelistEvents;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.kit.BlockBuilder;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.boss.Behaviours.*;
import dev.elsewhere.duelist.kit.Kit;
import dev.elsewhere.duelist.kit.Move;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The boss base entity: plumbing for a boss that fights like a player. It walks and swings, telegraphs every attack in
 * its own style, shows a boss bar ({@code boss_ui} reads "Name · Label"), and plays its theme through {@code boss_music}
 * (a data line in {@code boss_music.json} keyed by the entity type id). Every player-style behaviour (circle-strafe,
 * sprint-jump crit, shield, weapon swap, bow, crossbow, eat, drink, pearl, temporary blocks) is <b>off unless the boss's
 * {@link DuelistProfile} opts in</b>, with that boss's own numbers. Damage goes through {@link #hurtRaw} so the boss's
 * caps hold. A faction lane extends it with a profile, signature moves and a last stand. See API.md.
 */
public class HumanBoss extends Monster {
	private static final EntityDataAccessor<String> DATA_ACTION = SynchedEntityData.defineId(HumanBoss.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_ACTION_TICKS = SynchedEntityData.defineId(HumanBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION_TOTAL = SynchedEntityData.defineId(HumanBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(HumanBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_RAISED = SynchedEntityData.defineId(HumanBoss.class, EntityDataSerializers.BOOLEAN);
	/** 3.0 P4/B4: the running move's pose key as one byte ({@link MovePose}: key, strike bit, sequence bit); changes at wind-up start, fire and clear only. */
	private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(HumanBoss.class, EntityDataSerializers.BYTE);
	private static final Identifier STRAFE_MOD = DuelistContent.id("duelist_strafe");
	private static final Identifier COURT_HP = DuelistContent.id("duelist_court_hp");

	public enum Weapon { MELEE, THROWN, RANGED }

	public final DuelistProfile profile;
	public final PhaseController phase;
	public final Telegraph telegraph = new Telegraph();
	private final dev.elsewhere.duelist.foresight.ForesightBossEvent bossEvent;
	/** Moves fired and things that happened, by id (tests and faction lanes read this). */
	public final Map<String, Integer> stats = new HashMap<>();
	private final Map<String, Long> cooldowns = new HashMap<>();
	private final Map<UUID, Long> damagers = new HashMap<>();
	/** Squad key for the khopesh hook (at most one hook per 40 ticks per key). Defaults to this boss's UUID. */
	public Object squadKey;

	public BlockPos home = BlockPos.ZERO;
	boolean homeSet;
	public int arenaRadius = 48;
	/** Behaviours switched off at run time (a phase can stop using its bash, a boss can stop pearling). */
	private final EnumSet<Move> disabled = EnumSet.noneOf(Move.class);

	// weapon / swap
	Weapon current = Weapon.MELEE;
	private int swapLeft;
	private Weapon swapTo = Weapon.MELEE;
	private long lastSwap = -1000;
	// K1
	private boolean clockwise = true;
	private int strafeFlip, stillTicks, kiteTicks;
	private boolean strafing;
	// swing
	private long lastSwing = -1000;
	// K2
	private int critStage; // 0 none, 1 run, 2 air
	private long critReadyAt;
	private Vec3 critStart = Vec3.ZERO;
	private int critTimer;
	// K3
	private int shieldHeld, shieldHoldTarget, shieldLowered = 100, shieldDisabled;
	private long bashReadyAt;
	boolean bashing;
	private long nextPlayerShieldDisable;
	// K5/K6
	private int drawLeft, holdLeft;
	private boolean crossbowLoaded;
	private long lastShot = -1000;
	// K7/K8
	private @Nullable String healing; // "eat" or "drink"
	private int healUses, retreatTicks;
	private long healLockUntil;
	private int eatRegenLeft;
	private float eatPerTick;
	// K9
	private int pearls, unreachableTicks;
	private long pearlReadyAt;
	// K11, dodge, stagger
	int lastStandTicks, stagger, dodgeTicks;
	private float lastStandHeal;
	private boolean lastStandPhase;
	private @Nullable Runnable lastStandAfter;
	private boolean duel, yielded;
	private int yieldTimer;
	// misc
	public final BlockBuilder blocks = new BlockBuilder(this);
	private int phaseSync;

	public HumanBoss(EntityType<? extends HumanBoss> type, Level level, DuelistProfile profile) {
		super(type, level);
		this.profile = profile;
		this.phase = new PhaseController(profile.phases);
		if (profile.lastStand != null) this.phase.onLethalDamage(profile.lastStand, profile.lastStandMax);
		this.squadKey = this.getUUID();
		this.setPersistenceRequired();
		this.xpReward = 0;
		this.bossEvent = new dev.elsewhere.duelist.foresight.ForesightBossEvent(UUID.randomUUID(), getDisplayName(), profile.barColor, BossEvent.BossBarOverlay.PROGRESS);
		this.bossEvent.setVisible(true);
		this.duel = false;
		for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0F);
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) hp.setBaseValue(profile.maxHp);
		this.setHealth((float) profile.maxHp);
		HumanBossHealth.enforce(this);
	}

	/** RIVALS-DECISIONS 13 (300 HP cap compensation): a human boss's own defensive choices (block, parry, guard, sidestep) are made 1.3 times as often. */
	public static final float DEFENCE_FREQUENCY = 1.3F;
	/** The most likely a defensive choice may become. */
	public static final float DEFENCE_MAX_CHANCE = 0.95F;

	/** A defence choice's probability {@code p} made 1.3 times as likely, at most 0.95. Wind-ups and telegraphs are not touched. */
	public static float defenceChance(float p) {
		return Math.min(DEFENCE_MAX_CHANCE, p * DEFENCE_FREQUENCY);
	}

	/** A defence's repeat interval in ticks made 1.3 times as frequent (interval / 1.3), at least 1. */
	public static int defenceInterval(int ticks) {
		return Math.max(1, Math.round(ticks / DEFENCE_FREQUENCY));
	}

	/** Attributes for a type built on this profile: register with {@code FabricDefaultAttributeRegistry}. */
	public static AttributeSupplier.Builder attributes(DuelistProfile p) {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, p.maxHp)
			.add(Attributes.MOVEMENT_SPEED, p.walkSpeed)
			.add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
	}

	/** A plain healing potion stack, for a profile's {@code potion(...)}. */
	public static ItemStack healingPotion() {
		return PotionContents.createItemStack(Items.POTION, Potions.HEALING);
	}

	// ---- shield stack ---------------------------------------------------------------------------------------------

	/** A vanilla shield whose block arc is {@code arcDegrees} and whose raise delay is {@code raiseDelayTicks}. */
	public static ItemStack shieldStack(float arcDegrees, int raiseDelayTicks) {
		ItemStack s = new ItemStack(Items.SHIELD);
		BlocksAttacks v = s.get(DataComponents.BLOCKS_ATTACKS);
		if (v != null) {
			s.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(raiseDelayTicks / 20F, v.disableCooldownScale(),
				List.of(new BlocksAttacks.DamageReduction(arcDegrees / 2F, Optional.empty(), 0F, 1F)), v.itemDamage(), v.bypassedBy(), v.blockSound(), v.disableSound()));
		}
		return s;
	}

	// ---- set-up ---------------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_ACTION, "");
		b.define(DATA_ACTION_TICKS, 0);
		b.define(DATA_ACTION_TOTAL, 0);
		b.define(DATA_PHASE, 0);
		b.define(DATA_RAISED, false);
		b.define(DATA_POSE, (byte) 0);
	}

	/** True if the boss opted into {@code m} in its profile and it is not switched off at run time. */
	public boolean on(Move m) {
		return profile.has(m) && !disabled.contains(m);
	}

	/** Switches an opted-in behaviour on or off at run time (a phase that drops the shield, a boss that stops pearling). Cannot turn on what the profile did not opt into. */
	public void setEnabled(Move m, boolean enabled) {
		if (enabled) disabled.remove(m);
		else disabled.add(m);
	}

	/** This boss's telegraph look. */
	public TelegraphStyle style() {
		return profile.style;
	}

	/** Gives the boss its weapons. Override to add armor too (call super). Runs once, on the first server tick. */
	protected void applyLoadout() {
		setItemSlot(EquipmentSlot.MAINHAND, profile.melee.get());
		if (profile.shield != null) setItemSlot(EquipmentSlot.OFFHAND, shieldStack(profile.shield.arcDegrees(), profile.shield.raiseDelay()));
		else setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		current = Weapon.MELEE;
	}

	private boolean equipped;
	private int forcedShield;
	/** Test and scripting switch: the boss keeps its timers, shield and phases but never picks a target or an action. */
	public boolean passive;

	@Override
	public void tick() {
		if (!equipped && !level().isClientSide()) {
			equipped = true;
			applyLoadout();
		}
		super.tick();
	}

	/** Raises the shield for {@code ticks} regardless of threats (a scripted shield wall, tests). */
	public void forceShield(int ticks) {
		forcedShield = Math.max(forcedShield, ticks);
	}

	/** Starts a shield bash now (tests, scripted moves). */
	public void bashNow(LivingEntity t) {
		startBash(t);
	}

	public Component bossBarName() {
		return bossEvent.getName();
	}

	public long pearlCooldownLeft() {
		return Math.max(0, pearlReadyAt - now());
	}

	public void debugSetPearls(int n) {
		pearls = n;
	}

	public void debugClearPearlCooldown() {
		pearlReadyAt = 0;
	}

	public void debugSetHealUses(int n) {
		healUses = n;
		healLockUntil = 0;
	}

	public int critStage() {
		return critStage;
	}

	// ---- accessors ------------------------------------------------------------------------------------------------

	public boolean isDuel() {
		return duel;
	}

	/** Duel mode (RIVALS.md 4.3): the boss yields at 10 percent HP instead of dying; its own last stand, if it has one left, fires first. The court lane turns it on for an accepted challenge. */
	public void setDuel(boolean d) {
		this.duel = d;
	}

	public boolean yielded() {
		return yielded;
	}

	public int phaseIndex() {
		return phase.phase();
	}

	public String action() {
		return this.entityData.get(DATA_ACTION);
	}

	public int actionTicks() {
		return this.entityData.get(DATA_ACTION_TICKS);
	}

	public int actionTotal() {
		return this.entityData.get(DATA_ACTION_TOTAL);
	}

	public boolean armRaised() {
		return this.entityData.get(DATA_RAISED);
	}

	/** 3.0 P4/B4: the synced pose byte (see {@link MovePose#key}, {@link MovePose#struck}). Client and server. */
	public byte poseByte() {
		return this.entityData.get(DATA_POSE);
	}

	/** The synced phase index (0 = first phase); readable on the client, unlike {@link #phaseIndex()}. */
	public int syncedPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	public Weapon weapon() {
		return current;
	}

	public boolean shieldUp() {
		return isUsingItem() && getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	public int shieldDisabledTicks() {
		return shieldDisabled;
	}

	public int healUses() {
		return healUses;
	}

	public int pearlsUsed() {
		return pearls;
	}

	public boolean isStaggered() {
		return stagger > 0;
	}

	/** True during a last-stand window ({@link #lastStand}). */
	public boolean inLastStand() {
		return lastStandTicks > 0;
	}

	public boolean isHealing() {
		return healing != null;
	}

	/** How many times a last stand has kept this boss alive. */
	public int lastStandUses() {
		return phase.lastStandUses();
	}

	public int stat(String id) {
		return stats.getOrDefault(id, 0);
	}

	public void bump(String id) {
		stats.merge(id, 1, Integer::sum);
	}

	/** Seconds-independent: the time in ticks the boss has lived (use for cooldown maths in moves). */
	public long now() {
		return level().getGameTime();
	}

	public boolean ready(String id) {
		return now() >= cooldowns.getOrDefault(id, 0L);
	}

	public void setCooldown(String id, int ticks) {
		cooldowns.put(id, now() + ticks);
	}

	public long cooldownLeft(String id) {
		return Math.max(0, cooldowns.getOrDefault(id, 0L) - now());
	}

	public void setHome(BlockPos p) {
		this.home = p;
		this.homeSet = true;
	}

	/** Players that hurt this boss in the last 5 minutes (loot and the kill credit). */
	public List<ServerPlayer> participants(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		long now = level.getGameTime();
		for (var e : damagers.entrySet()) {
			if (now - e.getValue() > 6000) continue;
			if (level.getEntity(e.getKey()) instanceof ServerPlayer p) out.add(p);
		}
		return out;
	}

	// ---- hooks for subclasses -------------------------------------------------------------------------------------

	/** A wind-up began (pose, extra sound). */
	protected void onTelegraphStart(Telegraph t) {}

	/** A signature move's own pose key for the wind-up about to start (set by {@link #fireSignature}, read once). */
	byte pendingPose;
	private int swingPoses;

	/** 3.0 P4/B4: the pose key of a wind-up that is starting: the signature's own key, the plain swing by the weapon in hand, else the {@link MovePose} table. */
	byte poseFor(String id, Telegraph.Cue cue) {
		byte p = pendingPose;
		pendingPose = 0;
		if (p != MovePose.NONE) return p;
		if ("swing".equals(id)) return MovePose.swing(getMainHandItem(), swingPoses++);
		return MovePose.of(id, cue != null && cue.crouch(), cue != null && cue.raised());
	}

	/** Called by {@link Telegraph} when a wind-up starts: syncs the pose and the raised arm at once, then the subclass hook. */
	void telegraphStarted(Telegraph t) {
		syncAction();
		onTelegraphStart(t);
	}

	/** The boss entered a new phase. Cooldowns were already reset. */
	protected void onPhaseChange(ServerLevel level, int newPhase) {}

	/** The boss was defeated (killed or yielded). {@code participants} = players who hurt it. Roll drops here. */
	protected void onDefeated(ServerLevel level, List<ServerPlayer> participants) {}

	/** Called every server tick after the kit ran, with the current target (may be null). */
	protected void extraTick(ServerLevel level, @Nullable LivingEntity target) {}

	// ---- damage ---------------------------------------------------------------------------------------------------

	public float damageMultiplier() {
		double m = profile.court.damageMultiplier();
		if (profile.frenzy != null) m *= profile.frenzy.multiplier(getHealth() / getMaxHealth());
		return (float) m;
	}

	/**
	 * Deals {@code raw} damage (before the victim's armor) with the boss's multipliers (court bonus, frenzy), clamped to the
	 * cap for {@code kind}. MAGIC converts raw to armour-ignoring damage. Every attack of the boss goes through here.
	 */
	public boolean hurtRaw(ServerLevel level, LivingEntity victim, float raw, DamageCaps kind, DamageSource src) {
		float amt = raw * damageMultiplier();
		amt = Math.min(amt, kind.cap(profile));
		if (kind == DamageCaps.MAGIC) amt *= Kit.MAGIC_RAW_FACTOR;
		boolean hit = victim.hurtServer(level, src, amt);
		for (DuelistEvents.Hit h : DuelistEvents.HIT) h.onHit(this, victim, amt, kind, hit);
		if (hit) {
			stats.merge("hits", 1, Integer::sum);
			lastRaw = amt;
			if (kind != DamageCaps.MAGIC && amt > maxRaw) maxRaw = amt;
		}
		return hit;
	}

	public float lastRaw, maxRaw;

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return target instanceof LivingEntity le && strike(level, le, false);
	}

	/** A player-style melee hit: lowers the shield, swings, raw damage from the profile (x1.5 if {@code crit}), knockback, crit effects. */
	public boolean strike(ServerLevel level, LivingEntity target, boolean crit) {
		if (shieldUp()) {
			stopUsingItem();
			shieldLowered = 0;
		}
		swingForAttack(InteractionHand.MAIN_HAND);
		ItemStack weapon = getWeaponItem();
		DamageSource src = weapon.getDamageSource(this);
		float raw = profile.swingRaw * (crit && profile.crit != null ? profile.crit.multiplier() : 1F);
		Vec3 old = target.getDeltaMovement();
		boolean hurt = hurtRaw(level, target, raw, crit ? DamageCaps.SIGNATURE : DamageCaps.MINOR, src);
		lastSwing = now();
		if (hurt) {
			causeExtraKnockback(target, getKnockback(target, src), old, src, raw, true);
			if (crit) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, getSoundSource(), 1F, 1F);
				level.getChunkSource().sendToTrackingPlayers(target, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
				bump("crit_hits");
			} else level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, getSoundSource(), 0.7F, 1F);
			weapon.hurtEnemy(target, this);
			EnchantmentHelper.doPostAttackEffects(level, target, src);
			setLastHurtMob(target);
		}
		return hurt;
	}

	/** Opt-in ({@code shieldBreak}): this boss's weapon takes a player's shield down for its own ticks, at most once per its own cooldown. A bash never disables. */
	@Override
	public float getSecondsToDisableBlocking() {
		ShieldBreak sb = profile.shieldBreak;
		if (sb == null || bashing) return 0F;
		float s = super.getSecondsToDisableBlocking();
		if (s <= 0F) return 0F;
		long t = now();
		if (t < nextPlayerShieldDisable) return 0F;
		nextPlayerShieldDisable = t + sb.cooldown();
		bump("player_shield_disabled");
		return sb.ticks() / 20F;
	}

	/** A hit on the boss's raised shield by an axe (or anything that disables shields) takes it down for 100 ticks. */
	@Override
	protected void blockUsingItem(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked) {
		ItemStack with = getItemBlockingWith();
		BlocksAttacks ba = with != null ? with.get(DataComponents.BLOCKS_ATTACKS) : null;
		super.blockUsingItem(level, attacker, source, damage, fullyBlocked);
		float secs = attacker.getSecondsToDisableBlocking();
		if (secs > 0F && ba != null) {
			ba.disable(level, this, secs, with);
			int cap = profile.shield != null ? profile.shield.axeDisableTicks() : Math.round(secs * 20F);
			disableShield(Math.max(1, Math.min(cap, Math.round(secs * 20F))));
			bump("shield_disabled");
		}
		bump("blocked");
	}

	/** Takes the shield down for {@code ticks} (axe hit, khopesh hook). */
	public void disableShield(int ticks) {
		shieldDisabled = Math.max(shieldDisabled, ticks);
		if (shieldUp()) stopUsingItem();
		shieldLowered = 0;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if ((lastStandTicks > 0 || dodgeTicks > 0 || yielded) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (damage > 0F && source.getEntity() instanceof Player p) damagers.put(p.getUUID(), level.getGameTime());
		boolean airborneCrit = profile.crit != null && critStage == 2 && !onGround() && damage > 0F && source.getEntity() instanceof LivingEntity && !source.is(DamageTypeTags.IS_PROJECTILE);
		boolean wasHealing = healing != null;
		// Duel mode: yield at 10 percent instead of dying. The boss's own last stand, if it still has one, fires first (RIVAL-BOSSES v3).
		if (duel && !yielded && damage > 0F && getHealth() - damage <= getMaxHealth() * Kit.YIELD_HP && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (isInvulnerableTo(level, source)) return false;
			if (phase.lethal(this, level, source, damage)) {
				if (getHealth() < 1F) setHealth(1F);
				bump("last_stand");
				return true;
			}
			float room = getHealth() - getMaxHealth() * Kit.YIELD_HP;
			if (room <= 0F || super.hurtServer(level, source, Math.min(damage, room))) {
				beginYield(level);
				return true;
			}
			return false;
		}
		boolean r = super.hurtServer(level, source, damage);
		if (r) {
			if (wasHealing) cancelHeal(true);
			if (airborneCrit) {
				stagger = profile.crit.staggerTicks();
				critStage = 0;
				setSprinting(false);
				telegraph.cancel();
				syncAction(); // 3.0 P4: the wind-up pose drops at once and no strike plays for a staggered move
				bump("staggered");
				level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1, getZ(), 8, 0.3, 0.4, 0.3, 0.1);
			}
			if (source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() != null && shieldUp()) getLookControl().setLookAt(source.getEntity(), 30F, 30F);
		}
		return r;
	}

	// ---- lifecycle ------------------------------------------------------------------------------------------------

	@Override
	public void startSeenByPlayer(ServerPlayer p) {
		super.startSeenByPlayer(p);
		bossEvent.addPlayer(p);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer p) {
		super.stopSeenByPlayer(p);
		bossEvent.removePlayer(p);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel sl) DuelBlocks.clearOwner(sl, getUUID());
		bossEvent.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel sl) {
			DuelBlocks.clearOwner(sl, getUUID());
			onDefeated(sl, participants(sl));
		}
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	/** Called from the death-prevention hook ({@link DuelistContent}) when a hit would kill the boss: runs this boss's own last stand, if it has one. True = the boss survives. */
	public boolean onLethal(DamageSource source, float amount) {
		if (yielded || !(level() instanceof ServerLevel level)) return false;
		if (!phase.lethal(this, level, source, amount)) return false;
		if (getHealth() < 1F) setHealth(1F);
		bump("last_stand");
		return true;
	}

	/**
	 * The plumbing for a last stand (call it from your {@link PhaseController.LethalHook}): the boss drops to 1 HP, cancels what it
	 * was doing and stands invulnerable for {@code lockTicks}. When the window ends it heals to {@code healTo} of max HP (0 = stays
	 * at 1 HP), optionally jumps to the last phase, then runs {@code after}. What the player sees and hears during the window is the
	 * ruler's own business (particles, sounds, a speech, a summon): do it in the hook or in {@code after}.
	 */
	public void lastStand(int lockTicks, float healTo, boolean forceLastPhase, @Nullable Runnable after) {
		lastStandTicks = Math.max(1, lockTicks);
		lastStandHeal = healTo;
		lastStandPhase = forceLastPhase;
		lastStandAfter = after;
		setHealth(1F);
		telegraph.cancel();
		syncAction();
		cancelHeal(false);
		critStage = 0;
		stopUsingItem();
	}

	private void beginYield(ServerLevel level) {
		yielded = true;
		yieldTimer = 30;
		telegraph.cancel();
		syncAction();
		cancelHeal(false);
		stopUsingItem();
		setTarget(null);
		getNavigation().stop();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(), SoundSource.HOSTILE, 2F, 1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5, getZ(), 12, 0.4, 0.4, 0.4, 0.0);
		for (DuelistEvents.Yielded y : DuelistEvents.YIELD) y.onYield(this);
		bump("yield");
	}

	// ---- the brain ------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!homeSet) setHome(blockPosition());
		LivingEntity t = getTarget();
		if (t != null && (!t.isAlive() || (t instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			setTarget(null);
			t = null;
		}
		tickTimers(level);
		tickBar(level);
		if (yielded) {
			if (--yieldTimer <= 0) finishYield(level);
			return;
		}
		if (lastStandTicks > 0) {
			getNavigation().stop();
			if (--lastStandTicks == 0) {
				if (lastStandHeal > 0F) setHealth(Math.max(1F, getMaxHealth() * lastStandHeal));
				if (lastStandPhase && phase.forceLast() >= 0) {
					resetCooldowns();
					onPhaseChange(level, phase.phase());
					this.entityData.set(DATA_PHASE, phase.phase());
				}
				Runnable r = lastStandAfter;
				lastStandAfter = null;
				if (r != null) r.run();
			}
			return;
		}
		if (stagger > 0) {
			stagger--;
			getNavigation().stop();
			return;
		}
		if (passive) {
			tickShield(null);
			if (telegraph.active()) {
				telegraph.tick(this);
				syncAction();
			}
			return;
		}
		tickShield(t);
		boolean busy = telegraph.active() || healing != null || critStage != 0 || swapLeft > 0 || drawLeft > 0 || holdLeft > 0;
		if (on(Move.BLOCKS) && !busy) blocks.tick(level, t);
		if (t == null) {
			endIdle();
			extraTick(level, null);
			return;
		}
		double dist = distanceTo(t);
		getLookControl().setLookAt(t, 30F, 30F);
		track(level, t, dist);
		if (!busy && blocks.holding()) {
			getNavigation().stop();
			setSprinting(false);
			Vec3 m = getDeltaMovement();
			setDeltaMovement(0, m.y, 0);
			return;
		}
		if (telegraph.active()) {
			if (telegraph.freeze) getNavigation().stop();
			else move(t, dist);
			telegraph.tick(this);
			syncAction();
			return;
		}
		syncAction();
		if (healing != null) {
			tickHeal(level, t, dist);
			return;
		}
		if (critStage != 0) {
			tickCrit(level, t, dist);
			return;
		}
		if (swapLeft > 0) {
			if (--swapLeft == 0) finishSwap();
			move(t, dist);
			return;
		}
		if (drawLeft > 0 || holdLeft > 0) {
			tickRanged(level, t, dist);
			return;
		}
		if (!tryHeal(level, t, dist) && !tryPearl(level, t, dist) && !trySignature(level, t, dist) && !tryCrit(level, t, dist)
			&& !trySwap(dist) && !tryRanged(level, t, dist) && !tryThrown(level, t, dist)) {
			if (current == Weapon.MELEE || (profile.swap != null && dist <= profile.swap.meleeRange())) melee(level, t, dist);
			else move(t, dist);
		}
		extraTick(level, t);
	}

	private void finishYield(ServerLevel level) {
		yielded = false;
		Player p = getLastHurtByPlayer();
		if (p == null) {
			List<ServerPlayer> ps = participants(level);
			if (!ps.isEmpty()) p = ps.get(0);
		}
		if (p != null) setLastHurtByPlayer(p, 100);
		duel = false;
		setHealth(0F);
		die(p != null ? damageSources().playerAttack(p) : damageSources().generic());
	}

	private void tickTimers(ServerLevel level) {
		if (shieldDisabled > 0) shieldDisabled--;
		if (shieldLowered < 1000) shieldLowered++;
		if (dodgeTicks > 0) dodgeTicks--;
		if (eatRegenLeft > 0) {
			eatRegenLeft--;
			heal(eatPerTick);
		}
		if (tickCount % 100 == 0) {
			AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
			double bonus = profile.court.bonusHp();
			if (hp != null) {
				AttributeModifier cur = hp.getModifier(COURT_HP);
				if (cur == null ? bonus != 0 : cur.amount() != bonus) {
					hp.addOrUpdateTransientModifier(new AttributeModifier(COURT_HP, bonus, AttributeModifier.Operation.ADD_VALUE));
					if (bonus > 0 && cur != null) heal((float) (bonus - cur.amount()));
					HumanBossHealth.enforce(this); // a max-health modifier just changed: the only place duelist code changes one
				}
			}
		}
		int p = phase.update(getHealth() / getMaxHealth());
		if (p >= 0) {
			resetCooldowns();
			this.entityData.set(DATA_PHASE, p);
			level.playSound(null, getX(), getY(), getZ(), profile.style.sound(), SoundSource.HOSTILE, 2F, profile.style.pitch() * 0.6F);
			bump("phase_change");
			onPhaseChange(level, p);
		}
	}

	/** A phase change resets every cooldown. */
	void resetCooldowns() {
		cooldowns.clear();
		critReadyAt = 0;
		bashReadyAt = 0;
		pearlReadyAt = Math.max(pearlReadyAt, now());
	}

	private void tickBar(ServerLevel level) {
		if (tickCount % 4 != 0) return;
		bossEvent.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0F, 1F));
		String label = telegraph.active() ? telegraph.label : (healing != null ? (healing.equals("eat") ? "Eating" : "Drinking") : (lastStandTicks > 0 ? "Last Stand" : ""));
		bossEvent.setLabelled(getType().getDescription(), label);   // B5: the move label goes only to players with foresight gear
	}

	void syncAction() {
		byte pose = MovePose.synced(telegraph.active(), telegraph.pose, tickCount - telegraph.struckAt);
		if (pose != this.entityData.get(DATA_POSE)) this.entityData.set(DATA_POSE, pose);
		String id = telegraph.active() ? telegraph.id : "";
		if (!id.equals(this.entityData.get(DATA_ACTION))) this.entityData.set(DATA_ACTION, id);
		this.entityData.set(DATA_ACTION_TICKS, telegraph.left);
		this.entityData.set(DATA_ACTION_TOTAL, telegraph.total);
		boolean raised = telegraph.active() && telegraph.cue().raised();
		if (raised != this.entityData.get(DATA_RAISED)) this.entityData.set(DATA_RAISED, raised);
		boolean crouch = (telegraph.active() && telegraph.cue().crouch()) || lastStandTicks > 0;
		Pose want = crouch ? Pose.CROUCHING : Pose.STANDING;
		if (getPose() != want && (getPose() == Pose.STANDING || getPose() == Pose.CROUCHING)) setPose(want);
	}

	private void endIdle() {
		if (shieldUp()) stopUsingItem();
		if (drawLeft > 0 || holdLeft > 0) {
			stopUsingItem();
			drawLeft = holdLeft = 0;
		}
		setStrafing(false);
		if (telegraph.active()) telegraph.cancel();
		syncAction();
	}

	/** Pearl-reachability tracking (K9). */
	private void track(ServerLevel level, LivingEntity t, double dist) {
		if (tickCount % 10 != 0) return;
		if (profile.pearl == null || dist <= profile.pearl.minDist()) {
			unreachableTicks = 0;
			return;
		}
		PathNavigation nav = getNavigation();
		var path = nav.createPath(t, 0);
		if (path == null || !path.canReach()) unreachableTicks += 10;
		else unreachableTicks = 0;
	}

	// ---- K1 movement ----------------------------------------------------------------------------------------------

	private void setStrafing(boolean on) {
		if (on == strafing) return;
		strafing = on;
		AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
		if (sp == null) return;
		if (on && profile.strafe != null) sp.addOrUpdateTransientModifier(new AttributeModifier(STRAFE_MOD, profile.strafe.speedMult() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		else sp.removeModifier(STRAFE_MOD);
	}

	/** Movement in a fight: circle-strafing if the boss opted in, otherwise a plain approach that holds position once in range. */
	protected void move(LivingEntity t, double dist) {
		if (on(Move.STRAFE)) strafe(profile.strafe, t, dist);
		else approach(t, dist);
	}

	/** The range a non-melee weapon wants to keep, derived from the boss's own swap ranges. */
	private double wantRange() {
		Swap sw = profile.swap;
		if (sw == null) return profile.reach;
		return current == Weapon.RANGED ? sw.thrownRange() * 1.3 : (sw.meleeRange() + sw.thrownRange()) / 2.0;
	}

	/** No strafing: walk up to reach and stand there (a boss that holds its ground). */
	private void approach(LivingEntity t, double dist) {
		setStrafing(false);
		if (isSprinting() && critStage == 0) setSprinting(false);
		boolean melee = current == Weapon.MELEE;
		if (melee && on(Move.CRIT) && now() >= critReadyAt) {
			// a sprint-critter that does not strafe: step back for room, then wait in its run-up band for the crouch
			SprintCrit cc = profile.crit;
			if (kiteTicks > 0 && dist < cc.runUp() + 4.5) {
				Vec3 away = position().add(position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(4));
				getNavigation().moveTo(away.x, away.y, away.z, 1.0);
				return;
			}
			if (dist >= cc.runUp() + 3.3 && dist <= cc.runUp() + 9.0 && onGround() && !isInWater() && hasLineOfSight(t)) {
				getNavigation().stop();
				return;
			}
		}
		double stop = melee ? Math.max(0.5, profile.reach - 0.6) : wantRange();
		if (dist > stop) getNavigation().moveTo(t, 1.0);
		else getNavigation().stop();
	}

	/** Circle-strafe, with this boss's speed, flip times and spacing. */
	private void strafe(Strafe sc, LivingEntity t, double dist) {
		if (--strafeFlip <= 0) {
			clockwise = !clockwise;
			strafeFlip = sc.flipMin() + random.nextInt(Math.max(1, sc.flipMax() - sc.flipMin() + 1));
			bump("strafe_flip");
		}
		boolean melee = current == Weapon.MELEE;
		double chase = sc.spacingMax() + 4.5;
		if (dist > chase && melee) {
			setSprinting(true);
			setStrafing(false);
			getNavigation().moveTo(t, 1.0);
			return;
		}
		if (isSprinting() && critStage == 0) setSprinting(false);
		setStrafing(true);
		float fwd = 0F;
		if (melee) {
			double runUp = profile.crit != null ? profile.crit.runUp() : 3.0;
			double lo = kiteTicks > 0 ? runUp + 4.0 : sc.spacingMin(), hi = kiteTicks > 0 ? runUp + 6.0 : sc.spacingMax();
			if (dist > hi) fwd = 1F;
			else if (dist < lo) fwd = -1F;
		} else {
			double want = wantRange();
			if (dist > want + 2) fwd = 1F;
			else if (dist < want - 2) fwd = -1F;
		}
		if (dist > sc.spacingMax() + 2.5 && fwd > 0) {
			getNavigation().moveTo(t, 1.0);
			return;
		}
		// MoveControl.strafe moves relative to the body yaw (getYRot), which only navigation turns: face the target first,
		// as the vanilla bow goal does, or "forward" and "sideways" point wherever the last path left the boss
		lookAt(t, 30F, 30F);
		getMoveControl().strafe(fwd, clockwise ? 0.8F : -0.8F);
		getNavigation().stop();
		// never stand still for long
		if (getDeltaMovement().horizontalDistanceSqr() < 0.0004) {
			if (++stillTicks > sc.maxStillTicks()) {
				stillTicks = 0;
				clockwise = !clockwise;
				strafeFlip = sc.flipMin();
			}
		} else stillTicks = 0;
	}

	private void melee(ServerLevel level, LivingEntity t, double dist) {
		if (kiteTicks > 0) kiteTicks--;
		move(t, dist);
		long now = now();
		if (on(Move.SHIELD) && profile.shield.bash() != null && shieldUp() && dist < 2.8 && now >= bashReadyAt && random.nextInt(25) == 0) {
			startBash(t);
			return;
		}
		double reach = profile.reach;
		if (dist <= reach + 0.3 && now - lastSwing >= profile.swingInterval && !(shieldUp() && shieldHeld < 6) && hasLineOfSight(t)) {
			float raw = profile.swingRaw;
			int windup = profile.swingWindup;
			lastSwing = now + windup; // blocks a second wind-up while this one runs
			telegraph.start(this, "swing", "", windup, Telegraph.Weight.ORDINARY, raw, style().cue(false, true), false, () -> {
				if (!isAlive() || !(level() instanceof ServerLevel sl)) return;
				if (distanceTo(t) <= reach + 0.8 && hasLineOfSight(t)) strike(sl, t, false);
				else lastSwing = now();
				bump("swing");
			});
			return;
		}
		// crit run-up: back off so the sprint has room
		if (on(Move.CRIT) && now >= critReadyAt && kiteTicks <= 0 && dist < 6.0 && random.nextInt(60) == 0) kiteTicks = on(Move.STRAFE) ? 30 : 50;
	}

	// ---- K3 shield ------------------------------------------------------------------------------------------------

	private void tickShield(@Nullable LivingEntity t) {
		if (!on(Move.SHIELD)) return;
		Shield sc = profile.shield;
		boolean up = shieldUp();
		if (forcedShield > 0) {
			forcedShield--;
			if (shieldDisabled > 0) return;
			if (!up && getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
				startUsingItem(InteractionHand.OFF_HAND);
				shieldHeld = 0;
				shieldHoldTarget = 1000;
			} else if (up) shieldHoldTarget = 1000;
			if (forcedShield == 0 && up) {
				stopUsingItem();
				shieldLowered = 0;
			}
			return;
		}
		boolean busy = telegraph.active() && !"bash".equals(telegraph.id) && telegraph.cue().raised() || healing != null || critStage != 0 || drawLeft > 0 || holdLeft > 0 || lastStandTicks > 0 || stagger > 0;
		if (up) {
			shieldHeld++;
			if (shieldHeld >= shieldHoldTarget || shieldDisabled > 0 || busy || t == null) {
				stopUsingItem();
				shieldLowered = 0;
			}
			return;
		}
		if (t == null || busy || shieldDisabled > 0 || shieldLowered < sc.lowerMin() || yielded) return;
		boolean rangedThreat = t.isUsingItem() && (t.getUseItem().is(Items.BOW) || t.getUseItem().is(Items.CROSSBOW)) && distanceTo(t) < 40;
		boolean meleeThreat = distanceTo(t) < 5.0 && tickCount % 10 == 0 && random.nextFloat() < defenceChance(1F / 3F); // RIVALS-DECISIONS 13: blocking is chosen 1.3x as often
		boolean dodgeBusy = swapLeft > 0;
		if ((rangedThreat || meleeThreat) && !dodgeBusy && getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			startUsingItem(InteractionHand.OFF_HAND);
			shieldHeld = 0;
			shieldHoldTarget = sc.holdMin() + random.nextInt(Math.max(1, sc.holdMax() - sc.holdMin() + 1));
			bump("shield_raised");
		}
	}

	private void startBash(LivingEntity t) {
		if (!on(Move.SHIELD) || profile.shield.bash() == null) return;
		Bash bc = profile.shield.bash();
		bashReadyAt = now() + bc.cooldown();
		telegraph.start(this, "bash", "Shield Bash", bc.windup(), Telegraph.Weight.ORDINARY, bc.raw(), style().cue(false, false), true, () -> {
			if (!isAlive() || !(level() instanceof ServerLevel sl)) return;
			if (distanceTo(t) <= 3.6) {
				bashing = true;
				try {
					swingForAttack(InteractionHand.OFF_HAND);
					boolean hit = hurtRaw(sl, t, bc.raw(), DamageCaps.MINOR, damageSources().mobAttack(this));
					if (hit) {
						Vec3 d = t.position().subtract(position()).multiply(1, 0, 1).normalize();
						t.knockback(bc.knockback() * 0.5, -d.x, -d.z, damageSources().mobAttack(this), 0F);
					}
					bump("bash");
				} finally {
					bashing = false;
				}
			}
		});
	}

	// ---- K2 sprint-jump crit --------------------------------------------------------------------------------------

	private boolean tryCrit(ServerLevel level, LivingEntity t, double dist) {
		if (!on(Move.CRIT) || current != Weapon.MELEE || now() < critReadyAt || !onGround() || isInWater()) return false;
		SprintCrit cc = profile.crit;
		if (dist < cc.runUp() + 3.3 || dist > cc.runUp() + 9.0 || !hasLineOfSight(t)) return false;
		if (random.nextInt(12) != 0) return false;
		critReadyAt = now() + cc.cooldown() + cc.crouchTicks();
		bump("crit_started");
		telegraph.start(this, "crit", "Sprint Strike", cc.crouchTicks(), Telegraph.Weight.ORDINARY, profile.swingRaw * cc.multiplier(), style().cue(true, false), true, () -> {
			critStage = 1;
			critStart = position();
			critTimer = 0;
			setSprinting(true);
			setStrafing(false);
			if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 10, 0.3, 0.05, 0.3, 0.02);
		});
		return true;
	}

	private void tickCrit(ServerLevel level, LivingEntity t, double dist) {
		critTimer++;
		if (critStage == 1) {
			getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.3);
			double run = position().subtract(critStart).horizontalDistance();
			if (onGround() && run >= profile.crit.runUp() && dist <= 3.8) {
				Vec3 d = t.position().subtract(position()).multiply(1, 0, 1).normalize();
				jumpFromGround();
				setDeltaMovement(d.x * 0.42, getDeltaMovement().y, d.z * 0.42);
				critStage = 2;
				critTimer = 0;
				bump("crit_jump");
			} else if (critTimer > 40) endCrit();
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 1, 0.2, 0.02, 0.2, 0.0);
			return;
		}
		// airborne
		if (getDeltaMovement().y < 0 && isSprinting()) setSprinting(false); // a player cannot crit while sprinting
		if (!onGround() && getDeltaMovement().y < 0 && dist <= 2.9) {
			boolean hit = strike(level, t, true);
			bump("crit_attack");
			if (hit) bump("crit_landed");
			endCrit();
		} else if ((onGround() && critTimer > 3) || critTimer > 30) {
			bump("crit_missed");
			endCrit();
		}
	}

	private void endCrit() {
		critStage = 0;
		setSprinting(false);
	}

	// ---- K4 weapon swap, K5/K6 bow and crossbow, thrown -----------------------------------------------------------

	/** Weapon for a range, with a 0.7-block hysteresis around the melee and thrown limits so strafing at the edge does not flip weapons. */
	private Weapon weaponFor(double dist) {
		Swap sw = profile.swap;
		boolean thrown = profile.thrown != null, ranged = profile.ranged != null;
		double melee = sw.meleeRange() + (current == Weapon.MELEE ? 0.7 : 0.0);
		double far = sw.thrownRange() + (current == Weapon.RANGED ? 0.0 : 0.7);
		if (dist < melee) return Weapon.MELEE;
		if (dist < far) return thrown ? Weapon.THROWN : Weapon.MELEE;
		return ranged ? Weapon.RANGED : (thrown ? Weapon.THROWN : Weapon.MELEE);
	}

	private boolean trySwap(double dist) {
		if (!on(Move.SWAP)) return false;
		Swap sw = profile.swap;
		Weapon want = weaponFor(dist);
		if (want == current || now() - lastSwap < sw.minGap()) return false;
		lastSwap = now();
		swapTo = want;
		swapLeft = sw.swapTicks();
		stopUsingItem();
		if (level() instanceof ServerLevel l) l.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
		bump("swap");
		return true;
	}

	private void finishSwap() {
		current = swapTo;
		ItemStack s = switch (current) {
			case MELEE -> profile.melee.get();
			case THROWN -> profile.thrown != null ? profile.thrown.get() : profile.melee.get();
			case RANGED -> profile.ranged != null ? profile.ranged.get() : profile.melee.get();
		};
		setItemSlot(EquipmentSlot.MAINHAND, s);
	}

	private boolean rangedIsCrossbow() {
		return getMainHandItem().is(Items.CROSSBOW);
	}

	private boolean tryRanged(ServerLevel level, LivingEntity t, double dist) {
		if (current != Weapon.RANGED || profile.swap == null || dist < profile.swap.thrownRange() - 1 || !hasLineOfSight(t)) return false;
		if (rangedIsCrossbow()) {
			Crossbow cb = profile.crossbow;
			if (!on(Move.CROSSBOW) || now() - lastShot < cb.minGap()) return false;
			startUsingItem(InteractionHand.MAIN_HAND);
			drawLeft = cb.load();
			crossbowLoaded = false;
			holdLeft = 0;
		} else {
			Bow bw = profile.bow;
			if (!on(Move.BOW) || now() - lastShot < bw.minGap()) return false;
			startUsingItem(InteractionHand.MAIN_HAND);
			drawLeft = bw.draw();
		}
		bump("draw");
		move(t, dist);
		return true;
	}

	private void tickRanged(ServerLevel level, LivingEntity t, double dist) {
		move(t, dist);
		boolean xbow = rangedIsCrossbow();
		if (drawLeft > 0) {
			if (!isUsingItem()) startUsingItem(InteractionHand.MAIN_HAND);
			if (--drawLeft == 0) {
				if (xbow) {
					stopUsingItem();
					ItemStack s = getMainHandItem();
					s.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new net.minecraft.world.item.ItemStackTemplate(Items.ARROW)));
					crossbowLoaded = true;
					holdLeft = profile.crossbow.holdMax();
				} else {
					Bow bw = profile.bow;
					shootArrow(level, t, dist, bw.spreadAt(dist), bw.spreadCap(), bw.lead(), bw.raw());
					stopUsingItem();
				}
			}
			return;
		}
		if (holdLeft > 0) {
			holdLeft--;
			Crossbow cb = profile.crossbow;
			boolean los = hasLineOfSight(t);
			if (los && (holdLeft < cb.holdMax() - 6 || tickCount % 3 == 0 && random.nextInt(4) == 0)) {
				shootArrow(level, t, dist, cb.spread(), cb.spread(), 0.5, cb.raw());
				getMainHandItem().set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
				crossbowLoaded = false;
				holdLeft = 0;
			} else if (holdLeft == 0) {
				getMainHandItem().set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
				crossbowLoaded = false;
				lastShot = now();
			}
		}
	}

	/** An arrow of this boss's raw damage with its spread (vanilla inaccuracy units, up to {@code spreadCap}) and target lead. */
	private void shootArrow(ServerLevel level, LivingEntity t, double dist, double spread, double spreadCap, double lead, float raw) {
		spread = Math.min(spreadCap, spread);
		ItemStack bow = getMainHandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, 1.0F, bow);
		arrow.setBaseDamage(raw / 3.1);
		arrow.setCritArrow(false);
		float speed = 3.0F;
		Vec3 v = t.getDeltaMovement();
		double flight = dist / speed;
		double tx = t.getX() + v.x * flight * lead;
		double ty = t.getY(0.5) + v.y * flight * lead;
		double tz = t.getZ() + v.z * flight * lead;
		double dx = tx - getX(), dz = tz - getZ();
		double dy = ty - arrow.getY() + Math.sqrt(dx * dx + dz * dz) * 0.04;
		Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, dy, dz, speed, (float) spread);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
		lastShot = now();
		bump("arrow");
		lastSpread = spread;
	}

	public double lastSpread;

	private boolean tryThrown(ServerLevel level, LivingEntity t, double dist) {
		Swap sw = profile.swap;
		if (sw == null || current != Weapon.THROWN || profile.thrown == null || dist <= sw.meleeRange() || dist > sw.thrownRange() + 1 || !ready("thrown") || !hasLineOfSight(t)) return false;
		telegraph.start(this, "throw", "Throw", sw.throwWindup(), Telegraph.Weight.ORDINARY, sw.thrownRaw(), style().cue(false, true), false, () -> {
			if (!isAlive() || !(level() instanceof ServerLevel sl)) return;
			setCooldown("thrown", sw.throwCooldown());
			swingForAttack(InteractionHand.MAIN_HAND);
			DuelistProjectile.throwAt(sl, this, t, profile.thrown.get(), sw.thrownRaw(), 1.4F);
			bump("throw");
		});
		return true;
	}

	// ---- K7/K8 eat and drink --------------------------------------------------------------------------------------

	private boolean tryHeal(ServerLevel level, LivingEntity t, double dist) {
		if (profile.noHeal || now() < healLockUntil) return false;
		boolean eat = on(Move.EAT), drink = on(Move.DRINK);
		if (!eat && !drink) return false;
		// each kind has its own rule; pick one that is allowed right now
		boolean eatOk = eat && healUses < profile.eat.rule().maxUses() && getHealth() < getMaxHealth() * profile.eat.rule().below();
		boolean drinkOk = drink && healUses < profile.drink.rule().maxUses() && getHealth() < getMaxHealth() * profile.drink.rule().below();
		if (!eatOk && !drinkOk) return false;
		String kind = eatOk && drinkOk ? (random.nextBoolean() ? "eat" : "drink") : (eatOk ? "eat" : "drink");
		HealRule rule = kind.equals("eat") ? profile.eat.rule() : profile.drink.rule();
		if (dist < rule.clearRadius()) {
			if (++retreatTicks > 40) {
				retreatTicks = 0;
				healLockUntil = now() + 60;
				return false;
			}
			Vec3 away = position().add(position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(6));
			setSprinting(true);
			getNavigation().moveTo(away.x, away.y, away.z, 1.2);
			bump("heal_backoff");
			return true;
		}
		retreatTicks = 0;
		healing = kind;
		setSprinting(false);
		getNavigation().stop();
		setItemSlot(EquipmentSlot.MAINHAND, kind.equals("eat") ? profile.food.get() : profile.potion.get());
		startUsingItem(InteractionHand.MAIN_HAND);
		bump("heal_started");
		bump("heal_" + kind);
		return true;
	}

	private void tickHeal(ServerLevel level, LivingEntity t, double dist) {
		getNavigation().stop();
		if (!isUsingItem()) cancelHeal(false);
	}

	/** Vanilla would eat the item and apply its effects; the kit's own numbers apply instead and the item is not spent. */
	@Override
	protected void completeUsingItem() {
		if (healing == null) {
			super.completeUsingItem();
			return;
		}
		String kind = healing;
		stopUsingItem();
		healing = null;
		healUses++;
		if (kind.equals("eat")) {
			Eat ec = profile.eat;
			eatRegenLeft = ec.spreadTicks();
			eatPerTick = getMaxHealth() * ec.heal() / Math.max(1, ec.spreadTicks());
		} else heal(getMaxHealth() * profile.drink.heal());
		restoreWeapon();
		if (level() instanceof ServerLevel l) l.playSound(null, getX(), getY(), getZ(), kind.equals("eat") ? SoundEvents.GENERIC_EAT.value() : SoundEvents.GENERIC_DRINK.value(), SoundSource.HOSTILE, 1F, 1F);
		bump("heal_done");
	}

	/** Cancels an eat or drink. An interrupted use is not spent but locks the next try for 120 ticks. */
	void cancelHeal(boolean interrupted) {
		if (healing == null) return;
		String interruptedKind = healing;
		healing = null;
		stopUsingItem();
		restoreWeapon();
		if (interrupted) {
			HealRule hr = interruptedKind.equals("eat") && profile.eat != null ? profile.eat.rule() : (profile.drink != null ? profile.drink.rule() : profile.eat.rule());
			healLockUntil = now() + hr.lockout();
			bump("heal_interrupted");
		}
	}

	private void restoreWeapon() {
		setItemSlot(EquipmentSlot.MAINHAND, switch (current) {
			case MELEE -> profile.melee.get();
			case THROWN -> profile.thrown != null ? profile.thrown.get() : profile.melee.get();
			case RANGED -> profile.ranged != null ? profile.ranged.get() : profile.melee.get();
		});
	}

	// ---- K9 pearl hop ---------------------------------------------------------------------------------------------

	private boolean tryPearl(ServerLevel level, LivingEntity t, double dist) {
		if (!on(Move.PEARL)) return false;
		Pearl pc = profile.pearl;
		if (pearls >= pc.max() || now() < pearlReadyAt) return false;
		if (dist <= pc.minDist() || dist > pc.maxRange() + pc.landRadius() || unreachableTicks < pc.unreachableTicks()) return false;
		BlockPos land = findLanding(level, t);
		if (land == null) return false;
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ENDER_PEARL));
		telegraph.start(this, "pearl", "Pearl Hop", pc.windup(), Telegraph.Weight.ORDINARY, 0F, style().cue(false, true), true, () -> {
			if (!isAlive() || !(level() instanceof ServerLevel sl)) return;
			restoreWeapon();
			BlockPos dest = findLanding(sl, t);
			if (dest == null) return;
			pearls++;
			pearlReadyAt = now() + pc.cooldown();
			unreachableTicks = 0;
			sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.3);
			teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
			sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.3);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_TELEPORT, SoundSource.HOSTILE, 1F, 1F);
			hurtServer(sl, damageSources().fall(), pc.fallRaw());
			bump("pearl");
		});
		return true;
	}

	/** A standable spot within 4 blocks of the target and 20 to 40 blocks from the boss, in loaded chunks, never inside a block or fluid, inside the arena. */
	@Nullable BlockPos findLanding(ServerLevel level, LivingEntity t) {
		Pearl pc = profile.pearl;
		int lr = (int) Math.ceil(pc.landRadius());
		BlockPos tp = t.blockPosition();
		for (int i = 0; i < 40; i++) {
			int dx = random.nextInt(2 * lr + 1) - lr, dz = random.nextInt(2 * lr + 1) - lr;
			if (dx * dx + dz * dz > pc.landRadius() * pc.landRadius()) continue;
			int x = tp.getX() + dx, z = tp.getZ() + dz;
			if (!level.hasChunkAt(new BlockPos(x, tp.getY(), z))) continue;
			for (int dy = 3; dy >= -5; dy--) {
				BlockPos feet = new BlockPos(x, tp.getY() + dy, z);
				if (!level.isInWorldBounds(feet)) continue;
				if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) continue;
				if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
				if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
				double d = Math.sqrt(distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
				if (d < pc.minDist() || d > pc.maxRange()) continue;
				if (home.distSqr(feet) > (double) arenaRadius * arenaRadius) continue;
				return feet;
			}
		}
		return null;
	}

	// ---- signature moves ------------------------------------------------------------------------------------------

	private boolean trySignature(ServerLevel level, LivingEntity t, double dist) {
		List<SignatureMove> usable = null;
		int total = 0;
		for (SignatureMove m : profile.signatures) {
			if (!ready("sig:" + m.id()) || phase.phase() < m.minPhase() || dist < m.minRange() || dist > m.maxRange()) continue;
			if (!m.canUse(this, t)) continue;
			if (usable == null) usable = new ArrayList<>();
			usable.add(m);
			total += m.priority();
		}
		if (usable == null || random.nextInt(8) != 0) return false;
		int roll = random.nextInt(Math.max(1, total));
		SignatureMove pick = usable.get(0);
		for (SignatureMove m : usable) {
			roll -= m.priority();
			if (roll < 0) {
				pick = m;
				break;
			}
		}
		fireSignature(pick, t);
		return true;
	}

	/** Starts a signature move now (also used by faction code that scripts a move). */
	public void fireSignature(SignatureMove m, LivingEntity t) {
		if (shieldUp()) {
			stopUsingItem();
			shieldLowered = 0;
		}
		setCooldown("sig:" + m.id(), m.cooldown() + m.windup());
		m.begin(this, t);
		pendingPose = m.pose(this);
		telegraph.start(this, m.id(), m.label(), m.windup(), m.weight(), m.raw(), m.cue(this), m.freeze(), () -> {
			if (!isAlive()) return;
			bump(m.id());
			for (DuelistEvents.MoveFired f : DuelistEvents.MOVE) f.onMove(this, m.id());
			m.fire(this, t);
		});
	}

	/** Starts any scripted wind-up (for custom moves that do not use {@link SignatureMove}). */
	public void startTelegraph(String id, String label, int ticks, Telegraph.Weight w, float raw, Telegraph.Cue cue, boolean freeze, Runnable onFire) {
		telegraph.start(this, id, label, ticks, w, raw, cue, freeze, onFire);
	}

	/** True if the boss is free to start a new action. */
	public boolean idle() {
		return !telegraph.active() && healing == null && critStage == 0 && swapLeft == 0 && drawLeft == 0 && holdLeft == 0 && stagger == 0 && lastStandTicks == 0 && !yielded;
	}

	/** Gives the boss i-frames and a push (used by {@code DodgeStep}). */
	public void startDodge(int iframes) {
		dodgeTicks = Math.max(dodgeTicks, iframes);
	}

	// ---- persistence ----------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("DuelHealUses", healUses);
		out.putInt("DuelPearls", pearls);
		out.putInt("DuelLastStands", phase.lastStandUses());
		out.putBoolean("DuelDuel", duel);
		out.putInt("DuelPhase", phase.phase());
		out.putInt("DuelHomeX", home.getX());
		out.putInt("DuelHomeY", home.getY());
		out.putInt("DuelHomeZ", home.getZ());
		out.putBoolean("DuelHomeSet", homeSet);
		out.putBoolean("DuelEquipped", equipped);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		healUses = in.getIntOr("DuelHealUses", 0);
		pearls = in.getIntOr("DuelPearls", 0);
		duel = in.getBooleanOr("DuelDuel", false);
		phase.restore(in.getIntOr("DuelPhase", 0), in.getIntOr("DuelLastStands", 0));
		homeSet = in.getBooleanOr("DuelHomeSet", false);
		if (homeSet) home = new BlockPos(in.getIntOr("DuelHomeX", 0), in.getIntOr("DuelHomeY", 0), in.getIntOr("DuelHomeZ", 0));
		equipped = in.getBooleanOr("DuelEquipped", false);
		this.entityData.set(DATA_PHASE, phase.phase());
		// a boss from a 2.0 world may carry more than the 300 cap (attributes and health are loaded by now): clamp down, never up
		HumanBossHealth.enforce(this);
	}

	/** Splash and other scripted effects may need this (a real effect of the given kind, never lethal on its own). */
	public static MobEffectInstance effect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int ticks, int amp) {
		return new MobEffectInstance(e, ticks, amp);
	}
}
