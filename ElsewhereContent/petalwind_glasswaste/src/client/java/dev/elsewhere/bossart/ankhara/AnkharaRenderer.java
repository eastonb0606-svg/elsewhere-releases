package dev.elsewhere.bossart.ankhara;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.glasswaste.SerpentChain;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ankhara: the animated head model plus all 13 body segments, laid along the head's swim path (shared SerpentChain),
 * pitched to the path, rippling side to side, tapering to the tail. Fangs, crest, spines and the furnace gullet glow; the
 * gizzard plates blaze only while the glass-pillar stun exposes them.
 */
public class AnkharaRenderer extends MobRenderer<Ankhara, AnkharaRenderState, AnkharaModel> {
	public static final ModelLayerLocation HEAD_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "bossart_ankhara"), "main");
	public static final ModelLayerLocation SEGMENT_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "bossart_ankhara_segment"),
		"main");
	private static final Identifier HEAD = tex("ankhara_head"), HEAD_GLOW = tex("ankhara_head_glow"), SEG = tex("ankhara_segment"),
		SEG_GLOW = tex("ankhara_segment_glow"), GIZZARD_GLOW = tex("ankhara_gizzard_glow");
	private static final double SPACING = 2.3;
	private static final float TAIL_SCALE = 0.45F;
	private final AnkharaSegmentModel segment;
	private final Map<Integer, SerpentChain> chains = new HashMap<>();
	private final Map<Integer, Integer> lastMode = new HashMap<>();

	private static Identifier tex(String n) {
		return Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/" + n + ".png");
	}

	public AnkharaRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new AnkharaModel(ctx.bakeLayer(HEAD_LAYER)), 1.5F);
		this.segment = new AnkharaSegmentModel(ctx.bakeLayer(SEGMENT_LAYER));
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> HEAD_GLOW, (s, t) -> glow(s), this.getModel(), RenderTypes::entityTranslucentEmissive, false));
	}

	private static float glow(AnkharaRenderState s) {
		if (s.deathTime > 0) return Math.max(0F, 1F - s.deathTime / 20F);
		return 0.8F + 0.2F * Mth.sin(s.ageInTicks * 0.2F);
	}


	private static float secs(net.minecraft.world.entity.AnimationState a, float age) {
		return a.isStarted() ? a.getTimeInMillis(age) / 1000F : -1F;
	}

	/**
	 * Backs the entity events with the synced mode: a breach (burrowed to surfaced), a lunge or a stun starts its pose
	 * even if the event packet arrived before the entity was tracked; diving back under clears the surfaced poses.
	 */
	private void syncPoseToMode(Ankhara e) {
		int mode = e.mode();
		Integer prev = lastMode.put(e.getId(), mode);
		if (prev == null || prev == mode) return;
		int now = e.tickCount;
		if (mode == Ankhara.M_SURFACED && prev == Ankhara.M_BURROWED) startIfStale(e.eruptAnimation, now);
		else if (mode == Ankhara.M_LUNGE) startIfStale(e.lungeAnimation, now);
		else if (mode == Ankhara.M_STUNNED) startIfStale(e.stunAnimation, now);
		else if (mode == Ankhara.M_BURROWED) {
			e.eruptAnimation.stop();
			e.windupAnimation.stop();
			e.lungeAnimation.stop();
			e.stunAnimation.stop();
		}
	}

	private static void startIfStale(net.minecraft.world.entity.AnimationState a, int now) {
		if (!a.isStarted() || a.getTimeInMillis(now) > 250L) a.start(now);
	}

	/** 0..1 envelope: ramps up over [0, in], holds until hold, ramps down to 0 at out. */
	private static float env(float t, float in, float hold, float out) {
		if (t < 0F || t >= out) return 0F;
		if (t < in) return t / in;
		if (t < hold) return 1F;
		return 1F - (t - hold) / (out - hold);
	}

	@Override
	public AnkharaRenderState createRenderState() {
		return new AnkharaRenderState();
	}

	@Override
	public Identifier getTextureLocation(AnkharaRenderState state) {
		return HEAD;
	}

	@Override
	protected float getFlipDegrees() {
		return 0F;
	}

	@Override
	protected float getShadowRadius(AnkharaRenderState state) {
		return state.burrowed ? 0F : super.getShadowRadius(state);
	}

	@Override
	protected boolean affectedByCulling(Ankhara entity) {
		return false; // the body trails up to 30 blocks behind the head
	}

	@Override
	public void extractRenderState(Ankhara e, AnkharaRenderState s, float partialTicks) {
		super.extractRenderState(e, s, partialTicks);
		syncPoseToMode(e);
		s.erupt.copyFrom(e.eruptAnimation);
		s.windup.copyFrom(e.windupAnimation);
		s.lunge.copyFrom(e.lungeAnimation);
		s.stun.copyFrom(e.stunAnimation);
		s.burrowed = e.mode() == Ankhara.M_BURROWED;
		s.stunned = e.mode() == Ankhara.M_STUNNED;
		s.eruptT = secs(s.erupt, s.ageInTicks);
		s.windupT = secs(s.windup, s.ageInTicks);
		s.lungeT = secs(s.lunge, s.ageInTicks);
		s.stunT = secs(s.stun, s.ageInTicks);
		int tt = e.transitionTicks();
		s.roarTime = tt > 0 ? (BossBase.TRANSITION_TICKS - tt + partialTicks) / 20F : -1F;
		s.segments.clear();
		s.segYaw.clear();
		s.segPitch.clear();
		SerpentChain chain = chains.computeIfAbsent(e.getId(), id -> new SerpentChain(Ankhara.SEGMENTS, SPACING));
		Vec3 pos = e.getPosition(partialTicks);
		chain.update(pos, Vec3.directionFromRotation(0, e.getYRot()).scale(-1));
		Vec3 prev = pos.add(0, 0.4, 0);
		for (Vec3 p : chain.positions()) {
			s.segments.add(p.subtract(pos));
			Vec3 d = prev.subtract(p);
			double flat = Math.sqrt(d.x * d.x + d.z * d.z);
			s.segYaw.add(flat < 1.0E-4 ? (s.segYaw.isEmpty() ? s.bodyRot : s.segYaw.getLast()) : (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F);
			s.segPitch.add((float) (Mth.atan2(d.y, Math.max(flat, 1.0E-4)) * Mth.RAD_TO_DEG));
			prev = p;
		}
		if (chains.size() > 16) chains.keySet().removeIf(id -> id != e.getId());
		if (lastMode.size() > 16) lastMode.keySet().removeIf(id -> id != e.getId());
	}

	@Override
	public void submit(AnkharaRenderState s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(s, poseStack, collector, camera);
		if (s.isInvisible || s.segments.isEmpty()) return;
		int light = 0xF000F0; // the body lives in open desert sun even where its path dips under the sand
		int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(s.hasRedOverlay));
		RenderType base = segment.renderType(SEG);
		RenderType glow = RenderTypes.entityTranslucentEmissive(SEG_GLOW);
		RenderType gizzard = RenderTypes.entityTranslucentEmissive(GIZZARD_GLOW);
		float t = s.ageInTicks;
		float bodyGlow = glow(s);
		float gizzardGlow = s.stunned ? 0.7F + 0.3F * Mth.sin(t * 0.5F) : 0F;
		float sink = s.deathTime > 0 ? s.deathTime / 20F * 2.2F : 0F;
		float thrash = s.roarTime >= 0 ? 2.5F : 1F;
		// pose envelopes (seconds since each pose started, from the head's animation states)
		float erupt = env(s.eruptT, 0.15F, 0.6F, 1.4F);
		boolean struck = s.lungeT >= 0 && (s.windupT < 0 || s.lungeT <= s.windupT);
		float coil = struck ? 0F : env(s.windupT, 0.4F, 1.0F, 1.4F);
		float lunge = env(s.lungeT, 0.06F, 0.55F, 0.9F);
		float limp = s.stunT >= 0 ? env(s.stunT, 0.6F, 4.6F, 5.0F) : (s.stunned ? 1F : 0F);
		float calm = (1F - coil) * (1F - lunge) * (1F - limp);
		float rise = s.stunned ? 0.4F : 0F;
		int n = s.segments.size();
		final int arc = 7; // erupt: the first segments breach in a rainbow arc behind the head
		double prevH = 0;
		Vec3 prevOff = new Vec3(0, 0.4, 0);
		for (int i = 0; i < n; i++) {
			Vec3 off = s.segments.get(i);
			float f = n <= 1 ? 1F : 1F - (1F - TAIL_SCALE) * i / (n - 1);
			float yaw = s.segYaw.get(i);
			double h = i < arc ? 4.0 * erupt * Mth.sin(Mth.PI * (i + 1) / (arc + 1)) : 0;
			h += coil * 0.8 * Math.max(0, 1 - i / 4.0) + lunge * 0.35;
			float arcPitch = (float) (Mth.atan2(prevH - h, SPACING) * Mth.RAD_TO_DEG);
			prevH = h;
			// travelling ripple (calm), a tight standing S-coil (wind-up), straight and surging forward (lunge)
			float wave = Mth.sin(t * 0.3F - i * 0.9F) * 0.22F * thrash * calm + coil * 0.8F * Mth.sin(i * 0.9F) * (1F - 0.4F * i / n);
			float twist = Mth.cos(t * 0.3F - i * 0.9F) * 10F * calm + coil * 24F * Mth.cos(i * 0.9F);
			float bob = Mth.sin(t * 0.22F - i * 0.7F) * 0.12F * calm;
			Vec3 toHead = prevOff.subtract(off).multiply(1, 0, 1);
			Vec3 surge = toHead.lengthSqr() > 1.0E-4 ? toHead.normalize().scale(lunge * 1.8 * (1 - 0.5 * i / n)) : Vec3.ZERO;
			prevOff = off;
			float yr = yaw * Mth.DEG_TO_RAD;
			double sx = Mth.cos(yr) * wave + surge.x, sz = Mth.sin(yr) * wave + surge.z;
			poseStack.pushPose();
			poseStack.translate(off.x + sx, off.y + 0.55F * f + bob + rise * f + h - sink - limp * 0.25F * Math.max(0, 1 - i / 5F), off.z + sz);
			poseStack.rotateDegrees(Axis.YP, 180.0F - yaw + twist);
			poseStack.rotateDegrees(Axis.XP, s.segPitch.get(i) * (1F - erupt) + arcPitch);
			// stunned: the body goes limp and flops onto its side, baring the gizzard plates
			if (limp > 0F) poseStack.rotateDegrees(Axis.ZP, limp * (48F + 14F * Mth.sin(i * 0.8F + t * 0.05F)));
			poseStack.scale(-f, -f, f);
			collector.submitModel(segment, s, poseStack, base, light, overlay, -1, null, s.outlineColor);
			if (bodyGlow > 0.01F) collector.order(1).submitModel(segment, s, poseStack, glow, light, overlay, ARGB.white(bodyGlow), null, s.outlineColor);
			if (gizzardGlow > 0.01F) collector.order(1).submitModel(segment, s, poseStack, gizzard, light, overlay, ARGB.white(gizzardGlow), null, s.outlineColor);
			poseStack.popPose();
		}
	}
}
