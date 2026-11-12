package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

/** STUB: which players are inside an active boss fight (trips and some items check it). */
public final class BossFightRegistry {
	private BossFightRegistry() {}

	private static final Set<BossBase> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

	static void active(BossBase boss, boolean on) {
		if (on) ACTIVE.add(boss);
		else ACTIVE.remove(boss);
	}

	public static boolean inFight(ServerPlayer player) {
		UUID id = player.getUUID();
		for (BossBase boss : ACTIVE) if (boss.isAlive() && boss.isParticipant(id)) return true;
		return false;
	}

	public static Set<BossBase> active() {
		return ACTIVE;
	}
}
