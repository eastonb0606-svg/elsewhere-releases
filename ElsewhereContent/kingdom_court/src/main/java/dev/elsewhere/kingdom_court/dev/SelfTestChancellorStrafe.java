package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;

import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * s-asm-court: the Chancellor's back-away step. {@code MoveControl.strafe} moves relative to the body yaw, which only navigation or
 * {@code Mob.lookAt} turns, so without the {@code lookAt} before it the Chancellor "backed away" wherever its last path had left it facing
 * (s-testfix, 12:58). A Chancellor made off-world, facing straight away from a target 3 blocks off, runs its kit for 8 ticks with the
 * teleport and the decree on cooldown; it must end up facing the target.
 */
final class SelfTestChancellorStrafe {
	private SelfTestChancellorStrafe() {}

	static void add() {
		STEPS.add((srv, l) -> {
			CourtRuler ch = Ruler.CHANCELLOR.type == null ? null : Ruler.CHANCELLOR.type.create(l, EntitySpawnReason.COMMAND);
			ArmorStand t = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			boolean ok = false;
			String detail = "the Chancellor or the target could not be made";
			if (ch != null && t != null) {
				ch.setPos(0.5, 200, 0.5);
				t.setPos(3.5, 200, 0.5); // d2 = 9: inside the back-away band (d2 < 16)
				float toT = (float) (Mth.atan2(t.getZ() - ch.getZ(), t.getX() - ch.getX()) * (180F / (float) Math.PI)) - 90F;
				ch.setYRot(toT + 180F);
				ch.yBodyRot = toT + 180F;
				float before = Math.abs(Mth.wrapDegrees(ch.getYRot() - toT));
				for (int i = 0; i < 8; i++) {
					ch.cdA = 100;
					ch.cdB = 100;
					ch.testAbility(l, "kit", t);
				}
				float after = Math.abs(Mth.wrapDegrees(ch.getYRot() - toT));
				ok = after <= 5F;
				detail = String.format("body yaw %.0f deg off the target before, %.0f deg after 8 back-away ticks", before, after);
				ch.discard();
				t.discard();
			}
			check("court ruler: the Chancellor faces its target while it backs away (strafe follows the body yaw)", ok, detail);
			return 1;
		});
	}
}
