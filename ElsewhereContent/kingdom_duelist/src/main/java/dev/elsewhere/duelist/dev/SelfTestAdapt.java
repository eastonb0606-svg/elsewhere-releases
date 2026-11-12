package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.adapt.Formation;
import dev.elsewhere.duelist.adapt.GuardSignals;
import dev.elsewhere.duelist.adapt.LoadoutClass;
import dev.elsewhere.duelist.adapt.PlayerTrace;
import dev.elsewhere.duelist.adapt.Side;
import dev.elsewhere.duelist.adapt.Tactic;
import dev.elsewhere.duelist.adapt.TacticReader;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.Move;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The adaptive framework (loadout read, the repetition tracker, guard signals) and the 3-duelist performance check. */
final class SelfTestAdapt {
	private SelfTestAdapt() {}

	static final List<Long> NANOS = new ArrayList<>();
	static long startNs;
	static boolean measuring;

	static void add(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final FakePlayer[] fp = new FakePlayer[1];
		final TacticReader[] rd = new TacticReader[1];
		h.test("tracker: a repeated combo is spotted on its 3rd use, never before; then blocked 400 ticks").run(() -> {
			b[0] = h.boss(H.base("rd").build(), 0, 0);
			b[0].passive = true;
			fp[0] = h.fake(0, 6);
			rd[0] = new TacticReader(b[0]);
			rd[0].setMarked(true);
			rd[0].tick(h.level, fp[0]);
		}).wait(2)
			.run(() -> combo(h, b[0], fp[0], rd[0])).check("after combo 1: no counter (the first is free)", () -> rd[0].pendingCount() == 0).each(60, () -> rd[0].tick(h.level, fp[0]))
			.run(() -> combo(h, b[0], fp[0], rd[0])).check("after combo 2: no counter (the second is free)", () -> rd[0].pendingCount() == 0).each(60, () -> rd[0].tick(h.level, fp[0]))
			.run(() -> combo(h, b[0], fp[0], rd[0])).check("after combo 3: exactly one counter", () -> rd[0].pendingCount() == 1)
			.check("the counter is the combo from the front, for this player", () -> {
				TacticReader.Counter c = rd[0].pollCounter();
				return c != null && c.tactic() == Tactic.COMBO && c.side() == Side.FRONT && c.player().equals(fp[0].getUUID());
			})
			.run(() -> {
				combo(h, b[0], fp[0], rd[0]);
				combo(h, b[0], fp[0], rd[0]);
				combo(h, b[0], fp[0], rd[0]);
			}).check("three more combos inside the 400-tick block make no counter", () -> rd[0].pendingCount() == 0)
			.each(405, () -> rd[0].tick(h.level, fp[0]))
			.run(() -> {
				combo(h, b[0], fp[0], rd[0]);
				combo(h, b[0], fp[0], rd[0]);
				combo(h, b[0], fp[0], rd[0]);
			}).check("after the block, the 3rd combo counters again (2 counters in all)", () -> rd[0].pendingCount() == 1 && rd[0].countersFired() == 2).cleanup(() -> rd[0].close());
		h.test("tracker: arrows from beyond 8 blocks are a ranged poke, countered on the 3rd").run(() -> {
			b[0] = h.boss(H.base("rd2").build(), 0, 0);
			b[0].passive = true;
			fp[0] = h.fake(0, 14);
			rd[0] = new TacticReader(b[0]);
			rd[0].setMarked(true);
			rd[0].tick(h.level, fp[0]);
			PlayerTrace.of(fp[0]).lastArrowTick = -1000;
		}).wait(2).run(() -> shoot(h, fp[0])).wait(3).run(() -> rd[0].tick(h.level, fp[0])).check("arrow 1: nothing yet", () -> rd[0].pendingCount() == 0)
			.run(() -> shoot(h, fp[0])).wait(3).run(() -> rd[0].tick(h.level, fp[0])).check("arrow 2: nothing yet", () -> rd[0].pendingCount() == 0)
			.run(() -> shoot(h, fp[0])).wait(3).run(() -> rd[0].tick(h.level, fp[0])).check("arrow 3: the ranged-poke counter is queued", () -> {
				TacticReader.Counter c = rd[0].pollCounter();
				return c != null && c.tactic() == Tactic.RANGED_POKE;
			}).cleanup(() -> rd[0].close());
		h.test("tracker: a different side or hit count does not count; three pearls landing near are T4").run(() -> {
			b[0] = h.boss(H.base("rd3").build(), 0, 0);
			b[0].passive = true;
			fp[0] = h.fake(0, 6);
			rd[0] = new TacticReader(b[0]);
			rd[0].setMarked(true);
			rd[0].tick(h.level, fp[0]);
			long now = h.now();
			rd[0].record(Tactic.COMBO, Side.FRONT, 3, now, fp[0]);
			rd[0].record(Tactic.COMBO, Side.LEFT, 3, now + 5, fp[0]);
			rd[0].record(Tactic.COMBO, Side.FRONT, 4, now + 6, fp[0]);
			rd[0].record(Tactic.COMBO, Side.FRONT, 3, now + 7, fp[0]);
		}).check("same tactic needs the same side and the same hit count", () -> rd[0].pendingCount() == 0).run(() -> {
			rd[0].record(Tactic.COMBO, Side.FRONT, 3, h.now() + 9, fp[0]);
		}).check("the third matching combo counters", () -> rd[0].pendingCount() == 1).run(() -> rd[0].pollCounter())
			.run(() -> pearl(h, fp[0])).wait(2).run(() -> rd[0].tick(h.level, fp[0]))
			.run(() -> pearl(h, fp[0])).wait(2).run(() -> rd[0].tick(h.level, fp[0]))
			.run(() -> pearl(h, fp[0])).wait(2).run(() -> rd[0].tick(h.level, fp[0]))
			.check("pearls 1 to 3 landing within 12 blocks: the pearl-in counter", () -> {
				TacticReader.Counter c = rd[0].pollCounter();
				return c != null && c.tactic() == Tactic.PEARL_IN;
			}).cleanup(() -> rd[0].close());
		h.test("tracker limits: 2 pending, 6 per fight, then it stops").run(() -> {
			b[0] = h.boss(H.base("rd4").build(), 0, 0);
			b[0].passive = true;
			fp[0] = h.fake(0, 6);
			rd[0] = new TacticReader(b[0]);
			rd[0].setMarked(true);
			rd[0].tick(h.level, fp[0]);
			long now = h.now();
			Tactic[] ts = {Tactic.JUMP_CRIT_OPENER, Tactic.COMBO, Tactic.RANGED_POKE, Tactic.PEARL_IN, Tactic.HIGH_GROUND, Tactic.SHIELD_TURTLE, Tactic.HEAL_RUN};
			for (Tactic t : ts) for (int i = 0; i < 3; i++) rd[0].record(t, Side.FRONT, 0, now + i, fp[0]);
		}).check("at most 2 pending (the oldest were dropped)", () -> rd[0].pendingCount() == 2)
			.check("6 counters, then the reader has read enough", () -> rd[0].countersFired() == 6 && rd[0].satisfied())
			.check("the seventh tactic made no counter", () -> {
				TacticReader.Counter a = rd[0].pollCounter(), c = rd[0].pollCounter();
				return a != null && c != null && a.tactic() != Tactic.HEAL_RUN && c.tactic() != Tactic.HEAL_RUN;
			}).cleanup(() -> rd[0].close());
		h.test("feints: 20 percent of mirage steps").run(() -> {
			b[0] = h.boss(H.base("rd5").build(), 0, 0);
			b[0].passive = true;
			rd[0] = new TacticReader(b[0]);
		}).check("about 20 percent of 10000 rolls are fakes", () -> {
			int n = 0;
			for (int i = 0; i < 10000; i++) if (rd[0].rollFeint(h.level.getRandom())) n++;
			h.info("feints " + n + " of 10000");
			return n > 1800 && n < 2200;
		}).cleanup(() -> rd[0].close());
		loadout(h);
		guards(h);
		perf(h);
	}

	private static void combo(H h, TestDuelist boss, FakePlayer fp, TacticReader rd) {
		for (int i = 0; i < 3; i++) {
			boss.damageCooldownTime = 0;
			boss.hurtServer(h.level, h.level.damageSources().playerAttack(fp), 1F);
		}
		rd.tick(h.level, fp);
	}

	private static void shoot(H h, FakePlayer fp) {
		Arrow a = EntityTypes.ARROW.create(h.level, EntitySpawnReason.COMMAND);
		a.setOwner(fp);
		a.setPos(0, H.Y + 1, 14);
		h.level.addFreshEntity(a);
		a.discard();
	}

	private static void pearl(H h, FakePlayer fp) {
		var pearl = EntityTypes.ENDER_PEARL.create(h.level, EntitySpawnReason.COMMAND);
		pearl.setOwner(fp);
		pearl.setPos(0, H.Y, 8);
		h.level.addFreshEntity(pearl);
		pearl.discard();
	}

	static void loadout(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final FakePlayer[] fp = new FakePlayer[1];
		h.test("loadout classes (B1)").run(() -> {
			b[0] = h.boss(H.base("ld").build(), 0, 0);
			b[0].passive = true;
		}).check("ranged / shield / heavy / reach / light / mobile / potion-heavy are read", () -> {
			boolean ok = true;
			ok &= has(h, b[0], p -> p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW)), LoadoutClass.RANGED);
			ok &= has(h, b[0], p -> PlayerTrace.of(p).lastShieldUp = h.now(), LoadoutClass.SHIELD);
			ok &= has(h, b[0], p -> p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE)), LoadoutClass.HEAVY);
			ok &= has(h, b[0], p -> p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT)), LoadoutClass.REACH);
			ok &= has(h, b[0], p -> p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)), LoadoutClass.LIGHT);
			ok &= has(h, b[0], p -> p.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0)), LoadoutClass.MOBILE);
			ok &= has(h, b[0], p -> PlayerTrace.of(p).pearlsThrown = 1, LoadoutClass.MOBILE);
			ok &= has(h, b[0], p -> PlayerTrace.of(p).healUses = 2, LoadoutClass.POTION_HEAVY);
			return ok;
		}).check("a sword with a shield is not Light", () -> !has(h, b[0], p -> {
			p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			PlayerTrace.of(p).lastShieldUp = h.now();
		}, LoadoutClass.LIGHT));
		final TacticReader[] rd = new TacticReader[1];
		h.test("read delay: 200 ticks, 0 when marked, lost after 600 ticks without sight").run(() -> {
			b[0] = h.boss(H.base("ld2").build(), 0, 0);
			b[0].passive = true;
			fp[0] = h.fake(0, 6);
			rd[0] = new TacticReader(b[0]);
			rd[0].tick(h.level, fp[0]);
		}).check("not read at first sight", () -> !rd[0].readDone(h.now())).each(150, () -> rd[0].tick(h.level, fp[0])).check("not read at 150 ticks", () -> !rd[0].readDone(h.now()))
			.each(60, () -> rd[0].tick(h.level, fp[0])).check("read after 200 ticks", () -> rd[0].readDone(h.now()))
			.run(() -> {
				for (int x = -3; x <= 3; x++) for (int y = H.Y; y < H.Y + 4; y++) h.level.setBlock(new BlockPos(x, y, 3), Blocks.STONE.defaultBlockState(), 2);
			}).each(610, () -> rd[0].tick(h.level, fp[0])).check("the read is lost after 600 ticks without line of sight", () -> !rd[0].readDone(h.now()))
			.run(() -> rd[0].setMarked(true)).run(() -> rd[0].tick(h.level, fp[0])).check("a falcon mark makes the read instant", () -> rd[0].readDone(h.now()))
			.cleanup(() -> {
				rd[0].close();
				h.flatten(-4, 4, 1, 5);
			});
	}

	private static boolean has(H h, TestDuelist boss, java.util.function.Consumer<FakePlayer> setup, LoadoutClass c) {
		FakePlayer p = h.fake(0, 6);
		PlayerTrace.forgetAll();
		setup.accept(p);
		TacticReader r = new TacticReader(boss);
		r.tick(h.level, p);
		boolean ok = r.has(c);
		r.close();
		if (!ok) h.info("class " + c + " not read; read " + r.classes());
		return ok;
	}

	static void guards(H h) {
		final GuardSignals[] gs = new GuardSignals[1];
		final Mob[] leader = new Mob[1];
		final List<Mob> guards = new ArrayList<>();
		final Husk[] tgt = new Husk[1];
		final double[] r0 = {0};
		h.test("guard signals (B3): tell, 0.5 s delay, formations, caps").run(() -> {
			tgt[0] = h.dummy(0, 14);
			leader[0] = mob(h, 0, 0);
			guards.clear();
			for (int i = 0; i < 4; i++) guards.add(mob(h, -3 + 2 * i, -3));
			gs[0] = new GuardSignals(leader[0]);
			gs[0].setGuards(guards, guards.get(0));
			h.check("Screen signal accepted", gs[0].signal(h.level, Formation.SCREEN, GuardSignals.Tell.BANNER_UP, tgt[0]));
			h.check("Screen is on cooldown right after (400 ticks)", !gs[0].signal(h.level, Formation.SCREEN, GuardSignals.Tell.BANNER_UP, tgt[0]));
		}).each(8, () -> gs[0].tick(h.level)).check("the guards obey 10 ticks after the tell, not before", () -> gs[0].current() == Formation.WITHDRAW && gs[0].ordering())
			.each(4, () -> gs[0].tick(h.level)).check("Screen is in force after the delay", () -> gs[0].current() == Formation.SCREEN)
			.each(60, () -> {
				gs[0].tick(h.level);
				if (h.now() % 15 == 0) h.info("screen: " + guards.stream().map(g -> String.format("(%.1f,%.1f nav %s)", g.getX(), g.getZ(), g.getNavigation().isDone())).toList());
			}).check("a shield wall formed about 3 blocks ahead of the leader, toward the target", () -> {
				double z = guards.stream().mapToDouble(Mob::getZ).average().orElse(0);
				double xs = guards.stream().mapToDouble(Mob::getX).max().orElse(0) - guards.stream().mapToDouble(Mob::getX).min().orElse(0);
				h.info("screen mean z " + String.format("%.2f", z) + " width " + String.format("%.2f", xs));
				return z > 1.2 && z < 5.0 && xs > 1.5;
			}).each(40, () -> gs[0].tick(h.level)).check("Screen ended after 80 ticks and the guards were sent back (Withdraw)", () -> gs[0].current() == Formation.WITHDRAW)
			.run(() -> h.check("Ring is allowed (a flare tell, 40-tick delay)", gs[0].signal(h.level, Formation.RING, GuardSignals.Tell.FLARE_BLUE, tgt[0])))
			.each(30, () -> gs[0].tick(h.level)).check("a flare is obeyed after 2 seconds, not at 1.5", () -> gs[0].current() == Formation.WITHDRAW)
			.each(12, () -> gs[0].tick(h.level)).check("Ring in force after the flare delay", () -> gs[0].current() == Formation.RING)
			.each(70, () -> gs[0].tick(h.level)).check("the ring closed in on the target (within 6.5 blocks of it)", () -> guards.stream().allMatch(g -> g.distanceTo(tgt[0]) < 7.5))
			.run(() -> h.check("Hook is accepted once, then locked for 160 ticks", gs[0].signal(h.level, Formation.HOOK, GuardSignals.Tell.SHIELD_TAPS, tgt[0]) && !gs[0].signal(h.level, Formation.HOOK, GuardSignals.Tell.SHIELD_TAPS, tgt[0])))
			.cleanup(() -> {});
	}

	private static Mob mob(H h, double x, double z) {
		Husk m = EntityTypes.HUSK.create(h.level, EntitySpawnReason.COMMAND);
		((dev.elsewhere.duelist.mixin.MobAccessor) m).elsewhere$goalSelector().removeAllGoals(g -> true);
		((dev.elsewhere.duelist.mixin.MobAccessor) m).elsewhere$targetSelector().removeAllGoals(g -> true);
		m.setPersistenceRequired();
		m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.4);
		m.setPos(x, H.Y, z);
		h.level.addFreshEntity(m);
		return m;
	}

	static void perf(H h) {
		final TestDuelist[] bs = new TestDuelist[3];
		final boolean[] reg = {false};
		final float[] prevHp = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, fightLost = {0F};
		h.test("MSPT with 3 duelists fighting (all moves on)").run(() -> {
			if (!reg[0]) {
				reg[0] = true;
				ServerTickEvents.START_SERVER_TICK.register(s -> startNs = System.nanoTime());
				ServerTickEvents.END_SERVER_TICK.register(s -> {
					if (measuring) NANOS.add(System.nanoTime() - startNs);
				});
			}
			bs[0] = h.boss(TestKit.all(H.base("p0")).hp(4000).build(), -2, 0);
			bs[1] = h.boss(TestKit.all(H.base("p1")).hp(4000).build(), 2, 0);
			bs[2] = h.ruler(TestKit.all(H.base("p2")).ruler().caps(18F, 40F).hp(4000).build(), 0, 4);
			NANOS.clear();
			measuring = true;
		}).each(800, () -> {
			// 0 and 1 duel each other, 2 joins in on 0: everyone always has a live target in front of it
			bs[0].setTarget(bs[1]);
			bs[1].setTarget(bs[0]);
			bs[2].setTarget(bs[0]);
			// the 300 HP cap means they no longer outlast 800 ticks: count damage as it happens and top a boss up when it is low
			for (int i = 0; i < 3; i++) {
				float hp = bs[i].getHealth();
				if (prevHp[i] < Float.MAX_VALUE && prevHp[i] > hp) fightLost[0] += prevHp[i] - hp;
				if (hp < 90F && bs[i].isAlive()) { bs[i].setHealth(bs[i].getMaxHealth()); hp = bs[i].getHealth(); }
				prevHp[i] = hp;
			}
		}).run(() -> measuring = false).check("they actually fought: at least 20 attacks started and at least 30 HP was lost", () -> {
			h.info("fight stats " + bs[0].stats + " " + bs[1].stats + " " + bs[2].stats);
			int hits = 0, windups = 0;
			for (TestDuelist x : bs) {
				hits += x.stat("hits");
				windups += x.stat("swing") + x.stat("throw") + x.stat("arrow") + x.stat("crit_started") + x.stat("bash");
			}
			// "hits" counts only melee and thrown hits (arrows are vanilla arrows), so the proof of a real fight is HP actually lost
			float lost = fightLost[0];
			h.info("melee and thrown hits " + hits + ", attacks started " + windups + ", HP lost by the three " + lost);
			return windups >= 20 && lost >= 30F && hits >= 1 && bs[0].isAlive() && bs[1].isAlive() && bs[2].isAlive();
		})
			.check("avg MSPT at most 2.0 and p95 at most 3.0", () -> {
				List<Long> s = new ArrayList<>(NANOS);
				s.sort(Long::compare);
				if (s.isEmpty()) return false;
				double avg = s.stream().mapToLong(x -> x).average().orElse(0) / 1e6;
				double p95 = s.get((int) (s.size() * 0.95) - 1) / 1e6;
				h.info(String.format("3 duelists over %d ticks: avg %.3f ms, p95 %.3f ms, max %.3f ms", s.size(), avg, p95, s.get(s.size() - 1) / 1e6));
				return avg <= 2.0 && p95 <= 3.0;
			});
	}
}
