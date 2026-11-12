package dev.elsewhere.lore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * The Aldermark Reeve (market stall in the towns) and the Clan Quartermaster (Ironpeak): vanilla villagers with fixed
 * trades, no AI, invulnerable. The Reeve sells chain and iron gear at a premium, surcoats and copies of the
 * Proclamation; both buy Lord Seals. Placed by K-B through {@link LoreApi#spawnQuartermaster}.
 */
public final class Quartermasters {
	private Quartermasters() {}

	public static final String TAG = "lore_quartermaster", CLAN_TAG = "lore_clan_quartermaster";
	public static final int SURCOAT_BLUE = 0x2A4B9B;
	private static final Map<UUID, Long> LAST_RESTOCK = new HashMap<>();

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && entity instanceof Villager v && isQuartermaster(v)) {
				restock(sl, v);
				LoreAdvancements.award(sp, "a_fair_price");
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean isQuartermaster(Villager v) {
		return v.entityTags().contains(TAG);
	}

	public static @Nullable Villager spawn(ServerLevel level, BlockPos pos, boolean clan) {
		Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (v == null) return null;
		v.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
		v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), clan ? VillagerProfession.ARMORER : VillagerProfession.MASON).withLevel(5));
		v.setVillagerDataFinalized(true);
		v.setOffers(offers(clan));
		v.setCustomName(Component.translatable(clan ? "entity.elsewhere.lore_clan_quartermaster" : "entity.elsewhere.lore_reeve"));
		v.setCustomNameVisible(true);
		v.setNoAi(true);
		v.setPermanentlyInvulnerable(true);
		v.setPersistenceRequired();
		v.addTag(TAG);
		if (clan) v.addTag(CLAN_TAG);
		return level.addFreshEntity(v) ? v : null;
	}

	/** Once per in-game day, the stock comes back. */
	static void restock(ServerLevel level, Villager v) {
		long day = level.getGameTime() / 24000L;
		Long last = LAST_RESTOCK.put(v.getUUID(), day);
		if (last != null && last == day) return;
		for (MerchantOffer o : v.getOffers()) o.resetUses();
	}

	public static MerchantOffers offers(boolean clan) {
		MerchantOffers o = new MerchantOffers();
		if (!clan) {
			// Reeve: premium gear for emeralds (a trading hall is still cheaper), the King's word, surcoats.
			o.add(sell(Items.EMERALD, 3, new ItemStack(LoreItems.ROYAL_PROCLAMATION), 99, 1));
			o.add(sell(Items.EMERALD, 8, surcoat(), 8, 5));
			o.add(sell(Items.EMERALD, 12, new ItemStack(Items.CHAINMAIL_HELMET), 4, 5));
			o.add(sell(Items.EMERALD, 22, new ItemStack(Items.CHAINMAIL_CHESTPLATE), 4, 8));
			o.add(sell(Items.EMERALD, 17, new ItemStack(Items.CHAINMAIL_LEGGINGS), 4, 7));
			o.add(sell(Items.EMERALD, 10, new ItemStack(Items.CHAINMAIL_BOOTS), 4, 5));
			o.add(sell(Items.EMERALD, 9, new ItemStack(Items.IRON_SWORD), 6, 5));
			o.add(sell(Items.EMERALD, 8, new ItemStack(Items.SHIELD), 6, 5));
			o.add(sell(Items.EMERALD, 4, new ItemStack(Items.ARROW, 16), 16, 2));
			o.add(sell(Items.GOLD_INGOT, 4, new ItemStack(Items.EMERALD, 3), 16, 2));
		} else {
			o.add(sell(Items.GOLD_BLOCK, 1, new ItemStack(Items.EMERALD, 8), 8, 3));
			o.add(sell(Items.EMERALD, 6, surcoat(), 8, 5));
			o.add(sell(Items.EMERALD, 4, new ItemStack(Items.ARROW, 16), 16, 2));
		}
		// Both buy Lord Seals (a seal is a trophy: 12 emeralds), and sell cosmetics for them.
		for (Item seal : LoreItems.SEALS.values()) o.add(sell(seal, 1, new ItemStack(Items.EMERALD, 12), 4, 10));
		o.add(sellTwo(LoreItems.SEALS.get("marshal"), 1, Items.EMERALD, 6, new ItemStack(Items.GLOBE_BANNER_PATTERN), 2, 15));
		o.add(sellTwo(LoreItems.SEALS.get("chancellor"), 1, Items.EMERALD, 6, new ItemStack(Items.FLOWER_BANNER_PATTERN), 2, 15));
		return o;
	}

	static ItemStack surcoat() {
		ItemStack s = new ItemStack(Items.LEATHER_CHESTPLATE);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(SURCOAT_BLUE));
		s.set(DataComponents.CUSTOM_NAME, Component.translatable("item.elsewhere.lore_aldermark_surcoat"));
		return s;
	}

	private static MerchantOffer sell(net.minecraft.world.level.ItemLike cost, int n, ItemStack result, int max, int xp) {
		return new MerchantOffer(new ItemCost(cost, n), result, max, xp, 0.05F);
	}

	private static MerchantOffer sellTwo(net.minecraft.world.level.ItemLike a, int na, net.minecraft.world.level.ItemLike b, int nb, ItemStack result, int max, int xp) {
		return new MerchantOffer(new ItemCost(a, na), Optional.of(new ItemCost(b, nb)), result, max, xp, 0.05F);
	}
}
