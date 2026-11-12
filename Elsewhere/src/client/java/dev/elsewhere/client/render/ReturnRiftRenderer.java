package dev.elsewhere.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.riftruin.ReturnRiftBlockEntity;
import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The tall beam in the dimension's colour above every Return Rift, visible from far away. */
public class ReturnRiftRenderer implements BlockEntityRenderer<ReturnRiftBlockEntity, ReturnRiftRenderer.State> {
	private static final Identifier BEAM = Elsewhere.id("textures/entity/return_rift_beam.png");

	public static class State extends BlockEntityRenderState {
		public float anim;
		public int color;
		public boolean draw;
		public float scale = 1.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final ReturnRiftBlockEntity be, final State state, final float partialTicks, final Vec3 camera,
		final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
		var level = be.getLevel();
		state.anim = level == null ? 0.0F : Math.floorMod(level.getGameTime(), 40) + partialTicks;
		state.color = 0xFF000000 | (level == null ? 0xB89CFF : ElsewhereDimensions.colorOf(level.dimension()));
		state.draw = level != null && !level.getBlockState(be.getBlockPos().below()).is(ModBlocks.RETURN_RIFT);
		float dist = (float)camera.subtract(Vec3.atCenterOf(be.getBlockPos())).horizontalDistance();
		state.scale = Math.max(1.0F, dist / 96.0F);
	}

	@Override
	public void submit(final State state, final PoseStack pose, final SubmitNodeCollector out, final CameraRenderState camera) {
		if (state.draw) {
			BeaconRenderer.submitBeaconBeam(pose, out, BEAM, 1.0F, state.anim, 2, 320, state.color, 0.22F * state.scale, 0.3F * state.scale);
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return Math.max(160, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
	}

	@Override
	public boolean shouldRender(final ReturnRiftBlockEntity be, final Vec3 camera) {
		return Vec3.atCenterOf(be.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(camera.multiply(1.0, 0.0, 1.0), this.getViewDistance());
	}
}
