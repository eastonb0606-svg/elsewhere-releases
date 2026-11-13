package dev.elsewhere.arsenal.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elsewhere.arsenal.anim.SwingCurves;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * One swing-trail ribbon: a quad strip between the inner and outer weapon edge of the last few swing samples, glowing
 * (the additive lightning render type, no texture) and fading toward the tail. Drawn from both sides.
 * <p>
 * Geometry is submitted for a deferred flush, so each ribbon keeps its own points: they come from a small fixed pool
 * that is reused round-robin (no per-frame allocation). {@link #POOL} entries is also the hard cap of ribbons per frame.
 */
final class TrailGeom implements SubmitNodeCollector.CustomGeometryRenderer {
	static final RenderType TYPE = RenderTypes.lightning();
	static final int POOL = 16;
	private static final TrailGeom[] RING = new TrailGeom[POOL];
	private static int next;

	static {
		for (int i = 0; i < POOL; i++) RING[i] = new TrailGeom();
	}

	final float[] pts = new float[SwingCurves.TRAIL_MAX * 6];
	int count;
	int color = 0xFFFFFF;
	float alpha = 0.32F;

	static TrailGeom next() {
		TrailGeom g = RING[next];
		next = (next + 1) % POOL;
		g.count = 0;
		g.alpha = 0.32F;
		return g;
	}

	@Override
	public void render(PoseStack.Pose pose, VertexConsumer buf) {
		for (int k = 0; k + 1 < count; k++) {
			int a0 = Math.round(255 * SwingCurves.trailAlpha(k, count, alpha));
			int a1 = Math.round(255 * SwingCurves.trailAlpha(k + 1, count, alpha));
			int c0 = (a0 << 24) | color, c1 = (a1 << 24) | color;
			int i = k * 6, j = (k + 1) * 6;
			// front face
			v(buf, pose, i, c0);
			v(buf, pose, i + 3, c0);
			v(buf, pose, j + 3, c1);
			v(buf, pose, j, c1);
			// back face (reverse winding)
			v(buf, pose, j, c1);
			v(buf, pose, j + 3, c1);
			v(buf, pose, i + 3, c0);
			v(buf, pose, i, c0);
		}
	}

	private void v(VertexConsumer buf, PoseStack.Pose pose, int o, int argb) {
		// thin ribbon: the outer edge (o + 3) is pulled halfway to the inner edge, so it never fills the view
		float k = (o % 6 == 0) ? 0F : 0.5F;
		int b = o - (o % 6);
		float x = pts[b] + (pts[b + 3] - pts[b]) * k, y = pts[b + 1] + (pts[b + 4] - pts[b + 1]) * k, z = pts[b + 2] + (pts[b + 5] - pts[b + 2]) * k;
		buf.addVertex(pose, x, y, z).setColor(argb);
	}
}
