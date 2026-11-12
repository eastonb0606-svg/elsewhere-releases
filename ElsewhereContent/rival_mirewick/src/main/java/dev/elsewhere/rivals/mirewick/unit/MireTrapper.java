package dev.elsewhere.rivals.mirewick.unit;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.mirewick.TempBlocks;
import dev.elsewhere.rivals.mirewick.boss.GroundPatches;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Mire-Trapper: 16 HP and a crossbow. Lays traps near its target, at most 3 at a time: a 3x3 of cobweb or a patch of mud
 * (Slowness II). Traps vanish after 10 seconds and never replace a solid block (webs go only into air; mud is a particle patch).
 */
public class MireTrapper extends KingdomSoldier {
	public static final int MAX_TRAPS = 3, TTL = 200, COOLDOWN = 80;
	public int trapCd = 40, laid, webBlocks, mudPatches;
	private final List<Long> expiries = new ArrayList<>();
	private boolean webNext = true;

	public MireTrapper(EntityType<? extends KingdomSoldier> type, Level level) {
		super(type, level);
	}

	/** How many of this trapper's traps are still live. */
	public int liveTraps(long now) {
		expiries.removeIf(e -> e <= now);
		return expiries.size();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (trapCd > 0) trapCd--;
		LivingEntity t = getTarget();
		if (t == null) {
			// trap_line: with enemies in view but no target yet, seed the likely approach
			var sq = squad();
			if (sq != null && !sq.enemies().isEmpty()) t = sq.enemies().get(0);
		}
		if (t == null || !t.isAlive() || trapCd > 0 || distanceToSqr(t) > 18 * 18) return;
		long now = level.getGameTime();
		if (liveTraps(now) >= MAX_TRAPS) return;
		trapCd = COOLDOWN;
		if (layTrap(level, t.position(), now)) {
			webNext = !webNext;
			if (laid % 2 == 1) dev.elsewhere.rivals.mirewick.tactics.MirewickTactics.shout(level, this, "message.elsewhere.mirewick_mind_mud");
		}
	}

	/** Lays one trap within 3 blocks of {@code near} (so always within 6 of the target). Returns true when something was placed. */
	public boolean layTrap(ServerLevel level, Vec3 near, long now) {
		for (int tries = 0; tries < 6; tries++) {
			double ang = random.nextDouble() * Math.PI * 2, r = 1.0 + random.nextDouble() * 2.0;
			BlockPos c = BlockPos.containing(near.x + Math.cos(ang) * r, near.y, near.z + Math.sin(ang) * r);
			if (!level.getBlockState(c).isAir() || !level.getBlockState(c.below()).isSolid()) continue;
			if (webNext) {
				int n = 0;
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (TempBlocks.place(level, c.offset(dx, 0, dz), Blocks.COBWEB.defaultBlockState(), TTL)) n++;
				if (n == 0) continue;
				webBlocks += n;
			} else {
				GroundPatches.add(level, new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5), 1.5, TTL, GroundPatches.Kind.MUD, getUUID());
				mudPatches++;
			}
			expiries.add(now + TTL);
			laid++;
			return true;
		}
		return false;
	}
}
