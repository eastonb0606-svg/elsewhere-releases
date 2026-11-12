package dev.elsewhere.petalwind_glasswaste.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.glasswaste.SerpentChain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One renderer for every entity in this module: a generated {@link PGModel}, a texture, a scale, an optional per-entity
 * state extractor and, for serpents, a segment model drawn along a client-side {@link SerpentChain} of the head's path.
 */
public class PGRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, PGState, PGModel> {
	private final Identifier texture;
	private final float modelScale;
	private final BiConsumer<T, PGState> extractor;
	private final @Nullable PGModel segmentModel;
	private final @Nullable Identifier segmentTexture;
	private final int segments;
	private final double spacing;
	private final float segmentScaleEnd;
	private final boolean translucent;
	private final Map<Integer, SerpentChain> chains = new HashMap<>();

	public PGRenderer(EntityRendererProvider.Context ctx, PGModel model, String texture, float modelScale, float shadow, BiConsumer<T, PGState> extractor,
		boolean translucent, @Nullable PGModel segmentModel, @Nullable String segmentTexture, int segments, double spacing, float segmentScaleEnd) {
		super(ctx, model, shadow);
		this.texture = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + texture + ".png");
		this.modelScale = modelScale;
		this.extractor = extractor;
		this.translucent = translucent;
		this.segmentModel = segmentModel;
		this.segmentTexture = segmentTexture == null ? null : Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + segmentTexture + ".png");
		this.segments = segments;
		this.spacing = spacing;
		this.segmentScaleEnd = segmentScaleEnd;
	}

	@Override
	public PGState createRenderState() {
		return new PGState();
	}

	@Override
	public Identifier getTextureLocation(PGState state) {
		return texture;
	}

	@Override
	protected void scale(PGState state, PoseStack poseStack) {
		poseStack.scale(modelScale, modelScale, modelScale);
	}

	@Override
	protected @Nullable RenderType getRenderType(PGState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		if (translucent && isBodyVisible) return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture);
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	public void extractRenderState(T entity, PGState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.pose = 0;
		state.flag = false;
		extractor.accept(entity, state);
		state.segments.clear();
		state.segmentYaw.clear();
		if (segmentModel != null) {
			state.lightCoords = 0xF000F0; // serpents live in open desert sun; their trail may dip into the sand
			SerpentChain chain = chains.computeIfAbsent(entity.getId(), id -> new SerpentChain(segments, spacing));
			Vec3 pos = entity.getPosition(partialTicks);
			chain.update(pos, Vec3.directionFromRotation(0, entity.getYRot()).scale(-1));
			List<Vec3> list = chain.positions();
			Vec3 prev = pos;
			for (Vec3 p : list) {
				state.segments.add(p.subtract(pos));
				Vec3 d = prev.subtract(p);
				state.segmentYaw.add((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F);
				prev = p;
			}
			if (chains.size() > 64) chains.clear();
		}
	}

	@Override
	public void submit(PGState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		if (segmentModel == null || state.segments.isEmpty() || state.isInvisible) return;
		segmentModel.setupAnim(state);
		RenderType type = segmentModel.renderType(segmentTexture);
		int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(state.hasRedOverlay));
		for (int i = 0; i < state.segments.size(); i++) {
			Vec3 off = state.segments.get(i);
			float f = segments <= 1 ? 1 : 1 - (1 - segmentScaleEnd) * i / (float) (segments - 1);
			poseStack.pushPose();
			poseStack.translate(off.x, off.y, off.z);
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.segmentYaw.get(i));
			poseStack.scale(-modelScale * f, -modelScale * f, modelScale * f);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			collector.submitModel(segmentModel, state, poseStack, type, state.lightCoords, overlay, -1, null, state.outlineColor);
			poseStack.popPose();
		}
	}
}
