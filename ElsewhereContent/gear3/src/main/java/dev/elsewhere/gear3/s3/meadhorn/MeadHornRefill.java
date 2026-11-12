package dev.elsewhere.gear3.s3.meadhorn;

import dev.elsewhere.arsenal.Overhaul3;
import dev.elsewhere.gear3.MeadHornItem;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Mead-Horn of the Hall: the honey-bottle refill (GEAR.md: "drink: heal 6 over 3 s + 3 Fury stacks, 30 s cooldown, refill
 * with a honey bottle"; swarm lane s-meadhorn).
 *
 * <p>A drunk horn is empty (custom_data flag {@value #EMPTY_KEY}; a horn without the flag is full, so every horn that
 * already exists stays full). An empty horn cannot be drunk. To refill it, use it with a honey bottle in the other hand,
 * or use the honey bottle with the horn in the other hand: the bottle is consumed, a glass bottle is returned (into the
 * bottle's hand when it was the last one, otherwise into the inventory or at your feet) and the horn is full again. A full
 * horn does not touch the bottle, so a honey bottle held next to a full horn is simply drunk as usual. In creative mode the
 * horn refills and the bottle is kept (no glass bottle appears), like every other creative-mode cost.
 *
 * <p>Event-driven: a Fabric use-item callback runs only when a player uses an item; nothing ticks and nothing scans. The
 * client only answers "will this be handled" (to swing and not start drinking the honey); the server does the work.
 *
 * <p>Kill switch: key {@value #KILL_KEY} in {@code config/elsewhere-overhaul3.json} (or
 * {@code -Delsewhere.overhaul3.mead_horn_refill=false}). Off, the horn is the old reusable horn (30 s cooldown, never
 * empties) and honey bottles are never touched by it.
 */
public final class MeadHornRefill {
	public static final String KILL_KEY = "mead_horn_refill";
	/** Kill switch, read once (the self-test flips it to check the off state). */
	public static boolean ENABLED = Overhaul3.on(KILL_KEY);
	/** custom_data key: present = the horn is empty. */
	public static final String EMPTY_KEY = "elsewhere_mead_empty";
	public static final String LANG_REFILLED = "item.elsewhere.hrafnholt_mead_horn.refilled";
	public static final String LANG_EMPTY = "item.elsewhere.hrafnholt_mead_horn.empty";

	private static boolean registered;

	private MeadHornRefill() {}

	public static void init() {
		if (registered) return;
		registered = true;
		UseItemCallback.EVENT.register((player, level, hand) -> onUse(player, level, hand));
	}

	public static boolean isHorn(ItemStack s) {
		return s.getItem() instanceof MeadHornItem;
	}

	/** True for a horn that was drunk and not refilled. Always false with the kill switch off. */
	public static boolean isEmpty(ItemStack horn) {
		if (!ENABLED || !isHorn(horn)) return false;
		CustomData cd = horn.get(DataComponents.CUSTOM_DATA);
		return cd != null && cd.copyTag().contains(EMPTY_KEY);
	}

	public static void setEmpty(ItemStack horn, boolean empty) {
		if (empty) CustomData.update(DataComponents.CUSTOM_DATA, horn, tag -> tag.putBoolean(EMPTY_KEY, true));
		else if (horn.get(DataComponents.CUSTOM_DATA) != null) CustomData.update(DataComponents.CUSTOM_DATA, horn, tag -> tag.remove(EMPTY_KEY));
	}

	static InteractionHand other(InteractionHand h) {
		return h == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/** Can the horn in {@code hornHand} be refilled right now (empty horn there, honey bottle in the other hand)? */
	public static boolean canRefill(Player p, InteractionHand hornHand) {
		return ENABLED && isEmpty(p.getItemInHand(hornHand)) && p.getItemInHand(other(hornHand)).is(Items.HONEY_BOTTLE);
	}

	/**
	 * Refill the horn in {@code hornHand} from the honey bottle in the other hand (server side). Returns whether it did.
	 * Survival: one bottle is consumed and a glass bottle returned; creative: the bottle is kept.
	 */
	public static boolean tryRefill(Player p, InteractionHand hornHand) {
		if (!canRefill(p, hornHand)) return false;
		InteractionHand bottleHand = other(hornHand);
		ItemStack horn = p.getItemInHand(hornHand);
		ItemStack bottle = p.getItemInHand(bottleHand);
		setEmpty(horn, false);
		if (!p.hasInfiniteMaterials()) {
			bottle.shrink(1);
			ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
			if (bottle.isEmpty()) p.setItemInHand(bottleHand, glass);
			else if (!p.getInventory().add(glass) && p.level() instanceof net.minecraft.server.level.ServerLevel sl) p.spawnAtLocation(sl, glass);
		}
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
		say(p, LANG_REFILLED);
		return true;
	}

	/** Action-bar line (server side only). */
	public static void say(Player p, String key) {
		if (!p.level().isClientSide()) p.sendOverlayMessage(Component.translatable(key));
	}

	static InteractionResult onUse(Player p, Level level, InteractionHand hand) {
		if (!ENABLED || p.isSpectator()) return InteractionResult.PASS;
		ItemStack held = p.getItemInHand(hand);
		InteractionHand hornHand;
		if (isHorn(held)) hornHand = hand; // the horn is used: refill it from the other hand
		else if (held.is(Items.HONEY_BOTTLE) && isHorn(p.getItemInHand(other(hand)))) hornHand = other(hand); // the bottle is used first (main hand): refill the horn in the other hand instead of drinking
		else return InteractionResult.PASS;
		if (!canRefill(p, hornHand)) return InteractionResult.PASS; // full horn or no bottle: normal use (the horn itself says "empty")
		if (!level.isClientSide()) tryRefill(p, hornHand);
		return InteractionResult.SUCCESS;
	}
}
