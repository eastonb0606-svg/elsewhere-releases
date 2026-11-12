package dev.elsewhere.kingdom_court.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 "no valuable blocks in structures" (RIVALS-DECISIONS 16, final review B9). Every block the realm writes (castle, towns, road, walls, kit,
 * faction style palettes and pieces, waterfront) goes through {@link CastleLayout.Placer}, which passes it through {@link #safe}: a valuable
 * block becomes its lore-fit stand-in (gold is yellow terracotta, lapis is blue glazed terracotta, ...). This holds on every path: fv 1 and
 * fv 2 starts, saved 2.x starts still finishing, {@code buildNow}, and every {@code castle_kit} / {@code town_kit} / {@code realm_fit} setting,
 * so no castle can be mined for wealth. The check is one identity-set lookup per write (no level reads, no per-tick work).
 * <p>
 * Valuable means a block that is, or turns back into, precious material or a rare treasure: gold, iron, copper, diamond, emerald, lapis,
 * redstone, coal and netherite storage blocks (raw and weathered copper too), every ore (any namespace), ancient debris, gilded blackstone,
 * beacons, conduits, lodestones, enchanting tables, jukeboxes, heavy cores and the dragon egg.
 */
public final class Valuables {
	private Valuables() {}

	/** Built once, lazily, from the frozen block registry (worldgen threads may call {@link #safe} concurrently: holder idiom). */
	private static final class Holder {
		static final Set<Block> SET = build();
	}

	private static Set<Block> build() {
		Set<Block> s = Collections.newSetFromMap(new IdentityHashMap<>());
		Collections.addAll(s, Blocks.GOLD_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK,
			Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.COAL_BLOCK, Blocks.NETHERITE_BLOCK,
			Blocks.ANCIENT_DEBRIS, Blocks.GILDED_BLACKSTONE, Blocks.BEACON, Blocks.CONDUIT, Blocks.LODESTONE, Blocks.ENCHANTING_TABLE, Blocks.JUKEBOX,
			Blocks.HEAVY_CORE, Blocks.DRAGON_EGG);
		// every copper storage block in any weathering or waxed state (all scrape back to a copper block, then ingots)
		Blocks.COPPER_BLOCK.forEach(s::add);
		// every ore, vanilla and mod
		for (Block b : BuiltInRegistries.BLOCK) if (BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("_ore")) s.add(b);
		return s;
	}

	public static boolean isValuable(BlockState s) {
		return Holder.SET.contains(s.getBlock());
	}

	public static boolean isValuable(Block b) {
		return Holder.SET.contains(b);
	}

	/** The state itself, or the lore-fit non-valuable stand-in when it is a valuable block. */
	public static BlockState safe(BlockState s) {
		return Holder.SET.contains(s.getBlock()) ? standIn(s.getBlock()) : s;
	}

	/** Stand-in for a valuable block (STRUCTURES.md 3: gold is yellow terracotta, lapis blue glazed terracotta; the rest by colour and material). */
	static BlockState standIn(Block b) {
		Block r;
		if (b == Blocks.GOLD_BLOCK || b == Blocks.RAW_GOLD_BLOCK) r = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW);
		else if (b == Blocks.LAPIS_BLOCK) r = Blocks.GLAZED_TERRACOTTA.pick(DyeColor.BLUE);
		else if (b == Blocks.IRON_BLOCK || b == Blocks.RAW_IRON_BLOCK || b == Blocks.HEAVY_CORE) r = Blocks.POLISHED_ANDESITE;
		else if (b == Blocks.RAW_COPPER_BLOCK) r = Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE);
		else if (b == Blocks.DIAMOND_BLOCK) r = Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE);
		else if (b == Blocks.EMERALD_BLOCK) r = Blocks.DYED_TERRACOTTA.pick(DyeColor.LIME);
		else if (b == Blocks.REDSTONE_BLOCK) r = Blocks.DYED_TERRACOTTA.pick(DyeColor.RED);
		else if (b == Blocks.COAL_BLOCK) r = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLACK);
		else if (b == Blocks.NETHERITE_BLOCK || b == Blocks.DRAGON_EGG) r = Blocks.POLISHED_BLACKSTONE;
		else if (b == Blocks.GILDED_BLACKSTONE) r = Blocks.CHISELED_POLISHED_BLACKSTONE;
		else if (b == Blocks.ANCIENT_DEBRIS) r = Blocks.NETHERRACK;
		else if (b == Blocks.BEACON || b == Blocks.CONDUIT) r = Blocks.SEA_LANTERN;
		else if (b == Blocks.LODESTONE) r = Blocks.CHISELED_STONE_BRICKS;
		else if (b == Blocks.ENCHANTING_TABLE) r = Blocks.BOOKSHELF;
		else if (b == Blocks.JUKEBOX) r = Blocks.NOTE_BLOCK;
		else if (Blocks.COPPER_BLOCK.asList().contains(b)) r = Blocks.CUT_COPPER.waxed().unaffected();
		else {
			// an ore: its host rock
			String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
			r = p.startsWith("deepslate_") ? Blocks.DEEPSLATE : p.startsWith("nether_") ? Blocks.NETHERRACK : Blocks.STONE;
		}
		return r.defaultBlockState();
	}

	/**
	 * Every valuable block a {@code kingdom_castle/*.json} style names as a palette target, a piece block or cap, or anywhere in its waterfront
	 * object (shipped data as the server loaded it, not test registrations). Palette keys are fine: mapping gold away is how a style removes it.
	 * Returns "file: where -> block" lines; empty means clean. The Placer filter would neutralise them anyway; this keeps the data honest.
	 */
	public static List<String> paletteFindings(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		var found = server.getResourceManager().listResources("kingdom_castle", id -> id.getPath().endsWith(".json"));
		for (var e : found.entrySet()) {
			String file = e.getKey().toString();
			try (Reader r = e.getValue().openAsReader()) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				if (o.has("palette")) for (Map.Entry<String, JsonElement> pe : o.getAsJsonObject("palette").entrySet())
					flag(out, file, "palette " + pe.getKey(), pe.getValue());
				if (o.has("pieces")) for (JsonElement pe : o.getAsJsonArray("pieces")) {
					JsonObject p = pe.getAsJsonObject();
					flag(out, file, "piece " + p.get("type"), p.get("block"));
					flag(out, file, "piece cap", p.get("cap"));
				}
				if (o.has("waterfront")) walk(out, file, "waterfront", o.get("waterfront"));
			} catch (Exception ex) {
				out.add(file + ": unreadable (" + ex + ")");
			}
		}
		return out;
	}

	/** Number of {@code kingdom_castle/*.json} files the server loaded (the palette check reports it so an empty pass is visible). */
	public static int paletteFiles(MinecraftServer server) {
		return server.getResourceManager().listResources("kingdom_castle", id -> id.getPath().endsWith(".json")).size();
	}

	private static void walk(List<String> out, String file, String where, JsonElement e) {
		if (e == null || e.isJsonNull()) return;
		if (e.isJsonObject()) for (Map.Entry<String, JsonElement> c : e.getAsJsonObject().entrySet()) walk(out, file, where + "." + c.getKey(), c.getValue());
		else if (e.isJsonArray()) for (JsonElement c : e.getAsJsonArray()) walk(out, file, where, c);
		else flag(out, file, where, e);
	}

	private static void flag(List<String> out, String file, String where, @Nullable JsonElement v) {
		if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) return;
		Identifier id = Identifier.tryParse(v.getAsString());
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return;
		Block b = BuiltInRegistries.BLOCK.getValue(id);
		if (isValuable(b)) out.add(file + ": " + where + " -> " + id);
	}
}
