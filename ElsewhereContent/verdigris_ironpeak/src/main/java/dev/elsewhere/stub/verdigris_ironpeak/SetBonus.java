package dev.elsewhere.stub.verdigris_ironpeak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * STUB of the core's set-bonus API (Terraria-style full-set bonuses). A set lists its pieces; when a player wears all
 * of them, {@link #apply} runs once, and {@link #remove} runs once when any piece comes off (or the player leaves).
 * {@link #tick} runs every 10 ticks while the set is complete. Tooltip lines are shown on every piece by the client stub.
 * At merge, swap this for the core's API; the set definitions in the dimension packages carry over.
 */
public abstract class SetBonus {
	public static final List<SetBonus> ALL = new ArrayList<>();
	private static final Map<SetBonus, Set<UUID>> ACTIVE = new HashMap<>();

	public final String id;
	public final Map<EquipmentSlot, Item> pieces;

	protected SetBonus(String id, Map<EquipmentSlot, Item> pieces) {
		this.id = id;
		this.pieces = pieces;
		ALL.add(this);
		ACTIVE.put(this, new HashSet<>());
	}

	/** Other items that fill a slot of this set (e.g. a boss helmet that counts as the set's helmet). */
	private final Map<EquipmentSlot, java.util.List<Item>> alternates = new java.util.EnumMap<>(EquipmentSlot.class);

	public SetBonus alternate(EquipmentSlot slot, Item item) {
		alternates.computeIfAbsent(slot, k -> new ArrayList<>()).add(item);
		return this;
	}

	public abstract void apply(ServerPlayer player);

	public abstract void remove(ServerPlayer player);

	public void tick(ServerPlayer player) {}

	public abstract List<Component> tooltip();

	public boolean wears(ServerPlayer player) {
		for (Map.Entry<EquipmentSlot, Item> e : pieces.entrySet()) {
			var worn = player.getItemBySlot(e.getKey());
			if (!worn.is(e.getValue()) && alternates.getOrDefault(e.getKey(), java.util.List.of()).stream().noneMatch(worn::is)) return false;
		}
		return true;
	}

	public boolean isActive(ServerPlayer player) {
		return ACTIVE.get(this).contains(player.getUUID());
	}

	public static SetBonus forItem(Item item) {
		for (SetBonus s : ALL) if (s.pieces.containsValue(item) || s.alternates.values().stream().anyMatch(l -> l.contains(item))) return s;
		return null;
	}

	/** Called every server tick. */
	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		for (SetBonus set : ALL) {
			Set<UUID> active = ACTIVE.get(set);
			Set<UUID> seen = new HashSet<>();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean wears = set.wears(player) && player.isAlive();
				if (wears) {
					seen.add(player.getUUID());
					if (active.add(player.getUUID())) set.apply(player);
					set.tick(player);
				} else if (active.remove(player.getUUID())) {
					set.remove(player);
				}
			}
			active.retainAll(seen);
		}
	}

	/** For tests: re-evaluate one player immediately. */
	public static void update(ServerPlayer player) {
		for (SetBonus set : ALL) {
			Set<UUID> active = ACTIVE.get(set);
			if (set.wears(player)) {
				if (active.add(player.getUUID())) set.apply(player);
			} else if (active.remove(player.getUUID())) {
				set.remove(player);
			}
		}
	}
}
