package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;

/**
 * The ONLY code path that changes world blocks for the kingdom (sieges, rebuild, outposts, roads, hamlets). Every write is
 * checked against the safety rules and journaled (original + written state), so {@code /aldermark restore} can put the
 * original layout back.
 *
 * <p>Rules: the site is not PROTECTED; the chunk has no player mark; a village write lands inside the village's own
 * generated piece boxes (or, for new additions, in AIR at most {@value #UP} blocks above a piece in its footprint); the
 * current block is exactly what the kingdom last wrote there (or, first time, not a block entity, job site or
 * player-indicative block). No neighbour/shape updates are sent, so nothing cascades outside the written block.
 */
public final class BlockWriter {
	private BlockWriter() {}

	public static final int UP = 8;
	/** Clients only, no neighbour shape updates, no drops: a write changes exactly one block. */
	public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	public static long writes, refused;

	/** Blocks no natural village contains; finding one marks the village as player-built (never touched). */
	public static final Set<Block> PLAYER_BLOCKS = Set.of(Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.OBSERVER, Blocks.PISTON,
		Blocks.STICKY_PISTON, Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.ENDER_CHEST, Blocks.ENCHANTING_TABLE, Blocks.BEACON,
		Blocks.CRAFTER, Blocks.NETHERITE_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.RESPAWN_ANCHOR,
		Blocks.LODESTONE, Blocks.TARGET, Blocks.DAYLIGHT_DETECTOR, Blocks.TRAPPED_CHEST, Blocks.TNT, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK);

	public static boolean isPlayerBlock(BlockState s) {
		return PLAYER_BLOCKS.contains(s.getBlock()) || s.is(BlockTags.SHULKER_BOXES) || s.is(BlockTags.RAILS);
	}

	public static boolean isKingdomBlock(BlockState s) {
		return s.is(VillagesContent.SIEGE_FIRE) || s.is(VillagesContent.ASH) || s.is(VillagesContent.CHARRED_PLANKS);
	}

	/** Can the kingdom write {@code target} at {@code pos} for site {@code r}? */
	public static boolean allowed(ServerLevel level, VillageRecord r, BlockPos pos, BlockState target) {
		if (r.state == VillageRecord.State.PROTECTED || r.state == VillageRecord.State.RESTORED) return false;
		if (!level.isLoaded(pos)) return false;
		VillageLedger ledger = VillageLedger.get(level);
		if (ledger.playerChunks.contains(ChunkPos.pack(pos))) return false;
		BlockState cur = level.getBlockState(pos);
		VillageRecord.Change ch = r.journal.get(pos.asLong());
		if (ch != null) {
			boolean ours = cur == ch.written() || cur.getBlock() == ch.written().getBlock() || ch.written().is(VillagesContent.SIEGE_FIRE) && (cur.isAir() || cur.is(VillagesContent.ASH));
			if (!ours) return false;
		} else {
			if (cur.hasBlockEntity() && !cur.is(Blocks.BELL)) return false;
			if (isPlayerBlock(cur)) return false;
			if (cur.is(BlockTags.BEDS) || cur.is(BlockTags.DOORS)) return false;
			// a pier piling (a log) may replace one water block inside the waterfront box; nothing else ever touches fluid
			if (!cur.getFluidState().isEmpty() && !(target.is(BlockTags.LOGS) && cur.is(Blocks.WATER) && r.hasPier(pos))) return false;
		}
		if (r.kind == VillageRecord.Kind.VILLAGE) {
			if (!r.insidePieces(pos)) {
				// additions above the village: air only, in a piece's footprint, at most UP blocks above it
				if (!(cur.isAir() || ch != null) || !r.insideFootprint(pos, UP)) return false;
			}
		} else if (r.kind != VillageRecord.Kind.ROAD) {
			if (!r.insidePieces(pos)) return false;
			if (ch == null && !(cur.isAir() || cur.canBeReplaced() || natural(cur))) return false;
		}
		return true;
	}

	/** Natural ground the outposts / hamlets / roads may build on. */
	public static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW) || s.is(Blocks.STONE)
			|| s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.DIRT_PATH);
	}

	/** Writes {@code target} if allowed; returns true when written. */
	public static boolean write(ServerLevel level, VillageRecord r, BlockPos pos, BlockState target) {
		if (!allowed(level, r, pos, target)) {
			refused++;
			return false;
		}
		BlockState cur = level.getBlockState(pos);
		if (cur == target) return true;
		long key = pos.asLong();
		VillageRecord.Change ch = r.journal.get(key);
		BlockState original = ch != null ? ch.original() : cur;
		if (!level.setBlock(pos, target, FLAGS)) {
			refused++;
			return false;
		}
		r.journal.put(key, new VillageRecord.Change(original, target));
		writes++;
		VillageLedger.get(level).dirty();
		return true;
	}

	/**
	 * Puts every journaled block back to its original state, but only where the block is still what the kingdom wrote
	 * (or kingdom fire/ash/charred planks). Anything a player changed since is left alone. Returns {restored, skipped}.
	 */
	public static int[] restore(ServerLevel level, VillageRecord r) {
		int ok = 0, skipped = 0;
		var it = r.journal.long2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			BlockPos pos = BlockPos.of(e.getLongKey());
			LevelChunk chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // loads the chunk if needed (command use)
			if (chunk == null) {
				skipped++;
				continue;
			}
			BlockState cur = level.getBlockState(pos);
			VillageRecord.Change ch = e.getValue();
			if (cur == ch.original()) {
				it.remove();
				ok++;
				continue;
			}
			boolean ours = cur == ch.written() || cur.getBlock() == ch.written().getBlock() || !cur.getFluidState().isEmpty() && !cur.getFluidState().isSource() || isKingdomBlock(cur) || cur.is(VillagesContent.ROYAL_COFFER)
				|| ch.written().is(VillagesContent.SIEGE_FIRE) && cur.isAir()
				|| ch.written().is(Blocks.DIRT) && (cur.is(Blocks.GRASS_BLOCK) || cur.is(Blocks.DIRT) || cur.is(Blocks.COARSE_DIRT) || cur.is(Blocks.PODZOL) || cur.is(Blocks.MYCELIUM)) // grass spread onto the dirt we left where farmland was
				|| cur.getBlock() == ch.written().getBlock() && cur.getBlock() instanceof net.minecraft.world.level.block.CropBlock; // villagers farm the rebuilt fields
			if (!ours) {
				skipped++;
				it.remove();
				continue;
			}
			BlockState orig = ch.original();
			// farmland must stay farmland: a restored field with no water nearby would dry out to dirt on the next random tick
			if (orig.is(Blocks.FARMLAND) && orig.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE)) orig = orig.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE, 7);
			level.setBlock(pos, orig, FLAGS);
			it.remove();
			ok++;
		}
		r.plan.clear();
		r.planIndex = 0;
		r.planKind = 0;
		VillageLedger.get(level).dirty();
		return new int[] {ok, skipped};
	}

	/** Puts out every siege fire the kingdom lit in this site (the block under it stays as it is). */
	public static int extinguish(ServerLevel level, VillageRecord r) {
		int n = 0;
		for (var e : r.journal.long2ObjectEntrySet()) {
			if (!e.getValue().written().is(VillagesContent.SIEGE_FIRE)) continue;
			BlockPos pos = BlockPos.of(e.getLongKey());
			if (!level.isLoaded(pos)) continue;
			if (level.getBlockState(pos).is(VillagesContent.SIEGE_FIRE)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
				e.setValue(new VillageRecord.Change(e.getValue().original(), Blocks.AIR.defaultBlockState()));
				n++;
			}
		}
		return n;
	}
}
