package dev.elsewhere.gear3.setbonus;

import dev.elsewhere.arsenal.spell.Calling;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * One gear3 set or pair bonus (GEAR.md A5, the "(B)" set and pair bonuses). Build it with {@link #of}, then
 * {@link SetBonuses#register}. It is active while every listed slot holds one of that slot's accepted items; a pair bonus
 * ({@link #hybrid}) scales by the wearer's hybrid strength H(a, b) (GEAR.md A1). Lang keys: {@code setbonus.elsewhere.<id>}
 * (the name) and {@code setbonus.elsewhere.<id>.detail1..N} (the Shift lines). Kill switch: {@code "setbonus_<id>": false}
 * in config/elsewhere-overhaul3.json (or {@code "set_bonuses": false} for all of them).
 */
public final class SetBonus {
	/** Called on the server when the bonus turns on or off for a player (equipment change, join, respawn). */
	public interface Listener {
		void changed(ServerPlayer p, boolean active);
	}

	public final String id;
	final List<EquipmentSlot> slots = new ArrayList<>();
	final List<Set<Identifier>> accepted = new ArrayList<>();
	Calling.Path hybridA, hybridB;
	int details = 1;
	final List<Listener> listeners = new ArrayList<>();
	boolean enabled = true;
	int bit = -1;

	private SetBonus(String id) {
		this.id = id;
	}

	/** A new bonus; {@code id} is lowercase snake case (e.g. "single_combat"), used for lang keys and the kill switch. */
	public static SetBonus of(String id) {
		return new SetBonus(id);
	}

	/** A required slot and the items that fill it ("galeplate_helmet" = elsewhere:galeplate_helmet; "ns:path" for others). */
	public SetBonus piece(EquipmentSlot slot, String... ids) {
		Set<Identifier> s = new LinkedHashSet<>();
		for (String i : ids) s.add(i.contains(":") ? Identifier.parse(i) : Identifier.fromNamespaceAndPath("elsewhere", i));
		slots.add(slot);
		accepted.add(s);
		return this;
	}

	/** A pair (hybrid) bonus: its strength is H(a, b). */
	public SetBonus hybrid(Calling.Path a, Calling.Path b) {
		hybridA = a;
		hybridB = b;
		return this;
	}

	/** How many Shift detail lines ({@code setbonus.elsewhere.<id>.detail1..n}) the tooltip shows. */
	public SetBonus details(int n) {
		details = n;
		return this;
	}

	public SetBonus onChange(Listener l) {
		listeners.add(l);
		return this;
	}

	public boolean isHybrid() {
		return hybridA != null && hybridB != null;
	}

	public boolean enabled() {
		return enabled;
	}

	public String killKey() {
		return "setbonus_" + id;
	}

	public String langKey() {
		return "setbonus.elsewhere." + id;
	}

	public int pieceCount() {
		return slots.size();
	}

	public int detailLines() {
		return details;
	}

	public Calling.Path hybridA() {
		return hybridA;
	}

	public Calling.Path hybridB() {
		return hybridB;
	}

	/** Accepted item ids for the i-th slot (in {@link #piece} order). */
	public Set<Identifier> accepted(int i) {
		return accepted.get(i);
	}

	public EquipmentSlot slot(int i) {
		return slots.get(i);
	}

	/** Adds another accepted item to the slot that already accepts {@code baseId} (e.g. an Ascendant variant with its own id). */
	public void alias(Identifier baseId, Identifier otherId) {
		for (Set<Identifier> s : accepted) if (s.contains(baseId)) s.add(otherId);
	}

	/** True if {@code stack} is one of this bonus's pieces (any slot). */
	public boolean isPiece(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Identifier k = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (Set<Identifier> s : accepted) if (s.contains(k)) return true;
		return false;
	}

	boolean fills(int i, ItemStack stack) {
		return !stack.isEmpty() && accepted.get(i).contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}
}
