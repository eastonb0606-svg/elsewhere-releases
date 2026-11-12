package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomLod;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.war.WarApi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Reinforcement waves (ARMIES.md 3.5): a watched battle of up to 150 a side that never has more than the area cap (64) alive. Each side
 * keeps a live front of {@code min(live_front, area cap / 2)} soldiers; the rest of its heads wait as a reserve and step in as waves in the
 * kingdom's own style from {@code army.scale.wave}: sergeant (Aldermark, when the front falls below the trigger), surge (Hrafnholt, on the
 * horn every interval), relief (Azmar, a fresh block swaps in and up to half a block of wounded step back into the reserve with their heads
 * saved) and ragged (Mirewick, jittered intervals). Ticked once a second, only while a battle runs; no scans. Waves spawn at each side's rear
 * through {@link KingdomApi#spawnSquad}, so every cap still binds.
 * <p>Owns watched war-layer assaults through {@link WarApi#setBattleHandler} (kill switch {@code waves}), except Hrafnholt longship landings and
 * militia defences, which stay with the built-in hand-off. One wave battle at a time; a second assault falls back to the built-in hand-off.
 */
public final class ArmyWaves {
	private ArmyWaves() {}

	/** Longest wave battle (5 minutes), then the remaining heads decide. */
	public static int TIMEOUT_TICKS = 6000;
	/** Test hook: multiplies every wave interval (the self-test runs at 0.25). */
	public static double intervalScale = 1.0;
	/** Live front of a kingdom without {@code army.scale} (militia, test factions). */
	public static int defaultFront = 15;
	public static final int MAX_BATTLES = 1;
	public static int started, finished, wavesSpawned, withdrawnWounded, maxLiveSeen;

	public static final class Side {
		public final Identifier faction;
		final @Nullable ArmyScale scale;
		final int heads;
		public int reserve, spawned, lost;
		final List<UUID> soldiers = new ArrayList<>();
		final Set<UUID> squads = new HashSet<>();
		long nextWave;
		int waves;
		final BlockPos rear;
		final BlockPos objective;

		Side(Identifier faction, int heads, BlockPos rear, BlockPos objective) {
			this.faction = faction;
			this.scale = ArmyScale.of(faction);
			this.heads = heads;
			this.reserve = heads;
			this.rear = rear;
			this.objective = objective;
		}

		/** This side's share of the live budget, set at the start of the battle. */
		int front = 15;

		/** The kingdom's own live front (army.scale.live_front). */
		int wantedFront() {
			return scale != null ? scale.liveFront() : defaultFront;
		}

		/** Live target for this side. */
		int front() {
			return front;
		}

		String style() {
			return scale != null ? scale.wave().style() : "sergeant";
		}

		int waveSize() {
			return scale != null ? scale.wave().size() : 8;
		}

		long intervalTicks(net.minecraft.util.RandomSource r) {
			int s = scale != null ? scale.wave().intervalSeconds() : 40, j = scale != null ? scale.wave().intervalJitterSeconds() : 0;
			int sec = s + (j > 0 ? r.nextInt(2 * j + 1) - j : 0);
			return Math.max(20, Math.round(sec * 20 * intervalScale));
		}

		double trigger() {
			return scale != null ? scale.wave().triggerFront() : 0.7;
		}

		public int waves() {
			return waves;
		}
	}

	public static final class Battle {
		public final int assaultId;
		public final BlockPos pos;
		public final Side a, d;
		final long start;
		boolean playerInvolved;
		public boolean over;
		public String result = "";

		Battle(int assaultId, BlockPos pos, Side a, Side d, long start) {
			this.assaultId = assaultId;
			this.pos = pos;
			this.a = a;
			this.d = d;
			this.start = start;
		}
	}

	static final List<Battle> BATTLES = new ArrayList<>();

	public static List<Battle> battles() {
		return BATTLES;
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (BATTLES.isEmpty() || srv.getTickCount() % 20 != 7) return;
			ServerLevel lv = srv.overworld();
			for (Battle b : new ArrayList<>(BATTLES)) tick(lv, b);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
			if (BATTLES.isEmpty() || !(e instanceof KingdomSoldier s) || s.squad() == null) return;
			UUID q = s.squad().id();
			for (Battle b : BATTLES) {
				Side side = b.a.squads.contains(q) ? b.a : b.d.squads.contains(q) ? b.d : null;
				if (side == null) continue;
				side.lost++;
				if (src.getEntity() instanceof ServerPlayer) b.playerInvolved = true;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			for (Battle b : new ArrayList<>(BATTLES)) end(srv.overworld(), b, true);
			BATTLES.clear();
		});
		if (ArmiesConfig.waves && !ArmiesConfig.otherSelfTest()) {
			WarApi.setBattleHandler(ArmyWaves::handle);
			KingdomContent.LOG.info("Armies 3.0: watched war assaults use reinforcement waves (kill switch: waves)");
		}
	}

	/** The war-layer hand-off: true when this assault becomes a wave battle. */
	static boolean handle(ServerLevel lv, String villageKey, BlockPos pos, Identifier attacker, @Nullable Identifier defender, double ha, double hd, int assaultId) {
		if (defender == null || BATTLES.size() >= MAX_BATTLES) return false; // militia and a second battle stay with the built-in hand-off
		if (dev.elsewhere.kingdom.war.Longship.enabled(attacker)) return false; // Hrafnholt sailing landings stay with the longship lane
		return start(lv, pos, attacker, defender, heads(attacker, ha), heads(defender, hd), assaultId) != null;
	}

	static int heads(Identifier faction, double h) {
		ArmyScale s = ArmyScale.of(faction);
		int max = s != null ? s.battleSideMax() : 30;
		return (int) Math.max(4, Math.min(max, Math.round(h)));
	}

	/** Starts a wave battle at {@code pos}: the attacker comes from 30 blocks out, the defender holds. Null when nobody could be fielded. */
	public static @Nullable Battle start(ServerLevel lv, BlockPos pos, Identifier attacker, Identifier defender, int attackerHeads, int defenderHeads, int assaultId) {
		double ang = Math.floorMod(attacker.hashCode() * 31 + pos.hashCode(), 360) * Math.PI / 180;
		double cx = Math.cos(ang), cz = Math.sin(ang);
		BlockPos ar = ground(lv, pos.getX() + cx * 30, pos.getZ() + cz * 30, pos);
		BlockPos dr = ground(lv, pos.getX() - cx * 10, pos.getZ() - cz * 10, pos);
		long now = lv.getGameTime();
		Battle b = new Battle(assaultId, pos, new Side(attacker, attackerHeads, ar, pos), new Side(defender, defenderHeads, dr, pos), now);
		// the live budget: what the caps allow here now (area cap, and the per-player cap of every watcher), shared in proportion to the
		// kingdoms' own live fronts (Aldermark 40 v Azmar 24 under a 32 budget: 20 v 12)
		int budget = Math.min(KingdomConfig.maxRealSoldiersPerArea, dev.elsewhere.kingdom.KingdomPerf.room(lv, net.minecraft.world.phys.Vec3.atCenterOf(pos)));
		int wa = b.a.wantedFront(), wd = b.d.wantedFront();
		double share = Math.min(1.0, budget / (double) Math.max(1, wa + wd));
		b.a.front = Math.max(4, (int) Math.floor(wa * share));
		b.d.front = Math.max(4, (int) Math.floor(wd * share));
		BATTLES.add(b);
		started++;
		for (Side s : List.of(b.d, b.a)) {
			field(lv, b, s, s.front());
			s.nextWave = now + s.intervalTicks(lv.getRandom());
		}
		if (b.a.soldiers.isEmpty() || b.d.soldiers.isEmpty()) {
			end(lv, b, true);
			BATTLES.remove(b);
			if (assaultId >= 0) return null;
		}
		return b;
	}

	/** Spawns up to {@code n} of this side's reserve at its rear, in squads of up to 10. Returns the number spawned. */
	static int field(ServerLevel lv, Battle b, Side s, int n) {
		n = Math.min(n, s.reserve);
		int made = 0, k = 0;
		var r = lv.getRandom();
		while (made < n && k < 6) {
			int size = Math.min(10, n - made);
			BlockPos at = s.rear.offset((k - 1) * 6 + r.nextInt(3) - 1, 0, r.nextInt(3) - 1);
			k++;
			List<KingdomSoldier> sq = KingdomApi.spawnSquad(lv, at, s.faction, s == b.d ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE, UnitTypes.SERGEANT,
				ArmyComposer.members(r, Math.max(0, size - 1), false, s.faction));
			if (sq.isEmpty()) break; // the caps are full: wait for the next wave
			Squad q = sq.get(0).squad();
			if (q != null) {
				q.setVirtualizable(false);
				q.setObjective(s.objective);
				s.squads.add(q.id());
			}
			for (KingdomSoldier x : sq) s.soldiers.add(x.getUUID());
			made += sq.size();
		}
		s.reserve -= made;
		s.spawned += made;
		return made;
	}

	static int alive(ServerLevel lv, Side s) {
		int c = 0;
		for (var it = s.soldiers.iterator(); it.hasNext();) {
			Entity e = lv.getEntity(it.next());
			if (e == null || !e.isAlive()) it.remove();
			else c++;
		}
		return c;
	}

	static void tick(ServerLevel lv, Battle b) {
		long now = lv.getGameTime();
		int la = alive(lv, b.a), ld = alive(lv, b.d);
		maxLiveSeen = Math.max(maxLiveSeen, la + ld);
		for (Side s : List.of(b.a, b.d)) {
			int live = s == b.a ? la : ld;
			if (s.reserve <= 0 || now < s.nextWave) continue;
			String style = s.style();
			boolean due = switch (style) {
				case "surge" -> live < s.front(); // the horn blows on the interval whenever there is room
				default -> live < s.front() * s.trigger();
			};
			if (!due) continue;
			if (style.equals("relief")) withdrawWounded(lv, s, s.waveSize() / 2);
			int room = s.front() - alive(lv, s);
			int n = field(lv, b, s, Math.min(s.waveSize(), Math.max(0, room)));
			if (n > 0) {
				s.waves++;
				wavesSpawned++;
				if (KingdomLod.observerNear(lv, s.rear.getX(), s.rear.getZ(), 64)) {
					var sound = switch (style) {
						case "surge" -> net.minecraft.sounds.SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value();
						case "relief" -> net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value();
						default -> net.minecraft.sounds.SoundEvents.RAID_HORN.value();
					};
					lv.playSound(null, s.rear, sound, net.minecraft.sounds.SoundSource.HOSTILE, style.equals("ragged") ? 0.8F : 2.0F, 1.0F);
				}
			}
			s.nextWave = now + s.intervalTicks(lv.getRandom());
		}
		la = alive(lv, b.a);
		ld = alive(lv, b.d);
		maxLiveSeen = Math.max(maxLiveSeen, la + ld);
		boolean watched = KingdomLod.observerNear(lv, b.pos.getX(), b.pos.getZ(), 128);
		if (la + b.a.reserve <= 0 || ld + b.d.reserve <= 0 || now - b.start > TIMEOUT_TICKS || !watched) {
			end(lv, b, false);
			BATTLES.remove(b);
		}
	}

	/** Azmar line relief: wounded soldiers below half health leave the line, their heads go back into the reserve. */
	static void withdrawWounded(ServerLevel lv, Side s, int max) {
		int n = 0;
		for (var it = s.soldiers.iterator(); it.hasNext() && n < max;) {
			Entity e = lv.getEntity(it.next());
			if (e instanceof KingdomSoldier k && k.isAlive() && k.getHealth() < k.getMaxHealth() * 0.5F && k.getTarget() == null) {
				k.discard();
				it.remove();
				s.reserve++;
				s.spawned--;
				n++;
				withdrawnWounded++;
			}
		}
	}

	/** Ends a battle: survivors march back to their rear and leave when out of sight (dismissed squads); the war layer gets the result. */
	static void end(ServerLevel lv, Battle b, boolean discard) {
		if (b.over) return;
		b.over = true;
		finished++;
		int la = alive(lv, b.a), ld = alive(lv, b.d);
		double ra = (la + b.a.reserve) / (double) Math.max(1, b.a.heads), rd = (ld + b.d.reserve) / (double) Math.max(1, b.d.heads);
		boolean attackerWon = ld + b.d.reserve <= 0 || (la + b.a.reserve > 0 && ra > rd);
		b.result = (attackerWon ? "attacker" : "defender") + " won; attacker " + (la + b.a.reserve) + "/" + b.a.heads + " left, defender " + (ld + b.d.reserve) + "/"
			+ b.d.heads + " left";
		KingdomState ks = KingdomState.get(lv);
		for (Side s : List.of(b.a, b.d)) {
			for (UUID id : s.squads) {
				Squad q = ks.squad(id);
				if (q == null) continue;
				if (discard) {
					for (KingdomSoldier k : q.memberEntities(lv)) k.discard();
					if (q.leaderEntity(lv) instanceof KingdomSoldier l) l.discard();
					ks.disband(q, lv);
				} else {
					q.setObjective(s.rear);
					q.setVirtualizable(true);
					q.dismiss();
				}
			}
		}
		if (b.assaultId >= 0) WarApi.reportAssault(lv.getServer(), b.assaultId, attackerWon, 1 - ra, 1 - rd, b.playerInvolved);
		KingdomContent.LOG.info("Armies wave battle {} ended: {} ({} + {} waves)", b.assaultId, b.result, b.a.waves, b.d.waves);
	}

	/** Ends every running battle (the self-test and {@code /kingdom armies battle stop}). */
	public static void stopAll(MinecraftServer srv, boolean discard) {
		for (Battle b : new ArrayList<>(BATTLES)) end(srv.overworld(), b, discard);
		BATTLES.clear();
	}

	static BlockPos ground(ServerLevel lv, double x, double z, BlockPos fallback) {
		BlockPos p = BlockPos.containing(x, 64, z);
		if (!lv.isLoaded(p)) return fallback;
		BlockPos g = lv.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
		if (!lv.getFluidState(g).isEmpty() || !lv.getFluidState(g.below()).isEmpty() || Math.abs(g.getY() - fallback.getY()) > 12) return fallback;
		return g;
	}

	public static void reset() {
		started = finished = wavesSpawned = withdrawnWounded = maxLiveSeen = 0;
	}
}
