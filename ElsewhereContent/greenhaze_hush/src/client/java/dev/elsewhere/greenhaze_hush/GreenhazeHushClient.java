package dev.elsewhere.greenhaze_hush;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.auris.Auris;
import dev.elsewhere.boss.grumblegut.Grumblegut;
import dev.elsewhere.boss.lithara.Lithara;
import dev.elsewhere.boss.percolax.Percolax;
import dev.elsewhere.greenhaze.Greenhaze;
import dev.elsewhere.greenhaze.GreenhazeEntities;
import dev.elsewhere.greenhaze.GreenhazeMobs;
import dev.elsewhere.greenhaze.Haze;
import dev.elsewhere.hush.Hush;
import dev.elsewhere.hush.HushEntities;
import dev.elsewhere.hush.HushMobs;
import dev.elsewhere.hush.Resonance;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.panda.PandaModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.monster.hoglin.HoglinModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.shulker.ShulkerModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.client.renderer.entity.state.ShulkerRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * Client entry point of the greenhaze_hush module. At merge the core's client entrypoint calls {@link #initClient()}.
 * Beta: every mob and boss renders with a vanilla model rig (scaled) and its own recoloured texture; HUD bars for Haze and Resonance.
 */
public final class GreenhazeHushClient {
	private GreenhazeHushClient() {}

	static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + name + ".png");
	}

	/** A mob drawn with a vanilla model rig, its own texture and a scale. */
	public static class Simple<T extends Mob, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends MobRenderer<T, S, M> {
		private final Identifier texture;
		private final Supplier<S> states;
		private final float scale;

		public Simple(EntityRendererProvider.Context ctx, M model, float shadow, String texture, Supplier<S> states, float scale) {
			super(ctx, model, shadow * scale);
			this.texture = tex(texture);
			this.states = states;
			this.scale = scale;
		}

		@Override
		public Identifier getTextureLocation(S state) {
			return texture;
		}

		@Override
		public S createRenderState() {
			return states.get();
		}

		@Override
		protected void scale(S state, PoseStack pose) {
			pose.scale(scale, scale, scale);
		}
	}

	public static void initClient() {
		EntityRendererRegistry.register(GreenhazeEntities.LAZY_GORGER, c -> new Simple<GreenhazeMobs.LazyGorger, PandaRenderState, PandaModel>(
			c, new PandaModel(c.bakeLayer(ModelLayers.PANDA)), 0.9F, "lazy_gorger", PandaRenderState::new, 1.0F));
		EntityRendererRegistry.register(GreenhazeEntities.MUNCHIE_BOAR, c -> new Simple<GreenhazeMobs.MunchieBoar, HoglinRenderState, HoglinModel>(
			c, new HoglinModel(c.bakeLayer(ModelLayers.HOGLIN)), 0.7F, "munchie_boar", HoglinRenderState::new, 0.85F));
		EntityRendererRegistry.register(GreenhazeEntities.TOKE_TOAD, c -> new Simple<GreenhazeMobs.TokeToad, FrogRenderState, FrogModel>(
			c, new FrogModel(c.bakeLayer(ModelLayers.FROG)), 0.3F, "toke_toad", FrogRenderState::new, 2.4F));
		EntityRendererRegistry.register(GreenhazeEntities.HOTBOX_SMOG, c -> new SmogRenderer(c));
		EntityRendererRegistry.register(GreenhazeEntities.PARANOIA_SHADE, c -> new Simple<GreenhazeMobs.ParanoiaShade, VexRenderState, VexModel>(
			c, new VexModel(c.bakeLayer(ModelLayers.VEX)), 0.0F, "paranoia_shade", VexRenderState::new, 1.0F) {
			@Override
			public boolean shouldRender(GreenhazeMobs.ParanoiaShade e, Frustum f, double x, double y, double z, float pt) {
				var p = Minecraft.getInstance().player;
				return p != null && p.getAttachedOrElse(Haze.HAZE, 0F) >= 60F && super.shouldRender(e, f, x, y, z, pt);
			}
		});
		EntityRendererRegistry.register(GreenhazeEntities.BUD_BOMB, c -> new Simple<GreenhazeMobs.BudBomb, CreeperRenderState, CreeperModel>(
			c, new CreeperModel(c.bakeLayer(ModelLayers.CREEPER)), 0.5F, "bud_bomb", CreeperRenderState::new, 1.0F) {
			@Override
			public void extractRenderState(GreenhazeMobs.BudBomb e, CreeperRenderState s, float pt) {
				super.extractRenderState(e, s, pt);
				s.swelling = Math.min(1F, e.swell() / (float) GreenhazeMobs.BudBomb.FUSE);
			}
		});
		EntityRendererRegistry.register(GreenhazeEntities.BUDTENDER, c -> new Simple<GreenhazeMobs.Budtender, VillagerRenderState, VillagerModel>(
			c, new VillagerModel(c.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F, "budtender", VillagerRenderState::new, 1.0F));
		EntityRendererRegistry.register(GreenhazeEntities.PERCOLAX, c -> new Simple<Percolax, IronGolemRenderState, IronGolemModel>(
			c, new IronGolemModel(c.bakeLayer(ModelLayers.IRON_GOLEM)), 1.0F, "percolax", IronGolemRenderState::new, 2.75F));
		EntityRendererRegistry.register(GreenhazeEntities.GRUMBLEGUT, c -> new Simple<Grumblegut, HoglinRenderState, HoglinModel>(
			c, new HoglinModel(c.bakeLayer(ModelLayers.HOGLIN)), 1.0F, "grumblegut", HoglinRenderState::new, 2.1F));
		EntityRendererRegistry.register(GreenhazeEntities.SMOKE_RING, ThrownItemRenderer::new);
		EntityRendererRegistry.register(GreenhazeEntities.EMBER_FLICK, c -> new ThrownItemRenderer<>(c, 1.0F, true));
		EntityRendererRegistry.register(GreenhazeEntities.SPIT_SEED, ThrownItemRenderer::new);
		EntityRendererRegistry.register(GreenhazeEntities.CLOUD_JAR, ThrownItemRenderer::new);

		EntityRendererRegistry.register(HushEntities.ECHO_STALKER, c -> new Simple<HushMobs.EchoStalker, EndermanRenderState, EndermanModel<EndermanRenderState>>(
			c, new EndermanModel<>(c.bakeLayer(ModelLayers.ENDERMAN)), 0.5F, "echo_stalker", EndermanRenderState::new, 1.25F));
		EntityRendererRegistry.register(HushEntities.SHRIEKER_SWARM, c -> new Simple<HushMobs.ShriekerSwarm, BatRenderState, BatModel>(
			c, new BatModel(c.bakeLayer(ModelLayers.BAT)), 0.2F, "shrieker_swarm", BatRenderState::new, 1.5F));
		EntityRendererRegistry.register(HushEntities.OSSUARY_CHORISTER, c -> new Simple<HushMobs.OssuaryChorister, SkeletonRenderState, SkeletonModel<SkeletonRenderState>>(
			c, new SkeletonModel<>(c.bakeLayer(ModelLayers.SKELETON)), 0.5F, "ossuary_chorister", SkeletonRenderState::new, 1.2F));
		EntityRendererRegistry.register(HushEntities.CATALYST_MIMIC, c -> new Simple<HushMobs.CatalystMimic, ShulkerRenderState, ShulkerModel>(
			c, new ShulkerModel(c.bakeLayer(ModelLayers.SHULKER)), 0.0F, "catalyst_mimic", ShulkerRenderState::new, 1.0F) {
			@Override
			public void extractRenderState(HushMobs.CatalystMimic e, ShulkerRenderState s, float pt) {
				super.extractRenderState(e, s, pt);
				s.peekAmount = e.openness() / 20F;
				s.yBodyRot = e.getYRot();
				s.yHeadRot = e.getYRot();
			}
		});
		EntityRendererRegistry.register(HushEntities.MUTE_ARCHIVIST, c -> new Simple<HushMobs.MuteArchivist, VillagerRenderState, VillagerModel>(
			c, new VillagerModel(c.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F, "mute_archivist", VillagerRenderState::new, 1.0F));
		EntityRendererRegistry.register(HushEntities.AURIS, c -> new Simple<Auris, WardenRenderState, WardenModel>(
			c, new WardenModel(c.bakeLayer(ModelLayers.WARDEN)), 1.0F, "auris", WardenRenderState::new, 1.7F) {
			@Override
			public void extractRenderState(Auris e, WardenRenderState s, float pt) {
				super.extractRenderState(e, s, pt);
				s.tendrilAnimation = e.anim() == Auris.ANIM_LISTEN || e.anim() == Auris.ANIM_CHARGE ? 1F : 0.3F;
				s.heartAnimation = 0.5F + 0.5F * (float) Math.sin(e.tickCount * 0.2);
			}
		});
		EntityRendererRegistry.register(HushEntities.LITHARA, c -> new Simple<Lithara, EvokerRenderState, IllagerModel<EvokerRenderState>>(
			c, new IllagerModel<>(c.bakeLayer(ModelLayers.EVOKER)), 0.8F, "lithara", EvokerRenderState::new, 3.0F));
		EntityRendererRegistry.register(HushEntities.CRYSTAL_SHARD, c -> new ThrownItemRenderer<>(c, 1.0F, true));
		EntityRendererRegistry.register(HushEntities.TUNING_FORK, ThrownItemRenderer::new);

		dev.elsewhere.stub.greenhaze_hush.HudStub.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("elsewhere", "greenhaze_hush_meters"), MeterHud::render);
	}

	/** Smog clouds scale with their size like slimes. */
	static class SmogRenderer extends MobRenderer<GreenhazeMobs.HotboxSmog, SmogState, SlimeModel> {
		SmogRenderer(EntityRendererProvider.Context c) {
			super(c, new SlimeModel(c.bakeLayer(ModelLayers.SLIME_OUTER)), 0.25F);
		}

		@Override
		public Identifier getTextureLocation(SmogState s) {
			return tex("hotbox_smog");
		}

		@Override
		public SmogState createRenderState() {
			return new SmogState();
		}

		@Override
		public void extractRenderState(GreenhazeMobs.HotboxSmog e, SmogState s, float pt) {
			super.extractRenderState(e, s, pt);
			s.size = e.getSize();
		}

		@Override
		protected void scale(SmogState s, PoseStack pose) {
			float f = 0.999F * s.size;
			pose.scale(f, f, f);
		}
	}

	static class SmogState extends LivingEntityRenderState {
		int size = 1;
	}

	static boolean inGreenhaze() {
		var l = Minecraft.getInstance().level;
		return l != null && l.dimension().equals(Greenhaze.LEVEL);
	}

	static boolean inHush() {
		var l = Minecraft.getInstance().level;
		return l != null && l.dimension().equals(Hush.LEVEL);
	}

	static float resonance() {
		var p = Minecraft.getInstance().player;
		return p == null ? 0 : p.getAttachedOrElse(Resonance.RESONANCE, 0F);
	}
}
