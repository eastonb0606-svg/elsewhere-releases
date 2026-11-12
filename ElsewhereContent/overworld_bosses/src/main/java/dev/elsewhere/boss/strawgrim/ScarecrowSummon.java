package dev.elsewhere.boss.strawgrim;

import dev.elsewhere.overworld_bosses.Summons;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * Strawgrim's summon: on a farm field, under a full moon at night, build the scarecrow pattern (a fence post, two
 * hay bales on top, a carved pumpkin as the head, like a snow-golem pattern) and light the pumpkin with flint and
 * steel. The arena is the surrounding 24x24 (radius 12) of farmland.
 */
public final class ScarecrowSummon {
	public static final int MIN_FARMLAND = 16;

	private ScarecrowSummon() {}

	public static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.FLINT_AND_STEEL) || !(world instanceof ServerLevel level)) return InteractionResult.PASS;
			if (!dev.elsewhere.overworld_bosses.OwbConfig.overworldContent()) return InteractionResult.PASS; // overworldContent=false: plain vanilla scarecrow
			BlockPos head = hit.getBlockPos();
			if (!level.getBlockState(head).is(Blocks.CARVED_PUMPKIN)) return InteractionResult.PASS;
			BlockPos post = isPattern(level, head);
			if (post == null) return InteractionResult.PASS;
			String problem = conditions(level, post);
			if (problem != null) {
				player.sendOverlayMessage(Component.translatable(problem));
				return InteractionResult.FAIL;
			}
			stack.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			Summons.summonStrawgrim(level, post);
			return InteractionResult.SUCCESS;
		});
	}

	/** Returns the fence-post position if the pumpkin at head tops a valid scarecrow, else null. */
	public static BlockPos isPattern(ServerLevel level, BlockPos head) {
		if (!level.getBlockState(head.below(1)).is(Blocks.HAY_BLOCK)) return null;
		if (!level.getBlockState(head.below(2)).is(Blocks.HAY_BLOCK)) return null;
		if (!level.getBlockState(head.below(3)).is(BlockTags.FENCES)) return null;
		return head.below(3);
	}

	/** Null when the summon may happen, else a translation key explaining why not. */
	public static String conditions(ServerLevel level, BlockPos post) {
		if (!level.isDarkOutside()) return "message.elsewhere.strawgrim.not_night";
		MoonPhase moon = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, net.minecraft.world.phys.Vec3.atCenterOf(post), null);
		if (moon != MoonPhase.FULL_MOON) return "message.elsewhere.strawgrim.not_full_moon";
		if (countFarmland(level, post) < MIN_FARMLAND) return "message.elsewhere.strawgrim.no_farm";
		return null;
	}

	public static int countFarmland(ServerLevel level, BlockPos post) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(post.offset(-12, -3, -12), post.offset(12, 2, 12))) {
			if (level.getBlockState(p).is(Blocks.FARMLAND)) n++;
		}
		return n;
	}
}
