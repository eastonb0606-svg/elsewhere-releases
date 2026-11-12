package dev.elsewhere.verdigris_ironpeak;

import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.verdigris_ironpeak.world.ProceduralStructure;
import dev.elsewhere.verdigris_sea.VerdigrisEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * Residents of structures: one Tidewright in every Engineer's Atoll, one Clan Quartermaster in every Clan Hold.
 * Every 2 seconds, for each player, the sites within 64 blocks that have no trader get one (so it also works in
 * structures generated before 2.0). A dead trader is replaced after 3 days (in memory: a restart re-arms).
 */
public final class StructureTraders {
	private StructureTraders() {}

	public static final long RESPAWN_TICKS = 72000L;
	private static final Map<String, Long> COOLDOWN = new HashMap<>();

	public record Site(String blueprint, BlockPos origin) {
		public EntityType<ElsewhereTrader> type() {
			return blueprint.equals("engineers_atoll") ? VerdigrisEntities.TIDEWRIGHT : IronpeakEntities.CLAN_QUARTERMASTER;
		}

		/** Where the trader stands: inside the hut / hold, next to the hearth. */
		public BlockPos spot() {
			return blueprint.equals("engineers_atoll") ? origin.offset(0, 1, 1) : origin.offset(2, 1, 2);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 7) return;
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.dimension().identifier().getNamespace().equals("elsewhere")) continue;
				for (ServerPlayer p : level.players()) ensure(level, p.blockPosition());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ElsewhereTrader t && t.level() instanceof ServerLevel level) {
				for (Site s : sites(level, t.blockPosition(), 40)) COOLDOWN.put(key(level, s), level.getGameTime() + RESPAWN_TICKS);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> COOLDOWN.clear());
	}

	static String key(ServerLevel level, Site s) {
		return level.dimension().identifier() + "@" + s.origin().asLong();
	}

	/** Test hook. */
	public static void clearCooldowns() {
		COOLDOWN.clear();
	}

	/** Atoll and hold sites whose structure start is known near {@code pos} (horizontal distance at most {@code range}). */
	public static List<Site> sites(ServerLevel level, BlockPos pos, int range) {
		List<Site> out = new ArrayList<>();
		if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return out; // never force chunk generation from the tick (it raced the generator in the combined smoke)
		for (StructureStart start : level.structureManager().startsForStructure(pos.getX() >> 4, pos.getZ() >> 4,
			st -> st instanceof ProceduralStructure ps && (ps.blueprint().equals("engineers_atoll") || ps.blueprint().equals("clan_hold")))) {
			if (!start.isValid()) continue;
			for (StructurePiece piece : start.getPieces()) {
				if (!(piece instanceof ProceduralStructure.Piece pp)) continue;
				BlockPos o = pp.origin();
				if (Math.abs(o.getX() - pos.getX()) <= range && Math.abs(o.getZ() - pos.getZ()) <= range) out.add(new Site(pp.blueprint(), o));
			}
		}
		return out;
	}

	/** Spawns the missing traders of the sites near {@code pos}; returns how many were spawned. */
	public static int ensure(ServerLevel level, BlockPos pos) {
		return ensure(level, pos, false);
	}

	/** {@code force}: also spawn where the chunk is loaded but not entity-ticking (self-tests without a player). */
	public static int ensure(ServerLevel level, BlockPos pos, boolean force) {
		int spawned = 0;
		for (Site s : sites(level, pos, 64)) {
			BlockPos spot = s.spot();
			if (force ? !level.hasChunkAt(spot) : !level.isPositionEntityTicking(spot)) continue;
			Long until = COOLDOWN.get(key(level, s));
			if (until != null && level.getGameTime() < until) continue;
			EntityType<ElsewhereTrader> type = s.type();
			AABB box = new AABB(spot).inflate(24, 12, 24);
			if (!level.getEntitiesOfClass(ElsewhereTrader.class, box, t -> t.getType() == type).isEmpty()) continue;
			ElsewhereTrader t = type.create(level, EntitySpawnReason.STRUCTURE);
			if (t == null) continue;
			t.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
			t.setWanderTarget(spot);
			level.addFreshEntity(t);
			spawned++;
		}
		return spawned;
	}
}
