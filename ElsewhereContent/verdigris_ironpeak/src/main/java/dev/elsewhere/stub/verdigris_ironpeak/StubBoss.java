package dev.elsewhere.stub.verdigris_ironpeak;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * STUB of the core's shared boss framework, enough to run full fights: boss bar, phase machine with 2 s invulnerable
 * transitions, arena seal (particle ring) and snapshot/restore, reset when everyone leaves or dies, multiplayer HP
 * scaling, overpower guard, per-player loot with first-kill signature guarantee, attack scheduler with synced attack id
 * for client animations, stun, minions. Boss-specific logic lives in each boss's own package.
 */
public abstract class StubBoss extends Monster {
	public static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(StubBoss.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(StubBoss.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> DATA_STUNNED = SynchedEntityData.defineId(StubBoss.class, EntityDataSerializers.BOOLEAN);
	public static final int TRANSITION_TICKS = 40, SEAL_TIMEOUT = 600, LEAVE_TIMEOUT = 600, SCALE_WINDOW = 600, SURVIVE_FOR_LOOT = 1200;
	private static final Identifier PARTY_HP = Identifier.fromNamespaceAndPath("elsewhere", "boss_party_scaling");

	/** One animation state per attack id (index 0 unused); the client starts them when DATA_ATTACK changes. */
	public final AnimationState[] attackAnims = new AnimationState[16];
	public final AnimationState phaseAnim = new AnimationState();

	protected final ServerBossEvent bar;
	protected BlockPos arenaCenter = BlockPos.ZERO;
	protected int arenaRadius = 16, arenaHeight = 20;
	protected @Nullable ArenaSnapshot snapshot;
	protected int phase;
	protected int transition;
	protected int attack, attackTick, attackCooldown = 60;
	protected int stun;
	protected int fightTicks;
	protected int emptyTicks;
	protected boolean defeated;
	protected final Map<UUID, Float> damageBy = new HashMap<>();
	protected final Map<UUID, Integer> insideTicks = new HashMap<>();
	protected final Map<UUID, Integer> outsideTicks = new HashMap<>();
	protected final Set<UUID> participants = new HashSet<>();
	protected final List<UUID> minions = new ArrayList<>();
	/** Players seen dealing damage (also covers players not in the player list, such as test FakePlayers). */
	protected final Map<UUID, ServerPlayer> seen = new HashMap<>();

	protected StubBoss(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor color) {
		super(type, level);
		for (int i = 0; i < attackAnims.length; i++) attackAnims[i] = new AnimationState();
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(type.getDescriptionId()), color, BossEvent.BossBarOverlay.NOTCHED_10);
		this.bar.setDarkenScreen(false);
		this.xpReward = 0; // XP comes from BossRewards.awardXp (always dropped, never doubled)
		this.setPersistenceRequired();
	}

	// ---- identity ------------------------------------------------------------------------------------
	public abstract String bossId();

	public abstract int tier();

	/** Health fractions where phases 2, 3, ... begin, highest first (e.g. 0.65, 0.30). */
	protected abstract float[] phaseThresholds();

	/** This boss's own drops for one player (the signature tier item is added by the framework). */
	protected abstract List<ItemStack> personalLoot(ServerPlayer player, boolean firstKill, RandomSource random);

	protected @Nullable SoundEvent phaseSound() {
		return null;
	}

	// ---- arena ---------------------------------------------------------------------------------------
	public void setArena(BlockPos center, int radius, int height) {
		this.arenaCenter = center.immutable();
		this.arenaRadius = radius;
		this.arenaHeight = height;
	}

	public BlockPos arenaCenter() {
		return arenaCenter;
	}

	public int arenaRadius() {
		return arenaRadius;
	}

	public BoundingBox arenaBox() {
		return new BoundingBox(arenaCenter.getX() - arenaRadius - 1, arenaCenter.getY() - 4, arenaCenter.getZ() - arenaRadius - 1,
			arenaCenter.getX() + arenaRadius + 1, arenaCenter.getY() + arenaHeight, arenaCenter.getZ() + arenaRadius + 1);
	}

	/** Call once after the arena is built and the boss is placed: snapshots the arena and opens the fight. */
	public void beginFight(ServerLevel level) {
		this.snapshot = ArenaSnapshot.take(level, arenaBox());
		this.fightTicks = 0;
		// fix-arena-restart / bal-boss-retry: the fight is on disk; a wipe or restart queues the summon item (none here: focus blocks) for a retry.
		net.minecraft.world.entity.player.Player near = level.getNearestPlayer(getX(), getY(), getZ(), 40.0, false);
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightStart(getUUID(), bossId(), near == null ? null : near.getUUID(),
			summonItem(), level.dimension().identifier().toString(), blockPosition().getX(), blockPosition().getY(), blockPosition().getZ());
	}

	/** The item the summoner spent (refunded after a wipe); the Verdigris/Ironpeak summons use focus blocks, so none. */
	public ItemStack summonItem() {
		return ItemStack.EMPTY;
	}

	public @Nullable ArenaSnapshot snapshot() {
		return snapshot;
	}

	public boolean isInside(Entity e) {
		double dx = e.getX() - (arenaCenter.getX() + 0.5), dz = e.getZ() - (arenaCenter.getZ() + 0.5);
		return dx * dx + dz * dz <= (arenaRadius + 1.5) * (arenaRadius + 1.5) && e.getY() > arenaCenter.getY() - 6 && e.getY() < arenaCenter.getY() + arenaHeight + 4;
	}

	public List<ServerPlayer> fighters() {
		List<ServerPlayer> out = new ArrayList<>();
		if (!(level() instanceof ServerLevel level)) return out;
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && isInside(p)) out.add(p);
		}
		return out;
	}

	public Collection<ServerPlayer> barPlayers() {
		return bar.getPlayers();
	}

	// ---- synced state --------------------------------------------------------------------------------
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACK, 0);
		builder.define(DATA_PHASE, 0);
		builder.define(DATA_STUNNED, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (accessor == DATA_ATTACK && level().isClientSide()) {
			int id = entityData.get(DATA_ATTACK);
			for (int i = 1; i < attackAnims.length; i++) if (i != id) attackAnims[i].stop();
			if (id > 0 && id < attackAnims.length) attackAnims[id].start(tickCount);
		}
		if (accessor == DATA_PHASE && level().isClientSide()) phaseAnim.start(tickCount);
	}

	public int currentAttack() {
		return entityData.get(DATA_ATTACK);
	}

	public int currentPhase() {
		return entityData.get(DATA_PHASE);
	}

	public boolean isStunned() {
		return entityData.get(DATA_STUNNED);
	}

	// ---- persistence -----------------------------------------------------------------------------------
	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putLong("ArenaCenter", arenaCenter.asLong());
		out.putInt("ArenaRadius", arenaRadius);
		out.putInt("ArenaHeight", arenaHeight);
		out.putInt("Phase", phase);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		arenaCenter = BlockPos.of(in.getLongOr("ArenaCenter", blockPosition().asLong()));
		arenaRadius = in.getIntOr("ArenaRadius", 16);
		arenaHeight = in.getIntOr("ArenaHeight", 20);
		phase = in.getIntOr("Phase", 0);
		entityData.set(DATA_PHASE, phase);
		if (hasCustomName()) bar.setName(getDisplayName());
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bar.removePlayer(player);
	}

	// ---- damage -----------------------------------------------------------------------------------------
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (transition > 0 || defeated) return false;
		if (source.getEntity() instanceof Player p && !isInside(p) && !p.isCreative()) return false; // no cheese from outside the seal
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
		dmg = BossGuards.overpower(dmg);
		dmg = modifyIncoming(source, dmg);
		float before = getHealth();
		super.actuallyHurt(level, source, dmg);
		float dealt = before - getHealth();
		if (dealt > 0 && source.getEntity() instanceof ServerPlayer p) {
			damageBy.merge(p.getUUID(), dealt, Float::sum);
			participants.add(p.getUUID());
			seen.put(p.getUUID(), p);
		}
		checkPhase(level);
	}

	/** Boss-specific reductions (plates, shields), applied after the overpower guard and before armor. */
	protected float modifyIncoming(DamageSource source, float dmg) {
		return dmg;
	}

	/** Deals a scripted hit (raw value on Normal) to a player; the lethal-hit guard caps it after armor. */
	public boolean hitPlayer(ServerLevel level, LivingEntity target, float raw) {
		return target.hurtServer(level, damageSources().mobAttack(this), BossGuards.scaled(level, raw));
	}

	private void checkPhase(ServerLevel level) {
		float[] t = phaseThresholds();
		float frac = phaseFraction();
		int want = 0;
		for (float th : t) if (frac <= th) want++;
		if (want > phase && isAlive()) {
			phase = want;
			entityData.set(DATA_PHASE, phase);
			transition = TRANSITION_TICKS;
			endAttack();
			SoundEvent s = phaseSound();
			if (s != null) level.playSound(null, blockPosition(), s, SoundSource.HOSTILE, 4.0F, 0.8F);
			onPhaseStart(level, phase);
		}
	}

	protected void onPhaseStart(ServerLevel level, int phase) {}

	/** Health fraction used for phase thresholds (rider + mount bosses combine both bodies). */
	protected float phaseFraction() {
		return getHealth() / getMaxHealth();
	}

	/** Re-evaluates the phase (e.g. after a mount took damage). */
	public void recheckPhase(ServerLevel level) {
		checkPhase(level);
	}

	// ---- ticking ------------------------------------------------------------------------------------
	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (defeated) return;
		fightTicks++;
		bar.setProgress(Math.max(0, Math.min(1, phaseFraction())));
		tickSeal(level);
		if (transition > 0) {
			transition--;
			getNavigation().stop();
			if (transition % 10 == 0) level.sendParticles(new DustParticleOptions(0xFFFFFF, 2.0F), getX(), getY() + getBbHeight() / 2, getZ(), 20, 1, getBbHeight() / 3, 1, 0);
			return;
		}
		if (stun > 0) {
			stun--;
			entityData.set(DATA_STUNNED, stun > 0);
			getNavigation().stop();
			return;
		}
		// Leash: never leave the arena.
		if (!isInside(this)) {
			teleportTo(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5);
		}
		LivingEntity target = pickTarget();
		setTarget(target);
		if (attack != 0) {
			attackTick++;
			if (!tickAttack(level, attack, attackTick, target)) endAttack();
		} else {
			if (attackCooldown > 0) attackCooldown--;
			tickMovement(level, target);
			if (attackCooldown <= 0 && target != null) chooseAttack(level, target);
		}
		tickAlways(level);
	}

	/** Runs every tick outside transitions and stuns (hazard timers, minion waves). */
	protected void tickAlways(ServerLevel level) {}

	protected void tickMovement(ServerLevel level, @Nullable LivingEntity target) {
		if (target == null) return;
		getLookControl().setLookAt(target, 30, 30);
		if (distanceToSqr(target) > meleeRange() * meleeRange()) getNavigation().moveTo(target, 1.0);
		else getNavigation().stop();
	}

	protected double meleeRange() {
		return 3.5;
	}

	protected @Nullable LivingEntity pickTarget() {
		List<ServerPlayer> f = fighters();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : f) {
			double d = distanceToSqr(p);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	public @Nullable ServerPlayer randomFighter() {
		List<ServerPlayer> f = fighters();
		return f.isEmpty() ? null : f.get(getRandom().nextInt(f.size()));
	}

	protected abstract void chooseAttack(ServerLevel level, LivingEntity target);

	/** Returns false when the attack is over. */
	protected abstract boolean tickAttack(ServerLevel level, int attack, int tick, @Nullable LivingEntity target);

	public void startAttack(int id, Component name, int cooldownAfter) {
		attack = id;
		attackTick = 0;
		attackCooldown = cooldownAfter;
		entityData.set(DATA_ATTACK, id);
		getNavigation().stop();
		if (name != null) Telegraph.announce(barPlayers(), name);
	}

	public void endAttack() {
		attack = 0;
		attackTick = 0;
		entityData.set(DATA_ATTACK, 0);
	}

	public void stun(int ticks) {
		stun = Math.max(stun, ticks);
		entityData.set(DATA_STUNNED, true);
		endAttack();
	}

	public int phase() {
		return phase;
	}

	public boolean inTransition() {
		return transition > 0;
	}

	/** For tests and dev commands: finish a phase transition at once. */
	public void skipTransition() {
		transition = 0;
	}

	public void trackMinion(Entity e) {
		minions.add(e.getUUID());
	}

	public int liveMinions(ServerLevel level) {
		int n = 0;
		for (UUID id : minions) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) n++;
		}
		return n;
	}

	private void tickSeal(ServerLevel level) {
		List<ServerPlayer> inside = fighters();
		// Bar: everyone nearby sees it.
		for (ServerPlayer p : level.players()) {
			boolean near = p.distanceToSqr(Vec3.atCenterOf(arenaCenter)) < (arenaRadius + 24) * (arenaRadius + 24);
			if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
			else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
		}
		for (ServerPlayer p : inside) {
			UUID id = p.getUUID();
			insideTicks.merge(id, 1, Integer::sum);
			outsideTicks.remove(id);
			if (participants.add(id) && fightTicks <= SCALE_WINDOW) rescaleForParty();
		}
		for (UUID id : new ArrayList<>(participants)) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null && seen.containsKey(id)) continue;
			if (p == null || !inside.contains(p)) {
				int out = outsideTicks.merge(id, 1, Integer::sum);
				if (out > LEAVE_TIMEOUT) participants.remove(id);
			}
		}
		emptyTicks = inside.isEmpty() ? emptyTicks + 1 : 0;
		if (emptyTicks > SEAL_TIMEOUT) {
			resetFight(level);
			return;
		}
		// The seal: a translucent ring of light at the arena edge.
		if (tickCount % 5 == 0) {
			double r = arenaRadius + 1;
			DustParticleOptions dust = new DustParticleOptions(sealColor(), 1.6F);
			// w4-vfx: 96 columns (about 1 block apart at r 16), 3 dust each every 5 ticks, so the ring reads as one wall, not pillars
			for (int i = 0; i < 96; i++) {
				double a = Math.PI * 2 * (i + (tickCount / 5 % 2) * 0.5) / 96;
				level.sendParticles(dust, arenaCenter.getX() + 0.5 + Math.cos(a) * r,
					arenaCenter.getY() + 3.0, arenaCenter.getZ() + 0.5 + Math.sin(a) * r, 3, 0, 1.6, 0, 0);
			}
		}
	}

	protected int sealColor() {
		return 0x80E0FF;
	}

	private void rescaleForParty() {
		int n = Math.max(1, participants.size());
		var attr = getAttribute(Attributes.MAX_HEALTH);
		if (attr == null) return;
		float frac = getHealth() / getMaxHealth();
		attr.removeModifier(PARTY_HP);
		if (n > 1) attr.addPermanentModifier(new AttributeModifier(PARTY_HP, 0.5 * (n - 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		setHealth(getMaxHealth() * frac);
	}

	/** Everyone left or died: restore the arena, remove minions and the boss. The summon can be repeated. */
	public void resetFight(ServerLevel level) {
		if (level.getServer() != null) dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWiped(getUUID(), "wipe");
		clearMinions(level);
		if (snapshot != null) snapshot.restore(level);
		bar.removeAllPlayers();
		onReset(level);
		discard();
	}

	protected void onReset(ServerLevel level) {}

	protected void clearMinions(ServerLevel level) {
		for (UUID id : minions) {
			Entity e = level.getEntity(id);
			if (e != null) e.discard();
		}
		minions.clear();
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level && !defeated) {
			defeated = true;
			endAttack();
			clearMinions(level);
			deliverLoot(level);
			onDefeated(level);
			if (snapshot != null) snapshot.restore(level);
			bar.setProgress(0);
			bar.removeAllPlayers();
		}
		super.die(source);
	}

	protected void onDefeated(ServerLevel level) {}

	/** Per-player rolls (5%+ of the damage or 60 s in the arena); everything drops on the ground like any mob, and XP always drops. */
	protected void deliverLoot(ServerLevel level) {
		float total = 0;
		for (float f : damageBy.values()) total += f;
		List<ItemStack> all = new ArrayList<>();
		for (UUID id : participants) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) p = seen.get(id);
			if (p == null) continue;
			boolean eligible = damageBy.getOrDefault(id, 0F) >= 0.05F * Math.max(1, total) || insideTicks.getOrDefault(id, 0) >= SURVIVE_FOR_LOOT;
			if (!eligible) continue;
			boolean first = StubPlayerData.recordKill(p, bossId());
			List<ItemStack> loot = new ArrayList<>(personalLoot(p, first, getRandom()));
			Item signature = SignatureGear.forTier(tier());
			if (signature != null && (first || getRandom().nextFloat() < dev.elsewhere.verdigris_ironpeak.GearStats.SIGNATURE_REPEAT_CHANCE)) loot.add(new ItemStack(signature));
			all.addAll(loot);
		}
		all.removeIf(ItemStack::isEmpty);
		lastLoot = all;
		dev.elsewhere.boss.kit.BossRewards.dropLoot(level, position(), all);
		dev.elsewhere.boss.kit.BossRewards.awardXp(level, position(), 0);
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWon(getUUID());
	}

	/** The last kill's drops (tests). */
	public List<ItemStack> lastLoot = new ArrayList<>();

	/** Personal drops: only that player can pick them up. */
	public static void give(ServerLevel level, ServerPlayer p, List<ItemStack> loot) {
		for (ItemStack s : loot) {
			if (s.isEmpty()) continue;
			ItemEntity item = new ItemEntity(level, p.getX(), p.getY() + 0.5, p.getZ(), s);
			item.setTarget(p.getUUID());
			item.setPickUpDelay(10);
			item.setUnlimitedLifetime();
			level.addFreshEntity(item);
		}
	}

	/** For tests: how many ticks the arena has been empty. */
	public void setEmptyTicks(int t) {
		emptyTicks = t;
	}

	public Set<UUID> participants() {
		return participants;
	}

	public static float clampHealthFraction(LivingEntity e) {
		return Mth.clamp(e.getHealth() / e.getMaxHealth(), 0, 1);
	}
}
