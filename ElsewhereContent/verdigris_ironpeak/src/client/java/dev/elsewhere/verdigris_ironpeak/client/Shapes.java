package dev.elsewhere.verdigris_ironpeak.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.ToIntFunction;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.goat.GoatModel;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.phantom.PhantomModel;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.GoatRenderState;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PhantomRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * Beta renderers: each mob and boss is drawn with a vanilla model shape (humanoid, golem, goat, cow, phantom,
 * guardian, drowned) scaled to its size, wearing its own texture (palette-mapped from the vanilla layout by
 * tools/gen_assets.py). The custom Blockbench-style rigs from the design come later; see NOTES.md.
 */
public final class Shapes {
	private Shapes() {}

	static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + name + ".png");
	}

	public static class Humanoid<T extends Mob> extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
		private final Identifier texture;
		private final float scale;

		public Humanoid(EntityRendererProvider.Context ctx, String texture, float scale) {
			super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F * scale);
			this.texture = tex(texture);
			this.scale = scale;
		}

		@Override
		public HumanoidRenderState createRenderState() {
			return new HumanoidRenderState();
		}

		@Override
		public Identifier getTextureLocation(HumanoidRenderState state) {
			return texture;
		}

		@Override
		protected void scale(HumanoidRenderState state, PoseStack poseStack) {
			poseStack.scale(scale, scale, scale);
		}
	}

	public static class Golem<T extends Mob> extends MobRenderer<T, IronGolemRenderState, IronGolemModel> {
		private final Identifier texture;
		private final float scale;
		private final ToIntFunction<T> attack;

		public Golem(EntityRendererProvider.Context ctx, String texture, float scale, ToIntFunction<T> attack) {
			super(ctx, new IronGolemModel(ctx.bakeLayer(ModelLayers.IRON_GOLEM)), 0.7F * scale);
			this.texture = tex(texture);
			this.scale = scale;
			this.attack = attack;
		}

		@Override
		public IronGolemRenderState createRenderState() {
			return new IronGolemRenderState();
		}

		@Override
		public void extractRenderState(T entity, IronGolemRenderState state, float partial) {
			super.extractRenderState(entity, state, partial);
			int a = attack.applyAsInt(entity);
			state.attackTicksRemaining = a > 0 ? a - partial : 0;
		}

		@Override
		public Identifier getTextureLocation(IronGolemRenderState state) {
			return texture;
		}

		@Override
		protected void scale(IronGolemRenderState state, PoseStack poseStack) {
			poseStack.scale(scale, scale, scale);
		}
	}

	public static class Goat<T extends Mob> extends MobRenderer<T, GoatRenderState, GoatModel> {
		private final Identifier texture;
		private final float scale;

		public Goat(EntityRendererProvider.Context ctx, String texture, float scale) {
			super(ctx, new GoatModel(ctx.bakeLayer(ModelLayers.GOAT)), 0.7F * scale);
			this.texture = tex(texture);
			this.scale = scale;
		}

		@Override
		public GoatRenderState createRenderState() {
			return new GoatRenderState();
		}

		@Override
		public Identifier getTextureLocation(GoatRenderState state) {
			return texture;
		}

		@Override
		protected void scale(GoatRenderState state, PoseStack poseStack) {
			poseStack.scale(scale, scale, scale);
		}
	}

	public static class Cow<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, CowModel> {
		private final Identifier texture;
		private final float scale;

		public Cow(EntityRendererProvider.Context ctx, String texture, float scale) {
			super(ctx, new CowModel(ctx.bakeLayer(ModelLayers.COLD_COW)), 0.7F * scale);
			this.texture = tex(texture);
			this.scale = scale;
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return texture;
		}

		@Override
		protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
			poseStack.scale(scale, scale, scale);
		}
	}

	public static class Flier<T extends Mob> extends MobRenderer<T, PhantomRenderState, PhantomModel> {
		private final Identifier texture;
		private final float scale;

		public Flier(EntityRendererProvider.Context ctx, String texture, float scale) {
			super(ctx, new PhantomModel(ctx.bakeLayer(ModelLayers.PHANTOM)), 0.75F * scale);
			this.texture = tex(texture);
			this.scale = scale;
		}

		@Override
		public PhantomRenderState createRenderState() {
			return new PhantomRenderState();
		}

		@Override
		public void extractRenderState(T entity, PhantomRenderState state, float partial) {
			super.extractRenderState(entity, state, partial);
			state.flapTime = entity.tickCount + partial;
			state.size = 0;
		}

		@Override
		public Identifier getTextureLocation(PhantomRenderState state) {
			return texture;
		}

		@Override
		protected void scale(PhantomRenderState state, PoseStack poseStack) {
			poseStack.scale(scale, scale, scale);
		}
	}

	public static class Brass extends GuardianRenderer {
		public Brass(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public Identifier getTextureLocation(GuardianRenderState state) {
			return tex("brass_guardian");
		}

		@Override
		protected void scale(GuardianRenderState state, PoseStack poseStack) {
			poseStack.scale(1.3F, 1.3F, 1.3F);
		}
	}

	public static class Clockwork extends DrownedRenderer {
		public Clockwork(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public Identifier getTextureLocation(ZombieRenderState state) {
			return tex("clockwork_drowned");
		}

		@Override
		protected void scale(ZombieRenderState state, PoseStack poseStack) {
			poseStack.scale(1.1F, 1.1F, 1.1F);
		}
	}
}
