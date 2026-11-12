package dev.elsewhere.kingdom_court.look;

import java.util.Optional;
import java.util.Set;
import dev.elsewhere.kingdom_court.CourtContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * 3.0.0 final review B10 (s-rulerarmor): Aldermark's King and Lords (the court rulers) keep their real vanilla armor items (armor, toughness, trims, drop
 * chances unchanged) but draw Aldermark's own armor layer instead of cyan vanilla diamond or black netherite: the baseline Aldermark knight: steel plate under a royal-blue surcoat with a cream hem and the cream Aldermark tower, a blue crest and cream brow band, gilt rims; iron, chainmail, dyed leather and the gold crown keep the vanilla baseline look.
 * Only the item's equippable asset is swapped, once, when the loadout is applied (no ticking). Textures and the asset JSON come
 * from rival_hrafnholt/tools/gen_ruler_armor.py. Kill switch {@code ruler_armor_looks} (JVM flag
 * -Delsewhere.overhaul3.ruler_armor_looks=false or config/elsewhere-overhaul3.json): off restores the vanilla looks.
 */
public final class AldermarkRulerLook {
	private AldermarkRulerLook() {}

	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, CourtContent.id("aldermark_ruler"));
	public static final boolean ENABLED = flag("ruler_armor_looks");
	/** The vanilla looks that are swapped for the faction layer. */
	public static final Set<ResourceKey<EquipmentAsset>> SWAPPED = Set.of(EquipmentAssets.DIAMOND, EquipmentAssets.NETHERITE);
	public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Gives every worn diamond or netherite piece of {@code m} the faction layer (call after the loadout is set). */
	public static void dress(Mob m) {
		if (!ENABLED) return;
		for (EquipmentSlot s : ARMOR) restyle(m.getItemBySlot(s));
	}

	/** Swaps a vanilla look in {@link #SWAPPED} for the faction layer; the item, its stats, trim and slot stay. */
	public static boolean restyle(ItemStack st) {
		if (!ENABLED || st.isEmpty()) return false;
		Equippable e = st.get(DataComponents.EQUIPPABLE);
		if (e == null || e.assetId().isEmpty() || !SWAPPED.contains(e.assetId().get())) return false;
		st.set(DataComponents.EQUIPPABLE, withAsset(e, ASSET));
		return true;
	}

	public static Equippable withAsset(Equippable e, ResourceKey<EquipmentAsset> asset) {
		return new Equippable(e.slot(), e.equipSound(), Optional.of(asset), e.cameraOverlay(), e.allowedEntities(), e.dispensable(), e.swappable(),
			e.damageOnHurt(), e.equipOnInteract(), e.canBeSheared(), e.shearingSound());
	}

	/** The asset a worn piece draws with, or null. */
	public static ResourceKey<EquipmentAsset> assetOf(ItemStack st) {
		Equippable e = st.isEmpty() ? null : st.get(DataComponents.EQUIPPABLE);
		return e == null ? null : e.assetId().orElse(null);
	}

	/** True for a piece that draws as undyed vanilla diamond (the cyan, off-palette look B10 removes). */
	public static boolean vanillaDiamond(ItemStack st) {
		return EquipmentAssets.DIAMOND.equals(assetOf(st)) && st.get(DataComponents.DYED_COLOR) == null;
	}

	/** Faction-styled: nothing worn, dyed leather, a custom (elsewhere) layer, or a vanilla look this faction keeps (iron, chainmail and the gold crown are the Aldermark baseline). */
	public static boolean styled(ItemStack st) {
		ResourceKey<EquipmentAsset> a = assetOf(st);
		return a == null || st.get(DataComponents.DYED_COLOR) != null || !SWAPPED.contains(a);
	}

	static boolean flag(String key) {
		String p = System.getProperty("elsewhere.overhaul3." + key);
		if (p != null) return !"false".equalsIgnoreCase(p);
		try {
			var f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (java.nio.file.Files.isRegularFile(f)) {
				var o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
				if (o.has(key)) return o.get(key).getAsBoolean();
			}
		} catch (Exception e) {
			// default on
		}
		return true;
	}

	/** The Marshal's warhorse barding: royal-blue enamel over steel instead of cyan diamond horse armour (horse_body layer). */
	public static final ResourceKey<EquipmentAsset> BARDING = ResourceKey.create(EquipmentAssets.ROOT_ID, CourtContent.id("aldermark_barding"));

	/** Restyles a mount's diamond horse armour as Aldermark barding (armour value unchanged). */
	public static void dressMount(net.minecraft.world.entity.Entity mount) {
		if (!ENABLED || !(mount instanceof Mob m)) return;
		ItemStack st = m.getItemBySlot(EquipmentSlot.BODY);
		if (EquipmentAssets.DIAMOND.equals(assetOf(st))) st.set(DataComponents.EQUIPPABLE, withAsset(st.get(DataComponents.EQUIPPABLE), BARDING));
	}

	private static boolean registered;

	/** Rulers saved before 3.0 keep their loadout (it is not re-applied on load): give them the look once when they load. Event-driven, no ticking. */
	public static void init() {
		if (registered || !ENABLED) return;
		registered = true;
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof dev.elsewhere.kingdom_court.ruler.CourtRuler r) {
				dress(r);
				if (r.getVehicle() != null) dressMount(r.getVehicle());
			}
		});
	}
}
