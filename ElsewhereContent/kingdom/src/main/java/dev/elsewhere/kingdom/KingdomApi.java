package dev.elsewhere.kingdom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The public API for the boss/castle/conquest layer. Everything here is server-side.
 * <pre>{@code
 * // a squad of a sergeant, 4 men-at-arms and 2 archers marching to the gate, hostile to players
 * List<KingdomSoldier> sq = KingdomApi.spawnSquad(level, pos, Factions.ALDERMARK, Squad.Formation.LINE,
 *     UnitTypes.SERGEANT, List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS,
 *     UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.ARCHER));
 * KingdomApi.squadOf(sq.get(0)).setObjective(gatePos);
 * KingdomApi.setFactionHostileToPlayers(server, Factions.ALDERMARK, false); // neutral until provoked
 * }</pre>
 */
public final class KingdomApi {
	private KingdomApi() {}

	/**
	 * Spawns one soldier of {@code unit} for {@code faction} at {@code pos}, joining {@code squad} if given.
	 * Respects the perf caps ({@link KingdomPerf#canSpawn}) and the squad size cap: returns null when refused.
	 * The entity type is the built-in one whose default unit is {@code unit} (else man-at-arms); the unit decides the loadout.
	 */
	public static @Nullable KingdomSoldier spawnSoldier(ServerLevel level, Identifier unit, Identifier faction, Vec3 pos, @Nullable Squad squad) {
		return spawnSoldier(level, KingdomEntities.typeFor(Factions.effectiveUnitId(faction, unit)), unit, faction, pos, squad);
	}

	/** As {@link #spawnSoldier(ServerLevel, Identifier, Identifier, Vec3, Squad)} with an explicit entity type (e.g. a Lord subclass). */
	public static <T extends KingdomSoldier> @Nullable T spawnSoldier(ServerLevel level, EntityType<T> type, Identifier unit, Identifier faction, Vec3 pos, @Nullable Squad squad) {
		if (squad != null && !squad.hasRoom()) return null;
		if (KingdomPerf.canSpawn(level, pos, Factions.unit(faction, unit).mounted()) != null) return null;
		T s = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (s == null) return null;
		s.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360F, 0F);
		s.setFaction(faction);
		s.setUnit(unit);
		s.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), EntitySpawnReason.MOB_SUMMONED, null);
		if (!level.addFreshEntity(s)) return null;
		KingdomPerf.onLoad(s);
		if (squad != null) KingdomState.get(level).join(squad, s);
		return s;
	}

	/** Creates an empty squad (fill it with {@link #spawnSoldier} or {@link #join}). */
	public static Squad createSquad(ServerLevel level, Identifier faction, Squad.Formation formation) {
		return KingdomState.get(level).createSquad(faction, formation);
	}

	/**
	 * Spawns a full squad around {@code pos}: the leader first, then the members in a loose block. Soldiers refused by
	 * the caps are skipped; the returned list holds the ones spawned (leader first if it spawned).
	 */
	public static List<KingdomSoldier> spawnSquad(ServerLevel level, BlockPos pos, Identifier faction, Squad.Formation formation, Identifier leaderUnit, List<Identifier> memberUnits) {
		Squad q = createSquad(level, faction, formation);
		List<KingdomSoldier> out = new ArrayList<>();
		Vec3 c = Vec3.atBottomCenterOf(pos);
		KingdomSoldier lead = spawnSoldier(level, leaderUnit, faction, c, q);
		if (lead != null) out.add(lead);
		int i = 0;
		for (Identifier u : memberUnits) {
			int k = i++;
			Vec3 p = c.add((k % 4) * 1.5 - 2.25, 0, -1.5 - (k / 4) * 1.5);
			KingdomSoldier s = spawnSoldier(level, u, faction, p, q);
			if (s != null) out.add(s);
		}
		if (out.isEmpty()) KingdomState.get(level).disband(q, level);
		return out;
	}

	/** Adds an existing soldier to a squad; false when the squad is full. */
	public static boolean join(Squad squad, KingdomSoldier s) {
		return s.level() instanceof ServerLevel sl && KingdomState.get(sl).join(squad, s);
	}

	/** Makes any mob (e.g. a Lord or the King) lead the squad. Its death triggers the morale/rout rules. */
	public static void setLeader(ServerLevel level, Squad squad, LivingEntity leader) {
		KingdomState.get(level).setLeader(squad, leader);
	}

	public static @Nullable Squad squadOf(KingdomSoldier s) {
		return s.squad();
	}

	// ---- 0.2.0 -----------------------------------------------------------------------------------------------------

	/**
	 * A field squad: a sergeant plus {@code size - 1} members rolled by {@link ArmyComposer} (about 10% cavalry, at most
	 * one of each support unit). Caps apply.
	 */
	public static List<KingdomSoldier> spawnPatrol(ServerLevel level, BlockPos pos, Identifier faction, int size) {
		return spawnSquad(level, pos, faction, Squad.Formation.LINE, UnitTypes.SERGEANT, ArmyComposer.members(level.getRandom(), Math.max(0, size - 1), true, faction));
	}

	/**
	 * A captain's company (15 soldiers): the captain leads squad A (a sergeant + 6), and squad B (a sergeant + 6) follows
	 * the captain as a wing. Returns the squads that spawned (A first). Caps apply (a lone player's cap is exactly 15).
	 */
	public static List<Squad> spawnCompany(ServerLevel level, BlockPos pos, Identifier faction) {
		List<Squad> out = new java.util.ArrayList<>();
		List<Identifier> a = new java.util.ArrayList<>();
		a.add(UnitTypes.SERGEANT);
		a.addAll(ArmyComposer.members(level.getRandom(), 6, true, faction));
		List<KingdomSoldier> sa = spawnSquad(level, pos, faction, Squad.Formation.LINE, UnitTypes.CAPTAIN, a);
		if (sa.isEmpty() || sa.get(0).squad() == null) return out;
		Squad qa = sa.get(0).squad();
		out.add(qa);
		List<KingdomSoldier> sb = spawnSquad(level, pos.offset(-6, 0, -6), faction, Squad.Formation.SHIELD_WALL, UnitTypes.SERGEANT,
			ArmyComposer.members(level.getRandom(), 6, true, faction));
		if (!sb.isEmpty() && sb.get(0).squad() != null) {
			Squad qb = sb.get(0).squad();
			Mob cap = qa.leaderEntity(level);
			if (cap != null) qb.setCommander(cap);
			out.add(qb);
		}
		return out;
	}

	/**
	 * A squad stored as data only (LOD ring 2): it costs nothing until a player comes within 80 blocks, then spawns at
	 * {@code pos} (caps allowing) with full health. Ideal for garrisons and patrols far from players.
	 */
	public static Squad createVirtualSquad(ServerLevel level, BlockPos pos, Identifier faction, Squad.Formation formation, Identifier leaderUnit, List<Identifier> memberUnits) {
		Squad q = createSquad(level, faction, formation);
		List<Identifier> all = new java.util.ArrayList<>();
		all.add(leaderUnit);
		all.addAll(memberUnits);
		int k = 0;
		for (Identifier u : all) {
			java.util.UUID id = java.util.UUID.randomUUID();
			var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(KingdomEntities.typeFor(u));
			double dx = k == 0 ? 0 : ((k - 1) % 4) * 1.5 - 2.25, dz = k == 0 ? 0 : -1.5 - ((k - 1) / 4) * 1.5;
			q.virtualMembers.add(new Squad.VirtualSoldier(id, type, u, (float) Factions.unit(faction, u).stats().health(), dx, 0, dz));
			if (k == 0) q.leader = id;
			else q.members.add(id);
			k++;
		}
		q.anchor = Vec3.atBottomCenterOf(pos);
		q.virtual = true;
		KingdomState.get(level).setDirty();
		return q;
	}

	/** Stores a squad as data now (false when a non-soldier leads it). */
	public static boolean virtualize(ServerLevel level, Squad squad) {
		return KingdomLod.virtualize(level, squad);
	}

	/** Spawns a virtual squad's soldiers now (0 when the caps leave no room for all of them). */
	public static int materialize(ServerLevel level, Squad squad) {
		return KingdomLod.materialize(level, squad);
	}

	/** Player aggression towards Aldermark (see {@link KingdomAggression}): Lords add 6, the King 10, scouts 2. */
	public static void addAggression(ServerPlayer player, float amount) {
		KingdomAggression.add(player.level(), player.getUUID(), amount);
	}

	public static float aggression(ServerPlayer player) {
		return KingdomAggression.get(player.level(), player.getUUID());
	}

	// ---- 2.1 multi-faction ------------------------------------------------------------------------------------------

	/** Player aggression towards {@code faction}: each faction keeps its own ledger per player. */
	public static void addAggression(ServerPlayer player, Identifier faction, float amount) {
		KingdomAggression.add(player.level(), player.getUUID(), faction, amount);
	}

	public static float aggression(ServerPlayer player, Identifier faction) {
		return KingdomAggression.get(player.level(), player.getUUID(), faction);
	}

	/** Sets the relation between two factions at runtime and saves it with the world (war, peace or allied). */
	public static void setStance(MinecraftServer server, Identifier a, Identifier b, Factions.Stance stance) {
		Factions.setStance(a, b, stance);
		KingdomState.get(server.overworld()).setStance(a, b, stance);
	}

	/** Player Standing with a faction (-100..100, see {@link Standing}); the faction's start value until something moves it. */
	public static int standing(ServerPlayer player, Identifier faction) {
		return Standing.get(player.level(), player.getUUID(), faction);
	}

	public static void setStanding(ServerPlayer player, Identifier faction, int value) {
		Standing.set(player.level(), player.getUUID(), faction, value);
	}

	/** The faction's ruler has been killed at least once (lifts Aldermark's Wary cap on Standing). */
	public static void markRulerKilled(MinecraftServer server, Identifier faction) {
		KingdomState.get(server.overworld()).setRulerKilled(faction);
	}

	/** Marks a faction's kingdom as ended (or revived): no more hunts or abstract clashes for it. Saved with the world. */
	public static void setFactionFallen(MinecraftServer server, Identifier faction, boolean fallen) {
		KingdomState.get(server.overworld()).setFallen(faction, fallen);
	}

	public static boolean isFactionFallen(MinecraftServer server, Identifier faction) {
		return KingdomState.get(server.overworld()).isFallen(faction);
	}

	/** Sets a faction's stance towards players and saves it with the world (overworld kingdom state). */
	public static void setFactionHostileToPlayers(MinecraftServer server, Identifier faction, boolean hostile) {
		Factions.setHostileToPlayers(faction, hostile);
		KingdomState.get(server.overworld()).setFactionHostile(faction, hostile);
	}

	// ---- 2.1 war layer ---------------------------------------------------------------------------------------------

	/**
	 * A brain boss of {@code faction} died (the court lane fires this): starts the 7-day empty seat, the 30-day degrade and the 90-day relearning ramp of WAR-AI section 4.
	 * Roles by keyword: sultana, wazir, falconer, jarl, hersir, hawk, dove, miser, king. False when nothing applies. See {@link dev.elsewhere.kingdom.war.WarApi#brainBossKilled}.
	 */
	public static boolean brainBossKilled(MinecraftServer server, Identifier faction, String role) {
		return dev.elsewhere.kingdom.war.WarApi.brainBossKilled(server, faction, role);
	}

	/**
	 * Registers a faction's squad-tactics profile (FIELD-TACTICS 1.7). Call from the mod initialiser. A faction without one runs Aldermark's baseline (the 2.0 goals, unchanged).
	 * See {@link dev.elsewhere.kingdom.tactics.TacticsProfile}, {@link dev.elsewhere.kingdom.tactics.SimpleTacticsProfile#builder} and {@link dev.elsewhere.kingdom.tactics.Behaviours#library()}.
	 */
	public static void registerTactics(Identifier faction, dev.elsewhere.kingdom.tactics.TacticsProfile profile) {
		dev.elsewhere.kingdom.tactics.TacticsRegistry.register(profile);
	}

	/** Alias of {@link #successionEvent} for the court's reflective hook name. */
	public static boolean onSuccession(MinecraftServer server, Identifier faction, String seat, String kind) {
		return successionEvent(server, faction, seat, kind);
	}

	/** The court fires a succession event: kind ruler_succession, risen, heir or vacant_forever (see {@link dev.elsewhere.kingdom.war.WarApi#successionEvent}). */
	public static boolean successionEvent(MinecraftServer server, Identifier faction, String seat, String kind) {
		return dev.elsewhere.kingdom.war.WarApi.successionEvent(server, faction, seat, kind);
	}
}
