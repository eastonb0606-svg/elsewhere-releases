package dev.elsewhere.stub.summoner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Glimmer bridge (ARSENAL.md §1). When the arsenal module is present (combined jar) every call goes to arsenal's
 * {@code dev.elsewhere.arsenal.glimmer.Glimmer} (soft link by reflection, no compile dependency):
 * {@code trySpendScaled(ServerPlayer, int)}, {@code refund(ServerPlayer, float)}, {@code drain(ServerPlayer, float)}.
 * A summon's 10 is scaled by the {@code elsewhere:spell_cost} attribute (0.5–2, rounded up, min 1) but not by the
 * caster Calling cost table, which is for spells only. Arsenal reads the per-minion reserve and regen slow from
 * {@code dev.elsewhere.minion.MinionApi.activeCount / reservedGlimmer}.
 * Without arsenal (this dev module alone): every spend succeeds (unless {@link #devDeny} is set by the self-test) and
 * is recorded so the self-test can check the cost; refunds are recorded too.
 */
public final class GlimmerStub {
	private GlimmerStub() {}

	public static final Map<UUID, Integer> SPENT = new HashMap<>();
	public static final Map<UUID, Float> REFUNDED = new HashMap<>();
	public static final Map<UUID, Float> DRAINED = new HashMap<>();
	/** Self-test switch: pretend the pool is empty. */
	public static boolean devDeny;
	/**
	 * Self-test switch: spends/refunds go to the dev records above even when arsenal is present, so a long
	 * self-test doesn't run the real pool dry (the summoning check reads the real pool first, see {@link #pool}).
	 */
	public static boolean testFree;

	private static final Identifier SPELL_COST = Identifier.fromNamespaceAndPath("elsewhere", "spell_cost");
	private static boolean looked;
	private static Method spendScaled, refundM, drainM, currentM, setForTestM;
	private static boolean spendTakesFloat;

	private static boolean arsenal() {
		if (!looked) {
			looked = true;
			try {
				Class<?> g = Class.forName("dev.elsewhere.arsenal.glimmer.Glimmer");
				try {
					spendScaled = g.getMethod("trySpendScaled", ServerPlayer.class, float.class);
					spendTakesFloat = true;
				} catch (NoSuchMethodException old) {
					spendScaled = g.getMethod("trySpendScaled", ServerPlayer.class, int.class);
				}
				refundM = g.getMethod("refund", ServerPlayer.class, float.class);
				drainM = g.getMethod("drain", ServerPlayer.class, float.class);
				try {
					currentM = g.getMethod("current", net.minecraft.world.entity.player.Player.class);
					setForTestM = g.getMethod("setForTest", ServerPlayer.class, float.class);
				} catch (NoSuchMethodException optional) {
					currentM = setForTestM = null;
				}
			} catch (ReflectiveOperationException | LinkageError e) {
				spendScaled = refundM = drainM = currentM = setForTestM = null;
			}
		}
		return spendScaled != null;
	}

	/** True when spends go to arsenal's real Glimmer pool (combined jar). */
	public static boolean arsenalPresent() {
		return arsenal();
	}

	/** The real pool's current Glimmer (arsenal), or NaN without arsenal. Self-test use. */
	public static float pool(ServerPlayer player) {
		if (!arsenal() || currentM == null) return Float.NaN;
		try {
			return ((Number) currentM.invoke(null, player)).floatValue();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Float.NaN;
		}
	}

	/** Sets the real pool (clamped to its max by arsenal). Self-test use; no-op without arsenal. */
	public static void setPool(ServerPlayer player, float v) {
		if (!arsenal() || setForTestM == null) return;
		try {
			setForTestM.invoke(null, player, v);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private static boolean real() {
		return !testFree && arsenal();
	}

	/** {@code raw} × spell_cost (clamped 0.5–2), rounded up, minimum 1. */
	public static int scaled(ServerPlayer player, int raw) {
		double m = 1.0;
		var h = BuiltInRegistries.ATTRIBUTE.get(SPELL_COST);
		if (h.isPresent()) {
			AttributeInstance inst = player.getAttribute(h.get());
			if (inst != null) m = Math.max(0.5, Math.min(2.0, inst.getValue()));
		}
		return Math.max(1, (int) Math.ceil(raw * m - 1.0E-6));
	}

	/** Spends {@code cost} raw Glimmer. Returns false (and does nothing) if not enough. */
	public static boolean trySpend(ServerPlayer player, int cost) {
		if (devDeny) return false;
		if (real()) {
			try {
				int amount = scaled(player, cost);
				return (Boolean) (spendTakesFloat ? spendScaled.invoke(null, player, (float) amount) : spendScaled.invoke(null, player, amount));
			} catch (ReflectiveOperationException | RuntimeException e) {
				return false;
			}
		}
		SPENT.merge(player.getUUID(), cost, Integer::sum);
		return true;
	}

	/** Ongoing upkeep drain (fractional). Returns false if the pool can't pay. Unused by the current design. */
	public static boolean drain(ServerPlayer player, float amount) {
		if (devDeny) return false;
		if (real()) {
			try {
				return (Boolean) drainM.invoke(null, player, amount);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return false;
			}
		}
		DRAINED.merge(player.getUUID(), amount, Float::sum);
		return true;
	}

	/** Gives Glimmer back (Pack Bond: a dying minion returns its 10). */
	public static void refund(ServerPlayer player, float amount) {
		if (real()) {
			try {
				refundM.invoke(null, player, amount);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall through to the dev record
			}
		}
		REFUNDED.merge(player.getUUID(), amount, Float::sum);
	}
}
