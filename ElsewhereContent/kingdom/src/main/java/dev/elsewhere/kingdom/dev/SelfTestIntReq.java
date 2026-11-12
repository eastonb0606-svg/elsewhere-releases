package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;
import static dev.elsewhere.kingdom.dev.SelfTest.info;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.ai.SoldierMeleeGoal;
import dev.elsewhere.kingdom.armies.ArmyScale;
import dev.elsewhere.kingdom.armies.WaveScaleProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * 3.0 integration fixes (s-intreq):
 * <ul>
 * <li>Foot spears (s-benches 13:54): the 26.x spear has a minimum reach ({@code attack_range} 2.0 x mob factor 0.5), and the melee goal
 * walked spearmen inside it, so pikemen and spear thanes never landed a hit. A pikeman now holds its reach band and steps back when crowded.</li>
 * <li>Azmar's composite bow (s-benches): vanilla's bow goal only counted {@code Items.BOW}, so the bowman never drew. Any {@code BowItem}
 * now works (checked where Azmar is loaded: the merged jar, and rival_azmar).</li>
 * <li>Melee soldiers circle facing their target (s-testfix 12:58): {@code strafe} follows the body yaw, which {@code lookAt} turns.</li>
 * <li>Wave battles field each kingdom in its own {@code army.scale} squads (s-azmarsize 12:46), not squads of 10.</li>
 * </ul>
 */
final class SelfTestIntReq {
	private SelfTestIntReq() {}

	static final Identifier AZMAR = KingdomContent.id("azmar");
	static final Identifier AZMAR_BOWMAN = KingdomContent.id("azmar_bowman");
	static final Identifier COMPOSITE_BOW = KingdomContent.id("azmar_composite_bow");

	static boolean savedForce;
	static KingdomSoldier pike, pikeCrowded, bowman;
	static Zombie dummy, dummyCrowded, bowDummy;
	static float hp0, hpCrowded0, bowHp0;
	static Vec3 crowdedStart;

	static void add() {
		// --- foot spears: a pikeman 2 blocks from a standing dummy, and one crowded inside its minimum reach
		STEPS.add((srv, l) -> {
			clean(l);
			savedForce = KingdomPerf.forceActive;
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			dummy = SelfTest.zombie(l, at(2, -20), false);
			pike = SelfTest.soldier(l, UnitTypes.PIKEMAN, Factions.ALDERMARK, at(0, -20));
			Vec3 c = at(14, -20);
			dummyCrowded = SelfTest.zombie(l, c.add(0.9, 0, 0), false);
			pikeCrowded = SelfTest.soldier(l, UnitTypes.PIKEMAN, Factions.ALDERMARK, c);
			crowdedStart = c;
			hp0 = dummy.getHealth();
			hpCrowded0 = dummyCrowded.getHealth();
			if (pike != null) pike.setTarget(dummy);
			if (pikeCrowded != null) pikeCrowded.setTarget(dummyCrowded);
			return 240;
		});
		STEPS.add((srv, l) -> {
			boolean made = pike != null && pikeCrowded != null;
			float dealt = hp0 - (dummy.isAlive() ? dummy.getHealth() : 0F);
			float dealtCrowded = hpCrowded0 - (dummyCrowded.isAlive() ? dummyCrowded.getHealth() : 0F);
			String spear = pike == null ? "none" : BuiltInRegistries.ITEM.getKey(pike.getMainHandItem().getItem()).toString();
			check("a foot spear hit deals damage (Aldermark pikeman, " + spear + ", standing dummy 2 blocks away)", made && dealt > 0F && pike.hasMinReach(),
				String.format("%.1f damage in 12 s, %d hits", dealt, pike == null ? 0 : pike.attacks));
			check("a foot spear hit deals damage when the target starts inside the spear's minimum reach (the pikeman steps back and jabs)",
				made && dealtCrowded > 0F, String.format("%.1f damage in 12 s, %d hits, stepped %.1f blocks", dealtCrowded, pikeCrowded == null ? 0 : pikeCrowded.attacks,
					pikeCrowded == null ? 0 : pikeCrowded.position().distanceTo(crowdedStart)));
			clean(l);
			return 2;
		});

		// --- melee soldiers face their target while circling (SoldierMeleeGoal's in-reach strafe)
		STEPS.add((srv, l) -> {
			KingdomSoldier k = SelfTest.soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(0, -30));
			ArmorStand t = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			boolean ok = false;
			String detail = "the soldier or the target could not be made";
			if (k != null && t != null) {
				t.setPos(k.getX() + 1.2, k.getY(), k.getZ()); // inside sword reach: the goal strafes while the weapon recharges
				l.addFreshEntity(t);
				k.setTarget(t);
				float toT = (float) (Mth.atan2(t.getZ() - k.getZ(), t.getX() - k.getX()) * (180F / (float) Math.PI)) - 90F;
				SelfTest.face(k, toT + 180F);
				float before = Math.abs(Mth.wrapDegrees(k.getYRot() - toT));
				SoldierMeleeGoal g = new SoldierMeleeGoal(k);
				boolean inReach = k.isWithinMeleeAttackRange(t);
				for (int i = 0; i < 8; i++) g.tick();
				float after = Math.abs(Mth.wrapDegrees(k.getYRot() - toT));
				ok = inReach && !k.attackReady() && after <= 5F;
				detail = String.format("in reach %s, body yaw %.0f deg off the target before, %.0f deg after 8 circling ticks", inReach, before, after);
			}
			check("a kingdom melee soldier faces its target while circling (strafe follows the body yaw)", ok, detail);
			clean(l);
			return 2;
		});

		// --- wave battles field every kingdom in its own army.scale squads (s-azmarsize)
		STEPS.add((srv, l) -> {
			clean(l);
			BlockPos rear = BlockPos.containing(at(-30, 30));
			// the code path, whatever data is loaded: a kingdom whose squad is 5 (Azmar's rank block) fields 5s, never the old 10
			ArmyScale five = WaveScaleProbe.testScale(Factions.ALDERMARK, 5, 5);
			WaveScaleProbe.Seen front = WaveScaleProbe.field(l, rear, Factions.ALDERMARK, five, 12);
			WaveScaleProbe.Seen wave = WaveScaleProbe.field(l, rear, Factions.ALDERMARK, five, five.wave().size());
			check("wave battles field a kingdom with squad_size 5 in squads of 5, not 10 (front of 12, then one wave of 5)",
				front.fielded() == 12 && front.squads().equals(List.of(5, 5, 2)) && wave.fielded() == 5 && wave.squads().equals(List.of(5)),
				"front " + front.squads() + ", wave " + wave.squads());
			clean(l);
			return 2;
		});
		STEPS.add((srv, l) -> {
			Map<Identifier, ArmyScale> all = ArmyScale.all();
			BlockPos rear = BlockPos.containing(at(-30, 30));
			List<String> seen = new ArrayList<>();
			boolean ok = !all.isEmpty();
			for (ArmyScale s : all.values()) {
				clean(l);
				int room = KingdomPerf.room(l, Vec3.atCenterOf(rear)); // the caps bind a wave battle too (ArmyWaves.start shares this budget)
				int nf = Math.min(s.liveFront(), room), nw = Math.min(s.wave().size(), room);
				WaveScaleProbe.Seen front = WaveScaleProbe.field(l, rear, s.faction(), s, nf);
				clean(l);
				WaveScaleProbe.Seen wave = WaveScaleProbe.field(l, rear, s.faction(), s, nw);
				boolean one = front.squadSize() == s.squadSize() && front.fielded() == nf && front.followsScale()
					&& wave.fielded() == nw && wave.followsScale();
				ok &= one;
				seen.add(s.faction().getPath() + " squad " + s.squadSize() + ": front " + front.fielded() + "/" + nf + " " + front.squads() + ", wave "
					+ wave.fielded() + "/" + nw + " " + wave.squads() + (one ? "" : " (WRONG)"));
			}
			check("the waves each kingdom fields match its army.scale (squads of its own size, front and wave sizes from the file; "
				+ all.size() + " kingdoms loaded)", ok, String.join("; ", seen));
			ArmyScale az = all.get(AZMAR);
			if (az != null && all.size() >= 4) {
				boolean smallest = true;
				for (ArmyScale o : all.values()) {
					if (o == az || o.faction().getPath().startsWith("test_")) continue;
					smallest &= az.squadSize() < o.squadSize() && az.wave().size() <= o.wave().size() && az.liveFront() < o.liveFront()
						&& az.battleSideMax() <= o.battleSideMax();
				}
				check("Azmar fields the smallest waves of every kingdom (squad 5, relief wave 5 every 30 s, smallest live front)",
					smallest && az.squadSize() == 5 && az.wave().size() == 5 && az.wave().intervalSeconds() == 30 && az.wave().style().equals("relief"),
					"Azmar squad " + az.squadSize() + ", wave " + az.wave().size() + " every " + az.wave().intervalSeconds() + " s (" + az.wave().style() + "), front "
						+ az.liveFront());
			} else {
				info("INFO Azmar-smallest wave check runs where all four kingdoms are loaded (merged jar); loaded here: " + all.keySet());
			}
			clean(l);
			return 2;
		});

		// --- Azmar's composite bow (where Azmar is loaded: merged jar)
		STEPS.add((srv, l) -> {
			bowman = null;
			if (!Factions.exists(AZMAR) || !BuiltInRegistries.ITEM.containsKey(COMPOSITE_BOW)) {
				info("INFO Azmar bow check runs where rival_azmar is loaded (merged jar, rival_azmar self-test); not loaded here");
				return 0;
			}
			clean(l);
			bowDummy = SelfTest.zombie(l, at(10, -40), false);
			bowman = KingdomApi.spawnSoldier(l, AZMAR_BOWMAN, AZMAR, at(0, -40), null);
			bowHp0 = bowDummy.getHealth();
			if (bowman != null) bowman.setTarget(bowDummy);
			return 400;
		});
		STEPS.add((srv, l) -> {
			if (bowman != null || BuiltInRegistries.ITEM.containsKey(COMPOSITE_BOW) && Factions.exists(AZMAR)) {
				float dealt = bowHp0 - (bowDummy.isAlive() ? bowDummy.getHealth() : 0F);
				boolean bow = bowman != null && BuiltInRegistries.ITEM.getKey(bowman.getMainHandItem().getItem()).equals(COMPOSITE_BOW);
				check("an Azmar bow hit deals damage (Azmar bowman with the composite bow, standing dummy 10 blocks away)", bow && dealt > 0F,
					String.format("holds the composite bow %s, %d shots, %.1f damage in 20 s", bow, bowman == null ? 0 : bowman.shots, dealt));
			}
			KingdomPerf.forceActive = savedForce;
			clean(l);
			return 2;
		});
	}
}
