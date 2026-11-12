package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.ruler.CourtRulers;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lord and King drops (each ruler dies once, so this is the first-kill rule): the Lord's signature item 100%, its K-D
 * Lord Seal ({@code lore_seal_<lord>}) 100%, 16-32 Aldermark Crowns and 1 Oathkeeper's Seal (K-A items). The King:
 * Crown of Aldermark 100%, Sovereign's Standard 50% (also craftable from the five Lord Seals), 16-32 Crowns plus the
 * 64-Crown treasury, and 6-12 emeralds. Items from other lanes are skipped while absent.
 */
public final class CourtDrops {
	private CourtDrops() {}

	public static final float STANDARD_CHANCE = 0.5F;

	/** Drops of a ruler of {@code faction}: the faction file's {@code drops} lines (items that are not registered are skipped), or the built-in Aldermark table when the file has none. */
	public static List<ItemStack> roll(Identifier faction, Ruler r, RandomSource rnd) {
		FactionDef.RulerDef d = CourtRulers.def(faction, r);
		if (d == null || d.drops().isEmpty()) return roll(r, rnd);
		List<ItemStack> out = new ArrayList<>();
		for (FactionDef.Drop dr : d.drops()) {
			if (dr.chance() < 1.0F && rnd.nextFloat() >= dr.chance()) continue;
			Item item = BuiltInRegistries.ITEM.containsKey(dr.item()) ? BuiltInRegistries.ITEM.getValue(dr.item()) : null;
			if (item == null) continue;
			int n = dr.min() + (dr.max() > dr.min() ? rnd.nextInt(dr.max() - dr.min() + 1) : 0);
			while (n > 0) {
				int k = Math.min(n, item.getDefaultMaxStackSize());
				out.add(new ItemStack(item, k));
				n -= k;
			}
		}
		return out;
	}

	public static List<ItemStack> roll(Ruler r, RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(CourtItems.SIGNATURE.get(r)));
		Item crowns = CourtItems.optional("aldermark_crown");
		int n = 16 + rnd.nextInt(17);
		if (r == Ruler.KING) {
			if (rnd.nextFloat() < STANDARD_CHANCE) out.add(new ItemStack(CourtItems.SOVEREIGNS_STANDARD));
			n += 64;
			out.add(new ItemStack(Items.EMERALD, 6 + rnd.nextInt(7)));
		} else {
			Item seal = CourtItems.optional("lore_seal_" + r.key);
			if (seal != null) out.add(new ItemStack(seal));
			Item oath = CourtItems.optional("oathkeepers_seal");
			if (oath != null) out.add(new ItemStack(oath));
		}
		if (crowns != null) while (n > 0) {
			int k = Math.min(n, crowns.getDefaultMaxStackSize());
			out.add(new ItemStack(crowns, k));
			n -= k;
		}
		return out;
	}

	// ---- rival factions: the boss DropRule (100/50 first kill, 67/33 repeat, +10 pity up to 60) -----------------------------

	public static final float FIRST_SIGNATURE = 1.0F, FIRST_OTHER = 0.5F, REPEAT_SIGNATURE = 0.67F, REPEAT_OTHER = 0.33F, PITY_STEP = 0.10F, PITY_CAP = 0.60F;

	/** Chance of one unique (the same numbers as the boss kit's {@code DropRule}). */
	public static float chance(boolean signature, boolean firstKill, int misses) {
		if (signature) return firstKill ? FIRST_SIGNATURE : REPEAT_SIGNATURE;
		float base = firstKill ? FIRST_OTHER : REPEAT_OTHER;
		return Math.max(base, Math.min(PITY_CAP, base + PITY_STEP * misses));
	}

	/**
	 * A line is a boss unique when it is a single item of a mod namespace that is neither the faction's currency nor a Seal. The first unique line
	 * of the slot is its signature; the rest are "other" uniques.
	 */
	static boolean unique(Identifier faction, FactionDef.Drop d) {
		if (d.min() != 1 || d.max() != 1 || "minecraft".equals(d.item().getNamespace()) || d.item().getPath().contains("seal")) return false;
		Identifier cur = dev.elsewhere.kingdom_court.api.Shops.currencyId(faction);
		return !d.item().equals(cur);
	}

	static ItemStack stack(FactionDef.Drop dr, RandomSource rnd) {
		Item item = BuiltInRegistries.ITEM.containsKey(dr.item()) ? BuiltInRegistries.ITEM.getValue(dr.item()) : null;
		if (item == null) return ItemStack.EMPTY;
		return new ItemStack(item, dr.min() + (dr.max() > dr.min() ? rnd.nextInt(dr.max() - dr.min() + 1) : 0));
	}

	static void split(List<ItemStack> out, ItemStack s) {
		int n = s.getCount();
		while (n > 0) {
			int k = Math.min(n, s.getMaxStackSize());
			out.add(s.copyWithCount(k));
			n -= k;
		}
	}

	/** Kills of {@code slot} this player has made (court ledger). */
	public static int kills(CourtState st, java.util.UUID player, Ruler slot) {
		return (int) (long) st.ledger.getOrDefault("kills|" + player + "|" + slot.key, 0L);
	}

	/** Rolls the uniques of one slot for one player with the DropRule and updates their kill and pity counters. */
	public static List<ItemStack> rollUniques(CourtState st, java.util.@org.jspecify.annotations.Nullable UUID player, Ruler slot, RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		FactionDef.RulerDef def = CourtRulers.def(st.faction, slot);
		if (def == null) return out;
		boolean first = (player == null || kills(st, player, slot) == 0) && !dev.elsewhere.kingdom_court.api.Succession.risen(st, slot);
		boolean sig = true;
		for (FactionDef.Drop d : def.drops()) {
			if (!unique(st.faction, d)) continue;
			String mk = "miss|" + player + "|" + slot.key + "|" + d.item();
			int misses = (int) (long) st.ledger.getOrDefault(mk, 0L);
			ItemStack s = stack(d, rnd);
			boolean hit = !s.isEmpty() && rnd.nextFloat() < chance(sig, first, sig ? 0 : misses);
			if (hit) {
				out.add(s);
				if (player != null) st.ledger.put(mk, 0L);
			} else if (player != null && !sig) st.ledger.put(mk, (long) misses + 1);
			sig = false;
		}
		if (player != null) st.ledger.put("kills|" + player + "|" + slot.key, (long) kills(st, player, slot) + 1);
		st.touch();
		return out;
	}

	/** The non-unique lines (currency, Seals, emeralds, materials) of a slot, each at its own chance. */
	public static List<ItemStack> rollGuaranteed(Identifier faction, Ruler slot, RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		FactionDef.RulerDef def = CourtRulers.def(faction, slot);
		if (def == null) return out;
		for (FactionDef.Drop d : def.drops()) {
			if (unique(faction, d)) continue;
			if (d.chance() < 1.0F && rnd.nextFloat() >= d.chance()) continue;
			ItemStack s = stack(d, rnd);
			if (!s.isEmpty()) split(out, s);
		}
		return out;
	}

	/** A rival leader's death drops: the guaranteed lines once, plus each credited player's own DropRule roll (the killer, else players within 48 blocks). */
	public static List<ItemStack> rollRival(net.minecraft.server.level.ServerLevel level, CourtState st, Ruler slot, net.minecraft.world.phys.Vec3 pos, net.minecraft.server.level.@org.jspecify.annotations.Nullable ServerPlayer killer) {
		List<ItemStack> out = rollGuaranteed(st.faction, slot, level.getRandom());
		List<net.minecraft.server.level.ServerPlayer> credited = new ArrayList<>();
		if (killer != null) credited.add(killer);
		else for (var p : level.players()) if (!p.isSpectator() && p.position().distanceToSqr(pos) < 48 * 48) credited.add(p);
		if (credited.isEmpty()) {
			for (ItemStack s : rollUniques(st, null, slot, level.getRandom())) out.add(s);
		} else for (var p : credited) for (ItemStack s : rollUniques(st, p.getUUID(), slot, level.getRandom())) out.add(s);
		return out;
	}
}
