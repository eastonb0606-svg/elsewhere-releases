package dev.elsewhere.bossart.vorlagh;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.vorlagh.VorlaghTentacle;
import dev.elsewhere.bossart.overworld_b.ActionTracker;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/** Custom renderer for Vorlagh's tentacle part entities (replaces the module's TentacleRenderer). */
public class VorlaghTentacleRenderer extends MobRenderer<VorlaghTentacle, VorlaghTentacleRenderState, VorlaghTentacleModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BossRegistry.id("boss/vorlagh_tentacle"), "main");
	private static final Identifier TEXTURE = BossRegistry.id("textures/entity/boss/vorlagh_tentacle.png");
	private static final RenderType GLOW = RenderTypes.eyes(BossRegistry.id("textures/entity/boss/vorlagh_tentacle_glow.png"));
	private final ActionTracker states = new ActionTracker();

	public VorlaghTentacleRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new VorlaghTentacleModel(ctx.bakeLayer(LAYER)), 0.6F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public void submit(PoseStack pose, SubmitNodeCollector c, int light, VorlaghTentacleRenderState s, float yRot, float xRot) {
				boolean glowing = !s.severed && (s.tentacleState == VorlaghTentacle.STATE_GLOW || s.tentacleState == VorlaghTentacle.STATE_GRAB);
				if (glowing) super.submit(pose, c, light, s, yRot, xRot);
			}

			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public VorlaghTentacleRenderState createRenderState() {
		return new VorlaghTentacleRenderState();
	}

	@Override
	public void extractRenderState(VorlaghTentacle e, VorlaghTentacleRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		float age = e.tickCount + pt;
		ActionTracker.Track t = this.states.update(e, e.state(), age);
		s.tentacleState = e.state();
		s.prevState = t.prev;
		s.stateMillis = ActionTracker.millis(age, t.start);
		s.severed = e.isSevered();
		s.index = e.index();
	}

	@Override
	public Identifier getTextureLocation(VorlaghTentacleRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(VorlaghTentacleRenderState s, PoseStack pose) {
		pose.scale(2.0F, 2.0F, 2.0F);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	protected AABB getBoundingBoxForCulling(VorlaghTentacle e, float pt) {
		return super.getBoundingBoxForCulling(e, pt).inflate(8.0, 2.0, 8.0);
	}
}
