package dev.elsewhere.kingdom.armies.impostor;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.server.level.ServerLevel;

/**
 * Owns the bookkeeping of a rank's heads: a wave battle side (reserve ranks) or an R3 fold (the squad's virtual record). The rank itself only
 * draws, marches and breaks out; whoever made it decides where it goes and who the heads belong to. Server only.
 */
public interface RankController {
	/** Every 20 ticks for each of its ranks (placement, dice, links). */
	void slowTick(ServerLevel lv, RankEntity r);

	/**
	 * A figure became a real soldier. {@code committed}: its head was already taken from the reserve (a queued wave); false for a player's
	 * hit, where the controller moves the head from the reserve to the field itself.
	 */
	void brokeOut(ServerLevel lv, RankEntity r, Figure f, KingdomSoldier s, boolean committed);

	/** A queued figure could not break out (the caps are full): its head goes back to the reserve and the figure stays. */
	void breakFailed(ServerLevel lv, RankEntity r, Figure f);

	/** A figure died inside the rank (rank dice, a stray arrow, a blast). {@code committed}: it was queued to break out (its head already left the reserve). */
	void figureDied(ServerLevel lv, RankEntity r, Figure f, boolean committed);

	/** A figure was wounded inside the rank (its {@link Figure#health} already lowered). */
	default void figureWounded(ServerLevel lv, RankEntity r, Figure f) {}

	/** A player hit a folded rank: bring its soldiers back now. True when they are real again. */
	boolean unfold(ServerLevel lv, RankEntity r);

	/** The rank left the world (abstract at the edge of the loaded area, unloaded, emptied, killed). */
	void removed(ServerLevel lv, RankEntity r);
}
