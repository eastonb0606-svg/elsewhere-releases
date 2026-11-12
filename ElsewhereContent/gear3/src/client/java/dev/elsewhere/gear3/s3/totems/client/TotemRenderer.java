package dev.elsewhere.gear3.s3.totems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.totems.ClockworkSentry;
import dev.elsewhere.gear3.s3.totems.SunglassObelisk;
import dev.elsewhere.gear3.s3.totems.TotemEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a totem: its rig ({@link TotemModel}), and from the synced state the sentry's aim and bolt streak (4 ticks
 * after each shot) and the obelisk's sunbeam (while lit and aiming; wider and whiter once ramped). Beams are two
 * crossed, double-sided emissive quads from the muzzle / lens to the target's chest; the culling box includes the target.
 */
public class TotemRenderer<T extends TotemEntity> extends MobRenderer<T, TotemRenderer.State, TotemModel> {
	static final Identifier BEAM = Gear3Content.id("textures/entity/totem/totem_beam.png");
	static final RenderType BEAM_TYPE = RenderTypes.entityTranslucentEmissive(BEAM);
	static final int SUN = 0xE0FFC850, SUN_RAMPED = 0xF0FFF2B8, BOLT = 0xF0B8F5E0;
	static final float BOLT_TICKS = 4F;

	public static class State extends LivingEntityRenderState {
		public float pulseAge = 100F;
		public boolean lit = true, ramped, hasAim;
		/** Head yaw / pitch toward the target, relative to the body (radians, model space). */
		public float aimYaw, aimPitch;
		/** Muzzle / lens -> target chest, world axes, from the entity's feet (blocks). */
		public float ox, oy, oz, tx, ty, tz;
	}

	private final TotemModel.Kind kind;
	private final Identifier texture;

	public TotemRenderer(EntityRendererProvider.Context ctx, TotemModel.Kind kind) {
		super(ctx, new TotemModel(ctx.bakeLayer(layer(kind)), kind), kind == TotemModel.Kind.OBELISK ? 0.55F : 0.45F);
		this.kind = kind;
		this.texture = Gear3Content.id("textures/entity/totem/" + switch (kind) {
			case BRAMBLE -> "bramble_totem";
			case SENTRY -> "clockwork_sentry";
			case OBELISK -> "azmar_sunglass_obelisk";
		} + ".png");
	}

	static net.minecraft.client.model.geom.ModelLayerLocation layer(TotemModel.Kind kind) {
		return switch (kind) {
			case BRAMBLE -> TotemModel.BRAMBLE;
			case SENTRY -> TotemModel.SENTRY;
			case OBELISK -> TotemModel.OBELISK;
		};
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.pulseAge = Math.max(0F, e.tickCount - e.clientPulseTick + pt);
		s.lit = e.isLit();
		s.ramped = e.isRamped();
		s.hasAim = false;
		int id = e.aimTargetId();
		Entity t = id < 0 ? null : e.level().getEntity(id);
		if (t == null || !t.isAlive()) return;
		double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
		double gx = Mth.lerp(pt, t.xo, t.getX()), gy = Mth.lerp(pt, t.yo, t.getY()) + t.getBbHeight() * 0.6, gz = Mth.lerp(pt, t.zo, t.getZ());
		double originY = kind == TotemModel.Kind.OBELISK ? SunglassObelisk.LENS_Y : ClockworkSentry.MUZZLE_Y;
		s.hasAim = true;
		s.ox = 0F;
		s.oy = (float) originY;
		s.oz = 0F;
		s.tx = (float) (gx - ex);
		s.ty = (float) (gy - ey);
		s.tz = (float) (gz - ez);
		double dx = s.tx, dy = s.ty - originY, dz = s.tz;
		float worldYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
		s.aimYaw = Mth.wrapDegrees(worldYaw - s.bodyRot) * Mth.DEG_TO_RAD; // like vanilla netHeadYaw
		s.aimPitch = (float) -Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz));
	}

	@Override
	public Identifier getTextureLocation(State s) {
		return texture;
	}

	@Override
	protected AABB getBoundingBoxForCulling(T e, float pt) {
		AABB box = super.getBoundingBoxForCulling(e, pt);
		int id = e.aimTargetId();
		Entity t = id < 0 ? null : e.level().getEntity(id);
		return t == null ? box : box.minmax(t.getBoundingBox());
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		super.submit(s, pose, out, camera);
		if (!s.hasAim || s.deathTime > 0) return;
		final float width;
		final int color;
		if (kind == TotemModel.Kind.OBELISK) {
			if (!s.lit) return;
			width = s.ramped ? 0.16F : 0.09F;
			color = s.ramped ? SUN_RAMPED : SUN;
		} else if (kind == TotemModel.Kind.SENTRY) {
			if (s.pulseAge >= BOLT_TICKS) return;
			float fade = 1F - s.pulseAge / BOLT_TICKS;
			width = 0.035F;
			color = ((int) (0xF0 * fade) << 24) | (BOLT & 0xFFFFFF);
		} else return;
		final Vec3 a = new Vec3(s.ox, s.oy, s.oz), b = new Vec3(s.tx, s.ty, s.tz);
		final float scroll = s.ageInTicks * 0.05F;
		out.submitCustomGeometry(pose, BEAM_TYPE, (p, buf) -> beam(p, buf, a, b, width, color, scroll));
	}

	/** Two crossed quads along a -> b, each drawn both ways round (no culling surprises). */
	static void beam(PoseStack.Pose p, VertexConsumer buf, Vec3 a, Vec3 b, float w, int color, float scroll) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1.0E-3) return;
		Vec3 dir = d.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-4) side = dir.cross(new Vec3(1, 0, 0));
		side = side.normalize().scale(w);
		Vec3 up = dir.cross(side).normalize().scale(w);
		float v1 = scroll, v2 = scroll + (float) len;
		quad(p, buf, a, b, side, color, v1, v2);
		quad(p, buf, a, b, up, color, v1, v2);
	}

	private static void quad(PoseStack.Pose p, VertexConsumer buf, Vec3 a, Vec3 b, Vec3 off, int color, float v1, float v2) {
		Vec3 a0 = a.subtract(off), a1 = a.add(off), b0 = b.subtract(off), b1 = b.add(off);
		vertex(p, buf, a0, color, 0F, v1);
		vertex(p, buf, a1, color, 1F, v1);
		vertex(p, buf, b1, color, 1F, v2);
		vertex(p, buf, b0, color, 0F, v2);
		vertex(p, buf, b0, color, 0F, v2);
		vertex(p, buf, b1, color, 1F, v2);
		vertex(p, buf, a1, color, 1F, v1);
		vertex(p, buf, a0, color, 0F, v1);
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer buf, Vec3 v, int color, float u, float vv) {
		buf.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(color).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p, 0, 1, 0);
	}
}
