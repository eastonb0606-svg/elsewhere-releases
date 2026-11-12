package dev.elsewhere.duelist.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.duelist.boss.PoseTable;
import dev.elsewhere.duelist.client.DuelistRenderer;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * 3.0 (P4/B4) phase aura, the custom art POLISH P4 asks for: from a rival boss's second phase, glowing kingdom glyphs float on a
 * shell just outside the armour (Hrafnholt frost runes, Azmar gold sun-bands and the eye, Mirewick crimson ledger lines and a gold
 * coin seal, Aldermark white oath lines), pulsing slowly; from the third phase a kingdom-coloured energy swirl (the charged-creeper
 * technique) wraps the body. Drawn on the vanilla avatar renderers (26.3 draws every boss's {@link AvatarRenderState} there) for
 * {@link DuelistRenderer.BossRenderState} only, so real players never get it. Both shells take the boss's animated pose (the
 * {@code PlayerModel.setupAnim} mixin runs for them too). Glyphs within 48 blocks, swirl within 32, nothing else per frame:
 * two extra model submits per boss at most, no allocation. Kill switch: {@code "bossAura3": false} in {@code config/elsewhere-client.json}.
 */
public final class PhaseAuraLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final String[] KINDS = {"aldermark", "hrafnholt", "azmar", "mirewick"};
	private static final Identifier[] GLYPHS = new Identifier[KINDS.length], SWIRL = new Identifier[KINDS.length];
	private static final double SWIRL_SQ = 32 * 32;

	static {
		for (int i = 0; i < KINDS.length; i++) {
			GLYPHS[i] = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/duelist_aura/" + KINDS[i] + "_glyphs.png");
			SWIRL[i] = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/duelist_aura/" + KINDS[i] + "_swirl.png");
		}
	}

	private final PlayerModel glyphs, swirl;

	public PhaseAuraLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
		// outer armour is inflated by 1.0, so the glyph shell sits just outside it and the swirl a little further out
		this.glyphs = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(1.08F), false), 64, 64).bakeRoot(), false);
		this.swirl = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(1.35F), false), 64, 64).bakeRoot(), false);
	}

	/** Texture paths, for the self-test and the shot list. */
	public static Identifier glyphs(int kind) {
		return GLYPHS[Math.clamp(kind, 0, KINDS.length - 1)];
	}

	public static Identifier swirl(int kind) {
		return SWIRL[Math.clamp(kind, 0, KINDS.length - 1)];
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (!(state instanceof DuelistRenderer.BossRenderState s) || s.auraLevel <= 0 || s.auraKind < 0 || s.auraKind >= KINDS.length || s.isInvisible) return;
		int k = s.auraKind;
		float age = s.ageInTicks;
		// slow pulse; the third phase burns brighter and faster
		float pulse = s.auraLevel >= 2 ? 0.80F + 0.20F * Mth.sin(age * 0.24F) : 0.55F + 0.20F * Mth.sin(age * 0.12F);
		collector.order(1).submitModel(glyphs, state, pose, RenderTypes.eyes(GLYPHS[k]), light, OverlayTexture.NO_OVERLAY, ARGB.colorFromFloat(1F, pulse, pulse, pulse), null, state.outlineColor);
		if (s.auraLevel >= 2 && s.distanceToCameraSq <= SWIRL_SQ) {
			float drift = PoseTable.STYLES[k].breathRate() * 0.25F; // each kingdom's swirl drifts at its own pace
			float u = (age * drift) % 1F, v = (age * 0.01F) % 1F;
			collector.order(2).submitModel(swirl, state, pose, RenderTypes.energySwirl(SWIRL[k], u, v), light, OverlayTexture.NO_OVERLAY, -8355712, null, state.outlineColor);
		}
	}
}
