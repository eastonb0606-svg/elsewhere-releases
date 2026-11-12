package dev.elsewhere.stub.verdigris_ironpeak;

import static dev.elsewhere.verdigris_ironpeak.Reg.block;
import static dev.elsewhere.verdigris_ironpeak.Reg.item;

import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * STUB of the core's Return Rift (CONTRACT.md rule 2) and Frayed Wayfinder: a permanent one-way rift to the
 * Overworld in a small ruin, marked by a beacon beam in the dimension's colour; plus the single-use way home given on
 * accidental arrival. The core owns the real block, piece and item; this module's structure sets point at
 * {@code elsewhere:<dimension>_return_rift} structures that use these blueprints.
 */
public final class ReturnRiftStub {
	public static final Block RETURN_RIFT = dev.elsewhere.verdigris_ironpeak.Reg.sharedBlock("return_rift", RiftBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollision().noOcclusion().strength(-1.0F, 3600000.0F)
			.lightLevel(s -> 12).pushReaction(PushReaction.IMMOVEABLE).noLootTable(), false);
	public static final Item FRAYED_WAYFINDER = dev.elsewhere.verdigris_ironpeak.Reg.sharedItem("frayed_wayfinder", FrayedWayfinderItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	private ReturnRiftStub() {}

	public static void init() {
		Blueprint.register("return_rift_verdigris_sea", ruin(Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.CYAN).defaultBlockState(), Blocks.CUT_COPPER.weathering().oxidized().defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState()));
		Blueprint.register("return_rift_ironpeak", ruin(Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE).defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState()));
	}

	/**
	 * A 7x7 ruin: a sea-lantern hearth under tinted glass on a pillar-stone base, cracked pillars, and the rift beside it. No beacon and no iron
	 * base (no valuable blocks in structures, s-throne B9): the tall beam is the Return Rift's own (the core block entity draws it).
	 */
	private static Blueprint ruin(BlockState glass, BlockState floor, BlockState pillar) {
		return new Blueprint() {
			public int radius() {
				return 4;
			}

			public int height() {
				return 6;
			}

			public void build(BlockSink s, RandomSource r) {
				for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
					s.foundation(x, -2, z, pillar);
					s.set(x, -1, z, floor);
					for (int y = 0; y <= 5; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
				s.fill(-1, -2, -1, 1, -2, 1, pillar);
				s.set(0, -1, 0, Blocks.SEA_LANTERN);
				s.set(0, 0, 0, glass);
				for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
					int h = 2 + r.nextInt(3);
					for (int y = 0; y < h; y++) s.set(c[0], y, c[1], y == h - 1 && r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : pillar);
				}
				s.set(2, 0, 0, RETURN_RIFT);
				s.set(2, 1, 0, RETURN_RIFT);
				s.set(2, -1, 0, Blocks.CHISELED_STONE_BRICKS);
			}
		};
	}

	/** Takes a player to their Overworld respawn point (or the world spawn). */
	public static boolean sendHome(ServerPlayer p) {
		ServerLevel over = p.level().getServer().overworld();
		BlockPos home = over.getRespawnData().pos();
		ServerPlayer.RespawnConfig cfg = p.getRespawnConfig();
		if (cfg != null && cfg.respawnData().dimension() == net.minecraft.world.level.Level.OVERWORLD) home = cfg.respawnData().pos();
		BlockPos spot = Trips.safeSpot(over, home);
		p.teleport(new TeleportTransition(over, Vec3.atBottomCenterOf(spot), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
		return true;
	}

	public static class RiftBlock extends Block {
		private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

		public RiftBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
			if (!(entity instanceof ServerPlayer p) || p.isPassenger()) return;
			long now = level.getGameTime();
			Long last = COOLDOWN.get(p.getUUID());
			if (last != null && now - last < 100) return;
			COOLDOWN.put(p.getUUID(), now);
			level.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.4F, 1.4F);
			sendHome(p);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + random.nextDouble(),
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
			}
		}
	}

	public static class FrayedWayfinderItem extends Item {
		public FrayedWayfinderItem(Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel) {
				if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
					sp.sendOverlayMessage(Component.literal("The frayed needle only stirs Elsewhere."));
					return InteractionResult.FAIL;
				}
				ItemStack stack = player.getItemInHand(hand);
				stack.shrink(1);
				sendHome(sp);
				sp.sendSystemMessage(Component.literal("The Frayed Wayfinder crumbles as it carries you home."));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
