package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.Reg;
import dev.elsewhere.stub.petalwind_glasswaste.SetBonus;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

/** Glasswaste set bonus registration (Sunwright: full set = Glare immunity; per-piece Heat resistance is in {@link Heat}). */
public final class GlasswasteRules {
	private GlasswasteRules() {}

	public static void init() {
		SunwrightPerks.init();
		SetBonus.register(new SetBonus(Reg.id("sunwright"), Map.of(
			EquipmentSlot.HEAD, List.of(GlasswasteItems.SUNWRIGHT_HELMET),
			EquipmentSlot.CHEST, List.of(GlasswasteItems.SUNWRIGHT_CHESTPLATE, GlasswasteItems.SUNWRIGHT_PLATE),
			EquipmentSlot.LEGS, List.of(GlasswasteItems.SUNWRIGHT_LEGGINGS), EquipmentSlot.FEET, List.of(GlasswasteItems.SUNWRIGHT_BOOTS)),
			List.of(Component.translatable("set.elsewhere.sunwright.title"), Component.translatable("set.elsewhere.sunwright.noonfire"), Component.translatable("set.elsewhere.sunwright.noonfire.1"),
				Component.translatable("set.elsewhere.sunwright.noonfire.2"))) {});
	}
}
