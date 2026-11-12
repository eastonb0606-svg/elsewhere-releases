package dev.elsewhere.kingdom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.kingdom.war.HrafnholtLongship;
import dev.elsewhere.kingdom.war.Longship;
import dev.elsewhere.kingdom.war.ShipShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Hrafnholt longship from block models: the {@link ShipShape} layout (the same blocks the beached prop places) in the synced style, plus a row of
 * round shields (green and brown wool plates with a bone boss) on each outer hull side. Raiders are separate entities seated by the ship; they render through
 * vanilla's avatar renderer in its riding pose (their skins via {@link NpcSkin}).
 */
public class HrafnholtLongshipRenderer extends EntityRenderer<HrafnholtLongship, HrafnholtLongshipRenderer.State> {
	private static final BlockDisplayContext CTX = BlockDisplayContext.create();
	/** Shield plate pieces (half-width across the hull length, half-height) making a pixel-round disc, drawn a hair apart so they never z-fight. */
	private static final float[][] DISC = {{0.40F, 0.27F}, {0.27F, 0.40F}, {0.34F, 0.34F}};
	private final BlockModelResolver resolver;
	private Longship.Style lastStyle;
	private BlockState[] uniq = new BlockState[0];
	private int[] idx = new int[0];

	public static final class State extends EntityRenderState {
		float yaw, bob;
		BlockModelRenderState[] models = new BlockModelRenderState[0];
		int[] idx = new int[0];
		int boss;
	}

	public HrafnholtLongshipRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.resolver = ctx.getBlockModelResolver();
		this.shadowRadius = 0;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	private void layout(Longship.Style style) {
		if (style.equals(lastStyle)) return;
		List<BlockState> u = new ArrayList<>();
		int[] ix = new int[ShipShape.PARTS.size()];
		for (int i = 0; i < ix.length; i++) {
			BlockState bs = ShipShape.state(ShipShape.PARTS.get(i), style);
			int k = u.indexOf(bs);
			if (k < 0) {
				k = u.size();
				u.add(bs);
			}
			ix[i] = k;
		}
		u.add(Blocks.BONE_BLOCK.defaultBlockState()); // shield bosses
		uniq = u.toArray(new BlockState[0]);
		idx = ix;
		lastStyle = style;
	}

	@Override
	public void extractRenderState(HrafnholtLongship ship, State st, float pt) {
		super.extractRenderState(ship, st, pt);
		st.yaw = Mth.rotLerp(pt, ship.yRotO, ship.getYRot());
		st.bob = ship.isBeached() ? 0 : Mth.sin((ship.tickCount + pt) * 0.1F) * 0.03F;
		layout(ship.style());
		if (st.models.length != uniq.length) {
			st.models = new BlockModelRenderState[uniq.length];
			for (int i = 0; i < uniq.length; i++) st.models[i] = new BlockModelRenderState();
		}
		for (int i = 0; i < uniq.length; i++) resolver.update(st.models[i], uniq[i], CTX);
		st.idx = idx;
		st.boss = uniq.length - 1;
	}

	@Override
	public void submit(State st, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0, st.bob, 0);
		pose.rotateDegrees(Axis.YP, -st.yaw);
		int light = st.lightCoords;
		for (int i = 0; i < st.idx.length; i++) {
			ShipShape.Part p = ShipShape.PARTS.get(i);
			BlockModelRenderState m = st.models[st.idx[i]];
			if (!p.shield()) {
				pose.pushPose();
				pose.translate(p.x() - 0.5F, p.y(), p.z() - 0.5F);
				m.submit(pose, out, light, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
				continue;
			}
			float side = Math.signum(p.x()), face = side * 2.5F, cy = p.y() + 0.72F;
			for (int k = 0; k < DISC.length; k++) {
				pose.pushPose();
				pose.translate(face + side * (0.01F + k * 0.004F), cy, p.z());
				pose.scale(0.05F, DISC[k][1] * 2, DISC[k][0] * 2);
				pose.translate(side < 0 ? -1F : 0F, -0.5F, -0.5F);
				m.submit(pose, out, light, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
			}
			pose.pushPose();
			pose.translate(face + side * 0.07F, cy, p.z());
			pose.scale(0.07F, 0.18F, 0.18F);
			pose.translate(side < 0 ? -1F : 0F, -0.5F, -0.5F);
			st.models[st.boss].submit(pose, out, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		pose.popPose();
		super.submit(st, pose, out, camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(HrafnholtLongship ship, float pt) {
		return ship.getBoundingBox().inflate(7.5, 0, 7.5).expandTowards(0, 8, 0).expandTowards(0, -1, 0);
	}
}
