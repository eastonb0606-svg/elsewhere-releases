package dev.elsewhere.kingdom_villages;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One village (or outpost / road / hamlet "site") the kingdom knows about. Holds the generated piece boxes (the ONLY place
 * village blocks may change), the state machine, the block journal (original + last written state for every block the
 * kingdom ever changed, used by {@code /aldermark restore}), and the pending block plan.
 */
public final class VillageRecord {
	public enum State { FREE, PROTECTED, SIEGE, OCCUPIED, FORTIFYING, FORTIFIED, LIBERATED, RESTORED }
	public enum Kind { VILLAGE, OUTPOST, ROAD, HAMLET }

	public static final int HOUSE = 0, STREET = 1, CENTER = 2, OTHER = 3;

	public record Piece(BoundingBox box, int type) {}

	/** Journal entry: what was there first, and what we last wrote. */
	public record Change(BlockState original, BlockState written) {}

	public record Op(long pos, BlockState state) {}

	public long id;
	public Kind kind = Kind.VILLAGE;
	public BlockPos center = BlockPos.ZERO;
	public BoundingBox bounds = new BoundingBox(BlockPos.ZERO);
	public final List<Piece> pieces = new ArrayList<>();
	public State state = State.FREE;
	public String reason = "";
	public long since, nextEval, siegeEnd;
	public @Nullable BlockPos castle;
	public double castleDist = -1;
	public int forceSpawned, forceDead;
	public boolean forceSpawnedOnce;
	public final List<UUID> squads = new ArrayList<>();
	public final List<UUID> serfs = new ArrayList<>();
	public @Nullable UUID siegePlayer, taxSquad, archerSquad;
	public @Nullable BlockPos coffer, towerTop, taxPost;
	public long roadTarget; // packed BlockPos of the road's destination (ROAD kind)
	public int roadStep, roadLength;
	public long builtAt;
	/** Fractional block-op carry for fast builders (Thralls); not saved. */
	public transient double opCarry;
	/** The border hint (tower-flag, raven post, obelisk or lantern post) standing at the village or outpost, or null. */
	public @Nullable BlockPos hint;
	/** Faction (id string) that occupies or besieges this village / owns this site. Existing saves read as Aldermark. */
	public String owner = "elsewhere:aldermark";
	/** Previous owner (flip-back target on a revolt); empty = none. */
	public String prevOwner = "";
	/** Game time when {@link #owner} took the village (0 = unknown). Drives wall growth and integration. */
	public long heldSince;
	/** The war layer locks a freshly flipped village for 3 days. */
	public long lockUntil;
	/** Border-wall growth steps already built (Azmar). */
	public int wallSteps;
	/** Unrest marker (Hrafnholt) currently shown. */
	public boolean unrestShown;
	/** A cosmetic owner swap is queued ({@code planKind == 3}). */
	public boolean swapQueued;

	public final Long2ObjectLinkedOpenHashMap<Change> journal = new Long2ObjectLinkedOpenHashMap<>();
	public final List<Op> plan = new ArrayList<>();
	public int planIndex;
	/** 0 none, 1 burn, 2 fortify, 3 cosmetic owner swap, 4 wall growth */
	public int planKind;

	public boolean isOccupiedLike() {
		return state == State.OCCUPIED || state == State.FORTIFYING || state == State.FORTIFIED;
	}

	public boolean insidePieces(BlockPos p) {
		for (Piece pc : pieces) if (pc.box.isInside(p)) return true;
		return false;
	}

	public boolean hasPier(BlockPos p) {
		for (Piece pc : pieces) if (pc.type == RivalPlans.PIER && pc.box.isInside(p)) return true;
		return false;
	}

	/** Inside a piece box, or (air only, enforced by the writer) at most {@code up} blocks above a piece in its footprint. */
	public boolean insideFootprint(BlockPos p, int up) {
		for (Piece pc : pieces) {
			BoundingBox b = pc.box;
			if (p.getX() >= b.minX() && p.getX() <= b.maxX() && p.getZ() >= b.minZ() && p.getZ() <= b.maxZ() && p.getY() >= b.minY() && p.getY() <= b.maxY() + up) return true;
		}
		return false;
	}

	// ---- NBT --------------------------------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putLong("id", id);
		t.putString("kind", kind.name());
		t.store("center", BlockPos.CODEC, center);
		t.store("bounds", BoundingBox.CODEC, bounds);
		ListTag pl = new ListTag();
		for (Piece p : pieces) {
			CompoundTag c = new CompoundTag();
			c.store("box", BoundingBox.CODEC, p.box);
			c.putInt("type", p.type);
			pl.add(c);
		}
		t.put("pieces", pl);
		t.putString("state", state.name());
		t.putString("reason", reason);
		t.putLong("since", since);
		t.putLong("nextEval", nextEval);
		t.putLong("siegeEnd", siegeEnd);
		if (castle != null) t.store("castle", BlockPos.CODEC, castle);
		t.putDouble("castleDist", castleDist);
		t.putInt("forceSpawned", forceSpawned);
		t.putInt("forceDead", forceDead);
		t.putBoolean("forceSpawnedOnce", forceSpawnedOnce);
		t.store("squads", UUIDUtil.CODEC.listOf(), squads);
		t.store("serfs", UUIDUtil.CODEC.listOf(), serfs);
		if (siegePlayer != null) t.store("siegePlayer", UUIDUtil.CODEC, siegePlayer);
		if (taxSquad != null) t.store("taxSquad", UUIDUtil.CODEC, taxSquad);
		if (archerSquad != null) t.store("archerSquad", UUIDUtil.CODEC, archerSquad);
		if (coffer != null) t.store("coffer", BlockPos.CODEC, coffer);
		if (towerTop != null) t.store("towerTop", BlockPos.CODEC, towerTop);
		if (taxPost != null) t.store("taxPost", BlockPos.CODEC, taxPost);
		t.putLong("roadTarget", roadTarget);
		t.putInt("roadStep", roadStep);
		t.putInt("roadLength", roadLength);
		t.putLong("builtAt", builtAt);
		if (hint != null) t.store("hint", BlockPos.CODEC, hint);
		t.putString("owner", owner);
		t.putString("prevOwner", prevOwner);
		t.putLong("heldSince", heldSince);
		t.putLong("lockUntil", lockUntil);
		t.putInt("wallSteps", wallSteps);
		t.putBoolean("unrestShown", unrestShown);
		t.putBoolean("swapQueued", swapQueued);
		// palette-compressed journal and plan
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> idx = new HashMap<>();
		long[] jp = new long[journal.size()];
		int[] jo = new int[journal.size()], jw = new int[journal.size()];
		int i = 0;
		for (var e : journal.long2ObjectEntrySet()) {
			jp[i] = e.getLongKey();
			jo[i] = pal(palette, idx, e.getValue().original);
			jw[i] = pal(palette, idx, e.getValue().written);
			i++;
		}
		long[] pp = new long[plan.size()];
		int[] ps = new int[plan.size()];
		for (int k = 0; k < plan.size(); k++) {
			pp[k] = plan.get(k).pos;
			ps[k] = pal(palette, idx, plan.get(k).state);
		}
		t.store("palette", BlockState.CODEC.listOf(), palette);
		t.putLongArray("jpos", jp);
		t.putIntArray("jorig", jo);
		t.putIntArray("jwritten", jw);
		t.putLongArray("ppos", pp);
		t.putIntArray("pstate", ps);
		t.putInt("planIndex", planIndex);
		t.putInt("planKind", planKind);
		return t;
	}

	private static int pal(List<BlockState> palette, Map<BlockState, Integer> idx, BlockState s) {
		Integer i = idx.get(s);
		if (i != null) return i;
		palette.add(s);
		idx.put(s, palette.size() - 1);
		return palette.size() - 1;
	}

	public static VillageRecord load(CompoundTag t) {
		VillageRecord r = new VillageRecord();
		r.id = t.getLongOr("id", 0L);
		try {
			r.kind = Kind.valueOf(t.getStringOr("kind", "VILLAGE"));
		} catch (IllegalArgumentException e) {
			r.kind = Kind.VILLAGE;
		}
		r.center = t.read("center", BlockPos.CODEC).orElse(BlockPos.ZERO);
		r.bounds = t.read("bounds", BoundingBox.CODEC).orElse(new BoundingBox(r.center));
		for (Tag tag : t.getListOrEmpty("pieces")) {
			if (tag instanceof CompoundTag c) c.read("box", BoundingBox.CODEC).ifPresent(b -> r.pieces.add(new Piece(b, c.getIntOr("type", OTHER))));
		}
		try {
			r.state = State.valueOf(t.getStringOr("state", "FREE"));
		} catch (IllegalArgumentException e) {
			r.state = State.FREE;
		}
		r.reason = t.getStringOr("reason", "");
		r.since = t.getLongOr("since", 0L);
		r.nextEval = t.getLongOr("nextEval", 0L);
		r.siegeEnd = t.getLongOr("siegeEnd", 0L);
		r.castle = t.read("castle", BlockPos.CODEC).orElse(null);
		r.castleDist = t.getDoubleOr("castleDist", -1);
		r.forceSpawned = t.getIntOr("forceSpawned", 0);
		r.forceDead = t.getIntOr("forceDead", 0);
		r.forceSpawnedOnce = t.getBooleanOr("forceSpawnedOnce", false);
		r.squads.addAll(t.read("squads", UUIDUtil.CODEC.listOf()).orElse(List.of()));
		r.serfs.addAll(t.read("serfs", UUIDUtil.CODEC.listOf()).orElse(List.of()));
		r.siegePlayer = t.read("siegePlayer", UUIDUtil.CODEC).orElse(null);
		r.taxSquad = t.read("taxSquad", UUIDUtil.CODEC).orElse(null);
		r.archerSquad = t.read("archerSquad", UUIDUtil.CODEC).orElse(null);
		r.coffer = t.read("coffer", BlockPos.CODEC).orElse(null);
		r.towerTop = t.read("towerTop", BlockPos.CODEC).orElse(null);
		r.taxPost = t.read("taxPost", BlockPos.CODEC).orElse(null);
		r.roadTarget = t.getLongOr("roadTarget", 0L);
		r.roadStep = t.getIntOr("roadStep", 0);
		r.roadLength = t.getIntOr("roadLength", 0);
		r.builtAt = t.getLongOr("builtAt", 0L);
		r.hint = t.read("hint", BlockPos.CODEC).orElse(null);
		r.owner = t.getStringOr("owner", "elsewhere:aldermark");
		r.prevOwner = t.getStringOr("prevOwner", "");
		r.heldSince = t.getLongOr("heldSince", 0L);
		r.lockUntil = t.getLongOr("lockUntil", 0L);
		r.wallSteps = t.getIntOr("wallSteps", 0);
		r.unrestShown = t.getBooleanOr("unrestShown", false);
		r.swapQueued = t.getBooleanOr("swapQueued", false);
		List<BlockState> palette = t.read("palette", BlockState.CODEC.listOf()).orElse(List.of());
		long[] jp = t.getLongArray("jpos").orElse(new long[0]);
		int[] jo = t.getIntArray("jorig").orElse(new int[0]);
		int[] jw = t.getIntArray("jwritten").orElse(new int[0]);
		for (int i = 0; i < jp.length && i < jo.length && i < jw.length; i++) {
			if (jo[i] < palette.size() && jw[i] < palette.size()) r.journal.put(jp[i], new Change(palette.get(jo[i]), palette.get(jw[i])));
		}
		long[] pp = t.getLongArray("ppos").orElse(new long[0]);
		int[] ps = t.getIntArray("pstate").orElse(new int[0]);
		for (int i = 0; i < pp.length && i < ps.length; i++) if (ps[i] < palette.size()) r.plan.add(new Op(pp[i], palette.get(ps[i])));
		r.planIndex = t.getIntOr("planIndex", 0);
		r.planKind = t.getIntOr("planKind", 0);
		return r;
	}
}
