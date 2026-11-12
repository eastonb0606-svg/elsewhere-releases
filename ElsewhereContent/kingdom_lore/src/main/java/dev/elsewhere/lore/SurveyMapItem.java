package dev.elsewhere.lore;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The folded Royal Survey Map (3 fragments joined). Using it in the Overworld unfolds it, explorer-map style, into a
 * filled map centred on the nearest Castle Aldermark, with a red X on it.
 */
public class SurveyMapItem extends Item {
	private final net.minecraft.resources.Identifier faction;

	public SurveyMapItem(Properties p) {
		this(p, LoreFactions.ALDERMARK);
	}

	public SurveyMapItem(Properties p, net.minecraft.resources.Identifier faction) {
		super(p);
		this.faction = faction;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		ItemStack held = player.getItemInHand(hand);
		if (sl.dimension() != Level.OVERWORLD) {
			sp.sendSystemMessage(Component.translatable("message.elsewhere.lore_map_wrong_dimension"), true);
			return InteractionResult.FAIL;
		}
		ItemStack map = LoreMaps.makeMap(sl, player.blockPosition(), faction);
		if (map.isEmpty()) {
			sp.sendSystemMessage(Component.translatable("message.elsewhere.lore_map_no_castle"), true);
			return InteractionResult.FAIL;
		}
		held.shrink(1);
		if (held.isEmpty()) player.setItemInHand(hand, map);
		else if (!player.getInventory().add(map)) player.spawnAtLocation(sl, map);
		sp.sendSystemMessage(Component.translatable("message.elsewhere.lore_map_resolved"), true);
		LoreAdvancements.award(sp, "the_lines_resolve");
		return InteractionResult.SUCCESS;
	}
}
