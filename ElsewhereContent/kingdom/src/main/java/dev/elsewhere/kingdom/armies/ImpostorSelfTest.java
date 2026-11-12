package dev.elsewhere.kingdom.armies;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.armies.impostor.Figure;
import dev.elsewhere.kingdom.armies.impostor.RankEntity;
import dev.elsewhere.kingdom.armies.impostor.RankLayout;
import dev.elsewhere.kingdom.armies.impostor.Ranks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Self-test of the 3.0 rank impostors (ARMIES.md 3.5 / 3.6, s-impostors), appended to the armies self-test ({@code -Delsewhere.armies.selftest=true})
 * once that test has built its own steps, so it runs last on a clean arena. Checks: the kill switch and the entity; every kingdom's rank shape;
 * a 150 v 150 wave battle forms reserve ranks inside the budgets, on the ground, behind the fronts and never more figures than the reserve; a
 * wave breaks out one soldier per tick from the ranks with exact head accounting; a player's hit breaks a figure out and the soldier takes the
 * blow; an NPC's stray hit wounds and then kills a figure; R3 folds a far battle squad into a rank and unfolds it when a player comes near;
 * at the end the ranks loosen (the loser routs); a rank marching off the loaded area goes abstract; the measured cost of a rank tick.
 */
public final class ImpostorSelfTest {
	private ImpostorSelfTest() {}

	static boolean appended;
	static ArmyWaves.Battle wb;
	static int fielded, reserveBefore, spawnedBefore;
	static long brokeBefore;
	static RankEntity probe;
	static UUID hitSoldier;
	static Vec3 farObserver;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (appended || !ArmiesSelfTest.started) return;
			appended = true;
			build();
		});
	}

	static void check(String name, boolean ok, Object detail) {
		ArmiesSelfTest.check("ranks: " + name, ok, detail);
	}

	static List<RankEntity> ranks(ArmyWaves.Side s) {
		List<RankEntity> out = new ArrayList<>();
		for (RankEntity r : Ranks.live()) if (r.controller instanceof RankWaves.SideRanks sr && sr.side == s) out.add(r);
		return out;
	}

	static int shown(ArmyWaves.Side s) {
		int n = 0;
		for (RankEntity r : ranks(s)) n += r.available() + r.reservedSlots();
		return n;
	}

	static boolean grounded(ServerLevel l, Entity e) {
		int g = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
		return Math.abs(e.getY() - g) <= 1.5;
	}

	static void build() {
		List<ArmiesSelfTest.Step> S = ArmiesSelfTest.STEPS;

		// ---- switch, entity, shapes -----------------------------------------------------------------------------------------------
		S.add((srv, l) -> {
			ArmiesSelfTest.info("rank impostors (s-impostors): " + (Ranks.enabled ? "on" : "off"));
			check("on (rank_impostors in config/elsewhere_armies.json) with the hook installed", Ranks.enabled && ArmyWaves.rankHook == RankWaves.INSTANCE, Ranks.enabled);
			check("elsewhere:army_rank is registered, not saved, tracked 10 chunks out every 10 ticks", Ranks.RANK != null
				&& KingdomContent.id("army_rank").equals(BuiltInRegistries.ENTITY_TYPE.getKey(Ranks.RANK)) && Ranks.RANK.clientTrackingRange() == 10
				&& Ranks.RANK.updateInterval() == 10, Ranks.RANK == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(Ranks.RANK));
			Identifier hraf = KingdomContent.id("hrafnholt"), azmar = KingdomContent.id("azmar"), mire = KingdomContent.id("mirewick");
			check("Aldermark ranks are lances of 10 in a line", Ranks.capacity(ArmiesSelfTest.A) == 10 && Ranks.holdShape(ArmiesSelfTest.A) == RankLayout.Shape.LINE,
				Ranks.capacity(ArmiesSelfTest.A) + " " + Ranks.holdShape(ArmiesSelfTest.A));
			if (ArmyScale.of(azmar) != null) check("Azmar ranks are square blocks of 16", Ranks.capacity(azmar) == 16 && Ranks.holdShape(azmar) == RankLayout.Shape.BLOCK,
				Ranks.capacity(azmar) + " " + Ranks.holdShape(azmar));
			if (ArmyScale.of(hraf) != null) check("Hrafnholt ranks of 16 march in the boar's snout (wedge)", Ranks.capacity(hraf) == 16 && Ranks.marchShape(hraf) == RankLayout.Shape.WEDGE,
				Ranks.capacity(hraf) + " " + Ranks.marchShape(hraf));
			ArmyScale ms = ArmyScale.of(mire);
			if (ms != null) {
				float sp = (float) ms.formation().spacing();
				float[] o = new float[2 * RankLayout.MAX];
				RankLayout.offsets(RankLayout.Shape.LINE, Ranks.capacity(mire), ms.formation().width(), sp, 0.5F, 7L, o);
				int w = Math.min(ms.formation().width(), Ranks.capacity(mire));
				float[] row = new float[w];
				for (int i = 0; i < w; i++) row[i] = o[2 * i];
				Arrays.sort(row);
				float gap = 0;
				for (int i = 1; i < w; i++) gap = Math.max(gap, row[i] - row[i - 1]);
				check("Mirewick ranks of 12 stand ragged, with a gap in the line", Ranks.capacity(mire) == 12 && ms.formation().jitter() >= 0.5 && gap >= Math.max(0.8F, sp) * 1.5F,
					Ranks.capacity(mire) + " jitter " + ms.formation().jitter() + " gap " + gap);
			}
			float minD = Float.MAX_VALUE;
			for (Identifier f : List.of(ArmiesSelfTest.A, hraf, azmar, mire)) {
				ArmyScale sc = ArmyScale.of(f);
				for (RankLayout.Shape sh : RankLayout.Shape.values()) {
					float[] o = new float[2 * RankLayout.MAX];
					int n = Ranks.capacity(f);
					RankLayout.offsets(sh, n, sc != null ? sc.formation().width() : 4, sc != null ? (float) sc.formation().spacing() : 1.25F,
						sc != null ? (float) sc.formation().jitter() : 0F, 12345L, o);
					for (int i = 0; i < n; i++) for (int k = i + 1; k < n; k++) minD = Math.min(minD, (float) Math.hypot(o[2 * i] - o[2 * k], o[2 * i + 1] - o[2 * k + 1]));
				}
			}
			check("no two figures of any shape stand inside each other (closest " + String.format("%.2f", minD) + ")", minD >= 0.6F, minD);
			// kill switch: off, nothing forms and a wave battle fields soldiers the 2.1 way
			ArmiesSelfTest.clean(l);
			Ranks.enabled = false;
			ArmyWaves.rankHook = null;
			ArmyWaves.reset();
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(Vec3.atCenterOf(ArmiesSelfTest.at(0, 0)));
			RankEntity none = Ranks.create(l, ArmiesSelfTest.A, Vec3.atBottomCenterOf(ArmiesSelfTest.at(0, 0)), 0F, RankEntity.Kind.LOOSE, null);
			ArmyWaves.Battle off = ArmyWaves.start(l, ArmiesSelfTest.at(0, 0), ArmiesSelfTest.A, ArmiesSelfTest.M, 40, 40, -1);
			check("kill switch off: no rank forms and a wave battle fields real soldiers as before", none == null && off != null && !off.a.soldiers.isEmpty()
				&& Ranks.live().isEmpty(), (none == null) + " " + (off == null ? null : off.a.spawned) + " ranks " + Ranks.live().size());
			ArmyWaves.stopAll(srv, true);
			Ranks.enabled = true;
			ArmyWaves.rankHook = RankWaves.INSTANCE;
			ArmiesSelfTest.clean(l);
			return 5;
		});

		// ---- a 150 v 150 wave battle forms its reserve into ranks -------------------------------------------------------------------
		S.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			RankWaves.reset();
			Ranks.resetCounters();
			ArmyWaves.reset();
			ArmyWaves.intervalScale = 100; // no waves of their own: this test calls them
			ArmyWaves.defaultFront = 32;
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(Vec3.atCenterOf(ArmiesSelfTest.at(0, 0)));
			wb = ArmyWaves.start(l, ArmiesSelfTest.at(0, 0), ArmiesSelfTest.A, ArmiesSelfTest.M, 150, 150, -1);
			check("a 150 v 150 wave battle starts", wb != null, null);
			return 200; // the ranks march up to their places (about 10 s)
		});
		S.add((srv, l) -> {
			if (wb == null) return 0;
			List<RankEntity> ra = ranks(wb.a), rd = ranks(wb.d);
			check("each side forms 2-6 reserve ranks", ra.size() >= 2 && ra.size() <= Ranks.PER_SIDE && rd.size() >= 2 && rd.size() <= Ranks.PER_SIDE, ra.size() + " + " + rd.size());
			int near = 0;
			for (RankEntity r : Ranks.live()) if (r.position().distanceTo(Vec3.atCenterOf(wb.pos)) < Ranks.AREA + 40) near++;
			check("at most " + Ranks.PER_AREA + " ranks in the battle area and " + Ranks.PER_SERVER + " on the server", near <= Ranks.PER_AREA && Ranks.live().size() <= Ranks.PER_SERVER, near);
			check("the ranks show no more men than the reserve holds", shown(wb.a) <= wb.a.reserve && shown(wb.d) <= wb.d.reserve,
				shown(wb.a) + "/" + wb.a.reserve + " " + shown(wb.d) + "/" + wb.d.reserve);
			boolean caps = true, ground = true, behind = true;
			Vec3 fa = RankWaves.front(l, wb.a), fd = RankWaves.front(l, wb.d);
			for (RankEntity r : ra) {
				caps &= r.count() <= Ranks.capacity(wb.a.faction) && r.slots() <= RankLayout.MAX;
				ground &= grounded(l, r);
				behind &= r.position().distanceToSqr(fd) > fa.distanceToSqr(fd);
			}
			for (RankEntity r : rd) {
				caps &= r.count() <= Ranks.capacity(wb.d.faction) && r.slots() <= RankLayout.MAX;
				ground &= grounded(l, r);
				behind &= r.position().distanceToSqr(fa) > fd.distanceToSqr(fa);
			}
			check("every rank holds at most its kingdom's figures (16 max)", caps, null);
			check("every rank stands on the ground (nothing floating or buried)", ground, null);
			check("the ranks stand behind their own front, facing the enemy", behind, null);
			// a wave: the front rank breaks out, one man per tick (room made first: the watched battle fills the per-player cap)
			clearLive(l, wb.a, 12);
			clearLive(l, wb.d, 4);
			int want = Math.min(10, KingdomPerf.room(l, Vec3.atCenterOf(wb.pos)) - 2);
			reserveBefore = wb.a.reserve;
			spawnedBefore = wb.a.spawned;
			brokeBefore = Ranks.brokeOut;
			Ranks.breakRefused = 0;
			fielded = want > 0 ? ArmyWaves.field(l, wb, wb.a, want) : 0;
			check("a wave is taken from the ranks (heads leave the reserve at once)", want > 0 && fielded == want && wb.a.reserve == reserveBefore - want
				&& wb.a.spawned == spawnedBefore + want, fielded + "/" + want + " reserve " + reserveBefore + "->" + wb.a.reserve);
			return 0;
		});
		S.add((srv, l) -> {
			long d = Ranks.brokeOut - brokeBefore;
			check("the break-out runs one soldier per tick, not all at once", d >= 1 && d <= 2, d);
			return 20;
		});
		S.add((srv, l) -> {
			if (wb == null) return 0;
			check("the whole wave stepped out of the ranks as real soldiers of the side", Ranks.brokeOut - brokeBefore == fielded && Ranks.breakRefused == 0,
				(Ranks.brokeOut - brokeBefore) + "/" + fielded + " out, refused " + Ranks.breakRefused);
			check("accounting holds after the break-out (shown <= reserve)", shown(wb.a) <= wb.a.reserve, shown(wb.a) + "/" + wb.a.reserve);
			// a player's blow breaks a figure out and the soldier takes it
			probe = null;
			for (RankEntity r : ranks(wb.d)) if (r.available() > 0) probe = r;
			if (probe == null) {
				check("a defender rank with figures to hit", false, null);
				return 0;
			}
			FakePlayer fp = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("rank-impostors".getBytes()), "rank-impostors"));
			fp.snapTo(probe.getX() + 3, probe.getY(), probe.getZ(), 0F, 0F);
			int count = probe.count(), res = wb.d.reserve;
			Set<UUID> was = new HashSet<>(wb.d.soldiers);
			long hits = Ranks.hitBreakOuts;
			probe.hurtServer(l, l.damageSources().playerAttack(fp), 4F);
			hitSoldier = null;
			for (UUID id : wb.d.soldiers) if (!was.contains(id)) hitSoldier = id;
			KingdomSoldier k = hitSoldier == null ? null : l.getEntity(hitSoldier) instanceof KingdomSoldier ks ? ks : null;
			check("a player's hit breaks a figure out (never invulnerable scenery) and the soldier takes the blow",
				Ranks.hitBreakOuts == hits + 1 && probe.count() == count - 1 && wb.d.reserve == res - 1 && k != null && k.getHealth() < k.getMaxHealth(),
				(Ranks.hitBreakOuts - hits) + " out, figures " + count + "->" + probe.count() + (k == null ? "" : ", hp " + k.getHealth() + "/" + k.getMaxHealth()));
			// an NPC's stray blow wounds a figure; a lethal one kills it inside the rank
			KingdomSoldier npc = null;
			for (UUID id : wb.a.soldiers) if (l.getEntity(id) instanceof KingdomSoldier ks) npc = ks;
			int c2 = probe.count(), res2 = wb.d.reserve, lost = wb.d.lost;
			long hits2 = Ranks.hitBreakOuts, deaths = Ranks.figureDeaths;
			if (npc != null) {
				probe.hurtServer(l, l.damageSources().mobAttack(npc), 3F);
				check("a soldier's stray blow wounds a figure without breaking it out", probe.count() == c2 && Ranks.hitBreakOuts == hits2, probe.count() + "/" + c2);
				probe.hurtServer(l, l.damageSources().mobAttack(npc), 1000F);
				check("a lethal stray blow kills a figure inside the rank; the head is lost from the reserve",
					Ranks.figureDeaths == deaths + 1 && wb.d.reserve == res2 - 1 && wb.d.lost == lost + 1, (Ranks.figureDeaths - deaths) + " died, reserve " + res2 + "->" + wb.d.reserve);
			}
			// R3: every observer far away (the battle still watched at 118 blocks) -> battle squads fold into ranks
			double dx = wb.a.rear.getX() - wb.pos.getX(), dz = wb.a.rear.getZ() - wb.pos.getZ(), dl = Math.max(1, Math.sqrt(dx * dx + dz * dz));
			farObserver = new Vec3(wb.pos.getX() - dx / dl * 118, wb.pos.getY(), wb.pos.getZ() - dz / dl * 118);
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(farObserver);
			RankEntity.tickNanos = 0;
			RankEntity.ticks = 0;
			materializedBefore = dev.elsewhere.kingdom.KingdomLod.materialized;
			return 200;
		});
		S.add((srv, l) -> {
			if (wb == null) return 0;
			List<RankEntity> folds = new ArrayList<>();
			for (RankEntity r : Ranks.live()) if (r.kind == RankEntity.Kind.FOLDED) folds.add(r);
			boolean records = !folds.isEmpty();
			for (RankEntity r : folds) {
				Squad q = r.squad == null ? null : KingdomState.get(l).squad(r.squad);
				records &= q != null && q.isVirtual() && r.count() > 0 && r.count() <= q.virtualMembers().size();
			}
			check("R3: battle squads beyond " + (int) RankWaves.FOLD + " blocks of every player fold into ranks (the squad's record keeps UUIDs and health)",
				Ranks.folded >= 1 && records, Ranks.folded + " folded, " + folds.size() + " shown");
			check("R3: folded soldiers still count as alive for their side", ArmyWaves.alive(l, wb.a) + ArmyWaves.alive(l, wb.d) > 0, null);
			check("budgets hold with folds (" + Ranks.live().size() + " ranks)", Ranks.live().size() <= Ranks.PER_SERVER, null);
			double us = RankEntity.ticks == 0 ? 0 : RankEntity.tickNanos / 1000.0 / RankEntity.ticks;
			ArmiesSelfTest.info(String.format("BENCH rank impostors: %.2f us per rank tick over %d rank ticks, %d ranks, %d figures, %d dice rounds", us, RankEntity.ticks,
				Ranks.live().size(), Ranks.figuresShown(), Ranks.diceRolls));
			check("a rank tick costs at most 20 us on average (" + String.format("%.2f", us) + ")", RankEntity.ticks > 0 && us <= 20.0, us);
			// a player comes back: the squads materialize and their ranks vanish the same tick (room made: the live front fills the cap)
			clearLive(l, wb.a, 64);
			clearLive(l, wb.d, 64);
			materializedBefore = dev.elsewhere.kingdom.KingdomLod.materialized;
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(Vec3.atCenterOf(wb.pos));
			return 80;
		});
		S.add((srv, l) -> {
			if (wb == null) return 0;
			int stale = 0;
			for (RankEntity r : Ranks.live()) {
				if (r.kind != RankEntity.Kind.FOLDED) continue;
				Squad q = r.squad == null ? null : KingdomState.get(l).squad(r.squad);
				if (q == null || !q.isVirtual()) stale++;
			}
			long mat = dev.elsewhere.kingdom.KingdomLod.materialized - materializedBefore;
			check("R3: a player within 80 unfolds the squads and their ranks go the same tick (never both)", Ranks.folded >= 1 && mat >= 1 && Ranks.unfolded >= 1 && stale == 0,
				mat + " materialized, " + Ranks.unfolded + " ranks gone, " + stale + " stale");
			// the battle ends: the ranks loosen, the losing side routs
			ArmyWaves.stopAll(srv, false);
			boolean loose = true, rout = false;
			for (RankEntity r : Ranks.live()) {
				loose &= r.kind == RankEntity.Kind.LOOSE && r.controller == null;
				rout |= r.state() == RankEntity.State.ROUT;
			}
			check("at the end every rank loosens and the losing side's ranks rout", !Ranks.live().isEmpty() && loose && rout, Ranks.live().size() + " ranks");
			return 40;
		});
		S.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			RankWaves.reset();
			wipe();
			// a rank marching past the loaded area goes abstract (no chunk is ever loaded for it)
			RankEntity r = Ranks.create(l, ArmiesSelfTest.A, Vec3.atBottomCenterOf(ArmiesSelfTest.at(86, 0)), -90F, RankEntity.Kind.LOOSE, null);
			if (r != null) {
				for (int i = 0; i < 6; i++) r.addFigure(new Figure(UnitTypes.MAN_AT_ARMS, Figure.FULL, null), Figure.look(ArmiesSelfTest.A, UnitTypes.MAN_AT_ARMS));
				r.pace = 0.4F;
				r.marchTo(new Vec3(400, r.getY(), r.getZ()), -90F);
			}
			probe = r;
			brokeBefore = Ranks.abstracted;
			return 200;
		});
		S.add((srv, l) -> {
			check("a rank reaching unloaded ground goes abstract instead of loading chunks", probe != null && probe.isRemoved() && Ranks.abstracted == brokeBefore + 1,
				probe == null ? null : probe.getX() + " removed " + probe.isRemoved());
			ArmyWaves.intervalScale = 1.0;
			ArmyWaves.defaultFront = 15;
			KingdomPerf.testObservers.clear();
			ArmiesSelfTest.clean(l);
			RankWaves.reset();
			wipe();
			return 5;
		});
	}

	static long materializedBefore;

	/** Test only: removes up to {@code max} of a side's live soldiers (to make room under the per-player cap). */
	static void clearLive(ServerLevel l, ArmyWaves.Side s, int max) {
		int n = 0;
		for (UUID id : new ArrayList<>(s.soldiers)) {
			if (n >= max) break;
			if (l.getEntity(id) instanceof KingdomSoldier k && k.isAlive()) {
				k.discard();
				n++;
			}
		}
		ArmyWaves.alive(l, s);
	}

	static void wipe() {
		for (RankEntity r : Ranks.live()) {
			r.controller = null;
			r.discard();
		}
	}
}
