package dev.elsewhere.stub.summoner;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * STUB of arsenal's Calling (ARSENAL.md rev 3 §3b): Summoner Calling = worn armor pieces tagged
 * {@code elsewhere:summoner_armor} (0..4). At merge, point {@link #summoner} at arsenal's Calling API.
 */
public final class CallingStub {
	private CallingStub() {}

	public static final TagKey<Item> SUMMONER_ARMOR = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "summoner_armor"));
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Set by the summoner self-test: keep the 2.1 piece-count tuning while it runs (its numbers are written for it). */
	public static volatile boolean forceLegacy;
	private static boolean lookedM, v3Looked, v3;
	private static java.lang.reflect.Method arsenalMastery;

	/** 3.0 Callings switch of arsenal ({@code dev.elsewhere.arsenal.Overhaul3.CALLINGS}); false when arsenal is absent. */
	public static boolean callingsV3() {
		if (!v3Looked) {
			v3Looked = true;
			try {
				v3 = Class.forName("dev.elsewhere.arsenal.Overhaul3").getField("CALLINGS").getBoolean(null);
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				v3 = false;
			}
		}
		return v3;
	}

	/** Summoner mastery M (0..1) from arsenal's {@code Calling.summonerMastery}, or -1 = use the 2.1 tables (arsenal absent or switch off). */
	public static float mastery(Player p) {
		if (forceLegacy) return -1F;
		if (!lookedM) {
			lookedM = true;
			try {
				arsenalMastery = Class.forName("dev.elsewhere.arsenal.spell.Calling").getMethod("summonerMastery", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				arsenalMastery = null;
			}
		}
		if (arsenalMastery == null) return -1F;
		try {
			return ((Number) arsenalMastery.invoke(null, p)).floatValue();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1F;
		}
	}

	private static boolean looked;
	private static java.lang.reflect.Method arsenalSummoner;

	/** Soft link: arsenal's {@code dev.elsewhere.arsenal.spell.Calling.summoner(Player)} when present (same tag, same count). */
	public static int summoner(Player p) {
		if (!looked) {
			looked = true;
			try {
				arsenalSummoner = Class.forName("dev.elsewhere.arsenal.spell.Calling").getMethod("summoner", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				arsenalSummoner = null;
			}
		}
		if (arsenalSummoner != null) {
			try {
				return ((Number) arsenalSummoner.invoke(null, p)).intValue();
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall back to the local count
			}
		}
		int n = 0;
		for (EquipmentSlot s : ARMOR) if (p.getItemBySlot(s).is(SUMMONER_ARMOR)) n++;
		return n;
	}
}
