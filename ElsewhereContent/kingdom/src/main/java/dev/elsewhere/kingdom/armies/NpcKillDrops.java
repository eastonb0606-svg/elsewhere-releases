package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A soldier killed by anything but a player (or a player's pet or minion) drops no equipment, so mass battles between kingdoms
 * don't litter item entities (ARMIES.md 3.4 / 9.5). Player kills keep the normal 2% armor / 4% weapon odds. Runs once per death.
 */
public final class NpcKillDrops {
	private NpcKillDrops() {}

	public static long suppressed;

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof KingdomSoldier s && !byPlayer(s, source)) {
				for (EquipmentSlot slot : EquipmentSlot.values()) s.setDropChance(slot, 0.0F);
				suppressed++;
			}
			return true;
		});
	}

	static boolean byPlayer(KingdomSoldier s, DamageSource source) {
		Entity e = source.getEntity();
		if (e instanceof Player) return true;
		if (e instanceof OwnableEntity o && o.getOwnerReference() != null && s.level() instanceof ServerLevel sl)
			return sl.getPlayerByUUID(o.getOwnerReference().getUUID()) != null;
		return false;
	}
}
