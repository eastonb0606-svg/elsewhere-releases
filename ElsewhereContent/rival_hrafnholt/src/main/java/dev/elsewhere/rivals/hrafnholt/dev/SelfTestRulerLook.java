package dev.elsewhere.rivals.hrafnholt.dev;

import dev.elsewhere.rivals.hrafnholt.boss.Hrafn;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss;
import dev.elsewhere.rivals.hrafnholt.look.HrafnholtRulerLook;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 3.0.0 final review B10 (s-rulerarmor): every Hrafnholt boss and Risen champion, spawned for real and equipped by its own first-tick
 * loadout, wears no undyed vanilla diamond; every iron, gold or diamond piece draws the green-and-brown hrafnholt_ruler layer while
 * the item, its armor attributes, trim and slot stay vanilla; and the layer's asset and both textures ship.
 */
final class SelfTestRulerLook {
	private SelfTestRulerLook() {}

	static final List<HrafnholtBoss> SPAWNED = new ArrayList<>();
	static int waited;

	static boolean res(String path) {
		try (InputStream in = SelfTestRulerLook.class.getResourceAsStream("/" + path)) {
			return in != null;
		} catch (Exception e) {
			return false;
		}
	}

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			SelfTestBosses.flat(l, 600, 600, 640, 606);
			int i = 0;
			for (Hrafn h : Hrafn.values())
				for (boolean risen : new boolean[] {false, true}) {
					if (risen && h.risenType == null) continue;
					HrafnholtBoss b = SelfTestBosses.boss(l, h, risen, 601 + 3 * i++, 603);
					b.passive = true;
					SPAWNED.add(b);
				}
			return 3;   // the first tick runs each boss's own loadout
		});
		SelfTest.STEPS.add((srv, l) -> {
			// the freshly forced chunk takes a few ticks to start ticking entities: wait (bounded) until every boss's own first-tick loadout has run
			boolean ready = true;
			for (HrafnholtBoss b : SPAWNED) { boolean any = false; for (EquipmentSlot s : HrafnholtRulerLook.ARMOR) any |= !b.getItemBySlot(s).isEmpty(); ready &= any; }
			if (!ready && ++waited < 200) return -1;
			boolean on = HrafnholtRulerLook.ENABLED, noDiamond = true, styled = true, layer = true, kept = true, jarl = false;
			int metal = 0;
			StringBuilder bad = new StringBuilder();
			for (HrafnholtBoss b : SPAWNED) {
				String who = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath();
				for (EquipmentSlot s : HrafnholtRulerLook.ARMOR) {
					ItemStack st = b.getItemBySlot(s);
					if (st.isEmpty()) continue;
					if (HrafnholtRulerLook.vanillaDiamond(st)) { noDiamond = false; bad.append(who).append(':').append(s.getName()).append("=diamond "); }
					if (!HrafnholtRulerLook.styled(st)) { styled = false; bad.append(who).append(':').append(s.getName()).append('=').append(HrafnholtRulerLook.assetOf(st)).append(' '); }
					if (HrafnholtRulerLook.ASSET.equals(HrafnholtRulerLook.assetOf(st))) {
						metal++;
						var eq = st.get(DataComponents.EQUIPPABLE);
						kept &= eq.slot() == s && Objects.equals(st.get(DataComponents.ATTRIBUTE_MODIFIERS), st.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS));
						if (b.getType() == Hrafn.JARL.type && s == EquipmentSlot.CHEST && st.is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE) && st.get(DataComponents.TRIM) != null) jarl = true;
					}
				}
			}
			SelfTest.check("B10 ruler armor: no Hrafnholt boss or Risen champion wears undyed vanilla diamond (" + SPAWNED.size() + " bosses, real loadouts)", !on || noDiamond, bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: every iron/gold/diamond piece on them draws the green-and-brown hrafnholt_ruler layer (" + metal + " pieces)", !on || (styled && metal > 0), bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (the Jarl's trimmed diamond chestplate)", !on || (kept && jarl), null);
			layer = res("assets/elsewhere/equipment/hrafnholt_ruler.json") && res("assets/elsewhere/textures/entity/equipment/humanoid/hrafnholt_ruler.png")
				&& res("assets/elsewhere/textures/entity/equipment/humanoid_leggings/hrafnholt_ruler.png");
			SelfTest.check("B10 ruler armor: the hrafnholt_ruler equipment asset and its humanoid + leggings textures ship", layer, null);
			for (HrafnholtBoss b : SPAWNED) b.discard();
			SPAWNED.clear();
			return 0;
		});
	}
}
