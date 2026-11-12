package dev.elsewhere.boss.kit;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Attack-hint particles are shown only to players holding or wearing an item of {@code #elsewhere:foresight_gear}
 * (Stormcrest Helm, Seer's Hourglass, ...). Bosses call {@link #sees} before spawning a hint particle for a player.
 */
public final class ForesightGear {
	private ForesightGear() {}

	public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "foresight_gear"));

	public static boolean sees(Player p) {
		if (p.getMainHandItem().is(TAG) || p.getOffhandItem().is(TAG)) return true;
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (p.getItemBySlot(s).is(TAG)) return true;
		}
		return false;
	}

	/** Sends a hint particle only to players with foresight gear. */
	public static void hint(ServerLevel level, ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		for (ServerPlayer pl : level.players()) {
			if (sees(pl)) level.sendParticles(pl, p, true, true, x, y, z, count, dx, dy, dz, speed);
		}
	}
}
