package dev.elsewhere.palehollow.fit3;

import net.minecraft.core.Direction;

/** Local plan coordinates to world: origin plus one of four rotations (clockwise quarter turns). */
public record Frame(int ox, int oz, int rot) {
	public int x(int lx, int lz) {
		return ox + switch (rot & 3) {
			case 1 -> -lz;
			case 2 -> -lx;
			case 3 -> lz;
			default -> lx;
		};
	}

	public int z(int lx, int lz) {
		return oz + switch (rot & 3) {
			case 1 -> lx;
			case 2 -> -lz;
			case 3 -> -lx;
			default -> lz;
		};
	}

	/** A local horizontal direction in world terms. */
	public Direction d(Direction local) {
		Direction d = local;
		if (d.getAxis().isVertical()) return d;
		for (int i = 0; i < (rot & 3); i++) d = d.getClockWise();
		return d;
	}

	/** World rectangle {minX, minZ, maxX, maxZ} of a local rectangle. */
	public int[] rect(int x0, int z0, int x1, int z1) {
		int ax = x(x0, z0), az = z(x0, z0), bx = x(x1, z1), bz = z(x1, z1);
		return new int[] {Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
	}
}
