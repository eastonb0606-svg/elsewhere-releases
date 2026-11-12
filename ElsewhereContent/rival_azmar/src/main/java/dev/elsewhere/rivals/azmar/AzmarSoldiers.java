package dev.elsewhere.rivals.azmar;

import dev.elsewhere.duelist.kit.ShieldHook;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.world.entity.EntityType;

/** Registers the Azmar soldier class (Sand-Dancer, Naffatun, Jackal Guard), binds the unit ids to it, and links squads for the khopesh hook. */
public final class AzmarSoldiers {
	private AzmarSoldiers() {}

	/** Tag on every non-soldier ally of Azmar's court (the war camel): fire pots and friendly checks skip it. */
	public static final String ALLY_TAG = "azmar_ally";
	public static EntityType<AzmarSoldier> TYPE;
	private static boolean done;

	static void init() {
		if (done) return;
		done = true;
		TYPE = KingdomEntities.registerSoldierType("azmar_soldier", AzmarSoldier.SAND_DANCER, AzmarSoldier::new);
		KingdomEntities.registerUnitClass(AzmarSoldier.SAND_DANCER, TYPE);
		KingdomEntities.registerUnitClass(AzmarSoldier.NAFFATUN, TYPE);
		KingdomEntities.registerUnitClass(AzmarSoldier.JACKAL_GUARD, TYPE);
		// the khopesh hook counts once per 40 ticks across a whole squad: soldiers of one squad share a key
		var prev = ShieldHook.squadKeyOf;
		ShieldHook.squadKeyOf = a -> a instanceof KingdomSoldier s && s.squadId() != null ? s.squadId() : prev.apply(a);
		// fire pots never hurt the thrower's own side
		FirePots.friendly = (thrower, other) -> thrower == other
			|| (thrower instanceof KingdomSoldier a && other instanceof KingdomSoldier b && !Factions.hostile(a.factionId(), b.factionId()))
			|| other instanceof dev.elsewhere.rivals.azmar.boss.AzmarBoss || other instanceof AzmarFalcon || other.entityTags().contains(ALLY_TAG);
	}
}
