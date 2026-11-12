package dev.elsewhere.gear2.item;

import dev.elsewhere.gear2.Gear2Stats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * gear-trinket2-b Prospector's Pendulum: as an active trinket it swings toward the nearest ore within 24 blocks every
 * 5 s (a short spark line only you see, 1 durability per swing that finds ore); used in hand it names up to 2 ore types
 * and their distance (1 durability). Diamond ore and ancient debris are never found.
 */
public class ProspectorsPendulum extends Item {
	public ProspectorsPendulum(Properties p) {
		super(p);
	}

	public record Find(Block block, BlockPos pos, double distance) {}

	public static boolean isOre(BlockState s) {
		if (s.isAir() || s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE) || s.is(Blocks.ANCIENT_DEBRIS)) return false;
		if (s.is(ConventionalBlockTags.ORES)) return true;
		String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return path.endsWith("_ore") && !path.contains("diamond");
	}

	/** Same ore kind for "deepslate_iron_ore" and "iron_ore". */
	static String kind(Block b) {
		return BuiltInRegistries.BLOCK.getKey(b).getPath().replace("deepslate_", "");
	}

	/** Nearest ore of each kind within the range, nearest first, at most {@link Gear2Stats#PENDULUM_TYPES} kinds. */
	public static List<Find> scan(ServerLevel level, BlockPos center) {
		int r = Gear2Stats.PENDULUM_RANGE;
		double r2 = (double) r * r;
		Map<String, Find> best = new HashMap<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
			for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) continue;
				LevelChunk chunk = level.getChunk(cx, cz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int si = 0; si < sections.length; si++) {
					LevelChunkSection sec = sections[si];
					int baseY = level.getSectionYFromSectionIndex(si) << 4;
					if (sec.hasOnlyAir() || baseY + 15 < center.getY() - r || baseY > center.getY() + r) continue;
					if (!sec.maybeHas(ProspectorsPendulum::isOre)) continue;
					// only the part of the section inside the scan cube
					int x0 = Math.max(0, center.getX() - r - (cx << 4)), x1 = Math.min(15, center.getX() + r - (cx << 4));
					int y0 = Math.max(0, center.getY() - r - baseY), y1 = Math.min(15, center.getY() + r - baseY);
					int z0 = Math.max(0, center.getZ() - r - (cz << 4)), z1 = Math.min(15, center.getZ() + r - (cz << 4));
					for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
						BlockState s = sec.getBlockState(x, y, z);
						if (!isOre(s)) continue;
						m.set((cx << 4) + x, baseY + y, (cz << 4) + z);
						double d2 = m.distSqr(center);
						if (d2 > r2) continue;
						String k = kind(s.getBlock());
						Find f = best.get(k);
						if (f == null || d2 < f.distance() * f.distance()) best.put(k, new Find(s.getBlock(), m.immutable(), Math.sqrt(d2)));
					}
				}
			}
		}
		List<Find> out = new ArrayList<>(best.values());
		out.sort((a, b) -> Double.compare(a.distance(), b.distance()));
		return out.size() > Gear2Stats.PENDULUM_TYPES ? new ArrayList<>(out.subList(0, Gear2Stats.PENDULUM_TYPES)) : out;
	}

	/** The swing: a short line of sparks from your chest toward the nearest ore, only for you. */
	public static void swing(ServerPlayer p, Find f) {
		ServerLevel level = p.level();
		Vec3 from = p.position().add(0, 1.1, 0);
		Vec3 dir = Vec3.atCenterOf(f.pos()).subtract(from).normalize();
		for (int i = 1; i <= 6; i++) {
			Vec3 at = from.add(dir.scale(0.3 * i));
			level.sendParticles(p, ParticleTypes.WAX_ON, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS,
			p.getX(), p.getY(), p.getZ(), 0.25F, 1.8F, level.getRandom().nextLong()));
	}

	public static Component describe(List<Find> finds) {
		if (finds.isEmpty()) return Component.translatable("message.elsewhere.prospectors_pendulum.none", Gear2Stats.PENDULUM_RANGE);
		Component out = Component.empty();
		for (int i = 0; i < finds.size(); i++) {
			Find f = finds.get(i);
			if (i > 0) out = out.copy().append("  ·  ");
			out = out.copy().append(Component.translatable("message.elsewhere.prospectors_pendulum.find", f.block().getName(), (int) Math.round(f.distance())));
		}
		return out;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		List<Find> finds = scan(sl, sp.blockPosition());
		sp.sendOverlayMessage(describe(finds));
		if (!finds.isEmpty()) swing(sp, finds.get(0));
		stack.hurtAndBreak(1, sp, hand);
		sp.getCooldowns().addCooldown(stack, 20);
		return InteractionResult.SUCCESS_SERVER;
	}
}
