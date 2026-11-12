package dev.elsewhere.greenhaze_hush;

import dev.elsewhere.greenhaze.Greenhaze;
import dev.elsewhere.greenhaze.GreenhazeTrips;
import dev.elsewhere.hush.Hush;
import dev.elsewhere.hush.HushTrips;
import dev.elsewhere.hush.Resonance;
import dev.elsewhere.greenhaze_hush.world.BuiltStructure;
import dev.elsewhere.stub.greenhaze_hush.ArenaSeal;
import dev.elsewhere.stub.greenhaze_hush.SignatureGear;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import dev.elsewhere.stub.greenhaze_hush.Telegraph;
import dev.elsewhere.stub.greenhaze_hush.TripSystem;
import dev.elsewhere.stub.greenhaze_hush.TripTrigger;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point of the greenhaze_hush content module. At merge the core's main entrypoint calls {@link #init()}. */
public final class GreenhazeHushContent {
	private GreenhazeHushContent() {}

	public static final Logger LOGGER = LoggerFactory.getLogger("Elsewhere/greenhaze_hush");
	public static final List<TripTrigger> TRIPS = List.of(GreenhazeTrips.GREEN_LEAF_USE, HushTrips.WARDEN, HushTrips.SHRIEK);
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		Sfx3.init();
		StubBlocks.init();
		dev.elsewhere.boss.kit.BossKit.init();
		Stability.init();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(s -> HushTrips.migrateStaleConfig());
		GHFeature.init();
		FxPrefs.init();
		BuiltStructure.init();
		Greenhaze.init();
		dev.elsewhere.greenhaze_hush.s3.groups.SpawnGroups3.init(); // ARMIES 6 spawn groups (s-spawngroups, key spawn_groups_greenhaze_hush)
		SignatureGear.init();
		Hush.init();
		Telegraph.init();
		TripSystem.init();
		TripSystem.inBossFight = ArenaSeal::inAnyFight;
		TripSystem.arrival = DimTravel::accidentalArrival;
		Resonance.engineAnswer = dev.elsewhere.boss.auris.Auris::answer;
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && hand == net.minecraft.world.InteractionHand.MAIN_HAND && dev.elsewhere.greenhaze.GreenLeafBridge.loaded()) {
				var state = level.getBlockState(hit.getBlockPos());
				if (dev.elsewhere.greenhaze.GreenLeafBridge.isGreenLeafBlock(state)
					&& net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("bong")) onGreenLeafUse(sp);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp) Resonance.onHit(sp);
			return InteractionResult.PASS;
		});
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Reg.id("greenhaze"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.greenhaze"))
			.icon(() -> new ItemStack(dev.elsewhere.greenhaze.GreenhazeItems.HAZEBORN_BUD))
			.displayItems((params, out) -> Reg.GREENHAZE_TAB.forEach(out::accept)).build());
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Reg.id("the_hush"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.the_hush"))
			.icon(() -> new ItemStack(dev.elsewhere.hush.HushItems.RESONANT_SHARD))
			.displayItems((params, out) -> Reg.HUSH_TAB.forEach(out::accept)).build());
		LOGGER.info("Greenhaze & Hush content registered: {} blocks, {} items", Reg.BLOCKS.size(), Reg.ITEMS.size());
	}

	/** Called from the item-use mixin when any Green Leaf item use completes. */
	public static void onGreenLeafUse(ServerPlayer p) {
		GreenhazeTrips.onGreenLeafUse(p);
		Resonance.onGreenLeafUse(p);
	}
}
