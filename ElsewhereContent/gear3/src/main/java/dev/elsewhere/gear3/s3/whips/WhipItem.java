package dev.elsewhere.gear3.s3.whips;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * gear3 whip (catalogue mechanic "whip"): Thornlash, Tidechain Lash, Sunscourge, Echo Chord. Its {@link WhipKind} comes
 * from its registry id. Hit = tag ({@link Gear3Whips#onHit}); right-click = crack ({@link Gear3Whips#crack}).
 */
public class WhipItem extends Item {
	/** Crack cooldown (ticks): the held model shows the cracked lash while it runs. */
	public static final int CRACK_COOLDOWN = 10;
	/** A whip's lash: the overhead whack, held 8 ticks instead of a sword's 6. */
	public static final SwingAnimation LASH = new SwingAnimation(SwingAnimationType.WHACK, 8);

	private @Nullable WhipKind kind;
	private boolean looked;

	public WhipItem(Properties props) {
		super(props.component(DataComponents.ATTACK_ANIMATION, LASH).component(DataComponents.INTERACT_ANIMATION, LASH));
	}

	public @Nullable WhipKind kind() {
		if (!looked) {
			looked = true;
			kind = WhipKind.byItem(BuiltInRegistries.ITEM.getKey(this).getPath());
		}
		return kind;
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (attacker instanceof ServerPlayer p && target.isAlive()) Gear3Whips.onHit(p, target, kind());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!Gear3Whips.ENABLED || kind() == null || player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (player instanceof ServerPlayer sp) Gear3Whips.crack(sp, stack, kind());
		return InteractionResult.SUCCESS;
	}
}
