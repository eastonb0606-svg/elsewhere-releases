package dev.elsewhere.palehollow.entity;

import dev.elsewhere.palehollow.ArsenalLinks;
import dev.elsewhere.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * The Sap Tapper (Resin Kilns, Pale Hollow): a resident trader that never leaves. Offers are rebuilt for each customer and
 * each player's uses are remembered; limited trades restock twice a day per player, the Rootcall Staff never (one per player).
 * V2-PLAN A7: 12 resin clumps for an emerald; an emerald for 3 Pale Sap; 10 Pale Bark + an Eye of Ender for an Unblinking Eye;
 * 30 Pale Bark + 2 Moonsilk Thread for the Rootcall Staff (the last two only when the arsenal items exist).
 */
public class SapTapper extends WanderingTrader {
	public static final long RESTOCK_TICKS = 12000L;
	public static final Identifier STAFF_ID = ArsenalLinks.ROOTCALL_STAFF;
	private final Map<UUID, Map<String, Integer>> uses = new HashMap<>();
	private final Map<UUID, Long> restocked = new HashMap<>();

	public SapTapper(EntityType<? extends WanderingTrader> type, Level level) {
		super(type, level);
		setDespawnDelay(0);
		setPersistenceRequired();
	}

	@Override
	protected void updateTrades(ServerLevel level) {}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	public static List<MerchantOffer> offers() {
		List<MerchantOffer> o = new ArrayList<>();
		o.add(new MerchantOffer(new ItemCost(Items.RESIN_CLUMP, 12), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
		o.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.PALE_SAP, 3), 12, 1, 0.05F));
		o.add(new MerchantOffer(new ItemCost(ModItems.PALE_BARK, 10), Optional.of(new ItemCost(Items.ENDER_EYE, 1)), new ItemStack(ModItems.UNBLINKING_EYE), 3, 10, 0.05F));
		Optional<Item> staff = ArsenalLinks.item(STAFF_ID);
		Optional<Item> thread = ArsenalLinks.item(ArsenalLinks.MOONSILK_THREAD);
		if (staff.isPresent() && thread.isPresent()) {
			o.add(new MerchantOffer(new ItemCost(ModItems.PALE_BARK, 30), Optional.of(new ItemCost(thread.get(), 2)), new ItemStack(staff.get()), 1, 30, 0.05F));
		}
		return o;
	}

	static String key(MerchantOffer m) {
		return BuiltInRegistries.ITEM.getKey(m.getResult().getItem()) + "/" + BuiltInRegistries.ITEM.getKey(m.getBaseCostA().getItem());
	}

	private static boolean once(MerchantOffer m) {
		return BuiltInRegistries.ITEM.getKey(m.getResult().getItem()).equals(STAFF_ID);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && !isTrading() && isAlive()) {
			long now = level().getGameTime();
			Long last = restocked.get(sp.getUUID());
			if (last == null || now - last >= RESTOCK_TICKS) {
				Map<String, Integer> kept = new HashMap<>();
				for (Map.Entry<String, Integer> e : uses.getOrDefault(sp.getUUID(), Map.of()).entrySet()) {
					if (e.getKey().startsWith(STAFF_ID.toString() + "/")) kept.put(e.getKey(), e.getValue());
				}
				uses.put(sp.getUUID(), kept);
				restocked.put(sp.getUUID(), now);
			}
			Map<String, Integer> used = uses.getOrDefault(sp.getUUID(), Map.of());
			List<MerchantOffer> list = offers();
			for (MerchantOffer m : list) {
				int n = Math.min(used.getOrDefault(key(m), 0), m.getMaxUses());
				for (int i = 0; i < n; i++) m.increaseUses();
			}
			MerchantOffers mo = getOffers();
			mo.clear();
			mo.addAll(list);
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		super.notifyTrade(offer);
		if (getTradingPlayer() instanceof ServerPlayer sp) uses.computeIfAbsent(sp.getUUID(), k -> new HashMap<>()).put(key(offer), offer.getUses());
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDespawnDelay(0);
	}
}
