package dev.elsewhere.stub.greenhaze_hush;

import dev.elsewhere.greenhaze_hush.world.Blueprint;
import dev.elsewhere.greenhaze_hush.world.Sink;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

/**
 * STUB of the core's Return Rift structure piece (CONTRACT.md "User decisions" rule 2): a small ruin with a permanent
 * one-way rift to the Overworld and a tall beacon beam in the dimension's colour (visible from ~150 blocks).
 * At merge the structure JSON switches to the core's piece type; the structure set stays.
 */
public final class ReturnRift {
	private ReturnRift() {}

	public static Blueprint blueprint(BlockState beamGlass, BlockState wall, BlockState floor) {
		return new Blueprint() {
			public int radius() { return 5; }
			public int below() { return 5; }
			public int above() { return 6; }

			public void build(Sink s, BlockPos o, RandomSource r) {
				int x = o.getX(), y = o.getY(), z = o.getZ();
				s.foundation(x, y - 2, z, 5, wall, 6);
				s.clearAbove(x, y, z, 5, 5);
				s.disc(x, y - 1, z, 5, floor);
				// a shroomlight hearth on a 3x3 wall-stone base under the tinted glass, sunk into the floor. No beacon and no iron base (no
				// valuable blocks in structures, s-throne B9); in the merged jar the core Return Rift draws its own tall beam.
				s.fill(x - 1, y - 2, z - 1, x + 1, y - 2, z + 1, wall);
				s.set(x, y - 1, z, Blocks.SHROOMLIGHT.defaultBlockState());
				s.set(x, y, z, beamGlass);
				// broken ring of pillars
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4;
					int px = x + (int) Math.round(Math.cos(a) * 4), pz = z + (int) Math.round(Math.sin(a) * 4);
					int h = 1 + r.nextInt(4);
					for (int dy = 0; dy < h; dy++) s.set(px, y + dy, pz, wall);
				}
				// the rift itself, two blocks tall, beside the beacon
				s.set(x + 2, y, z, StubBlocks.RETURN_RIFT.defaultBlockState());
				s.set(x + 2, y + 1, z, StubBlocks.RETURN_RIFT.defaultBlockState());
				s.set(x + 2, y - 1, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
			}
		};
	}

	/** One-way trip to the player's Overworld respawn point (or world spawn). */
	public static void sendHome(ServerPlayer p) {
		ServerLevel overworld = p.level().getServer().overworld();
		ServerPlayer.RespawnConfig cfg = p.getRespawnConfig();
		LevelData.RespawnData data = cfg != null && cfg.respawnData().globalPos().dimension() == Level.OVERWORLD
			? cfg.respawnData() : overworld.getRespawnData();
		BlockPos pos = data.globalPos().pos();
		overworld.getChunk(pos);
		int y = Math.max(pos.getY(), overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()));
		p.teleportTo(overworld, pos.getX() + 0.5, y, pos.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
		p.sendSystemMessage(Component.literal("The rift spits you out at home.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
	}
}
