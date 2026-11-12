package dev.elsewhere.kingdom.client.impostor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.kingdom.armies.impostor.RankEntity;
import dev.elsewhere.kingdom.armies.impostor.RankLayout;
import dev.elsewhere.kingdom.client.lod.LodSkins;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Draws a rank impostor (ARMIES.md 3.6): up to 16 figures from one entity, each one shared player model with the unit's <b>baked LOD
 * skin</b> ({@link LodSkins}, s-lod's atlas of skin + armour + tabard and trim colour in one texture: one layer, no armour passes). Figures
 * stand in the kingdom's formation (synced shape, spacing, a seeded jitter), follow the ground client-side, dead-reckon along the heading
 * between the 10-tick position updates, walk with a phase from game time and their index, and in FIGHT swing at random, all client-side
 * with no packets. No nameplates, no shadow. The whole rank is culled as one entity (its box covers every figure).
 */
public final class RankRenderer extends EntityRenderer<RankEntity, RankRenderer.State> {
	private static final SwingAnimation SWING = SwingAnimation.DEFAULT;
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private final PlayerModel model;

	public RankRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0F;
	}

	public static final class State extends EntityRenderState {
		final AvatarRenderState[] fig = new AvatarRenderState[RankLayout.MAX];
		final Identifier[] tex = new Identifier[RankLayout.MAX];
		final float[] ox = new float[RankLayout.MAX], oy = new float[RankLayout.MAX], oz = new float[RankLayout.MAX], yaw = new float[RankLayout.MAX];
		int n;

		State() {
			for (int i = 0; i < RankLayout.MAX; i++) {
				AvatarRenderState s = new AvatarRenderState();
				s.showHat = s.showJacket = s.showLeftPants = s.showRightPants = s.showLeftSleeve = s.showRightSleeve = true; // the baked armour is the outer layer
				s.showCape = false;
				s.mainArm = HumanoidArm.RIGHT;
				s.rightArmPose = s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
				s.scale = s.ageScale = 1F;
				fig[i] = s;
			}
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/**
	 * The texture for a figure's look ({@code skin;head;chest;legs;feet;dye}) in a rank of this tabard colour: the baked LOD skin when one was
	 * baked for exactly that look, else the unit's own skin (never a wrong kingdom's colours). Cached per look.
	 */
	static Identifier texture(String look, int tabard) {
		String key = look + "#" + tabard;
		Identifier t = TEXTURES.get(key);
		if (t != null) return t;
		String[] p = look.split(";", -1);
		Identifier skin = p.length > 0 ? Identifier.tryParse(p[0]) : null;
		if (skin == null) skin = dev.elsewhere.kingdom.UnitType.DEFAULT_SKIN;
		Identifier baked = LodSkins.baked(skin, tabard, stack(p, 1), stack(p, 2), stack(p, 3), stack(p, 4));
		t = baked != null ? baked : skin;
		if (TEXTURES.size() > 512) TEXTURES.clear();
		TEXTURES.put(key, t);
		return t;
	}

	private static ItemStack stack(String[] p, int i) {
		if (i >= p.length || p[i].isEmpty()) return ItemStack.EMPTY;
		Identifier id = Identifier.tryParse(p[i]);
		var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	@Override
	public void extractRenderState(RankEntity e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		float[] lay = e.layout();
		float yaw = net.minecraft.util.Mth.rotLerp(partial, e.drawYawO, e.drawYaw);
		RankEntity.State st = e.state();
		boolean moving = e.speed() > 0.005F && (st == RankEntity.State.MARCH || st == RankEntity.State.ROUT);
		// dead reckoning: carry on along the heading between position updates
		double ahead = moving ? e.reckon(partial) : 0;
		double hx = -Math.sin(Math.toRadians(e.getYRot())) * ahead, hz = Math.cos(Math.toRadians(e.getYRot())) * ahead;
		float age = s.ageInTicks;
		long seed = e.seed();
		int n = 0;
		for (int i = 0; i < e.slots() && i < RankLayout.MAX; i++) {
			if (!e.present(i)) continue;
			AvatarRenderState f = s.fig[n];
			float ph = RankLayout.hash01(seed, 100 + i);
			s.ox[n] = (float) (RankLayout.worldX(lay[2 * i], lay[2 * i + 1], yaw) + hx);
			s.oz[n] = (float) (RankLayout.worldZ(lay[2 * i], lay[2 * i + 1], yaw) + hz);
			s.oy[n] = e.groundDy[i];
			s.yaw[n] = yaw + (e.jitter() > 0 ? (ph - 0.5F) * 16F * Math.min(1F, e.jitter()) : 0F);
			s.tex[n] = texture(e.look(e.lookOf(i)), e.tabard());
			f.ageInTicks = age + ph * 40F;
			f.walkAnimationSpeed = moving ? Math.min(1F, e.speed() * (st == RankEntity.State.ROUT ? 8F : 6F)) : 0F;
			f.walkAnimationPos = moving ? (age + partial) * (st == RankEntity.State.ROUT ? 0.85F : 0.6F) + ph * 7F : 0F;
			f.yRot = 0F;
			f.xRot = st == RankEntity.State.ROUT ? 0F : -4F + ph * 8F; // heads not all level
			f.bodyRot = 0F;
			f.lightCoords = s.lightCoords;
			f.outlineColor = s.outlineColor;
			if (st == RankEntity.State.FIGHT) {
				// random blows, client-side: each man swings once every 20-50 ticks, 6 ticks a swing
				int period = 20 + (int) (ph * 30);
				float t = ((age + ph * period) % period) / 6F;
				f.swingAnimation = t < 1F ? t : 0F;
				f.currentSwing = new LivingEntity.SwingDescription(InteractionHand.MAIN_HAND, SWING, 6);
			} else {
				f.swingAnimation = 0F;
			}
			n++;
		}
		s.n = n;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < s.n; i++) {
			pose.pushPose();
			pose.translate(s.ox[i], s.oy[i], s.oz[i]);
			pose.rotateDegrees(Axis.YP, 180F - s.yaw[i]);
			pose.scale(-0.9375F, -0.9375F, 0.9375F); // the player renderer's scale
			pose.translate(0F, -1.501F, 0F);
			collector.submitModel(model, s.fig[i], pose, RenderTypes.entityCutout(s.tex[i]), s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			pose.popPose();
		}
	}

	@Override
	protected float getShadowRadius(State s) {
		return 0F;
	}
}
