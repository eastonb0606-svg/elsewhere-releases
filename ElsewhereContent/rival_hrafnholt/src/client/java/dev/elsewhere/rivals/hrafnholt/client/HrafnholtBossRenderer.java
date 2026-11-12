package dev.elsewhere.rivals.hrafnholt.client;

import dev.elsewhere.duelist.client.DuelistRenderer;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** The duelist renderer with the Risen skin swap: a seat boss that was promoted from a Hird-Captain wears {@code boss/risen_<slot>.png}. */
public class HrafnholtBossRenderer extends DuelistRenderer<HrafnholtBoss> {
	public HrafnholtBossRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void extractRenderState(HrafnholtBoss e, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		Identifier tex = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + (e.isRisen() ? e.h.risenSkinPath() : e.h.skinPath()) + ".png");
		if (state instanceof BossRenderState s) s.texture = tex;
		SkinBridge.apply(state, tex);
	}
}
