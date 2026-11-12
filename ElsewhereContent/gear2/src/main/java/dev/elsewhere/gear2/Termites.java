package dev.elsewhere.gear2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Termite Hive Lantern spread: after you mine an edible block (dirt, wood, mud, stone), the termites eat the 8 blocks
 * of the 3x3x1 face around it (the face you're looking at) one by one over the next 2 s, as if you mined them. They
 * stop if you put the lantern away. Ores and anything outside {@code #elsewhere:termite_edible} are never eaten.
 */
public final class Termites {
	private Termites() {}

	record Bite(ServerLevel level, ServerPlayer player, BlockPos pos, long due) {}

	private static final List<Bite> QUEUE = new ArrayList<>();
	private static boolean chewing;

	public static boolean chewing() {
		return chewing;
	}

	public static int pending() {
		return QUEUE.size();
	}

	/** The plane's normal: the dominant axis of where the player looks. */
	public static Direction.Axis faceAxis(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		double ax = Math.abs(l.x), ay = Math.abs(l.y), az = Math.abs(l.z);
		return ay >= ax && ay >= az ? Direction.Axis.Y : (ax >= az ? Direction.Axis.X : Direction.Axis.Z);
	}

	public static List<BlockPos> face(BlockPos center, Direction.Axis axis) {
		List<BlockPos> out = new ArrayList<>(8);
		for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
			if (a == 0 && b == 0) continue;
			out.add(switch (axis) {
				case X -> center.offset(0, a, b);
				case Y -> center.offset(a, 0, b);
				case Z -> center.offset(a, b, 0);
			});
		}
		return out;
	}

	public static void spread(ServerLevel level, ServerPlayer p, BlockPos center) {
		List<BlockPos> ring = face(center, faceAxis(p));
		java.util.Collections.shuffle(ring, new java.util.Random(level.getRandom().nextLong()));
		long now = level.getGameTime();
		int step = Gear2Stats.TERMITE_SPREAD_TICKS / ring.size();
		for (int i = 0; i < ring.size(); i++) QUEUE.add(new Bite(level, p, ring.get(i), now + (long) step * (i + 1)));
	}

	static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) return;
		Iterator<Bite> it = QUEUE.iterator();
		List<Bite> due = new ArrayList<>();
		while (it.hasNext()) {
			Bite b = it.next();
			if (b.player().isRemoved() || b.level().getServer() != server) {
				it.remove();
			} else if (b.level().getGameTime() >= b.due()) {
				due.add(b);
				it.remove();
			}
		}
		for (Bite b : due) bite(b);
	}

	static void bite(Bite b) {
		ServerPlayer p = b.player();
		ServerLevel level = b.level();
		if (p.level() != level || !p.getMainHandItem().is(Gear2Items.TERMITE_HIVE_LANTERN)) return;
		BlockState s = level.getBlockState(b.pos());
		if (!s.is(Gear2Items.TERMITE_EDIBLE) || !level.mayInteract(p, b.pos())) return;
		chewing = true;
		try {
			if (p.gameMode.destroyBlock(b.pos())) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), b.pos().getX() + 0.5, b.pos().getY() + 0.5, b.pos().getZ() + 0.5,
					8, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, b.pos(), SoundEvents.SILVERFISH_STEP, SoundSource.BLOCKS, 0.5F, 1.4F);
			}
		} finally {
			chewing = false;
		}
	}
}
