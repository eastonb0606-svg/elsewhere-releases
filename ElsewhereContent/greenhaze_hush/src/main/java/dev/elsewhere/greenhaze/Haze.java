package dev.elsewhere.greenhaze;

import dev.elsewhere.greenhaze_hush.Reg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.Codec;

/**
 * The Haze meter (0-100). With Green Leaf it mirrors Green Leaf's high and the haze feeds that high; without it,
 * the Greenhaze keeps its own value and turns it into mild Slowness/Nausea (DIMENSIONS.md 7).
 * In the Greenhaze the haze creeps up ~1% per 20 s, capped at High (60%) unless you smoke; Haze Vents and Hotbox Smog push
 * it faster; Lucid Lilies and Lucid Water bring it down. The full Frond set or the Hotbox Visor blocks unchosen contact highs.
 */
public final class Haze {
	private Haze() {}

	/** Synced to its owner for the HUD bar. */
	public static final AttachmentType<Float> HAZE = AttachmentRegistry.create(Reg.id("haze"),
		b -> b.persistent(Codec.FLOAT).initializer(() -> 0F).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	public static final String LOW = "Sober", BUZZED = "Buzzed", HIGH = "High", BAKED = "Baked", COUCH = "Couch-locked";
	private static final Map<UUID, Integer> NAUSEA_TIMER = new HashMap<>();
	private static int tick;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Haze::tick);
	}

	public static float get(ServerPlayer p) {
		if (GreenLeafBridge.usable()) {
			double h = GreenLeafBridge.high(p);
			if (h >= 0) return (float) h;
		}
		return p.getAttachedOrElse(HAZE, 0F);
	}

	public static String label(double v) {
		if (v < 10) return LOW;
		if (v < 35) return BUZZED;
		if (v < 60) return HIGH;
		if (v < 85) return BAKED;
		return COUCH;
	}

	/**
	 * Add haze. chosen=false means a contact high (blocked by Frond set / Hotbox Visor).
	 * Passive haze respects the 60% cap; chosen sources do not.
	 */
	public static void add(ServerPlayer p, double amount, boolean chosen) {
		if (amount > 0 && !chosen && GreenhazeItems.immuneToContactHigh(p)) return;
		if (GreenLeafBridge.usable() && GreenLeafBridge.addHigh(p, amount)) {
			sync(p);
			return;
		}
		float v = (float) Math.max(0, Math.min(100, p.getAttachedOrElse(HAZE, 0F) + amount));
		p.setAttached(HAZE, v);
	}

	/** Passive creep toward a cap (does nothing if already above the cap). */
	static void creep(ServerPlayer p, double amount, double cap) {
		if (GreenhazeItems.immuneToContactHigh(p)) return;
		float now = get(p);
		if (now >= cap) return;
		add(p, Math.min(amount, cap - now), false);
	}

	private static void sync(ServerPlayer p) {
		float v = get(p);
		Float old = p.getAttached(HAZE);
		if (old == null || Math.abs(old - v) >= 0.25F) p.setAttached(HAZE, v);
	}

	public static void set(ServerPlayer p, float v) {
		p.setAttached(HAZE, v);
	}

	private static void tick(MinecraftServer server) {
		tick++;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Math.floorMod(tick + p.getId(), 20) != 0 || p.isSpectator()) continue;
			ServerLevel level = p.level();
			boolean inHaze = level.dimension().equals(Greenhaze.LEVEL);
			if (inHaze) {
				Haze.creep(p, GearStats.HAZE_PASSIVE_PER_SECOND, GearStats.HAZE_PASSIVE_CAP);
				scanBlocks(p, level);
			}
			if (GreenLeafBridge.usable()) {
				sync(p);
			} else {
				// own meter: fades outside the Greenhaze (half-life ~4 min like Green Leaf), effects instead of Green Leaf's high
				float v = p.getAttachedOrElse(HAZE, 0F);
				if (!inHaze && v > 0) p.setAttached(HAZE, v < 0.2F ? 0F : v * 0.9971F);
				applyEffects(p, v);
			}
		}
	}

	private static void scanBlocks(ServerPlayer p, ServerLevel level) {
		BlockPos c = p.blockPosition();
		boolean vent = false, lily = false;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-3, -2, -3), c.offset(3, 1, 3))) {
			BlockState s = level.getBlockState(q);
			if (s.is(GreenhazeBlocks.HAZE_VENT)) vent = true;
			else if (s.is(GreenhazeBlocks.LUCID_LILY) && q.distSqr(c) <= 16) lily = true;
		}
		if (vent) add(p, GearStats.HAZE_VENT_PER_SECOND, false);
		if (lily) add(p, GearStats.HAZE_LILY_PER_SECOND, true);
	}

	/** Without Green Leaf: mild Slowness from High, Nausea pulses from Baked, Slowness II when Couch-locked. */
	public static void applyEffects(ServerPlayer p, float v) {
		if (v >= 35) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, v >= 85 ? 1 : 0, true, false, true));
		if (v >= 60) {
			int t = NAUSEA_TIMER.merge(p.getUUID(), 1, Integer::sum);
			// Nausea is a screen effect: skipped for players who turned Elsewhere screen effects off or chose reduce motion
			if (t % 30 == 0 && dev.elsewhere.greenhaze_hush.FxPrefs.motion(p)) p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false, true));
		}
	}

	public static void forgetPlayer(UUID id) { NAUSEA_TIMER.remove(id); }

	public static void clearAll() { NAUSEA_TIMER.clear(); }
}
