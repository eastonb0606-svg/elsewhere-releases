package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A right-click spell item: spends Glimmer, casts, sets its use delay, takes 1 durability (ARSENAL §4a). */
public abstract class CasterItem extends Item {
	protected final int delayTicks;
	protected final boolean starter;

	protected CasterItem(Properties props, int delayTicks, boolean starter) {
		super(props);
		this.delayTicks = delayTicks;
		this.starter = starter;
	}

	public boolean isStarter() {
		return starter;
	}

	/** Raw Glimmer cost before spell_cost and Calling. */
	public abstract int rawCost(Player p);

	protected abstract void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit);

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		return castNow(sl, sp, stack, hand) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Runs one cast as if used (also used by the self-test). */
	public boolean castNow(ServerLevel sl, ServerPlayer sp, ItemStack stack, InteractionHand hand) {
		if (!Glimmer.trySpend(sp, rawCost(sp), starter)) return false;
		boolean moonlit = Glimmer.consumeMoonlit(sp);
		cast(sl, sp, stack, moonlit);
		sp.getCooldowns().addCooldown(stack, delayTicks);
		stack.hurtAndBreak(1, sp, hand);
		Spells.afterCast(sp, this);
		return true;
	}

	/**
	 * 3.0 hook: casts this spell for free at {@code scale} power (no Glimmer, cooldown, durability or ON_CAST), e.g. the
	 * Will-o'-Lantern's wisp recasting your last spell at 40%. Does nothing when {@code spell_hooks} is off.
	 */
	public void castFree(ServerLevel sl, ServerPlayer sp, ItemStack stack, float scale) {
		if (!dev.elsewhere.arsenal.Overhaul3.SPELL_HOOKS) return;
		Spells.withPowerScale(scale, () -> cast(sl, sp, stack, false));
	}
}
