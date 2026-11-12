package dev.elsewhere.kingdom.tactics;

import dev.elsewhere.kingdom.tactics.Order.Orders;
import dev.elsewhere.kingdom.tactics.Types.Bucket;
import dev.elsewhere.kingdom.tactics.Types.OrderKind;
import dev.elsewhere.kingdom.tactics.Types.TacticalRole;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The shared behaviour library (FIELD-TACTICS 1.5): {@code engage_default}, {@code shield_wall}, {@code kite_melee}, {@code focus_fire}, {@code refuse_fight},
 * {@code fall_back_regroup}, {@code back_off_creeper}, {@code break_los}, {@code high_ground}, {@code call_help}, {@code picket}, {@code protect_unit}, {@code corner_trap} and
 * {@code counter_cheese} (which runs the profile's {@link TacticsProfile.CheeseCounter}s). A profile lists the ones it owns; {@link #library()} is the lot. Each plans only from a
 * {@link Perception} (visible contacts, never ghosts) and returns orders; none can widen who may be attacked.
 */
public final class Behaviours {
	private Behaviours() {}

	public static List<Perception.Contact> visible(Perception p) {
		return p.contacts().stream().filter(Perception.Contact::visibleNow).toList();
	}

	/** Contacts a soldier may fight: visible, not a hazard type (creeper, warden, enderman are only avoided). */
	public static List<Perception.Contact> targets(Perception p) {
		return p.contacts().stream().filter(c -> c.visibleNow() && !hazard(c)).toList();
	}

	public static boolean hazard(Perception.Contact c) {
		return c.type().equals("creeper") || c.type().equals("warden") || c.type().equals("enderman");
	}

	public static Perception.@Nullable Contact nearest(Perception p, List<Perception.Contact> l, Vec3 from) {
		Perception.Contact best = null;
		double bd = Double.MAX_VALUE;
		for (Perception.Contact c : l) {
			double d = c.lastSeen().distanceToSqr(from);
			if (d < bd) {
				bd = d;
				best = c;
			}
		}
		return best;
	}

	public static Vec3 axis(Perception p, List<Perception.Contact> t) {
		if (t.isEmpty()) return new Vec3(0, 0, 1);
		Vec3 c = Vec3.ZERO;
		for (Perception.Contact x : t) c = c.add(x.lastSeen());
		c = c.scale(1.0 / t.size()).subtract(p.squadCentre());
		Vec3 h = new Vec3(c.x, 0, c.z);
		return h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	public static Order o(OrderKind k, @Nullable Vec3 pos, @Nullable UUID target) {
		return new Order(k, pos, target, 0, 0, 0);
	}

	public static Vec3 slot(Vec3 centre, Vec3 axis, double forward, double lateral) {
		Vec3 right = new Vec3(-axis.z, 0, axis.x);
		return centre.add(axis.scale(forward)).add(right.scale(lateral));
	}

	public static Vec3 rally(Perception p, SquadBrain b) {
		if (b.memory.rallyPoint != null) return b.memory.rallyPoint;
		Vec3 ax = axis(p, targets(p));
		return p.squadCentre().subtract(ax.scale(28));
	}

	public static double priority(Perception.Contact c, Vec3 from) {
		double base = c.commander() ? 4 : (c.ranged() ? 3 : c.banner() ? 3 : c.kind() == Types.ContactKind.HOSTILE_MOB ? 1.5 : 2);
		return base + 1.5 * (1 - c.hpFraction()) + 1.0 * Math.max(0, 1 - Math.sqrt(c.lastSeen().distanceToSqr(from)) / 24) + 0.5 * c.estThreat() + (c.shielded() ? 0 : 0.3);
	}

	/** Baseline behaviour: the 2.0 goals run (no orders). */
	public static final Behaviour ENGAGE_DEFAULT = new Base("engage_default", Bucket.ENGAGE, 0.05, 0.3) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			return targets(p).isEmpty() ? 0 : 0.3;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			return Orders.NONE;
		}
	};

	public static final Behaviour SHIELD_WALL = new Base("shield_wall", Bucket.ENGAGE, 0.10, 0.6) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			long shields = p.friends().stream().filter(Perception.Friendly::shield).count();
			boolean threat = targets(p).stream().anyMatch(c -> c.ranged() || c.lastSeen().distanceToSqr(p.squadCentre()) < 20 * 20);
			return shields >= 3 && threat && (p.odds().ratio() >= b.minOdds() || p.odds().theirs() <= 0) ? 0.7 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Vec3 ax = axis(p, targets(p));
			Map<UUID, Order> out = new HashMap<>();
			int i = 0, j = 0;
			for (Perception.Friendly f : p.friends()) {
				TacticalRole r = b.roleOf(f.id());
				if (r == TacticalRole.SHIELD || (r == TacticalRole.ANVIL && f.shield())) out.put(f.id(), o(OrderKind.SHIELD_UP, slot(p.squadCentre(), ax, 4.0, (i++ % 2 == 0 ? 1 : -1) * (1 + i / 2) * 1.5), null));
				else if (r == TacticalRole.SKIRMISHER) out.put(f.id(), o(OrderKind.MOVE_TO_SLOT, slot(p.squadCentre(), ax, 0.5, (j++ % 2 == 0 ? 1 : -1) * (1 + j / 2) * 1.5), null));
				else if (r != TacticalRole.HAMMER) out.put(f.id(), o(OrderKind.HOLD, slot(p.squadCentre(), ax, 3.0, (i++ % 2 == 0 ? 1 : -1) * (1 + i / 2) * 1.5), null));
			}
			return Orders.of(out, p.contacts().size() > 3 ? Signal.BANNER_RAISE : null);
		}
	};

	public static final Behaviour KITE_MELEE = new Base("kite_melee", Bucket.ENGAGE, 0.15, 0.8) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> t = targets(p);
			if (t.size() != 1 || t.get(0).ranged() || t.get(0).kind() == Types.ContactKind.KINGDOM_SOLDIER && false) return 0;
			Perception.Contact c = t.get(0);
			boolean archerNear = p.friends().stream().anyMatch(f -> f.ranged() && f.pos().distanceToSqr(c.lastSeen()) < 36);
			return archerNear ? 0.8 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Perception.Contact c = targets(p).get(0);
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) {
				if (f.ranged()) out.put(f.id(), o(OrderKind.KEEP_RANGE, c.lastSeen(), c.id()));
				else out.put(f.id(), o(OrderKind.FOCUS, c.lastSeen(), c.id()));
			}
			m.saw("kite", p.tick());
			return Orders.of(out, null);
		}
	};

	public static final Behaviour FOCUS_FIRE = new Base("focus_fire", Bucket.ENGAGE, 0.10, 0.5) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			return targets(p).size() >= 1 && p.friends().size() >= 2 && (p.odds().ratio() >= b.minOdds() || p.odds().theirs() <= 0) ? 0.5 : 0;
		}

		@Override int minCommit() {
			return 40;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Perception.Contact best = null;
			double bs = -1;
			for (Perception.Contact c : targets(p)) {
				if (c.lastSeen().distanceToSqr(p.squadCentre()) > 16 * 16) continue;
				double s = priority(c, p.squadCentre());
				if (s > bs) {
					bs = s;
					best = c;
				}
			}
			if (best == null) return Orders.NONE;
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), o(OrderKind.FOCUS, best.lastSeen(), best.id()));
			return Orders.of(out, null);
		}
	};

	public static final Behaviour REFUSE_FIGHT = new Base("refuse_fight", Bucket.REACT, 0.10, 0.8) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			List<Perception.Contact> t = targets(p);
			return !t.isEmpty() && p.odds().theirs() > 0 && p.odds().ratio() < b.minOdds() && p.odds().ratio() >= b.retreatOdds() ? 0.8 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Vec3 rp = rally(p, b);
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), o(f.ranged() ? OrderKind.SKIRMISH : OrderKind.WITHDRAW, rp, null));
			return new Orders(out, Signal.HORN_SHORT2, new Order.SquadFlags(true, false));
		}
	};

	public static final Behaviour FALL_BACK_REGROUP = new Base("fall_back_regroup", Bucket.SURVIVE, 0.05, 0.9) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			boolean low = b.squad.morale() < 30;
			boolean odds = b.retreatOdds() > 0 && p.odds().theirs() > 0 && p.odds().ratio() < b.retreatOdds();
			return !targets(p).isEmpty() && (low || odds) ? 0.9 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Vec3 rp = rally(p, b);
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), o(OrderKind.WITHDRAW, rp, null));
			return new Orders(out, Signal.HORN_SHORT2, new Order.SquadFlags(true, false));
		}

		@Override public int orderTicks() {
			return 120;
		}
	};

	public static final Behaviour BACK_OFF_CREEPER = new Base("back_off_creeper", Bucket.SURVIVE, 0.05, 1.0) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			if (!b.mobPolicy().equals("careful")) return 0;
			for (Perception.Contact c : visible(p)) {
				if (!c.type().equals("creeper")) continue;
				for (Perception.Friendly f : p.friends()) if (f.pos().distanceToSqr(c.lastSeen()) < (c.primed() ? 8 * 8 : 7 * 7)) return 1.0;
			}
			return 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Perception.Contact cr = null;
			for (Perception.Contact c : visible(p)) if (c.type().equals("creeper") && (cr == null || c.lastSeen().distanceToSqr(p.squadCentre()) < cr.lastSeen().distanceToSqr(p.squadCentre()))) cr = c;
			if (cr == null) return Orders.NONE;
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) {
				Vec3 away = f.pos().subtract(cr.lastSeen());
				away = new Vec3(away.x, 0, away.z);
				away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
				double d = Math.sqrt(f.pos().distanceToSqr(cr.lastSeen()));
				if (d < 9.5) out.put(f.id(), o(OrderKind.FALL_BACK, cr.lastSeen().add(away.scale(11)), null));
				else if (f.ranged()) out.put(f.id(), o(OrderKind.FOCUS, cr.lastSeen(), cr.id()));
				else out.put(f.id(), o(OrderKind.HOLD, null, null));
			}
			return Orders.of(out, null);
		}
	};

	public static final Behaviour BREAK_LOS = new Base("break_los", Bucket.REACT, 0.10, 0.5) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			boolean shot = targets(p).stream().anyMatch(c -> c.ranged() && c.lastSeen().distanceToSqr(p.squadCentre()) > 10 * 10);
			boolean shielded = p.friends().stream().anyMatch(Perception.Friendly::shield);
			return shot && !shielded && p.friends().size() >= 1 ? 0.5 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Vec3 ax = axis(p, targets(p));
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), o(OrderKind.FALL_BACK, f.pos().subtract(ax.scale(5)), null));
			return Orders.of(out, null);
		}
	};

	public static final Behaviour HIGH_GROUND = new Base("high_ground", Bucket.POSITION, 0.15, 0.4) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			return p.terrain().ledge() != null && !targets(p).isEmpty() && !p.env().thunder() && p.friends().stream().anyMatch(Perception.Friendly::ranged) ? 0.4 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Map<UUID, Order> out = new HashMap<>();
			Vec3 l = Vec3.atBottomCenterOf(p.terrain().ledge());
			for (Perception.Friendly f : p.friends()) if (f.ranged()) out.put(f.id(), o(OrderKind.MOVE_TO_SLOT, l, null));
			return Orders.of(out, null);
		}
	};

	public static final Behaviour CALL_HELP = new Base("call_help", Bucket.REACT, 0.05, 0.6) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			return !targets(p).isEmpty() && p.odds().theirs() > 0 && p.odds().ratio() < b.minOdds() && TacticsRuntime.alliesNear(b, 64).size() > 0 ? 0.6 : 0;
		}

		@Override public int cooldownTicks() {
			return 400;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Perception.Contact c = nearest(p, targets(p), p.squadCentre());
			if (c != null) for (SquadBrain a : TacticsRuntime.alliesNear(b, 64)) a.tell(new Perception.ThreatEvent(p.tick(), "call_help", c.lastSeen(), c.id()));
			return Orders.of(Map.of(), Signal.HORN_SHORT3);
		}
	};

	public static final Behaviour PICKET = new Base("picket", Bucket.IDLE, 0.05, 0.2) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			return targets(p).isEmpty() && p.friends().size() >= 3 ? 0.2 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Map<UUID, Order> out = new HashMap<>();
			int k = 0;
			for (Perception.Friendly f : p.friends()) {
				if (f.leader() || k >= 2) continue;
				double ang = (k++ == 0 ? 0 : Math.PI) + (p.tick() / 6000 % 4) * Math.PI / 2;
				out.put(f.id(), o(OrderKind.PICKET, p.squadCentre().add(Math.cos(ang) * 14, 0, Math.sin(ang) * 14), null));
			}
			return Orders.of(out, null);
		}

		@Override public int orderTicks() {
			return 200;
		}
	};

	public static final Behaviour PROTECT_UNIT = new Base("protect_unit", Bucket.REACT, 0.05, 0.6) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			for (Perception.Friendly f : p.friends()) if ((f.leader() || b.roleOf(f.id()) == TacticalRole.BANNER) && f.hpFraction() < 0.4 && !targets(p).isEmpty()) return 0.6;
			return 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Perception.Friendly v = null;
			for (Perception.Friendly f : p.friends()) if ((f.leader() || b.roleOf(f.id()) == TacticalRole.BANNER) && f.hpFraction() < 0.4) v = f;
			if (v == null) return Orders.NONE;
			Map<UUID, Order> out = new HashMap<>();
			int n = 0;
			Vec3 vp = v.pos();
			for (Perception.Friendly f : p.friends()) {
				if (f.id().equals(v.id()) || n >= 2 || f.ranged()) continue;
				n++;
				out.put(f.id(), o(OrderKind.GUARD_ALLY, vp, v.id()));
			}
			return Orders.of(out, null);
		}
	};

	public static final Behaviour CORNER_TRAP = new Base("corner_trap", Bucket.ENGAGE, 0.20, 0.4) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			return p.terrain().confinedContact() && targets(p).size() == 1 && p.odds().ratio() >= b.minOdds() ? 0.4 : 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			Perception.Contact c = targets(p).get(0);
			Vec3 ax = c.lastSeen().subtract(p.squadCentre());
			ax = new Vec3(ax.x, 0, ax.z).normalize();
			Map<UUID, Order> out = new HashMap<>();
			int i = 0;
			for (Perception.Friendly f : p.friends()) out.put(f.id(), f.shield() ? o(OrderKind.SHIELD_UP, slot(c.lastSeen(), ax, -3.0, (i++ % 2 == 0 ? 1 : -1) * 1.5), null)
				: o(f.ranged() ? OrderKind.SKIRMISH : OrderKind.HOLD, c.lastSeen(), c.id()));
			return Orders.of(out, null);
		}
	};

	/** Runs the profile's cheese counters: the first sighting gets a sane default (hold and do not follow), the third the real counter. */
	public static final Behaviour COUNTER_CHEESE = new Base("counter_cheese", Bucket.REACT, 0.10, 0.7) {
		@Override double s(Perception p, SquadMemory m, SquadBrain b) {
			for (TacticsProfile.CheeseCounter c : b.profile.cheese()) if (m.uses(c.id()) > 0 && c.detect(p)) return 0.7;
			return 0;
		}

		@Override Orders pl(Perception p, SquadMemory m, SquadBrain b) {
			for (TacticsProfile.CheeseCounter c : b.profile.cheese()) {
				if (m.uses(c.id()) > 0 && c.detect(p)) {
					int uses = m.uses(c.id());
					if (uses >= 3) return c.answer(p, m, b, uses);
					Map<UUID, Order> out = new HashMap<>();
					for (Perception.Friendly f : p.friends()) out.put(f.id(), o(OrderKind.HOLD, null, null));
					return Orders.of(out, null);
				}
			}
			return Orders.NONE;
		}
	};

	/** Every shared behaviour (a profile may take all or pick). */
	public static List<Behaviour> library() {
		return List.of(ENGAGE_DEFAULT, SHIELD_WALL, KITE_MELEE, FOCUS_FIRE, REFUSE_FIGHT, FALL_BACK_REGROUP, BACK_OFF_CREEPER, BREAK_LOS, HIGH_GROUND, CALL_HELP, PICKET, PROTECT_UNIT, CORNER_TRAP,
			COUNTER_CHEESE);
	}

	abstract static class Base implements Behaviour {
		private final String id;
		private final Bucket bucket;
		private final double cost, nominal;

		Base(String id, Bucket bucket, double cost, double nominal) {
			this.id = id;
			this.bucket = bucket;
			this.cost = cost;
			this.nominal = nominal;
		}

		abstract double s(Perception p, SquadMemory m, SquadBrain b);

		abstract Orders pl(Perception p, SquadMemory m, SquadBrain b);

		int minCommit() {
			return 20;
		}

		@Override public String id() { return id; }
		@Override public Bucket bucket() { return bucket; }
		@Override public double cost() { return cost; }
		@Override public int minCommitTicks() { return minCommit(); }
		@Override public double score(Perception p, SquadMemory m, SquadBrain b) { return s(p, m, b); }
		@Override public Orders plan(Perception p, SquadMemory m, SquadBrain b) { return pl(p, m, b); }
	}
}
