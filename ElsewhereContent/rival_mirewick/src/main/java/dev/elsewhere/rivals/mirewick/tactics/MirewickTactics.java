package dev.elsewhere.rivals.mirewick.tactics;

import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.tactics.Behaviour;
import dev.elsewhere.kingdom.tactics.Behaviours;
import dev.elsewhere.kingdom.tactics.Order;
import dev.elsewhere.kingdom.tactics.Perception;
import dev.elsewhere.kingdom.tactics.Signal;
import dev.elsewhere.kingdom.tactics.SimpleTacticsProfile;
import dev.elsewhere.kingdom.tactics.SquadBrain;
import dev.elsewhere.kingdom.tactics.SquadMemory;
import dev.elsewhere.kingdom.tactics.TacticsProfile;
import dev.elsewhere.kingdom.tactics.Types;
import dev.elsewhere.kingdom.war.WarApi;
import dev.elsewhere.kingdom.war.WarState;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import dev.elsewhere.rivals.mirewick.MirewickState;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mirewick's field-tactics profile (FIELD-TACTICS.md section 5: sloppy, money-run, hires its answers), built on the war lane's typed
 * squad-tactics framework ({@code dev.elsewhere.kingdom.tactics}, registered with {@code KingdomApi.registerTactics}). Profile numbers:
 * decision interval 20 (the slowest), human reaction 14-30 ticks, blunder rate 0.20, minimum local odds 0.9, retreat odds 0.40, intel noise 0.60,
 * mob policy "careful"-less default. The brain decides at most every 20 ticks per squad; nothing here paths or scans on its own.
 * <ul>
 * <li>{@code split_command} (ENGAGE): each real squad belongs to one Magnate's command group; the Chair's group gets the strategic order at
 * once, the others after 40-100 ticks, and with chance {@code disorg} 0.35 a group follows its own leaning instead (hawk advances, dove holds,
 * miser holds back), so two squads in one fight can run opposing orders;</li>
 * <li>{@code hire_answer} (REACT): when a squad's odds fall under 1.0 and the treasury is over the reserve, the Council pays for a Free Company
 * (cost per head, at most 3 companies, caps respected) that arrives 600 ticks later behind a lantern flare; the squad holds meanwhile;</li>
 * <li>{@code crossbow_volley} (ENGAGE): ranks of crossbows (at least 3) fire together on a banner drop; only crossbows within 6 blocks of each
 * other share a target, the rest of the line screens in front;</li>
 * <li>{@code desertion}: not a squad behaviour (Free Company squads belong to {@code elsewhere:free_company}, not to Mirewick): a money watcher
 * makes half of every company walk off at a treasury of 0 ("No pay, no fight"), the rest flee on morale;</li>
 * <li>{@code trap_line} and {@code tower_shield_hold} live on the Mire-Trapper and the Bog-Warden themselves; the library supplies
 * {@code engage_default, shield_wall, refuse_fight, fall_back_regroup, back_off_creeper, high_ground, call_help, picket, protect_unit}
 * ({@code focus_fire} is left out on purpose: Mirewick's focus is poor).</li>
 * </ul>
 */
public final class MirewickTactics {
	private MirewickTactics() {}

	public static final Identifier FC = FreeCompanies.FACTION;

	/** The profile's numbers (FIELD-TACTICS 5, WAR-AI 3). */
	public static final Map<String, Double> PARAMS = new LinkedHashMap<>();

	static {
		PARAMS.put("decision_interval", 20.0);
		PARAMS.put("min_local_odds", 0.9);
		PARAMS.put("retreat_odds", 0.40);
		PARAMS.put("blunder_rate", 0.20);
		PARAMS.put("reaction_ticks_min", 14.0);
		PARAMS.put("reaction_ticks_max", 30.0);
		PARAMS.put("intel_noise", 0.60);
		PARAMS.put("disorg", 0.35);
		PARAMS.put("miser", 0.20);
		PARAMS.put("hire_max", 45.0);
		PARAMS.put("split_delay_min", 40.0);
		PARAMS.put("split_delay_max", 100.0);
		PARAMS.put("hire_arrival_ticks", 600.0);
		PARAMS.put("hire_reserve", 150.0);
		PARAMS.put("hire_cost_per_head", 4.0);
		PARAMS.put("company_size", 8.0);
		PARAMS.put("max_companies", 3.0);
		PARAMS.put("desertion_share_when_broke", 0.5);
		PARAMS.put("focus_fire_poor", 1.0);
	}

	/** The typed profile (built in {@link #init()}, after the behaviours exist). */
	public static TacticsProfile PROFILE;

	public enum Strategic { ADVANCE, HOLD, HOLD_BACK }

	/** The Chair's current strategic order (set from the Council vote; the default follows the situation). */
	public static @Nullable Strategic chairOrder;

	/** One squad's command state: whose group it is, the order it is carrying out, and a pending order with its delivery tick. */
	public static final class Group {
		public Magnate magnate;
		public Strategic current = Strategic.HOLD;
		public @Nullable Strategic pending;
		public long applyAt;
		public boolean followedOwn;
		/** Game time of the squad's last decision (s-heapleak: lets {@link #track} drop squads that are gone). */
		public long seen;
	}

	/** s-heapleak (3.0 heap pass): GROUPS is pruned of squads idle this long once it holds more than GROUP_CAP (no growth over long sessions). */
	public static final int GROUP_CAP = 256, GROUP_IDLE_TICKS = 6000;

	public static final Map<UUID, Group> GROUPS = new HashMap<>();
	public static final List<Pending> HIRES = new ArrayList<>();
	public static long ordersApplied, ordersDelayed, ordersOwnLeaning, hiresRequested, hiresArrived, desertersMade;
	public static long lastHire = -100000;

	/** A company on its way: where it will appear and when. */
	public record Pending(ServerLevel level, Vec3 pos, long arriveAt, int heads) {}

	public static @Nullable MinecraftServer SERVER;

	public static void init() {
		if (PROFILE != null) return;
		List<Behaviour> lib = new ArrayList<>();
		for (Behaviour b : List.of(Behaviours.ENGAGE_DEFAULT, Behaviours.SHIELD_WALL, Behaviours.REFUSE_FIGHT, Behaviours.FALL_BACK_REGROUP, Behaviours.BACK_OFF_CREEPER,
			Behaviours.HIGH_GROUND, Behaviours.CALL_HELP, Behaviours.PICKET, Behaviours.PROTECT_UNIT)) lib.add(b);
		PROFILE = SimpleTacticsProfile.builder(MirewickContent.FACTION)
			.interval(PARAMS.get("decision_interval").intValue()).minOdds(PARAMS.get("min_local_odds")).retreatOdds(PARAMS.get("retreat_odds"))
			.blunder(PARAMS.get("blunder_rate")).reaction(PARAMS.get("reaction_ticks_min").intValue(), PARAMS.get("reaction_ticks_max").intValue())
			.intelNoise(PARAMS.get("intel_noise")).behaviours(lib).behaviour(SPLIT_COMMAND).behaviour(HIRE_ANSWER).behaviour(CROSSBOW_VOLLEY).build();
		KingdomApi.registerTactics(MirewickContent.FACTION, PROFILE);
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> SERVER = srv);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			SERVER = null;
			reset();
		});
		// the money side: hired companies arrive, and a broke treasury sends half of each company home
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 20 != 7) return;
			ServerLevel level = srv.overworld();
			arrivals(level, level.getGameTime());
			if (money(srv) <= 0) desertion(level);
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 5 != 0 || desertersMade == 0) return;
			for (ServerLevel l : srv.getAllLevels()) for (KingdomSoldier s : KingdomPerf.loaded(l)) tickDeserter(s);
		});
	}

	// ---- the behaviours (pure decision code over Perception; the only state is the command-group table and the hire queue) -----------

	public static final Behaviour SPLIT_COMMAND = new Behaviour() {
		public String id() { return "split_command"; }
		public Types.Bucket bucket() { return Types.Bucket.ENGAGE; }
		public double cost() { return 0.05; }
		public int minCommitTicks() { return 20; }
		public double score(Perception p, SquadMemory m, SquadBrain b) {
			return Behaviours.targets(p).isEmpty() ? 0 : 0.55;
		}
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> t = Behaviours.targets(p);
			Strategic cur = commandOrder(b.squadId, !t.isEmpty(), p.tick(), RandomSource.create(b.squadId.getLeastSignificantBits() ^ p.tick()));
			Map<UUID, Order> out = new HashMap<>();
			Perception.Contact near = Behaviours.nearest(p, t, p.squadCentre());
			for (Perception.Friendly f : p.friends()) {
				switch (cur) {
					case ADVANCE -> out.put(f.id(), Behaviours.o(Types.OrderKind.ADVANCE, near == null ? null : near.lastSeen(), near == null ? null : near.id()));
					case HOLD -> out.put(f.id(), Behaviours.o(f.shield() ? Types.OrderKind.SHIELD_UP : Types.OrderKind.HOLD, p.squadCentre(), null));
					case HOLD_BACK -> out.put(f.id(), Behaviours.o(Types.OrderKind.FALL_BACK, Behaviours.rally(p, b), null));
				}
			}
			return Order.Orders.of(out, null);
		}
	};

	public static final Behaviour HIRE_ANSWER = new Behaviour() {
		public String id() { return "hire_answer"; }
		public Types.Bucket bucket() { return Types.Bucket.REACT; }
		public double cost() { return 0.05; }
		public int cooldownTicks() { return 200; }
		public int minCommitTicks() { return 40; }
		public double score(Perception p, SquadMemory m, SquadBrain b) {
			MinecraftServer srv = SERVER;
			if (srv == null || p.odds().theirs() <= 0 || p.odds().ratio() >= 1.0) return 0;
			if (p.tick() - lastHire < 200 || money(srv) <= PARAMS.get("hire_reserve") || companiesOut(srv) >= PARAMS.get("max_companies")) return 0;
			return 0.6;
		}
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			MinecraftServer srv = SERVER;
			if (srv != null) requestHire(srv.overworld(), p.squadCentre(), p.tick(), srv.overworld().getRandom());
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), Behaviours.o(f.shield() ? Types.OrderKind.SHIELD_UP : Types.OrderKind.HOLD, p.squadCentre(), null));   // hold while the Council pays
			return Order.Orders.of(out, Signal.HORN_SHORT2);
		}
	};

	public static final Behaviour CROSSBOW_VOLLEY = new Behaviour() {
		public String id() { return "crossbow_volley"; }
		public Types.Bucket bucket() { return Types.Bucket.ENGAGE; }
		public double cost() { return 0.10; }
		public int minCommitTicks() { return 30; }
		public double score(Perception p, SquadMemory m, SquadBrain b) {
			long ranged = p.friends().stream().filter(Perception.Friendly::ranged).count();
			if (ranged < 3) return 0;
			for (Perception.Contact c : Behaviours.targets(p)) if (c.lastSeen().distanceToSqr(p.squadCentre()) < 28 * 28) return 0.65;
			return 0;
		}
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> t = Behaviours.targets(p);
			Vec3 ax = Behaviours.axis(p, t);
			Map<UUID, Order> out = new HashMap<>();
			List<Perception.Friendly> shooters = new ArrayList<>();
			for (Perception.Friendly f : p.friends()) if (f.ranged()) shooters.add(f);
			// crossbows within 6 blocks of each other form a rank and share the nearest target to that rank; everyone else screens
			List<List<Perception.Friendly>> ranks = new ArrayList<>();
			for (Perception.Friendly f : shooters) {
				List<Perception.Friendly> home = null;
				for (List<Perception.Friendly> r : ranks) if (r.get(0).pos().distanceToSqr(f.pos()) <= 36) { home = r; break; }
				if (home == null) ranks.add(home = new ArrayList<>());
				home.add(f);
			}
			for (List<Perception.Friendly> r : ranks) {
				Perception.Contact tgt = Behaviours.nearest(p, t, r.get(0).pos());
				for (Perception.Friendly f : r) out.put(f.id(), Behaviours.o(Types.OrderKind.KEEP_RANGE, tgt == null ? null : tgt.lastSeen(), tgt == null ? null : tgt.id()));
			}
			int i = 0;
			for (Perception.Friendly f : p.friends()) {
				if (f.ranged()) continue;
				out.put(f.id(), Behaviours.o(f.shield() ? Types.OrderKind.SHIELD_UP : Types.OrderKind.HOLD, Behaviours.slot(p.squadCentre(), ax, 4.0, (i++ % 2 == 0 ? 1 : -1) * (1 + i / 2) * 1.5), null));
			}
			volleys++;
			return Order.Orders.of(out, Signal.BANNER_RAISE);
		}
	};

	public static long volleys;

	// ---- split_command state ---------------------------------------------------------------------------------------------------------

	public static Magnate groupOf(UUID squadId) {
		return Magnate.values()[Math.floorMod(squadId.hashCode(), Magnate.values().length)];
	}

	/** The order a Magnate's own leaning produces: hawk advances, dove holds, miser holds back and keeps the troops. */
	public static Strategic ownLeaning(Magnate m) {
		return switch (m.leaning) {
			case HAWK -> Strategic.ADVANCE;
			case DOVE -> Strategic.HOLD;
			case MISER -> Strategic.HOLD_BACK;
		};
	}

	static long councilAt = -100000;
	static Strategic councilCache = Strategic.ADVANCE;

	/** What the Chair orders: a set order, else the Council's WAR vote (passes: advance on contact, otherwise hold), re-tallied every 200 ticks. */
	public static Strategic strategic(boolean enemiesInView, long now) {
		if (chairOrder != null) return chairOrder;
		if (!enemiesInView) return Strategic.HOLD;
		MinecraftServer srv = SERVER;
		if (srv != null && now - councilAt > 200) {
			councilAt = now;
			try {
				councilCache = dev.elsewhere.kingdom_court.api.Oligarchy.tally(srv, MirewickContent.FACTION, dev.elsewhere.kingdom_court.api.Oligarchy.Topic.WAR).passes() ? Strategic.ADVANCE : Strategic.HOLD;
			} catch (RuntimeException e) {
				councilCache = Strategic.ADVANCE;
			}
		}
		return councilCache;
	}

	/**
	 * The squad's command state, created on its first decision. s-heapleak: squads die, despawn and re-form all session, so once more
	 * than {@link #GROUP_CAP} are tracked, the ones without a decision for {@link #GROUP_IDLE_TICKS} are dropped (a squad that comes back
	 * starts a fresh state, as a new squad does). Only runs when the map is over the cap: no per-tick work.
	 */
	public static Group track(UUID squadId, long now) {
		Group g = GROUPS.computeIfAbsent(squadId, k -> {
			Group n = new Group();
			n.magnate = groupOf(k);
			return n;
		});
		g.seen = now;
		if (GROUPS.size() > GROUP_CAP) GROUPS.values().removeIf(x -> now - x.seen > GROUP_IDLE_TICKS);
		return g;
	}

	/** The squad's current strategic order after delivery delay and the disorganisation roll (called once per decision). */
	public static Strategic commandOrder(UUID squadId, boolean enemiesInView, long now, RandomSource r) {
		Group g = track(squadId, now);
		Strategic want = strategic(enemiesInView, now);
		boolean chairGroup = isChairGroup(g.magnate);
		if (g.pending == null && want != g.current) {
			if (chairGroup) {
				g.pending = want;
				g.applyAt = now;                                          // the Chair's own group gets it at once
			} else {
				int delay = 40 + r.nextInt(61);                           // the others hear it 40-100 ticks later
				boolean own = r.nextDouble() < PARAMS.get("disorg");      // and 35 percent follow their own leaning instead
				g.pending = own ? ownLeaning(g.magnate) : want;
				g.followedOwn = own;
				g.applyAt = now + delay;
				ordersDelayed++;
			}
		}
		if (g.pending != null && now >= g.applyAt) {
			g.current = g.pending;
			g.pending = null;
			ordersApplied++;
			if (g.followedOwn) ordersOwnLeaning++;
			g.followedOwn = false;
		}
		return g.current;
	}

	static boolean isChairGroup(Magnate m) {
		try {
			MinecraftServer srv = SERVER;
			String chair = srv == null ? null : dev.elsewhere.kingdom_court.api.Oligarchy.chair(srv, MirewickContent.FACTION);
			return chair == null ? m == Magnate.MARROW : chair.equals(m.courtSlot);
		} catch (RuntimeException e) {
			return m == Magnate.MARROW;
		}
	}

	// ---- hire_answer ------------------------------------------------------------------------------------------------------------

	/** The abstract treasury: the war model's when Mirewick takes part in it, else the module's own record (for a world without the model). */
	public static double money(MinecraftServer server) {
		return WarApi.participates(server, MirewickContent.FACTION) ? WarApi.treasury(server, MirewickContent.FACTION) : MirewickState.get(server).localTreasury;
	}

	public static void addMoney(MinecraftServer server, double delta) {
		if (WarApi.participates(server, MirewickContent.FACTION)) WarApi.addTreasury(server, MirewickContent.FACTION, delta);
		else {
			MirewickState s = MirewickState.get(server);
			s.localTreasury = Math.max(0, s.localTreasury + delta);
			s.setDirty();
		}
	}

	public static int companiesOut(MinecraftServer server) {
		return FreeCompanies.companiesOf(server, MirewickContent.FACTION).size() + HIRES.size();
	}

	/** The Council pays for a Free Company: only over the reserve and under the cap; it arrives 600 ticks later. Returns false when it declines. */
	public static boolean requestHire(ServerLevel level, Vec3 conflict, long game, RandomSource r) {
		MinecraftServer srv = level.getServer();
		int heads = PARAMS.get("company_size").intValue();
		if (money(srv) <= PARAMS.get("hire_reserve")) return false;
		if (companiesOut(srv) >= PARAMS.get("max_companies")) return false;
		// a Counting-House-linked point outside the enemy's sight: 24 blocks from the conflict, on a random bearing
		double ang = r.nextDouble() * Math.PI * 2;
		Vec3 spawn = conflict.add(Math.cos(ang) * 24, 0, Math.sin(ang) * 24);
		HIRES.add(new Pending(level, spawn, game + PARAMS.get("hire_arrival_ticks").longValue(), heads));
		lastHire = game;
		hiresRequested++;
		return true;
	}

	/** Companies whose 600 ticks are up appear: the hire cost is paid now, the soldiers spawn within the caps behind a lantern flare. */
	public static void arrivals(ServerLevel level, long game) {
		for (int i = HIRES.size() - 1; i >= 0; i--) {
			Pending p = HIRES.get(i);
			if (game < p.arriveAt() || p.level() != level) continue;
			HIRES.remove(i);
			arrive(level, p);
		}
	}

	public static @Nullable List<KingdomSoldier> arrive(ServerLevel level, Pending p) {
		MinecraftServer srv = level.getServer();
		double cost = PARAMS.get("hire_cost_per_head") * p.heads();
		if (money(srv) < cost) return null;
		BlockPos at = BlockPos.containing(p.pos());
		at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at);
		boolean modelHire = WarApi.participates(srv, MirewickContent.FACTION) && FreeCompanies.hire(srv, MirewickContent.FACTION, p.heads());
		if (!modelHire) addMoney(srv, -cost);
		hiresArrived++;
		FreeCompanies.refreshStances(srv);
		level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 3.0, at.getZ() + 0.5, 30, 0.4, 1.5, 0.4, 0.02);
		level.playSound(null, at, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.5F, 0.8F);
		List<Identifier> members = new ArrayList<>();
		for (int i = 0; i < p.heads() - 1; i++) members.add(MirewickContent.id(i % 4 == 0 ? "mirewick_fc_crossbow" : i % 4 == 1 ? "mirewick_fc_brute" : i == 6 ? "mirewick_fc_banner" : "mirewick_fc_sellsword"));
		return KingdomApi.spawnSquad(level, at, FC, Squad.Formation.LINE, MirewickContent.id("mirewick_fc_captain"), members);
	}

	// ---- desertion --------------------------------------------------------------------------------------------------------------

	public static boolean isCompanySoldier(KingdomSoldier s) {
		return s.factionId().equals(FC);
	}

	/** At a treasury of 0 half the company walks off the job mid-battle ("No pay, no fight"), the rest flee on morale. Returns how many walked off. */
	public static int desertion(ServerLevel level) {
		int off = 0;
		for (Squad q : KingdomState.get(level).squads()) {
			if (!q.faction().equals(FC) || q.isVirtual()) continue;
			List<KingdomSoldier> ms = new ArrayList<>(q.memberEntities(level));
			LivingEntity lead = q.leaderEntity(level);
			if (lead instanceof KingdomSoldier ls) ms.add(0, ls);
			if (ms.isEmpty() || ms.stream().noneMatch(s -> !s.entityTags().contains("mirewick_deserter"))) continue;
			ms.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
			int walk = (int) Math.ceil(ms.size() * PARAMS.get("desertion_share_when_broke"));
			for (int i = 0; i < ms.size(); i++) {
				KingdomSoldier s = ms.get(i);
				if (s.entityTags().contains("mirewick_deserter") || s.entityTags().contains("mirewick_fled")) continue;
				if (i < walk) {
					s.addTag("mirewick_deserter");
					s.setTarget(null);
					s.getNavigation().stop();
					s.setResting(true);
					desertersMade++;
					off++;
					shout(level, s, "message.elsewhere.mirewick_no_pay");
				} else {
					s.addTag("mirewick_fled");
					s.rout(600);
				}
			}
		}
		if (off > 0) {
			for (WarState.Company c : new ArrayList<>(FreeCompanies.companiesOf(level.getServer(), MirewickContent.FACTION))) FreeCompanies.desert(level.getServer(), c.id, null);
		}
		return off;
	}

	/** Keeps deserters out of the fight: no target, no march. */
	public static void tickDeserter(KingdomSoldier s) {
		if (s.entityTags().contains("mirewick_deserter")) {
			s.setTarget(null);
			if (!s.isResting()) s.setResting(true);
		}
	}

	/** A 2-second shout, shown to players nearby (client text from lang keys). */
	public static void shout(ServerLevel level, KingdomSoldier s, String key) {
		for (ServerPlayer p : level.players()) if (p.distanceToSqr(s) < 40 * 40) p.sendOverlayMessage(Component.translatable(key));
	}

	public static void reset() {
		GROUPS.clear();
		HIRES.clear();
		chairOrder = null;
		ordersApplied = ordersDelayed = ordersOwnLeaning = hiresRequested = hiresArrived = desertersMade = 0;
		lastHire = -100000;
	}

	static boolean canSpawnNear(ServerLevel level, Vec3 pos) {
		return KingdomPerf.canSpawn(level, pos, false) == null;
	}
}
