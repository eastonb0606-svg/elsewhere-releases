package dev.elsewhere.beta;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.api.Succession;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev-only (-Dbosshealth.selftest=true). 2.1 combined check: every human boss (rivals, Aldermark King and Lords, Risen, Regent,
 * heir, Chair) is spawned, driven through every phase by setting health, then through its last stand by a lethal hit,
 * and max health must stay at or below 300 at every step. Never part of normal play.
 */
public final class BossHealthCheck {
	private BossHealthCheck() {}

	static final double CAP = 300.0;
	static final double[] FRACS = {0.97, 0.8, 0.66, 0.5, 0.4, 0.33, 0.25, 0.15, 0.1, 0.05};
	record Step(int delay, Consumer<ServerLevel> run) {}
	static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
	static int pass, fail, ticks, wait;
	static boolean started, done;
	static final List<String> FAILS = new ArrayList<>();
	static final List<Mob> LIVE = new ArrayList<>();
	static int seq;

	static void check(String what, boolean ok) {
		if (ok) pass++; else { fail++; FAILS.add(what); System.out.println("[bosshealth-selftest] FAIL " + what); }
	}

	/** -Dbosshealth.legacy=<file>: a world saved by 2.0.2 (file lines "entity_path=oldMaxHealth"). Max health must not have risen and the King is <= 300. */
	static void registerLegacy(String file) {
		boolean[] ran = {false};
		int[] t = {0};
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (ran[0] || ++t[0] < 300) return;
			ran[0] = true;
			java.util.Map<String, Double> old = new java.util.HashMap<>();
			try { for (String ln : java.nio.file.Files.readAllLines(java.nio.file.Path.of(file))) { int i = ln.indexOf('='); if (i > 0) old.put(ln.substring(0, i).trim(), Double.parseDouble(ln.substring(i + 1).trim())); } }
			catch (Exception e) { check("legacy file readable: " + e, false); }
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (ServerLevel l : srv.getAllLevels()) {
				for (net.minecraft.world.entity.Entity e : l.getAllEntities()) {
					if (!(e instanceof Mob m)) continue;
					String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath();
					Double o = old.get(id);
					if (o == null) continue;
					seen.add(id);
					double now = m.getMaxHealth();
					System.out.println("[bosshealth-selftest] legacy " + id + " old " + o + " -> now " + now + " health " + m.getHealth());
					check("legacy " + id + ": max health " + now + " <= old " + o, now <= o + 1e-3);
					check("legacy " + id + ": health " + m.getHealth() + " <= max " + now, m.getHealth() <= now + 1e-3);
					if (id.equals("court_king")) check("legacy King <= 300", now <= CAP + 1e-3);
				}
			}
			for (String id : old.keySet()) check("legacy " + id + " loaded from the 2.0.2 world", seen.contains(id));
			finish(srv);
		});
	}

	public static void register() {
		String lf = System.getProperty("bosshealth.legacy");
		if (lf != null && !lf.isEmpty() && !lf.equals("true")) { registerLegacy(lf); return; }
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) srv.overworld().setChunkForced(cx, cz, true);
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (done) return;
			ServerLevel l = srv.overworld();
			if (!started) {
				if (++ticks < 200 || !l.isPositionEntityTicking(new BlockPos(8, 100, 0))) return;
				started = true;
				plan(srv, l);
			}
			if (wait-- > 0) return;
			Step s = STEPS.poll();
			if (s == null) { finish(srv); return; }
			try { s.run().accept(l); } catch (Throwable t) { check("step threw " + t, false); t.printStackTrace(); }
			wait = s.delay();
		});
	}

	static void finish(net.minecraft.server.MinecraftServer srv) {
		done = true;
		System.out.println("[bosshealth-selftest] TOTAL " + pass + " passed, " + fail + " failed");
		for (String f : FAILS) System.out.println("[bosshealth-selftest] FAILED: " + f);
		if (Boolean.getBoolean("bosshealth.selftest.exit")) srv.halt(false);
	}

	static void at(Mob m, ServerLevel l) {
		int i = seq++;
		m.snapTo(8.5 + (i % 10) * 6, -60, 8.5 + (i / 10) * 6, 0, 0);
	}

	static void assertCap(String tag, LivingEntity e) {
		double mx = e.getMaxHealth();
		check(tag + ": max health " + mx + " <= 300", mx <= CAP + 1e-3);
		check(tag + ": health " + e.getHealth() + " <= max", e.getHealth() <= mx + 1e-3);
	}

	/** Phase and last-stand drive for one live boss. */
	static void drive(String tag, Mob m) {
		assertCap(tag + " spawn", m);
		for (double f : FRACS) {
			STEPS.add(new Step(2, l -> { if (m.isAlive()) m.setHealth((float) (m.getMaxHealth() * f)); }));
			STEPS.add(new Step(0, l -> { if (m.isAlive()) assertCap(tag + " at " + f + (m instanceof HumanBoss b ? " phase " + b.phaseIndex() : ""), m); }));
		}
		STEPS.add(new Step(3, l -> { if (m.isAlive()) m.hurtServer(l, l.damageSources().generic(), 100000f); }));
		STEPS.add(new Step(2, l -> {
			assertCap(tag + " after lethal hit (" + (m.isAlive() ? "survived: last stand" : "died") + (m instanceof HumanBoss b ? ", uses " + b.phase.lastStandUses() : "") + ")", m);
			if (m.isAlive()) { m.setHealth(m.getMaxHealth()); }
		}));
		STEPS.add(new Step(0, l -> {
			if (m instanceof HumanBoss b && b.isAlive()) {
				// second lethal hit: last stands are single-use (or capped); the boss must now be able to die, cap holds
				m.hurtServer(l, l.damageSources().generic(), 100000f);
			}
		}));
		STEPS.add(new Step(2, l -> { assertCap(tag + " after second lethal hit", m); if (m.isAlive()) m.discard(); }));
	}

	static void plan(net.minecraft.server.MinecraftServer srv, ServerLevel l) {
		// 1) every registered HumanBoss type (15 rivals + Risen variants + duelist test bosses)
		for (EntityType<? extends HumanBoss> t : List.copyOf(DuelistContent.BOSS_TYPES)) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath();
			STEPS.add(new Step(2, lv -> {
				HumanBoss b = t.create(lv, EntitySpawnReason.COMMAND);
				if (b == null) { check(id + ": create", false); return; }
				at(b, lv);
				b.setPersistenceRequired();
				lv.addFreshEntity(b);
				drive(id, b);
			}));
		}
		// 2) every court slot of every faction as the court spawns it: holder (gated), non-holder, Risen, heir, Regent
		List<Identifier> factions = new ArrayList<>();
		factions.add(CourtState.get(l).faction);
		factions.add(dev.elsewhere.rivals.hrafnholt.HrafnholtContent.FACTION);
		factions.add(dev.elsewhere.rivals.azmar.AzmarContent.FACTION);
		factions.add(dev.elsewhere.rivals.mirewick.MirewickContent.FACTION);
		for (Identifier f : factions) {
			for (Ruler r : Ruler.values()) {
				for (String mode : new String[] {"holder", "risen", "heir", "regent"}) {
					STEPS.add(new Step(2, lv -> {
						CourtState st = CourtState.get(srv, f);
						st.resetForTests();
						String tag = f.getPath() + "/" + r.key + "/" + mode;
						if (Leaders.type(f, r.key) == null && !f.equals(factions.get(0))) return;
						if (mode.equals("heir")) { st.dead.add(r); Succession.heir(lv, st, r); }
						if (mode.equals("regent") && r != Ruler.KING) { st.crowned = r; }
						Mob m = CourtLife.spawnLeader(lv, st, r, new Vec3(8.5 + (seq % 10) * 6, -60, 8.5 + (seq++ / 10) * 6), null);
						if (m == null) { check(tag + ": spawnLeader returned null", false); return; }
						if (mode.equals("regent")) Succession.rulerSucceeded(lv, st, r);
						if (mode.equals("risen")) {
							var hp = m.getAttribute(Attributes.MAX_HEALTH);
							hp.addPermanentModifier(new AttributeModifier(dev.elsewhere.kingdom_court.CourtContent.id("court_risen_hp"), 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
							m.addTag("court_risen");
							if (m instanceof Leaders.LeaderBoss lb) lb.onRisen(2);
							Leaders.applyGating(m, st, r);
						}
						if (m instanceof dev.elsewhere.kingdom_court.ruler.CourtRuler cr && mode.equals("regent")) cr.setCrowned(true);
						drive(tag, m);
					}));
				}
			}
		}
		STEPS.add(new Step(0, lv -> check("ran at least 15 rival boss types", DuelistContent.BOSS_TYPES.size() >= 15)));
	}
}
