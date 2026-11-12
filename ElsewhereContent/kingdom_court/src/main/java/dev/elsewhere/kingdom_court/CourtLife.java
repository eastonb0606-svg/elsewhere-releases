package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.war.WarApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.CourtRulers;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CourtSite;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The court at runtime: site choice at server start, garrison posts and the rulers at their stations, daily routines
 * (dawn bell, gates and braziers, drill, the King's noon court, half the drill squad asleep at night), rare roaming
 * Lords leading a squad, deaths with drops, succession, the kingdom's fall (end state), realm music, temporary blocks.
 */
public final class CourtLife {
	private CourtLife() {}

	public static final String HOUND_TAG = "elsewhere_court_hound";
	public static final String REMNANT_TAG = "elsewhere_court_remnant";
	public static final String GARRISON_TAG = "elsewhere_court_garrison";
	public static final double REALM_RADIUS = 112;
	/** Per player and minute, far from spawn: chance a living Lord appears leading a squad (about 0.3 per hour). */
	public static double roamChancePerMinute = 0.005;
	public static boolean testMode;

	// ---- temporary blocks (Castellan's portcullis) -------------------------------------------------------------------

	record Temp(ServerLevel level, BlockPos pos, BlockState state, long until) {}

	static final List<Temp> TEMP = new ArrayList<>();

	public static boolean placeTemp(ServerLevel level, BlockPos pos, BlockState s, long until) {
		if (!level.getBlockState(pos).isAir()) return false;
		level.setBlock(pos, s, 3);
		TEMP.add(new Temp(level, pos, s, until));
		return true;
	}

	public static int tempCount() {
		return TEMP.size();
	}

	static void tickTemp(MinecraftServer server, boolean all) {
		long now = server.overworld().getGameTime();
		TEMP.removeIf(t -> {
			if (!all && now < t.until) return false;
			if (t.level.getBlockState(t.pos).is(t.state.getBlock())) t.level.removeBlock(t.pos, false);
			return true;
		});
	}

	// ---- init -----------------------------------------------------------------------------------------------------

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(CourtLife::started);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> tickTemp(srv, true));
		ServerTickEvents.END_SERVER_TICK.register(CourtLife::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(CourtLife::afterDeath);
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			// RIVALS-DECISIONS 13/14: a leader loaded from disk gets the current unit stats and the 300 cap too (health is only ever clamped down)
			if (e instanceof CourtRuler cr) {
				CourtState cs = CourtState.peek(level.getServer(), cr.factionId());
				if (cs != null) cr.refreshGating(cs);
			} else if (e instanceof Mob m && dev.elsewhere.kingdom_court.api.Leaders.of(m) != null) {
				var ref = dev.elsewhere.kingdom_court.api.Leaders.of(m);
				CourtState cs = CourtState.peek(level.getServer(), ref.faction());
				Ruler slot = Ruler.byKey(ref.slot());
				if (cs != null && slot != null) dev.elsewhere.kingdom_court.api.Leaders.applyGating(m, cs, slot);
				dev.elsewhere.kingdom_court.api.HpCap.clamp(m);
			}
			if (e instanceof KingdomSoldier s && !(e instanceof CourtRuler) && CourtState.isFallen(level.getServer(), s.factionId())) PENDING_END.add(s);
		});
		registerCastleLocator();
		// Wary players are attacked only inside a realm or near its castle (RIVALS-DECISIONS 2.1): the war lane asks us where the realm is.
		Standing.setRealmLocator((level, pos, faction) -> {
			CourtState st = CourtState.peek(level.getServer(), faction);
			return st != null && st.site != null && Math.hypot(pos.getX() - st.site.getX(), pos.getZ() - st.site.getZ()) < REALM_RADIUS + 64;
		});
	}

	/** The courts the tick looks after (one per realm faction, Aldermark first). Rebuilt by {@link #syncRealms}. */
	static final List<CourtState> STATES = new ArrayList<>();

	static void started(MinecraftServer server) {
		syncRealms(server);
	}

	/**
	 * Makes sure every faction with a realm has a court state, a chosen site and a remnant faction. A faction has exactly one realm in the whole world: its site is
	 * chosen once (the {@code siteChosen} flag is saved) and CourtWorld.Placement only ever builds at that one site. Aldermark is handled first, so its site is the 2.0 one;
	 * the rivals that still have no site are then placed together in Aldermark's 90-degree wedge (RIVALS-DECISIONS 15, {@link CourtSite#chooseRivals}), once per world.
	 * Call again after registering a faction at runtime (tests do).
	 */
	public static void syncRealms(MinecraftServer server) {
		ServerLevel ow = server.overworld();
		STATES.clear();
		List<Identifier> realms = FactionDefs.realmFactions();
		List<CourtState> sts = new ArrayList<>();
		for (Identifier f : realms) {
			CourtState st = CourtState.get(server, f);
			if (!Factions.ALDERMARK.equals(f)) Factions.registerRemnant(CourtContent.remnantOf(f), f, 0x77706A);
			sts.add(st);
		}
		BlockPos aldermark = null;
		for (CourtState st : sts) {
			if (!Factions.ALDERMARK.equals(st.faction)) continue;
			if (!st.siteChosen) {
				st.site = CourtSite.choose(ow, st.faction, List.of());
				st.siteChosen = true;
				st.touch();
				CourtContent.LOG.info("{} realm site: {}", st.faction, st.site);
			}
			aldermark = st.site;
		}
		List<Identifier> todo = new ArrayList<>();
		List<BlockPos> fixed = new ArrayList<>();
		for (CourtState st : sts) {
			if (st.siteChosen && st.site != null) fixed.add(st.site);
			if (!st.siteChosen) todo.add(st.faction);
		}
		if (!todo.isEmpty()) {
			Map<Identifier, BlockPos> got = CourtSite.chooseRivals(ow, todo, aldermark, fixed);
			for (CourtState st : sts) {
				if (st.siteChosen) continue;
				st.site = got.get(st.faction);
				st.waterBearing = CourtSite.WATER_BEARING.getOrDefault(st.faction, Double.NaN);
				st.waterDist = CourtSite.WATER_SHORE.getOrDefault(st.faction, 0);
				st.siteChosen = true;
				st.touch();
				CourtContent.LOG.info("{} realm site: {}", st.faction, st.site);
			}
		}
		for (CourtState st : sts) {
			Identifier f = st.faction;
			if (st.site != null) WarApi.setCastle(server, f, st.site);
			CourtWorld.setSite(ow.getSeed(), f, st.site);
			CourtWorld.setWater(ow.getSeed(), f, st.site != null && st.waterDist > 0 && !Double.isNaN(st.waterBearing) ? new int[] {st.waterBearing > 0 ? 1 : 3, st.waterDist} : null);
			if (st.fallen) {
				KingdomApi.setFactionHostileToPlayers(server, f, false);
				KingdomApi.setFactionFallen(server, f, true);
			}
			STATES.add(st);
		}
	}

	// ---- tick --------------------------------------------------------------------------------------------------------

	static final List<KingdomSoldier> PENDING_END = new ArrayList<>();
	public static final Map<String, Long> IN_REALM = new HashMap<>();
	static final Map<String, Integer> MISSING = new HashMap<>();

	static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		if (!TEMP.isEmpty() && t % 10 == 0) tickTemp(server, false);
		if (!PENDING_END.isEmpty()) {
			for (KingdomSoldier s : List.copyOf(PENDING_END)) if (s.isAlive()) endState(s);
			PENDING_END.clear();
		}
		ServerLevel ow = server.overworld();
		for (CourtState st : STATES) tickRealm(server, ow, st, t);
		if (t % 200 == 3) wanderRemnants(ow);
	}

	static void tickRealm(MinecraftServer server, ServerLevel ow, CourtState st, long t) {
		if (st.site == null) return;
		if (t % 40 == 7) {
			boolean near = testMode || nearestPlayerDist(ow, st.site) < REALM_RADIUS + 16;
			if (near && ow.isPositionEntityTicking(st.site)) {
				if (!st.built) {
					var start = ow.structureManager().getStructureAt(st.site, ow.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getValueOrThrow(CourtWorld.structureKey(st.faction)));
					if (start.isValid() || ow.getBlockState(CastleLayout.throne(st.site)).is(Blocks.DARK_OAK_STAIRS)) {
						st.built = true;
						st.touch();
					}
				}
				if (st.built && !st.fallen) {
					garrison(ow, st);
					schedule(ow, st);
				}
			}
		}
		if (t % 100 == 13) realmMusic(ow, st);
		if (t % 100 == 53 && st.darkLanterns && st.ledger.getOrDefault("vault_open", 0L) == 1L && st.site != null && ow.isLoaded(vaultPos(st.site))) openVault(ow, st);
		if (t % 1200 == 31 && !st.fallen) {
			if (st.oligarchy()) council(ow, st);
			else dev.elsewhere.kingdom_court.api.Succession.tick(ow, st);
			roam(ow, st);
		}
		if (t % 40 == 21 && st.roaming != null) tickRoaming(ow, st);
	}

	static double nearestPlayerDist(ServerLevel level, BlockPos p) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer pl : level.players()) if (!pl.isSpectator()) best = Math.min(best, Math.sqrt(pl.distanceToSqr(Vec3.atCenterOf(p))));
		return best;
	}

	// ---- garrison ------------------------------------------------------------------------------------------------

	public record Post(String id, BlockPos pos, Squad.Formation formation, net.minecraft.resources.Identifier leader, List<net.minecraft.resources.Identifier> members) {}

	public static List<Post> posts(BlockPos o) {
		var K = UnitTypes.KNIGHT;
		var M = UnitTypes.MAN_AT_ARMS;
		var S = UnitTypes.SERGEANT;
		return List.of(
			new Post("throne_guard", CastleLayout.hall(o), Squad.Formation.LINE, K, List.of(K, K, K)),
			new Post("gate", CastleLayout.gateInside(o), Squad.Formation.SHIELD_WALL, S, List.of(M, M, M)),
			new Post("drill", CastleLayout.yard(o), Squad.Formation.LINE, S, List.of(M, M, M)),
			new Post("wall_west", CastleLayout.wallTop(o, true), Squad.Formation.LINE, UnitTypes.ARCHER, List.of()),
			new Post("wall_east", CastleLayout.wallTop(o, false), Squad.Formation.LINE, UnitTypes.ARCHER, List.of()),
			new Post("town_west", CastleLayout.town(o, true).offset(0, 0, 8), Squad.Formation.COLUMN, S, List.of(M, M)),
			new Post("town_east", CastleLayout.town(o, false).offset(0, 0, 8), Squad.Formation.COLUMN, S, List.of(M, M)));
	}

	public static BlockPos rulerPost(BlockPos o, Ruler r, boolean noon) {
		return switch (r) {
			case KING -> noon ? CastleLayout.throneFront(o) : CastleLayout.hall(o);
			case MARSHAL -> CastleLayout.stables(o);
			case CASTELLAN -> CastleLayout.gateInside(o).offset(0, 0, -4);
			case MASTER_AT_ARMS -> CastleLayout.yard(o).offset(14, 0, -4);
			case HUNTSMAN -> CastleLayout.at(o, -14, 0, -4);
			case CHANCELLOR -> CastleLayout.at(o, 4, 0, -20);
		};
	}

	static boolean postAlive(ServerLevel level, CourtState st, String id) {
		UUID q = st.postSquads.get(id);
		if (q == null) return false;
		Squad s = KingdomState.get(level).squad(q);
		return s != null && s.size() > 0;
	}

	/** Spawns missing posts (first visit; then reinforcements at most every 2 in-game days) and the living rulers. */
	public static int garrison(ServerLevel level, CourtState st) {
		BlockPos o = st.site;
		int spawned = 0;
		long day = level.getDefaultClockTime() / 24000L;
		for (Post p : posts(o)) {
			if (!level.isPositionEntityTicking(p.pos)) continue;
			if (p.id.equals("drill") && st.sleepers > 0) continue;
			if (postAlive(level, st, p.id)) continue;
			Long last = st.postDay.get(p.id);
			if (last != null && day - last < 2) continue;
			List<KingdomSoldier> sq = KingdomApi.spawnSquad(level, p.pos, st.faction, p.formation, p.leader, p.members);
			st.postDay.put(p.id, day);
			if (!sq.isEmpty() && sq.get(0).squad() != null) {
				sq.forEach(s -> s.addTag(GARRISON_TAG));
				st.postSquads.put(p.id, sq.get(0).squad().id());
				spawned += sq.size();
			}
			st.touch();
		}
		for (Ruler r : st.slots()) {
			if (!st.alive(r) || st.roaming == r) continue;
			UUID id = st.rulerIds.get(r);
			String mk = st.faction + "|" + r.key;
			if (id != null && level.getEntity(id) != null) {
				MISSING.remove(mk);
				continue;
			}
			BlockPos home = rulerPost(o, st.crowned == r && r.isLord() && !st.oligarchy() ? Ruler.KING : r, false);
			if (!level.isPositionEntityTicking(home)) continue;
			if (id != null && MISSING.merge(mk, 1, Integer::sum) < 3) continue;
			MISSING.remove(mk);
			if (spawnLeader(level, st, r, Vec3.atBottomCenterOf(home), home) != null) spawned++;
		}
		spawned += diplomat(level, st);
		if (!st.civiliansSpawned && level.isPositionEntityTicking(CastleLayout.town(o, true)) && level.isPositionEntityTicking(CastleLayout.town(o, false))) {
			st.civiliansSpawned = true;
			st.touch();
			for (int i = 0; i < 2; i++) civilian(level, CastleLayout.at(o, i * 6 - 3, 0, -18), "entity.elsewhere.court_courtier");
			for (boolean west : new boolean[] {true, false}) {
				BlockPos c = CastleLayout.town(o, west);
				for (int i = 0; i < 3; i++) civilian(level, c.offset(i * 3 - 3, 0, 3), null);
				spawnReeve(level, c.offset(2, 0, -2));
			}
		}
		return spawned;
	}

	public static @Nullable CourtRuler spawnRuler(ServerLevel level, Ruler r, Vec3 at, @Nullable BlockPos home) {
		return spawnRuler(level, CourtState.get(level), r, at, home);
	}

	/** Spawns the faction's leader for {@code r}: its registered custom boss ({@link dev.elsewhere.kingdom_court.api.Leaders}) or the built-in {@link CourtRuler}. */
	/** The faction's diplomacy NPC (Lawspeaker, Envoy or Clerk) stands inside the gate; one per realm, found again by its saved id. Returns 1 when spawned. */
	public static int diplomat(ServerLevel level, CourtState st) {
		int spawned = 0, i = 0;
		if (st.fallen || st.site == null) return 0;
		for (var role : dev.elsewhere.kingdom_court.api.Diplomacy.rolesOf(st.faction)) {
			int slot = i++;
			if (role.type == null) continue;
			String key = slot == 0 ? "npc_diplomat" : "npc_" + role.path;
			BlockPos at = CastleLayout.gateInside(st.site).offset(4 + slot * 3, 0, -3);
			if (!level.isPositionEntityTicking(at)) continue;
			UUID id = st.postSquads.get(key);
			if (id != null && level.getEntity(id) != null) continue;
			if (id != null && MISSING.merge(st.faction + "|" + key, 1, Integer::sum) < 3) continue;
			MISSING.remove(st.faction + "|" + key);
			var n = role.type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (n == null) continue;
			n.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180F, 0F);
			n.setFaction(st.faction);
			n.setUnit(role.unitId());
			n.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
			n.setCustomName(Component.translatable("entity.elsewhere." + role.path));
			n.setPatron(dev.elsewhere.kingdom_court.api.Diplomacy.patronOf(st.faction, role));
			if (!level.addFreshEntity(n)) continue;
			st.postSquads.put(key, n.getUUID());
			st.touch();
			spawned++;
		}
		return spawned;
	}

	public static @Nullable Mob spawnLeader(ServerLevel level, CourtState st, Ruler r, Vec3 at, @Nullable BlockPos home) {
		var type = dev.elsewhere.kingdom_court.api.Leaders.type(st.faction, r.key);
		if (type == null) return spawnRuler(level, st, r, at, home);
		Mob e = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (e == null) return null;
		e.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360F, 0F);
		if (e instanceof KingdomSoldier ks) ks.setFaction(st.faction);
		dev.elsewhere.kingdom_court.api.Leaders.tag(e, st.faction, r.key);
		e.addTag(GARRISON_TAG);
		e.setPersistenceRequired();
		e.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
		String holder = st.holderName.get(r);
		if (holder != null) e.setCustomName(Component.literal(holder));
		if (e instanceof dev.elsewhere.kingdom_court.api.Leaders.LeaderBoss lb) lb.onCourtSpawn(st.faction, r.key, home);
		dev.elsewhere.kingdom_court.api.Leaders.applyGating(e, st, r);
		e.setHealth(e.getMaxHealth());
		if (!level.addFreshEntity(e)) return null;
		st.rulerIds.put(r, e.getUUID());
		st.touch();
		return e;
	}

	/** Re-applies the ruler bonus to every living leader (built-in and custom). */
	public static void refreshAll(ServerLevel level, CourtState st) {
		for (Ruler o : st.slots()) {
			UUID id = st.rulerIds.get(o);
			Entity en = id == null ? null : level.getEntity(id);
			if (en instanceof CourtRuler cr) cr.refreshGating(st);
			else if (en instanceof Mob m && dev.elsewhere.kingdom_court.api.Leaders.of(m) != null) dev.elsewhere.kingdom_court.api.Leaders.applyGating(m, st, o);
		}
	}

	public static @Nullable CourtRuler spawnRuler(ServerLevel level, CourtState st, Ruler r, Vec3 at, @Nullable BlockPos home) {
		CourtRuler e = (CourtRuler) r.type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (e == null) return null;
		e.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360F, 0F);
		e.setFaction(st.faction);
		e.setUnit(r.unitId());
		e.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
		e.applyBaseHealth();
		String holder = st.holderName.get(r);
		if (holder != null) e.setCustomName(Component.literal(holder));
		e.home = home;
		if (r.isLord() && st.crowned == r && !st.oligarchy()) e.setCrowned(true);
		e.refreshGating(st);
		if (!level.addFreshEntity(e)) return null;
		st.rulerIds.put(r, e.getUUID());
		st.touch();
		return e;
	}

	static void civilian(ServerLevel level, BlockPos p, @Nullable String name) {
		Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
		if (v == null) return;
		v.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0F, 0F);
		v.setPersistenceRequired();
		if (name != null) v.setCustomName(Component.translatable(name));
		level.addFreshEntity(v);
	}

	public static int reevesSpawned;

	/** K-D's Reeve (fixed-trade villager) through {@code dev.elsewhere.lore.LoreApi.spawnQuartermaster}, no-op without kingdom_lore. */
	static void spawnReeve(ServerLevel level, BlockPos p) {
		try {
			Method m = Class.forName("dev.elsewhere.lore.LoreApi").getMethod("spawnQuartermaster", ServerLevel.class, BlockPos.class, boolean.class);
			if (m.invoke(null, level, p, false) != null) reevesSpawned++;
		} catch (ReflectiveOperationException | LinkageError e) {
			// kingdom_lore absent: towns keep their own market villagers
		}
		CourtEvents.TOWN_POPULATED.invoker().onTown(level, p);
	}

	/** K-D's Survey Map finds the realm through {@code LoreApi.addCastleLocator} (custom placement is not locatable). */
	static void registerCastleLocator() {
		try {
			Method m = Class.forName("dev.elsewhere.lore.LoreApi").getMethod("addCastleLocator", BiFunction.class);
			BiFunction<ServerLevel, BlockPos, BlockPos> f = (level, from) -> level == level.getServer().overworld() ? CourtApi.realmCenter(level.getServer()) : null;
			m.invoke(null, f);
			CourtContent.LOG.info("Registered the Aldermark castle locator with kingdom_lore");
		} catch (ReflectiveOperationException | LinkageError e) {
			// kingdom_lore absent
		}
	}

	// ---- daily routine -----------------------------------------------------------------------------------------------

	public static int phaseOf(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		if (t < 1000) return 0;   // dawn
		if (t < 5000) return 1;   // morning
		if (t < 7000) return 2;   // noon court
		if (t < 12000) return 3;  // afternoon
		if (t < 13000) return 4;  // dusk
		return 5;                 // night
	}

	public static int bells;

	public static void schedule(ServerLevel level, CourtState st) {
		BlockPos o = st.site;
		int ph = phaseOf(level.getDefaultClockTime());
		if (ph != st.lastPhase) {
			boolean night = ph == 4 || ph == 5;
			for (BlockPos g : CastleLayout.gates(o)) {
				BlockState s = level.getBlockState(g);
				if (s.hasProperty(FenceGateBlock.OPEN) && s.getValue(FenceGateBlock.OPEN) == night) level.setBlock(g, s.setValue(FenceGateBlock.OPEN, !night), 3);
			}
			for (BlockPos b : CastleLayout.braziers(o)) {
				BlockState s = level.getBlockState(b);
				if (s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT) != night) level.setBlock(b, s.setValue(CampfireBlock.LIT, night), 3);
			}
			if (ph == 0) {
				for (int i = 0; i < 3; i++) level.playSound(null, CastleLayout.bell(o), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
				bells++;
				if (st.sleepers > 0) {
					List<net.minecraft.resources.Identifier> m = new ArrayList<>();
					for (int i = 0; i < st.sleepers; i++) m.add(UnitTypes.MAN_AT_ARMS);
					List<KingdomSoldier> sq = KingdomApi.spawnSquad(level, CastleLayout.barracks(o), st.faction, Squad.Formation.LINE, UnitTypes.SERGEANT, m);
					if (!sq.isEmpty() && sq.get(0).squad() != null) {
						st.postSquads.put("drill", sq.get(0).squad().id());
						sq.get(0).squad().setObjective(CastleLayout.yard(o));
					}
					st.sleepers = 0;
				}
			}
			if (ph == 5) {
				UUID q = st.postSquads.get("drill");
				Squad s = q == null ? null : KingdomState.get(level).squad(q);
				if (s != null) {
					int n = 0;
					for (KingdomSoldier k : s.memberEntities(level)) {
						k.discard();
						n++;
					}
					Entity lead = s.leaderEntity(level);
					if (lead != null) lead.discard();
					st.sleepers = Math.max(1, n);
					KingdomState.get(level).disband(s, level);
				}
			}
			st.lastPhase = ph;
			st.touch();
		}
		UUID q = st.postSquads.get("drill");
		Squad drill = q == null ? null : KingdomState.get(level).squad(q);
		if (drill != null && (ph == 1 || ph == 3)) {
			List<BlockPos> loop = CastleLayout.yardLoop(o);
			drill.setObjective(loop.get((int) ((level.getGameTime() / 600) % loop.size())));
		}
		UUID kid = st.rulerIds.get(st.crowned);
		if (kid != null && level.getEntity(kid) instanceof CourtRuler k) k.home = ph == 2 ? CastleLayout.throneFront(o) : CastleLayout.hall(o);
	}

	// ---- realm music -------------------------------------------------------------------------------------------------

	public static void realmMusic(ServerLevel ow, CourtState st) {
		if (st.fallen || !st.built) return;
		long now = ow.getGameTime();
		var realm = CourtMusic.realm(st.faction);
		for (ServerPlayer p : ow.players()) realmMusicFor(ow, st, p);
	}

	/** One player's realm music (enter, 4-minute loop, leave). */
	public static void realmMusicFor(ServerLevel ow, CourtState st, ServerPlayer p) {
		if (st.fallen || !st.built || st.site == null) return;
		long now = ow.getGameTime();
		var realm = CourtMusic.realm(st.faction);
		{
			double d = Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(st.site)));
			String key = st.faction + "|" + p.getUUID();
			Long since = IN_REALM.get(key);
			if (d < REALM_RADIUS) {
				if (since == null || now - since > 20 * 240) {
					if (!dev.elsewhere.kingdom_court.api.RealmMusic.ENTER.invoker().onEnter(p, st.faction)) {
						p.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
						p.connection.send(new ClientboundSoundEntityPacket(Holder.direct(realm), SoundSource.MUSIC, p, 0.7F, 1.0F, ow.getRandom().nextLong()));
					}
					IN_REALM.put(key, now);
				}
			} else if (since != null && d > REALM_RADIUS + 32) {
				p.connection.send(new ClientboundStopSoundPacket(realm.location(), SoundSource.MUSIC));
				dev.elsewhere.kingdom_court.api.RealmMusic.LEAVE.invoker().onLeave(p, st.faction);
				IN_REALM.remove(key);
			}
		}
	}

	// ---- roaming Lords -----------------------------------------------------------------------------------------------

	public static @Nullable CourtRuler roamNow(ServerLevel level, ServerPlayer p, Ruler r) {
		return roamNow(level, CourtState.get(level), p, r);
	}

	public static @Nullable CourtRuler roamNow(ServerLevel level, CourtState st, ServerPlayer p, Ruler r) {
		return roamLeader(level, st, p, r) instanceof CourtRuler cr ? cr : null;
	}

	/** As {@link #roamNow} for any leader (custom bosses included). */
	public static @Nullable Mob roamLeader(ServerLevel level, CourtState st, ServerPlayer p, Ruler r) {
		RandomSource rnd = level.getRandom();
		double a = rnd.nextDouble() * Math.PI * 2;
		int x = (int) (p.getX() + Math.cos(a) * 48), z = (int) (p.getZ() + Math.sin(a) * 48);
		if (!level.isPositionEntityTicking(new BlockPos(x, (int) p.getY(), z))) return null;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		UUID old = st.rulerIds.get(r);
		if (old != null && level.getEntity(old) != null) return null;
		Mob lord = spawnLeader(level, st, r, new Vec3(x + 0.5, y, z + 0.5), null);
		if (lord == null) return null;
		Squad q = KingdomApi.createSquad(level, st.faction, Squad.Formation.COLUMN);
		KingdomApi.setLeader(level, q, lord);
		for (var u : List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER))
			KingdomApi.spawnSoldier(level, u, st.faction, lord.position().add(rnd.nextGaussian() * 2, 0, rnd.nextGaussian() * 2), q);
		q.setObjective(BlockPos.containing(p.getX() - Math.cos(a) * 96, y, p.getZ() - Math.sin(a) * 96));
		st.roaming = r;
		st.roamingSince = level.getGameTime();
		st.touch();
		p.sendOverlayMessage(Component.translatable("message.elsewhere.court_horns"));
		return lord;
	}

	static void roam(ServerLevel level, CourtState st) {
		if (st.roaming != null || st.site == null) return;
		List<Ruler> home = new ArrayList<>();
		for (Ruler r : st.lords()) if (st.alive(r) && st.crowned != r) {
			UUID id = st.rulerIds.get(r);
			if (id == null || level.getEntity(id) == null) home.add(r);
		}
		if (home.isEmpty()) return;
		BlockPos spawn = level.getRespawnData().pos();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative()) continue;
			double fromSpawn = Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(spawn)));
			double fromCastle = Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(st.site)));
			if (fromSpawn < 500 || fromCastle < 300) continue;
			if (level.getRandom().nextDouble() < roamChancePerMinute) {
				roamLeader(level, st, p, home.get(level.getRandom().nextInt(home.size())));
				return;
			}
		}
	}

	static void tickRoaming(ServerLevel level, CourtState st) {
		Ruler r = st.roaming;
		UUID id = st.rulerIds.get(r);
		Entity e = id == null ? null : level.getEntity(id);
		if (!st.alive(r)) {
			st.roaming = null;
			st.touch();
			return;
		}
		boolean gone = e == null || nearestPlayerDist(level, e.blockPosition()) > 160 || level.getGameTime() - st.roamingSince > 20 * 60 * 20;
		if (!gone) return;
		if (e instanceof Mob lord && (e instanceof CourtRuler || dev.elsewhere.kingdom_court.api.Leaders.of(e) != null)) {
			Squad q = null;
			for (Squad s : KingdomState.get(level).squads()) if (lord.getUUID().equals(s.leader())) q = s;
			if (q != null) {
				for (KingdomSoldier k : q.memberEntities(level)) k.discard();
				KingdomState.get(level).disband(q, level);
			}
			lord.discard();
		}
		st.rulerIds.remove(r);
		st.roaming = null;
		st.touch();
	}

	// ---- deaths, drops, succession, fall ------------------------------------------------------------------------------

	/** Aldermark's last drops per slot (2.0 tests read this) and every faction's, keyed {@code "<faction>|<slot>"}. */
	public static final Map<Ruler, List<ItemStack>> LAST_DROPS = new HashMap<>();
	public static final Map<String, List<ItemStack>> LAST_DROPS_BY = new HashMap<>();

	static void afterDeath(LivingEntity dead, DamageSource src) {
		if (!(dead.level() instanceof ServerLevel level)) return;
		ServerPlayer killer = src.getEntity() instanceof ServerPlayer sp ? sp : null;
		if (dead instanceof CourtRuler r) {
			CourtState st = CourtState.get(level, r.factionId());
			UUID want = st.rulerIds.get(r.ruler);
			if (!st.alive(r.ruler) || (want != null && !want.equals(r.getUUID()))) return;
			onRulerDeath(level, r, killer);
			return;
		}
		var ref = dev.elsewhere.kingdom_court.api.Leaders.of(dead);
		if (ref == null) return;
		Ruler who = Ruler.byKey(ref.slot());
		if (who == null) return;
		CourtState st = CourtState.get(level, ref.faction());
		UUID want = st.rulerIds.get(who);
		if (!st.alive(who) || (want != null && !want.equals(dead.getUUID()))) return;
		onLeaderDeath(level, st, who, dead, killer, 0.0F);
	}

	public static void onRulerDeath(ServerLevel level, CourtRuler r, @Nullable ServerPlayer killer) {
		onLeaderDeath(level, CourtState.get(level, r.factionId()), r.ruler, r, killer, r.unit().aggressionValue());
	}

	/** Any leader's death (built-in ruler or a registered custom boss): drops, aggression, succession or the Chair, brain-boss event, the fall. */
	public static void onLeaderDeath(ServerLevel level, CourtState st, Ruler who, LivingEntity r, @Nullable ServerPlayer killer, float aggressionOffset) {
		boolean wasCrowned = st.oligarchy() ? st.isHolder(who) : st.crowned == who;
		st.dead.add(who);
		st.deadSince.put(who, level.getDefaultClockTime() / 24000L);
		st.rulerIds.remove(who);
		if (st.roaming == who) st.roaming = null;
		st.touch();
		List<ItemStack> drops = Factions.ALDERMARK.equals(st.faction) ? CourtDrops.roll(st.faction, who, level.getRandom()) : CourtDrops.rollRival(level, st, who, r.position(), killer);
		LAST_DROPS_BY.put(st.faction + "|" + who.key, drops);
		if (Factions.ALDERMARK.equals(st.faction)) LAST_DROPS.put(who, drops);
		for (ItemStack s : drops) {
			ItemEntity ie = new ItemEntity(level, r.getX(), r.getY() + 0.5, r.getZ(), s);
			ie.setDefaultPickUpDelay();
			level.addFreshEntity(ie);
		}
		for (Wolf w : level.getEntitiesOfClass(Wolf.class, r.getBoundingBox().inflate(48), w -> w.entityTags().contains(HOUND_TAG))) w.discard();
		if (killer != null) {
			KingdomApi.addAggression(killer, st.faction, Math.max(0.0F, (who == Ruler.KING ? 10.0F : 6.0F) - aggressionOffset));
			Standing.onKilled(killer, st.faction, wasCrowned || who == Ruler.KING ? Standing.Victim.RULER : Standing.Victim.LORD);
		}
		if (wasCrowned || who == Ruler.KING) KingdomApi.markRulerKilled(level.getServer(), st.faction);
		Component name = CourtRulers.displayName(st.faction, who);
		broadcast(level.getServer(), Component.translatable("message.elsewhere.court_ruler_fell", name));
		CourtEvents.RULER_DIED.invoker().onRulerDied(level, who, killer);
		brainRole(level.getServer(), st, who, false);
		if (wasCrowned) succeed(level, st, who);
		refreshAll(level, st);
		if (st.dead.size() >= st.slots().size()) fall(level.getServer(), st.faction);
	}

	/**
	 * A leader died ({@code alive=false}) or its seat came back ({@code alive=true}): fires {@link CourtEvents#BRAIN_ROLE} and, on death, calls the war layer's
	 * {@code KingdomApi.brainBossKilled(server, faction, role)} (role = slot key plus the leader's title and name, which the war lane matches by keyword:
	 * sultana, wazir, falconer, jarl, hersir, king ...; roles that are not brain roles are ignored there).
	 */
	public static void brainRole(MinecraftServer server, CourtState st, Ruler r, boolean alive) {
		CourtEvents.BRAIN_ROLE.invoker().onBrainRole(server, st.faction, r.key, alive);
		BRAIN_EVENTS.add(st.faction + "|" + r.key + "|" + alive);
		if (alive) return;
		var d = CourtRulers.def(st.faction, r);
		String role = r.key + (d == null ? "" : " " + d.title() + " " + d.name());
		try {
			KingdomApi.brainBossKilled(server, st.faction, role);
		} catch (RuntimeException e) {
			CourtContent.LOG.warn("brainBossKilled failed for {} {}: {}", st.faction, r.key, e.toString());
		}
	}

	/** Test log of fired brain events ({@code faction|slot|alive}). */
	public static final List<String> BRAIN_EVENTS = new ArrayList<>();

	static void succeed(ServerLevel level, CourtState st, Ruler died) {
		if (st.oligarchy()) {
			dev.elsewhere.kingdom_court.api.Oligarchy.chairDied(level, st, died);
			return;
		}
		for (Ruler r : st.lords()) {
			if (!st.alive(r)) continue;
			st.crowned = r;
			st.touch();
			UUID id = st.rulerIds.get(r);
			if (id != null && level.getEntity(id) instanceof CourtRuler cr) cr.setCrowned(true);
			broadcast(level.getServer(), Component.translatable("message.elsewhere.court_succession", CourtRulers.displayName(st.faction, r)));
			dev.elsewhere.kingdom_court.api.Succession.rulerSucceeded(level, st, r);
			return;
		}
	}

	/** Once a minute for an oligarchy: income, seat refills, the vacancy and the weekly Chair (see {@link dev.elsewhere.kingdom_court.api.Oligarchy#tick}). */
	public static void council(ServerLevel level, CourtState st) {
		dev.elsewhere.kingdom_court.api.Oligarchy.tick(level, st);
	}

	public static void broadcast(MinecraftServer server, Component c) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(c);
	}

	public static int remnantsKept, despawned;

	/** All of Aldermark's rulers are dead: most underlings despawn, conquest stops, a few neutral remnants roam. */
	public static void fall(MinecraftServer server) {
		fall(server, Factions.ALDERMARK);
	}

	/** All of {@code faction}'s leaders are dead (an oligarchy: all dead with no seat refilled in time): the end state, for that faction only. */
	public static void fall(MinecraftServer server, Identifier faction) {
		CourtState st = CourtState.get(server, faction);
		if (st.fallen) return;
		st.fallen = true;
		st.roaming = null;
		st.touch();
		if (!Factions.ALDERMARK.equals(faction)) Factions.registerRemnant(CourtContent.remnantOf(faction), faction, 0x77706A);
		if (st.oligarchy()) {
			// Dark Lanterns: the Republic ends for good. Contracts end, the Counting-House vault opens (see openVault), villages go contested (their lane listens).
			st.darkLanterns = true;
			st.ledger.put("vault_open", 1L);
			for (String k : List.copyOf(st.ledger.keySet())) if (k.startsWith("contract|")) st.ledger.put(k, 0L);
			st.touch();
			try {
				Class.forName("dev.elsewhere.kingdom.KingdomApi").getMethod("endContracts", MinecraftServer.class, Identifier.class).invoke(null, server, faction);
			} catch (ReflectiveOperationException | LinkageError ignored) {
				// the war lane has not published the hook yet
			}
			CourtEvents.DARK_LANTERNS.invoker().onDarkLanterns(server, faction);
		}
		KingdomApi.setFactionHostileToPlayers(server, faction, false);
		KingdomApi.setFactionFallen(server, faction, true);
		for (KingdomSoldier s : List.copyOf(KingdomPerf.unmodifiableLoaded()))
			if (s.isAlive() && !(s instanceof CourtRuler) && faction.equals(s.factionId())) endState(s);
		for (ServerLevel level : server.getAllLevels())
			for (Squad q : List.copyOf(KingdomState.get(level).squads())) if (faction.equals(q.faction())) KingdomState.get(level).disband(q, level);
		if (Factions.ALDERMARK.equals(faction)) broadcast(server, Component.translatable("message.elsewhere.court_fallen"));
		else broadcast(server, Factions.get(faction).displayName().copy().append(Component.literal(" has fallen.")));
		if (Factions.ALDERMARK.equals(faction)) CourtEvents.KINGDOM_FALLEN.invoker().onFallen(server);
		CourtEvents.FACTION_FALLEN.invoker().onFallen(server, faction);
	}

	/** Where the Counting-House vault chest stands after Dark Lanterns. */
	public static BlockPos vaultPos(BlockPos site) {
		return CastleLayout.at(site, 0, 1, -22);
	}

	/**
	 * Dark Lanterns: the Counting-House vault opens as a ruler-tier treasury chest in the keep (64 + 16-32 of the currency, a tenth of the Magnates' unspent vaults up to 200, and
	 * 6-12 emeralds). Done once ({@code vault_open} 1 to 2). Returns the chest position.
	 */
	public static BlockPos openVault(ServerLevel level, CourtState st) {
		BlockPos pos = vaultPos(st.site);
		while (!level.getBlockState(pos).isAir() && pos.getY() < st.site.getY() + 20) pos = pos.above();
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		long unspent = 0;
		for (long v : st.vault.values()) unspent += v;
		int scrip = 64 + 16 + level.getRandom().nextInt(17) + (int) Math.min(200, unspent / 10);
		List<ItemStack> loot = new ArrayList<>();
		var cur = dev.elsewhere.kingdom_court.api.Shops.currency(st.faction);
		while (cur != null && scrip > 0) {
			int k = Math.min(scrip, cur.getDefaultMaxStackSize());
			loot.add(new ItemStack(cur, k));
			scrip -= k;
		}
		loot.add(new ItemStack(Items.EMERALD, 6 + level.getRandom().nextInt(7)));
		if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) for (int i = 0; i < loot.size() && i < chest.getContainerSize(); i++) chest.setItem(i, loot.get(i));
		st.ledger.put("vault_open", 2L);
		st.touch();
		return pos;
	}

	public static boolean isRemnant(Entity e) {
		return Math.floorMod(e.getUUID().getLeastSignificantBits(), 6L) == 0;
	}

	static void endState(KingdomSoldier s) {
		if (!(s.level() instanceof ServerLevel level)) return;
		if (isRemnant(s)) {
			KingdomState.get(level).leave(s);
			s.setFaction(CourtContent.remnantOf(s.factionId()));
			s.addTag(REMNANT_TAG);
			s.setTarget(null);
			remnantsKept++;
		} else {
			if (s.getVehicle() != null) s.getVehicle().discard();
			s.discard();
			despawned++;
		}
	}

	static void wanderRemnants(ServerLevel level) {
		for (KingdomSoldier s : KingdomPerf.loaded(level)) {
			if (!s.entityTags().contains(REMNANT_TAG) || s.getTarget() != null || !s.getNavigation().isDone()) continue;
			if (level.getRandom().nextInt(3) != 0 || !KingdomPerf.tryPath(level)) continue;
			s.getNavigation().moveTo(s.getX() + level.getRandom().nextInt(17) - 8, s.getY(), s.getZ() + level.getRandom().nextInt(17) - 8, 0.7);
		}
	}
}
