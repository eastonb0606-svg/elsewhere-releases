package dev.elsewhere.petalwind;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.Reg;
import dev.elsewhere.stub.petalwind_glasswaste.SetBonus;
import dev.elsewhere.stub.petalwind_glasswaste.TripStub;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Petalwind rules: the void drop to the Overworld sky, mid-air jumps (Galeweave set, Petalstep Sandals, Zephyr Wings
 * boost), Petal Glider limits, and the two accidental-trip hooks (long falls, wind launches).
 */
public final class PetalwindRules {
	private PetalwindRules() {}

	private static final class State {
		int airJumps;
		boolean fallRolled;
		double lastVy;
		long lastLaunch;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	public static final Identifier GALEWEAVE_SET = Reg.id("galeweave");

	public static void init() {
		SetBonus.register(new SetBonus(GALEWEAVE_SET, Map.of(
			EquipmentSlot.HEAD, List.of(PetalwindItems.GALEWEAVE_HOOD), EquipmentSlot.CHEST, List.of(PetalwindItems.GALEWEAVE_ROBE),
			EquipmentSlot.LEGS, List.of(PetalwindItems.GALEWEAVE_LEGGINGS), EquipmentSlot.FEET, List.of(PetalwindItems.GALEWEAVE_BOOTS)),
			List.of(Component.translatable("set.elsewhere.galeweave.title"), Component.translatable("set.elsewhere.galeweave.jump"))) {
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Items.FIREWORK_ROCKET) && player.isFallFlying() && player.getItemBySlot(EquipmentSlot.CHEST).is(PetalwindItems.PETAL_GLIDER)) {
				if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("item.elsewhere.petal_glider.no_rockets"));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = STATES.computeIfAbsent(player.getUUID(), u -> new State());
			if (player.onGround() || player.isInWater() || player.isPassenger()) {
				s.airJumps = 0;
				s.fallRolled = false;
			}
			if (player.level().dimension() == PetalwindDimension.LEVEL && player.getY() < PetalwindDimension.VOID_Y) dropToOverworld(player);
			// Accidental trips (DIMENSIONS.md section 9): long falls and wind launches outside Petalwind.
			if (player.level().dimension() != PetalwindDimension.LEVEL) {
				if (!s.fallRolled && player.fallDistance >= 40 && !player.isFallFlying()) {
					s.fallRolled = true;
					TripStub.fire(PetalwindTrips.LONG_FALL, player);
				}
				Vec3 v = player.getDeltaMovement();
				if (v.y - s.lastVy > 0.8 && server.getTickCount() - s.lastLaunch > 40 && nearWind(player)) {
					s.lastLaunch = server.getTickCount();
					TripStub.fire(PetalwindTrips.WIND_LAUNCH, player);
				}
				s.lastVy = v.y;
			}
		}
	}

	private static boolean nearWind(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(6);
		return !player.level().getEntities(player, box, e -> e.getType() == EntityTypes.WIND_CHARGE || e.getType() == EntityTypes.BREEZE_WIND_CHARGE
			|| e.getType() == EntityTypes.BREEZE).isEmpty();
	}

	public static void dropToOverworld(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
		if (overworld == null) return;
		player.teleport(new TeleportTransition(overworld, new Vec3(player.getX(), PetalwindDimension.OVERWORLD_DROP_Y, player.getZ()),
			Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, PetalwindDimension.SLOW_FALL_TICKS, 0));
		player.resetFallDistance();
		player.sendOverlayMessage(Component.translatable("dimension.elsewhere.petalwind.fell"));
	}

	/** Server side of a mid-air jump request from the client. */
	public static void airJump(ServerPlayer player, boolean sneaking) {
		State s = STATES.computeIfAbsent(player.getUUID(), u -> new State());
		ServerLevel level = player.level();
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (player.isFallFlying()) {
			if (chest.is(PetalwindItems.ZEPHYR_WINGS) && !player.getCooldowns().isOnCooldown(chest) && spendWindCharge(player)) {
				Vec3 look = player.getLookAngle();
				player.setDeltaMovement(player.getDeltaMovement().add(look.scale(GearStats.ZEPHYR_WINGS_BOOST)));
				player.needsSync = true;
				player.getCooldowns().addCooldown(chest, GearStats.ZEPHYR_WINGS_BOOST_COOLDOWN);
				puff(level, player, true);
			}
			return;
		}
		if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying) return;
		boolean sandals = player.getItemBySlot(EquipmentSlot.FEET).is(PetalwindItems.PETALSTEP_SANDALS);
		if (sandals && sneaking && s.airJumps < 2 && spendWindCharge(player)) {
			jump(player, s);
			return;
		}
		if (SetBonus.has(player, GALEWEAVE_SET) && s.airJumps == 0) jump(player, s);
	}

	private static void jump(ServerPlayer player, State s) {
		s.airJumps++;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, GearStats.AIR_JUMP_VELOCITY, v.z);
		player.needsSync = true;
		player.resetFallDistance();
		puff(player.level(), player, false);
	}

	private static boolean spendWindCharge(Player player) {
		if (player.isCreative()) return true;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(Items.WIND_CHARGE)) {
				st.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static void puff(ServerLevel level, Player player, boolean big) {
		level.sendParticles(big ? ParticleTypes.GUST : ParticleTypes.SMALL_GUST, player.getX(), player.getY(), player.getZ(), big ? 2 : 6, 0.3, 0.1, 0.3, 0.02);
		level.sendParticles(ParticleTypes.CHERRY_LEAVES, player.getX(), player.getY(), player.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.3F);
	}
}
