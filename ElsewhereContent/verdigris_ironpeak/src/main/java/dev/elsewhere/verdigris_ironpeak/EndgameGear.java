package dev.elsewhere.verdigris_ironpeak;

import dev.elsewhere.ironpeak.IceAxeItem;
import dev.elsewhere.ironpeak.Ironpeak;
import dev.elsewhere.ironpeak.IronpeakItems;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import dev.elsewhere.verdigris_sea.VerdigrisSea;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The 2.0 endgame lines of Verdigris and Ironpeak (BALANCE N1/G9): Tidebrass perks, Tidesteel wet bonus, the Climber
 * Mountain Goat cling, Skyiron Quarry and felling axe, and the enchantment cons ("can't take Fortune / Feather Falling
 * / Respiration ..."). Per-piece attribute perks live on the item definitions; the code-only ones are here.
 */
public final class EndgameGear {
	private EndgameGear() {}

	private static boolean inQuarry;
	private static final Map<UUID, Long> GOAT_READY = new HashMap<>();
	private static boolean inGuard;
	private static final net.minecraft.resources.Identifier LAND_SLOW = Reg.id("tidebrass_land_slow");
	private static final net.minecraft.resources.Identifier WET_BONUS = Reg.id("tidesteel_wet");

	public static void init() {
		EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, target, context) -> banned(enchantment, target) ? TriState.FALSE : TriState.DEFAULT);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) afterBreak(sl, sp, pos, state);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (inGuard || !(entity instanceof ServerPlayer p) || !p.getItemBySlot(EquipmentSlot.CHEST).is(VerdigrisItems.DIVING_SUIT_CHESTPLATE)) return true;
			if (!(source.getEntity() instanceof Guardian) && !(source.getEntity() instanceof Drowned)) return true;
			// Chest perk: guardian and drowned damage -15% (cancel the hit and re-apply it reduced).
			inGuard = true;
			try {
				p.hurtServer(p.level(), source, (float) (amount * (1 - GearStats.TIDEBRASS_GUARD_REDUCTION)));
			} finally {
				inGuard = false;
			}
			return false;
		});
	}

	// ---- enchantment cons --------------------------------------------------------------------------------------
	/** Items that refuse an enchantment (BALANCE N1 / the Skyiron line). */
	public static boolean banned(Holder<net.minecraft.world.item.enchantment.Enchantment> e, ItemStack s) {
		var key = e.unwrapKey().orElse(null);
		if (key == null) return false;
		if (s.is(IronpeakItems.SKYIRON_PICKAXE) || s.is(IronpeakItems.SKYIRON_SHOVEL)) return key.equals(Enchantments.FORTUNE);
		if (s.is(IronpeakItems.CLIMBER_BOOTS)) return key.equals(Enchantments.FEATHER_FALLING);
		if (s.is(VerdigrisItems.DIVING_SUIT_HELMET)) return key.equals(Enchantments.RESPIRATION) || key.equals(Enchantments.AQUA_AFFINITY);
		if (s.is(VerdigrisItems.DIVING_SUIT_BOOTS)) return key.equals(Enchantments.DEPTH_STRIDER);
		return false;
	}

	// ---- per-second perks --------------------------------------------------------------------------------------
	public static int tidebrassPieces(LivingEntity e) {
		int n = 0;
		if (e.getItemBySlot(EquipmentSlot.HEAD).is(VerdigrisItems.DIVING_SUIT_HELMET)) n++;
		if (e.getItemBySlot(EquipmentSlot.CHEST).is(VerdigrisItems.DIVING_SUIT_CHESTPLATE)) n++;
		if (e.getItemBySlot(EquipmentSlot.LEGS).is(VerdigrisItems.DIVING_SUIT_LEGGINGS)) n++;
		if (e.getItemBySlot(EquipmentSlot.FEET).is(VerdigrisItems.DIVING_SUIT_BOOTS)) n++;
		return n;
	}

	private static void setModifier(ServerPlayer p, Holder<Attribute> attr, net.minecraft.resources.Identifier id, double amount, AttributeModifier.Operation op, boolean on) {
		AttributeInstance a = p.getAttribute(attr);
		if (a == null) return;
		AttributeModifier cur = a.getModifier(id);
		if (!on) {
			if (cur != null) a.removeModifier(id);
		} else if (cur == null || Math.abs(cur.amount() - amount) > 1e-6) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Wet = in water or rain (the Tidesteel sword and axe get +2 damage). */
	public static boolean wet(LivingEntity e) {
		return e.isInWater() || e.level().isRainingAt(e.blockPosition());
	}

	public static void serverTick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p, wet(p));
	}

	/** One player's code-only perks ({@code wet} = in water or rain). */
	public static void tickPlayer(ServerPlayer p, boolean wet) {
		int pieces = tidebrassPieces(p);
		setModifier(p, Attributes.MOVEMENT_SPEED, LAND_SLOW, GearStats.TIDEBRASS_LAND_SLOW * pieces, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, pieces > 0 && !p.isInWater());
		Item held = p.getMainHandItem().getItem();
		boolean blade = held == VerdigrisItems.TIDESTEEL_SWORD || held == VerdigrisItems.TIDESTEEL_AXE;
		setModifier(p, Attributes.ATTACK_DAMAGE, WET_BONUS, GearStats.TIDESTEEL_WET_BONUS, AttributeModifier.Operation.ADD_VALUE, blade && wet);
		mountainGoat(p);
	}

	/** Mountain Goat: with the full Climber set, jumping into a wall mid-air clings for 3 s (8 s cooldown). */
	public static void mountainGoat(ServerPlayer p) {
		if (!Ironpeak.MOUNTAIN_GOAT.isActive(p)) return;
		if (p.onGround() || !p.horizontalCollision || !p.getLastClientInput().jump() || IceAxeItem.CLINGING.containsKey(p.getUUID())) return;
		long now = p.level().getGameTime();
		if (GOAT_READY.getOrDefault(p.getUUID(), 0L) > now) return;
		startGoatCling(p);
	}

	public static void startGoatCling(ServerPlayer p) {
		GOAT_READY.put(p.getUUID(), p.level().getGameTime() + GearStats.GOAT_CLING_COOLDOWN);
		IceAxeItem.CLINGING.put(p.getUUID(), GearStats.GOAT_CLING_TICKS);
		p.setDeltaMovement(0, 0, 0);
		p.needsSync = true;
		p.resetFallDistance();
	}

	public static long goatReadyAt(ServerPlayer p) {
		return GOAT_READY.getOrDefault(p.getUUID(), 0L);
	}

	// ---- Skyiron Quarry and felling ----------------------------------------------------------------------------
	public static boolean isQuarryPick(Item i) {
		return i == IronpeakItems.SKYIRON_PICKAXE;
	}

	/** Pickaxe Quarry rule: stone-type blocks (pickaxe-mineable), hardness 3 or less, no ores, no containers. */
	public static boolean quarryPickBlock(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || !s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return false;
		if (level.getBlockEntity(pos) != null) return false;
		float h = s.getDestroySpeed(level, pos);
		if (h < 0 || h > GearStats.QUARRY_MAX_HARDNESS) return false;
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return !(path.endsWith("_ore") || path.contains("ancient_debris"));
	}

	public static boolean quarryShovelBlock(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY)
			|| s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.MUD);
	}

	/** The face the player is mining (from the look ray), or UP when nothing is hit. */
	static Direction faceOf(ServerPlayer p) {
		HitResult hit = p.pick(p.blockInteractionRange() + 1, 1.0F, false);
		return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getDirection() : Direction.UP;
	}

	/** The 8 neighbours of {@code pos} in the plane of {@code face}. */
	public static List<BlockPos> faceNeighbours(BlockPos pos, Direction face) {
		List<BlockPos> out = new ArrayList<>();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				out.add(switch (face.getAxis()) {
					case Y -> pos.offset(a, 0, b);
					case X -> pos.offset(0, a, b);
					case Z -> pos.offset(a, b, 0);
				});
			}
		}
		return out;
	}

	private static void afterBreak(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
		if (inQuarry || p.isCreative() || p.isShiftKeyDown() || pos.getY() <= GearStats.QUARRY_MIN_Y) return;
		ItemStack tool = p.getMainHandItem();
		boolean pick = tool.is(IronpeakItems.SKYIRON_PICKAXE), shovel = tool.is(IronpeakItems.SKYIRON_SHOVEL), axe = tool.is(IronpeakItems.SKYIRON_AXE);
		if (!pick && !shovel && !axe) return;
		inQuarry = true;
		try {
			if (axe) {
				if (state.is(BlockTags.LOGS)) fell(level, p, pos);
				return;
			}
			if (pick ? !state.is(BlockTags.MINEABLE_WITH_PICKAXE) : !quarryShovelBlock(state)) return;
			quarry(level, p, pos, faceOf(p));
		} finally {
			inQuarry = false;
		}
	}

	/** Breaks the 8 neighbours of {@code pos} in the plane of {@code face} that the held Skyiron pickaxe or shovel may quarry; returns how many broke. */
	public static int quarry(ServerLevel level, ServerPlayer p, BlockPos pos, Direction face) {
		if (pos.getY() <= GearStats.QUARRY_MIN_Y) return 0;
		boolean pick = p.getMainHandItem().is(IronpeakItems.SKYIRON_PICKAXE);
		if (!pick && !p.getMainHandItem().is(IronpeakItems.SKYIRON_SHOVEL)) return 0;
		boolean was = inQuarry;
		inQuarry = true;
		int n = 0;
		try {
			for (BlockPos b : faceNeighbours(pos, face)) {
				if (p.getMainHandItem().isEmpty()) break;
				BlockState s = level.getBlockState(b);
				if ((pick ? quarryPickBlock(level, b, s) : quarryShovelBlock(s)) && p.gameMode.destroyBlock(b)) n++;
			}
		} finally {
			inQuarry = was;
		}
		return n;
	}

	/** Felling axe: breaks up to 24 more connected logs (26-neighbourhood) above Y 63. */
	public static int fell(ServerLevel level, ServerPlayer p, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		List<BlockPos> logs = new ArrayList<>();
		while (!queue.isEmpty() && logs.size() < GearStats.FELL_MAX_LOGS) {
			BlockPos c = queue.poll();
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				BlockPos n = c.offset(dx, dy, dz);
				if (n.getY() <= GearStats.QUARRY_MIN_Y || !seen.add(n) || !level.getBlockState(n).is(BlockTags.LOGS)) continue;
				if (logs.size() >= GearStats.FELL_MAX_LOGS) break;
				logs.add(n);
				queue.add(n);
			}
		}
		int n = 0;
		for (BlockPos l : logs) {
			if (p.getMainHandItem().isEmpty()) break;
			if (p.gameMode.destroyBlock(l)) n++;
		}
		return n;
	}
}
