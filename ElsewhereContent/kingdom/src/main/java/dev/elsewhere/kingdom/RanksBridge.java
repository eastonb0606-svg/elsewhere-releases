package dev.elsewhere.kingdom;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Package bridge for the 3.0 rank impostors ({@code dev.elsewhere.kingdom.armies.impostor}, s-impostors): a battle squad folded at R3 keeps its
 * soldiers in its own virtual record ({@link Squad.VirtualSoldier}), and the rank showing it must write wounds and deaths from rank dice back
 * into that record, so an unfolded squad comes back with exactly the health it had (no free healing; the dead stay dead). Unused and harmless
 * when the impostors are off.
 */
public final class RanksBridge {
	private RanksBridge() {}

	/** Sets a folded soldier's saved health. False when the squad is not virtual or does not hold him. */
	public static boolean setVirtualHealth(ServerLevel level, Squad q, UUID soldier, float health) {
		if (!q.virtual) return false;
		for (int i = 0; i < q.virtualMembers.size(); i++) {
			Squad.VirtualSoldier v = q.virtualMembers.get(i);
			if (!v.uuid().equals(soldier)) continue;
			q.virtualMembers.set(i, new Squad.VirtualSoldier(v.uuid(), v.type(), v.unit(), health, v.dx(), v.dy(), v.dz()));
			KingdomState.get(level).setDirty();
			return true;
		}
		return false;
	}

	/** A folded soldier died in the rank: he leaves the virtual record and the squad (an emptied virtual squad is removed by its own tick). */
	public static boolean removeVirtual(ServerLevel level, Squad q, UUID soldier) {
		if (!q.virtual || !q.virtualMembers.removeIf(v -> v.uuid().equals(soldier))) return false;
		q.members.remove(soldier);
		if (soldier.equals(q.leader)) q.leader = null; // materialize promotes a new leader
		KingdomState.get(level).setDirty();
		return true;
	}

	/** True when a virtual squad's record holds this soldier. */
	public static boolean holdsVirtual(Squad q, UUID soldier) {
		if (!q.virtual) return false;
		for (Squad.VirtualSoldier v : q.virtualMembers) if (v.uuid().equals(soldier)) return true;
		return false;
	}
}
