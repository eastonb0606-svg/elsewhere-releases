package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.each;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.run;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.spawn;

import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.boss.AzmarSultana;
import dev.elsewhere.rivals.azmar.boss.AzmarWazir;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** RIVALS-DECISIONS 13 per boss: compensation (armour, toughness, knockback, damage, cooldown x0.8) and the defence frequency x1.3. */
final class SelfTestComp {
	private static final AzmarBoss[] B = new AzmarBoss[4];

	static void add() {
		run((srv, l) -> {
			SelfTest.clean(l);
			B[0] = spawn(l, AzmarBosses.SULTANA, 0, 150);
			B[1] = spawn(l, AzmarBosses.AMIR, 8, 150);
			B[2] = spawn(l, AzmarBosses.WAZIR, 16, 150);
			B[3] = spawn(l, AzmarBosses.FALCONER, 24, 150);
		});
		each(25, l -> { });
		run((srv, l) -> {
			String[] names = {"sultana", "amir", "wazir", "falconer"};
			for (int i = 0; i < 4; i++) {
				AzmarBoss b = B[i];
				boolean ruler = i == 0;
				b.setCooldown("comp_plain", 100);
				b.setCooldown("dodge", 130);
				check("compensation " + names[i] + ": armor >= " + (ruler ? 8 : 6) + ", toughness >= " + (ruler ? 4 : 3) + ", knockback resistance >= 0.4, damage x1.25, cooldown x0.8 (100 -> " + b.cooldownLeft("comp_plain") + "), max health <= 300",
					b.getAttributeValue(Attributes.ARMOR) >= (ruler ? 8 : 6) && b.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= (ruler ? 4 : 3) && b.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.4 - 1e-6
						&& b.damageMultiplier() >= 1.25F - 1e-4F && b.cooldownLeft("comp_plain") == 80 && b.getMaxHealth() <= AzmarBoss.HEALTH_CAP + 0.01F,
					b.getAttributeValue(Attributes.ARMOR) + "/" + b.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
				check("defence " + names[i] + ": defensive cooldowns scale 1/1.3 (130 -> " + b.cooldownLeft("dodge") + " = 100)", b.cooldownLeft("dodge") == 100, b.cooldownLeft("dodge"));
			}
			AzmarBoss f = B[3];
			f.setCooldown("intercept", 160);
			check("defence falconer: Falcon Intercept is defensive, 160 -> 123 (/1.3), not x0.8 (128)", f.cooldownLeft("intercept") == 123, f.cooldownLeft("intercept"));
			check("defence wazir: Hands Reform repeats every 300 / 1.3 = 231 ticks", AzmarWazir.REFORM_TICKS == 231, AzmarWazir.REFORM_TICKS);
			check("defence sultana: random Mirage Step trigger 1 in 31 (was 1 in 40)", AzmarSultana.MIRAGE_ODDS == 31, AzmarSultana.MIRAGE_ODDS);
			for (AzmarBoss b : B) b.discard();
		});
	}
}
