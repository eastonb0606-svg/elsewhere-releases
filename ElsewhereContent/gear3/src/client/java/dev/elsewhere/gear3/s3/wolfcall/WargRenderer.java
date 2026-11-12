package dev.elsewhere.gear3.s3.wolfcall;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.gear3.Gear3Content;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The Warg: the vanilla adult wolf rig and animations (walk, head, tail) at {@link Wolfcall#WARG_SCALE}, with its own skin
 * (charcoal war-wolf in a hacksilver-ringed harness, tools/wolfcall_assets.py). Hackles up (wolf "angry" pose, tail raised)
 * while it hunts; never sits, no collar, no wolf armor.
 */
public class WargRenderer extends MobRenderer<WargEntity, WolfRenderState, WolfModel> {
	static final Identifier TEXTURE = Gear3Content.id("textures/entity/hrafnholt_warg.png");
	/** Vanilla Wolf.getTailAngle(): angry, and a wild wolf's resting tail (PI / 5). */
	static final float TAIL_ANGRY = 1.5393804F, TAIL_REST = 0.62831855F;

	public WargRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new AdultWolfModel(ctx.bakeLayer(ModelLayers.WOLF)), 0.5F * Wolfcall.WARG_SCALE);
	}

	@Override
	public WolfRenderState createRenderState() {
		return new WolfRenderState();
	}

	@Override
	public void extractRenderState(WargEntity e, WolfRenderState s, float partialTick) {
		super.extractRenderState(e, s, partialTick);
		s.isAngry = e.isAggressive();
		s.isSitting = false;
		s.tailAngle = s.isAngry ? TAIL_ANGRY : TAIL_REST;
		s.headRollAngle = 0F;
		s.shakeAnim = 0F;
		s.wetShade = 1F;
		s.texture = TEXTURE;
		s.collarColor = null;
		s.bodyArmorItem = ItemStack.EMPTY;
	}

	@Override
	public Identifier getTextureLocation(WolfRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(WolfRenderState s, PoseStack pose) {
		pose.scale(Wolfcall.WARG_SCALE, Wolfcall.WARG_SCALE, Wolfcall.WARG_SCALE);
	}
}
