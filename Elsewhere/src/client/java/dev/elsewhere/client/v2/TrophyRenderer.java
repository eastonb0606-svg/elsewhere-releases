package dev.elsewhere.client.v2;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.trophy.BossTrophyBlockEntity;
import dev.elsewhere.registry.ModBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Boss trophy statue: the boss's emblem (its spawn egg, else the trophy) slowly turning and bobbing above the pedestal. */
public class TrophyRenderer implements BlockEntityRenderer<BossTrophyBlockEntity, TrophyRenderer.State> {
	private final ItemModelResolver items;

	public TrophyRenderer(final BlockEntityRendererProvider.Context ctx) {
		this.items = ctx.itemModelResolver();
	}

	public static class State extends BlockEntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float time;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	static ItemStack emblem(final String boss) {
		if (boss != null && !boss.isEmpty()) {
			Identifier egg = Elsewhere.id(boss + "_spawn_egg");
			if (BuiltInRegistries.ITEM.containsKey(egg)) {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(egg));
			}
		}
		return new ItemStack(ModBlocks.BOSS_TROPHY.asItem());
	}

	@Override
	public void extractRenderState(final BossTrophyBlockEntity be, final State state, final float partialTicks, final Vec3 camera,
		final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
		var level = be.getLevel();
		state.time = level == null ? 0.0F : (level.getGameTime() % 24000L) + partialTicks;
		this.items.updateForTopItem(state.item, emblem(be.boss()), ItemDisplayContext.FIXED, level, null, (int)be.getBlockPos().asLong());
	}

	@Override
	public void submit(final State state, final PoseStack pose, final SubmitNodeCollector out, final CameraRenderState camera) {
		if (state.item.isEmpty()) {
			return;
		}
		pose.pushPose();
		float bob = (float)Math.sin(state.time * 0.08F) * 0.06F;
		pose.translate(0.5F, 0.95F + bob, 0.5F);
		pose.rotateDegrees(Axis.YP, (state.time * 1.5F) % 360.0F);
		pose.scale(0.75F, 0.75F, 0.75F);
		state.item.submit(pose, out, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}
}
