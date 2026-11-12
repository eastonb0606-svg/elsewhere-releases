package dev.elsewhere.summoner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.summoner.SummonerContent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** One renderer for every minion: model + texture + scale. */
public class MinionRenderer<T extends MinionEntity> extends MobRenderer<T, MinionModels.State, EntityModel<MinionModels.State>> {
	private final Identifier texture;
	private final float scale;

	public MinionRenderer(EntityRendererProvider.Context ctx, EntityModel<MinionModels.State> model, String texture, float shadow, float scale) {
		super(ctx, model, shadow);
		this.texture = SummonerContent.id("textures/entity/" + texture + ".png");
		this.scale = scale;
	}

	@Override
	public MinionModels.State createRenderState() {
		return new MinionModels.State();
	}

	@Override
	public void extractRenderState(T e, MinionModels.State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.attack = e.getSwingAnimation(pt);
	}

	@Override
	public Identifier getTextureLocation(MinionModels.State s) {
		return texture;
	}

	@Override
	protected void scale(MinionModels.State s, PoseStack pose) {
		if (scale != 1F) pose.scale(scale, scale, scale);
	}
}
