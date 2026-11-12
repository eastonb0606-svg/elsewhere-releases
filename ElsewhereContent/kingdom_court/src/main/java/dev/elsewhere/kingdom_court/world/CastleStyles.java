package dev.elsewhere.kingdom_court.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Per-faction castle look, read from data. File {@code data/<ns>/kingdom_castle/<faction path>.json} (faction id {@code <ns>:<path>}):
 * <pre>
 * { "palette": { "minecraft:stone_bricks": "minecraft:sandstone_bricks", "minecraft:spruce_planks": "minecraft:cut_sandstone" },
 *   "pieces": [
 *     {"type": "box",     "from": [x,y,z], "to": [x,y,z], "block": "minecraft:cut_sandstone", "hollow": false},
 *     {"type": "column",  "at": [x,y,z], "height": 9, "block": "...", "cap": "..."},
 *     {"type": "obelisk", "at": [x,y,z], "height": 14, "base": 3, "block": "...", "cap": "..."},
 *     {"type": "set",     "at": [x,y,z], "block": "..."} ] }
 * </pre>
 * {@code palette} replaces blocks one for one wherever Aldermark's layout (castle, towns and road) places them, keeping stairs, slabs, logs and
 * other shared properties. {@code pieces} are extra pieces added to the castle after the base layout (coordinates relative to the castle origin: x east, z south,
 * y 0 = floor; they must lie inside x and z -34..34, y -1..22; anything outside is skipped). They run only when the castle is generated into fresh
 * chunks, like the rest of the layout. Without a file a faction gets Aldermark's look.
 */
public final class CastleStyles {
	private CastleStyles() {}

	public record Piece(String type, int[] a, int[] b, Block block, @Nullable Block cap, int height, int base, boolean hollow) {}

	public static final class Style {
		public final Map<Block, Block> palette = new HashMap<>();
		public final List<Piece> pieces = new ArrayList<>();
		/** The optional {@code waterfront} object (jetty, boathouse, longship blocks) for waterfront realms; null when absent (defaults apply). */
		public @Nullable JsonObject waterfront;

		/** The replacement for a state (same properties where both blocks have them), or the state itself. */
		public BlockState map(BlockState s) {
			Block to = palette.get(s.getBlock());
			if (to == null) return s;
			BlockState out = to.defaultBlockState();
			for (Property<?> p : s.getProperties()) out = copy(s, out, p);
			return out;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		static BlockState copy(BlockState from, BlockState to, Property p) {
			if (!to.hasProperty(p)) return to;
			Comparable v = from.getValue(p);
			return p.getPossibleValues().contains(v) ? to.setValue(p, v) : to;
		}
	}

	static final Map<String, Style> STYLES = new ConcurrentHashMap<>();
	static boolean hooked;

	public static @Nullable Style of(Identifier faction) {
		return STYLES.get(faction.toString());
	}

	/** Registers a style in code (tests, or a module that prefers it to a data file). A data file for the same faction replaces it at the next reload. */
	public static void register(Identifier faction, Style style) {
		STYLES.put(faction.toString(), style);
	}

	public static void clear(Identifier faction) {
		STYLES.remove(faction.toString());
	}

	public static void init() {
		if (hooked) return;
		hooked = true;
		ServerLifecycleEvents.SERVER_STARTED.register(CastleStyles::load);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s, rm, ok) -> load(s));
	}

	/** Loads every {@code kingdom_castle/*.json} of the server's data packs. */
	public static void load(MinecraftServer server) {
		var found = server.getResourceManager().listResources("kingdom_castle", id -> id.getPath().endsWith(".json"));
		for (var e : found.entrySet()) {
			Identifier rid = e.getKey();
			String path = rid.getPath().substring("kingdom_castle/".length(), rid.getPath().length() - 5);
			try (Reader r = e.getValue().openAsReader()) {
				Style s = parse(JsonParser.parseReader(r).getAsJsonObject());
				STYLES.put(rid.getNamespace() + ":" + path, s);
			} catch (Exception ex) {
				CourtContent.LOG.warn("castle style {} is invalid: {}", rid, ex.toString());
			}
		}
	}

	static @Nullable Block block(String id) {
		Identifier i = Identifier.tryParse(id);
		return i != null && BuiltInRegistries.BLOCK.containsKey(i) ? BuiltInRegistries.BLOCK.getValue(i) : null;
	}

	static int[] vec(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
	}

	public static Style parse(JsonObject o) {
		Style s = new Style();
		if (o.has("waterfront") && o.get("waterfront").isJsonObject()) s.waterfront = o.getAsJsonObject("waterfront");
		if (o.has("palette")) for (var e : o.getAsJsonObject("palette").entrySet()) {
			Block from = block(e.getKey()), to = block(e.getValue().getAsString());
			if (from != null && to != null) s.palette.put(from, to);
		}
		if (o.has("pieces")) for (JsonElement pe : o.getAsJsonArray("pieces")) {
			JsonObject p = pe.getAsJsonObject();
			Block b = block(p.get("block").getAsString());
			if (b == null) continue;
			String type = p.get("type").getAsString();
			int[] at = type.equals("box") ? vec(p.get("from")) : vec(p.get("at"));
			int[] to = type.equals("box") ? vec(p.get("to")) : at;
			Block cap = p.has("cap") ? block(p.get("cap").getAsString()) : null;
			s.pieces.add(new Piece(type, at, to, b, cap, p.has("height") ? p.get("height").getAsInt() : 1, p.has("base") ? p.get("base").getAsInt() : 3, p.has("hollow") && p.get("hollow").getAsBoolean()));
		}
		return s;
	}

	/** Places the extra pieces of {@code style} (castle only), clipped to the placer's chunk. */
	static void applyPieces(Style style, BlockPos o, CastleLayout.Placer pl) {
		BoundingBox castle = CastleLayout.box(CastleLayout.Part.CASTLE, o);
		for (Piece p : style.pieces) {
			switch (p.type()) {
				case "box" -> {
					int x1 = Math.min(p.a()[0], p.b()[0]), x2 = Math.max(p.a()[0], p.b()[0]), y1 = Math.min(p.a()[1], p.b()[1]), y2 = Math.max(p.a()[1], p.b()[1]), z1 = Math.min(p.a()[2], p.b()[2]), z2 = Math.max(p.a()[2], p.b()[2]);
					for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
						if (p.hollow() && x > x1 && x < x2 && y > y1 && y < y2 && z > z1 && z < z2) continue;
						put(pl, castle, o, x, y, z, p.block());
					}
				}
				case "column" -> {
					for (int y = 0; y < p.height(); y++) put(pl, castle, o, p.a()[0], p.a()[1] + y, p.a()[2], p.block());
					if (p.cap() != null) put(pl, castle, o, p.a()[0], p.a()[1] + p.height(), p.a()[2], p.cap());
				}
				case "obelisk" -> {
					int w = Math.max(1, p.base());
					int tiers = Math.max(1, p.height());
					for (int y = 0; y < tiers; y++) {
						int r = (w - 1) / 2 - (y >= tiers - 4 ? y - (tiers - 4) : 0);
						if (y < 2) r = (w - 1) / 2 + 1;
						r = Math.max(0, r);
						for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) put(pl, castle, o, p.a()[0] + dx, p.a()[1] + y, p.a()[2] + dz, p.block());
					}
					put(pl, castle, o, p.a()[0], p.a()[1] + tiers, p.a()[2], p.cap() != null ? p.cap() : p.block());
				}
				case "set" -> put(pl, castle, o, p.a()[0], p.a()[1], p.a()[2], p.block());
				default -> {
				}
			}
		}
	}

	static void put(CastleLayout.Placer pl, BoundingBox castle, BlockPos o, int x, int y, int z, Block b) {
		int wx = o.getX() + x, wy = o.getY() + y, wz = o.getZ() + z;
		if (!castle.isInside(wx, wy, wz)) return;
		pl.setRaw(wx, wy, wz, b.defaultBlockState());
	}
}
