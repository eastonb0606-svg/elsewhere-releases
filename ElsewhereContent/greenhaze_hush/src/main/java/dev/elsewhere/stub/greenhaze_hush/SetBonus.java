package dev.elsewhere.stub.greenhaze_hush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * STUB of the core's Terraria-style full-set bonus API. Register a bonus with its pieces; while the full set is worn,
 * {@link #apply} runs once, {@link #tick} every 10 ticks, and {@link #remove} runs as soon as a piece comes off.
 * Tooltip lines are shown on every piece (client side reads {@link #forItem}).
 */
public abstract class SetBonus {
	private static final List<SetBonus> ALL = new ArrayList<>();
	private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();
	private static boolean ticking;

	public final String id;
	public final List<Item> pieces;

	protected SetBonus(String id, List<Item> pieces) {
		this.id = id;
		this.pieces = List.copyOf(pieces);
	}

	public abstract void apply(ServerPlayer player);

	public abstract void remove(ServerPlayer player);

	public void tick(ServerPlayer player) {}

	public abstract List<Component> tooltip();

	public static <T extends SetBonus> T register(T bonus) {
		ALL.add(bonus);
		if (!ticking) {
			ticking = true;
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				if (server.getTickCount() % 10 != 0) return;
				for (ServerPlayer p : server.getPlayerList().getPlayers()) update(p);
			});
		}
		return bonus;
	}

	public boolean wornBy(ServerPlayer p) {
		Set<Item> worn = new HashSet<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = p.getItemBySlot(slot);
			if (!s.isEmpty()) worn.add(s.getItem());
		}
		return worn.containsAll(pieces);
	}

	public static boolean active(ServerPlayer p, String id) {
		Set<String> s = ACTIVE.get(p.getUUID());
		return s != null && s.contains(id);
	}

	public static void update(ServerPlayer p) {
		Set<String> active = ACTIVE.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (SetBonus b : ALL) {
			boolean worn = p.isAlive() && b.wornBy(p);
			boolean was = active.contains(b.id);
			if (worn && !was) { active.add(b.id); b.apply(p); }
			else if (!worn && was) { active.remove(b.id); b.remove(p); }
			if (worn) b.tick(p);
		}
	}

	public static List<SetBonus> forItem(Item item) {
		List<SetBonus> out = new ArrayList<>();
		for (SetBonus b : ALL) if (b.pieces.contains(item)) out.add(b);
		return out;
	}

	public static void forgetPlayer(UUID id) { ACTIVE.remove(id); }

	public static void clearAll() { ACTIVE.clear(); }
}
