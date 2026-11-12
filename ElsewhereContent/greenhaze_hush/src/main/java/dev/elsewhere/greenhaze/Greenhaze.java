package dev.elsewhere.greenhaze;

import dev.elsewhere.greenhaze_hush.Reg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** The Greenhaze ({@code elsewhere:greenhaze}): the lush early dimension made for Green Leaf. */
public final class Greenhaze {
	private Greenhaze() {}

	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Reg.id("greenhaze"));
	public static final ResourceKey<Biome> KUSH_CANOPY = biome("kush_canopy"), SATIVA_HIGHLANDS = biome("sativa_highlands"),
		INDICA_HOLLOWS = biome("indica_hollows"), HAZEBOTTOM_SWAMP = biome("hazebottom_swamp"), MUNCHIE_GROVES = biome("munchie_groves");

	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, Reg.id(name));
	}

	/** DIMENSIONS.md 7: bottle water at a Lucid Spring (water within 4 blocks of a Lucid Lily) to get Lucid Water. */
	static net.minecraft.world.InteractionResult bottleLucidWater(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
																	net.minecraft.world.InteractionHand hand) {
		var stack = player.getItemInHand(hand);
		if (level.isClientSide() || !stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) return net.minecraft.world.InteractionResult.PASS;
		var eye = player.getEyePosition();
		var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getLookAngle().scale(5)),
			net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return net.minecraft.world.InteractionResult.PASS;
		var pos = hit.getBlockPos();
		if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return net.minecraft.world.InteractionResult.PASS;
		boolean lily = false;
		for (var q : net.minecraft.core.BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 2, 4))) {
			if (level.getBlockState(q).is(GreenhazeBlocks.LUCID_LILY)) { lily = true; break; }
		}
		if (!lily) return net.minecraft.world.InteractionResult.PASS;
		stack.shrink(1);
		player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(GreenhazeItems.LUCID_WATER), net.minecraft.util.Prediction.SERVER_ONLY);
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1.2F);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	public static void init() {
		Reg.currentTab = Reg.GREENHAZE_TAB;
		GreenhazeBlocks.init();
		GreenhazeItems.init();
		GreenhazeEntities.init();
		Haze.init();
		GreenhazeStructures.init();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(GreenhazeItems::frondCamo);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(Greenhaze::bottleLucidWater);
	}
}
