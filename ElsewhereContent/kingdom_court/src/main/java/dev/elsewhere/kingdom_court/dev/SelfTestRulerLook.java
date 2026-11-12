package dev.elsewhere.kingdom_court.dev;

import dev.elsewhere.kingdom_court.look.AldermarkRulerLook;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.io.InputStream;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * 3.0.0 final review B10 (s-rulerarmor): Aldermark's King and every Lord, equipped by their own loadout, wear
 * no undyed vanilla diamond and no plain netherite: those pieces draw the baseline Aldermark plate (royal-blue surcoat, cream tower)
 * while the item, armor attributes, trim and slot stay vanilla; iron, chainmail, dyed leather and the gold crown keep the baseline
 * look; the Marshal's warhorse armour draws Aldermark barding; and the assets and textures ship.
 */
final class SelfTestRulerLook {
	private SelfTestRulerLook() {}

	static boolean res(String path) {
		try (InputStream in = SelfTestRulerLook.class.getResourceAsStream("/" + path)) {
			return in != null;
		} catch (Exception e) {
			return false;
		}
	}

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			boolean on = AldermarkRulerLook.ENABLED, noDiamond = true, styled = true, kept = true, king = false, crown = false;
			int swapped = 0;
			StringBuilder bad = new StringBuilder();
			for (Ruler r : Ruler.values()) {
				if (r.type == null) { bad.append(r.key).append(":unregistered "); styled = false; continue; }
				CourtRuler e = (CourtRuler) r.type.create(l, EntitySpawnReason.COMMAND);
				try {
					e.applyLoadout();
					for (EquipmentSlot s : AldermarkRulerLook.ARMOR) {
						ItemStack st = e.getItemBySlot(s);
						if (st.isEmpty()) continue;
						var a = AldermarkRulerLook.assetOf(st);
						if (AldermarkRulerLook.vanillaDiamond(st) || EquipmentAssets.NETHERITE.equals(a)) { noDiamond = false; bad.append(r.key).append(':').append(s.getName()).append('=').append(a).append(' '); }
						if (!AldermarkRulerLook.styled(st)) styled = false;
						if (AldermarkRulerLook.ASSET.equals(a)) {
							swapped++;
							kept &= st.get(DataComponents.EQUIPPABLE).slot() == s && Objects.equals(st.get(DataComponents.ATTRIBUTE_MODIFIERS), st.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS));
							if (r == Ruler.KING && s == EquipmentSlot.CHEST && st.is(Items.NETHERITE_CHESTPLATE) && st.get(DataComponents.TRIM) != null) king = true;
						}
						if (r == Ruler.KING && s == EquipmentSlot.HEAD && st.is(Items.GOLDEN_HELMET) && EquipmentAssets.GOLD.equals(a)) crown = true;
					}
				} finally {
					e.discard();
				}
			}
			SelfTest.check("B10 ruler armor: no Aldermark ruler wears undyed vanilla diamond or plain netherite (King and 5 Lords, real loadouts)", !on || noDiamond, bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: every such piece draws the baseline Aldermark plate (blue surcoat, cream tower) and keeps its vanilla item, attributes, trim and slot (" + swapped + " pieces)",
				!on || (styled && kept && king && swapped > 0), null);
			SelfTest.check("B10 ruler armor: the King keeps his gold crown helm (the Aldermark baseline look)", crown, null);
			var horse = EntityTypes.HORSE.create(l, EntitySpawnReason.COMMAND);
			horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
			AldermarkRulerLook.dressMount(horse);
			ItemStack bard = horse.getItemBySlot(EquipmentSlot.BODY);
			SelfTest.check("B10 ruler armor: the Marshal's warhorse armour draws Aldermark barding (still diamond horse armour by value)",
				!on || (AldermarkRulerLook.BARDING.equals(AldermarkRulerLook.assetOf(bard)) && bard.is(Items.DIAMOND_HORSE_ARMOR)), AldermarkRulerLook.assetOf(bard));
			horse.discard();
			SelfTest.check("B10 ruler armor: aldermark_ruler (humanoid + leggings) and aldermark_barding (horse_body) assets and textures ship",
				res("assets/elsewhere/equipment/aldermark_ruler.json") && res("assets/elsewhere/textures/entity/equipment/humanoid/aldermark_ruler.png")
					&& res("assets/elsewhere/textures/entity/equipment/humanoid_leggings/aldermark_ruler.png") && res("assets/elsewhere/equipment/aldermark_barding.json")
					&& res("assets/elsewhere/textures/entity/equipment/horse_body/aldermark_barding.png"), null);
			return 0;
		});
	}
}
