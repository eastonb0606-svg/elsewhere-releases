package dev.elsewhere.greenhaze_hush;

import dev.elsewhere.greenhaze.Greenhaze;
import dev.elsewhere.hush.Hush;
import dev.elsewhere.hush.HushBlocks;
import dev.elsewhere.stub.greenhaze_hush.TripTrigger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Safe arrival in the Greenhaze / the Hush, and the way back (dev command and accidental-trip arrival stub). */
public final class DimTravel {
	private DimTravel() {}

	private static final Map<UUID, Vec3> RETURN = new HashMap<>();
	private static final Map<UUID, ResourceKey<Level>> RETURN_DIM = new HashMap<>();

	public static BlockPos safeSpot(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		if (level.dimension().equals(Hush.LEVEL)) {
			// a solid ledge: scan down from y=0 for a floor with 2 blocks of air; else build a small hushstone ledge
			for (int y = 40; y > level.getMinY() + 20; y--) {
				BlockPos p = new BlockPos(x, y, z);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolid()
					&& level.getFluidState(p).isEmpty()) return p;
			}
			BlockPos p = new BlockPos(x, 0, z);
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				level.setBlock(p.offset(dx, -1, dz), HushBlocks.HUSHSTONE_BRICKS.defaultBlockState(), 3);
				for (int dy = 0; dy < 3; dy++) level.setBlock(p.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
			}
			return p;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos p = new BlockPos(x, y, z);
		if (!level.getFluidState(p.below()).isEmpty() || y <= level.getMinY() + 1) {
			level.setBlock(p.below(), Blocks.MOSS_BLOCK.defaultBlockState(), 3);
		}
		return p;
	}

	public static void sendTo(ServerPlayer p, ResourceKey<Level> dim) {
		ServerLevel target = p.level().getServer().getLevel(dim);
		if (target == null) { p.sendSystemMessage(Component.literal("Dimension " + dim.identifier() + " is not loaded")); return; }
		if (!p.level().dimension().equals(dim)) {
			RETURN.put(p.getUUID(), p.position());
			RETURN_DIM.put(p.getUUID(), p.level().dimension());
		}
		BlockPos spot = safeSpot(target, p.getBlockX(), p.getBlockZ());
		p.teleportTo(target, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
	}

	public static void home(ServerPlayer p) {
		Vec3 back = RETURN.remove(p.getUUID());
		ResourceKey<Level> dim = RETURN_DIM.remove(p.getUUID());
		if (back == null || dim == null) { dev.elsewhere.stub.greenhaze_hush.ReturnRift.sendHome(p); return; }
		ServerLevel level = p.level().getServer().getLevel(dim);
		if (level == null) level = p.level().getServer().overworld();
		p.teleportTo(level, back.x, back.y, back.z, Set.of(), p.getYRot(), p.getXRot(), true);
	}

	/** STUB arrival for accidental trips (the core gives a Frayed Wayfinder and picks a spot near a Return Rift). */
	public static void accidentalArrival(ServerPlayer p, TripTrigger t) {
		sendTo(p, t.target());
		String name = t.target().equals(Greenhaze.LEVEL) ? "the Greenhaze" : "the Hush";
		p.sendSystemMessage(Component.literal("You drifted into " + name + ". A Frayed Wayfinder will take you back once.")
			.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		var frayed = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(Reg.id("frayed_wayfinder"));
		frayed.ifPresent(item -> p.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(item), net.minecraft.util.Prediction.SERVER_ONLY));
	}

	public static void forgetPlayer(UUID id) {
		RETURN.remove(id);
		RETURN_DIM.remove(id);
	}

	public static void clearAll() {
		RETURN.clear();
		RETURN_DIM.clear();
	}
}
