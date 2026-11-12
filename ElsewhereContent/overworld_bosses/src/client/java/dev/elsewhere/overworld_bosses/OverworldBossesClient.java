package dev.elsewhere.overworld_bosses;

import dev.elsewhere.overworld_bosses.render.BlockEntityLikeRenderer;
import dev.elsewhere.overworld_bosses.render.BossRenderers;
import dev.elsewhere.overworld_bosses.render.TentacleRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.level.block.Blocks;

/**
 * Client entry point of the overworld_bosses module: entity renderers. BETA: the bosses use scaled, tinted vanilla
 * models (frog, creaking, husk, iron golem, squid) until the custom Blockbench-style models land; see NOTES.md.
 */
public final class OverworldBossesClient {
	private OverworldBossesClient() {}

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(TentacleRenderer.LAYER, TentacleRenderer::createLayer);
		EntityRendererRegistry.register(OwbEntities.GULPMOTHER, BossRenderers.Gulpmother::new);
		EntityRendererRegistry.register(OwbEntities.STRAWGRIM, BossRenderers.Strawgrim::new);
		EntityRendererRegistry.register(OwbEntities.NEFERKHET, BossRenderers.Neferkhet::new);
		EntityRendererRegistry.register(OwbEntities.RIMEBACK, BossRenderers.Rimeback::new);
		EntityRendererRegistry.register(OwbEntities.VORLAGH, BossRenderers.Vorlagh::new);
		EntityRendererRegistry.register(OwbEntities.VORLAGH_TENTACLE, TentacleRenderer::new);
		EntityRendererRegistry.register(OwbEntities.STRAWGRIM_HEAD, ctx -> new BlockEntityLikeRenderer<>(ctx, e -> Blocks.JACK_O_LANTERN.defaultBlockState(), 1.0F));
		EntityRendererRegistry.register(OwbEntities.GLACIER_BOULDER, ctx -> new BlockEntityLikeRenderer<>(ctx, e -> Blocks.PACKED_ICE.defaultBlockState(), 2.0F));
		EntityRendererRegistry.register(OwbEntities.SUN_LANCE, NoopRenderer::new);
		EntityRendererRegistry.register(OwbEntities.MIRE_BITER, SlimeRenderer::new);
		EntityRendererRegistry.register(OwbEntities.MURDER_CROW, PhantomRenderer::new);
		EntityRendererRegistry.register(OwbEntities.WRAPPED_SENTINEL, HuskRenderer::new);
		EntityRendererRegistry.register(OwbEntities.FROSTBOUND_REVENANT, StrayRenderer::new);
		EntityRendererRegistry.register(OwbEntities.TIDEWRACK_DROWNED, DrownedRenderer::new);
		EntityRendererRegistry.register(OwbEntities.FROGSPAWN_GLOB, ThrownItemRenderer::new);
		EntityRendererRegistry.register(OwbEntities.SEED_FIREBALL, ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
		EntityRendererRegistry.register(OwbEntities.FROST_SHARD, ThrownItemRenderer::new);
		EntityRendererRegistry.register(OwbEntities.INK_GLOB, ThrownItemRenderer::new);
	}
}
