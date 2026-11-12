package dev.elsewhere.kingdom;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * Who soldiers fight. Valid targets: hostile mobs ({@link Enemy}, except creepers unless provoked), soldiers of hostile
 * factions, players (only if the faction is hostile to players, or the player attacked this soldier/its squad), and any
 * entity that attacked this soldier or its squad in the last 30 s. Tamed pets and tamed horses are never targeted unless
 * they attacked. Creative/spectator players are never targeted.
 */
public final class KingdomTargeting {
	private KingdomTargeting() {}

	public static boolean canAttack(KingdomSoldier self, LivingEntity e) {
		return canAttack(self.factionId(), self.squad(), self, e);
	}

	public static boolean canAttack(Identifier faction, @Nullable Squad squad, @Nullable KingdomSoldier self, LivingEntity e) {
		if (e == self || !e.isAlive() || e.isSpectator() || e.isInvulnerable()) return false;
		if (e instanceof KingdomSoldier o) return Factions.hostile(faction, o.factionId());
		boolean provoked = (self != null && self.isProvokedBy(e))
			|| (squad != null && e.level() instanceof ServerLevel sl && squad.isProvokedBy(e.getUUID(), sl.getGameTime()));
		if (e instanceof Player p) {
			if (p.isCreative() || p.isSpectator()) return false;
			if (provoked || KingdomAggression.isMarked(p, faction)) return true;
			if (!Standing.attacksOnSight(p, faction)) return false;
			if (KingdomConfig.respectPvpGamerule && e.level() instanceof ServerLevel sl && !sl.getGameRules().get(GameRules.PVP)) return false;
			return true;
		}
		if (e instanceof TamableAnimal t && t.isTame()) return provoked;
		if (e instanceof AbstractHorse h && (h.isTamed() || h.isVehicle())) return provoked;
		if (e instanceof Enemy) return !(e instanceof Creeper) || provoked;
		return provoked;
	}
}
