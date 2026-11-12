package dev.elsewhere.stub.overworld_bosses;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.Nullable;

/**
 * A boss arena: a vertical cylinder around {@link #center}. On summon its blocks are snapshotted; the seal (a ring of
 * {@code elsewhere:arena_seal}) closes it; {@link #restore} puts every changed block back (holes, fire, sunk lily
 * pads, collapsed floors, stomped campfires, the seal itself). Nothing outside the cylinder is touched.
 * STUB: the core's framework owns the real arena seal; this copy keeps the same behaviour for the module's tests.
 */
public final class BossArena {
	public final BlockPos center;
	public final int radius;
	public final int down;
	public final int up;
	/** Seal wall height above {@link #floorY}. 0 = no seal (for arenas that seal themselves, e.g. the Sun Chamber). */
	public final int sealHeight;
	public final int floorY;
	private BlockState[] palette;
	private int[] snapshot;
	/** Seal positions this arena placed or shares (see {@link ArenaSeals}). */
	final LongOpenHashSet sealed = new LongOpenHashSet();
	/** The boss fighting in this arena; the arena's seals are live only while it is. */
	private @Nullable ElsewhereBoss owner;
	private boolean released;

	public BossArena(BlockPos center, int radius, int down, int up, int floorY, int sealHeight) {
		this.center = center.immutable();
		this.radius = radius;
		this.down = down;
		this.up = up;
		this.floorY = floorY;
		this.sealHeight = sealHeight;
	}

	public int minY() { return this.center.getY() - this.down; }
	public int maxY() { return this.center.getY() + this.up; }

	public boolean contains(Vec3 p) {
		double dx = p.x - (this.center.getX() + 0.5);
		double dz = p.z - (this.center.getZ() + 0.5);
		return dx * dx + dz * dz <= (this.radius + 0.5) * (this.radius + 0.5) && p.y >= minY() - 1 && p.y <= maxY() + 2;
	}

	public boolean contains(Entity e) {
		return contains(e.position());
	}

	public boolean containsBlock(BlockPos p) {
		double dx = p.getX() - this.center.getX();
		double dz = p.getZ() - this.center.getZ();
		return dx * dx + dz * dz <= (this.radius + 0.5) * (this.radius + 0.5) && p.getY() >= minY() && p.getY() <= maxY();
	}

	public AABB box() {
		return new AABB(this.center.getX() - this.radius, minY(), this.center.getZ() - this.radius,
			this.center.getX() + this.radius + 1, maxY() + 1, this.center.getZ() + this.radius + 1);
	}

	public Vec3 centerVec() {
		return new Vec3(this.center.getX() + 0.5, this.floorY + 1, this.center.getZ() + 0.5);
	}

	public void setOwner(@Nullable ElsewhereBoss owner) {
		this.owner = owner;
	}

	/** True while the owning boss is loaded, alive and still fighting in this arena (its seal must stay). */
	public boolean isLive() {
		ElsewhereBoss o = this.owner;
		return !this.released && o != null && !o.isRemoved() && o.isAlive() && o.getArena() == this;
	}

	/** True once {@link #restore} or {@link #unseal} lifted this arena (until it is sealed again). */
	public boolean isReleased() {
		return this.released;
	}

	public int sealCount() {
		return this.sealed.size();
	}

	/** How many of this arena's seal positions still hold a seal block (tests). */
	public int sealsStanding(ServerLevel level) {
		int n = 0;
		for (long k : this.sealed) if (level.getBlockState(BlockPos.of(k)).is(BossRegistry.ARENA_SEAL)) n++;
		return n;
	}

	public boolean hasSnapshot() {
		return this.snapshot != null;
	}

	private int size() {
		int d = this.radius * 2 + 1;
		return d * d * (this.down + this.up + 1);
	}

	private void forEach(PosVisitor v) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = minY(); y <= maxY(); y++) {
			for (int x = -this.radius; x <= this.radius; x++) {
				for (int z = -this.radius; z <= this.radius; z++) {
					m.set(this.center.getX() + x, y, this.center.getZ() + z);
					v.visit(i++, m, x * x + z * z <= (this.radius + 0.5) * (this.radius + 0.5));
				}
			}
		}
	}

	private interface PosVisitor {
		void visit(int index, BlockPos.MutableBlockPos pos, boolean inside);
	}

	/** Snapshots every block in the cylinder (palette + index array, so it can be saved with the boss). */
	public void capture(ServerLevel level) {
		Map<BlockState, Integer> ids = new HashMap<>();
		List<BlockState> pal = new ArrayList<>();
		int[] snap = new int[size()];
		forEach((i, pos, inside) -> {
			BlockState s = level.getBlockState(pos);
			if (s.is(BossRegistry.ARENA_SEAL)) {
				// Another arena's wall: remember what stood there before it, never the seal itself.
				BlockState before = ArenaSeals.original(level, pos);
				s = before != null ? before : Blocks.AIR.defaultBlockState();
			}
			Integer id = ids.get(s);
			if (id == null) {
				id = pal.size();
				pal.add(s);
				ids.put(s, id);
			}
			snap[i] = id;
		});
		this.palette = pal.toArray(new BlockState[0]);
		this.snapshot = snap;
	}

	/** Closes the arena: a ring of seal blocks on the cylinder edge where there is air or water. */
	public void seal(ServerLevel level) {
		this.released = false;
		if (this.sealHeight <= 0) return;
		BlockState seal = BossRegistry.ARENA_SEAL.defaultBlockState();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int steps = Math.max(32, (int) (Math.PI * 2 * this.radius * 1.6));
		for (int s = 0; s < steps; s++) {
			double a = Math.PI * 2 * s / steps;
			int x = this.center.getX() + (int) Math.round(Math.cos(a) * this.radius);
			int z = this.center.getZ() + (int) Math.round(Math.sin(a) * this.radius);
			for (int y = this.floorY + 1; y <= this.floorY + this.sealHeight && y <= maxY(); y++) {
				m.set(x, y, z);
				ArenaSeals.place(level, m, this, seal);
			}
		}
	}

	/** Puts every changed block back. Returns how many blocks were restored. */
	public int restore(ServerLevel level) {
		// Lift this arena's own seals first (to the block that stood there before any seal, shared walls stay up).
		int lifted = ArenaSeals.release(level, this);
		this.released = true;
		if (this.snapshot == null) return lifted;
		int[] count = {lifted};
		forEach((i, pos, inside) -> {
			BlockState want = this.palette[this.snapshot[i]];
			BlockState cur = level.getBlockState(pos);
			// An overlapping arena that is still fighting keeps its wall; it lifts it itself when its fight ends.
			if (cur.is(BossRegistry.ARENA_SEAL) && ArenaSeals.heldByOther(level, pos, this)) return;
			if (want.is(BossRegistry.ARENA_SEAL)) want = Blocks.AIR.defaultBlockState();
			if (differs(cur, want)) {
				level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				count[0]++;
			}
		});
		return count[0];
	}

	/** Removes only the seal blocks (used when a fight ends in a win and the arena was already restored). */
	public void unseal(ServerLevel level) {
		ArenaSeals.release(level, this);
		this.released = true;
		forEach((i, pos, inside) -> {
			if (level.getBlockState(pos).is(BossRegistry.ARENA_SEAL) && !ArenaSeals.heldByOther(level, pos, this)) {
				BlockState before = ArenaSeals.original(level, pos);
				level.setBlock(pos, before != null ? before : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		});
	}

	public void save(ValueOutput out) {
		out.putInt("CX", this.center.getX());
		out.putInt("CY", this.center.getY());
		out.putInt("CZ", this.center.getZ());
		out.putInt("R", this.radius);
		out.putInt("Down", this.down);
		out.putInt("Up", this.up);
		out.putInt("Floor", this.floorY);
		out.putInt("Seal", this.sealHeight);
		if (this.snapshot != null) {
			out.store("Palette", Codec.list(BlockState.CODEC), List.of(this.palette));
			out.putIntArray("Snapshot", this.snapshot);
		}
	}

	public static BossArena load(ValueInput in) {
		if (in.getInt("R").isEmpty()) return null;
		BossArena a = new BossArena(new BlockPos(in.getIntOr("CX", 0), in.getIntOr("CY", 0), in.getIntOr("CZ", 0)),
			in.getIntOr("R", 16), in.getIntOr("Down", 4), in.getIntOr("Up", 12), in.getIntOr("Floor", 0), in.getIntOr("Seal", 0));
		List<BlockState> pal = in.read("Palette", Codec.list(BlockState.CODEC)).orElse(null);
		int[] snap = in.getIntArray("Snapshot").orElse(null);
		if (pal != null && snap != null && snap.length == a.size()) {
			a.palette = pal.toArray(new BlockState[0]);
			a.snapshot = snap;
		}
		return a;
	}

	/** Core's Boss Waystone (placed near a slain boss, often inside the arena) is neither arena damage nor rolled back. */
	private static boolean differs(BlockState cur, BlockState want) {
		return cur != want && !"elsewhere:boss_waystone".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString());
	}
}
