package dev.elsewhere.nether_end_bosses;

import dev.elsewhere.boss.aphelion.AphelionContent;
import dev.elsewhere.boss.grunthar.GruntharContent;
import dev.elsewhere.boss.mournmaw.MournmawContent;
import dev.elsewhere.boss.ossyra.OssyraContent;
import dev.elsewhere.boss.solaxis.SolaxisContent;
import dev.elsewhere.stub.nether_end_bosses.TripTrigger;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint of the nether_end_bosses module: Grunthar & Ashtusk, Solaxis, Mournmaw (Nether) and
 * Ossyra, Aphelion (End, post-dragon). At merge the core's {@code ModInitializer} calls {@link #init()}.
 */
public final class NetherEndBossesContent {
	public static final Logger LOGGER = LoggerFactory.getLogger("Elsewhere nether_end_bosses");
	/** This module adds no dimensions, so it has no accidental-trip triggers. */
	public static final List<TripTrigger> TRIPS = List.of();
	/** On since 2.0 alpha.2: the arenas generate as structures (structure sets in data/elsewhere/worldgen/structure_set). */
	public static final boolean WORLDGEN = true;

	private static boolean done;

	private NetherEndBossesContent() {}

	public static void init() {
		dev.elsewhere.boss.kit.BossKit.init();
		if (done) return;
		done = true;
		NetherEndSounds.init();
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Reg.id("nether_end_boss_arena"), BossArenaFeature.CODEC);
		GruntharContent.init();
		SolaxisContent.init();
		MournmawContent.init();
		OssyraContent.init();
		AphelionContent.init();

		// Arenas are structures now (BossArenaStructure + structure_set JSON). The feature type above stays registered for 0.3.0 worlds but nothing places it.
		BossArenaStructure.init();
		dev.elsewhere.nether_end_bosses.fit3.NetherFit.init(); // 3.0 terrain-fit arenas (s-struct-nether; kill switch elsewhere.overhaul3.nether_fit)

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Reg.id("nether_end_bosses"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.nether_end_bosses"))
			.icon(() -> new ItemStack(SolaxisContent.NINEFOLD_ROD))
			.displayItems((params, out) -> Reg.ITEMS.forEach(out::accept))
			.build());

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> DevCommands.register(dispatcher));
		if (Boolean.getBoolean("nether_end_bosses.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::start);
			ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
		}
	}

	@SafeVarargs
	private static void addArena(String placed, GenerationStep.Decoration step, ResourceKey<Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), step, ResourceKey.create(Registries.PLACED_FEATURE, Reg.id(placed)));
	}
}
