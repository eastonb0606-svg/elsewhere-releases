package dev.elsewhere.rivals.hrafnholt;

import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.rivals.hrafnholt.unit.Berserker;
import dev.elsewhere.rivals.hrafnholt.unit.WolfHandler;
import net.minecraft.world.entity.EntityType;

/**
 * Soldier classes for the two unique Hrafnholt units. Unit JSONs (loadout, stats) are data; Winter Rage and the wolves are code.
 * Registered ids: entity types {@code elsewhere:hrafnholt_berserker} and {@code elsewhere:hrafnholt_wolf_handler} (each with a spawn egg).
 */
public final class HrafnholtUnits {
	private HrafnholtUnits() {}

	public static EntityType<Berserker> BERSERKER;
	public static EntityType<WolfHandler> WOLF_HANDLER;

	public static void init() {
		if (BERSERKER != null) return;
		BERSERKER = KingdomEntities.registerSoldierType("hrafnholt_berserker", HrafnholtContent.id("hrafnholt_berserker"), Berserker::new);
		WOLF_HANDLER = KingdomEntities.registerSoldierType("hrafnholt_wolf_handler", HrafnholtContent.id("hrafnholt_wolf_handler"), WolfHandler::new);
		KingdomEntities.registerUnitClass(HrafnholtContent.id("hrafnholt_berserker"), BERSERKER);
		KingdomEntities.registerUnitClass(HrafnholtContent.id("hrafnholt_wolf_handler"), WOLF_HANDLER);
	}
}
