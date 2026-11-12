package dev.elsewhere.stub.nether_end_bosses;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hook for Terraria-style full-set bonuses (CONTRACT.md "Set bonuses"). The design agent decides which sets get one;
 * this module registers none yet. The core's version replaces this stub.
 */
public interface SetBonus {
	List<SetBonus> REGISTERED = new ArrayList<>();

	String id();

	/** True when the player wears the full set. */
	boolean isWorn(ServerPlayer player);

	/** Applied once per second while worn. */
	void tick(ServerPlayer player);

	static void register(SetBonus bonus) {
		REGISTERED.add(bonus);
	}
}
