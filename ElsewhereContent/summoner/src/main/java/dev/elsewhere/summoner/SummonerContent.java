package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionManager;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Summoner module entry (ARSENAL.md §5): minion framework, three staffs and minions, Herdcaller gear, trinket, biscuit. */
public final class SummonerContent {
	private SummonerContent() {}

	public static final String NS = "elsewhere";
	// Gorger Fluff drops (Lazy Gorger 50%) and the Herdcaller recipes ship in greenhaze_hush (id lookups).

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		SummonerEffects.init();
		SummonerEntities.init();
		SummonerItems.init();
		MinionManager.init();
		SummonerPerks.initHooks();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> SummonerConfig.load());
		HerdingCrookItem.initEnchantRules();
		SummonerLoot.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				SummonerPerks.updateTrinketSlots(p);
				SummonerPerks.updateHerdFocus(p);
			}
		});
		if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id("summoner"))) {
			CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.elsewhere.summoner"))
				.icon(() -> new ItemStack(SummonerItems.ROOTCALL_STAFF))
				.displayItems((params, out) -> {
					for (Item it : SummonerItems.ALL) out.accept(it);
				})
				.build();
			Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("summoner"), tab);
		}
	}
}
