package dev.elsewhere.rivals.azmar;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Azmar's field-tactics profile (FIELD-TACTICS section 3), built on the war lane's framework. The Sultanate fights by never taking a bad fight and by always having
 * more people at the point of contact: it refuses below 1.5 to 1, breaks contact below 0.85, holds a shield wall on the defence, then strikes the rear once with the
 * Camel Lancers (hammer and anvil), wraps a small or disorganised enemy in a crescent, and baits aggressive chasers into an ambush (feigned retreat).
 * Information is its stat: a falcon in the air adds a {@link FalconIntel} source (its contacts keep their own age and are never targeted), and the intel noise is read
 * from the brain tuning, so a dead Falconer or Wazir simply raises it (their seats are never refilled; nothing here assumes any official is alive).
 */
public final class AzmarTactics {
	private AzmarTactics() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		List<Behaviour> mine = new ArrayList<>(Behaviours.library());
		mine.add(CRESCENT);
		mine.add(HAMMER_ANVIL);
		mine.add(FEIGNED_RETREAT);
		TacticsProfile profile = SimpleTacticsProfile.builder(AzmarContent.FACTION)
			// the interval equals the faction file's tactics.decision_interval (10), which overrides it at runtime
			.interval(Integer.getInteger("azmar.interval", 10)).minOdds(1.5).retreatOdds(0.85).reaction(6, 14).blunder(0.03).mobPolicy("careful")
			.signals(new TacticsProfile.SignalSet(true, true, 0.5))
			.intel(Perception.Filter.ALL)
			.behaviours(mine)
			.role(UnitType.Role.ARCHER, new TacticsProfile.RoleSpec(12, true, 0.3))
			.role(UnitType.Role.CROSSBOW, new TacticsProfile.RoleSpec(11, true, 0.3))
			.role(UnitType.Role.MELEE, new TacticsProfile.RoleSpec(0, false, 0.6))
			.cheese(PILLAR).cheese(BOAT).cheese(WATER).cheese(PEARL)
			.onSquadCreated(b -> b.intel.add(new FalconIntel(b)))
			.build();
		KingdomApi.registerTactics(profile.faction(), profile);
	}

	// ---- helpers --------------------------------------------------------------------------------------------------------------

	static List<Perception.Contact> enemies(Perception p) {
		return Behaviours.targets(p);
	}

	static Vec3 centroid(List<Perception.Contact> l) {
		Vec3 c = Vec3.ZERO;
		for (Perception.Contact x : l) c = c.add(x.lastSeen());
		return l.isEmpty() ? Vec3.ZERO : c.scale(1.0 / l.size());
	}

	/** Nearest enemy distance to any friend that is not a hammer (the wall's contact). */
	static double wallContact(Perception p, SquadBrain b, List<Perception.Contact> en) {
		double best = Double.MAX_VALUE;
		for (Perception.Friendly f : p.friends()) {
			if (b.roleOf(f.id()) == Types.TacticalRole.HAMMER) continue;
			for (Perception.Contact c : en) best = Math.min(best, f.pos().distanceTo(c.lastSeen()));
		}
		return best;
	}

	// ---- behaviours -----------------------------------------------------------------------------------------------------------

	/** Crescent: odds 2 to 1 or better against a small enemy in the open; the ends advance faster and wrap to a 120 degree arc, archers fire from inside, a gap is left at the back. */
	public static final Behaviour CRESCENT = new Behaviour() {
		public String id() { return "crescent"; }
		public Types.Bucket bucket() { return Types.Bucket.ENGAGE; }
		public double cost() { return 0.25; }
		public int minCommitTicks() { return 80; }
		public double score(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> en = enemies(p);
			if (en.isEmpty() || p.terrain().confinedContact() || p.terrain().water()) return 0;
			double need = Math.max(2.0, b.minRatio("crescent"));
			if (p.odds().ratio() < need || en.size() > Math.max(1, p.friends().size() * 0.4 + 1)) return 0;
			return 0.75 * b.weight("crescent");
		}
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			Vec3 ax = Behaviours.axis(p, enemies(p));
			Map<UUID, Order> out = new HashMap<>();
			int n = p.friends().size(), i = 0;
			for (Perception.Friendly f : p.friends()) {
				double t = n <= 1 ? 0 : (i++ / (double) (n - 1)) * 2 - 1;            // -1 .. 1 across the line
				double lateral = t * 7.0;
				double forward = 4.0 + Math.abs(t) * 6.0;                             // the ends reach further: a curve
				Types.TacticalRole r = b.roleOf(f.id());
				if (r == Types.TacticalRole.SKIRMISHER) out.put(f.id(), Behaviours.o(Types.OrderKind.KEEP_RANGE, Behaviours.slot(p.squadCentre(), ax, 1.0, lateral * 0.4), null));
				else if (r == Types.TacticalRole.HAMMER) out.put(f.id(), Behaviours.o(Types.OrderKind.HOLD, Behaviours.slot(p.squadCentre(), ax, -8.0, lateral), null));
				else out.put(f.id(), Behaviours.o(Math.abs(t) > 0.6 ? Types.OrderKind.FLANK : Types.OrderKind.ADVANCE, Behaviours.slot(p.squadCentre(), ax, forward, lateral), null));
			}
			return Order.Orders.of(out, Signal.HORN_LONG);
		}
	};

	/** Hammer and anvil: the wall holds 60 ticks, then once, on the banner, the Lancers circle wide and hit the rear. One pass, then back to the wall's flank. */
	public static final Behaviour HAMMER_ANVIL = new Behaviour() {
		public String id() { return "hammer_anvil"; }
		public Types.Bucket bucket() { return Types.Bucket.ENGAGE; }
		public double cost() { return 0.25; }
		public int cooldownTicks() { return 1200; }
		public int minCommitTicks() { return 100; }
		public int orderTicks() { return 100; }
		public double score(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> en = enemies(p);
			if (en.isEmpty() || p.odds().ratio() < b.minOdds() * 0.8) return 0;
			boolean hammer = p.friends().stream().anyMatch(f -> b.roleOf(f.id()) == Types.TacticalRole.HAMMER);
			if (!hammer || wallContact(p, b, en) > 6.0) return 0;                  // the anvil must be engaged
			if (p.tick() - m.encounterStart < 60 && m.encounterStart >= 0) return 0;  // holds 60+ ticks first
			return 0.85 * b.weight("hammer_anvil");
		}
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> en = enemies(p);
			Vec3 ax = Behaviours.axis(p, en), ec = centroid(en);
			Vec3 right = new Vec3(-ax.z, 0, ax.x);
			Map<UUID, Order> out = new HashMap<>();
			int k = 0;
			for (Perception.Friendly f : p.friends()) {
				Types.TacticalRole r = b.roleOf(f.id());
				if (r == Types.TacticalRole.HAMMER) {
					Vec3 rear = ec.add(ax.scale(10.0)).add(right.scale((k++ % 2 == 0 ? 1 : -1) * 18.0 / (1 + k / 2)));   // 18 blocks wide round to the rear
					out.put(f.id(), Behaviours.o(Types.OrderKind.CHARGE, rear, null));
				} else if (r == Types.TacticalRole.SHIELD || (r == Types.TacticalRole.ANVIL && f.shield())) out.put(f.id(), Behaviours.o(Types.OrderKind.SHIELD_UP, null, null));
				else out.put(f.id(), Behaviours.o(Types.OrderKind.HOLD, null, null));
			}
			return Order.Orders.of(out, Signal.BANNER_RAISE);
		}
	};

	/** Feigned retreat: against several aggressive chasers a bait of at most 3 runs toward the squad's hidden main body; the rest hold out of sight until they are strung out. */
	public static final Behaviour FEIGNED_RETREAT = new Behaviour() {
		public String id() { return "feigned_retreat"; }
		public Types.Bucket bucket() { return Types.Bucket.ENGAGE; }
		public double cost() { return 0.30; }
		public int cooldownTicks() { return 2400; }
		public int minCommitTicks() { return 120; }
		public int orderTicks() { return 140; }
		public double score(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> en = enemies(p).stream().filter(c -> c.kind() == Types.ContactKind.KINGDOM_SOLDIER).toList();
			if (en.size() < 3 || p.odds().ratio() < b.minOdds() || p.friends().size() < 5) return 0;
			double near = Double.MAX_VALUE, far = 0;
			Vec3 c = p.squadCentre();
			for (Perception.Contact x : en) { double d = x.lastSeen().distanceTo(c); near = Math.min(near, d); far = Math.max(far, d); }
			return near > 12 && near < 30 ? 0.6 * b.weight("feigned_retreat") : 0;       // they are coming, not yet on us
		}
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> en = enemies(p);
			Vec3 ax = Behaviours.axis(p, en);
			Vec3 hide = Behaviours.slot(p.squadCentre(), ax, -16.0, 0), lure = Behaviours.slot(p.squadCentre(), ax, -22.0, 4.0);
			Map<UUID, Order> out = new HashMap<>();
			int bait = 0;
			for (Perception.Friendly f : p.friends()) {
				Types.TacticalRole r = b.roleOf(f.id());
				if (bait < 3 && (r == Types.TacticalRole.ANVIL || r == Types.TacticalRole.SCREEN || r == Types.TacticalRole.HAMMER) && !f.ranged()) {
					bait++;
					out.put(f.id(), Behaviours.o(Types.OrderKind.LURE, lure, null));
				} else out.put(f.id(), Behaviours.o(Types.OrderKind.MOVE_TO_SLOT, hide, null));
			}
			return Order.Orders.of(out, null);
		}
	};

	// ---- cheese answers (FIELD-TACTICS 3.4) -----------------------------------------------------------------------------------

	static Order.Orders ring(Perception p, SquadBrain b, Vec3 around, double radius, Types.OrderKind kind) {
		Map<UUID, Order> out = new HashMap<>();
		int n = Math.max(1, p.friends().size()), i = 0;
		for (Perception.Friendly f : p.friends()) {
			double a = i++ * Math.PI * 2 / n;
			out.put(f.id(), Behaviours.o(kind, around.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), null));
		}
		return Order.Orders.of(out, null);
	}

	static Perception.Contact player(Perception p, java.util.function.Predicate<Perception.Contact> test) {
		for (Perception.Contact c : p.contacts()) if (c.visibleNow() && c.kind() == Types.ContactKind.PLAYER && test.test(c)) return c;
		return null;
	}

	/** Pillaring: after three uses the squad holds a wide ring at 20 blocks and waits; it never digs or builds. */
	static final TacticsProfile.CheeseCounter PILLAR = new TacticsProfile.CheeseCounter() {
		public String id() { return "pillar"; }
		public boolean detect(Perception p) { return player(p, c -> c.lastSeen().y > p.squadCentre().y + 3 && c.onHighGround()) != null; }
		public Order.Orders answer(Perception p, SquadMemory m, SquadBrain b, int uses) {
			var c = player(p, x -> true);
			return ring(p, b, c == null ? p.squadCentre() : c.lastSeen(), 20.0, Types.OrderKind.HOLD);
		}
	};

	/** Boats: archers keep the shore; melee and Lancers never enter water. */
	static final TacticsProfile.CheeseCounter BOAT = new TacticsProfile.CheeseCounter() {
		public String id() { return "boat"; }
		public boolean detect(Perception p) { return player(p, Perception.Contact::inBoat) != null; }
		public Order.Orders answer(Perception p, SquadMemory m, SquadBrain b, int uses) {
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), Behaviours.o(f.ranged() ? Types.OrderKind.KEEP_RANGE : Types.OrderKind.HOLD, null, null));
			return Order.Orders.of(out, null);
		}
	};

	/** Standing in water: melee does not swim in, archers stay ashore, the squad waits. */
	static final TacticsProfile.CheeseCounter WATER = new TacticsProfile.CheeseCounter() {
		public String id() { return "water"; }
		public boolean detect(Perception p) { return player(p, c -> c.inWater() && !c.inBoat()) != null; }
		public Order.Orders answer(Perception p, SquadMemory m, SquadBrain b, int uses) {
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), Behaviours.o(Types.OrderKind.HOLD, null, null));
			return Order.Orders.of(out, null);
		}
	};

	/** Ender pearls: on the third use the squad stands in a tight ring with shields facing outward. */
	static final TacticsProfile.CheeseCounter PEARL = new TacticsProfile.CheeseCounter() {
		public String id() { return "pearl"; }
		public boolean detect(Perception p) { return player(p, c -> c.loadout() != null && c.loadout().pearlInHand()) != null; }
		public Order.Orders answer(Perception p, SquadMemory m, SquadBrain b, int uses) {
			return ring(p, b, p.squadCentre(), 2.5, Types.OrderKind.SHIELD_UP);
		}
	};

	// ---- falcon intel ---------------------------------------------------------------------------------------------------------

	/**
	 * What a falcon in the air near the squad sees: players, hostile soldiers, reported with an age of 20 ticks and never as {@code visibleNow} (intel is for odds and
	 * for searching, never for targeting). Empty when no falcon of the Falconer lives (the seat is never refilled), so a dead Falconer costs the intel without errors.
	 */
	public static final class FalconIntel implements Perception.IntelSource {
		private final SquadBrain brain;
		private long cachedAt = -1000;
		private List<Perception.Contact> cache = List.of();

		public FalconIntel(SquadBrain brain) {
			this.brain = brain;
		}

		public String id() { return "falcon_azmar"; }

		public List<Perception.Contact> contacts(long now) {
			if (now - cachedAt < 20) return cache;          // one scan per 20 ticks per squad
			cachedAt = now;
			Perception last = brain.last;
			if (last == null || AzmarFalcon.ALL.isEmpty()) return cache = List.of();
			Vec3 centre = last.squadCentre();
			List<Perception.Contact> out = new ArrayList<>();
			for (AzmarFalcon f : AzmarFalcon.ALL) {
				if (!f.isAlive() || f.distanceToSqr(centre) > 48 * 48 || !(f.level() instanceof net.minecraft.server.level.ServerLevel level)) continue;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, f.getBoundingBox().inflate(32))) {
					Types.ContactKind kind;
					double threat;
					if (e instanceof Player pl && !pl.isSpectator() && !pl.isCreative()) { kind = Types.ContactKind.PLAYER; threat = 2.0; }
					else if (e instanceof KingdomSoldier s && Factions.hostile(AzmarContent.FACTION, s.factionId())) { kind = Types.ContactKind.KINGDOM_SOLDIER; threat = 1.0; }
					else continue;
					if (!f.hasLineOfSight(e)) continue;                                // a falcon cannot see through walls either
					out.add(new Perception.Contact(e.getUUID(), kind, e.position(), 20, false, null, threat, e.isInWater(), e.getY() > centre.y + 2.5, e.getType().toShortString(), false,
						e.getHealth() / Math.max(1F, e.getMaxHealth()), false, false, false, false, false));
					if (out.size() >= 12) break;
				}
			}
			return cache = out;
		}
	}
}
