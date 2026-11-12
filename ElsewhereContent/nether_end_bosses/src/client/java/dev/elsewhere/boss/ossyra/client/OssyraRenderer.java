package dev.elsewhere.boss.ossyra.client;

import dev.elsewhere.boss.ossyra.Ossyra;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class OssyraRenderer extends BossRenderer<Ossyra, BossRenderState, OssyraRenderer.Model> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("ossyra"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/ossyra/ossyra.png");

	public OssyraRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 2.5F, 6.0F, true);
	}

	@Override
	public BossRenderState createRenderState() {
		return new BossRenderState();
	}

	@Override
	public Identifier getTextureLocation(BossRenderState state) {
		return TEXTURE;
	}

	/** The lid lifts to expose the pearl when open; shell fragments orbit; the pearl pulses. */
	public static class Model extends EntityModel<BossRenderState> {
		private final ModelPart lid;
		private final ModelPart pearl;
		private final ModelPart[] frags = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			this.lid = root.getChild("lid");
			this.pearl = root.getChild("pearl");
			for (int i = 0; i < 4; i++) frags[i] = root.getChild("frag" + i);
		}

		@Override
		public void setupAnim(BossRenderState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			boolean open = state.flag(Ossyra.FLAG_OPEN);
			lid.y -= open ? 7.0F : 0.0F;
			lid.yRot = open ? Mth.sin(age * 0.05F) * 0.2F : 0;
			pearl.y -= open ? 3.0F + Mth.sin(age * 0.2F) : 0;
			float pulse = 1.0F + 0.06F * Mth.sin(age * 0.3F);
			pearl.xScale = pearl.yScale = pearl.zScale = pulse;
			for (int i = 0; i < 4; i++) {
				float a = age * 0.05F + i * Mth.HALF_PI;
				frags[i].x = Mth.cos(a) * 13;
				frags[i].z = Mth.sin(a) * 13;
				frags[i].y = 12 + Mth.sin(age * 0.1F + i) * 2;
				frags[i].yRot = -a;
			}
		}
	}
}
