package dev.elsewhere.petalwind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Runs on both sides: the client pushes its own player smoothly, the server pushes everything else. */
public class UpdraftVentBlockEntity extends BlockEntity {
	public static final int RANGE = 12;
	private int height = RANGE;

	public UpdraftVentBlockEntity(BlockPos pos, BlockState state) {
		super(PetalwindContentTypes.UPDRAFT_VENT, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, UpdraftVentBlockEntity vent) {
		if (level.getGameTime() % 20 == pos.asLong() % 20) vent.height = openHeight(level, pos);
		if (vent.height <= 0) return;
		AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1 + vent.height, pos.getZ() + 1);
		for (Entity entity : level.getEntities((Entity) null, column, e -> !e.isSpectator() && !e.noPhysics)) {
			if (level.isClientSide()) {
				if (!(entity instanceof Player p && p.isLocalPlayer())) continue; // the client moves its own player
			} else if (entity instanceof Player p && p.isClientAuthoritative()) {
				continue; // real players are pushed by their own client
			}
			push(entity, pos, vent.height);
		}
	}

	/** Upward acceleration that fades toward the top of the column, capped so you float rather than rocket. */
	public static void push(Entity entity, BlockPos pos, int height) {
		double above = entity.getY() - (pos.getY() + 1);
		double strength = 0.12 * (1.0 - Math.min(1.0, above / height)) + 0.02;
		Vec3 v = entity.getDeltaMovement();
		double vy = Math.min(v.y + strength, entity.isShiftKeyDown() ? 0.1 : 0.6);
		entity.setDeltaMovement(v.x, vy, v.z);
		entity.resetFallDistance();
	}

	static int openHeight(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos p = pos.mutable();
		for (int i = 1; i <= RANGE; i++) {
			p.setY(pos.getY() + i);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return i - 1;
		}
		return RANGE;
	}
}
