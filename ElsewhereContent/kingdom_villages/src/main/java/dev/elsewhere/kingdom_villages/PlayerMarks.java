package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Player-presence tracking for the safety layer: a block placed or broken by a player marks its chunk; a completed trade
 * marks the villager's chunk. Any village whose area touches a marked chunk becomes PROTECTED at once and is never changed
 * again (this is what keeps trading halls and player builds safe).
 */
public final class PlayerMarks {
	private PlayerMarks() {}

	public static void mark(ServerLevel level, BlockPos pos) {
		VillageLedger l = VillageLedger.get(level);
		if (l.playerChunks.add(ChunkPos.pack(pos))) l.dirty();
		VillageManager.protectAt(level, pos, "player block");
	}

	public static void trade(ServerLevel level, BlockPos pos) {
		VillageLedger l = VillageLedger.get(level);
		if (l.tradeChunks.add(ChunkPos.pack(pos))) l.dirty();
		VillageManager.protectAt(level, pos, "player trade");
	}
}
