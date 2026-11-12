package dev.elsewhere.rivals.azmar;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/** Boss armor helpers: real vanilla (or Azmar headpiece) pieces with real vanilla trims on plain-clothing skins. */
public final class AzmarGear {
	private AzmarGear() {}

	/** {@code stack} with the trim {@code pattern} (vanilla "dune", "wayfinder", or "azmar") in {@code material} ("gold", "lapis", "copper"), and optional leather dye. */
	public static ItemStack dress(ServerLevel level, ItemStack stack, String pattern, String material, int dye) {
		if (dye >= 0) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dye));
		var reg = level.registryAccess();
		Identifier mid = Identifier.withDefaultNamespace(material);
		Identifier pid = pattern.equals("azmar") ? AzmarContent.id("azmar") : Identifier.withDefaultNamespace(pattern);
		Optional<? extends net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimMaterial>> mat =
			reg.lookupOrThrow(Registries.TRIM_MATERIAL).get(ResourceKey.create(Registries.TRIM_MATERIAL, mid));
		Optional<? extends net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimPattern>> pat =
			reg.lookupOrThrow(Registries.TRIM_PATTERN).get(ResourceKey.create(Registries.TRIM_PATTERN, pid));
		if (mat.isPresent() && pat.isPresent()) stack.set(DataComponents.TRIM, new ArmorTrim(mat.get(), pat.get()));
		return stack;
	}

	public static void wear(Mob m, EquipmentSlot slot, ItemStack s) {
		m.setItemSlot(slot, s);
		m.setDropChance(slot, 0.0F);
	}
}
