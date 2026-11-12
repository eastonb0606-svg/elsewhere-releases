package dev.elsewhere.kingdom.war;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The one block layout of a Hrafnholt longship, shared by the sailing entity's renderer and the beached prop of temporary blocks, so the ship looks the
 * same before and after it beaches. Local frame: the bow points to +z (south when unrotated), x is across the ship, y up; (0, 0, 0) is the keel block under
 * the mast and the ship's entity position is that block's bottom centre. 13 blocks long, 5 wide, mast and sail 9 high.
 */
public final class ShipShape {
	private ShipShape() {}

	public enum Role { KEEL, HULL, SIDE, STEM, TRIM, BENCH, MAST, YARD, SAIL_A, SAIL_B, SHIELD_A, SHIELD_B }

	/** One block of the layout. {@code shield} parts are drawn only by the entity renderer (round plates on the outer hull), never placed as blocks. */
	public record Part(int x, int y, int z, Role role) {
		public boolean shield() {
			return role == Role.SHIELD_A || role == Role.SHIELD_B;
		}
	}

	/** Seats (local x, z) of the 8 raiders: two benches per row, four rows. */
	public static final int[][] SEATS = {{-1, 3}, {1, 3}, {-1, 1}, {1, 1}, {-1, -1}, {1, -1}, {-1, -3}, {1, -3}};
	public static final int CAPACITY = SEATS.length, HALF_LENGTH = 6;
	public static final List<Part> PARTS = build();

	private static List<Part> build() {
		List<Part> p = new ArrayList<>();
		for (int z = -6; z <= 6; z++) p.add(new Part(0, 0, z, Role.KEEL));
		for (int z = -5; z <= 5; z++) for (int x : new int[] {-1, 1}) p.add(new Part(x, 0, z, Role.HULL));
		for (int z = -3; z <= 3; z++) for (int x : new int[] {-2, 2}) p.add(new Part(x, 0, z, Role.HULL));
		for (int z = -4; z <= 4; z++) for (int x : new int[] {-2, 2}) p.add(new Part(x, 1, z, Role.SIDE));
		for (int z : new int[] {-5, 5}) for (int x : new int[] {-1, 1}) {
			p.add(new Part(x, 1, z, Role.SIDE));
			p.add(new Part(x, 2, z, Role.TRIM));
		}
		for (int z : new int[] {-4, 4}) for (int x : new int[] {-2, 2}) p.add(new Part(x, 2, z, Role.TRIM));
		for (int y = 1; y <= 3; y++) p.add(new Part(0, y, 6, Role.STEM)); // prow post
		p.add(new Part(0, 4, 6, Role.TRIM));
		for (int y = 1; y <= 2; y++) p.add(new Part(0, y, -6, Role.STEM)); // stern post
		p.add(new Part(0, 3, -6, Role.TRIM));
		for (int[] s : SEATS) p.add(new Part(s[0], 1, s[1], Role.BENCH));
		for (int y = 1; y <= 3; y++) p.add(new Part(0, y, 0, Role.MAST));
		for (int y = 4; y <= 6; y++) for (int x = -3; x <= 3; x++) p.add(new Part(x, y, 0, Math.abs(x) % 2 == 0 ? Role.SAIL_A : Role.SAIL_B));
		for (int x = -3; x <= 3; x++) p.add(new Part(x, 7, 0, Role.YARD));
		p.add(new Part(0, 8, 0, Role.MAST));
		for (int z = -3; z <= 3; z++) for (int x : new int[] {-2, 2}) p.add(new Part(x, 1, z, (z & 1) == 0 ? Role.SHIELD_A : Role.SHIELD_B));
		return List.copyOf(p);
	}

	/** The block state of {@code part} in the local (bow to +z) frame for {@code st}: logs of the keel run along the ship, yard fences connect sideways. */
	public static BlockState state(Part part, Longship.Style st) {
		return switch (part.role()) {
			case KEEL -> axis(st.keel(), Direction.Axis.Z);
			case HULL, SIDE -> st.hull().defaultBlockState();
			case STEM -> axis(st.stem(), Direction.Axis.Y);
			case TRIM -> st.trim().defaultBlockState();
			case BENCH -> st.bench().defaultBlockState();
			case MAST -> st.mast().defaultBlockState();
			case YARD -> yard(st.rail(), part.x());
			case SAIL_A -> st.sail().defaultBlockState();
			case SAIL_B -> st.sailAlt().defaultBlockState();
			case SHIELD_A -> st.shield().defaultBlockState();
			case SHIELD_B -> st.shieldAlt().defaultBlockState();
		};
	}

	private static BlockState axis(Block b, Direction.Axis a) {
		BlockState s = b.defaultBlockState();
		return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}

	private static BlockState yard(Block b, int x) {
		BlockState s = b.defaultBlockState();
		if (s.hasProperty(CrossCollisionBlock.EAST) && s.hasProperty(CrossCollisionBlock.WEST)) s = s.setValue(CrossCollisionBlock.EAST, x < 3).setValue(CrossCollisionBlock.WEST, x > -3);
		return s;
	}

	/** Block rotation that turns the local frame (bow south) to a bow pointing along (dx, dz). */
	public static Rotation rotation(int dx, int dz) {
		if (dz > 0) return Rotation.NONE;
		if (dx < 0) return Rotation.CLOCKWISE_90;
		if (dz < 0) return Rotation.CLOCKWISE_180;
		return Rotation.COUNTERCLOCKWISE_90;
	}

	/** Entity yaw (degrees) of a bow pointing along (dx, dz). */
	public static float yaw(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
}
