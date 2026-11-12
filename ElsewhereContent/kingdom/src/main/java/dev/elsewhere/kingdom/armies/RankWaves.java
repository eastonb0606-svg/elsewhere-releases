package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.KingdomLod;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.RanksBridge;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.armies.impostor.Figure;
import dev.elsewhere.kingdom.armies.impostor.RankController;
import dev.elsewhere.kingdom.armies.impostor.RankEntity;
import dev.elsewhere.kingdom.armies.impostor.RankLayout;
import dev.elsewhere.kingdom.armies.impostor.Ranks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wave battles' side of the rank impostors (ARMIES.md 3.5 / 3.6), installed as {@link ArmyWaves#rankHook} when {@code rank_impostors} is on.
 * <ul>
 * <li><b>Reserve ranks:</b> at the start each side forms up to {@link Ranks#PER_SIDE} ranks from its reserve (Aldermark lances of 10,
 * Hrafnholt walls of 16, Azmar blocks of 16, Mirewick watches of 12), two rows of three, 14 and 22 blocks behind its front and facing the
 * enemy; once a second they march to keep that distance as the front moves. While a side has fewer than 6 ranks and reserve left over, a new
 * rank forms at the rear every 30 s and marches up.</li>
 * <li><b>Waves:</b> {@link ArmyWaves#field} asks {@link #breakOut}: the rank nearest the front breaks out, one soldier per tick at his own
 * spot. A wave larger than one rank takes the next one too.</li>
 * <li><b>Line relief</b> (Azmar's style, any kingdom whose waves withdraw wounded): the wounded man walks back to the rearmost rank with room
 * and becomes a figure there with his saved health. Engaged by a player on the way, he turns and fights again.</li>
 * <li><b>R3 crowd:</b> a battle squad farther than {@value #FOLD} blocks from every player for {@value #FOLD_TICKS} ticks folds into a rank
 * (the squad's own virtual record keeps UUIDs and health; {@link ArmyWaves#alive} still counts them). Folded ranks follow the squad's
 * point, fight by dice every 20 ticks against enemy ranks and soldiers within 20 blocks, and vanish the moment the squad materializes
 * (a player within 80).</li>
 * <li><b>End:</b> the losing side's ranks rout, the winner's march home; both leave once out of sight.</li>
 * </ul>
 * Everything runs on the battle's once-a-second tick or the ranks' own 20-tick step, never per tick and never as a world scan.
 */
public final class RankWaves implements ArmyWaves.RankHook {
	private RankWaves() {}

	static final RankWaves INSTANCE = new RankWaves();
	/** R3: fold beyond this distance from every player, after this long. */
	public static final double FOLD = 104;
	public static final int FOLD_TICKS = 100;
	/** Reserve ranks stand this far behind their front (first row) and this much further per row. */
	public static final double BEHIND = 14, ROW = 8;
	public static final int REFILL_TICKS = 600;
	/** Dice: chance per figure per round (20 ticks) to land a blow, and its damage. */
	public static final double DICE_HIT = 0.06;
	public static final float DICE_DAMAGE = 5F;

	static final Map<ArmyWaves.Side, SideRanks> SIDES = new IdentityHashMap<>();
	static final Map<UUID, Fold> FOLDS = new HashMap<>();
	static final Map<UUID, Long> FAR_SINCE = new HashMap<>();

	public static void install() {
		ArmyWaves.rankHook = INSTANCE;
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
			if (SIDES.isEmpty() || !(e instanceof KingdomSoldier)) return;
			for (SideRanks sr : SIDES.values()) {
				Walker w = sr.walkers.remove(e.getUUID());
				if (w == null) continue;
				// a wounded man killed on his way back: his head (already back in the reserve) is lost
				sr.side.reserve = Math.max(0, sr.side.reserve - 1);
				sr.side.lost++;
				w.rank.reserveSlot(-1);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((e, lv) -> {
			// a folded squad materialized (a player came within 80): its rank goes the same tick, never both at once
			if (FOLDS.isEmpty() || !(e instanceof KingdomSoldier k) || k.squadId() == null) return;
			Fold f = FOLDS.get(k.squadId());
			if (f != null && f.rank != null && !f.rank.isRemoved()) {
				Ranks.unfolded++;
				RankEntity r = f.rank;
				f.rank = null;
				r.controller = null;
				r.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			SIDES.clear();
			FOLDS.clear();
			FAR_SINCE.clear();
		});
	}

	/** Forgets every battle's ranks (self-test between scenes; the entities themselves are cleaned by the test). */
	static void reset() {
		for (RankEntity r : Ranks.live()) {
			if (r.controller instanceof SideRanks || r.controller instanceof Fold) {
				r.controller = null;
				r.discard();
			}
		}
		SIDES.clear();
		FOLDS.clear();
		FAR_SINCE.clear();
	}

	// ---- geometry -------------------------------------------------------------------------------------------------------------------

	/** Centre of a side's live soldiers (its rear when nobody is out). */
	static Vec3 front(ServerLevel lv, ArmyWaves.Side s) {
		double x = 0, y = 0, z = 0;
		int n = 0;
		for (UUID id : s.soldiers) {
			Entity e = lv.getEntity(id);
			if (e == null || !e.isAlive()) continue;
			x += e.getX();
			y += e.getY();
			z += e.getZ();
			n++;
		}
		return n == 0 ? Vec3.atBottomCenterOf(s.rear) : new Vec3(x / n, y / n, z / n);
	}

	/** Unit vector from the enemy toward this side (its "behind"). */
	static Vec3 behind(ArmyWaves.Battle b, ArmyWaves.Side s, Vec3 own, Vec3 enemy) {
		double dx = own.x - enemy.x, dz = own.z - enemy.z, d = Math.sqrt(dx * dx + dz * dz);
		if (d < 4) {
			dx = s.rear.getX() - b.pos.getX();
			dz = s.rear.getZ() - b.pos.getZ();
			d = Math.sqrt(dx * dx + dz * dz);
			if (d < 1) {
				dx = s == b.a ? 1 : -1;
				dz = 0;
				d = 1;
			}
		}
		return new Vec3(dx / d, 0, dz / d);
	}

	static float yawToward(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Ground spot at x/z (the fallback height when not loaded). */
	static Vec3 ground(ServerLevel lv, double x, double z, double fallbackY) {
		BlockPos p = BlockPos.containing(x, fallbackY, z);
		if (!lv.isLoaded(p)) return new Vec3(x, fallbackY, z);
		return new Vec3(x, lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), z);
	}

	// ---- RankHook ---------------------------------------------------------------------------------------------------------------------

	@Override
	public void started(ServerLevel lv, ArmyWaves.Battle b) {
		for (ArmyWaves.Side s : List.of(b.a, b.d)) {
			SideRanks sr = new SideRanks(b, s);
			SIDES.put(s, sr);
			sr.refill(lv, true);
		}
	}

	@Override
	public void tick(ServerLevel lv, ArmyWaves.Battle b) {
		Vec3 fa = front(lv, b.a), fd = front(lv, b.d);
		for (ArmyWaves.Side s : List.of(b.a, b.d)) {
			SideRanks sr = SIDES.get(s);
			if (sr == null) continue;
			Vec3 own = s == b.a ? fa : fd, enemy = s == b.a ? fd : fa;
			sr.place(lv, own, enemy);
			if (lv.getGameTime() >= sr.nextRefill) sr.refill(lv, false);
			sr.walk(lv);
			foldFar(lv, b, s);
		}
	}

	@Override
	public int breakOut(ServerLevel lv, ArmyWaves.Battle b, ArmyWaves.Side s, int n) {
		SideRanks sr = SIDES.get(s);
		if (sr == null || n <= 0) return 0;
		ArmyWaves.Side other = s == b.a ? b.d : b.a;
		Vec3 enemy = front(lv, other);
		List<RankEntity> order = new ArrayList<>(sr.ranks);
		order.removeIf(r -> r.isRemoved() || r.available() <= 0);
		order.sort(Comparator.comparingDouble(r -> r.position().distanceToSqr(enemy)));
		int done = 0;
		for (RankEntity r : order) {
			if (done >= n) break;
			r.breakSquad = null; // each rank's break-out is a squad of its own
			done += r.queueBreakOut(n - done);
		}
		return done;
	}

	@Override
	public boolean foldWounded(ServerLevel lv, ArmyWaves.Side s, KingdomSoldier k) {
		SideRanks sr = SIDES.get(s);
		if (sr == null) return false;
		RankEntity to = null;
		double far = -1;
		for (RankEntity r : sr.ranks) {
			if (r.isRemoved() || r.freeSlots() <= 0) continue;
			double d = r.position().distanceToSqr(k.position());
			if (d > far) {
				far = d;
				to = r;
			}
		}
		if (to == null && sr.ranks.size() < Ranks.PER_SIDE) to = sr.form(lv, sr.rearSpot(lv), 0);
		if (to == null) return false;
		to.reserveSlot(1);
		sr.walkers.put(k.getUUID(), new Walker(k.getUUID(), to, lv.getGameTime()));
		KingdomState.get(lv).leave(k);
		k.setTarget(null);
		if (KingdomPerf.tryPath(lv)) k.getNavigation().moveTo(to.getX(), to.getY(), to.getZ(), 1.0);
		return true;
	}

	@Override
	public boolean folded(ServerLevel lv, UUID soldier) {
		if (FOLDS.isEmpty()) return false;
		KingdomState ks = KingdomState.get(lv);
		for (Fold f : FOLDS.values()) {
			Squad q = ks.squad(f.squad);
			if (q != null && RanksBridge.holdsVirtual(q, soldier)) return true;
		}
		return false;
	}

	@Override
	public void ended(ServerLevel lv, ArmyWaves.Battle b, boolean attackerWon, boolean discard) {
		for (ArmyWaves.Side s : List.of(b.a, b.d)) {
			SideRanks sr = SIDES.remove(s);
			boolean lost = (s == b.a) != attackerWon;
			Vec3 home = Vec3.atBottomCenterOf(s.rear);
			Vec3 away = behind(b, s, home, Vec3.atBottomCenterOf(b.pos)).scale(48).add(home);
			if (sr != null) {
				for (RankEntity r : new ArrayList<>(sr.ranks)) {
					if (r.isRemoved()) continue;
					if (discard) {
						r.controller = null;
						r.discard();
					} else {
						Ranks.loosen(r, away, lost);
					}
				}
				for (Walker w : sr.walkers.values()) if (lv.getEntity(w.soldier) instanceof KingdomSoldier k) k.discard(); // already counted in the reserve
				sr.walkers.clear();
			}
			for (Iterator<Fold> it = FOLDS.values().iterator(); it.hasNext();) {
				Fold f = it.next();
				if (f.side != s) continue;
				it.remove();
				FAR_SINCE.remove(f.squad);
				if (f.rank == null || f.rank.isRemoved()) continue;
				if (discard) {
					f.rank.controller = null;
					f.rank.discard();
				} else {
					Ranks.loosen(f.rank, away, lost);
				}
			}
			for (UUID q : s.squads) FAR_SINCE.remove(q);
		}
	}

	// ---- R3: far battle squads fold into ranks ---------------------------------------------------------------------------------------

	static void foldFar(ServerLevel lv, ArmyWaves.Battle b, ArmyWaves.Side s) {
		KingdomState ks = KingdomState.get(lv);
		long now = lv.getGameTime();
		for (UUID id : new ArrayList<>(s.squads)) {
			Squad q = ks.squad(id);
			if (q == null) {
				FAR_SINCE.remove(id);
				continue;
			}
			if (q.isVirtual()) {
				// folded earlier: make sure its rank stands (e.g. after a chunk unload took it)
				Fold f = FOLDS.get(id);
				if (f == null || f.rank == null || f.rank.isRemoved()) show(lv, b, s, q, f);
				continue;
			}
			var lead = q.leaderEntity(lv);
			Vec3 at = lead != null ? lead.position() : null;
			if (at == null) {
				List<KingdomSoldier> m = q.memberEntities(lv);
				if (m.isEmpty()) continue;
				at = m.get(0).position();
			}
			if (KingdomLod.observerNear(lv, at.x, at.z, FOLD) || engagedWithPlayer(lv, q)) {
				FAR_SINCE.remove(id);
				continue;
			}
			long since = FAR_SINCE.computeIfAbsent(id, k -> now);
			if (now - since < FOLD_TICKS) continue;
			if (!Ranks.roomFor(lv, at)) {
				// the area's 12 ranks are full: a fold (fighting men) outranks an idle reserve rank out of everyone's sight, whose heads simply
				// stay in the reserve as numbers; with nothing to yield the squad stays real (never an invisible squad)
				SideRanks own = SIDES.get(s);
				if (own == null || !own.yieldRear(lv) || !Ranks.roomFor(lv, at)) continue;
			}
			if (KingdomLod.virtualize(lv, q)) {
				FAR_SINCE.remove(id);
				Ranks.folded++;
				show(lv, b, s, q, null);
			}
		}
	}

	static boolean engagedWithPlayer(ServerLevel lv, Squad q) {
		if (q.leaderEntity(lv) instanceof KingdomSoldier l && l.getTarget() instanceof Player) return true;
		for (KingdomSoldier m : q.memberEntities(lv)) if (m.getTarget() instanceof Player) return true;
		return false;
	}

	/** Makes (or remakes) the rank that shows a folded squad. */
	static void show(ServerLevel lv, ArmyWaves.Battle b, ArmyWaves.Side s, Squad q, @Nullable Fold old) {
		Vec3 a = q.anchor();
		if (a == null) return;
		Fold f = old != null ? old : new Fold(b, s, q.id());
		BlockPos ob = q.objective();
		float yaw = ob != null ? yawToward(ob.getX() + 0.5 - a.x, ob.getZ() + 0.5 - a.z) : 0F;
		RankEntity r = Ranks.create(lv, s.faction, a, yaw, RankEntity.Kind.FOLDED, f);
		if (r == null) {
			FOLDS.put(q.id(), f); // shown again when a budget frees up
			return;
		}
		r.squad = q.id();
		for (Squad.VirtualSoldier v : q.virtualMembers()) {
			if (r.freeSlots() <= 0) break;
			r.addFigure(new Figure(v.unit(), v.health(), v.uuid()), Figure.look(s.faction, v.unit()));
		}
		r.setState(RankEntity.State.FIGHT);
		f.rank = r;
		FOLDS.put(q.id(), f);
	}

	/** The rank of a folded squad: follows the squad's point and fights by dice. */
	static final class Fold implements RankController {
		final ArmyWaves.Battle battle;
		final ArmyWaves.Side side;
		final UUID squad;
		@Nullable RankEntity rank;

		Fold(ArmyWaves.Battle battle, ArmyWaves.Side side, UUID squad) {
			this.battle = battle;
			this.side = side;
			this.squad = squad;
		}

		@Override
		public void slowTick(ServerLevel lv, RankEntity r) {
			Squad q = KingdomState.get(lv).squad(squad);
			if (q == null || !q.isVirtual()) {
				// gone (all fallen) or real again: the rank goes
				FOLDS.remove(squad);
				r.controller = null;
				r.discard();
				return;
			}
			Vec3 a = q.anchor();
			if (a != null && a.distanceToSqr(r.position()) > 1.0) {
				BlockPos ob = q.objective();
				r.marchTo(a, ob != null ? yawToward(ob.getX() + 0.5 - a.x, ob.getZ() + 0.5 - a.z) : r.getYRot());
			}
			dice(lv, r);
		}

		/** Rank-vs-rank dice (every 20 ticks): each figure may land a blow on an enemy figure or soldier within 20 blocks. */
		void dice(ServerLevel lv, RankEntity r) {
			ArmyWaves.Side other = side == battle.a ? battle.d : battle.a;
			List<Object> foes = new ArrayList<>();
			double r2 = 20 * 20;
			for (RankEntity e : Ranks.live()) {
				if (e == r || e.level() != lv || !e.faction.equals(other.faction) || e.position().distanceToSqr(r.position()) > r2) continue;
				for (int i = 0; i < e.slots(); i++) if (e.present(i)) foes.add(new int[] {e.getId(), i});
			}
			for (UUID id : other.soldiers) {
				Entity e = lv.getEntity(id);
				if (e instanceof LivingEntity le && le.isAlive() && e.position().distanceToSqr(r.position()) <= r2) foes.add(le);
			}
			if (foes.isEmpty()) {
				r.setState(r.goal != null ? RankEntity.State.MARCH : RankEntity.State.HOLD);
				return;
			}
			r.setState(RankEntity.State.FIGHT);
			Ranks.diceRolls++;
			RandomSource rnd = lv.getRandom();
			int blows = 0;
			for (int i = 0; i < r.slots(); i++) if (r.present(i) && rnd.nextDouble() < DICE_HIT) blows++;
			for (int k = 0; k < blows && !foes.isEmpty(); k++) {
				Object t = foes.get(rnd.nextInt(foes.size()));
				if (t instanceof int[] ref) {
					if (lv.getEntity(ref[0]) instanceof RankEntity e && !e.isRemoved()) Ranks.wound(lv, e, ref[1], DICE_DAMAGE);
				} else if (t instanceof LivingEntity le && le.isAlive()) {
					le.hurtServer(lv, lv.damageSources().generic(), DICE_DAMAGE);
				}
			}
		}

		@Override
		public void brokeOut(ServerLevel lv, RankEntity r, Figure f, KingdomSoldier s, boolean committed) {}

		@Override
		public void breakFailed(ServerLevel lv, RankEntity r, Figure f) {}

		@Override
		public void figureDied(ServerLevel lv, RankEntity r, Figure f, boolean committed) {
			Squad q = KingdomState.get(lv).squad(squad);
			if (q != null && f.uuid != null && RanksBridge.removeVirtual(lv, q, f.uuid)) side.lost++;
		}

		@Override
		public void figureWounded(ServerLevel lv, RankEntity r, Figure f) {
			Squad q = KingdomState.get(lv).squad(squad);
			if (q != null && f.uuid != null) RanksBridge.setVirtualHealth(lv, q, f.uuid, f.health);
		}

		@Override
		public boolean unfold(ServerLevel lv, RankEntity r) {
			Squad q = KingdomState.get(lv).squad(squad);
			if (q == null || !q.isVirtual()) return false;
			// materialize spawns the soldiers (same UUIDs, saved health); the ENTITY_LOAD hook removes this rank at once
			return KingdomLod.materialize(lv, q) > 0;
		}

		@Override
		public void removed(ServerLevel lv, RankEntity r) {
			if (rank == r) rank = null; // the squad stays virtual; foldFar shows it again while the battle runs
		}
	}

	// ---- reserve ranks of one side -------------------------------------------------------------------------------------------------

	record Walker(UUID soldier, RankEntity rank, long since) {}

	static final class SideRanks implements RankController {
		final ArmyWaves.Battle battle;
		final ArmyWaves.Side side;
		final List<RankEntity> ranks = new ArrayList<>();
		final Map<UUID, Walker> walkers = new HashMap<>();
		long nextRefill;
		Vec3 lastOwn, lastBehind;

		SideRanks(ArmyWaves.Battle battle, ArmyWaves.Side side) {
			this.battle = battle;
			this.side = side;
		}

		/** Reserve heads not yet standing in a rank (queued break-outs have already left the reserve; walking wounded are in it). */
		int unshown() {
			int shown = 0;
			for (RankEntity r : ranks) if (!r.isRemoved()) shown += r.available() + r.reservedSlots();
			return side.reserve - shown;
		}

		/** Spot k of the two rows of three behind the front (0 = middle of the first row). */
		Vec3 spot(ServerLevel lv, int k, Vec3 own, Vec3 back, double extra) {
			int row = k / 3, col = k % 3;
			int lateral = col == 0 ? 0 : col == 1 ? -1 : 1;
			double width = Math.max(4, Ranks.capacity(side.faction) >= 16 && Ranks.holdShape(side.faction) == RankLayout.Shape.BLOCK ? 5 : widthBlocks()) + 3;
			double dist = BEHIND + row * ROW + extra;
			double x = own.x + back.x * dist - back.z * lateral * width, z = own.z + back.z * dist + back.x * lateral * width;
			return ground(lv, x, z, own.y);
		}

		double widthBlocks() {
			ArmyScale s = ArmyScale.of(side.faction);
			return s == null ? 5 : s.formation().width() * s.formation().spacing();
		}

		Vec3 rearSpot(ServerLevel lv) {
			Vec3 own = lastOwn != null ? lastOwn : Vec3.atBottomCenterOf(side.rear);
			Vec3 back = lastBehind != null ? lastBehind : behind(battle, side, own, Vec3.atBottomCenterOf(battle.pos));
			return spot(lv, Math.min(ranks.size(), Ranks.PER_SIDE - 1), own, back, 12);
		}

		/** Forms ranks from the reserve: all at once at the start, then one per {@link #REFILL_TICKS}. */
		void refill(ServerLevel lv, boolean initial) {
			nextRefill = lv.getGameTime() + REFILL_TICKS;
			ranks.removeIf(RankEntity::isRemoved);
			Vec3 own = Vec3.atBottomCenterOf(side.rear);
			Vec3 back = behind(battle, side, own, Vec3.atBottomCenterOf(battle.pos));
			lastOwn = lastOwn != null ? lastOwn : own;
			lastBehind = lastBehind != null ? lastBehind : back;
			int made = 0;
			while (ranks.size() < Ranks.PER_SIDE && unshown() > 0 && (initial || made == 0)) {
				int k = ranks.size();
				Vec3 at = spot(lv, k, lastOwn, lastBehind, initial ? 6 : 12);
				RankEntity r = form(lv, at, Math.min(unshown(), Ranks.capacity(side.faction)));
				if (r == null) break;
				made++;
				if (!initial) Ranks.refilled++;
			}
		}

		/** A rank of {@code n} fresh figures of this side (a sergeant and his men, as the kingdom fields them). */
		@Nullable RankEntity form(ServerLevel lv, Vec3 at, int n) {
			Vec3 enemy = Vec3.atBottomCenterOf(battle.pos);
			RankEntity r = Ranks.create(lv, side.faction, at, yawToward(enemy.x - at.x, enemy.z - at.z), RankEntity.Kind.RESERVE, this);
			if (r == null) return null;
			if (n > 0) {
				List<Identifier> units = new ArrayList<>();
				units.add(UnitTypes.SERGEANT);
				units.addAll(ArmyComposer.members(lv.getRandom(), n - 1, false, side.faction));
				for (Identifier u : units) r.addFigure(new Figure(u, Figure.FULL, null), Figure.look(side.faction, u));
			}
			ranks.add(r);
			return r;
		}

		/** Keeps every rank 14-22 blocks behind the front, two rows of three, facing the enemy. */
		void place(ServerLevel lv, Vec3 own, Vec3 enemy) {
			ranks.removeIf(RankEntity::isRemoved);
			Vec3 back = behind(battle, side, own, enemy);
			lastOwn = own;
			lastBehind = back;
			float face = yawToward(-back.x, -back.z);
			for (int k = 0; k < ranks.size(); k++) {
				RankEntity r = ranks.get(k);
				if (r.state() == RankEntity.State.BREAK) continue; // stands still while its men step out
				Vec3 to = spot(lv, k, own, back, 0);
				if (to.distanceToSqr(r.position()) > 4.0) r.marchTo(to, face);
				else if (r.goal == null && Math.abs(net.minecraft.util.Mth.wrapDegrees(r.getYRot() - face)) > 20) r.marchTo(r.position(), face);
			}
		}

		/** Line relief: wounded men walking back fold into their rank on arrival (or after 5 s); one engaged by a player fights again. */
		void walk(ServerLevel lv) {
			long now = lv.getGameTime();
			for (Iterator<Walker> it = walkers.values().iterator(); it.hasNext();) {
				Walker w = it.next();
				Entity e = lv.getEntity(w.soldier());
				if (!(e instanceof KingdomSoldier k) || !k.isAlive()) {
					// unloaded or gone without a death: his head stays in the reserve
					w.rank().reserveSlot(-1);
					it.remove();
					continue;
				}
				if (k.getTarget() instanceof Player || k.getLastHurtByMob() instanceof Player && now - k.getLastHurtByMobTimestamp() < 100) {
					// a player is on him: he is a live soldier again (never folded mid-fight)
					w.rank().reserveSlot(-1);
					side.reserve = Math.max(0, side.reserve - 1);
					side.spawned++;
					side.soldiers.add(k.getUUID());
					it.remove();
					continue;
				}
				RankEntity r = w.rank();
				if (r.isRemoved()) {
					k.discard(); // his rank is gone: he joins the reserve as a number
					it.remove();
					continue;
				}
				if (k.distanceToSqr(r) < 9 || now - w.since() > 100) {
					r.reserveSlot(-1);
					r.addFigure(new Figure(k.unitId(), k.getHealth(), null), Figure.look(side.faction, k.unitId()));
					k.discard();
					Ranks.reliefFolded++;
					it.remove();
				} else if (k.getNavigation().isDone() && KingdomPerf.tryPath(lv)) {
					k.getNavigation().moveTo(r.getX(), r.getY(), r.getZ(), 1.0);
				}
			}
		}

		/** Retires this side's rearmost idle reserve rank (no break-out queued, nobody walking to it) if no player is within the fold distance. */
		boolean yieldRear(ServerLevel lv) {
			for (int k = ranks.size() - 1; k >= 0; k--) {
				RankEntity r = ranks.get(k);
				if (r.isRemoved() || r.queued() > 0 || r.reservedSlots() > 0 || KingdomLod.observerNear(lv, r.getX(), r.getZ(), FOLD)) continue;
				ranks.remove(k);
				r.controller = null;
				r.discard();
				nextRefill = lv.getGameTime() + REFILL_TICKS;
				return true;
			}
			return false;
		}

		@Override
		public void slowTick(ServerLevel lv, RankEntity r) {
			// placement runs on the battle's own once-a-second tick; nothing per rank here
		}

		@Override
		public void brokeOut(ServerLevel lv, RankEntity r, Figure f, KingdomSoldier s, boolean committed) {
			if (!committed) {
				side.reserve = Math.max(0, side.reserve - 1);
				side.spawned++;
			}
			side.soldiers.add(s.getUUID());
			Squad q = s.squad();
			if (q != null && side.squads.add(q.id())) {
				q.setVirtualizable(false);
				q.setObjective(side.objective);
				q.setFormation(side == battle.d ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE);
			}
		}

		@Override
		public void breakFailed(ServerLevel lv, RankEntity r, Figure f) {
			side.reserve++;
			side.spawned = Math.max(0, side.spawned - 1);
		}

		@Override
		public void figureDied(ServerLevel lv, RankEntity r, Figure f, boolean committed) {
			if (committed) side.spawned = Math.max(0, side.spawned - 1);
			else side.reserve = Math.max(0, side.reserve - 1);
			side.lost++;
		}

		@Override
		public boolean unfold(ServerLevel lv, RankEntity r) {
			return false;
		}

		@Override
		public void removed(ServerLevel lv, RankEntity r) {
			ranks.remove(r);
			// figures still queued had left the reserve but never stepped out: their heads go back
			for (int i = 0; i < r.queued(); i++) breakFailed(lv, r, null);
		}
	}
}
