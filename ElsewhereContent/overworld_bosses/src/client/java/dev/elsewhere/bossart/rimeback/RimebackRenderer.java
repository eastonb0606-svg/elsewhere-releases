package dev.elsewhere.bossart.rimeback;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.rimeback.RimebackEntity;
import dev.elsewhere.bossart.overworld_b.ActionTracker;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.phys.AABB;

/** Custom Rimeback renderer: RimebackModel x2.6 with a glowing eyes/icicle layer and keyframe animations. */
public class RimebackRenderer extends MobRenderer<RimebackEntity, RimebackRenderState, RimebackModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BossRegistry.id("boss/rimeback"), "main");
	private static final Identifier TEXTURE = BossRegistry.id("textures/entity/boss/rimeback.png");
	private static final RenderType GLOW = RenderTypes.eyes(BossRegistry.id("textures/entity/boss/rimeback_glow.png"));
	private static final float SCALE = 2.6F;
	private final ActionTracker actions = new ActionTracker();
	private final ActionTracker transitions = new ActionTracker();

	public RimebackRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new RimebackModel(ctx.bakeLayer(LAYER)), 2.2F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public RimebackRenderState createRenderState() {
		return new RimebackRenderState();
	}

	@Override
	public void extractRenderState(RimebackEntity e, RimebackRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		float age = e.tickCount + pt;
		int a = e.getAction();
		s.action = a;
		AnimationState st = a > 0 && a < e.actionAnim.length ? e.actionAnim[a] : null;
		s.actionMillis = st != null && st.isStarted() ? st.getTimeInMillis(age) : 0L;
		ActionTracker.Track t = this.actions.update(e, a, age);
		s.follow = a == 0 ? t.prev : 0;
		long fm = ActionTracker.millis(age, t.start);
		s.followMillis = a == 0 && fm < 1000L && !e.isTransitioning() ? fm : -1L;
		s.holding = e.isHolding();
		s.shattered = e.isShattered();
		boolean trans = e.isTransitioning();
		s.transMillis = trans && e.transitionAnim.isStarted() ? e.transitionAnim.getTimeInMillis(age) : (trans ? 0L : -1L);
		ActionTracker.Track tr = this.transitions.update(e, trans ? 1 : 0, age);
		s.shatterMillis = trans && s.shattered ? ActionTracker.millis(age, tr.start) : -1L;
	}

	@Override
	public Identifier getTextureLocation(RimebackRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(RimebackRenderState s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	protected AABB getBoundingBoxForCulling(RimebackEntity e, float pt) {
		return super.getBoundingBoxForCulling(e, pt).inflate(3.0, 2.0, 3.0);
	}
}
