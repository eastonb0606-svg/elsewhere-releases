package dev.elsewhere.stub.summoner;

import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;

/**
 * Test Meter half bonus (ARSENAL.md §3): minions get {@code 1 + (m - 1) / 2} of the owner's melee multiplier
 * {@code m}. Soft link: delegates to arsenal's {@code dev.elsewhere.arsenal.spell.Spells.testMeterHalf(Player)} when
 * the arsenal module is present (it reads Test Meter by reflection); 1.0 otherwise.
 */
public final class TestMeterHalf {
	private TestMeterHalf() {}

	private static boolean looked;
	private static Method half;

	public static double multiplier(Player owner) {
		if (!looked) {
			looked = true;
			try {
				half = Class.forName("dev.elsewhere.arsenal.spell.Spells").getMethod("testMeterHalf", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				half = null;
			}
		}
		if (half == null) return 1.0;
		try {
			return ((Number) half.invoke(null, owner)).doubleValue();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0;
		}
	}
}
