package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.boss.HumanBossHealth;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.compat.TestMeterOrder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Test Meter order checks (3.0, s-compat-testmeter). A spawned human boss gets Test Meter's +53% max health from an ENTITY_LOAD listener
 * in the default phase; the 300 cap must still hold afterwards. Without Test Meter in {@code run/mods} the test stands in for it with the
 * same listener body ({@code BodyStats.scaleMobHealth}: same modifier id, amount and health-fraction rule), registered the same way.
 */
final class SelfTestTestMeter {
	private SelfTestTestMeter() {}

	static final Identifier MOB_HEALTH = Identifier.fromNamespaceAndPath("testmeter", "mob_health");
	static final double BONUS = 0.53;
	static boolean armed, registered;

	/** Test Meter's own rule, for runs without it: hostile mobs get a saved +53% (ADD_MULTIPLIED_TOTAL), health keeps its fraction. */
	static void stand_in() {
		if (registered || FabricLoader.getInstance().isModLoaded("testmeter")) return;
		registered = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!armed || !(entity instanceof Mob mob) || !(entity instanceof Enemy)) return;
			AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
			if (hp == null || hp.getModifier(MOB_HEALTH) != null) return;
			float fraction = mob.getHealth() / mob.getMaxHealth();
			hp.addPermanentModifier(new AttributeModifier(MOB_HEALTH, BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			mob.setHealth(fraction * mob.getMaxHealth());
		});
	}

	static void add(H h) {
		stand_in();
		final TestDuelist[] b = new TestDuelist[2];
		h.test("Test Meter: 300 HP cap after the mob-health bonus").run(() -> {
			armed = true;
			b[0] = h.boss(H.base("tmcap300").hp(300).build(), -6, -10);
			b[1] = h.boss(H.base("tmcap150").hp(150).build(), -2, -10);
		}).wait(3).run(() -> armed = false)
			.check("kill switch testmeter_compat is on and its ENTITY_LOAD phase is registered", () -> TestMeterOrder.ENABLED)
			.check("a fresh 300 HP boss got Test Meter's +53% and still reads 300 max, 300 health", () -> {
				AttributeInstance hp = b[0].getAttribute(Attributes.MAX_HEALTH);
				h.info("max " + b[0].getMaxHealth() + " hp " + b[0].getHealth() + " tm " + hp.getModifier(MOB_HEALTH) + " cap " + hp.getModifier(HumanBossHealth.CAP_ID));
				return hp.getModifier(MOB_HEALTH) != null && hp.getModifier(HumanBossHealth.CAP_ID) != null
					&& Math.abs(b[0].getMaxHealth() - 300F) < 0.01F && b[0].getHealth() <= 300.01F && b[0].getHealth() > 299F;
			})
			.check("a 150 HP boss keeps Test Meter's bonus under the cap (229.5, no cap modifier)", () -> {
				AttributeInstance hp = b[1].getAttribute(Attributes.MAX_HEALTH);
				h.info("max " + b[1].getMaxHealth() + " hp " + b[1].getHealth());
				return hp.getModifier(MOB_HEALTH) != null && hp.getModifier(HumanBossHealth.CAP_ID) == null && Math.abs(b[1].getMaxHealth() - 150F * (1F + (float) BONUS)) < 0.5F;
			})
			.run(() -> {
				for (TestDuelist x : b) if (x != null) x.discard();
			});
	}
}
