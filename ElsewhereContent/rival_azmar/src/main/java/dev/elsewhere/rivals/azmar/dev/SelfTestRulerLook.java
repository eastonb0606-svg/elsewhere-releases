package dev.elsewhere.rivals.azmar.dev;

import dev.elsewhere.rivals.azmar.boss.AzmarAmir;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.look.AzmarRulerLook;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * 3.0.0 final review B10 + K29 (s-rulerarmor): the Sultana, the Grand Wazir, the Falconer and the Amir, spawned for real and equipped
 * by their own first-tick loadout, wear no undyed vanilla diamond (the Sultana's cyan chestplate is gone); every iron, gold or
 * diamond piece draws the Egyptian azmar_ruler layer while the item, armor attributes, trim and slot stay vanilla; the custom
 * headpieces keep their own looks; and the Amir's war camel wears a real saddle that draws the gold-and-lapis caparison.
 */
final class SelfTestRulerLook {
	private SelfTestRulerLook() {}

	static final List<AzmarBoss> SPAWNED = new ArrayList<>();
	static int waited;

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			SelfTest.clean(l);
			int y = SelfTestBosses.y();
			for (int x = 40; x <= 70; x++) for (int z = -4; z <= 4; z++) {
				l.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 2);
				for (int yy = y; yy < y + 5; yy++) l.setBlock(new BlockPos(x, yy, z), Blocks.AIR.defaultBlockState(), 2);
			}
			int i = 0;
			for (EntityType<? extends AzmarBoss> t : List.<EntityType<? extends AzmarBoss>>of(AzmarBosses.SULTANA, AzmarBosses.WAZIR, AzmarBosses.FALCONER, AzmarBosses.AMIR)) {
				AzmarBoss b = SelfTestBosses.spawn(l, t, 42 + 7 * i++, 0);
				b.passive = true;
				SPAWNED.add(b);
			}
			return 5;   // first ticks: each boss's own loadout, and the Amir mounts his war camel
		});
		SelfTest.STEPS.add((srv, l) -> {
			// wait (bounded) until every boss's own first-tick loadout has run, instead of trusting a fixed tick count
			boolean ready = true;
			for (AzmarBoss b : SPAWNED) { boolean any = false; for (EquipmentSlot s : AzmarRulerLook.ARMOR) any |= !b.getItemBySlot(s).isEmpty(); ready &= any; }
			if (!ready && ++waited < 200) return -1;
			boolean on = AzmarRulerLook.ENABLED, noDiamond = true, styled = true, kept = true, sultana = false, heads = true;
			int metal = 0;
			StringBuilder bad = new StringBuilder();
			for (AzmarBoss b : SPAWNED) {
				String who = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath();
				for (EquipmentSlot s : AzmarRulerLook.ARMOR) {
					ItemStack st = b.getItemBySlot(s);
					if (st.isEmpty()) continue;
					if (AzmarRulerLook.vanillaDiamond(st)) { noDiamond = false; bad.append(who).append(':').append(s.getName()).append("=diamond "); }
					if (!AzmarRulerLook.styled(st)) { styled = false; bad.append(who).append(':').append(s.getName()).append('=').append(AzmarRulerLook.assetOf(st)).append(' '); }
					var a = AzmarRulerLook.assetOf(st);
					if (s == EquipmentSlot.HEAD && (a == null || !"elsewhere".equals(a.identifier().getNamespace()) || AzmarRulerLook.ASSET.equals(a))) heads = false;
					if (AzmarRulerLook.ASSET.equals(a)) {
						metal++;
						kept &= st.get(DataComponents.EQUIPPABLE).slot() == s && Objects.equals(st.get(DataComponents.ATTRIBUTE_MODIFIERS), st.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS));
						if (b.getType() == AzmarBosses.SULTANA && s == EquipmentSlot.CHEST && st.is(Items.DIAMOND_CHESTPLATE) && st.get(DataComponents.TRIM) != null) sultana = true;
					}
				}
			}
			SelfTest.check("B10 ruler armor: no Azmar boss wears undyed vanilla diamond (" + SPAWNED.size() + " bosses, real loadouts; the Sultana's chestplate included)", !on || noDiamond, bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: every iron/gold/diamond piece on an Azmar boss draws the Egyptian gold-and-lapis azmar_ruler layer (" + metal + " pieces)", !on || (styled && metal > 0), bad.length() == 0 ? null : bad.toString().trim());
			SelfTest.check("B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot; the 3D headpieces keep their own looks", !on || (kept && sultana && heads), null);
			SelfTest.check("B10 ruler armor: the azmar_ruler equipment asset and its humanoid + leggings textures ship", SelfTest.res("assets/elsewhere/equipment/azmar_ruler.json")
				&& SelfTest.res("assets/elsewhere/textures/entity/equipment/humanoid/azmar_ruler.png") && SelfTest.res("assets/elsewhere/textures/entity/equipment/humanoid_leggings/azmar_ruler.png"), null);
			AzmarAmir amir = (AzmarAmir) SPAWNED.get(3);
			var camel = amir.camel();
			ItemStack saddle = camel == null ? ItemStack.EMPTY : camel.getItemBySlot(EquipmentSlot.SADDLE);
			SelfTest.check("K29 caparison: the Amir rides his war camel, whose real saddle draws the gold-and-lapis azmar_caparison (camel still saddled)",
				!on || (amir.mounted() && saddle.is(Items.SADDLE) && AzmarRulerLook.CAPARISON.equals(AzmarRulerLook.assetOf(saddle)) && camel.isSaddled()),
				camel == null ? "no camel" : AzmarRulerLook.assetOf(saddle));
			String json = "";
			try (var in = SelfTestRulerLook.class.getResourceAsStream("/assets/elsewhere/equipment/azmar_caparison.json")) {
				if (in != null) json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
			SelfTest.check("K29 caparison: the asset draws the caparison texture on a camel and the vanilla saddle on every other saddled animal",
				json.contains("\"camel_saddle\"") && json.contains("elsewhere:azmar_caparison") && json.contains("\"horse_saddle\"") && json.contains("minecraft:saddle")
					&& SelfTest.res("assets/elsewhere/textures/entity/equipment/camel_saddle/azmar_caparison.png"), null);
			if (camel != null) camel.discard();
			for (AzmarBoss b : SPAWNED) b.discard();
			SPAWNED.clear();
			return 0;
		});
	}
}
