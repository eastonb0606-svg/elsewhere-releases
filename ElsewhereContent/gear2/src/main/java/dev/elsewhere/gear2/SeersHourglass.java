package dev.elsewhere.gear2;

import dev.elsewhere.stub.gear2.TrinketStub;
import net.minecraft.world.entity.player.Player;

/**
 * gear-trinket1-a Seer's Hourglass API for the boss kit. The item is in {@code #elsewhere:foresight_gear} (the bosses'
 * {@code ForesightGear.sees} reads it from the hands/armor); {@code Gear2ForesightMixin} also makes it count from the
 * hotbar while it is one of your 2 active trinkets. Bosses that want the earlier warning call {@link #leadTicks}.
 */
public final class SeersHourglass {
	private SeersHourglass() {}

	/** True while the Hourglass is one of the player's (max 2) active trinkets: off hand or hotbar. */
	public static boolean active(Player p) {
		return TrinketStub.isActive(p, Gear2Items.SEERS_HOURGLASS);
	}

	/** Boss warnings come this many ticks earlier for this player (3 ticks = 0.15 s with an active Hourglass). */
	public static int leadTicks(Player p) {
		return active(p) ? Gear2Stats.HOURGLASS_LEAD_TICKS : 0;
	}
}
