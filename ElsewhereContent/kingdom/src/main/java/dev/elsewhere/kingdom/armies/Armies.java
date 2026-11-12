package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomContent;

/**
 * Elsewhere 3.0 epic armies (ARMIES.md section 9): per-kingdom army scale and routine data, raised soldier caps, distance rings
 * with the player-first rule and attack tokens, reinforcement waves, routine v1, soldier projectile hygiene and NPC-kill drop suppression.
 * Kill switches in {@code config/elsewhere_armies.json} (see {@link ArmiesConfig}); {@code "enabled": false} turns all of it off (the data
 * still loads; nothing else changes). Called once from {@code KingdomContent.init()}.
 */
public final class Armies {
	private Armies() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ArmiesConfig.load();
		ArmyScale.init();
		dev.elsewhere.kingdom.armies.townsfolk.TownsfolkContent.init(); // s-townsfolk: entity always registered; key "townsfolk" (and "enabled") switch it
		if (!ArmiesConfig.enabled) {
			ArmyCaps.restoreIfOurs();
			KingdomContent.LOG.info("Armies 3.0 overhaul is off (config/elsewhere_armies.json)");
		} else {
			ArmyCaps.apply();
			ArrowHygiene.init();
			NpcKillDrops.init();
			ArmyLod.init();
			// shared-code lag fix (coordinator, 10:16): melee repath throttle and one shared candidate query per cell for squad perception
			dev.elsewhere.kingdom.ai.SoldierMeleeGoal.throttle = true;
			dev.elsewhere.kingdom.ai.RangedNav.throttle = ArmiesConfig.rangedRepath && !"off".equals(System.getenv("ELSEWHERE_RANGEDNAV")); // kill switch: ranged_repath (env ELSEWHERE_RANGEDNAV=off is the bench A/B switch)
			dev.elsewhere.kingdom.tactics.Perceiver.shareQueries = true;
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, lv) -> {
				if (e instanceof net.minecraft.world.entity.LivingEntity) dev.elsewhere.kingdom.tactics.Perceiver.invalidate();
			});
			ArmyWaves.init();
			ArmyRoutine.init();
			ArmiesCommands.init();
		}
		dev.elsewhere.kingdom.armies.swarm.Swarm.init(ArmiesConfig.enabled, ArmiesConfig.otherSelfTest()); // swarm-as-one (key swarm, s-swarm1)
		if (Boolean.getBoolean("elsewhere.armies.selftest") && !ArmiesConfig.otherSelfTest()) ArmiesSelfTest.register();
	}
}
