package dev.elsewhere.bossart.vorlagh;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.vorlagh.VorlaghEntity;
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

/** Custom Vorlagh body renderer: VorlaghModel x3 with glowing lantern eyes and keyframe animations. */
public class VorlaghRenderer extends MobRenderer<VorlaghEntity, VorlaghRenderState, VorlaghModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BossRegistry.id("boss/vorlagh"), "main");
	private static final Identifier TEXTURE = BossRegistry.id("textures/entity/boss/vorlagh.png");
	private static final RenderType GLOW = RenderTypes.eyes(BossRegistry.id("textures/entity/boss/vorlagh_glow.png"));
	private static final float SCALE = 3.0F;
	private final ActionTracker actions = new ActionTracker();

	public VorlaghRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new VorlaghModel(ctx.bakeLayer(LAYER)), 0.0F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public VorlaghRenderState createRenderState() {
		return new VorlaghRenderState();
	}

	@Override
	public void extractRenderState(VorlaghEntity e, VorlaghRenderState s, float pt) {
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
		boolean trans = e.isTransitioning();
		s.transMillis = trans && e.transitionAnim.isStarted() ? e.transitionAnim.getTimeInMillis(age) : (trans ? 0L : -1L);
	}

	@Override
	public Identifier getTextureLocation(VorlaghRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(VorlaghRenderState s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	protected AABB getBoundingBoxForCulling(VorlaghEntity e, float pt) {
		return super.getBoundingBoxForCulling(e, pt).inflate(3.0, 7.0, 3.0);
	}
}
