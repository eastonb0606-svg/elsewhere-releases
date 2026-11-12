package dev.elsewhere.kingdom.client.lod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.KingdomContent;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Baked LOD skins (ARMIES.md 3.7): one 64x64 texture per unit type with skin, armour, trim colour and tabard colour painted into the
 * outer layer, made by {@code art2/kingdom/bake_lod_skins.py}; index {@code assets/elsewhere/lod/lod_skins.json}, textures
 * {@code textures/entity/kingdom/lod/<unit>.png}. The rank impostors (ARMIES.md 3.6) draw one layer only, so each unit also has a flat
 * copy ({@code lod/flat/<unit>.png}, outer layer composited onto the inner one) and all flat copies sit in one 1024x1024 atlas
 * {@link #ATLAS} (16 x 16 slots of 64). Loaded once, lazily, on the first far soldier or rank lookup.
 * <p>
 * The client only knows a soldier's skin, tabard colour and armour, so a baked skin is used only when all three match the unit it was
 * baked for; otherwise the far tier keeps the real armour (without trims) and the tabard, which is always correct.
 */
public final class LodSkins {
	private LodSkins() {}

	public static final Identifier INDEX = KingdomContent.id("lod/lod_skins.json");
	public static final Identifier ATLAS = KingdomContent.id("textures/entity/kingdom/lod/atlas.png");
	public static final int ATLAS_SIZE = 1024, SLOT = 64, COLUMNS = 16;

	/**
	 * One baked skin: the look it was baked for (armour items: AIR for none or for a non-armour head item such as a banner). {@code baked}
	 * is the two-layer texture the far soldier tier draws, {@code flat} the one-layer copy for rank figures.
	 */
	public record Entry(Identifier unit, Identifier skin, Identifier baked, Identifier flat, int slot, int tabardRgb, Item head, Item chest, Item legs, Item feet) {
		/** Atlas UVs {u0, v0, u1, v1} (0-1) of this unit's flat copy in {@link #ATLAS}. */
		public float[] atlasUv() {
			float u0 = (slot % COLUMNS) * SLOT / (float) ATLAS_SIZE, v0 = (slot / COLUMNS) * SLOT / (float) ATLAS_SIZE;
			return new float[] {u0, v0, u0 + SLOT / (float) ATLAS_SIZE, v0 + SLOT / (float) ATLAS_SIZE};
		}
	}

	private static Map<Identifier, List<Entry>> bySkin;
	private static Map<Identifier, Entry> byUnit;

	private static synchronized void load() {
		if (bySkin != null) return;
		Map<Identifier, List<Entry>> skins = new HashMap<>();
		Map<Identifier, Entry> units = new HashMap<>();
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(INDEX);
			if (res.isPresent()) {
				try (Reader r = res.get().openAsReader()) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					for (JsonElement el : o.getAsJsonArray("skins")) {
						JsonObject s = el.getAsJsonObject();
						JsonArray a = s.getAsJsonArray("armor");
						Identifier skin = Identifier.parse(s.get("skin").getAsString());
						Entry en = new Entry(Identifier.parse(s.get("unit").getAsString()), skin, Identifier.parse(s.get("baked").getAsString()),
								Identifier.parse(s.get("flat").getAsString()), s.get("slot").getAsInt(),
							Integer.parseInt(s.get("tabard").getAsString().substring(1), 16), armor(a, 0, EquipmentSlot.HEAD), armor(a, 1, EquipmentSlot.CHEST),
							armor(a, 2, EquipmentSlot.LEGS), armor(a, 3, EquipmentSlot.FEET));
						skins.computeIfAbsent(skin, k -> new ArrayList<>()).add(en);
						units.put(en.unit(), en);
					}
				}
			}
		} catch (Exception ex) {
			KingdomContent.LOG.warn("baked LOD skin index unreadable ({}); far soldiers keep their armour", ex.toString());
		}
		bySkin = skins;
		byUnit = units;
	}

	private static Item armor(JsonArray a, int i, EquipmentSlot slot) {
		if (a == null || i >= a.size() || a.get(i).isJsonNull()) return Items.AIR;
		Identifier id = Identifier.tryParse(a.get(i).getAsString());
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		// a banner worn as a hat is not drawn by the armour layer, so the render state shows no head armour for it
		return item == null || !HumanoidArmorLayer.shouldRender(new ItemStack(item), slot) ? Items.AIR : item;
	}

	/** The baked texture for this look, or null when no unit was baked with exactly this skin, tabard colour and armour. */
	public static @Nullable Identifier baked(@Nullable Identifier skin, int tabardArgb, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		if (skin == null) return null;
		if (bySkin == null) load();
		List<Entry> l = bySkin.get(skin);
		if (l == null) return null;
		int rgb = tabardArgb & 0xFFFFFF;
		for (Entry en : l) {
			if (en.tabardRgb() == rgb && en.head() == head.getItem() && en.chest() == chest.getItem() && en.legs() == legs.getItem() && en.feet() == feet.getItem())
				return en.baked();
		}
		return null;
	}

	/** For the rank impostors: the baked entry of a unit type (null if none). */
	public static @Nullable Entry forUnit(Identifier unit) {
		if (byUnit == null) load();
		return byUnit.get(unit);
	}

	/**
	 * For the rank impostors: the one-layer baked texture of a unit type (null if none). Matches the {@code (unit, faction)} override of
	 * {@code client.impostor.LodSkins}: {@code override = (unit, faction) -> dev.elsewhere.kingdom.client.lod.LodSkins.flat(unit)}.
	 */
	public static @Nullable Identifier flat(Identifier unit) {
		Entry en = forUnit(unit);
		return en == null ? null : en.flat();
	}

	/** For the rank impostors: the first baked entry made from this skin texture (null if none). */
	public static @Nullable Entry forSkin(Identifier skin) {
		if (bySkin == null) load();
		List<Entry> l = bySkin.get(skin);
		return l == null || l.isEmpty() ? null : l.get(0);
	}

	/** Number of baked skins in the index (for /kingdomlod). */
	public static int size() {
		if (byUnit == null) load();
		return byUnit.size();
	}
}
