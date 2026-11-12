package dev.elsewhere.kingdom_court.dev;

import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.api.HpCap;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.compat.TestMeterOrder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Test Meter order check (3.0, s-compat-testmeter): a court leader spawned with Test Meter's +53% mob-health bonus still reads 300.
 * Without Test Meter in {@code run/mods} a stand-in listener with Test Meter's exact rule ({@code BodyStats.scaleMobHealth}) is registered
 * in the default phase, after the court's own ENTITY_LOAD clamp: the order that let a ruler reach 459 HP before the fix.
 */
final class SelfTestTestMeter {
	private SelfTestTestMeter() {}

	static final Identifier MOB_HEALTH = Identifier.fromNamespaceAndPath("testmeter", "mob_health");
	static boolean armed, registered;

	static void add() {
		if (!registered && !FabricLoader.getInstance().isModLoaded("testmeter")) {
			registered = true;
			ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
				if (!armed || !(entity instanceof Mob mob) || !(entity instanceof Enemy)) return;
				AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
				if (hp == null || hp.getModifier(MOB_HEALTH) != null) return;
				float fraction = mob.getHealth() / mob.getMaxHealth();
				hp.addPermanentModifier(new AttributeModifier(MOB_HEALTH, 0.53, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				mob.setHealth(fraction * mob.getMaxHealth());
			});
		}
		final Zombie[] z = new Zombie[1];
		SelfTest.STEPS.add((srv, l) -> {
			Zombie m = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND);
			Vec3 p = SelfTest.at(-30, -30);
			m.snapTo(p.x, p.y, p.z, 0F, 0F);
			m.setNoAi(true);
			m.setPersistenceRequired();
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
			m.setHealth(300F);
			Leaders.tag(m, CourtContent.id("tm_test_realm"), "king");
			armed = true;
			l.addFreshEntity(m);
			z[0] = m;
			return 3;
		});
		SelfTest.STEPS.add((srv, l) -> {
			armed = false;
			Zombie m = z[0];
			AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
			SelfTest.check("testmeter: kill switch testmeter_compat on, late ENTITY_LOAD phase registered", TestMeterOrder.ENABLED, null);
			SelfTest.check("testmeter: a 300 HP court leader spawned with Test Meter's +53% still reads 300 max and at most 300 health",
				hp.getModifier(MOB_HEALTH) != null && hp.getModifier(HpCap.ID) != null && Math.abs(m.getMaxHealth() - 300F) < 0.01F && m.getHealth() <= 300.01F,
				"max " + m.getMaxHealth() + ", health " + m.getHealth() + ", tm " + (hp.getModifier(MOB_HEALTH) != null));
			m.discard();
			return 0;
		});
	}
}
