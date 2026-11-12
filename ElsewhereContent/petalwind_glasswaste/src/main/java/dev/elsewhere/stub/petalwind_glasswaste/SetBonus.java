package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * STUB of the core's set-bonus API (Terraria-style full-set bonuses). A set lists one item per armor slot; while all are
 * worn its attribute modifiers are applied (and removed cleanly when a piece comes off) and {@link #whileWorn} runs
 * once per second. Tooltip lines are added on the client by the module's client class.
 */
public abstract class SetBonus {
	private static final List<SetBonus> SETS = new ArrayList<>();
	private static final Map<UUID, Set<Identifier>> ACTIVE = new HashMap<>();

	public final Identifier id;
	public final Map<EquipmentSlot, List<Item>> pieces;
	public final List<Component> tooltip;

	protected SetBonus(Identifier id, Map<EquipmentSlot, List<Item>> pieces, List<Component> tooltip) {
		this.id = id;
		this.pieces = pieces;
		this.tooltip = tooltip;
	}

	/** Attribute modifiers applied while the full set is worn. */
	public Map<Holder<Attribute>, AttributeModifier> modifiers() {
		return Map.of();
	}

	/** Runs once per second while the full set is worn (server side). */
	public void whileWorn(ServerPlayer player) {}

	public boolean isWorn(net.minecraft.world.entity.LivingEntity entity) {
		for (var e : pieces.entrySet()) {
			ItemStack worn = entity.getItemBySlot(e.getKey());
			if (e.getValue().stream().noneMatch(worn::is)) return false;
		}
		return true;
	}

	public boolean contains(ItemStack stack) {
		for (List<Item> items : pieces.values()) for (Item i : items) if (stack.is(i)) return true;
		return false;
	}

	public static void register(SetBonus set) {
		SETS.add(set);
	}

	public static List<SetBonus> all() {
		return SETS;
	}

	public static boolean has(ServerPlayer player, Identifier id) {
		return ACTIVE.getOrDefault(player.getUUID(), Set.of()).contains(id);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			boolean second = server.getTickCount() % 20 == 0;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Set<Identifier> active = ACTIVE.computeIfAbsent(p.getUUID(), u -> new HashSet<>());
				for (SetBonus set : SETS) {
					boolean worn = set.isWorn(p);
					boolean was = active.contains(set.id);
					if (worn && !was) {
						active.add(set.id);
						set.modifiers().forEach((attr, mod) -> {
							AttributeInstance inst = p.getAttribute(attr);
							if (inst != null && !inst.hasModifier(mod.id())) inst.addTransientModifier(mod);
						});
					} else if (!worn && was) {
						active.remove(set.id);
						set.modifiers().forEach((attr, mod) -> {
							AttributeInstance inst = p.getAttribute(attr);
							if (inst != null) inst.removeModifier(mod.id());
						});
					}
					if (worn && second) set.whileWorn(p);
				}
			}
		});
	}
}
