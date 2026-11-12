package dev.elsewhere.arsenal.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.compat.TestMeterCompat;
import dev.elsewhere.arsenal.spell.MagicBolt;
import dev.elsewhere.arsenal.spell.Spells;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;

/**
 * Test Meter compat checks (3.0, s-compat-testmeter; kill switch {@code testmeter_compat}). They pass with and without Test Meter
 * in {@code run/mods}: without it every factor is 1; with it they prove a spell lands Elsewhere's own half bonus exactly once.
 * The target is a pig: no armor, not an {@code Enemy}, so neither armor nor Test Meter's mob-health bonus touches the numbers.
 */
final class SelfTestTestMeter {
	private SelfTestTestMeter() {}

	static void run(ServerLevel l, BlockPos base) {
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("arsenal-testmeter".getBytes()), "TMTester"));
		p.snapTo(base.getX() + 30.5, base.getY(), base.getZ() + 0.5, 0, 0);
		boolean tm = TestMeterCompat.present();
		Pig pig = EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND);
		if (pig == null) {
			SelfTest.check("testmeter: test pig spawned", false);
			return;
		}
		pig.setNoAi(true);
		pig.snapTo(base.getX() + 32.5, base.getY(), base.getZ() + 0.5, 0, 0);
		l.addFreshEntity(pig);

		float melee = TestMeterCompat.outgoingFactor(l.damageSources().playerAttack(p));
		float boltFactor = TestMeterCompat.outgoingFactor(Spells.source(l, MagicBolt.create(l, p, 0xFFFFFF), p));
		System.out.println("[arsenal-selftest] INFO testmeter: " + (tm ? "loaded" : "absent") + ", melee x" + melee + ", projectile x" + boltFactor + ", compat " + TestMeterCompat.ENABLED);

		double half = Spells.testMeterHalf(p);
		SelfTest.check("testmeter: spell and minion half bonus is 1 + (melee - 1) / 2 (" + (tm ? "Test Meter melee x" + melee : "1.0 without Test Meter") + ")",
			tm ? Math.abs(half - (1 + (melee - 1) / 2.0)) < 1e-3 && half >= 1.0 : SelfTest.near(half, 1.0), half);

		// a bolt is a Projectile: Test Meter would add its own half bonus on top of the one already inside Spells.damage
		MagicBolt bolt = MagicBolt.create(l, p, 0xA77BF3);
		float d = Spells.damage(p, pig, 2F, false, false);
		float lost = hit(l, p, bolt, pig, d);
		SelfTest.check("testmeter: a spell bolt (Projectile) lands exactly Spells.damage, half bonus once (" + d + ")", Math.abs(lost - d) < 0.01F, lost);

		// a beam or burst hits with the caster as the direct entity: Test Meter leaves it alone, so nothing is divided
		float lost2 = hit(l, p, p, pig, d);
		SelfTest.check("testmeter: a beam spell (caster is the direct entity) lands exactly Spells.damage (" + d + ")", Math.abs(lost2 - d) < 0.01F, lost2);

		// minion bites: the minion (not a projectile) is the direct entity, the owner the cause; Test Meter must not scale them again
		SelfTest.check("testmeter: Test Meter leaves an owned non-projectile hit (minion style) unscaled", TestMeterCompat.outgoingFactor(Spells.source(l, pig, p)) == 1F,
			TestMeterCompat.outgoingFactor(Spells.source(l, pig, p)));
		SelfTest.check("testmeter: pre-compensation divides by Test Meter's own factor for this source", Math.abs(TestMeterCompat.preCompensate(Spells.source(l, bolt, p), d) * boltFactor - d) < 1e-3);
		pig.discard();
	}

	private static float hit(ServerLevel l, FakePlayer p, net.minecraft.world.entity.Entity direct, LivingEntity target, float d) {
		target.setHealth(target.getMaxHealth());
		target.damageCooldownTime = 0;
		target.setInvulnerableTime(0);
		float before = target.getHealth();
		Spells.hurt(p, direct, target, d);
		return before - target.getHealth();
	}
}
