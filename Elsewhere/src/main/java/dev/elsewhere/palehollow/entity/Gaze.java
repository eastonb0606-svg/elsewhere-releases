package dev.elsewhere.palehollow.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import dev.elsewhere.registry.ModItems;

/** Shared "is a player looking at me" checks for Stillness mobs and gaze attacks. */
public final class Gaze {
	private Gaze() {
	}

	/** Same test as the vanilla Creaking: view cone, line of sight, at eye, feet and middle heights. */
	public static boolean isWatchedBy(final LivingEntity self, final Player player) {
		if (player.isSpectator() || !player.isAlive()) {
			return false;
		}
		return self.isLookingAtMe(player, 0.5, false, true, self.getEyeY(), self.getY() + 0.5 * self.getScale(), (self.getEyeY() + self.getY()) / 2.0);
	}

	public static boolean isWatched(final ServerLevel level, final LivingEntity self, final double range) {
		for (Player p : level.players()) {
			if (p.distanceToSqr(self) < range * range && !p.isCreative() && isWatchedBy(self, p)) {
				return true;
			}
		}
		return false;
	}

	/** Eyeblossom Circlet wearers are invisible to gaze attacks. */
	public static boolean gazeImmune(final Player player) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		return head.is(ModItems.EYEBLOSSOM_CIRCLET);
	}
}
