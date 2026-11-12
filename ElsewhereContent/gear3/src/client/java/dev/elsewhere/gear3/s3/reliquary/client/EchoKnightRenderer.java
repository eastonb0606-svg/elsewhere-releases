package dev.elsewhere.gear3.s3.reliquary.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.reliquary.EchoKnight;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Echo Knight renderer: {@link EchoKnightModel} a little taller than a player, plus a full-bright glow layer (visor slit, sigil, blade edge). */
public class EchoKnightRenderer extends MobRenderer<EchoKnight, EchoKnightRenderer.State, EchoKnightModel> {
	public static final Identifier TEXTURE = Gear3Content.id("textures/entity/echo_knight.png");
	private static final RenderType GLOW = RenderTypes.eyes(Gear3Content.id("textures/entity/echo_knight_glow.png"));
	static final float SCALE = 1.1F;

	/** Render state: the sword swing (0..1, from the synced attack swing). */
	public static class State extends LivingEntityRenderState {
		public float attack;
	}

	public EchoKnightRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new EchoKnightModel(ctx.bakeLayer(EchoKnightModel.LAYER)), 0.55F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EchoKnight e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.attack = e.getSwingAnimation(pt);
	}

	@Override
	public Identifier getTextureLocation(State s) {
		return TEXTURE;
	}

	@Override
	protected void scale(State s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}
}
