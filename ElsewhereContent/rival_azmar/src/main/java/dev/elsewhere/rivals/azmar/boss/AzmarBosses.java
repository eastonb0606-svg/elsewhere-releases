package dev.elsewhere.rivals.azmar.boss;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.rivals.azmar.AzmarContent;
import net.minecraft.world.entity.EntityType;

/**
 * Registers the four Azmar bosses on the duelist kit and binds them to the court slots: king = Sultana, marshal = Amir,
 * castellan = Wazir, huntsman = Falconer (succession order Amir, Wazir, Falconer).
 */
public final class AzmarBosses {
	private AzmarBosses() {}

	public static EntityType<AzmarSultana> SULTANA;
	public static EntityType<AzmarAmir> AMIR;
	public static EntityType<AzmarWazir> WAZIR;
	public static EntityType<AzmarFalconer> FALCONER;
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		DuelistContent.init();
		dev.elsewhere.rivals.azmar.AzmarFalcon.init();
		SULTANA = DuelistContent.registerBoss("azmar_sultana", AzmarSultana::new, AzmarSultana.PROFILE);
		AMIR = DuelistContent.registerBoss("azmar_amir", AzmarAmir::new, AzmarAmir.PROFILE);
		WAZIR = DuelistContent.registerBoss("azmar_wazir", AzmarWazir::new, AzmarWazir.PROFILE);
		FALCONER = DuelistContent.registerBoss("azmar_falconer", AzmarFalconer::new, AzmarFalconer.PROFILE);
		AzmarBoss.initRegent();
		Leaders.register(AzmarContent.FACTION, "king", SULTANA);
		Leaders.register(AzmarContent.FACTION, "marshal", AMIR);
		Leaders.register(AzmarContent.FACTION, "castellan", WAZIR);
		Leaders.register(AzmarContent.FACTION, "huntsman", FALCONER);
	}
}
