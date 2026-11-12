package dev.elsewhere.verdigris_sea;

import dev.elsewhere.boss.kit.KitStore;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_sea.mob.BarnacleGolem;
import dev.elsewhere.verdigris_sea.mob.ClockworkDrowned;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * new-event-thunder-tide: now and then the Verdigris Sea's storm surges for three minutes. Lightning strikes about four
 * times as often (and still charges Storm Coils), Clockwork Drowned and Barnacle Golems drop an extra Verdigris Gear,
 * and a full Tidebrass set (Sea Legs) is already immune to the bolts. The state is on disk (KitStore cooldowns), so a
 * restart neither loses a running tide nor re-arms the next one.
 */
public final class ThunderTide {
	private ThunderTide() {}

	public static final int DURATION = 3600, COOLDOWN = 24000;
	static final String ACTIVE = "thunder_tide_active", NEXT = "thunder_tide_next";

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> bonusDrop(entity));
	}

	/** The tide's extra Verdigris Gear for a dying Clockwork Drowned or Barnacle Golem; true if it dropped one. */
	public static boolean bonusDrop(net.minecraft.world.entity.LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || level.dimension() != Fx.VERDIGRIS_SEA || level.getServer() == null || !isActive(level.getServer())) return false;
		if (!(entity instanceof ClockworkDrowned || entity instanceof BarnacleGolem)) return false;
		level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(VerdigrisItems.VERDIGRIS_GEAR)));
		return true;
	}

	public static boolean isActive(MinecraftServer server) {
		return !KitStore.of(server).cooldownReady(ACTIVE);
	}

	public static int remaining(MinecraftServer server) {
		return KitStore.of(server).cooldownRemaining(ACTIVE);
	}

	/** Starts a tide now (ignores the cooldown; the next natural one waits {@link #COOLDOWN} after it ends). */
	public static void start(MinecraftServer server) {
		KitStore store = KitStore.of(server);
		store.cooldownStart(ACTIVE, DURATION);
		store.cooldownStart(NEXT, DURATION + COOLDOWN);
		VerdigrisSea.nextBolt = Math.min(VerdigrisSea.nextBolt, 60);
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA);
		if (sea != null) {
			for (ServerPlayer p : sea.players()) p.sendSystemMessage(Component.literal("A Thunder Tide rises. The storm is surging.").withStyle(ChatFormatting.AQUA));
		}
	}

	/** Called every tick: a natural tide may start (20% every 10 s once the cooldown has passed, only with a player in the sea). */
	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 200 != 11) return;
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA);
		if (sea == null || sea.players().isEmpty() || isActive(server)) return;
		if (KitStore.of(server).cooldownReady(NEXT) && sea.getRandom().nextFloat() < 0.2F) start(server);
	}

	/** Ticks between bolts: 400-1200 normally, 100-300 in a tide. */
	public static int boltInterval(ServerLevel sea) {
		boolean tide = sea.getServer() != null && isActive(sea.getServer());
		return tide ? 100 + sea.getRandom().nextInt(200) : 400 + sea.getRandom().nextInt(800);
	}
}
