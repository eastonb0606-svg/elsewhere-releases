package dev.elsewhere.kingdom_court.npc;

import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Registers the three diplomacy NPC types and their code-defined units (skins {@code textures/entity/kingdom/diplomat_*.png}; a faction file's roster may reskin the unit ids). */
public final class Npcs {
	private Npcs() {}

	public static void init() {
		for (Diplomacy.Role r : Diplomacy.Role.values()) {
			String skin = switch (r) {
				case LAWSPEAKER -> "diplomat_lawspeaker";
				case ENVOY -> "diplomat_royal_envoy";
				case CLERK -> "diplomat_clerk";
				case SMITH -> "trader_smith";
				case INSPECTOR -> "trader_market_inspector";
				case AUCTIONEER -> "trader_auctioneer";
				case CARAVAN_MASTER -> "trader_caravan_master";
			};
			UnitTypes.putCodeDefined(r.unitId(), new UnitType(UnitType.Role.MELEE, false, CourtContent.id("textures/entity/kingdom/" + skin + ".png"),
				new UnitType.Armor(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				new UnitType.Stats(20.0, 0.25, 1.0, 16.0, 1.0, 0.0), new UnitType.Combat(0.0F, 0.0F, 30, 0.0F)));
			final Diplomacy.Role rr = r;
			r.type = KingdomEntities.registerSoldierType(r.path, r.unitId(), (t, l) -> new DiplomatNpc(t, l, rr));
		}
	}

	public static Identifier id(Diplomacy.Role r) {
		return r.unitId();
	}
}
