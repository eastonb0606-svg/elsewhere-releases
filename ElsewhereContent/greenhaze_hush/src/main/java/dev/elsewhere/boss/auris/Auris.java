package dev.elsewhere.boss.auris;

import dev.elsewhere.greenhaze_hush.BossUtil;
import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.world.Sink;
import dev.elsewhere.hush.HushBlocks;
import dev.elsewhere.hush.HushEntities;
import dev.elsewhere.hush.HushItems;
import dev.elsewhere.hush.HushMobs;
import dev.elsewhere.hush.HushNoise;
import dev.elsewhere.hush.HushStructures;
import dev.elsewhere.hush.Resonance;
import dev.elsewhere.stub.greenhaze_hush.ArenaSeal;
import dev.elsewhere.stub.greenhaze_hush.BossEntity;
import dev.elsewhere.stub.greenhaze_hush.SignatureGear;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import dev.elsewhere.stub.greenhaze_hush.Telegraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * Auris, the Listening Engine (BOSSES.md 15). Tier V, 720 base HP, armor 18, toughness 8. Blind: it hunts noise.
 * Phase 1 Listen: projectiles and decoys pull it to their impact spot ("distracted"); while distracted its 4 tympanum
 * membranes are exposed, and striking all 4 within 10 s deafens it (stunned, x2 damage). Sonic Boom (vanilla 1.7 s charge,
 * 9 true damage, 15-block range) and warden melee (30 raw).
 * Phase 2 Echo: every noise is repeated 2 s later at the mirrored position; Shrieker Swarms spawn on loud players.
 * Phase 3 Silence: wool pads burn away, Darkness is permanent, its ear-rings lock onto the loudest player, who must sneak
 * while the others strike (x1.5 damage while it has lost them).
 */
public class Auris extends BossEntity {
	public static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Auris.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> DEAF = SynchedEntityData.defineId(Auris.class, EntityDataSerializers.BOOLEAN);
	public static final int ANIM_IDLE = 0, ANIM_LISTEN = 1, ANIM_CHARGE = 2, ANIM_SMASH = 3, ANIM_DEAF = 4;
	public static final double BASE_HP = 720, ARMOR = 18, TOUGHNESS = 8;
	public static final float SONIC_TRUE = 9F, MELEE_RAW = 30F, SONIC_RANGE = 15F;
	public static final ResourceKey<Structure> ENGINE = ResourceKey.create(Registries.STRUCTURE, Reg.id("listening_engine"));
	/** Engines built live (dev command / tests); worldgen engines are found through the structure manager. */
	public static final List<BlockPos> LIVE_ENGINES = new ArrayList<>();
	/** fix-stability: at most this many remembered engines (LIVE_ENGINES used to grow with every dev build, forever). */
	public static final int MAX_LIVE_ENGINES = 16;

	public static void registerEngine(BlockPos throne) {
		BlockPos p = throne.immutable();
		LIVE_ENGINES.remove(p);
		LIVE_ENGINES.add(p);
		while (LIVE_ENGINES.size() > MAX_LIVE_ENGINES) LIVE_ENGINES.remove(0);
	}

	public static void clearEngines() { LIVE_ENGINES.clear(); }

	/** Boss art: phase-transition roar, started on the client by entity event 90. */
	public final net.minecraft.world.entity.AnimationState roarAnim = new net.minecraft.world.entity.AnimationState();
	private long lastHeard;
	private Vec3 focus;
	private boolean focusIsPlayer;
	private int distractedTicks, deafTicks, membraneWindow, silencedFocusTicks;
	private final Map<BlockPos, Long> membraneHits = new HashMap<>();
	private final List<BlockPos> membranes = new ArrayList<>();
	private ServerPlayer loudest;
	/** Counters for the self-test. */
	public int sonicBooms, smashes, deafenings, echoes, distractions, swarmsSpawned, membraneStrikes;

	public Auris(EntityType<? extends Auris> type, Level level) {
		super(type, level, BossEvent.BossBarColor.BLUE);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(ANIM, 0);
		b.define(DEAF, false);
	}

	public int anim() { return entityData.get(ANIM); }
	private void anim(int a) { entityData.set(ANIM, a); }
	public boolean deafened() { return entityData.get(DEAF); }

	@Override public String bossId() { return "auris"; }
	@Override public double baseHealth() { return BASE_HP; }
	@Override protected float[] phaseThresholds() { return new float[]{0.60F, 0.30F}; }

	// ---------------------------------------------------------------------------------------------- summon

	/** Resonance hook: at 100% anywhere near the Engine it is Auris, not a plain Warden, that answers. */
	public static boolean answer(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockPos engine = null;
		for (BlockPos e : LIVE_ENGINES) if (e.closerThan(p.blockPosition(), 40)) engine = e;
		if (engine == null) {
			StructureStart start = level.structureManager().getStructureWithPieceAt(p.blockPosition(), h -> h.is(ENGINE));
			if (start != null && start.isValid()) {
				var bb = start.getBoundingBox();
				engine = new BlockPos(bb.getCenter().getX(), bb.minY() + 10 - 4, bb.getCenter().getZ());
			}
		}
		if (engine == null) return false;
		for (ArenaSeal a : ArenaSeal.ACTIVE) if (a.level == level && a.center.closerThan(engine, 6)) return true; // already awake
		p.sendSystemMessage(net.minecraft.network.chat.Component.literal("The Engine heard you.").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
		summon(level, engine);
		return true;
	}

	public static Auris summon(ServerLevel level, BlockPos throneFloor) {
		ArenaSeal arena = ArenaSeal.create(level, throneFloor, 21, 6, 22);
		Auris boss = HushEntities.AURIS.create(level, EntitySpawnReason.TRIGGERED);
		boss.snapTo(throneFloor.getX() + 0.5, throneFloor.getY() + 1, throneFloor.getZ() + 3.5, 0F, 0F);
		boss.setArena(arena);
		boss.findMembranes(level);
		level.addFreshEntity(boss);
		arena.seal(StubBlocks.ARENA_SEAL);
		boss.lastHeard = level.getGameTime();
		boss.playBoss(SoundEvents.WARDEN_EMERGE, 4F, 0.6F);
		return boss;
	}

	/** Dev: build The Listening Engine around this spot and summon. Origin is the funnel's top centre. */
	public static Auris buildAndSummon(ServerLevel level, BlockPos origin) {
		HushStructures.LISTENING_ENGINE.build(Sink.live(level), origin, RandomSource.create(origin.asLong()));
		BlockPos throne = origin.below(4);
		registerEngine(throne);
		return summon(level, throne);
	}

	void findMembranes(ServerLevel level) {
		membranes.clear();
		if (arena == null) return;
		for (BlockPos p : BlockPos.betweenClosed(arena.center.offset(-14, 0, -14), arena.center.offset(14, 12, 14))) {
			if (level.getBlockState(p).is(HushBlocks.TYMPANUM_MEMBRANE)) {
				BlockPos c = p.immutable();
				boolean near = false;
				for (BlockPos m : membranes) if (m.closerThan(c, 3)) { near = true; break; }
				if (!near) membranes.add(c);
			}
		}
	}

	public List<BlockPos> membranes() {
		return membranes;
	}

	private int groupOf(BlockPos p) {
		for (int i = 0; i < membranes.size(); i++) if (membranes.get(i).closerThan(p, 3)) return i;
		return -1;
	}

	private void setExposed(ServerLevel level, boolean exposed) {
		if (arena == null) return;
		for (BlockPos m : membranes) for (BlockPos p : BlockPos.betweenClosed(m.offset(-2, -2, -2), m.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.is(HushBlocks.TYMPANUM_MEMBRANE) && s.getValue(HushBlocks.TympanumBlock.EXPOSED) != exposed)
				level.setBlock(p, s.setValue(HushBlocks.TympanumBlock.EXPOSED, exposed), 3);
		}
	}

	/** Called by the membrane block when a player strikes it. */
	public static void onMembraneStruck(ServerLevel level, BlockPos pos, Player player) {
		BlockState s = level.getBlockState(pos);
		if (!s.getValue(HushBlocks.TympanumBlock.EXPOSED)) return;
		for (Auris a : level.getEntitiesOfClass(Auris.class, new net.minecraft.world.phys.AABB(pos).inflate(32))) a.strike(level, pos, player);
	}

	public void strike(ServerLevel level, BlockPos pos, Player player) {
		int g = groupOf(pos);
		if (g < 0 || deafTicks > 0) return;
		membraneStrikes++;
		long now = level.getGameTime();
		membraneHits.put(membranes.get(g), now);
		level.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2F, 1.6F);
		level.sendParticles(ParticleTypes.SONIC_BOOM, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		hurtServer(level, damageSources().indirectMagic(player, player), (float) (getMaxHealth() * 0.02));
		membraneHits.values().removeIf(t -> now - t > 200);
		if (membraneHits.size() >= Math.max(1, membranes.size())) deafen(level);
	}

	private void deafen(ServerLevel level) {
		deafenings++;
		membraneHits.clear();
		deafTicks = 160;
		entityData.set(DEAF, true);
		anim(ANIM_DEAF);
		distractedTicks = 0;
		setExposed(level, false);
		announce("Deafened! Strike now", ChatFormatting.AQUA);
		playBoss(SoundEvents.WARDEN_DEATH, 3F, 1.4F);
		Telegraph.circle(level, position(), 3, Telegraph.CYAN, 160);
	}

	// ---------------------------------------------------------------------------------------------- fight

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		level.broadcastEntityEvent(this, (byte) 90);
		anim(ANIM_IDLE);
		if (phase == 1) announce("Echo", ChatFormatting.DARK_AQUA);
		if (phase == 2) {
			announce("Silence", ChatFormatting.DARK_GRAY);
			// the wool silence pads burn away
			if (arena != null) for (BlockPos p : BlockPos.betweenClosed(arena.center.offset(-20, -6, -20), arena.center.offset(20, 8, 20))) {
				if (level.getBlockState(p).is(BlockTags.WOOL)) {
					level.setBlock(p, Blocks.SCULK.defaultBlockState(), 3);
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.01);
				}
			}
		}
	}

	@Override
	protected void bossTick(ServerLevel level) {
		List<ServerPlayer> players = fighters();
		if (deafTicks > 0) {
			getNavigation().stop();
			if (--deafTicks == 0) { entityData.set(DEAF, false); anim(ANIM_IDLE); }
			return;
		}
		if (phase >= 2 && tickCount % 80 == 0) for (ServerPlayer p : players) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
		listen(level);
		if (phase >= 2) earRings(level, players);
		if (distractedTicks > 0 && --distractedTicks == 0) setExposed(level, false);
		if (phase >= 1 && ready("swarm")) swarms(level, players);
		if (phase >= 1 && ready("stalkers")) stalkers(level, players);
		if (ready("busy") && focus != null) act(level, players);
	}

	/** Read every noise since last tick. Decoys and impacts distract it; players' noise makes it hunt. */
	private void listen(ServerLevel level) {
		long now = level.getGameTime();
		Vec3 c = arenaCenter();
		for (HushNoise.Noise n : HushNoise.since(level, lastHeard + 1)) {
			if (n.source() == this || n.pos().distanceToSqr(c) > 40 * 40) continue;
			if (n.source() instanceof HushMobs.ShriekerSwarm || n.source() instanceof HushMobs.EchoStalker) continue;
			boolean player = n.source() instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator();
			if (phase >= 2 && loudest != null && player && n.source() != loudest && silencedFocusTicks > 0) continue;
			focus = n.pos();
			focusIsPlayer = player;
			if (!player && n.loudness() >= 2) {
				if (distractedTicks == 0) distractions++;
				distractedTicks = 200;
				setExposed(level, true);
			}
			// Echo phase: every noise repeats 2 s later, mirrored across the arena
			if (phase >= 1 && !n.decoy() && n.loudness() >= 1 && echoes < 100000) {
				Vec3 mirror = new Vec3(2 * c.x - n.pos().x, n.pos().y, 2 * c.z - n.pos().z);
				if (n.source() != null) schedule(40, () -> { echoes++; HushNoise.post(level, mirror, null, n.loudness()); });
			}
			anim(ANIM_LISTEN);
		}
		lastHeard = now;
	}

	private void act(ServerLevel level, List<ServerPlayer> players) {
		ServerPlayer near = null;
		for (ServerPlayer p : players) if (p.position().distanceToSqr(focus) < 16 && (near == null || p.distanceToSqr(this) < near.distanceToSqr(this))) near = p;
		double d = Math.sqrt(distanceToSqr(focus));
		if (near != null && distanceTo(near) < 4.0) { melee(level, near); return; }
		if (near != null && focusIsPlayer && distanceTo(near) < SONIC_RANGE && ready("sonic")) { sonicBoom(level, near); return; }
		if (!focusIsPlayer && d < 4) {
			// attack the impact spot
			smashes++;
			cooldown("busy", 30);
			anim(ANIM_SMASH);
			playBoss(SoundEvents.WARDEN_ATTACK_IMPACT, 2F, 0.7F);
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, focus.x, focus.y + 0.2, focus.z, 20, 1, 0.1, 1, 0.05);
			focus = null;
			return;
		}
		getNavigation().moveTo(focus.x, focus.y, focus.z, focusIsPlayer ? 1.2 : 1.0);
		getLookControl().setLookAt(focus);
	}

	private void melee(ServerLevel level, ServerPlayer p) {
		cooldown("busy", 40);
		anim(ANIM_SMASH);
		Telegraph.circle(level, p.position(), 1.5, Telegraph.YELLOW, 10);
		schedule(10, () -> {
			if (p.distanceTo(this) < 4.5) {
				hitScripted(level, p, MELEE_RAW);
				playBoss(SoundEvents.WARDEN_ATTACK_IMPACT, 2F, 0.9F);
			}
			anim(ANIM_IDLE);
		});
	}

	/** Vanilla Sonic Boom: 1.7 s charge (the telegraph), 9 true damage, leave its 15-block range to dodge. */
	private void sonicBoom(ServerLevel level, ServerPlayer target) {
		cooldown("sonic", 200);
		attackDone("sonic", 40);
		cooldown("busy", 50);
		getNavigation().stop();
		anim(ANIM_CHARGE);
		announce("Sonic Boom", ChatFormatting.RED);
		playBoss(SoundEvents.WARDEN_SONIC_CHARGE, 3F, 1F);
		Telegraph.line(level, position(), target.position(), 1, Telegraph.RED, 34);
		schedule(34, () -> {
			anim(ANIM_IDLE);
			if (!target.isAlive() || target.distanceTo(this) > SONIC_RANGE) return;
			sonicBooms++;
			Vec3 from = position().add(0, 2.6, 0), to = target.getEyePosition();
			Vec3 step = to.subtract(from).normalize();
			for (int i = 1; i < (int) Math.ceil(from.distanceTo(to)) + 2; i++) {
				Vec3 at = from.add(step.scale(i));
				level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
			playBoss(SoundEvents.WARDEN_SONIC_BOOM, 3F, 1F);
			target.hurtServer(level, damageSources().sonicBoom(this), SONIC_TRUE);
		});
	}

	private void earRings(ServerLevel level, List<ServerPlayer> players) {
		if (tickCount % 100 == 0) {
			loudest = null;
			for (ServerPlayer p : players) if (loudest == null || Resonance.get(p) > Resonance.get(loudest)) loudest = p;
			if (loudest != null) {
				Telegraph.ring(level, loudest.position(), 2.5, Telegraph.CYAN, 60);
				loudest.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Its ears turn to you. Sneak!").withStyle(ChatFormatting.AQUA));
			}
		}
		if (loudest != null && loudest.isAlive()) {
			if (loudest.isShiftKeyDown()) { silencedFocusTicks = 40; anim(ANIM_LISTEN); }
			else { focus = loudest.position(); focusIsPlayer = true; if (silencedFocusTicks > 0) silencedFocusTicks--; }
		}
	}

	private void swarms(ServerLevel level, List<ServerPlayer> players) {
		cooldown("swarm", 400);
		attackDone("swarm", 24);
		for (ServerPlayer p : players) {
			if (Resonance.get(p) < 40) continue;
			int have = level.getEntitiesOfClass(HushMobs.ShriekerSwarm.class, p.getBoundingBox().inflate(16)).size();
			for (int i = have; i < Math.min(6, have + 3); i++) {
				HushMobs.ShriekerSwarm s = HushEntities.SHRIEKER_SWARM.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (s == null) continue;
				s.snapTo(p.getX() + random.nextGaussian() * 3, p.getY() + 3, p.getZ() + random.nextGaussian() * 3, 0F, 0F);
				level.addFreshEntity(s);
				swarmsSpawned++;
			}
		}
	}

	private void stalkers(ServerLevel level, List<ServerPlayer> players) {
		cooldown("stalkers", 600);
		attackDone("stalkers", 24);
		int have = level.getEntitiesOfClass(HushMobs.EchoStalker.class, getBoundingBox().inflate(32)).size();
		if (have >= 2) return;
		HushMobs.EchoStalker s = HushEntities.ECHO_STALKER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (s == null) return;
		Vec3 c = arenaCenter();
		s.snapTo(c.x + random.nextGaussian() * 8, c.y + 1, c.z + random.nextGaussian() * 8, 0F, 0F);
		level.addFreshEntity(s);
	}

	// ---------------------------------------------------------------------------------------------- damage rules

	@Override
	protected float damageMultiplier(ServerLevel level, DamageSource source, float amount) {
		if (deafTicks > 0) return 2F;
		if (phase >= 2 && silencedFocusTicks > 0) return 1.5F;
		return 1F;
	}

	@Override
	protected void onReset(ServerLevel level) {
		setExposed(level, false);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel sl) {
			setExposed(sl, false);
			sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.5, getZ(), 80, 1.5, 2, 1.5, 0.05);
			Telegraph.ring(sl, position(), 12, Telegraph.CYAN, 20);
		}
		super.die(source);
	}

	@Override
	public List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		RandomSource r = player.getRandom();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("hushstep_greaves", () -> new ItemStack(HushItems.HUSHSTEP_GREAVES)),
			dev.elsewhere.boss.kit.DropRule.other("tuning_fork_of_auris", () -> new ItemStack(HushItems.TUNING_FORK_OF_AURIS)),
			dev.elsewhere.boss.kit.DropRule.other("echo_forged_blade", () -> new ItemStack(SignatureGear.ECHO_FORGED_BLADE)))));
		out.add(new ItemStack(HushItems.RESONANT_SHARD, 4 + r.nextInt(5)));
		out.add(new ItemStack(net.minecraft.world.item.Items.ECHO_SHARD, 2 + r.nextInt(3)));
		return out;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_HEARTBEAT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource s) {
		return SoundEvents.WARDEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 90) roarAnim.start(tickCount);
		else super.handleEntityEvent(id);
	}

	/** Test hook: hear a decoy at a position right now. */
	public void hearDecoy(ServerLevel level, Vec3 pos) {
		HushNoise.decoy(level, pos, null);
	}

	public int distractedTicks() {
		return distractedTicks;
	}
}
