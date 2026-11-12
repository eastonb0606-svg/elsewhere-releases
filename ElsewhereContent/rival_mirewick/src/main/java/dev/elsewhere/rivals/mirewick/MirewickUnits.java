package dev.elsewhere.rivals.mirewick;

import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.mirewick.unit.BogWarden;
import dev.elsewhere.rivals.mirewick.unit.MireTrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Mirewick's unique soldiers: the Bog-Warden (tower shield, shove) and the Mire-Trapper (10 s webs and mud). */
public final class MirewickUnits {
	private MirewickUnits() {}

	public static EntityType<BogWarden> BOG_WARDEN;
	public static EntityType<MireTrapper> MIRE_TRAPPER;
	public static final Map<String, EntityType<? extends KingdomSoldier>> TYPES = new LinkedHashMap<>();
	/** The two spawn eggs (they also sit in the kingdom tab). */
	public static final List<Item> EGGS = new ArrayList<>();

	public static void init() {
		if (BOG_WARDEN != null) return;
		BOG_WARDEN = KingdomEntities.registerSoldierType("mirewick_bog_warden", MirewickContent.id("mirewick_bog_warden"), BogWarden::new);
		EGGS.add(KingdomEntities.EGGS.get(KingdomEntities.EGGS.size() - 1));
		MIRE_TRAPPER = KingdomEntities.registerSoldierType("mirewick_mire_trapper", MirewickContent.id("mirewick_mire_trapper"), MireTrapper::new);
		EGGS.add(KingdomEntities.EGGS.get(KingdomEntities.EGGS.size() - 1));
		TYPES.put("bog_warden", BOG_WARDEN);
		TYPES.put("mire_trapper", MIRE_TRAPPER);
	}
}
