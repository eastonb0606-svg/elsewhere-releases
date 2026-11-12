package dev.elsewhere.arsenal.compat;

import dev.elsewhere.stub.arsenal.BossCheck;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Elsewhere bosses count as bosses for Test Meter's combat factor (3.0, s-compat-testmeter; kill switch {@code testmeter_compat}).
 *
 * <p>Test Meter scores a kill at 12 points for a boss but only knows the Ender Dragon, the Wither and the Warden; every other hostile
 * mob scores 0.5 to 3 by health. So killing Aphelion or a rival Sultana scored like a tough zombie. On a boss death this adds the
 * difference to Test Meter's own kill points for the killer (a minion's or a spell's kill counts for its owner, as in Test Meter).
 * Bosses: {@code #elsewhere:bosses}, {@code #c:bosses}, every human kingdom boss ({@code HumanBoss}) and every court ruler.
 * One event per death, nothing ticks; a no-op without Test Meter or if its internals change.
 */
public final class TestMeterKills {
	private TestMeterKills() {}

	public static final double BOSS_POINTS = 12.0;
	private static Method data, killPoints;
	private static Field pending;
	private static Class<?> humanBoss, courtRuler;
	private static boolean looked, done;

	public static void init() {
		if (done || !TestMeterCompat.ENABLED || !FabricLoader.getInstance().isModLoaded("testmeter")) return;
		done = true;
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && isBoss(entity)) credit(p, entity);
		});
	}

	/** Elsewhere's boss rule for Test Meter kill credit. */
	public static boolean isBoss(Entity e) {
		if (BossCheck.isBoss(e)) return true;
		look();
		return humanBoss != null && humanBoss.isInstance(e) || courtRuler != null && courtRuler.isInstance(e);
	}

	/** Points this boss kill is short of Test Meter's boss score (0 without Test Meter or for a boss it already knows). */
	public static double missing(LivingEntity killed) {
		look();
		if (killPoints == null) return 0;
		try {
			return Math.max(0, BOSS_POINTS - ((Number) killPoints.invoke(null, killed)).doubleValue());
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return 0;
		}
	}

	/** Adds the missing points to the killer's pending kill points (Test Meter folds them into its combat average next second). */
	public static double credit(ServerPlayer p, LivingEntity killed) {
		double add = missing(killed);
		if (add <= 0 || data == null || pending == null) return 0;
		try {
			Object d = data.invoke(null, p);
			pending.setDouble(d, pending.getDouble(d) + add);
			return add;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return 0;
		}
	}

	/** Test Meter's pending kill points for {@code p} (-1 without Test Meter), for the self-test. */
	public static double pending(ServerPlayer p) {
		look();
		if (data == null || pending == null) return -1;
		try {
			return pending.getDouble(data.invoke(null, p));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return -1;
		}
	}

	private static void look() {
		if (looked) return;
		looked = true;
		humanBoss = cls("dev.elsewhere.duelist.boss.HumanBoss");
		courtRuler = cls("dev.elsewhere.kingdom_court.ruler.CourtRuler");
		if (!FabricLoader.getInstance().isModLoaded("testmeter")) return;
		try {
			Class<?> tm = Class.forName("dev.testmeter.TestMeter");
			data = tm.getMethod("data", ServerPlayer.class);
			pending = data.getReturnType().getDeclaredField("pendingKillPoints");
			pending.setAccessible(true);
			killPoints = Class.forName("dev.testmeter.Physiology").getDeclaredMethod("killPoints", LivingEntity.class);
			killPoints.setAccessible(true);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			data = null;
			pending = null;
			killPoints = null;
		}
	}

	private static Class<?> cls(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException | LinkageError e) {
			return null;
		}
	}
}
