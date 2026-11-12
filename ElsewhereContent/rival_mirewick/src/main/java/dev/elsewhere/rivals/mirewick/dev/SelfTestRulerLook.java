package dev.elsewhere.rivals.mirewick.dev;

import dev.elsewhere.rivals.mirewick.boss.Magnate;
import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import dev.elsewhere.rivals.mirewick.look.MirewickRulerLook;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * 3.0.0 final review B10 (s-rulerarmor): all five Magnates, spawned for real and equipped by their own first-tick loadout, wear no
 * undyed vanilla diamond; Thwaite's and Sorrel's iron and diamond pieces draw the purple, crimson and gold mirewick_ruler layer while
 * the item, its armor attributes, trim and slot stay vanilla; and the layer's asset and both textures ship.
 */
final class SelfTestRulerLook {
	private SelfTestRulerLook() {}

	static final List<MagnateBoss> SPAWNED = new ArrayList<>();
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
			int y = SelfTest.Y;
			// inside the chunks the harness forces at start (cx -5..5, cz -6..36), so the Magnates really tick and run their first-tick loadout
			for (int x = 0; x <= 30; x++) for (int z = 401; z <= 405; z++) {
				l.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 2);
				for (int yy = y; yy < y + 4; yy++) l.setBlock(new BlockPos(x, yy, z), Blocks.AIR.defaultBlockState(), 2);
			}
			int i = 0;
			for (Magnate m : Magnate.values()) {
				MagnateBoss b = m.type.create(l, EntitySpawnReason.COMMAND);
				b.snapTo(2.5 + 5 * i++, y, 403.5, 0F, 0F);
				b.passive = true;
				l.addFreshEntity(b);
				SPAWNED.add(b);
			}
			return 3;   // the first tick runs each Magnate's own loadout
		});
		SelfTest.STEPS.add((srv, l) -> {
			// wait (bounded) until every Magnate's own first-tick loadout has run, instead of trusting a fixed tick count
			boolean ready = true;
			for (MagnateBoss b : SPAWNED) ready &= !b.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
			if (!ready && ++waited < 200) return -1;
			boolean on = MirewickRulerLook.ENABLED, noDiamond = true, styled = true, kept = true, thwaite = false;
			int metal = 0;
			StringBuilder bad = new StringBuilder();
			for (MagnateBoss b : SPAWNED) {
				String who = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath();
				for (EquipmentSlot s : MirewickRulerLook.ARMOR) {
					ItemStack st = b.getItemBySlot(s);
					if (st.isEmpty()) continue;
					if (MirewickRulerLook.vanillaDiamond(st)) { noDiamond = false; bad.append(who).append(':').append(s.getName()).append("=diamond "); }
					if (!MirewickRulerLook.styled(st)) { styled = false; bad.append(who).append(':').append(s.getName()).append('=').append(MirewickRulerLook.assetOf(st)).append(' '); }
					if (MirewickRulerLook.ASSET.equals(MirewickRulerLook.assetOf(st))) {
						metal++;
						kept &= st.get(DataComponents.EQUIPPABLE).slot() == s && Objects.equals(st.get(DataComponents.ATTRIBUTE_MODIFIERS), st.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS));
						if (b.getType() == Magnate.THWAITE.type && s == EquipmentSlot.CHEST && st.is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE) && st.get(DataComponents.TRIM) != null) thwaite = true;
					}
				}
			}
			SelfTest.check("B10 ruler armor: no Mirewick Magnate wears undyed vanilla diamond (" + SPAWNED.size() + " Magnates, real loadouts)", !on || noDiamond, bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: every iron/gold/diamond piece on a Magnate draws the purple-crimson-gold mirewick_ruler layer (" + metal + " pieces)", !on || (styled && metal > 0), bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (Thwaite's trimmed diamond chestplate)", !on || (kept && thwaite), null);
			boolean layer = res("assets/elsewhere/equipment/mirewick_ruler.json") && res("assets/elsewhere/textures/entity/equipment/humanoid/mirewick_ruler.png")
				&& res("assets/elsewhere/textures/entity/equipment/humanoid_leggings/mirewick_ruler.png");
			SelfTest.check("B10 ruler armor: the mirewick_ruler equipment asset and its humanoid + leggings textures ship", layer, null);
			for (MagnateBoss b : SPAWNED) b.discard();
			SPAWNED.clear();
			return 0;
		});
	}
}
