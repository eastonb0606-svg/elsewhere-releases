package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.Behaviours;
import dev.elsewhere.duelist.boss.HumanBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in temporary blocks: bridges over gaps or deep water and pillars out of {@code duel_block}s, with the boss's own numbers
 * ({@link Behaviours.TempBlocks}: interval, lifetime, active cap, bridge and pillar length), air only, never replacing a block, never fully enclosing a player.
 * Everything goes through {@link DuelBlocks}, so the blocks vanish on their own and on boss death.
 */
public final class BlockBuilder {
	private final HumanBoss boss;
	private long nextAt;
	private int bridgeLen, pillarH;
	private boolean jumpedForPillar;
	public int placed;

	public BlockBuilder(HumanBoss boss) {
		this.boss = boss;
	}

	private boolean pathMissing;
	private boolean pillaring;

	/** True while the boss is stacking a pillar: it stands still horizontally and only jumps and places. */
	public boolean pillaring() {
		return pillaring;
	}

	/** Runs every AI tick while the boss is free. Cheap when there is nothing to build (the path check runs every 10 ticks). */
	public void tick(ServerLevel level, @Nullable LivingEntity target) {
		pillaring = jumpedForPillar;
		bridgeHold = false;
		if (target == null || boss.profile.blocks == null) return;
		Behaviours.TempBlocks cfg = boss.profile.blocks;
		boolean grounded = boss.onGround();
		BlockPos below = boss.blockPosition().below();
		if (grounded && !level.getBlockState(below).is(dev.elsewhere.duelist.DuelistContent.DUEL_BLOCK)) {
			bridgeLen = 0;
			if (!jumpedForPillar) pillarH = 0;
		}
		if (boss.tickCount % 10 == 0) {
			var path = boss.getNavigation().createPath(target, 0);
			pathMissing = path == null || !path.canReach();
		}
		boolean water = deepWaterAhead(level, target);
		if (!pathMissing && !jumpedForPillar && !water) return;
		double dy = target.getY() - boss.getY();
		double horiz = Math.sqrt((target.getX() - boss.getX()) * (target.getX() - boss.getX()) + (target.getZ() - boss.getZ()) * (target.getZ() - boss.getZ()));
		boolean full = DuelBlocks.count(level, boss.getUUID()) >= cfg.maxActive();
		boolean ready = level.getGameTime() >= nextAt && !full;
		// pillar: the target is 2 or more blocks higher, close by, and the boss cannot walk up
		if (dy >= 2.0 && horiz <= 5.0 && pillarH < cfg.pillarMax() && pathMissing) {
			pillaring = true;
			if (ready || jumpedForPillar) pillar(level);
			return;
		}
		jumpedForPillar = false;
		if (grounded && bridgeLen < cfg.bridgeMax() && boss.distanceTo(target) > 3.5) {
			Direction d = Direction.getApproximateNearest(target.getX() - boss.getX(), 0, target.getZ() - boss.getZ());
			BlockPos floor = boss.blockPosition().relative(d).below();
			if (level.getBlockState(floor).isAir()) {
				bridgeHold = true; // a gap ahead: stand at the edge until the block is down
				if (ready && place(level, floor)) bridgeLen++;
			}
		}
	}

	private boolean bridgeHold;

	/** True while the boss waits at an edge for its next bridge block. */
	public boolean holding() {
		return bridgeHold || pillaring;
	}

	/** Water just below the missing floor ahead: bridge it even though a swim path exists. */
	private boolean deepWaterAhead(ServerLevel level, LivingEntity target) {
		if (!boss.onGround()) return false;
		Direction d = Direction.getApproximateNearest(target.getX() - boss.getX(), 0, target.getZ() - boss.getZ());
		BlockPos floor = boss.blockPosition().relative(d).below();
		return level.getBlockState(floor).isAir() && !level.getFluidState(floor.below()).isEmpty();
	}

	private int jumpBaseY;

	private void pillar(ServerLevel level) {
		if (boss.onGround()) {
			jumpedForPillar = false;
			if (pillarH < boss.profile.blocks.pillarMax()) {
				jumpBaseY = boss.getBlockY();
				boss.jumpFromGround();
				jumpedForPillar = true;
			}
			return;
		}
		if (jumpedForPillar && boss.getY() >= jumpBaseY + 1.0) {
			BlockPos at = new BlockPos(boss.getBlockX(), jumpBaseY, boss.getBlockZ());
			if (place(level, at)) pillarH++;
			jumpedForPillar = false;
		}
	}

	private boolean place(ServerLevel level, BlockPos at) {
		boolean ok = DuelBlocks.place(level, at, boss.getUUID(), boss, boss.profile.blocks.lifetime());
		if (ok) {
			placed++;
			nextAt = level.getGameTime() + boss.profile.blocks.interval();
			boss.bump("block");
		}
		return ok;
	}
}
