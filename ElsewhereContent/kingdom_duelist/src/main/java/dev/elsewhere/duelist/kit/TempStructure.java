package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.HumanBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * X6 {@code TempStructure}: a multi-block temporary wall built on the K10 ledger. Air only (never replaces a block),
 * never encloses a player, vanishes with the ledger (lifetime, boss death, unload). One per boss per cooldown.
 */
public final class TempStructure {
	private TempStructure() {}

	/**
	 * A wall {@code width} wide and {@code height} high, centred on {@code base} (its bottom row), running perpendicular to
	 * {@code facing}. Returns how many blocks were placed (0 when still on cooldown). Respects the per-boss cap of
	 * {@link Kit#BLOCK_MAX_ACTIVE} unless {@code cap} is larger (a wall needs more than a bridge; pass the faction's own cap).
	 */
	public static int wall(ServerLevel level, HumanBoss boss, BlockPos base, Direction facing, int width, int height, int cap, int lifetime, int cooldown) {
		if (!boss.ready("tempstruct")) return 0;
		boss.setCooldown("tempstruct", cooldown);
		Direction side = facing.getClockWise();
		int placed = 0;
		for (int h = 0; h < height; h++) {
			for (int w = -width / 2; w <= width / 2; w++) {
				if (DuelBlocks.count(level, boss.getUUID()) >= cap) return placed;
				if (DuelBlocks.place(level, base.relative(side, w).above(h), boss.getUUID(), boss, lifetime)) placed++;
			}
		}
		if (placed > 0) boss.bump("tempstruct");
		return placed;
	}
}
