package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.CourtRulers;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The duel right (RIVALS-DECISIONS 2.4). An Allied player may challenge one Lord (never the ruler: the King, or the seated Chair) once per in-game
 * week per kingdom: Hrafnholt's holmgang, Azmar's tourney, Mirewick's contract bout. The Lord yields at 10 percent health: he does not die, the
 * challenger gets the same drops as a kill (the boss DropRule, counted as a kill), and no Standing is lost. Damage that would take the Lord below
 * 10 percent is cancelled, so no hit, arrow or fall can kill him in a duel.
 * <p>For the war lane: {@link #isDueling(Entity, Entity)} is true while attacker and victim are a duelling pair; Standing losses and aggression
 * for hurting a soldier must be skipped for them.
 */
public final class Duel {
	private Duel() {}

	public static final float YIELD_FRACTION = 0.10F;
	public static final long TIMEOUT_TICKS = 20 * 60 * 5;
	public static final double LEAVE_DISTANCE = 64.0;

	public enum Result { OK, NOT_ALLIED, WEEK_LIMIT, NO_SUCH_LORD, IS_RULER, DEAD, NOT_FOUND, IN_DUEL, FALLEN }

	record Active(ServerPlayer player, Identifier faction, Ruler slot, long start) {}

	static final Map<UUID, Active> BY_LORD = new HashMap<>();
	/** Log of finished duels (test hook): {@code faction|slot|won}. */
	public static final List<String> LOG = new ArrayList<>();
	static final Map<String, String> NAMES = new HashMap<>(Map.of("hrafnholt", "holmgang", "azmar", "tourney", "mirewick", "contract bout"));
	static boolean registered;

	public static void init() {
		if (registered) return;
		registered = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Duel::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (!BY_LORD.isEmpty() && s.getTickCount() % 10 == 0) tick(s.overworld());
		});
	}

	/** Sets the display name of a faction's duel ("holmgang"); the default for unknown factions is "duel". */
	public static void registerName(Identifier faction, String name) {
		NAMES.put(faction.getPath(), name);
	}

	public static String nameOf(Identifier faction) {
		return NAMES.getOrDefault(faction.getPath(), "duel");
	}

	public static boolean isRuler(CourtState st, Ruler slot) {
		return st.oligarchy() ? st.isHolder(slot) : slot == Ruler.KING;
	}

	/** Weeks a player may challenge again in: 0 when they can now. */
	public static boolean usedThisWeek(ServerLevel level, CourtState st, UUID player) {
		return st.ledger.getOrDefault("duel|" + player, -1L) == level.getDefaultClockTime() / 24000L / 7;
	}

	public static Result challenge(ServerPlayer p, Identifier faction, Ruler slot) {
		ServerLevel level = (ServerLevel) p.level();
		CourtState st = CourtState.get(level, faction);
		if (st.fallen) return Result.FALLEN;
		if (Standing.bandOf(level, p.getUUID(), faction) != Standing.Band.ALLIED) return Result.NOT_ALLIED;
		if (!st.slots().contains(slot)) return Result.NO_SUCH_LORD;
		if (isRuler(st, slot)) return Result.IS_RULER;
		if (!st.alive(slot)) return Result.DEAD;
		if (usedThisWeek(level, st, p.getUUID())) return Result.WEEK_LIMIT;
		UUID id = st.rulerIds.get(slot);
		Entity e = id == null ? null : level.getEntity(id);
		if (!(e instanceof Mob lord) || !lord.isAlive()) return Result.NOT_FOUND;
		if (BY_LORD.containsKey(id)) return Result.IN_DUEL;
		st.ledger.put("duel|" + p.getUUID(), level.getDefaultClockTime() / 24000L / 7);
		st.touch();
		BY_LORD.put(id, new Active(p, faction, slot, level.getGameTime()));
		lord.setTarget(p);
		CourtLife.broadcast(level.getServer(), Component.literal("A " + nameOf(faction) + " begins: ").append(p.getDisplayName()).append(" against ").append(CourtRulers.displayName(faction, slot)).append("."));
		return Result.OK;
	}

	public static boolean active(UUID lord) {
		return BY_LORD.containsKey(lord);
	}

	/** True when {@code a} and {@code b} are a duelling player and Lord (either way round). */
	public static boolean isDueling(@Nullable Entity a, @Nullable Entity b) {
		if (a == null || b == null) return false;
		Active x = BY_LORD.get(a.getUUID());
		if (x != null && x.player.getUUID().equals(b.getUUID())) return true;
		Active y = BY_LORD.get(b.getUUID());
		return y != null && y.player.getUUID().equals(a.getUUID());
	}

	static boolean allowDamage(LivingEntity victim, DamageSource src, float amount) {
		Active d = BY_LORD.get(victim.getUUID());
		if (d == null || !(victim.level() instanceof ServerLevel level)) return true;
		if (victim.getHealth() - amount > victim.getMaxHealth() * YIELD_FRACTION) return true;
		if (src.getEntity() != null && src.getEntity().getUUID().equals(d.player.getUUID())) yieldDuel(level, victim, d);
		return false; // nothing but the challenger can end the duel, and nothing can kill a duelling Lord
	}

	static void yieldDuel(ServerLevel level, LivingEntity lord, Active d) {
		BY_LORD.remove(lord.getUUID());
		float floor = lord.getMaxHealth() * YIELD_FRACTION;
		if (lord.getHealth() > floor) lord.setHealth(floor);
		if (lord instanceof Mob m) m.setTarget(null);
		ServerPlayer p = d.player;
		CourtState st = CourtState.get(level, d.faction);
		if (p != null) {
			List<ItemStack> drops = CourtDrops.rollRival(level, st, d.slot, lord.position(), p);
			CourtLife.LAST_DROPS_BY.put(d.faction + "|" + d.slot.key + "|duel", drops);
			for (ItemStack s : drops) {
				ItemEntity ie = new ItemEntity(level, lord.getX(), lord.getY() + 0.5, lord.getZ(), s);
				ie.setDefaultPickUpDelay();
				level.addFreshEntity(ie);
			}
			p.sendSystemMessage(CourtRulers.displayName(d.faction, d.slot).copy().append(Component.literal(" yields. The " + nameOf(d.faction) + " is yours.")));
		}
		LOG.add(d.faction + "|" + d.slot.key + "|won");
		if (p != null) CourtEvents.DUEL_ENDED.invoker().onDuelEnded(level, p, d.faction, d.slot.key, true);
	}

	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (Map.Entry<UUID, Active> e : List.copyOf(BY_LORD.entrySet())) {
			Active d = e.getValue();
			Entity lord = level.getEntity(e.getKey());
			ServerPlayer p = d.player;
			boolean over = lord == null || !lord.isAlive() || p == null || !p.isAlive() || now - d.start > TIMEOUT_TICKS || p.distanceTo(lord) > LEAVE_DISTANCE;
			if (over) {
				BY_LORD.remove(e.getKey());
				if (lord instanceof Mob m) {
					m.setTarget(null);
					m.setHealth(m.getMaxHealth());
				}
				LOG.add(d.faction + "|" + d.slot.key + "|lost");
				if (p != null) CourtEvents.DUEL_ENDED.invoker().onDuelEnded(level, p, d.faction, d.slot.key, false);
				continue;
			}
			if (lord instanceof Mob m && m.getTarget() != p) m.setTarget(p);
		}
	}

	/** Test hook: runs the duel clock once. */
	public static void tickForTests(ServerLevel level) {
		tick(level);
	}

	public static boolean standingsUntouched(UUID player, Identifier faction, ServerLevel level, int before) {
		return Standing.get(level, player, faction) == before && Factions.exists(faction);
	}
}
