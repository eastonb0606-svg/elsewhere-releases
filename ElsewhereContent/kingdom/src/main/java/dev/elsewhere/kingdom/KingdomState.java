package dev.elsewhere.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Per-level saved kingdom state ({@code data/elsewhere/kingdom.dat}): the squads of that level, and (overworld only)
 * runtime faction hostility overrides. Get it with {@link #get(ServerLevel)}.
 */
public final class KingdomState extends SavedData {
	public static final Codec<KingdomState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Squad.CODEC.listOf().optionalFieldOf("squads", List.of()).forGetter(s -> List.copyOf(s.squads.values())),
		Codec.unboundedMap(Identifier.CODEC, Codec.BOOL).optionalFieldOf("faction_hostile_to_players", Map.of()).forGetter(s -> s.factionHostile),
		Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, Codec.FLOAT).optionalFieldOf("aggression", Map.of()).forGetter(s -> s.aggression),
		Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("last_hunt", Map.of()).forGetter(s -> s.lastHunt),
		// multi-faction (2.1): the ledgers of every faction except Aldermark (whose ledger stays in "aggression"), stances, fallen factions, clash log
		Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, Codec.FLOAT)).optionalFieldOf("faction_aggression", Map.of()).forGetter(s -> s.otherAggression),
		Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, Codec.LONG)).optionalFieldOf("faction_last_hunt", Map.of()).forGetter(s -> s.otherLastHunt),
		Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("faction_stances", Map.of()).forGetter(s -> s.stances),
		Identifier.CODEC.listOf().optionalFieldOf("fallen_factions", List.of()).forGetter(s -> List.copyOf(s.fallen)),
		FactionConflict.Record.CODEC.listOf().optionalFieldOf("clashes", List.of()).forGetter(s -> s.clashes),
		Codec.LONG.optionalFieldOf("clash_count", 0L).forGetter(s -> s.clashCount),
		Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, Codec.INT)).optionalFieldOf("standing", Map.of()).forGetter(s -> s.standing),
		Identifier.CODEC.listOf().optionalFieldOf("rulers_killed", List.of()).forGetter(s -> List.copyOf(s.rulerKilled))
	).apply(i, (sq, fh, ag, lh, oag, olh, st, fl, cl, cc, sd, rk) -> {
		KingdomState s = new KingdomState();
		for (Squad q : sq) s.squads.put(q.id, q);
		s.factionHostile.putAll(fh);
		s.aggression.putAll(ag);
		s.lastHunt.putAll(lh);
		oag.forEach((k, v) -> s.otherAggression.put(k, new LinkedHashMap<>(v)));
		olh.forEach((k, v) -> s.otherLastHunt.put(k, new LinkedHashMap<>(v)));
		s.stances.putAll(st);
		s.fallen.addAll(fl);
		s.clashes.addAll(cl);
		s.clashCount = cc;
		sd.forEach((k, v) -> s.standing.put(k, new LinkedHashMap<>(v)));
		s.rulerKilled.addAll(rk);
		return s;
	}));

	public static final SavedDataType<KingdomState> TYPE = new SavedDataType<>(KingdomContent.id("kingdom"), KingdomState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	final Map<UUID, Squad> squads = new LinkedHashMap<>();
	final Map<Identifier, Boolean> factionHostile = new LinkedHashMap<>();
	/** Overworld only: per-player aggression (see {@link KingdomAggression}) and the game time of their last hunt. */
	final Map<UUID, Float> aggression = new LinkedHashMap<>();
	final Map<UUID, Long> lastHunt = new LinkedHashMap<>();
	/** 2.1: aggression ledgers and last hunts of every faction but Aldermark (Aldermark's are {@link #aggression}/{@link #lastHunt}, as in 2.0). */
	final Map<Identifier, Map<UUID, Float>> otherAggression = new LinkedHashMap<>();
	final Map<Identifier, Map<UUID, Long>> otherLastHunt = new LinkedHashMap<>();
	/** Saved stance overrides, key {@code "<a>|<b>"} (ids sorted), value WAR/PEACE/ALLIED. */
	final Map<String, String> stances = new LinkedHashMap<>();
	/** Factions whose kingdom has ended: no hunts, no clashes. */
	final java.util.Set<Identifier> fallen = new java.util.LinkedHashSet<>();
	/** Log of resolved abstract clashes (newest last, at most {@link FactionConflict#MAX_RECORDS}). */
	final List<FactionConflict.Record> clashes = new java.util.ArrayList<>();
	long clashCount;
	/** Player Standing per faction (see {@link Standing}); factions whose ruler has been killed at least once. */
	final Map<Identifier, Map<UUID, Integer>> standing = new LinkedHashMap<>();
	final java.util.Set<Identifier> rulerKilled = new java.util.LinkedHashSet<>();
	private boolean factionsApplied;

	public KingdomState() {}

	public static KingdomState get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable Squad squad(UUID id) {
		return squads.get(id);
	}

	public Collection<Squad> squads() {
		return Collections.unmodifiableCollection(squads.values());
	}

	/** Creates an empty squad of {@code faction}. */
	public Squad createSquad(Identifier faction, Squad.Formation formation) {
		Squad s = new Squad(UUID.randomUUID(), faction, formation);
		squads.put(s.id, s);
		setDirty();
		return s;
	}

	/** Adds a soldier: the first one (or an officer when the squad has no leader) becomes leader. False when the squad is full. */
	public boolean join(Squad squad, KingdomSoldier s) {
		if (squad.leader != null && squad.leader.equals(s.getUUID()) || squad.members.contains(s.getUUID())) return true;
		if (!squad.hasRoom()) return false;
		Squad old = s.squad();
		if (old != null && old != squad) leave(s);
		if (squad.leader == null) squad.leader = s.getUUID();
		else squad.members.add(s.getUUID());
		s.setSquadId(squad.id);
		setDirty();
		return true;
	}

	/** Makes any mob (e.g. a boss) the squad's leader; the previous leader becomes a member. */
	public void setLeader(Squad squad, LivingEntity leader) {
		if (squad.leader != null && !squad.leader.equals(leader.getUUID())) squad.members.add(0, squad.leader);
		squad.members.remove(leader.getUUID());
		squad.leader = leader.getUUID();
		if (leader instanceof KingdomSoldier s) s.setSquadId(squad.id);
		setDirty();
	}

	public void leave(KingdomSoldier s) {
		Squad q = s.squad();
		s.setSquadId(null);
		if (q == null) return;
		q.members.remove(s.getUUID());
		if (s.getUUID().equals(q.leader)) {
			q.leader = null;
			if (s.level() instanceof ServerLevel sl) q.promote(sl);
		}
		if (q.leader == null && q.members.isEmpty()) squads.remove(q.id);
		setDirty();
	}

	public void disband(Squad q, ServerLevel level) {
		for (KingdomSoldier s : q.memberEntities(level)) s.setSquadId(null);
		if (q.leaderEntity(level) instanceof KingdomSoldier ls) ls.setSquadId(null);
		squads.remove(q.id);
		setDirty();
	}

	void onDeath(ServerLevel level, LivingEntity dead, DamageSource src) {
		UUID id = dead.getUUID();
		for (Squad q : List.copyOf(squads.values())) {
			if (id.equals(q.leader)) {
				q.morale = Math.max(0, q.morale - 50);
				q.leader = null;
				int routed = 0;
				List<KingdomSoldier> mem = q.memberEntities(level);
				if (KingdomConfig.useMorale) {
					for (KingdomSoldier s : mem) {
						if (level.getRandom().nextDouble() < KingdomConfig.routChanceOnOfficerDeath) {
							s.rout(KingdomConfig.routTicks);
							routed++;
						}
					}
				}
				KingdomEvents.LEADER_DIED.invoker().onLeaderDied(level, q, dead, routed);
				q.promote(level);
				if (q.leader == null && q.members.isEmpty()) squads.remove(q.id);
				setDirty();
			} else if (q.members.remove(id)) {
				int loss = 5;
				if (dead instanceof KingdomSoldier ds && ds.unit().ability() == UnitType.Ability.STANDARD) loss = 25; // the standard falls
				if (!q.halvedHit && q.peak >= 4 && q.livingCount(level) * 2 <= q.peak) {
					q.halvedHit = true; // heavy losses
					loss += 15;
				}
				q.morale = Math.max(0, q.morale - loss);
				if (KingdomConfig.useMorale && q.morale < BREAK_MORALE) {
					// the squad breaks: everyone flees, then regroups at a steadier morale
					for (KingdomSoldier s : q.memberEntities(level)) s.rout(KingdomConfig.routTicks);
					if (q.leaderEntity(level) instanceof KingdomSoldier ls) ls.rout(KingdomConfig.routTicks);
					q.morale = REGROUP_MORALE;
				}
				if (q.leader == null && q.members.isEmpty()) squads.remove(q.id);
				setDirty();
			}
		}
		if (dead instanceof KingdomSoldier s) {
			KingdomAggression.onSoldierDied(level, s, src);
			KingdomEvents.SOLDIER_DIED.invoker().onSoldierDied(level, s, src);
		}
	}

	/** Below this morale a squad breaks and flees; it regroups at {@link #REGROUP_MORALE}. */
	public static final int BREAK_MORALE = 20, REGROUP_MORALE = 40;

	/** Squads whose virtual record or entities should be removed (dismissed hunts). */
	void removeSquad(Squad q) {
		squads.remove(q.id);
		setDirty();
	}

	void tick(ServerLevel level) {
		if (!factionsApplied) {
			factionsApplied = true;
			factionHostile.forEach(Factions::setHostileToPlayers);
			stances.forEach((k, v) -> {
				String[] p = k.split("\\|");
				Identifier a = p.length == 2 ? Identifier.tryParse(p[0]) : null, b = p.length == 2 ? Identifier.tryParse(p[1]) : null;
				if (a != null && b != null) Factions.setStance(a, b, Factions.Stance.parse(v, Factions.Stance.WAR));
			});
		}
		if (level == level.getServer().overworld()) KingdomAggression.tick(level, this);
		if (squads.isEmpty()) return;
		long now = level.getGameTime();
		for (Squad q : List.copyOf(squads.values())) q.tick(level, now);
		FactionConflict.tick(level, this, now);
		setDirty();
	}

	/** Tests: forget every squad of this level. */
	public void clearForTests() {
		squads.clear();
		setDirty();
	}

	/** Saved faction overrides (overworld). */
	public Map<Identifier, Boolean> factionOverrides() {
		return Collections.unmodifiableMap(factionHostile);
	}

	// ---- multi-faction state ---------------------------------------------------------------------------------------

	/** The per-player aggression ledger of {@code faction} (Aldermark's is the 2.0 map). */
	Map<UUID, Float> ledger(Identifier faction) {
		if (Factions.ALDERMARK.equals(faction)) return aggression;
		return otherAggression.computeIfAbsent(faction, k -> new LinkedHashMap<>());
	}

	Map<UUID, Long> hunts(Identifier faction) {
		if (Factions.ALDERMARK.equals(faction)) return lastHunt;
		return otherLastHunt.computeIfAbsent(faction, k -> new LinkedHashMap<>());
	}

	/** Every faction that has (or may have) an aggression ledger: Aldermark plus the others with entries. */
	List<Identifier> ledgerFactions() {
		List<Identifier> out = new java.util.ArrayList<>();
		out.add(Factions.ALDERMARK);
		for (var e : otherAggression.entrySet()) if (!e.getValue().isEmpty()) out.add(e.getKey());
		return out;
	}

	/** Saved stance overrides ("a|b" -> stance), read-only. */
	public Map<String, String> savedStances() {
		return Collections.unmodifiableMap(stances);
	}

	/** A player's aggression towards {@code faction} as stored in this state (tests inspect decoded copies with it). */
	public float aggressionOf(Identifier faction, UUID player) {
		return ledger(faction).getOrDefault(player, 0F);
	}

	/** Tests: the raw Standing ledger of a faction (null when none recorded and {@code create} is false). */
	public @Nullable Map<UUID, Integer> standingLedger(Identifier faction, boolean create) {
		return create ? standing.computeIfAbsent(faction, k -> new LinkedHashMap<>()) : standing.get(faction);
	}

	/** Factions that have any recorded Standing entries. */
	public java.util.Set<Identifier> standingFactions() {
		return standing.keySet();
	}

	public boolean isRulerKilled(Identifier faction) {
		return rulerKilled.contains(faction);
	}

	void setRulerKilled(Identifier faction) {
		if (rulerKilled.add(faction)) setDirty();
	}

	public boolean isFallen(Identifier faction) {
		return fallen.contains(faction);
	}

	void setFallen(Identifier faction, boolean f) {
		if (f ? fallen.add(faction) : fallen.remove(faction)) setDirty();
	}

	void setStance(Identifier a, Identifier b, Factions.Stance s) {
		String ka = a.toString(), kb = b.toString();
		String key = ka.compareTo(kb) <= 0 ? ka + "|" + kb : kb + "|" + ka;
		stances.put(key, s.name());
		setDirty();
	}

	/** The resolved clashes of this level, oldest first. */
	public List<FactionConflict.Record> clashes() {
		return Collections.unmodifiableList(clashes);
	}

	void setFactionHostile(Identifier faction, boolean hostile) {
		Boolean def = Factions.DEFAULT_HOSTILE.get(faction);
		if (def != null && def == hostile) factionHostile.remove(faction);
		else factionHostile.put(faction, hostile);
		setDirty();
	}
}
