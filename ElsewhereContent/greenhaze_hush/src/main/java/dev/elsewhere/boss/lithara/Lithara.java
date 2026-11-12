package dev.elsewhere.boss.lithara;

import dev.elsewhere.greenhaze_hush.BossUtil;
import dev.elsewhere.greenhaze_hush.GHProjectile;
import dev.elsewhere.greenhaze_hush.world.Sink;
import dev.elsewhere.hush.HushBlocks;
import dev.elsewhere.hush.HushEntities;
import dev.elsewhere.hush.HushItems;
import dev.elsewhere.hush.HushMobs;
import dev.elsewhere.hush.HushStructures;
import dev.elsewhere.hush.Resonance;
import dev.elsewhere.stub.greenhaze_hush.ArenaSeal;
import dev.elsewhere.stub.greenhaze_hush.BossEntity;
import dev.elsewhere.stub.greenhaze_hush.SignatureGear;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import dev.elsewhere.stub.greenhaze_hush.Telegraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lithara, the Geode Cantor (BOSSES.md 16). Tier V, 800 base HP, armor 18, toughness 8. Wake her by striking the 8 pillars.
 * Phase 1 Choir: she sings a 4-note sequence (pillars light in order); strike them in the same order to shatter her crystal
 * armor (otherwise hits glance for 25%). A wrong note sends a resonance blast to everyone (44 raw). Fang Psalm: lines of fangs.
 * Phase 2 Shatter: crystal rain (dust circles, 28 raw); vexes carry chimes that ring (+Resonance) if not killed.
 * Phase 3 Black Lake: the floor breaks into islands over the lake (falling in: Darkness, pulled down, 4 hearts);
 * the sequence grows to 6 notes; Ossuary Choristers join.
 */
public class Lithara extends BossEntity {
	public static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Lithara.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> ARMORED = SynchedEntityData.defineId(Lithara.class, EntityDataSerializers.BOOLEAN);
	public static final int ANIM_IDLE = 0, ANIM_SING = 1, ANIM_CONDUCT = 2, ANIM_SHATTERED = 3;
	public static final double BASE_HP = 800, ARMOR = 18, TOUGHNESS = 8;
	public static final float BLAST_RAW = 44, RAIN_RAW = 28, SHARD_RAW = 12, LAKE_DAMAGE = 8, ARMORED_FACTOR = 0.25F;

	/** Boss art: client animation clocks started by entity events 90 (phase roar), 91 psalm, 92 rain, 93 shards, 94 vexes. */
	public final net.minecraft.world.entity.AnimationState roarAnim = new net.minecraft.world.entity.AnimationState(),
		psalmAnim = new net.minecraft.world.entity.AnimationState(), rainAnim = new net.minecraft.world.entity.AnimationState(),
		shardsAnim = new net.minecraft.world.entity.AnimationState(), vexAnim = new net.minecraft.world.entity.AnimationState();
	private static final Map<BlockPos, Set<Integer>> WAKE = new HashMap<>();

	private final BlockPos[] pillars = new BlockPos[8];
	private final List<Integer> sequence = new ArrayList<>();
	private int expect, singTicks, answerTicks, exposedTicks, blastLock;
	private boolean islands;
	private int floorY;
	private final Map<Vex, Integer> chimes = new HashMap<>();
	private final Map<java.util.UUID, Long> lakeCooldown = new HashMap<>();
	/** Counters for the self-test. */
	public int songs, correctNotes, wrongNotes, blasts, shatters, psalms, rains, vexesSummoned, chimesRung, shardsFired, lakeFalls;

	public Lithara(EntityType<? extends Lithara> type, Level level) {
		super(type, level, BossEvent.BossBarColor.PURPLE);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ATTACK_DAMAGE, 0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(ANIM, 0);
		b.define(ARMORED, true);
	}

	public int anim() { return entityData.get(ANIM); }
	private void anim(int a) { entityData.set(ANIM, a); }
	public boolean armored() { return entityData.get(ARMORED); }
	public List<Integer> sequence() { return sequence; }
	public BlockPos pillar(int note) { return pillars[note]; }
	public boolean answering() { return answerTicks > 0; }

	@Override public String bossId() { return "lithara"; }
	@Override public double baseHealth() { return BASE_HP; }
	@Override protected float[] phaseThresholds() { return new float[]{0.65F, 0.30F}; }

	// ---------------------------------------------------------------------------------------------- waking

	/** Pillar struck: answers her song during the fight, or counts toward waking her (all 8 once each). */
	public static void onPillarStruck(ServerLevel level, BlockPos pos, int note, Player player) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2F, dev.elsewhere.hush.HushBlocks.ChoirPillarBlock.pitch(note));
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2F, dev.elsewhere.hush.HushBlocks.ChoirPillarBlock.pitch(note));
		List<Lithara> active = level.getEntitiesOfClass(Lithara.class, new AABB(pos).inflate(40));
		if (!active.isEmpty()) { active.get(0).answer(level, note, player); return; }
		BlockPos center = findGeodeCenter(level, pos);
		if (center == null) return;
		Set<Integer> struck = WAKE.computeIfAbsent(center, c -> new HashSet<>());
		struck.add(note);
		player.sendOverlayMessage(Component.literal("The geode hums (" + struck.size() + "/8)").withStyle(ChatFormatting.LIGHT_PURPLE));
		if (struck.size() >= 8) {
			WAKE.remove(center);
			summon(level, center);
		}
	}

	/** Centre of the 8 pillars around this one (pillar bases sit on the floor). */
	static BlockPos findGeodeCenter(ServerLevel level, BlockPos near) {
		long sx = 0, sz = 0;
		int n = 0, minY = Integer.MAX_VALUE;
		Set<Integer> seen = new HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(near.offset(-30, -8, -30), near.offset(30, 8, 30))) {
			BlockState s = level.getBlockState(p);
			if (!s.is(HushBlocks.CHOIR_PILLAR)) continue;
			int note = s.getValue(HushBlocks.ChoirPillarBlock.NOTE);
			BlockState below = level.getBlockState(p.below());
			if (below.is(HushBlocks.CHOIR_PILLAR) || !seen.add(note)) continue;
			sx += p.getX(); sz += p.getZ(); n++;
			minY = Math.min(minY, p.getY());
		}
		if (n < 8) return null;
		return new BlockPos((int) Math.round((double) sx / n), minY, (int) Math.round((double) sz / n));
	}

	public static Lithara summon(ServerLevel level, BlockPos center) {
		ArenaSeal arena = ArenaSeal.create(level, center, 20, 16, 24);
		Lithara boss = HushEntities.LITHARA.create(level, EntitySpawnReason.TRIGGERED);
		boss.snapTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, 0F, 0F);
		boss.setArena(arena);
		boss.floorY = center.getY() - 1;
		boss.findPillars(level);
		level.addFreshEntity(boss);
		arena.seal(StubBlocks.ARENA_SEAL);
		boss.playBoss(SoundEvents.AMETHYST_BLOCK_RESONATE, 4F, 0.5F);
		boss.announce("Lithara wakes", ChatFormatting.LIGHT_PURPLE);
		return boss;
	}

	public static Lithara buildAndSummon(ServerLevel level, BlockPos origin) {
		if (dev.elsewhere.greenhaze_hush.world.fit.GhFit.LOOKS) dev.elsewhere.greenhaze_hush.world.fit.GhFit.buildPlan(level, "choir_geode", origin);
		else HushStructures.CHOIR_GEODE.build(Sink.live(level), origin, RandomSource.create(origin.asLong()));
		return summon(level, origin);
	}

	void findPillars(ServerLevel level) {
		if (arena == null) return;
		for (BlockPos p : BlockPos.betweenClosed(arena.center.offset(-18, -2, -18), arena.center.offset(18, 8, 18))) {
			BlockState s = level.getBlockState(p);
			if (s.is(HushBlocks.CHOIR_PILLAR) && !level.getBlockState(p.below()).is(HushBlocks.CHOIR_PILLAR)) {
				pillars[s.getValue(HushBlocks.ChoirPillarBlock.NOTE)] = p.immutable();
			}
		}
	}

	private void light(ServerLevel level, int note, boolean lit) {
		BlockPos base = pillars[note];
		if (base == null) return;
		for (int i = 0; i < 8; i++) {
			BlockPos p = base.above(i);
			BlockState s = level.getBlockState(p);
			if (s.is(HushBlocks.CHOIR_PILLAR)) level.setBlock(p, s.setValue(HushBlocks.ChoirPillarBlock.LIT, lit), 3);
		}
	}

	// ---------------------------------------------------------------------------------------------- song

	private void sing(ServerLevel level) {
		songs++;
		sequence.clear();
		int len = phase >= 2 ? 6 : 4;
		for (int i = 0; i < len; i++) {
			int note;
			do note = random.nextInt(8); while (!sequence.isEmpty() && sequence.get(sequence.size() - 1) == note);
			sequence.add(note);
		}
		expect = 0;
		singTicks = len * 16 + 10;
		anim(ANIM_SING);
		announce("She sings " + len + " notes", ChatFormatting.LIGHT_PURPLE);
		for (int i = 0; i < len; i++) {
			int note = sequence.get(i);
			schedule(i * 16, () -> {
				light(level, note, true);
				BlockPos b = pillars[note];
				if (b != null) {
					float pitch = HushBlocks.ChoirPillarBlock.pitch(note);
					level.playSound(null, b, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 3F, pitch);
					level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2F, pitch);
					level.sendParticles(ParticleTypes.NOTE, b.getX() + 0.5, b.getY() + 8, b.getZ() + 0.5, 6, 0.4, 0.4, 0.4, note / 8.0);
					Telegraph.circle(level, Vec3.atBottomCenterOf(b), 1.5, Telegraph.CYAN, 14);
				}
			});
			schedule(i * 16 + 12, () -> light(level, note, false));
		}
	}

	/** A pillar was struck during the fight. */
	public void answer(ServerLevel level, int note, Player player) {
		if (answerTicks <= 0 || sequence.isEmpty() || blastLock > 0) return;
		if (note == sequence.get(expect)) {
			correctNotes++;
			light(level, note, true);
			schedule(8, () -> light(level, note, false));
			expect++;
			if (expect >= sequence.size()) shatter(level);
		} else {
			wrongNotes++;
			resonanceBlast(level);
		}
	}

	private void shatter(ServerLevel level) {
		shatters++;
		answerTicks = 0;
		exposedTicks = 200;
		entityData.set(ARMORED, false);
		anim(ANIM_SHATTERED);
		announce("Crystal armor shattered!", ChatFormatting.AQUA);
		playBoss(SoundEvents.AMETHYST_CLUSTER_BREAK, 4F, 0.6F);
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3, getZ(), 60, 1, 2, 1, 0.1);
	}

	private void resonanceBlast(ServerLevel level) {
		blasts++;
		answerTicks = 0;
		blastLock = 30;
		announce("Wrong note! Resonance blast", ChatFormatting.RED);
		Telegraph.ring(level, position(), 6, Telegraph.RED, 20);
		Telegraph.ring(level, position(), 12, Telegraph.RED, 20);
		Telegraph.ring(level, position(), 18, Telegraph.RED, 20);
		playBoss(SoundEvents.WARDEN_SONIC_CHARGE, 3F, 1.6F);
		schedule(20, () -> {
			playBoss(SoundEvents.AMETHYST_BLOCK_BREAK, 4F, 0.5F);
			for (ServerPlayer p : fighters()) {
				hitScripted(level, p, BLAST_RAW);
				Resonance.addRaw(p, 10);
			}
			sing(level);
		});
	}

	// ---------------------------------------------------------------------------------------------- fight

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		level.broadcastEntityEvent(this, (byte) 90);
		answerTicks = 0;
		singTicks = 0;
		exposedTicks = 0;
		entityData.set(ARMORED, true);
		if (phase == 1) announce("Shatter", ChatFormatting.LIGHT_PURPLE);
		if (phase == 2) {
			announce("Black Lake", ChatFormatting.DARK_PURPLE);
			breakFloor(level);
			for (int i = 0; i < 2; i++) {
				HushMobs.OssuaryChorister c = HushEntities.OSSUARY_CHORISTER.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (c == null) continue;
				BlockPos b = pillars[i * 4] != null ? pillars[i * 4] : blockPosition();
				c.snapTo(b.getX() + 0.5 + (b.getX() > getX() ? -2 : 2), b.getY(), b.getZ() + 0.5, 0F, 0F);
				level.addFreshEntity(c);
			}
		}
	}

	private void breakFloor(ServerLevel level) {
		if (arena == null || islands) return;
		islands = true;
		BlockPos c = arena.center;
		int y = floorY;
		for (int dx = -18; dx <= 18; dx++) for (int dz = -18; dz <= 18; dz++) {
			BlockPos p = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
			if (dx * dx + dz * dz <= 16) continue; // dais
			boolean keep = false;
			for (BlockPos pil : pillars) if (pil != null && pil.distSqr(new BlockPos(p.getX(), pil.getY(), p.getZ())) <= 7) keep = true;
			double ang = Math.atan2(dz, dx);
			double rad = Math.sqrt(dx * dx + dz * dz);
			if (Math.floorMod((int) Math.round(ang * 8 / Math.PI), 2) == 0 && rad > 7 && rad < 11) keep = true; // stepping stones
			if (!keep && !level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				if (random.nextInt(12) == 0) level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.05);
			}
		}
		playBoss(SoundEvents.GLASS_BREAK, 5F, 0.4F);
	}

	@Override
	protected void bossTick(ServerLevel level) {
		List<ServerPlayer> players = fighters();
		hover();
		if (blastLock > 0) blastLock--;
		if (phase >= 2) tickLake(level, players);
		if (phase >= 1) tickChimes(level, players);
		// song state machine
		if (exposedTicks > 0) {
			if (--exposedTicks == 0) { entityData.set(ARMORED, true); anim(ANIM_IDLE); }
		} else if (singTicks > 0) {
			if (--singTicks == 0) { answerTicks = 400; anim(ANIM_CONDUCT); announce("Answer her: strike the pillars in order", ChatFormatting.AQUA); }
		} else if (answerTicks > 0) {
			if (--answerTicks == 0) sing(level);
		} else if (blastLock == 0) {
			sing(level);
		}
		if (players.isEmpty()) return;
		if (ready("psalm")) fangPsalm(level, players);
		else if (ready("shards")) shards(level, players);
		else if (phase >= 1 && ready("rain")) crystalRain(level, players);
		else if (phase >= 1 && ready("vex")) vexes(level);
	}

	private void hover() {
		Vec3 c = arenaCenter();
		double ty = c.y + 1.5 + Math.sin(tickCount * 0.05) * 0.3;
		Vec3 want = new Vec3(c.x, ty, c.z);
		Vec3 d = want.subtract(position());
		setDeltaMovement(d.scale(0.1));
		ServerPlayer near = fighters().isEmpty() ? null : BossUtil.sortedByDistance(fighters(), this).get(0);
		if (near != null) getLookControl().setLookAt(near);
	}

	private void fangPsalm(ServerLevel level, List<ServerPlayer> players) {
		level.broadcastEntityEvent(this, (byte) 91);
		cooldown("psalm", 160);
		attackDone("psalm", 28);
		psalms++;
		ServerPlayer t = players.get(random.nextInt(players.size()));
		float yaw = BossUtil.yawTo(position(), t.position());
		double a = Math.toRadians(yaw);
		Vec3 dir = new Vec3(-Math.sin(a), 0, Math.cos(a));
		Telegraph.line(level, position(), position().add(dir.scale(16)), 0.8, Telegraph.YELLOW, 20);
		announce("Fang Psalm", ChatFormatting.YELLOW);
		for (int i = 1; i <= 16; i++) {
			Vec3 at = position().add(dir.scale(i * 1.0));
			level.addFreshEntity(new EvokerFangs(level, at.x, Math.floor(floorY + 1), at.z, (float) a, 20 + i, this));
		}
	}

	private void shards(ServerLevel level, List<ServerPlayer> players) {
		level.broadcastEntityEvent(this, (byte) 93);
		cooldown("shards", 100);
		for (ServerPlayer p : players) {
			for (int i = 0; i < 3; i++) {
				shardsFired++;
				GHProjectile s = new GHProjectile(HushEntities.CRYSTAL_SHARD, level, this, new ItemStack(HushItems.CRYSTAL_SHARD), SHARD_RAW);
				s.setPos(getX(), getY() + 4.5, getZ());
				Vec3 d = p.getEyePosition().subtract(s.position());
				s.shoot(d.x, d.y, d.z, 1.1F, 6F);
				level.addFreshEntity(s);
			}
		}
		playBoss(SoundEvents.AMETHYST_CLUSTER_PLACE, 2F, 1.4F);
	}

	private void crystalRain(ServerLevel level, List<ServerPlayer> players) {
		level.broadcastEntityEvent(this, (byte) 92);
		cooldown("rain", 120);
		attackDone("rain", 40);
		rains++;
		announce("Crystal Rain", ChatFormatting.YELLOW);
		List<Vec3> spots = new ArrayList<>();
		for (ServerPlayer p : players) {
			spots.add(p.position());
			spots.add(p.position().add(random.nextGaussian() * 4, 0, random.nextGaussian() * 4));
		}
		for (Vec3 s : spots) Telegraph.circle(level, s, 1.8, Telegraph.YELLOW, 30);
		schedule(30, () -> {
			for (Vec3 s : spots) {
				level.sendParticles(ParticleTypes.END_ROD, s.x, s.y + 6, s.z, 10, 0.5, 3, 0.5, 0.2);
				level.playSound(null, BlockPos.containing(s), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 2F, 0.8F);
				for (ServerPlayer p : fighters()) if (BossUtil.ground(p.position(), 0).distanceToSqr(BossUtil.ground(s, 0)) <= 1.8 * 1.8 + 0.5) hitScripted(level, p, RAIN_RAW);
			}
		});
	}

	private void vexes(ServerLevel level) {
		level.broadcastEntityEvent(this, (byte) 94);
		cooldown("vex", 300);
		attackDone("vex", 34);
		chimes.keySet().removeIf(v -> !v.isAlive());
		if (chimes.size() >= 4) return;
		for (int i = 0; i < 2; i++) {
			Vex v = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (v == null) continue;
			v.snapTo(getX() + random.nextGaussian() * 2, getY() + 3, getZ() + random.nextGaussian() * 2, 0F, 0F);
			v.setOwner(this);
			v.setBoundOrigin(blockPosition());
			v.setLimitedLife(20 * 40);
			level.addFreshEntity(v);
			chimes.put(v, tickCount);
			vexesSummoned++;
		}
	}

	private void tickChimes(ServerLevel level, List<ServerPlayer> players) {
		for (var e : chimes.entrySet()) {
			Vex v = e.getKey();
			if (!v.isAlive()) continue;
			int age = tickCount - e.getValue();
			if (age >= 200 && age % 100 == 0) {
				chimesRung++;
				level.playSound(null, v.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.5F, 1.8F);
				for (ServerPlayer p : players) if (p.distanceToSqr(v) < 256) Resonance.addRaw(p, 10);
			}
		}
	}

	private void tickLake(ServerLevel level, List<ServerPlayer> players) {
		long now = level.getGameTime();
		for (ServerPlayer p : players) {
			if (p.getY() < floorY - 1 && p.isInWater()) {
				Long last = lakeCooldown.get(p.getUUID());
				p.setDeltaMovement(p.getDeltaMovement().x, -0.4, p.getDeltaMovement().z);
				p.needsSync = true;
				if (last == null || now - last > 60) {
					lakeFalls++;
					lakeCooldown.put(p.getUUID(), now);
					p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
					p.hurtServer(level, damageSources().drown(), LAKE_DAMAGE);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------- damage rules

	@Override
	protected float damageMultiplier(ServerLevel level, DamageSource source, float amount) {
		return armored() ? ARMORED_FACTOR : 1F;
	}

	@Override
	protected void onReset(ServerLevel level) {
		for (int i = 0; i < 8; i++) light(level, i, false);
		islands = false;
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel sl) {
			for (int i = 0; i < 8; i++) light(sl, i, true);
			sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3, getZ(), 150, 1.5, 3, 1.5, 0.2);
			for (int i = 0; i < 8; i++) {
				int n = i;
				sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 3F, HushBlocks.ChoirPillarBlock.pitch(n));
			}
		}
		super.die(source);
	}

	@Override
	public List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		RandomSource r = player.getRandom();
		java.util.List<dev.elsewhere.boss.kit.DropRule.Unique> uniques = new ArrayList<>(List.of(
			dev.elsewhere.boss.kit.DropRule.signature("choir_vestment", () -> new ItemStack(HushItems.CHOIR_VESTMENT)),
			dev.elsewhere.boss.kit.DropRule.other("resonant_tuning", () -> new ItemStack(HushItems.RESONANT_TUNING)),
			dev.elsewhere.boss.kit.DropRule.other("echo_forged_blade", () -> new ItemStack(SignatureGear.ECHO_FORGED_BLADE))));
		// Cantor's Diadem is the arsenal module's item (looked up by id, skipped when that module is absent)
		net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(dev.elsewhere.greenhaze_hush.Reg.id("cantors_diadem"))
			.ifPresent(i -> uniques.add(dev.elsewhere.boss.kit.DropRule.other("cantors_diadem", () -> new ItemStack(i))));
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, uniques));
		out.add(new ItemStack(HushItems.CANTORS_BELL_SHARD, 1 + r.nextInt(3)));
		out.add(new ItemStack(HushItems.ECHO_CRYSTAL, 4 + r.nextInt(5)));
		return out;
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case 90 -> roarAnim.start(tickCount);
			case 91 -> psalmAnim.start(tickCount);
			case 92 -> rainAnim.start(tickCount);
			case 93 -> shardsAnim.start(tickCount);
			case 94 -> vexAnim.start(tickCount);
			default -> super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource s) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}

	public static void clearAll() { WAKE.clear(); }
}
