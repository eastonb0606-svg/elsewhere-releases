package dev.elsewhere.petalwind;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hanging windchime. It rings in the wind (random ticks) and nudges hostile mobs away. Punching it or hitting it with a
 * projectile (a wind charge) rings it on demand; listeners (Kazehana's wind-bell posts) hear every ring.
 */
public class WindchimeBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(4, 2, 4, 12, 16, 12);
	/** Called on the server whenever a chime rings, with the ringer (null for wind). */
	public static final List<BiConsumer<ServerLevel, BlockPos>> RING_LISTENERS = new CopyOnWriteArrayList<>();

	public WindchimeBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		ring(level, pos, false);
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server) ring(server, pos, true);
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (level instanceof ServerLevel server) ring(server, hit.getBlockPos(), true);
	}

	public static void ring(ServerLevel level, BlockPos pos, boolean struck) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, struck ? 2.0F : 0.8F, 0.9F + level.getRandom().nextFloat() * 0.4F);
		if (struck) level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 1.6F);
		level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, struck ? 6 : 1, 0.4, 0.3, 0.4, 0.5);
		Vec3 c = Vec3.atCenterOf(pos);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(5), m -> m instanceof Enemy)) {
			Vec3 away = mob.position().subtract(c).multiply(1, 0, 1).normalize().scale(struck ? 0.8 : 0.35);
			mob.push(away.x, 0.15, away.z);
			mob.needsSync = true;
		}
		if (struck) for (BiConsumer<ServerLevel, BlockPos> l : RING_LISTENERS) l.accept(level, pos);
	}
}
