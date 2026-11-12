package dev.elsewhere.rivals.hrafnholt.boss;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.api.Succession;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;

/**
 * Registers the six Hrafnholt bosses ({@code elsewhere:hrafnholt_{jarl,hersir,hallwarden,skald,chief,huntmaster}}) and the five Risen champion
 * types ({@code elsewhere:hrafnholt_risen_<slot>}) on the duelist kit, and binds the six seat types to the court's leader slots.
 */
public final class HrafnholtBosses {
	private HrafnholtBosses() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		DuelistContent.init();
		HrafnholtStandards.init();
		for (Hrafn h : Hrafn.values()) {
			final Hrafn fh = h;
			h.type = DuelistContent.registerBoss("hrafnholt_" + h.key, (t, l) -> new HrafnholtBoss(t, l, fh, false), HrafnholtProfiles.of(h, false));
			Leaders.register(HrafnholtContent.FACTION, h.courtSlot, h.type);
			if (h.champion()) h.risenType = DuelistContent.registerBoss("hrafnholt_risen_" + h.key, (t, l) -> new HrafnholtBoss(t, l, fh, true), HrafnholtProfiles.of(h, true));
		}
		Succession.registerRisen(HrafnholtContent.FACTION, Hrafn.HUNTMASTER.courtSlot);   // the court promotes one seat today; see INTEGRATION-REQUESTS
		dev.elsewhere.rivals.hrafnholt.rewards.HrafnholtRewards.init();   // 3.0 (s-bossdrops): XP and drops on every death
	}

	/** Every boss and Risen type registered here. */
	public static List<EntityType<HrafnholtBoss>> types() {
		List<EntityType<HrafnholtBoss>> out = new ArrayList<>();
		for (Hrafn h : Hrafn.values()) {
			if (h.type != null) out.add(h.type);
			if (h.risenType != null) out.add(h.risenType);
		}
		return out;
	}
}
