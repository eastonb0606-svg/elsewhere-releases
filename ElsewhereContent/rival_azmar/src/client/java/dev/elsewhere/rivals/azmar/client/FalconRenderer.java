package dev.elsewhere.rivals.azmar.client;

import dev.elsewhere.rivals.azmar.AzmarFalcon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Renders {@link AzmarFalcon} with its own hooded falcon model (cubes of the ornament atlas, wings flapping in flight, folded when perched). */
public final class FalconRenderer extends MobRenderer<AzmarFalcon, ParrotRenderState, FalconRenderer.FalconModel> {
	public FalconRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new FalconModel(OrnamentLayer.rootOf("falcon_bird")), 0.25F);
	}

	@Override
	public ParrotRenderState createRenderState() {
		return new ParrotRenderState();
	}

	@Override
	public Identifier getTextureLocation(ParrotRenderState state) {
		return OrnamentLayer.atlas();
	}

	@Override
	public void extractRenderState(AzmarFalcon falcon, ParrotRenderState state, float partialTicks) {
		super.extractRenderState(falcon, state, partialTicks);
		state.flapAngle = (Mth.sin(Mth.lerp(partialTicks, falcon.oFlap, falcon.flap)) + 1.0F) * Mth.lerp(partialTicks, falcon.oFlapSpeed, falcon.flapSpeed);
		state.pose = falcon.isFlying() ? net.minecraft.client.model.animal.parrot.ParrotModel.Pose.FLYING : net.minecraft.client.model.animal.parrot.ParrotModel.Pose.STANDING;
	}

	public static final class FalconModel extends EntityModel<ParrotRenderState> {
		private final ModelPart wingR, wingL, tail, body;

		FalconModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.wingR = root.getChild("wing_r");
			this.wingL = root.getChild("wing_l");
			this.tail = root.getChild("tail");
			this.body = root.getChild("body");
		}

		@Override
		public void setupAnim(ParrotRenderState state) {
			super.setupAnim(state);
			boolean flying = state.pose == net.minecraft.client.model.animal.parrot.ParrotModel.Pose.FLYING;
			float fold = 1.45F;                                   // wings hang folded alongside the body
			float r = flying ? Mth.sin(state.ageInTicks * 1.2F) * 0.85F - 0.15F : fold;
			wingR.zRot = r;
			wingL.zRot = -r;
			wingR.xRot = wingL.xRot = flying ? 0.0F : 0.0F;
			tail.xRot = flying ? -0.25F : 0.1F;
			this.root.y = flying ? Mth.cos(state.ageInTicks * 0.6F) * 0.6F - 1.0F : 0.0F;
			this.root.xRot = flying ? 0.35F : 0.0F;
		}
	}
}
