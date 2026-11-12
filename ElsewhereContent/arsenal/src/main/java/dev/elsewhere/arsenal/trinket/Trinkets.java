package dev.elsewhere.arsenal.trinket;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.spell.Calling;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Trinkets (ARSENAL.md §7): items in {@code #elsewhere:trinket} work from the off hand or the hotbar; at most 2
 * different ones are active (off hand first, then hotbar 1→9). Arsenal applies the Glimmerstone Focus modifiers.
 */
public final class Trinkets {
	private Trinkets() {}

	public static final TagKey<Item> TRINKET = TagKey.create(Registries.ITEM, ArsenalContent.id("trinket"));
	private static final Identifier FOCUS_MAX = ArsenalContent.id("trinket/glimmerstone_focus_max");
	private static final Identifier FOCUS_REGEN = ArsenalContent.id("trinket/glimmerstone_focus_regen");

	/** Active trinket items in priority order (works on both sides). */
	public static List<Item> active(Player p) {
		List<Item> out = new ArrayList<>(2);
		consider(out, p.getOffhandItem());
		for (int i = 0; i < 9 && out.size() < ArsenalStats.MAX_ACTIVE_TRINKETS; i++) consider(out, p.getInventory().getItem(i));
		return out;
	}

	/** Hotbar slots (0–8) whose trinket is active, plus -1 for the off hand. */
	public static List<Integer> activeSlots(Player p) {
		List<Item> act = active(p);
		List<Integer> slots = new ArrayList<>();
		List<Item> seen = new ArrayList<>();
		ItemStack off = p.getOffhandItem();
		if (off.is(TRINKET) && act.contains(off.getItem())) {
			slots.add(-1);
			seen.add(off.getItem());
		}
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(TRINKET) && act.contains(s.getItem()) && !seen.contains(s.getItem())) {
				slots.add(i);
				seen.add(s.getItem());
			}
		}
		return slots;
	}

	private static void consider(List<Item> out, ItemStack s) {
		if (out.size() >= ArsenalStats.MAX_ACTIVE_TRINKETS || s.isEmpty() || !s.is(TRINKET)) return;
		if (!out.contains(s.getItem())) out.add(s.getItem());
	}

	public static boolean isActive(Player p, Item item) {
		return active(p).contains(item);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) update(p);
		});
	}

	/** Applies the arsenal trinkets' attribute modifiers (Glimmerstone Focus: caster trinket, needs a caster piece). */
	public static void update(ServerPlayer p) {
		boolean focus = isActive(p, ArsenalItems.GLIMMERSTONE_FOCUS) && (dev.elsewhere.arsenal.Overhaul3.CALLINGS ? Calling.mastery(p, Calling.Path.CASTER) >= Calling.FOCUS_MIN_MASTERY : Calling.caster(p) > 0);
		apply(p, ArsenalAttributes.MAX_GLIMMER, FOCUS_MAX, focus ? ArsenalStats.FOCUS_MAX : 0);
		apply(p, ArsenalAttributes.GLIMMER_REGEN, FOCUS_REGEN, focus ? ArsenalStats.FOCUS_REGEN : 0);
	}

	private static void apply(ServerPlayer p, Holder<Attribute> attr, Identifier id, double amount) {
		AttributeInstance inst = p.getAttribute(attr);
		if (inst == null) return;
		AttributeModifier cur = inst.getModifier(id);
		if (amount == 0) {
			if (cur != null) inst.removeModifier(id);
		} else if (cur == null || cur.amount() != amount) {
			inst.removeModifier(id);
			inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
