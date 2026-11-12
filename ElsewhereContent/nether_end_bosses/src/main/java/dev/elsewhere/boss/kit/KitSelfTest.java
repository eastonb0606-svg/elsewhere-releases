package dev.elsewhere.boss.kit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side checks for the boss kit (drop rule rates, pity, retry refund, persistence). Call from a module's self-test with its own check function. */
public final class KitSelfTest {
	private KitSelfTest() {}

	public static void run(BiConsumer<Boolean, String> check) {
		try {
			dropRates(check);
			persistence(check);
			openings(check);
		} catch (Throwable t) {
			check.accept(false, "boss kit self-test crashed: " + t);
		}
	}

	private static double rate(boolean first, DropRule.Kind kind, boolean pity, int n, RandomSource r) {
		int hits = 0;
		Map<String, Integer> misses = new HashMap<>();
		for (int i = 0; i < n; i++) {
			if (!pity) misses.clear();
			var drops = DropRule.roll(first, misses, List.of(new DropRule.Unique("u", kind, () -> new ItemStack(Items.STICK))), r);
			if (!drops.isEmpty()) hits++;
		}
		return hits / (double) n;
	}

	private static boolean near(double v, double want, double tol) { return Math.abs(v - want) <= tol; }

	private static void dropRates(BiConsumer<Boolean, String> check) {
		RandomSource r = RandomSource.create(12345L);
		int n = 40000;
		double fs = rate(true, DropRule.Kind.SIGNATURE, false, n, r), fo = rate(true, DropRule.Kind.OTHER, false, n, r);
		double rs = rate(false, DropRule.Kind.SIGNATURE, false, n, r), ro = rate(false, DropRule.Kind.OTHER, false, n, r);
		check.accept(fs == 1.0 && near(fo, 0.50, 0.01), String.format("drop rule: first kill signature %.3f (want 1.0), other %.3f (want 0.50) over %d kills", fs, fo, n));
		check.accept(near(rs, 0.67, 0.01) && near(ro, 0.33, 0.01), String.format("drop rule: later kills signature %.3f (want 0.67), other %.3f (want 0.33) without pity", rs, ro));
		check.accept(near(DropRule.chance(DropRule.Kind.OTHER, false, 1), 0.43, 1e-4) && near(DropRule.chance(DropRule.Kind.OTHER, false, 3), 0.60, 1e-4)
			&& near(DropRule.chance(DropRule.Kind.OTHER, false, 40), 0.60, 1e-4), "bad-luck protection: +10% per miss, capped at 60% (0.33, 0.43, ... 0.60)");
		double po = rate(false, DropRule.Kind.OTHER, true, n, r);
		check.accept(po > 0.40 && po < 0.50, String.format("bad-luck protection: with pity the later-kill rate of an other unique is %.3f (between 0.40 and 0.50)", po));
		double el1 = rate(true, DropRule.Kind.EXTRA_LIFE, false, n, r), el2 = rate(false, DropRule.Kind.EXTRA_LIFE, true, n, r);
		check.accept(el1 == 1.0 && near(el2, 0.05, 0.01), String.format("extra-life items: first kill %.2f, repeat %.3f (low, no pity)", el1, el2));
	}

	private static void openings(BiConsumer<Boolean, String> check) {
		String[] bosses = {"gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh", "grunthar", "solaxis", "mournmaw", "ossyra", "aphelion"};
		String[] big = {"belly_flop", "reap", "staff_swing", "fist_slam", "tentacle_slam", "charge", "supernova", "grief_storm", "shell_slam", "devour_dive"};
		java.util.Set<String> shapes = new java.util.HashSet<>();
		boolean all = true;
		for (int i = 0; i < bosses.length; i++) {
			var op = Openings.get(bosses[i], big[i]);
			all &= op != null;
			if (op != null) shapes.add(op.ticks() + "/" + op.bonus());
		}
		check.accept(all && shapes.size() >= 9, "boss openings: all 10 bosses have one after their big attack and the shapes differ (" + shapes.size() + " distinct length/bonus pairs)");
		Openings.Tracker t = new Openings.Tracker();
		check.accept(t.multiplier(100) == 1.0F && !t.isOpen(100), "boss openings: closed by default (damage x1.0)");
		check.accept(ForesightGear.TAG.location().toString().equals("elsewhere:foresight_gear"), "foresight: hint particles are gated by #elsewhere:foresight_gear");
	}

	private static void persistence(BiConsumer<Boolean, String> check) throws Exception {
		Path dir = Files.createTempDirectory("elsewhere_kit_test");
		Path file = dir.resolve("kit.json");
		AtomicLong clock = new AtomicLong(1000);
		KitStore a = new KitStore(file, clock::get);
		UUID player = UUID.randomUUID(), boss = UUID.randomUUID();
		a.missesView(player, "grunthar").put("ashtusk_harness", 3);
		a.markKilled(player, "grunthar");
		a.cooldownStart("kazehana_seal", 12000);
		a.fightStart(boss, "grunthar", player, new ItemStack(Items.GOLD_BLOCK), "minecraft:the_nether", 1, 2, 3);
		boolean wiped = a.fightWiped(boss, "wipe");
		boolean again = a.fightWiped(boss, "wipe");
		check.accept(wiped && !again, "retry: a wipe queues the summon item once (a second wipe call does nothing)");
		check.accept(a.takeDueRefunds(id -> true).isEmpty(), "retry: the summon item is not back before the 30 s retry delay");
		// "restart": a brand new store reads the same file
		clock.addAndGet(100);
		KitStore b = new KitStore(file, clock::get);
		check.accept(b.cooldownRemaining("kazehana_seal") == 11900 && !b.cooldownReady("kazehana_seal"), "cooldown persistence: 12000-tick cooldown survives a save/load (" + b.cooldownRemaining("kazehana_seal") + " left)");
		check.accept(b.missesView(player, "grunthar").getOrDefault("ashtusk_harness", 0) == 3 && b.hasKilled(player, "grunthar"), "persistence: pity counters and first-kill record survive a save/load");
		check.accept(b.takeDueRefunds(id -> false).isEmpty(), "retry: refund waits while the summoner is offline");
		clock.addAndGet(BossKit.RETRY_DELAY_TICKS);
		var due = b.takeDueRefunds(id -> true);
		check.accept(due.size() == 1 && due.get(0).item.equals("minecraft:gold_block") && due.get(0).player.equals(player.toString()), "retry: after 30 s the summon item is handed back to the summoner (survived a save/load)");
		check.accept(new KitStore(file, clock::get).data().refunds.isEmpty(), "retry: a delivered refund is gone from disk");
		clock.addAndGet(12000);
		check.accept(new KitStore(file, clock::get).cooldownReady("kazehana_seal"), "cooldown persistence: the cooldown expires on schedule after a reload");
		// interrupted fight (restart mid-fight) counts as a wipe
		UUID boss2 = UUID.randomUUID();
		b.fightStart(boss2, "ossyra", player, new ItemStack(Items.SHULKER_SHELL), "minecraft:the_end", 0, 0, 0);
		KitStore c = new KitStore(file, clock::get);
		check.accept(c.fightActive(boss2) && c.recoverInterrupted() == 1 && c.data().refunds.size() == 1, "arena restart: a fight on disk at server start is recovered as a wipe with a refund");
		Files.deleteIfExists(file);
		Files.deleteIfExists(dir);
	}
}
