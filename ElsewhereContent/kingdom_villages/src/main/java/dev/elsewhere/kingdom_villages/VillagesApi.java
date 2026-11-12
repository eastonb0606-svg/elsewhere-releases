package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Public hooks of the villages lane for the other lanes (war, court, kingdom lanes). Server side, overworld.
 * <ul>
 * <li>{@link #raidCountingHouse}: court or war calls it when Mirewick's Counting-House is raided (vault opened, Clerk killed,
 *     or the keep's outpost sacked): its Peat-Cutters strike for 3 in-game days.</li>
 * <li>{@link #ownerOf} / {@link #villageAt}: who holds the village at a position.</li>
 * <li>{@link #flip}: the war layer's {@code WarEvents.VILLAGE_CHANGED} already calls this (by reflection); other code may too.</li>
 * <li>{@link VillageEvents#OWNER_CHANGED}, {@link VillageEvents#STRIKE}, {@link VillageEvents#LIBERATED}: events.</li>
 * </ul>
 */
public final class VillagesApi {
	private VillagesApi() {}

	public static final int STRIKE_DAYS = 3;

	public static void raidCountingHouse(MinecraftServer server, Identifier faction) {
		VillageManager.strike(server.overworld(), faction.toString(), STRIKE_DAYS);
	}

	public static boolean isStriking(MinecraftServer server, Identifier faction) {
		return VillageManager.isStriking(server.overworld(), faction.toString());
	}

	public static @Nullable VillageRecord villageAt(ServerLevel level, BlockPos pos) {
		return VillageManager.villageAt(level, pos);
	}

	/** The faction occupying the village at {@code pos}, or null when none is (free, besieged, liberated, protected). */
	public static @Nullable Identifier ownerOf(ServerLevel level, BlockPos pos) {
		VillageRecord r = VillageManager.villageAt(level, pos);
		return r == null ? null : VillageManager.warOwner(r);
	}

	/** Villages under siege right now (for the squad-tactics framework: the "attack this village" goal). */
	public static java.util.List<VillageRecord> sieges(ServerLevel level) {
		java.util.List<VillageRecord> out = new java.util.ArrayList<>();
		for (VillageRecord r : VillageLedger.get(level).villages.values()) if (r.state == VillageRecord.State.SIEGE) out.add(r);
		return out;
	}

	/** Where a squad should stand around a village (FIELD-TACTICS 3.1/3.2): gates, pickets 40 blocks out on the four sides, high ground, obelisks. */
	public record Layout(BlockPos center, java.util.List<BlockPos> gates, java.util.List<BlockPos> pickets, java.util.List<BlockPos> highGround, java.util.List<BlockPos> obelisks) {}

	public static Layout layout(ServerLevel level, VillageRecord r) {
		java.util.List<BlockPos> gates = new java.util.ArrayList<>(VillageManager.waypoints(level, r));
		gates.sort((a, b) -> Double.compare(b.distSqr(r.center), a.distSqr(r.center)));
		if (gates.size() > 4) gates = new java.util.ArrayList<>(gates.subList(0, 4));
		int rad = Math.max(r.bounds.getXSpan(), r.bounds.getZSpan()) / 2 + 40;
		java.util.List<BlockPos> pickets = new java.util.ArrayList<>();
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) pickets.add(Plans.surface(level, r.center.relative(d, rad)));
		java.util.List<BlockPos> high = new java.util.ArrayList<>();
		if (r.towerTop != null) high.add(r.towerTop);
		java.util.List<BlockPos> obelisks = new java.util.ArrayList<>();
		for (var e : r.journal.long2ObjectEntrySet()) {
			if (e.getValue().written().is(net.minecraft.world.level.block.Blocks.GLAZED_TERRACOTTA.yellow()) && obelisks.size() < 8) obelisks.add(BlockPos.of(e.getLongKey()));
		}
		if (r.hint != null && !obelisks.contains(r.hint) && FactionVillages.of(r.owner).shape() == FactionVillages.Shape.AZMAR) obelisks.add(r.hint);
		high.addAll(obelisks);
		return new Layout(r.center, gates, pickets, high, obelisks);
	}

	/** A village changed hands (key = start-chunk id, as registered with the war layer). */
	public static void flip(ServerLevel level, String key, BlockPos pos, @Nullable Identifier newOwner, @Nullable Identifier oldOwner, String reason) {
		VillageManager.onVillageChanged(level, key, pos, newOwner, oldOwner, reason);
	}
}
