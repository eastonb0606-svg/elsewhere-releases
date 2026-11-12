package dev.elsewhere.stub.petalwind_glasswaste;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.Reg;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.sounds.SoundEvents;

/**
 * STUB: the tier signature items (BOSSES.md "Signature gear") belong to the core's boss framework. This module only
 * needs Tier I (Kazehana), III (Tempestra) and IV (Heliodor, Ankhara), so it registers look-alike placeholders under the
 * core's ids for dev testing. Drop this class at merge; the core registers the real items with the same ids.
 */
public final class SignatureGearStub {
	private SignatureGearStub() {}

	public static Item CHALLENGERS_CLEAVER, PHASEWALKER_GREAVES, STORMCREST_HELM;

	public static void init() {
		String tab = Reg.currentTab;
		Reg.currentTab = "petalwind";
		CHALLENGERS_CLEAVER = Reg.sharedItem("challengers_cleaver", () -> new Item.Properties().rarity(Rarity.RARE)
			.sword(net.minecraft.world.item.ToolMaterial.DIAMOND, 3.0F, -2.4F)
			.attributes(GearStats.weapon(GearStats.CHALLENGERS_CLEAVER_DAMAGE, GearStats.CHALLENGERS_CLEAVER_SPEED)));
		PHASEWALKER_GREAVES = Reg.sharedItem("phasewalker_greaves", () -> new Item.Properties().rarity(Rarity.EPIC).humanoidArmor(
			GearStats.armor(37, new int[] {0, 6, 0, 0}, 4.0F, 0.1F, SoundEvents.ARMOR_EQUIP_NETHERITE, "repairs_galeweave_armor", "phasewalker"),
			ArmorType.LEGGINGS));
		STORMCREST_HELM = Reg.sharedItem("stormcrest_helm", () -> new Item.Properties().rarity(Rarity.EPIC).humanoidArmor(
			GearStats.armor(37, new int[] {0, 0, 0, 3}, 3.0F, 0.2F, SoundEvents.ARMOR_EQUIP_NETHERITE, "repairs_sunwright_armor", "stormcrest"),
			ArmorType.HELMET));
		Reg.currentTab = tab;
	}

	public static List<ItemStack> roll(int tier, ServerPlayer player, boolean firstKill) {
		Item item = switch (tier) {
			case 1 -> CHALLENGERS_CLEAVER;
			case 3 -> PHASEWALKER_GREAVES;
			case 4 -> STORMCREST_HELM;
			default -> null;
		};
		if (item == null) return List.of();
		if (firstKill || player.getRandom().nextFloat() < GearStats.SIGNATURE_DROP_CHANCE_REPEAT) return List.of(new ItemStack(item));
		return List.of();
	}
}
