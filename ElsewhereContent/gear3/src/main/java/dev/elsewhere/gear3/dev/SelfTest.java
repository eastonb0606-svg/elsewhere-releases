package dev.elsewhere.gear3.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.elsewhere.gear3.Gear3Catalogue;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Def;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3Loot;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Gear3 server self-test (-Dgear3.selftest=true): for every catalogue item checks registration, texture, models, lang,
 * stats, tags, recipes and loot hooks; then the per-mechanic blocks (Phase 2). Logs PASS/FAIL and writes selftest-gear3.log.
 * The stats are read from the catalogue, which gen_gear_md_check.py compares against the GEAR.md tables.
 */
public final class SelfTest {
	private SelfTest() {}

	static final List<String> LOG = new ArrayList<>();
	static int pass, fail;

	public static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[gear3-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	public static void check(String name, boolean ok) {
		check(name, ok, null);
	}

	public static void checkS(String name, BooleanSupplier s) {
		try {
			check(name, s.getAsBoolean(), null);
		} catch (Throwable t) {
			check(name, false, t);
		}
	}

	public static void info(String s) {
		LOG.add("INFO " + s);
		System.out.println("[gear3-selftest] INFO " + s);
	}

	public static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	/** Forces the arena chunks, waits until they tick, then runs once. */
	public static void register() {
		int[] ticks = {0};
		boolean[] ran = {false};
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			for (int cx = 1; cx <= 4; cx++) for (int cz = 1; cz <= 4; cz++) srv.overworld().setChunkForced(cx, cz, true);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (ran[0]) return;
			if (++ticks[0] < 400 && !srv.overworld().isPositionEntityTicking(new net.minecraft.core.BlockPos(40, 100, 40))) return;
			ran[0] = true;
			run(srv);
		});
	}

	/**
	 * gear3 shields render as real 3D shields (tools/shield3d.py): the item definition switches between the idle and the
	 * blocking element model on minecraft:using_item like vanilla's shield; both models are valid element models on the
	 * shield's own 32x32 texture, carry vanilla shield.json / shield_blocking.json display transforms, and have the vanilla
	 * ShieldModel grip (handle box -1,-3,-5 .. 1,3,1) so the shield is held and blocks exactly where a vanilla one does.
	 */
	static boolean shield3d(String id) {
		JsonObject def = json("assets/elsewhere/items/" + id + ".json");
		if (def == null) return false;
		JsonObject m = def.getAsJsonObject("model");
		if (!"minecraft:condition".equals(m.get("type").getAsString()) || !"minecraft:using_item".equals(m.get("property").getAsString())) return false;
		if (!("elsewhere:item/" + id).equals(m.getAsJsonObject("on_false").get("model").getAsString())) return false;
		if (!("elsewhere:item/" + id + "_blocking").equals(m.getAsJsonObject("on_true").get("model").getAsString())) return false;
		JsonObject idle = json("assets/elsewhere/models/item/" + id + ".json"), block = json("assets/elsewhere/models/item/" + id + "_blocking.json");
		for (JsonObject model : new JsonObject[] {idle, block}) {
			if (model == null || !model.has("elements") || model.getAsJsonArray("elements").size() < 3) return false;
			if (!("elsewhere:item/" + id + "_3d").equals(model.getAsJsonObject("textures").get("shield").getAsString())) return false;
			JsonObject disp = model.getAsJsonObject("display");
			for (String ctx : new String[] {"firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand", "gui"})
				if (disp == null || !disp.has(ctx)) return false;
			boolean grip = false;
			for (var e : model.getAsJsonArray("elements")) {
				JsonObject el = e.getAsJsonObject();
				var from = el.getAsJsonArray("from");
				var to = el.getAsJsonArray("to");
				for (int i = 0; i < 3; i++) {
					double a = from.get(i).getAsDouble(), b = to.get(i).getAsDouble();
					if (a > b || a < -16 || b > 32) return false;
				}
				if (from.toString().equals("[-1,-3,-5]") && to.toString().equals("[1,3,1]")) grip = true;
				for (var f : el.getAsJsonObject("faces").entrySet()) {
					JsonObject face = f.getValue().getAsJsonObject();
					if (!"#shield".equals(face.get("texture").getAsString())) return false;
					for (var u : face.getAsJsonArray("uv")) if (u.getAsDouble() < 0 || u.getAsDouble() > 16) return false;
				}
			}
			if (!grip) return false;
		}
		// vanilla shield.json / shield_blocking.json numbers (third-person hold and the raised block)
		if (!idle.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand").get("translation").toString().equals("[10,6,-4]")) return false;
		if (!block.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand").get("rotation").toString().equals("[45,155,0]")) return false;
		if (!block.getAsJsonObject("display").getAsJsonObject("firstperson_righthand").get("translation").toString().equals("[-15,3.25,-11]")) return false;
		try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/item/" + id + "_3d.png")) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			return img.getWidth() == 32 && img.getHeight() == 32 && (img.getRGB(8, 8) >>> 24) != 0 && (img.getRGB(24, 8) >>> 24) != 0
				&& (img.getRGB(4, 20) >>> 24) != 0 && (img.getRGB(24, 24) >>> 24) != 0;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	static boolean res(String path) {
		return Gear3Content.class.getResource("/" + path) != null;
	}

	/** Every copy of a resource on the classpath, merged (the merged Elsewhere jar carries its own lang file too). */
	static JsonObject mergedJson(String path) {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources(path))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			return null;
		}
		return out;
	}

	static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	static boolean recipe(MinecraftServer s, String path) {
		return s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Gear3Content.id(path))).isPresent();
	}

	/** The table exists and (after the modify event) names the item. */
	static boolean loot(MinecraftServer s, String table, Item item) {
		LootTable t = s.reloadableRegistries().getLootTable(Gear3Loot.table(table));
		if (t == LootTable.EMPTY) return false;
		String j = LootTable.DIRECT_CODEC.encodeStart(s.registryAccess().createSerializationContext(JsonOps.INSTANCE), t).getOrThrow().toString();
		return j.contains(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	/** False when the recipe JSON names an elsewhere: item that no module in this environment registers (its load condition then skips it). */
	static boolean foreignPresent(String json) {
		var m = java.util.regex.Pattern.compile("\"elsewhere:([a-z0-9_]+)\"").matcher(json);
		while (m.find()) if (!BuiltInRegistries.ITEM.containsKey(Gear3Content.id(m.group(1))) ) return false;
		return true;
	}

	static double attr(ItemStack st, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a) {
		ItemAttributeModifiers m = st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double sum = 0;
		for (ItemAttributeModifiers.Entry e : m.modifiers()) if (e.attribute().equals(a) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) sum += e.modifier().amount();
		return sum;
	}

	public static void run(MinecraftServer s) {
		try {
			runChecks(s);
		} catch (Throwable t) {
			t.printStackTrace();
			check("self-test ran to the end", false, t);
		}
		info("items=" + Gear3Catalogue.DEFS.size() + " pass=" + pass + " fail=" + fail);
		System.out.println("[gear3-selftest] DONE pass=" + pass + " fail=" + fail);
		try {
			Path dir = Path.of(System.getProperty("gear3.selftest.log", "selftest-gear3.log"));
			try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir))) {
				for (String l : LOG) w.println(l);
				w.println("DONE pass=" + pass + " fail=" + fail);
			}
		} catch (Exception e) {
			System.out.println("[gear3-selftest] cannot write log: " + e);
		}
		if (Boolean.getBoolean("gear3.selftest.exit")) s.halt(false);
	}

	static void runChecks(MinecraftServer s) {
		check("catalogue loaded and every def registered", Gear3Items.BY_ID.size() == Gear3Catalogue.DEFS.size(), Gear3Items.BY_ID.size() + "/" + Gear3Catalogue.DEFS.size());
		check("no id clash with another module's registry entry", Gear3Items.CLASHES.isEmpty(), Gear3Items.CLASHES);
		Set<String> seen = new HashSet<>();
		JsonObject lang = mergedJson("assets/elsewhere/lang/en_us.json");
		check("lang file present", lang != null);
		JsonObject jei = json("elsewhere_gear3/jei_info.json");
		check("JEI info file present", jei != null);
		check("creative tab lang line", lang != null && lang.has("itemGroup.elsewhere.gear3"));

		for (Gear3Def d : Gear3Catalogue.DEFS) {
			String id = d.id;
			Identifier rl = Gear3Content.id(id);
			check(id + ": unique id", seen.add(id));
			Item item = Gear3Items.BY_ID.get(id);
			check(id + ": registered", BuiltInRegistries.ITEM.containsKey(rl) && BuiltInRegistries.ITEM.getValue(rl) == item);
			if (item == null) continue;
			ItemStack st = new ItemStack(item);
			checkS(id + ": texture is a 16x16 PNG with pixels", () -> {
				try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/item/" + id + ".png")) {
					if (in == null) return false;
					var img = ImageIO.read(in);
					if (img.getWidth() != 16 || img.getHeight() != 16) return false;
					int opaque = 0;
					for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((img.getRGB(x, y) >>> 24) != 0) opaque++;
					return opaque >= 8;
				} catch (java.io.IOException e) {
					return false;
				}
			});
			if (d.kind.equals("shield")) checkS(id + ": 3D shield (element models in vanilla shield space, vanilla held/blocking display, blocking model on using_item, 32x32 model texture)",
				() -> shield3d(id));
			else checkS(id + ": item definition + model json point at the texture", () -> {
				JsonObject def = json("assets/elsewhere/items/" + id + ".json");
				JsonObject model = json("assets/elsewhere/models/item/" + id + ".json");
				return def != null && model != null && def.toString().contains("elsewhere:item/" + id)
					&& model.getAsJsonObject("textures").get("layer0").getAsString().equals("elsewhere:item/" + id);
			});
			checkS(id + ": lang name and " + d.loreLines() + " lore line(s)", () -> {
				if (!lang.has("item.elsewhere." + id) || !lang.get("item.elsewhere." + id).getAsString().equals(d.name)) return false;
				for (int i = 1; i <= d.loreLines(); i++) if (!lang.has("item.elsewhere." + id + ".lore" + i)) return false;
				return !lang.has("item.elsewhere." + id + ".lore" + (d.loreLines() + 1));
			});
			checkS(id + ": JEI line", () -> jei != null && jei.has("elsewhere:" + id) && jei.getAsJsonArray("elsewhere:" + id).size() > 0);
			checkS(id + ": stats match the catalogue (" + d.kind + ")", () -> stats(d, st));
			if (!d.attrs.isEmpty()) checkS(id + ": attribute modifiers " + d.attrs.size(), () -> {
				ItemAttributeModifiers m = st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
				for (Gear3Def.Attr a : d.attrs) {
					boolean found = false;
					for (ItemAttributeModifiers.Entry e : m.modifiers()) if (e.attribute().equals(Gear3Items.attribute(a.attr())) && near(e.modifier().amount(), a.amount())) found = true;
					if (!found) return false;
				}
				return true;
			});
			checkS(id + ": tags " + d.tags, () -> {
				for (String t : d.tags) if (!st.is(Gear3Items.itemTag(t))) return false;
				return true;
			});
			checkS(id + ": recipes " + d.recipeFiles, () -> {
				for (String r : d.recipeFiles) {
					JsonObject rj = json("data/elsewhere/recipe/" + r + ".json");
					if (rj == null) return false;
					if (!foreignPresent(rj.toString())) {
						info(id + ": recipe " + r + " skipped, it names an item from a module that is not in this environment");
						continue;
					}
					if (!recipe(s, r)) return false;
				}
				return true;
			});
			for (Gear3Def.Drop dr : d.drops) checkS(id + ": loot hook " + dr.table() + " @ " + dr.chance(), () -> {
				if (s.reloadableRegistries().getLootTable(Gear3Loot.table(dr.table())) == LootTable.EMPTY && !Gear3Loot.DROPS.containsKey(Gear3Loot.tableId(dr.table()).toString())) return false;
				if (s.reloadableRegistries().getLootTable(Gear3Loot.table(dr.table())) == LootTable.EMPTY) {
					info(id + ": loot table " + dr.table() + " is not in this environment (hook is registered)");
					return true;
				}
				return loot(s, dr.table(), item);
			});
		}
		dev.elsewhere.gear3.s3.callingtags.CallingTagChecks.run(s); // s-callingtags: calling tags of all 82 GEAR.md items, lang/JEI audit
		Gear3MechanicTests.run(s);
		dev.elsewhere.gear3.s3.echoarrow.EchoArrowSelfTest.run(s); // s-echoarrow: "echo_arrow: ..." checks
	}

	static boolean stats(Gear3Def d, ItemStack st) {
		switch (d.kind) {
			case "sword", "axe", "pickaxe", "shovel", "hoe" -> {
				if (!near(1.0 + attr(st, Attributes.ATTACK_DAMAGE), d.stat("damage"))) return false;
				if (!near(4.0 + attr(st, Attributes.ATTACK_SPEED), d.stat("speed"))) return false;
				return st.getMaxDamage() == (int) d.stat("durability");
			}
			case "armor" -> {
				if (!near(attr(st, Attributes.ARMOR), d.stat("armor"))) return false;
				if (!near(attr(st, Attributes.ARMOR_TOUGHNESS), d.stat("toughness", 0))) return false;
				return near(attr(st, Attributes.KNOCKBACK_RESISTANCE), d.stat("kb", 0));
			}
			case "shield" -> {
				return st.getMaxDamage() == (int) d.stat("durability") && st.has(DataComponents.BLOCKS_ATTACKS);
			}
			default -> {
				if (d.stat("durability", 0) > 0 && st.getMaxDamage() != (int) d.stat("durability")) return false;
				return d.stat("durability", 0) > 0 || st.getMaxDamage() == 0;
			}
		}
	}
}
