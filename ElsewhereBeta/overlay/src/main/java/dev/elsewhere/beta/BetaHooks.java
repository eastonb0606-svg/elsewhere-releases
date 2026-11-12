package dev.elsewhere.beta;

import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.core.wayfinder.AttunedWayfinderItem;
import dev.elsewhere.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Integration glue that runs after every module has initialised. */
public final class BetaHooks {
	private BetaHooks() {}

	private static void dim(final String id, final String name, final int color, final int maxLandingY, final Block platform, final boolean returnRifts) {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("elsewhere", id));
		if (ElsewhereDimensions.get(key) == null) {
			ElsewhereDimensions.register(new ElsewhereDimensions.Info(key, name, color, maxLandingY, platform, returnRifts));
		}
		// attuned Wayfinder for this dimension (the attunement recipe crafts elsewhere:wayfinder_<id>)
		Identifier wid = Identifier.fromNamespaceAndPath("elsewhere", "wayfinder_" + id);
		if (!BuiltInRegistries.ITEM.containsKey(wid)) {
			ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, wid);
			int c = ElsewhereDimensions.get(key).color();
			Registry.register(BuiltInRegistries.ITEM, ik, new AttunedWayfinderItem(new Item.Properties().setId(ik).stacksTo(1).rarity(Rarity.UNCOMMON)
				.component(DataComponents.USE_COOLDOWN, ModItems.WAYFINDER_COOLDOWN), key, c));
		}
	}

	public static void afterModules() {
		GlobalTrips.init();
		OverworldContent.init();
		// Module dimensions the module itself did not register with the core (rifts, landing, return points need this).
		dim("petalwind", "Petalwind Isles", 0xF4A6C8, 400, Blocks.CHERRY_PLANKS, true);
		dim("glasswaste", "The Glasswaste", 0xF2C14E, 400, Blocks.SANDSTONE, true);
		dim("verdigris_sea", "Verdigris Sea", 0x3FB8A0, 400, Blocks.PRISMARINE_BRICKS, true);
		dim("ironpeak", "Ironpeak", 0x9FB3C8, 2000, Blocks.COBBLESTONE, true);
		dim("greenhaze", "The Greenhaze", 0x7CCB5A, 400, Blocks.MOSS_BLOCK, true);
		dim("the_hush", "The Hush", 0x2E3A6B, 400, Blocks.DEEPSLATE_TILES, false);

		if (Boolean.getBoolean("elsewhere.betatest") || Boolean.getBoolean("elsewhere.prodcheck")) {
			BetaSmokeTest.register();
		}
		int haltAfter = Integer.getInteger("elsewhere.haltAfterSeconds", 0);
		if (haltAfter > 0) {
			int[] ticks = {0};
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				if (++ticks[0] == haltAfter * 20) {
					BetaSmokeTest.LOG.info("[betatest] halting after {} s", haltAfter);
					server.halt(false);
				}
			});
		}
	}
}
