package dev.elsewhere.boss_ui.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.boss_ui.BossBarData;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class SelfTest {
	private static int pass, fail;

	private static void check(boolean ok, String what) {
		if (ok) pass++; else fail++;
		System.out.println("[boss_ui-selftest] " + (ok ? "PASS " : "FAIL ") + what);
	}

	public static void run(MinecraftServer server) {
		try {
			JsonObject root;
			try (InputStream in = SelfTest.class.getResourceAsStream("/assets/elsewhere/boss_bars.json")) {
				root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
			}
			Map<String, BossBarData.Entry> m = BossBarData.parse(root);
			check(m.size() == 24, "boss_bars.json covers 24 bosses (" + m.size() + ")");
			boolean icons = true;
			StringBuilder bad = new StringBuilder();
			for (BossBarData.Entry e : m.values()) {
				String rel = "/assets/" + e.icon().replace(':', '/');
				boolean ok = !e.icon().isEmpty() && SelfTest.class.getResource(rel) != null;
				if (ok) {
					try (InputStream in = SelfTest.class.getResourceAsStream(rel)) {
						var img = javax.imageio.ImageIO.read(in);
						ok = img != null && img.getWidth() == 16 && img.getHeight() == 16;
					}
				}
				if (!ok) { icons = false; bad.append(' ').append(e.id()); }
			}
			check(icons, "every boss has a real 16x16 icon texture" + bad);
			check(m.keySet().stream().allMatch(k -> k.startsWith("elsewhere:")), "ids are Elsewhere entity ids");
			var s = BossBarData.split("Tempestra, Eye of the Gale  ·  Gust Volley");
			check(s.name().equals("Tempestra, Eye of the Gale") && s.attack().equals("Gust Volley"), "title splits into name and attack");
			check(BossBarData.split("Ender Dragon").attack().isEmpty(), "plain title has no attack");
			java.util.function.Function<String, String> names = id -> switch (id) {
				case "elsewhere:grunthar" -> "Grunthar, the Gilded";
				case "elsewhere:strawgrim" -> "Strawgrim, the Hollow Shepherd";
				default -> "Name of " + id;
			};
			check(BossBarData.match(m, "Strawgrim, the Hollow Shepherd  ·  Reap", names) != null && BossBarData.match(m, "Strawgrim, the Hollow Shepherd  ·  Reap", names).id().equals("elsewhere:strawgrim"), "bar title resolves to its boss id");
			check(BossBarData.match(m, "Ender Dragon", names) == null && BossBarData.match(m, "Wither", names) == null, "vanilla bosses are not matched (bars stay vanilla)");
			var sub = BossBarData.subOwner(m, "Ashtusk, the Boar");
			check(sub != null && sub.id().equals("elsewhere:grunthar") && BossBarData.subOwner(m, "Supernova timer").id().equals("elsewhere:solaxis"), "rider/mount and timer bars attach to their boss");
			check(BossBarData.subOwner(m, "Ender Dragon") == null, "vanilla title is not a sub-bar");
			check(BossBarData.parse(null).isEmpty() && BossBarData.parse(new JsonObject()).isEmpty(), "missing data falls back to vanilla (empty map)");
			var one = BossBarData.parse(JsonParser.parseString("{\"bosses\":{\"elsewhere:x\":{\"color\":\"#FFFFFF\",\"phases\":[0.75,0.5,0.25]}}}").getAsJsonObject());
			check(one.get("elsewhere:x").phases().length == 3 && one.get("elsewhere:x").phases()[2] == 0.25F, "explicit phases parse");
			SelfTestBossBar.run(server, SelfTest::check); // P1 boss bar v2: real phases, state chips, labels (s-p1-bossbar)

			// P5 tooltips: lines come live from the stack's attribute modifiers
			var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
			var lo = dev.elsewhere.boss_ui.TooltipLines.build(sword, false);
			var hi = dev.elsewhere.boss_ui.TooltipLines.build(sword, true);
			check(lo.size() == 1, "tooltip without Shift is one hint line");
			check(hi.size() >= 2, "tooltip with Shift lists hit and dps lines");
			check(hi.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith("tooltip.hit") && String.valueOf(t.getArgs()[0]).equals("8")), "netherite sword reads 8 damage live (1 + 7)");
			check(dev.elsewhere.boss_ui.TooltipLines.build(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT), true).isEmpty(), "plain block gets no tooltip lines");
			var helm = dev.elsewhere.boss_ui.TooltipLines.build(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET), true);
			check(helm.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith("tooltip.armor") && String.valueOf(t.getArgs()[0]).equals("3")), "netherite helmet reads 3 armor live");
			SelfTestTelegraph.run(server, SelfTest::check); // P2 telegraphs (s-p2-telegraph)
		} catch (Throwable t) {
			check(false, "self-test threw " + t);
			t.printStackTrace();
		}
		System.out.println("[boss_ui-selftest] TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
		if (Boolean.getBoolean("boss_ui.selftest.exit")) server.halt(false);
	}
}
