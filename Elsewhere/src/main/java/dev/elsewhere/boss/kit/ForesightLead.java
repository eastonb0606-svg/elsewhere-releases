package dev.elsewhere.boss.kit;

import java.lang.reflect.Method;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Foresight lead time (core lane, 2.0): how many ticks earlier a player sees a boss's attack hint. Stormcrest Helm = 5 ticks
 * (0.25 s, with the attack name in the action bar); gear2's Seer's Hourglass adds its own {@code SeersHourglass.leadTicks}
 * (3 ticks) when that module is present. Bosses: start the foresight hint {@code leadTicks(p)} ticks before the visible
 * tell, or call {@link #announce} at wind-up start (it also names the attack for players with a lead).
 */
public final class ForesightLead {
	private ForesightLead() {}

	public static final int STORMCREST_TICKS = 5;
	private static Method hourglass;
	private static boolean looked;

	public static int leadTicks(final Player p) {
		int lead = 0;
		Identifier helm = Identifier.fromNamespaceAndPath("elsewhere", "stormcrest_helm");
		if (BuiltInRegistries.ITEM.containsKey(helm) && p.getItemBySlot(EquipmentSlot.HEAD).is(BuiltInRegistries.ITEM.getValue(helm))) {
			lead += STORMCREST_TICKS;
		}
		if (!looked) {
			looked = true;
			try {
				hourglass = Class.forName("dev.elsewhere.gear2.SeersHourglass").getMethod("leadTicks", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				hourglass = null;
			}
		}
		if (hourglass != null) {
			try {
				lead += (Integer)hourglass.invoke(null, p);
			} catch (ReflectiveOperationException | ClassCastException e) {
				// ignore
			}
		}
		return lead;
	}

	/** Names the coming attack in the action bar of every player within 48 blocks who has a foresight lead. Returns how many. */
	public static int announce(final ServerLevel level, final LivingEntity boss, final Component attackName) {
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(boss) < 48 * 48 && leadTicks(p) > 0) {
				p.sendOverlayMessage(Component.literal("⚠ ").append(attackName).withStyle(ChatFormatting.YELLOW));
				n++;
			}
		}
		return n;
	}
}
