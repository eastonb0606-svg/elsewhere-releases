package dev.elsewhere.gear3.s3.dredge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Tidebrass Dredge (GEAR.md A5, shared tools, G4, source Tidewright): "shovel: 3x3 of sand, gravel, clay, mud underwater only,
 * full speed underwater". A netherite-class shovel (catalogue kind "shovel", mechanic "dredge"); everything vanilla shovels do
 * (paths, enchantments through {@code #minecraft:shovels}) still works.
 * <ul>
 * <li><b>Dredging:</b> when the holder's eyes are under water and the block they dig is in {@link #MINEABLE}
 * ({@code #elsewhere:tidebrass_dredge_mineable}: sand, red sand, gravel, clay, mud), the 8 blocks around it in the plane of the face
 * they hit are dug too, if they are also in the tag. Each extra block goes through {@code ServerPlayerGameMode.destroyBlock}, so it
 * drops its loot, costs 1 durability (Unbreaking applies), fires Fabric's block-break events and respects protection. Sneaking digs one
 * block; on land, in creative or with another block it is a plain shovel. Digging stops the moment the dredge breaks.</li>
 * <li><b>Full speed underwater:</b> {@code mixin/TidebrassDredgeMixin} skips both water penalties of {@code Player.getDestroySpeed} while
 * the dredge is in the main hand: the x0.2 for eyes in water and the /5 for floating while in water. Exactly the land speed, so it
 * never stacks with Aqua Affinity into something faster than land.</li>
 * </ul>
 * Lag: nothing ticks. The dig runs in {@link #mineBlock} (only when this item breaks a block, server side) and the speed check is
 * two field reads on the mining path. Kill switch: key {@value #KILL_KEY} in {@code config/elsewhere-overhaul3.json} or
 * {@code -Delsewhere.overhaul3.tidebrass_dredge=false} (read once; set it on the server and on clients): off = a plain netherite-class
 * shovel, the item stays registered.
 */
public class TidebrassDredgeItem extends Item {
	public static final String ID = "tidebrass_dredge";
	public static final String KILL_KEY = "tidebrass_dredge";
	/** Blocks the dredge digs 3x3 (data/elsewhere/tags/block/tidebrass_dredge_mineable.json; datapacks may add to it). */
	public static final TagKey<Block> MINEABLE = TagKey.create(Registries.BLOCK, Gear3Content.id("tidebrass_dredge_mineable"));
	/** Level event 2001: block break particles and sound (what the client plays for a block it broke itself). */
	public static final int BREAK_EVENT = 2001;
	/** Kill switch, read once (the self-test flips it and puts it back). */
	public static boolean enabled = readSwitch(KILL_KEY);
	/** True while the extra blocks of one dig are being broken (their own mineBlock calls must not dig again). Server thread only. */
	private static boolean digging;

	public TidebrassDredgeItem(Properties props) {
		super(props);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		boolean used = super.mineBlock(stack, level, state, pos, miner);
		if (!digging && level instanceof ServerLevel sl && miner instanceof ServerPlayer p) dig(sl, p, pos, state);
		return used;
	}

	/** The holder is "underwater" when their eyes are in water (the same test vanilla uses for the underwater mining penalty). */
	public static boolean underwater(Player p) {
		return p.isEyeInFluid(FluidTags.WATER);
	}

	/** True while the switch is on and the dredge is in the main hand (the hand {@code Player.getDestroySpeed} mines with). */
	public static boolean holding(Player p) {
		return enabled && p.getMainHandItem().getItem() instanceof TidebrassDredgeItem;
	}

	/** Mixin: skip the x0.2 penalty for eyes in water. */
	public static boolean ignoresSubmergedPenalty(Player p) {
		return holding(p);
	}

	/** Mixin: skip the /5 penalty for not standing on the ground, but only while in water (swimming); on land vanilla applies. */
	public static boolean ignoresAfloatPenalty(Player p) {
		return holding(p) && p.isInWater();
	}

	/**
	 * Digs the 8 neighbours of {@code pos} (just broken as {@code state} by {@code p}) in the plane of the face the player hit, if this is
	 * a dredging dig. Returns how many extra blocks broke.
	 */
	public static int dig(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
		if (!enabled || digging || p.isShiftKeyDown() || p.isCreative() || !state.is(MINEABLE) || !underwater(p)) return 0;
		digging = true;
		int n = 0;
		try {
			for (BlockPos b : plane(pos, face(p, pos))) {
				if (!(p.getMainHandItem().getItem() instanceof TidebrassDredgeItem)) break; // broke (or was swapped) mid-dig
				BlockState s = level.getBlockState(b);
				if (!s.is(MINEABLE) || !level.mayInteract(p, b)) continue;
				if (p.gameMode.destroyBlock(b)) {
					n++;
					// playerWillDestroy sends the break particles and sound to everyone except the digger (their client predicts its
					// own block); these blocks were not predicted, so the digger gets them here.
					p.connection.send(new ClientboundLevelEventPacket(BREAK_EVENT, b, Block.getId(s), false));
				}
			}
		} finally {
			digging = false;
		}
		return n;
	}

	/** The face of {@code pos} the player is digging: their look ray against the full block, or the side facing their eyes. */
	public static Direction face(Player p, BlockPos pos) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1.0F).scale(p.blockInteractionRange() + 1.0));
		BlockHitResult hit = Shapes.block().clip(eye, end, pos);
		if (hit != null) return hit.getDirection();
		Vec3 d = eye.subtract(Vec3.atCenterOf(pos));
		double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
		if (ay >= ax && ay >= az) return d.y >= 0 ? Direction.UP : Direction.DOWN;
		if (ax >= az) return d.x >= 0 ? Direction.EAST : Direction.WEST;
		return d.z >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The 8 neighbours of {@code pos} in the plane of {@code face}, top row first. */
	public static List<BlockPos> plane(BlockPos pos, Direction face) {
		List<BlockPos> out = new ArrayList<>(8);
		for (int a = 1; a >= -1; a--) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				out.add(switch (face.getAxis()) {
					case Y -> pos.offset(b, 0, a);
					case X -> pos.offset(0, a, b);
					case Z -> pos.offset(b, a, 0);
				});
			}
		}
		return out;
	}

	/** {@code -Delsewhere.overhaul3.<key>} wins, then {@code "<key>": bool} in config/elsewhere-overhaul3.json; default on (the 3.0 Overhaul3 convention). */
	static boolean readSwitch(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true; // a missing or malformed file keeps the default
		}
	}
}
