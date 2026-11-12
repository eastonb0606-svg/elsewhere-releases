package dev.elsewhere.boss.verdigrim;

import dev.elsewhere.stub.verdigris_ironpeak.BossGuards;
import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import dev.elsewhere.stub.verdigris_ironpeak.Telegraph;
import dev.elsewhere.verdigris_ironpeak.BossShot;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds;
import dev.elsewhere.verdigris_sea.VerdigrisEntities;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import dev.elsewhere.verdigris_sea.mob.ClockworkDrowned;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Archdeacon Verdigrim, the Drowned Organist (BOSSES.md #12, Tier II). HP 500 base (765 with Test Meter), armor 12,
 * toughness 4. A BPM note scheduler drives the fight: each note lights its pipe 1 s ahead; low notes send a floor
 * shockwave (jump it, 30 raw), high notes a 3-trident volley down that pipe's lane (12 raw each); the crozier slam
 * (1.2 s wind-up, 42 raw) answers anyone close. Phase 2 floods the nave a layer every 20 s (bells drain one, 60 s
 * cooldown each; conduits give breath) while Clockwork Drowned swim in. Phase 3: fully submerged, fast arpeggios cause
 * Nausea, and playing his last chord back on the note blocks stuns him for 8 s.
 */
public class VerdigrimBoss extends StubBoss {
	public static final int A_SLAM = 1, A_NOTE = 2, A_FLOOD = 3, A_ARPEGGIO = 4, A_CHORD = 5;
	public static final float SLAM_RAW = 42, SHOCKWAVE_RAW = 30, TRIDENT_RAW = 12;
	public static final int BEAT = 15, FLOOD_EVERY = 400, MAX_FLOOD_P2 = 8, FULL_FLOOD = 13, BELL_COOLDOWN = 1200, CHORD_EVERY = 400, STUN_TICKS = 160;

	private int beatTimer = 40, slamCooldown = 60;
	private int pendingPipe = -1, pendingTicks;
	private double waveRadius = -1;
	private final Set<UUID> waveHit = new HashSet<>();
	private int floodLevel, floodTimer, minionTimer = 200, arpTimer = 80, chordTimer = 200;
	private final Map<Long, Integer> bellCooldowns = new HashMap<>();
	private final List<Integer> chord = new ArrayList<>();
	private int chordShow = -1, chordWindow, chordProgress;

	public VerdigrimBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.GREEN);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 500).add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 4)
			.add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
			.add(Attributes.FOLLOW_RANGE, 48).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	public String bossId() {
		return "verdigrim";
	}

	@Override
	public int tier() {
		return 2;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.65F, 0.30F};
	}

	@Override
	protected @Nullable SoundEvent phaseSound() {
		return VerdigrisIronpeakSounds.VERDIGRIM_ROAR.value();
	}

	@Override
	protected int sealColor() {
		return 0x2FA58B;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected double meleeRange() {
		return 3.0;
	}

	@Override
	protected void tickMovement(ServerLevel level, @Nullable LivingEntity target) {
		// The organist keeps near his organ; he only advances on close targets.
		BlockPos post = arenaCenter.offset(VerdigrimArena.BOSS_SPAWN[0], 0, VerdigrimArena.BOSS_SPAWN[2]);
		if (target != null && target.distanceToSqr(Vec3.atCenterOf(post)) < 12 * 12) super.tickMovement(level, target);
		else {
			if (target != null) getLookControl().setLookAt(target, 30, 30);
			if (distanceToSqr(Vec3.atBottomCenterOf(post)) > 4) getNavigation().moveTo(post.getX() + 0.5, post.getY() + 1, post.getZ() + 0.5, 0.8);
		}
	}

	@Override
	protected void chooseAttack(ServerLevel level, LivingEntity target) {
		if (slamCooldown <= 0 && distanceToSqr(target) < 4.5 * 4.5) {
			startAttack(A_SLAM, Component.translatable("attack.elsewhere.verdigrim.slam"), 30);
			slamCooldown = 160;
		}
	}

	@Override
	protected boolean tickAttack(ServerLevel level, int attack, int tick, @Nullable LivingEntity target) {
		if (attack == A_SLAM) {
			if (tick % 4 == 0) Telegraph.filledCircle(level, position(), 4.5, Telegraph.RED);
			if (tick == 24) {
				level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0F, 0.6F);
				level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.2, getZ(), 60, 2.5, 0.2, 2.5, 0.1);
				for (ServerPlayer p : fighters()) {
					if (p.distanceToSqr(this) < 4.5 * 4.5) {
						hitPlayer(level, p, SLAM_RAW);
						dev.elsewhere.verdigris_ironpeak.Fx.push(p, position(), 1.0, 0.4);
					}
				}
			}
			return tick < 34;
		}
		return tick < 20;
	}

	@Override
	protected void tickAlways(ServerLevel level) {
		if (slamCooldown > 0) slamCooldown--;
		tickNotes(level);
		tickWave(level);
		if (tickCount % 40 == 0) conduitBreath(level);
		if (phase >= 1) tickFlood(level);
		if (phase >= 2) tickRequiem(level);
	}

	// ---- the organ ----------------------------------------------------------------------------------------
	private void tickNotes(ServerLevel level) {
		if (pendingPipe >= 0) {
			pendingTicks--;
			BlockPos top = VerdigrimArena.pipeTop(arenaCenter, pendingPipe);
			if (pendingTicks % 4 == 0) level.sendParticles(ParticleTypes.NOTE, top.getX() + 0.5, top.getY() + 1.2, top.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 1);
			if (pendingPipe >= 5 && pendingTicks % 5 == 0) {
				Vec3 a = new Vec3(top.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() - 11);
				Telegraph.line(level, a, a.add(0, 0, 25), 1.0, Telegraph.YELLOW);
			}
			if (pendingTicks <= 0) playNote(level, pendingPipe);
			return;
		}
		if (chordShow >= 0 || attack == A_SLAM) return;
		int interval = phase >= 2 ? BEAT : BEAT * 2;
		if (--beatTimer > 0) return;
		beatTimer = interval;
		int pipe = random.nextInt(VerdigrimArena.PIPES);
		light(level, pipe, true);
		pendingPipe = pipe;
		pendingTicks = 20; // the pipe glows 1 s before the note
	}

	private void light(ServerLevel level, int pipe, boolean on) {
		BlockPos top = VerdigrimArena.pipeTop(arenaCenter, pipe);
		BlockState s = level.getBlockState(top);
		if (s.hasProperty(CopperBulbBlock.LIT)) level.setBlock(top, s.setValue(CopperBulbBlock.LIT, on), 3);
	}

	public static float pitch(int pipe) {
		return (float) Math.pow(2, (pipe * 3 - 12) / 12.0);
	}

	private void playNote(ServerLevel level, int pipe) {
		pendingPipe = -1;
		light(level, pipe, false);
		BlockPos top = VerdigrimArena.pipeTop(arenaCenter, pipe);
		level.playSound(null, top, VerdigrisIronpeakSounds.ORGAN_NOTE.value(), SoundSource.HOSTILE, 3.0F, pitch(pipe));
		if (attack == 0) startAttack(A_NOTE, null, attackCooldown);
		if (pipe <= 2) {
			waveRadius = 1;
			waveHit.clear();
		} else if (pipe >= 5) {
			volley(level, pipe);
		} else {
			ServerPlayer p = randomFighter();
			if (p != null) shoot(level, Vec3.atCenterOf(top), p.position().add(0, 1, 0));
		}
	}

	private void volley(ServerLevel level, int pipe) {
		BlockPos top = VerdigrimArena.pipeTop(arenaCenter, pipe);
		Vec3 from = new Vec3(top.getX() + 0.5, arenaCenter.getY() + 3, arenaCenter.getZ() - 11);
		for (int i = 0; i < 3; i++) {
			Vec3 to = new Vec3(from.x + (i - 1) * 0.8, arenaCenter.getY() + 1.4, arenaCenter.getZ() + VerdigrimArena.RADIUS);
			shoot(level, from.add(0, i * 0.4, 0), to);
		}
	}

	private void shoot(ServerLevel level, Vec3 from, Vec3 to) {
		BossShot t = BossShot.create(VerdigrimEntities.HYMN_TRIDENT, level, this, from.x, from.y, from.z);
		t.damage = TRIDENT_RAW;
		Vec3 d = to.subtract(from);
		t.shoot(d.x, d.y, d.z, 1.4F, 0.5F);
		level.addFreshEntity(t);
	}

	private void tickWave(ServerLevel level) {
		if (waveRadius < 0) return;
		boolean waveStart = waveRadius <= 1.0;
		waveRadius += 0.6;
		Vec3 c = new Vec3(getX(), arenaCenter.getY() + 1, getZ());
		Telegraph.wave(level, this, c, waveRadius, 0.6, VerdigrimArena.RADIUS, 0.8, Telegraph.YELLOW, waveStart);
		for (ServerPlayer p : fighters()) {
			double d = Math.sqrt(p.distanceToSqr(c.x, p.getY(), c.z));
			if (Math.abs(d - waveRadius) < 0.8 && p.onGround() && p.getY() < arenaCenter.getY() + 2.5 && waveHit.add(p.getUUID())) {
				hitPlayer(level, p, SHOCKWAVE_RAW);
			}
		}
		if (waveRadius > VerdigrimArena.RADIUS) waveRadius = -1;
	}

	private void conduitBreath(ServerLevel level) {
		for (ServerPlayer p : fighters()) {
			for (int[] c : VerdigrimArena.CONDUITS) {
				if (p.distanceToSqr(Vec3.atCenterOf(arenaCenter.offset(c[0], c[1], c[2]))) < 8 * 8) {
					p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100, 0, true, true, true));
					break;
				}
			}
		}
	}

	// ---- flood ------------------------------------------------------------------------------------------------
	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		if (phase == 1) {
			startAttack(A_FLOOD, Component.translatable("attack.elsewhere.verdigrim.flood"), 40);
			floodTimer = 0;
			setFlood(level, 1);
		} else if (phase == 2) {
			startAttack(A_FLOOD, Component.translatable("attack.elsewhere.verdigrim.requiem"), 40);
			setFlood(level, FULL_FLOOD);
			chordTimer = 120;
		}
	}

	private void tickFlood(ServerLevel level) {
		int max = phase >= 2 ? FULL_FLOOD : MAX_FLOOD_P2;
		if (++floodTimer >= FLOOD_EVERY && floodLevel < max) {
			floodTimer = 0;
			setFlood(level, floodLevel + 1);
		}
		bellCooldowns.replaceAll((k, v) -> v - 1);
		bellCooldowns.values().removeIf(v -> v <= 0);
		if (--minionTimer <= 0) {
			minionTimer = 600;
			int cap = 3 + 2 * Math.max(0, participants.size() - 1);
			for (int i = 0; i < 2 && liveMinions(level) < cap; i++) {
				ClockworkDrowned d = VerdigrisEntities.CLOCKWORK_DROWNED.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (d == null) continue;
				double a = random.nextDouble() * Math.PI * 2;
				d.snapTo(arenaCenter.getX() + 0.5 + Math.cos(a) * 11, arenaCenter.getY() + 1 + Math.max(0, floodLevel - 1), arenaCenter.getZ() + 0.5 + Math.sin(a) * 11, 0, 0);
				d.finalizeSpawn(level, level.getCurrentDifficultyAt(d.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
				d.setPersistenceRequired();
				level.addFreshEntity(d);
				trackMinion(d);
			}
		}
	}

	/** Fills the nave with water up to layer n (y 1..n) inside the walls, and clears water above it. */
	public void setFlood(ServerLevel level, int n) {
		n = Math.max(0, Math.min(FULL_FLOOD, n));
		int old = floodLevel;
		floodLevel = n;
		int r = VerdigrimArena.RADIUS - 1;
		for (int y = Math.min(old, n) + 1; y <= Math.max(old, n); y++) {
			boolean fill = y <= n;
			for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
				if (x * x + z * z > (r - 0.3) * (r - 0.3)) continue;
				BlockPos p = arenaCenter.offset(x, y, z);
				BlockState s = level.getBlockState(p);
				if (fill && s.isAir()) level.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
				else if (!fill && s.is(Blocks.WATER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		if (n > old) level.playSound(null, arenaCenter, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 3.0F, 0.5F);
	}

	public int floodLevel() {
		return floodLevel;
	}

	/** A bell rung during the fight drains one layer (each bell has a 60 s cooldown). Returns true if it drained. */
	public boolean ringBell(ServerLevel level, BlockPos bell, ServerPlayer player) {
		if (phase < 1 || floodLevel <= 0) return false;
		long key = bell.asLong();
		if (bellCooldowns.containsKey(key)) {
			player.sendOverlayMessage(Component.translatable("message.elsewhere.verdigrim.bell_cooldown", bellCooldowns.get(key) / 20));
			return false;
		}
		bellCooldowns.put(key, BELL_COOLDOWN);
		setFlood(level, floodLevel - 1);
		floodTimer = 0;
		level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, bell.getX() + 0.5, bell.getY() + 1, bell.getZ() + 0.5, 30, 0.5, 1, 0.5, 0.2);
		return true;
	}

	// ---- requiem ------------------------------------------------------------------------------------------------
	private void tickRequiem(ServerLevel level) {
		if (--arpTimer <= 0) {
			arpTimer = 60;
			startAttack(A_ARPEGGIO, null, attackCooldown);
			for (int i = 0; i < 4; i++) {
				int pipe = random.nextInt(VerdigrimArena.PIPES);
				level.playSound(null, VerdigrimArena.pipeTop(arenaCenter, pipe), VerdigrisIronpeakSounds.ORGAN_NOTE.value(), SoundSource.HOSTILE, 2.0F, pitch(pipe));
			}
			for (ServerPlayer p : fighters()) if (p.distanceToSqr(this) < 16 * 16) p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
		}
		if (chordShow >= 0) {
			// Show the chord: each pipe glows for 10 ticks, in order.
			int step = chordShow / 10, sub = chordShow % 10;
			if (sub == 0 && step < chord.size()) {
				light(level, chord.get(step), true);
				BlockPos top = VerdigrimArena.pipeTop(arenaCenter, chord.get(step));
				level.playSound(null, top, VerdigrisIronpeakSounds.ORGAN_NOTE.value(), SoundSource.HOSTILE, 3.0F, pitch(chord.get(step)));
				Telegraph.circle(level, Vec3.atBottomCenterOf(VerdigrimArena.noteBlock(arenaCenter, chord.get(step))), 0.8, Telegraph.CYAN);
			}
			if (sub == 9 && step < chord.size()) light(level, chord.get(step), false);
			chordShow++;
			if (step >= chord.size()) {
				chordShow = -1;
				chordWindow = 200;
				chordProgress = 0;
			}
		} else if (chordWindow > 0) {
			chordWindow--;
		} else if (--chordTimer <= 0) {
			chordTimer = CHORD_EVERY;
			chord.clear();
			for (int i = 0; i < 4; i++) chord.add(random.nextInt(VerdigrimArena.PIPES));
			chordShow = 0;
			startAttack(A_CHORD, Component.translatable("attack.elsewhere.verdigrim.chord"), attackCooldown);
		}
	}

	/** A player played note block i. During the chord window, the right sequence stuns him for 8 s. */
	public void notePlayed(ServerLevel level, int pipe, ServerPlayer player) {
		level.playSound(null, VerdigrimArena.noteBlock(arenaCenter, pipe), VerdigrisIronpeakSounds.ORGAN_NOTE.value(), SoundSource.PLAYERS, 2.0F, pitch(pipe));
		if (chordWindow <= 0 || chord.isEmpty()) return;
		if (chord.get(chordProgress) == pipe) {
			chordProgress++;
			if (chordProgress >= chord.size()) {
				chordWindow = 0;
				chord.clear();
				stun(STUN_TICKS);
				Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.verdigrim.stunned"));
			}
		} else {
			chordProgress = 0;
			hitPlayer(level, player, 6);
		}
	}

	public List<Integer> chord() {
		return chord;
	}

	public int chordWindow() {
		return chordWindow;
	}

	/** For tests: open the replay window for a given chord immediately. */
	public void forceChord(List<Integer> notes) {
		chord.clear();
		chord.addAll(notes);
		chordShow = -1;
		chordWindow = 200;
		chordProgress = 0;
	}

	public int pipeForNoteBlock(BlockPos pos) {
		for (int i = 0; i < VerdigrimArena.PIPES; i++) if (VerdigrimArena.noteBlock(arenaCenter, i).equals(pos)) return i;
		return -1;
	}

	@Override
	protected List<ItemStack> personalLoot(ServerPlayer player, boolean firstKill, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>(dev.elsewhere.verdigris_ironpeak.ArsenalLoot.roll(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("verdigris_mitre", () -> new ItemStack(VerdigrisItems.VERDIGRIS_MITRE)),
			dev.elsewhere.boss.kit.DropRule.other("hymnal_horn", () -> new ItemStack(VerdigrisItems.HYMNAL_HORN)))));
		loot.add(new ItemStack(VerdigrisItems.TIDE_KEY));
				loot.add(new ItemStack(VerdigrisItems.VERDIGRIS_GEAR, 4 + random.nextInt(5)));
		return loot;
	}

	@Override
	protected void onReset(ServerLevel level) {
		for (int i = 0; i < VerdigrimArena.PIPES; i++) light(level, i, false);
	}

	@Override
	protected void onDefeated(ServerLevel level) {
		level.playSound(null, blockPosition(), VerdigrisIronpeakSounds.ORGAN_NOTE.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
		onReset(level);
	}
}
