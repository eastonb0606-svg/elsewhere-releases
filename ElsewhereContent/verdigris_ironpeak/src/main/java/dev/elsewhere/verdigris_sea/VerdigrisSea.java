package dev.elsewhere.verdigris_sea;

import dev.elsewhere.stub.verdigris_ironpeak.SetBonus;
import dev.elsewhere.stub.verdigris_ironpeak.TripTrigger;
import dev.elsewhere.stub.verdigris_ironpeak.Trips;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.Reg;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Verdigris Sea's rules: a perpetual local storm (forced rain and thunder, re-synced to its players), lightning every
 * 20-60 s near players that prefers copper and lightning rods, Storm Coil charging, the Diving Suit set bonus, the
 * Verdigris Mitre, the Engineer's Lantern, and the accidental-trip trigger.
 */
public final class VerdigrisSea {
	public static final ResourceKey<Level> DIM = Fx.VERDIGRIS_SEA;
	static int nextBolt = 400;

	/** Each minute of an Overworld thunderstorm while in water or within 6 blocks of copper: 2.5% (DIMENSIONS.md section 9). */
	public static final TripTrigger STORM_MINUTE = new TripTrigger() {
		public String id() {
			return "verdigris_sea.storm_minute";
		}

		public ResourceKey<Level> target() {
			return DIM;
		}

		public Kind kind() {
			return Kind.LOCATION;
		}

		public double baseChance(ServerPlayer player) {
			return 0.20; // BALANCE.md final
		}
	};

	public static final SetBonus DIVING_SUIT = new SetBonus("diving_suit", Map.of(
		EquipmentSlot.HEAD, VerdigrisItems.DIVING_SUIT_HELMET, EquipmentSlot.CHEST, VerdigrisItems.DIVING_SUIT_CHESTPLATE,
		EquipmentSlot.LEGS, VerdigrisItems.DIVING_SUIT_LEGGINGS, EquipmentSlot.FEET, VerdigrisItems.DIVING_SUIT_BOOTS)) {
		private final AttributeModifier walk = new AttributeModifier(Reg.id("diving_suit_set_walk"), GearStats.TIDEBRASS_SET_WATER_EFF, AttributeModifier.Operation.ADD_VALUE);

		private final AttributeModifier mine = new AttributeModifier(Reg.id("sea_legs_mining"), 0.8, AttributeModifier.Operation.ADD_VALUE);

		public void apply(ServerPlayer p) {
			var a = p.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
			if (a != null && !a.hasModifier(walk.id())) a.addTransientModifier(walk);
			var m = p.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
			if (m != null && !m.hasModifier(mine.id())) m.addTransientModifier(mine);
		}

		public void remove(ServerPlayer p) {
			var a = p.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
			if (a != null) a.removeModifier(walk.id());
			var m = p.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
			if (m != null) m.removeModifier(mine.id());
		}

		public void tick(ServerPlayer p) {
			p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 220, 0, true, false, true));
		}

		public List<Component> tooltip() {
			return List.of(Component.translatable("set.elsewhere.diving_suit").withStyle(ChatFormatting.GOLD),
				Component.translatable("set.elsewhere.diving_suit.1").withStyle(ChatFormatting.BLUE),
				Component.translatable("set.elsewhere.diving_suit.2").withStyle(ChatFormatting.BLUE),
				Component.translatable("set.elsewhere.diving_suit.3").withStyle(ChatFormatting.BLUE));
		}
	}.alternate(EquipmentSlot.HEAD, VerdigrisItems.VERDIGRIS_MITRE);

	private VerdigrisSea() {}

	public static void serverTick(MinecraftServer server) {
		ServerLevel sea = server.getLevel(DIM);
		if (sea != null) tickStorm(sea);
		ThunderTide.serverTick(server);
		int t = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getItemBySlot(EquipmentSlot.FEET).is(VerdigrisItems.DIVING_SUIT_BOOTS) && p.isInWater() && !p.getLastClientInput().jump()) {
				// Sea-floor walking: sink instead of floating.
				p.setDeltaMovement(p.getDeltaMovement().add(0, -0.03, 0));
				p.needsSync = true;
			}
			if ((t + p.getId()) % 40 == 0 && p.getItemBySlot(EquipmentSlot.HEAD).is(VerdigrisItems.VERDIGRIS_MITRE) && nearCopper(p.level(), p.blockPosition(), GearStats.MITRE_COPPER_RADIUS)) {
				p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100, 0, true, true, true));
			}
			if ((t + p.getId()) % 1200 == 0 && p.level().dimension() == Level.OVERWORLD && p.level().isThundering() && p.level().isRainingAt(p.blockPosition())
				&& (p.isInWater() || nearCopper(p.level(), p.blockPosition(), 6))) {
				Trips.fire(p, STORM_MINUTE);
			}
		}
	}

	private static void tickStorm(ServerLevel sea) {
		sea.setRainLevel(1.0F);
		sea.setThunderLevel(1.0F);
		if (sea.getGameTime() % 40 == 0) {
			for (ServerPlayer p : sea.players()) {
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 1.0F));
				p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 1.0F));
			}
		}
		if (sea.players().isEmpty()) return;
		if (--nextBolt > 0) return;
		nextBolt = ThunderTide.boltInterval(sea); // every 20-60 s; 5-15 s in a Thunder Tide
		ServerPlayer p = sea.players().get(sea.getRandom().nextInt(sea.players().size()));
		BlockPos strike = pickStrike(sea, p.blockPosition());
		Fx.lightning(sea, Vec3.atBottomCenterOf(strike), false, null);
	}

	/** Picks a spot within 48 blocks of the player, preferring the tallest copper or lightning rod among 24 samples. */
	public static BlockPos pickStrike(ServerLevel level, BlockPos near) {
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 24; i++) {
			int x = near.getX() + level.getRandom().nextInt(97) - 48, z = near.getZ() + level.getRandom().nextInt(97) - 48;
			if (!level.hasChunk(x >> 4, z >> 4)) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockState top = level.getBlockState(new BlockPos(x, y - 1, z));
			int score = y + (top.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock ? 200 : isCopper(top) ? 100 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = new BlockPos(x, y, z);
			}
		}
		return best == null ? near : best;
	}

	public static boolean isCopper(BlockState s) {
		String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return path.contains("copper") || path.contains("verdigris") || s.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock || s.is(VerdigrisBlocks.STORM_COIL);
	}

	public static boolean nearCopper(Level level, BlockPos c, int r) {
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (isCopper(level.getBlockState(p))) return true;
		}
		return false;
	}

	/** Every bolt (natural or not) charges Storm Coils within 8 blocks. */
	public static void onLightning(ServerLevel level, LightningBolt bolt) {
		BlockPos c = bolt.blockPosition();
		int r = StormCoilBlock.RANGE;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			BlockState s = level.getBlockState(p);
			if (s.is(VerdigrisBlocks.STORM_COIL)) StormCoilBlock.charge(level, p.immutable(), s);
		}
	}

	/** Engineer's Lantern in the offhand: lightning can't hurt you. */
	public static boolean allowDamage(LivingEntity e, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT) && e.getItemBySlot(EquipmentSlot.OFFHAND).is(VerdigrisItems.ENGINEERS_LANTERN)) return false;
		if (source.is(DamageTypes.LIGHTNING_BOLT) && e instanceof ServerPlayer p && DIVING_SUIT.isActive(p)) return false; // Sea Legs
		return true;
	}

	public static boolean isStormWater(Entity e) {
		return e.level().dimension() == DIM && e.isInWater();
	}
}
