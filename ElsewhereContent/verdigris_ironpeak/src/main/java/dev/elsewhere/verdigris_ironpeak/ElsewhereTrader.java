package dev.elsewhere.verdigris_ironpeak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * A resident trader (Tidewright, Clan Quartermaster): a wandering trader that never leaves and whose offers are
 * rebuilt for each customer, so trades can be gated on that player (clan rank, Test Meter level).
 * 2.0: persistent (never despawns), and each player's trade uses are remembered and restocked twice a day like a
 * villager's, so reopening the screen no longer refreshes a limited trade.
 */
public class ElsewhereTrader extends WanderingTrader {
	/** Builds the offers for one customer; {@code self} is the trader (null in tests that only count offers). */
	public interface OfferFactory {
		List<MerchantOffer> build(ElsewhereTrader self, ServerPlayer customer);
	}

	public static final long RESTOCK_TICKS = 12000L;
	private final OfferFactory offers;
	/** Explorer maps made by this trader, by structure id (so the screen doesn't mint a new map every time it opens). */
	public final Map<String, ItemStack> mapCache = new HashMap<>();
	private final Map<UUID, Map<String, Integer>> uses = new HashMap<>();
	private final Map<UUID, Long> restocked = new HashMap<>();

	public ElsewhereTrader(EntityType<? extends WanderingTrader> type, Level level, OfferFactory offers) {
		super(type, level);
		this.offers = offers;
		setDespawnDelay(0);
		setPersistenceRequired();
	}

	@Override
	protected void updateTrades(ServerLevel level) {}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	public List<MerchantOffer> offersFor(ServerPlayer p) {
		return offers.build(this, p);
	}

	static String key(MerchantOffer m) {
		return BuiltInRegistries.ITEM.getKey(m.getResult().getItem()) + "/" + BuiltInRegistries.ITEM.getKey(m.getBaseCostA().getItem());
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && !isTrading() && isAlive()) {
			List<MerchantOffer> list = offers.build(this, sp);
			long now = level().getGameTime();
			Long last = restocked.get(sp.getUUID());
			if (last == null || now - last >= RESTOCK_TICKS) {
				uses.remove(sp.getUUID());
				restocked.put(sp.getUUID(), now);
			}
			Map<String, Integer> used = uses.getOrDefault(sp.getUUID(), Map.of());
			for (MerchantOffer m : list) {
				int n = Math.min(used.getOrDefault(key(m), 0), m.getMaxUses());
				for (int i = 0; i < n; i++) m.increaseUses();
			}
			MerchantOffers o = getOffers();
			o.clear();
			o.addAll(list);
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
