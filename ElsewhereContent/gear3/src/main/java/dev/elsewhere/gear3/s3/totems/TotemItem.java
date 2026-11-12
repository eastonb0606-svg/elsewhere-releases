package dev.elsewhere.gear3.s3.totems;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.summoner.GearStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A totem item (catalogue mechanic "totem"): use it on the ground to set the totem there, facing you. Costs the summon
 * Glimmer (10) through {@code MinionManager.summon} (slot cap, Herd focus, the summon chime) and 1 durability; 2 s
 * cooldown. At most {@link Totems#MAX_TOTEMS} totems per player: placing another crumbles your oldest. Sneak-use (on a
 * block or in the air) takes down your totems of this kind.
 */
public class TotemItem extends Item {
	public TotemItem(Item.Properties p) {
		super(p);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Player player = ctx.getPlayer();
		if (player == null) return InteractionResult.PASS;
		if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		ItemStack stack = ctx.getItemInHand();
		if (sp.isSecondaryUseActive()) return takeDown(sp, stack) > 0 ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		return place(sp, stack, ctx.getHand(), ctx.getClickedPos(), ctx.getClickedFace()) != null ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		return takeDown(sp, player.getItemInHand(hand)) > 0 ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}

	/**
	 * Sets a totem on the block {@code clicked} (on top of it, or in it when it is replaceable like grass). Needs a sturdy
	 * top face below and room for the totem. Returns the placed totem, or null (with an action-bar reason).
	 */
	public static TotemEntity place(ServerPlayer sp, ItemStack stack, InteractionHand hand, BlockPos clicked, Direction face) {
		EntityType<? extends TotemEntity> type = Totems.typeFor(stack.getItem());
		if (type == null) return null;
		if (!Totems.enabled) {
			sp.sendOverlayMessage(Component.translatable("message.elsewhere.totem.disabled"));
			return null;
		}
		if (sp.getCooldowns().isOnCooldown(stack)) return null;
		ServerLevel level = (ServerLevel) sp.level();
		BlockPos pos = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
		BlockState below = level.getBlockState(pos.below());
		Vec3 foot = Vec3.atBottomCenterOf(pos);
		AABB box = type.getDimensions().makeBoundingBox(foot);
		// noCollision ignores mobs (only boats/shulkers collide), so a totem already standing there is checked by hand (s-asm-gear3)
		if (!below.isFaceSturdy(level, pos.below(), Direction.UP) || !level.getBlockState(pos).canBeReplaced() || !level.noCollision(box)
			|| !level.getEntitiesOfClass(TotemEntity.class, box, e -> !e.isRemoved()).isEmpty()) {
			sp.sendOverlayMessage(Component.translatable("message.elsewhere.totem.no_room"));
			return null;
		}
		TotemEntity.pendingPos = foot;
		TotemEntity.pendingYaw = Mth.wrapDegrees(sp.getYRot() + 180F);
		MinionEntity m;
		try {
			m = MinionManager.summon(sp, type, GearStats.SUMMON_GLIMMER_COST);
		} finally {
			TotemEntity.pendingPos = null;
		}
		if (!(m instanceof TotemEntity totem)) return null; // no Glimmer (the summoner says why)
		enforceCap(sp, totem);
		stack.hurtAndBreak(1, sp, hand);
		sp.getCooldowns().addCooldown(stack, Totems.PLACE_COOLDOWN);
		level.playSound(null, foot.x, foot.y, foot.z, totem.materialSound(), SoundSource.PLAYERS, 1.0F, 0.9F);
		return totem;
	}

	/** GEAR.md "max 2": over the cap, the oldest totems (other than the new one) crumble. */
	static void enforceCap(ServerPlayer sp, TotemEntity keep) {
		List<TotemEntity> totems = new ArrayList<>();
		for (MinionEntity m : MinionManager.minions(sp)) if (m instanceof TotemEntity t && !t.isRemoved()) totems.add(t); // oldest first
		int over = totems.size() - Totems.MAX_TOTEMS;
		for (int i = 0; i < totems.size() && over > 0; i++) {
			TotemEntity t = totems.get(i);
			if (t == keep) continue;
			t.crumble((ServerLevel) t.level());
			over--;
		}
	}

	/** Sneak-use: takes down the player's totems of this item's kind. Returns how many. */
	public static int takeDown(ServerPlayer sp, ItemStack stack) {
		EntityType<? extends TotemEntity> type = Totems.typeFor(stack.getItem());
		if (type == null) return 0;
		int n = 0;
		for (MinionEntity m : MinionManager.minions(sp)) {
			if (m.getType() == type && m instanceof TotemEntity t && !t.isRemoved()) {
				t.crumble((ServerLevel) t.level());
				n++;
			}
		}
		if (n > 0) sp.sendOverlayMessage(Component.translatable("message.elsewhere.totem.taken_down", n));
		return n;
	}
}
