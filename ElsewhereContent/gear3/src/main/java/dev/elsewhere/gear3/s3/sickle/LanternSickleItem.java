package dev.elsewhere.gear3.s3.sickle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/**
 * Lantern Sickle (GEAR.md A5 "Shared materials and tools", G2, source Mirewick's Scrip-Desk): "hoe: harvest and replant 3x3 crops;
 * cuts leaves and vines 3x3". A diamond-class hoe (catalogue kind "hoe", mechanic "lantern_sickle"); everything a vanilla hoe does
 * (tilling through the hoe block transformer, fast leaves, hoe enchantments through {@code #minecraft:hoes}) still works.
 * <ul>
 * <li><b>Harvest:</b> using it on any crop ({@link CropBlock} family or nether wart) harvests every <i>ripe</i> crop in the 3x3 around
 * it on that level and replants each at age 0. One seed per crop comes out of that crop's own drops (or, failing that, the holder's
 * inventory; a crop that cannot be replanted is left standing, so farmland is never left bare). The drops are rolled with the sickle
 * as the tool (Fortune applies) and the rest falls where the crop stood. One sweep costs 1 durability. Every crop goes through
 * Fabric's block-break events, spawn protection and adventure-mode rules, like a block the player broke.</li>
 * <li><b>Cutting:</b> breaking a block in {@link #CUTS} ({@code #elsewhere:mirewick_lantern_sickle_cuts}: leaves and vines) cuts the
 * 8 neighbours in the plane of the face hit that are also in the tag. Each goes through {@code ServerPlayerGameMode.destroyBlock}:
 * vanilla drops for the sickle, 1 durability each (Unbreaking applies), block-break events, protection. Sneaking cuts one block.
 * Vines mine at the sickle's leaf speed. Cutting stops the moment the sickle breaks.</li>
 * </ul>
 * Lag: nothing ticks and no event is registered; both run inside this item's own use / mine calls, server side.
 * Kill switch: key {@value #KILL_KEY} in {@code config/elsewhere-overhaul3.json} or {@code -Delsewhere.overhaul3.lantern_sickle=false}
 * (read once): off = a plain diamond-class hoe, the item stays registered.
 */
public class LanternSickleItem extends Item {
	public static final String ID = "mirewick_lantern_sickle";
	public static final String KILL_KEY = "lantern_sickle";
	/** Leaves and vines the sickle cuts 3x3 (data/elsewhere/tags/block/mirewick_lantern_sickle_cuts.json; datapacks may add to it). */
	public static final TagKey<Block> CUTS = TagKey.create(Registries.BLOCK, Gear3Content.id("mirewick_lantern_sickle_cuts"));
	/** Mining speed on {@link #CUTS} (the diamond hoe's speed on leaves), so vines cut as fast as leaves. */
	public static final float CUT_SPEED = 8.0F;
	/** Durability per harvest sweep, however many crops it takes. */
	public static final int HARVEST_COST = 1;
	/** Level event 2001: block break particles and sound (what the client plays for a block it broke itself). */
	public static final int BREAK_EVENT = LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK;
	/** Kill switch, read once (the self-test may flip it and puts it back). */
	public static boolean enabled = readSwitch(KILL_KEY);
	/** True while the extra blocks of one cut are being broken (their own mineBlock calls must not cut again). Server thread only. */
	private static boolean cutting;

	public LanternSickleItem(Properties props) {
		super(props);
	}

	// ------------------------------------------------------------------ harvest (use on a crop)

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		if (!enabled || !isCrop(level.getBlockState(pos))) return super.useOn(ctx); // a hoe: till dirt and the like
		if (!(level instanceof ServerLevel sl)) return anyRipe(level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		Player p = ctx.getPlayer();
		int n = harvest(sl, p, pos, ctx.getItemInHand());
		if (n == 0) return InteractionResult.PASS;
		if (p != null) ctx.getItemInHand().hurtAndBreak(HARVEST_COST, p, ctx.getHand());
		return InteractionResult.SUCCESS;
	}

	/** A crop the sickle harvests when ripe: the {@link CropBlock} family (wheat, carrots, potatoes, beetroots, mod crops) and nether wart. */
	public static boolean isCrop(BlockState s) {
		return s.getBlock() instanceof CropBlock || (s.getBlock() instanceof NetherWartBlock && s.hasProperty(NetherWartBlock.AGE));
	}

	/** Fully grown (torchflower and pitcher crops never are: they turn into their flower instead). */
	public static boolean isRipe(BlockState s) {
		if (s.getBlock() instanceof CropBlock c) return c.isMaxAge(s);
		return s.getBlock() instanceof NetherWartBlock && s.hasProperty(NetherWartBlock.AGE) && s.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
	}

	/** The freshly planted state of a ripe crop: the same block at age 0. */
	public static BlockState replanted(BlockState s) {
		if (s.getBlock() instanceof CropBlock c) return c.getStateForAge(0);
		return s.setValue(NetherWartBlock.AGE, 0);
	}

	/** Client prediction: is there anything ripe in the 3x3 around {@code center}? */
	public static boolean anyRipe(Level level, BlockPos center) {
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (isRipe(level.getBlockState(center.offset(dx, 0, dz)))) return true;
		return false;
	}

	/** Harvests and replants every ripe crop in the 3x3 around {@code center} (same y). Returns how many were harvested. */
	public static int harvest(ServerLevel level, @Nullable Player p, BlockPos center, ItemStack tool) {
		int n = 0;
		BlockState first = null;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos q = center.offset(dx, 0, dz);
				BlockState s = level.getBlockState(q);
				if (!isRipe(s) || (p != null && !mayBreak(level, p, q, s))) continue;
				if (harvestOne(level, p, q, s, tool)) {
					n++;
					if (first == null) first = s;
				}
			}
		}
		if (n > 0) {
			Vec3 c = Vec3.atCenterOf(center);
			level.playSound(null, center, first.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
			level.playSound(null, center, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.3F);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (p != null) p.causeFoodExhaustion(0.005F * n);
		}
		return n;
	}

	/** The checks a player's own block break passes: spawn protection and world border, adventure mode, Fabric's BEFORE event (claims). */
	static boolean mayBreak(ServerLevel level, Player p, BlockPos q, BlockState s) {
		if (!level.mayInteract(p, q)) return false;
		if (p instanceof ServerPlayer sp && sp.blockActionRestricted(level, q, sp.gameMode.getGameModeForPlayer())) return false;
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, p, q, s, null);
	}

	static boolean harvestOne(ServerLevel level, @Nullable Player p, BlockPos q, BlockState s, ItemStack tool) {
		Item seed = s.getBlock().asItem();
		if (seed == Items.AIR) return false;
		List<ItemStack> drops = Block.getDrops(s, level, q, null, p, tool);
		boolean planted = p != null && p.getAbilities().instabuild; // creative: replant for free
		for (ItemStack d : drops) {
			if (!planted && d.is(seed)) {
				d.shrink(1);
				planted = true;
			}
		}
		if (!planted && p != null) planted = takeOne(p, seed);
		if (!planted) return false; // nothing to replant with: leave the crop standing
		BlockState young = replanted(s);
		level.setBlock(q, young, Block.UPDATE_ALL);
		level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, q, Block.getId(s));
		level.gameEvent(GameEvent.BLOCK_DESTROY, q, GameEvent.Context.of(p, s));
		level.gameEvent(GameEvent.BLOCK_PLACE, q, GameEvent.Context.of(p, young));
		if (p == null || !p.preventsBlockDrops()) for (ItemStack d : drops) if (!d.isEmpty()) Block.popResource(level, q, d);
		s.spawnAfterBreak(level, q, tool, true);
		if (p != null) PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, p, q, s, null);
		return true;
	}

	/** Takes one {@code seed} from the player's inventory; false when they have none. */
	public static boolean takeOne(Player p, Item seed) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && st.is(seed)) {
				st.shrink(1);
				inv.setChanged();
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ cutting (break a leaf or vine)

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		boolean used = super.mineBlock(stack, level, state, pos, miner);
		if (!cutting && level instanceof ServerLevel sl && miner instanceof ServerPlayer p) cut(sl, p, pos, state);
		return used;
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		float speed = super.getDestroySpeed(stack, state);
		return enabled && state.is(CUTS) ? Math.max(speed, CUT_SPEED) : speed;
	}

	/**
	 * Cuts the 8 neighbours of {@code pos} (just broken as {@code state} by {@code p}) in the plane of the face the player hit, if this is a
	 * cutting break. Returns how many extra blocks were cut.
	 */
	public static int cut(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
		if (!enabled || cutting || p.isShiftKeyDown() || p.isCreative() || !state.is(CUTS)) return 0;
		cutting = true;
		int n = 0;
		try {
			for (BlockPos b : plane(pos, face(p, pos))) {
				if (!(p.getMainHandItem().getItem() instanceof LanternSickleItem)) break; // broke (or was swapped) mid-cut
				BlockState s = level.getBlockState(b);
				if (!s.is(CUTS) || !level.mayInteract(p, b)) continue;
				if (p.gameMode.destroyBlock(b)) {
					n++;
					// playerWillDestroy sends the break particles and sound to everyone except the cutter (their client predicts its own
					// block); these blocks were not predicted, so the cutter gets them here.
					p.connection.send(new ClientboundLevelEventPacket(BREAK_EVENT, b, Block.getId(s), false));
				}
			}
		} finally {
			cutting = false;
		}
		return n;
	}

	/** The face of {@code pos} the player is cutting: their look ray against the full block, or the side facing their eyes. */
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
			Path f = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(f)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true; // a missing or malformed file keeps the default
		}
	}
}
