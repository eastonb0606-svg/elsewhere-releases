package dev.elsewhere.gear2;

import dev.elsewhere.stub.gear2.SetBonusStub;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * gear-armor-a Amberstill Plate set bonus "Stillness": after 2 s without sprinting or attacking (hits and swings at
 * air) you gain +4 toughness (12 total, netherite's), lost the moment you sprint or swing.
 */
public final class Amberstill {
	private Amberstill() {}

	public static final Identifier STILLNESS = Gear2Content.id("amberstill.stillness");
	private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

	public static boolean wearing(Player p) {
		return SetBonusStub.active(p, Gear2Items.AMBERSTILL_SET);
	}

	/**
	 * A hit or swing (2.x): Stillness is lost at once and the 2 s start again. 3.0 "Brace" ({@code rebalance_gear2}, GEAR.md A4):
	 * hits and swings keep it; only sprinting (checked in {@link #update}) breaks it.
	 */
	public static void onAction(ServerPlayer p, long now) {
		if (Overhaul3.REBALANCE_GEAR2) return;
		LAST_ACTION.put(p.getUUID(), now);
		if (wearing(p)) apply(p, false);
	}

	public static void update(ServerPlayer p, long now) {
		if (!wearing(p)) {
			apply(p, false);
			return;
		}
		if (p.isSprinting()) LAST_ACTION.put(p.getUUID(), now);
		long last = LAST_ACTION.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2);
		apply(p, now - last >= Gear2Stats.STILLNESS_TICKS);
	}

	public static boolean still(Player p) {
		AttributeInstance t = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
		return t != null && t.getModifier(STILLNESS) != null;
	}

	static void apply(Player p, boolean on) {
		AttributeInstance t = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (t == null) return;
		boolean has = t.getModifier(STILLNESS) != null;
		if (on && !has) t.addTransientModifier(new AttributeModifier(STILLNESS, Gear2Stats.STILLNESS_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE));
		else if (!on && has) t.removeModifier(STILLNESS);
	}
}
