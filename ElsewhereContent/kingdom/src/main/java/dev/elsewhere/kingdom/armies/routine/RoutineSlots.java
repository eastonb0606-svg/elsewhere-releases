package dev.elsewhere.kingdom.armies.routine;

import dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where person {@code i} of {@code n} stands for an activity, and which way they face (pure geometry, computed once on arrival).
 * Index 0 is the squad leader's place (the anchor itself). Layouts by activity and style: rings facing in (feast, crowds, the
 * holmgang ring, the Thing), two facing lines (court, the diwan, the council), rows (temple, muster, rank blocks, butts, queues),
 * sparring pairs, an outward guard ring (market), scattered shade (siesta) and a ragged row (Mirewick's half-hearted drill).
 * Yaw is Minecraft's: forward = (-sin yaw, cos yaw).
 */
public final class RoutineSlots {
	private RoutineSlots() {}

	public record Spot(Vec3 pos, float yaw, Pose pose) {}

	public static float yawTo(double fromX, double fromZ, double toX, double toZ) {
		return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
	}

	/** Shape id for an activity and style. */
	public static String shape(String activity, String style) {
		return switch (style) {
			case "crowd", "holmgang", "minstrels", "dice" -> "ring";
			case "rank_blocks", "butts", "queue", "inspection", "review", "dawn_rite", "matins", "vigil" -> "rows";
			case "half_hearted" -> "ragged";
			case "brawl" -> "pairs";
			case "diwan", "council" -> "lines";
			default -> switch (activity) {
				case "feast", "tourney" -> "ring";
				case "court" -> "lines";
				case "temple", "muster", "drill", "procession", "caravan", "lamplight" -> "rows";
				case "spar" -> "pairs";
				case "market" -> "guard_ring";
				case "siesta", "work" -> "scatter";
				default -> "rows";
			};
		};
	}

	/** Spots for {@code n} people (leader first) around {@code anchor}, facing {@code yaw} where the shape has a front. */
	public static List<Spot> layout(String activity, String style, Pose pose, BlockPos anchor, float yaw, int n, UUID seed) {
		String shape = shape(activity, style);
		double cx = anchor.getX() + 0.5, cy = anchor.getY(), cz = anchor.getZ() + 0.5;
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad), rx = -fz, rz = fx;
		List<Spot> out = new ArrayList<>(n);
		long h = seed.getLeastSignificantBits();
		for (int i = 0; i < n; i++) {
			double f = 0, r = 0;
			float y = yaw;
			Pose p = pose;
			switch (shape) {
				case "ring", "guard_ring" -> {
					double radius = (shape.equals("guard_ring") ? 4.5 : 2.5) + n / 8.0;
					double a = 2 * Math.PI * i / Math.max(1, n);
					f = Math.cos(a) * radius;
					r = Math.sin(a) * radius;
					double px = cx + fx * f + rx * r, pz = cz + fz * f + rz * r;
					y = shape.equals("ring") ? yawTo(px, pz, cx, cz) : yawTo(cx, cz, px, pz);
				}
				case "lines" -> {
					// an aisle: two lines facing each other along the yaw axis, the leader at the head of it
					if (i == 0) {
						f = 1.5;
						break;
					}
					int k = i - 1;
					boolean left = (k & 1) == 0;
					f = -1.6 * (k / 2);
					r = left ? -2.0 : 2.0;
					y = yaw + (left ? -90 : 90);
				}
				case "pairs" -> {
					// partners 2.2 blocks apart facing each other; pairs 3 blocks apart side by side
					int pair = i / 2;
					boolean first = (i & 1) == 0;
					r = (pair - (n / 2 - 1) / 2.0) * 3.0;
					f = first ? -1.1 : 1.1;
					y = yaw + (first ? 0 : 180);
				}
				case "scatter" -> {
					long s = h * 31 + i * 0x9E3779B97F4A7C15L;
					f = ((s >>> 8) % 61) / 10.0 - 3.0;
					r = ((s >>> 24) % 61) / 10.0 - 3.0;
					y = yaw + ((s >>> 40) % 360);
				}
				case "ragged" -> {
					long s = h * 17 + i * 0xC2B2AE3D27D4EB4FL;
					int w = 6;
					r = (i % w - (w - 1) / 2.0) * 1.6 + (((s >>> 8) % 17) / 10.0 - 0.8);
					f = -(i / w) * 1.8 + (((s >>> 20) % 17) / 10.0 - 0.8);
					y = yaw + (((s >>> 32) % 41) - 20);
					if (i % 3 == 2) p = Pose.LOUNGE; // only about half turn up properly: some sit it out
				}
				default -> { // rows
					int w = style.equals("rank_blocks") ? 4 : Math.max(3, (int) Math.ceil(Math.sqrt(n * 2.0)));
					if (i == 0) {
						f = 2.0;
						break;
					}
					int k = i - 1;
					r = (k % w - (w - 1) / 2.0) * 1.3;
					f = -(k / w) * 1.4;
				}
			}
			if (pose == Pose.SIT && (i & 1) == 1 && shape.equals("ring")) p = Pose.DRINK; // the feast: half sit, half drink
			out.add(new Spot(new Vec3(cx + fx * f + rx * r, cy, cz + fz * f + rz * r), y, p));
		}
		return out;
	}

	/** A stable offset so several squads sent to one anchor don't stack: a ring of 8 places, 6 blocks out. */
	public static BlockPos spread(BlockPos anchor, UUID squad, int slot) {
		if (slot <= 0) return anchor;
		double a = (Math.floorMod(squad.hashCode(), 8) + slot) * Math.PI / 4;
		return anchor.offset((int) Math.round(Math.cos(a) * 6), 0, (int) Math.round(Math.sin(a) * 6));
	}
}
