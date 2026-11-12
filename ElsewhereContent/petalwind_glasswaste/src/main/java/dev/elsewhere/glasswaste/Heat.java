package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.Net;
import dev.elsewhere.petalwind_glasswaste.Reg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Glasswaste rules: the Heat meter (builds in direct sun, drops in shade, water and with Cactus Water; at 100 you get
 * Nausea and Slowness, then fire ticks), Glare (standing on glass under the sun without a helmet blinds in pulses) and
 * the Sunwright Visor's sunlight attack-speed bonus.
 */
public final class Heat {
	private Heat() {}

	public static final float MAX = 100.0F;
	public static final float SUN_GAIN_PER_SECOND = 0.9F;     // about 110 s of direct sun from 0 to 100
	public static final float SHADE_LOSS_PER_SECOND = 2.5F;
	public static final float WATER_LOSS_PER_SECOND = 12.0F;
	public static final float WATER_BOTTLE_COOLING = 40.0F;
	public static final int OVERHEAT_FIRE_DELAY = 100;        // ticks at 100 before fire ticks start
	public static final int GLARE_PERIOD = 80, GLARE_BLIND_TICKS = 30;
	public static final TagKey<Block> GLARE_BLOCKS = TagKey.create(Registries.BLOCK, Reg.id("glasswaste_glare_blocks"));
	private static final net.minecraft.resources.Identifier VISOR_ID = Reg.id("sunwright_visor_sun");

	private static final class State {
		float heat;
		int atMax;
		int lastSent = -1;
		boolean shown;
		long boostUntil;
		boolean glare, glareSent;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	public static boolean isGlasswaste(Level level) {
		return level.dimension() == GlasswasteDimension.LEVEL;
	}

	/** True where the sun is up: always in the Glasswaste, during the day elsewhere. */
	public static boolean isSunlit(ServerLevel level) {
		return isGlasswaste(level) || level.isBrightOutside();
	}

	public static boolean inDirectSun(ServerLevel level, LivingEntity e) {
		return isSunlit(level) && !level.isRainingAt(e.blockPosition().above()) && level.canSeeSky(BlockPos.containing(e.getEyePosition()));
	}

	public static float get(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s == null ? 0 : s.heat;
	}

	public static void set(ServerPlayer player, float value) {
		STATES.computeIfAbsent(player.getUUID(), u -> new State()).heat = Math.max(0, Math.min(MAX, value));
	}

	public static void add(ServerPlayer player, float delta) {
		set(player, get(player) + delta);
	}

	/** Heliodor's Overheat phase: Heat rises twice as fast for this player for a while. */
	public static void boost(ServerPlayer player, int ticks) {
		STATES.computeIfAbsent(player.getUUID(), u -> new State()).boostUntil = player.level().getGameTime() + ticks;
	}

	public static double heatGainMultiplier(ServerPlayer player) {
		if (player.getItemBySlot(EquipmentSlot.CHEST).is(GlasswasteItems.SUNWRIGHT_PLATE) || SunwrightPerks.noonfire(player)) return 0.0; // Plate: Heat immunity; Noonfire: Heat never builds
		return Math.max(0.0, 1.0 - GearStats.SUNWRIGHT_HEAT_REDUCTION_PER_PIECE * GlasswasteItems.sunwrightPieces(player));
	}

	public static boolean glareImmune(ServerPlayer player) {
		return SunwrightPerks.sunwrightHelm(player); // Helm and Visor (BALANCE: Glare and Blindness immunity)
	}

	/** Called every server tick. */
	public static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((t + player.getId()) % 5 != 0) continue;
			step(player, 0.25F);
		}
	}

	/** One update of {@code seconds} for a player (public so the self-test can drive it). */
	public static void step(ServerPlayer player, float seconds) {
		ServerLevel level = player.level();
		State s = STATES.computeIfAbsent(player.getUUID(), u -> new State());
		boolean inGlasswaste = isGlasswaste(level);
		visor(player, level);
		if (!inGlasswaste) {
			s.heat = Math.max(0, s.heat - WATER_LOSS_PER_SECOND * seconds);
			s.atMax = 0;
			sync(player, s, false);
			return;
		}
		if (player.isCreative() || player.isSpectator()) {
			s.heat = 0;
		} else if (player.isInWaterOrRain()) {
			s.heat -= WATER_LOSS_PER_SECOND * seconds;
		} else if (inDirectSun(level, player)) {
			double mult = heatGainMultiplier(player) * (level.getGameTime() < s.boostUntil ? 2.0 : 1.0);
			s.heat += (float) (SUN_GAIN_PER_SECOND * mult * seconds);
		} else {
			s.heat -= SHADE_LOSS_PER_SECOND * seconds;
		}
		s.heat = Math.max(0, Math.min(MAX, s.heat));
		if (s.heat >= MAX) {
			s.atMax += (int) (seconds * 20);
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false, true));
			if (s.atMax >= OVERHEAT_FIRE_DELAY && !player.isOnFire()) player.igniteForSeconds(2.0F);
		} else {
			s.atMax = 0;
		}
		s.glare = glare(player, level);
		sync(player, s, true);
	}

	private static boolean glare(ServerPlayer player, ServerLevel level) {
		if (glareImmune(player)) return false;
		boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		boolean bossGlare = dev.elsewhere.stub.petalwind_glasswaste.BossFightRegistry.inFight(player);
		if (helmet && !bossGlare) return false;
		if (!level.getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).is(GLARE_BLOCKS)) return false;
		if (!inDirectSun(level, player)) return false;
		if (level.getGameTime() % GLARE_PERIOD < 5) player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, GLARE_BLIND_TICKS, 0, true, false, true));
		return true;
	}

	private static void visor(ServerPlayer player, ServerLevel level) {
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (speed == null) return;
		boolean on = player.getItemBySlot(EquipmentSlot.HEAD).is(GlasswasteItems.SUNWRIGHT_VISOR) && inDirectSun(level, player);
		boolean has = speed.hasModifier(VISOR_ID);
		if (on && !has) speed.addTransientModifier(new AttributeModifier(VISOR_ID, GearStats.SUNWRIGHT_VISOR_SUN_ATTACK_SPEED,
			AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		else if (!on && has) speed.removeModifier(VISOR_ID);
	}

	private static void sync(ServerPlayer player, State s, boolean show) {
		int v = Math.round(s.heat);
		if (v == s.lastSent && show == s.shown && s.glare == s.glareSent) return;
		s.lastSent = v;
		s.shown = show;
		s.glareSent = s.glare;
		if (ServerPlayNetworking.canSend(player, Net.HeatPayload.TYPE)) ServerPlayNetworking.send(player, new Net.HeatPayload(v, show, s.glare));
	}

	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}
}
