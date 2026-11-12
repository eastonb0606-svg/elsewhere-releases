package dev.elsewhere.rivals.hrafnholt.tactics;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
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
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hrafnholt's field-tactics profile (FIELD-TACTICS section 4: aggressive, impatient, brutal) on the war lane's typed framework
 * ({@code dev.elsewhere.kingdom.tactics}). Hit first, hit hard, never break off: decision interval 15, minimum local odds 1.0, retreat odds 0
 * (the only retreat is the framework's morale rout), blunder 0.10, reaction 8 to 18 ticks, intelligence noise 0.45, mob policy "default".
 * <ul>
 * <li>{@code berserker_screen}: when a charge begins the Berserkers run ahead, the line follows one decision later, a long horn bellows;</li>
 * <li>{@code charge} and {@code wolf_flank}: every melee soldier charges the nearest contact (archers shoot until contact); with a Wolf-Handler the handler holds the front and its wolves circle to the flank ({@link dev.elsewhere.rivals.hrafnholt.unit.WolfHandler});</li>
 * <li>{@code chase_routed}: routed soldiers and fleeing players that fought us are chased while in sight, for at most 48 blocks from where the chase began;</li>
 * <li>{@code poor_patience}: a stalemate of 400 ticks becomes a charge; {@code weak_focus}: 30 percent of the melee pick the weakest contact;</li>
 * <li>{@code night_raid}: at night the patrol closes up into one noisy group; {@code intimidate}: a squad the villages lane marks stands in the square with banners up ({@link #intimidate});</li>
 * <li>{@code shield_bash} is a soldier ability run by {@link #tick}: an 8-tick wind-up, a shove of 2 blocks that drops the guard for 10 ticks, cooldown 120;</li>
 * <li>the framework's own {@code engage_default} and {@code fall_back_regroup} (only when morale routs the squad).</li>
 * </ul>
 */
public final class HrafnholtTactics {
	private HrafnholtTactics() {}

	public static final Identifier FACTION = HrafnholtContent.FACTION;
	public static final int CHASE_BLOCKS = 48, CHASE_SIGHT_TICKS = 60, STALEMATE_TICKS = 400, BASH_WINDUP = 8, BASH_COOLDOWN = 120, BASH_GUARD_DROP = 10;
	public static TacticsProfile PROFILE;
	static @Nullable MinecraftServer server;
	/** Test counters. */
	public static long bashes, chaseOrders, screens, charges, withdrawOrders;

	// ---- state ---------------------------------------------------------------------------------------------------------------------

	/** Where a squad's current chase began and the last tick it saw its quarry. */
	record Chase(Vec3 start, long lastSight) {}

	static final Map<UUID, Chase> CHASES = new HashMap<>();
	/** Players that fought Hrafnholt soldiers recently (player id to tick): a player who runs after this is chased. */
	static final Map<UUID, Long> FOUGHT = new HashMap<>();
	/** Squads the villages lane marked as intimidating a village: squad id to {centre, expiry tick}. */
	static final Map<UUID, Object[]> INTIMIDATE = new HashMap<>();
	static final Map<UUID, long[]> BASH = new HashMap<>();   // soldier id -> {windup fire tick, ready tick}

	/** The villages lane marks a squad as standing in a village square (siege target, no live player): it holds a ring with banners up and no one is harmed. */
	public static void intimidate(UUID squadId, Vec3 centre, int ticks, long now) {
		INTIMIDATE.put(squadId, new Object[] {centre, now + ticks});
	}

	public static boolean intimidating(UUID squadId, long now) {
		Object[] o = INTIMIDATE.get(squadId);
		return o != null && (long) o[1] > now;
	}

	// ---- registration ----------------------------------------------------------------------------------------------------------------

	public static void init() {
		List<Behaviour> list = new ArrayList<>();
		list.add(Behaviours.ENGAGE_DEFAULT);
		list.add(Behaviours.FALL_BACK_REGROUP);
		list.add(BERSERKER_SCREEN);
		list.add(CHARGE);
		list.add(WOLF_FLANK);
		list.add(CHASE_ROUTED);
		list.add(POOR_PATIENCE);
		list.add(WEAK_FOCUS);
		list.add(NIGHT_RAID);
		list.add(INTIMIDATE_B);
		PROFILE = SimpleTacticsProfile.builder(FACTION).interval(15).minOdds(1.0).retreatOdds(0.0).reaction(8, 18).blunder(0.10).mobPolicy("default").intelNoise(0.45)
			.signals(new TacticsProfile.SignalSet(true, true, 0.6)).behaviours(list)
			.role(UnitType.Role.MELEE, new TacticsProfile.RoleSpec(0.0, false, 1.0)).build();
		KingdomApi.registerTactics(FACTION, PROFILE);
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			CHASES.clear();
			FOUGHT.clear();
			INTIMIDATE.clear();
			BASH.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> {
			if (e instanceof KingdomSoldier s && FACTION.equals(s.factionId()) && src.getEntity() instanceof Player p) FOUGHT.put(p.getUUID(), e.level().getGameTime());
			else if (e instanceof Player p && src.getEntity() instanceof KingdomSoldier s && FACTION.equals(s.factionId())) FOUGHT.put(p.getUUID(), e.level().getGameTime());
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			int t = srv.getTickCount();
			if (t % 4 == 1) for (ServerLevel l : srv.getAllLevels()) bashTick(l);
			if (t % 40 == 0 && !INTIMIDATE.isEmpty()) intimidateTick(srv);
			if (t % 600 == 0) prune(srv.overworld().getGameTime());
		});
	}

	static void prune(long now) {
		FOUGHT.values().removeIf(t -> now - t > 1200);
		INTIMIDATE.values().removeIf(o -> (long) o[1] <= now);
		if (CHASES.size() > 64) CHASES.clear();
		if (BASH.size() > 256) BASH.clear();
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------------------------

	static @Nullable Entity entity(UUID id) {
		if (server == null) return null;
		for (ServerLevel l : server.getAllLevels()) {
			Entity e = l.getEntity(id);
			if (e != null) return e;
		}
		return null;
	}

	static boolean isUnit(UUID id, String path) {
		return entity(id) instanceof KingdomSoldier s && s.unitId().getPath().equals(path);
	}

	static boolean hasUnit(Perception p, String path) {
		for (Perception.Friendly f : p.friends()) if (isUnit(f.id(), path)) return true;
		return false;
	}

	/** Visible, fightable contacts (never creepers and the like) within {@code r} blocks of the squad. */
	static List<Perception.Contact> near(Perception p, double r) {
		List<Perception.Contact> out = new ArrayList<>();
		for (Perception.Contact c : Behaviours.targets(p)) if (c.lastSeen().distanceToSqr(p.squadCentre()) <= r * r && c.kind() != Types.ContactKind.PASSIVE && c.kind() != Types.ContactKind.PET) out.add(c);
		return out;
	}

	static Order.Orders allCharge(Perception p, Perception.Contact c, boolean holdHandlers) {
		Map<UUID, Order> out = new HashMap<>();
		for (Perception.Friendly f : p.friends()) {
			if (f.ranged()) out.put(f.id(), Behaviours.o(Types.OrderKind.FOCUS, c.lastSeen(), c.id()));
			else if (holdHandlers && isUnit(f.id(), "hrafnholt_wolf_handler")) out.put(f.id(), Behaviours.o(Types.OrderKind.FOCUS, c.lastSeen(), c.id()));
			else out.put(f.id(), Behaviours.o(Types.OrderKind.CHARGE, c.lastSeen(), c.id()));
		}
		return Order.Orders.of(out, null);
	}

	/** A behaviour as data: id, bucket, cost; the two lambdas are the score and the plan. */
	static final class B implements Behaviour {
		interface Score { double of(Perception p, SquadMemory m, SquadBrain b); }
		interface Plan { Order.Orders of(Perception p, SquadMemory m, SquadBrain b); }
		final String id;
		final Types.Bucket bucket;
		final double cost;
		final int commit, orderTicks, cooldown;
		final Score score;
		final Plan plan;

		B(String id, Types.Bucket bucket, double cost, int commit, int orderTicks, int cooldown, Score score, Plan plan) {
			this.id = id;
			this.bucket = bucket;
			this.cost = cost;
			this.commit = commit;
			this.orderTicks = orderTicks;
			this.cooldown = cooldown;
			this.score = score;
			this.plan = plan;
		}

		public String id() { return id; }
		public Types.Bucket bucket() { return bucket; }
		public double cost() { return cost; }
		public int minCommitTicks() { return commit; }
		public int orderTicks() { return orderTicks; }
		public int cooldownTicks() { return cooldown; }
		public double score(Perception p, SquadMemory m, SquadBrain b) { return score.of(p, m, b); }
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) { return plan.of(p, m, b); }
	}

	static final Signal BELLOW = Signal.HORN_LONG;
	static final Signal SHOUT = new Signal.Std(Types.SignalKind.SHOUT, SoundEvents.VINDICATOR_CELEBRATE, ParticleTypes.NOTE, 32, 10, "tactics.hrafnholt.shout.0");

	static boolean engageable(Perception p, SquadBrain b) {
		return p.odds().ratio() >= b.minOdds() || p.odds().theirs() <= 0;
	}

	// ---- charge, screen, wolf flank -------------------------------------------------------------------------------------------------------

	/** The Berserkers run 6 blocks ahead when a charge begins; the line follows one decision later (about 1 s). */
	static final Behaviour BERSERKER_SCREEN = new B("berserker_screen", Types.Bucket.ENGAGE, 0.05, 10, 30, 0,
		(p, m, b) -> m.uses("hrafn_screen") == 0 && !near(p, 40).isEmpty() && engageable(p, b) && hasUnit(p, "hrafnholt_berserker") ? 0.95 : 0,
		(p, m, b) -> {
			Perception.Contact c = Behaviours.nearest(p, near(p, 40), p.squadCentre());
			if (c == null) return Order.Orders.NONE;
			Vec3 ax = Behaviours.axis(p, List.of(c));
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) {
				if (isUnit(f.id(), "hrafnholt_berserker")) out.put(f.id(), Behaviours.o(Types.OrderKind.CHARGE, c.lastSeen(), c.id()));
				else if (f.ranged()) out.put(f.id(), Behaviours.o(Types.OrderKind.FOCUS, c.lastSeen(), c.id()));
				else out.put(f.id(), Behaviours.o(Types.OrderKind.MOVE_TO_SLOT, Behaviours.slot(p.squadCentre(), ax, 2.0, (out.size() % 5 - 2) * 1.5), null));
			}
			m.saw("hrafn_screen", p.tick());
			screens++;
			return Order.Orders.of(out, BELLOW);
		});

	static final Behaviour CHARGE = new B("charge", Types.Bucket.ENGAGE, 0.10, 40, 45, 0,
		(p, m, b) -> {
			List<Perception.Contact> t = near(p, 40);
			if (t.isEmpty()) return 0;
			if (engageable(p, b)) return 0.90;
			Perception.Contact c = Behaviours.nearest(p, t, p.squadCentre());
			return c != null && c.lastSeen().distanceToSqr(p.squadCentre()) < 24 * 24 ? 0.55 : 0;     // never breaks off: even bad odds close the distance
		},
		(p, m, b) -> {
			Perception.Contact c = Behaviours.nearest(p, near(p, 40), p.squadCentre());
			if (c == null) return Order.Orders.NONE;
			boolean first = m.uses("hrafn_charge") == 0;
			m.saw("hrafn_charge", p.tick());
			charges++;
			Order.Orders o = allCharge(p, c, false);
			return first && m.uses("hrafn_screen") == 0 ? Order.Orders.of(o.perSoldier(), BELLOW) : o;
		});

	static final Behaviour WOLF_FLANK = new B("wolf_flank", Types.Bucket.ENGAGE, 0.05, 30, 45, 0,
		(p, m, b) -> {
			if (!hasUnit(p, "hrafnholt_wolf_handler")) return 0;
			Perception.Contact c = Behaviours.nearest(p, near(p, 16), p.squadCentre());
			return c != null ? 0.97 : 0;                                  // engaged: the handler holds the front and its wolves circle
		},
		(p, m, b) -> {
			Perception.Contact c = Behaviours.nearest(p, near(p, 16), p.squadCentre());
			if (c == null) return Order.Orders.NONE;
			m.saw("wolf_flank", p.tick());
			return allCharge(p, c, true);
		});

	// ---- chase the routed --------------------------------------------------------------------------------------------------------------------

	static Perception.@Nullable Contact quarry(Perception p) {
		long now = p.tick();
		Perception.Contact best = null;
		for (Perception.Contact c : p.contacts()) {
			if (!c.visibleNow()) continue;
			boolean routed = c.kind() == Types.ContactKind.KINGDOM_SOLDIER && entity(c.id()) instanceof KingdomSoldier ks && ks.isRouted();
			Long fought = FOUGHT.get(c.id());
			boolean fleeing = c.kind() == Types.ContactKind.PLAYER && fought != null && now - fought < 600 && c.lastSeen().distanceToSqr(p.squadCentre()) > 7 * 7;
			if (!routed && !fleeing) continue;
			if (best == null || c.lastSeen().distanceToSqr(p.squadCentre()) < best.lastSeen().distanceToSqr(p.squadCentre())) best = c;
		}
		return best;
	}

	static final Behaviour CHASE_ROUTED = new B("chase_routed", Types.Bucket.REACT, 0.10, 20, 40, 0,
		(p, m, b) -> {
			Perception.Contact q = quarry(p);
			UUID sq = b.squadId;
			Chase ch = CHASES.get(sq);
			if (q == null) {
				if (ch != null && p.tick() - ch.lastSight() > CHASE_SIGHT_TICKS) CHASES.remove(sq);   // sight lost for 60 ticks: the pursuit ends
				return 0;
			}
			if (ch == null) CHASES.put(sq, ch = new Chase(p.squadCentre(), p.tick()));
			if (p.squadCentre().distanceToSqr(ch.start()) > (double) CHASE_BLOCKS * CHASE_BLOCKS) return 0;   // 48 blocks: they return
			CHASES.put(sq, new Chase(ch.start(), p.tick()));
			return 0.95;
		},
		(p, m, b) -> {
			Perception.Contact q = quarry(p);
			if (q == null) return Order.Orders.NONE;
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), Behaviours.o(Types.OrderKind.CHARGE, q.lastSeen(), q.id()));
			chaseOrders += out.size();
			return Order.Orders.of(out, null);
		});

	// ---- impatience ------------------------------------------------------------------------------------------------------------------------

	static final Behaviour POOR_PATIENCE = new B("poor_patience", Types.Bucket.ENGAGE, 0.05, 30, 45, 0,
		(p, m, b) -> m.encounterStart >= 0 && p.tick() - m.encounterStart > STALEMATE_TICKS && !near(p, 48).isEmpty() ? 0.80 : 0,
		(p, m, b) -> {
			Perception.Contact c = Behaviours.nearest(p, near(p, 48), p.squadCentre());
			return c == null ? Order.Orders.NONE : allCharge(p, c, false);
		});

	/** 30 percent of the melee pick the weakest contact within 16 blocks, the rest go on with the 2.0 goals; seldom (one decision in twenty). */
	static final Behaviour WEAK_FOCUS = new B("weak_focus", Types.Bucket.ENGAGE, 0.05, 20, 45, 0,
		(p, m, b) -> (p.tick() / 15) % 20 == 0 && near(p, 16).size() >= 2 && engageable(p, b) ? 0.98 : 0,
		(p, m, b) -> {
			Perception.Contact weak = null;
			for (Perception.Contact c : near(p, 16)) if (weak == null || c.hpFraction() < weak.hpFraction()) weak = c;
			if (weak == null) return Order.Orders.NONE;
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) if (!f.ranged() && Math.floorMod(f.id().hashCode(), 10) < 3) out.put(f.id(), Behaviours.o(Types.OrderKind.FOCUS, weak.lastSeen(), weak.id()));
			return Order.Orders.of(out, null);
		});

	// ---- night raid and intimidation ------------------------------------------------------------------------------------------------------

	/** At night, with nobody in sight, the patrol closes into one tight group and shouts: no stealth. */
	static final Behaviour NIGHT_RAID = new B("night_raid", Types.Bucket.POSITION, 0.05, 100, 200, 0,
		(p, m, b) -> p.env().night() && near(p, 40).isEmpty() && p.friends().size() >= 3 ? 0.5 : 0,
		(p, m, b) -> {
			Map<UUID, Order> out = new HashMap<>();
			int i = 0;
			for (Perception.Friendly f : p.friends()) {
				if (f.leader()) continue;
				double ang = i++ * 2.399963;
				out.put(f.id(), Behaviours.o(Types.OrderKind.MOVE_TO_SLOT, p.squadCentre().add(Math.cos(ang) * (1.5 + i * 0.15), 0, Math.sin(ang) * (1.5 + i * 0.15)), null));
			}
			return Order.Orders.of(out, SHOUT);
		});

	/** Stands in the village square with banners raised (a ring of HOLD orders around the marked centre). No block damage, no villager harm. */
	static final Behaviour INTIMIDATE_B = new B("intimidate", Types.Bucket.POSITION, 0.05, 100, 100, 0,
		(p, m, b) -> {
			if (!intimidating(b.squadId, p.tick())) return 0;
			if (p.odds().theirs() > 0 && p.odds().ratio() < 1.0) {              // a strong garrison: they leave
				INTIMIDATE.remove(b.squadId);
				return 0;
			}
			return 0.7;
		},
		(p, m, b) -> {
			Object[] o = INTIMIDATE.get(b.squadId);
			Vec3 c = o == null ? p.squadCentre() : (Vec3) o[0];
			Map<UUID, Order> out = new HashMap<>();
			int i = 0, n = Math.max(1, p.friends().size());
			for (Perception.Friendly f : p.friends()) {
				double ang = i++ * Math.PI * 2 / n;
				out.put(f.id(), Behaviours.o(Types.OrderKind.HOLD, c.add(Math.cos(ang) * 6, 0, Math.sin(ang) * 6), null));
			}
			return Order.Orders.of(out, Signal.BANNER_RAISE);
		});

	/** Shield-beating and howls while a squad intimidates (sound only). */
	static void intimidateTick(MinecraftServer srv) {
		long now = srv.overworld().getGameTime();
		for (var e : INTIMIDATE.entrySet()) {
			if ((long) e.getValue()[1] <= now) continue;
			Vec3 c = (Vec3) e.getValue()[0];
			for (ServerLevel l : srv.getAllLevels()) {
				if (!l.isLoaded(net.minecraft.core.BlockPos.containing(c))) continue;
				if (dev.elsewhere.rivals.hrafnholt.Sfx3.enabled()) l.playSound(null, c.x, c.y, c.z, dev.elsewhere.rivals.hrafnholt.Sfx3.get("hrafnholt", "shieldwall"), SoundSource.HOSTILE, 2.0F, 1.0F);
				else l.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
				l.playSound(null, c.x, c.y, c.z, SoundEvents.WOLF_GROWL_BABY.value(), SoundSource.HOSTILE, 1.5F, 0.5F);
			}
		}
	}

	// ---- shield bash (soldier ability) -----------------------------------------------------------------------------------------------------------

	/**
	 * Melee soldiers within 2 blocks of a shield-blocking enemy shove it: an 8-tick wind-up with red crack particles, a push of 2 blocks, the enemy's guard dropped
	 * for 10 ticks, cooldown 120. Run every 4 ticks over the loaded soldiers; only a soldier with a target within 2.4 blocks costs anything.
	 */
	static void bashTick(ServerLevel level) {
		long now = level.getGameTime();
		for (KingdomSoldier s : KingdomPerf.loaded(level)) {
			if (!s.isAlive() || !FACTION.equals(s.factionId()) || s.isRouted()) continue;
			LivingEntity t = s.getTarget();
			if (t == null || !t.isAlive() || s.distanceToSqr(t) > 2.4 * 2.4) continue;
			long[] st = BASH.get(s.getUUID());
			if (st != null && st[0] > 0) {
				if (now >= st[0]) {
					st[0] = 0;
					st[1] = now + BASH_COOLDOWN;
					bash(level, s, t);
				}
				continue;
			}
			if ((st != null && now < st[1]) || !t.isBlocking() || s.getMainHandItem().isEmpty()
				|| s.unit().role().ranged() || !KingdomTargeting.canAttack(s, t)) continue;
			BASH.put(s.getUUID(), new long[] {now + BASH_WINDUP, st == null ? 0 : st[1]});
			level.sendParticles(ParticleTypes.CRIT, s.getX(), s.getY() + 1.4, s.getZ(), 5, 0.3, 0.2, 0.3, 0.1);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, s.getX(), s.getY() + 1.6, s.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
			level.playSound(null, s.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.7F, 1.4F);
		}
	}

	public static void bash(ServerLevel level, KingdomSoldier s, LivingEntity t) {
		if (s.distanceToSqr(t) > 3.0 * 3.0) return;
		t.knockback(0.9, -(t.getX() - s.getX()), -(t.getZ() - s.getZ()), level.damageSources().mobAttack(s), 0.0F);
		t.needsSync = true;
		if (t instanceof Player p && p.isBlocking()) {
			var use = p.getUseItem();
			p.stopUsingItem();
			p.getCooldowns().addCooldown(use, BASH_GUARD_DROP);
		} else if (t instanceof KingdomSoldier ts) {
			ts.stopUsingItem();
			ts.shieldDisabledTicks = Math.max(ts.shieldDisabledTicks, BASH_GUARD_DROP);
		}
		level.playSound(null, t.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.2, t.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
		bashes++;
	}

	public static boolean hasShield(KingdomSoldier s) {
		return s.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}
}
