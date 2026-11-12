package dev.elsewhere.boss.mournmaw.client;

import dev.elsewhere.boss.mournmaw.Mournmaw;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class MournmawRenderer extends BossRenderer<Mournmaw, BossRenderState, MournmawRenderer.Model> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("mournmaw"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/mournmaw/mournmaw.png");
	private static final Identifier WAIL = Reg.id("textures/entity/mournmaw/mournmaw_wail.png");

	public MournmawRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 3.0F, 8.0F, true);
	}

	@Override
	public BossRenderState createRenderState() {
		return new BossRenderState();
	}

	@Override
	public Identifier getTextureLocation(BossRenderState state) {
		return state.flag(Mournmaw.FLAG_INHALE) || state.flag(Mournmaw.FLAG_CHAINS_GLOW) ? WAIL : TEXTURE;
	}

	/** Weep (body bob), tentacles sway; the whip snaps them outward; the wail tilts the face up. */
	public static class Model extends EntityModel<BossRenderState> {
		private final ModelPart body;
		private final ModelPart[] tentacles = new ModelPart[9];

		public Model(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			for (int i = 0; i < 9; i++) tentacles[i] = body.getChild("tentacle" + i);
		}

		@Override
		public void setupAnim(BossRenderState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			body.y += Mth.sin(age * 0.05F) * 0.4F;
			boolean whip = state.flag(Mournmaw.FLAG_CHAINS_GLOW);
			for (int i = 0; i < 9; i++) {
				tentacles[i].xRot = 0.2F * Mth.sin(age * 0.1F + i) + (whip ? 0.6F * Mth.sin(age * 0.8F + i) : 0);
				tentacles[i].zRot = 0.1F * Mth.cos(age * 0.08F + i * 2);
			}
			if (state.flag(Mournmaw.FLAG_INHALE)) body.xRot = -0.25F;
			if (state.transition) body.zRot = 0.15F * Mth.sin(age * 0.6F);
			if (state.dormant) body.xRot = 0.2F;
		}
	}
}
