package dev.elsewhere.kingdom.war;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Hrafnholt's longship landing (RIVALS-DECISIONS 16a). Only when the faction's {@code army.landing} is {@code "longship"}: when the war layer hands an
 * attack to real units and the village is within 64 blocks of water, the attackers spawn at the nearest shore (one search at hand-off) and a beached
 * longship of temporary blocks is placed. Every block it changes is recorded (original state and placed state) in the saved war data and put back when the
 * raid ends or after 10 minutes. It only replaces air, water and replaceable plants (never solid blocks, so never a player's build), and on restore it
 * leaves any block a player changed since. No claims or protection areas exist in this mod, so there is nothing further to check. No per-tick cost: the
 * expiry check is a single list-empty test.
 * <p>
 * RIVALS-DECISIONS 18 (sailing): {@link #sail} starts a voyage at the hand-off. One budgeted water route ({@link WaterPath}, cached per realm and target) is
 * computed over the next few ticks; the {@link HrafnholtLongship} then appears where the route enters the loaded area with the raiders seated, sails at boat
 * speed and beaches; the raiders hop off one by one, and the beached prop above replaces the ship. No route: the caller's walking fallback runs. Per tick the
 * war hook only steps the route search (budgeted) while one is running; a sailing ship costs one route step in its own tick.
 */
public final class Longship {
	public static final int SEARCH = 64, LIFETIME = 12000;
	/** True while any ship is recorded (the only thing the per-tick hook looks at). */
	static boolean pending;

	private Longship() {}

	/** One recorded block of a prop. */
	public static final class BlockRec {
		public int x, y, z, orig, placed;
		public String origKey = "";
	}

	/** One placed prop. */
	public static final class ShipRec {
		public long until;
		public int battle;
		public List<BlockRec> blocks = new ArrayList<>();
	}

	/** Block choices of the ship and prop (spruce and dark oak, green and brown wool sail and shields; overridable by the castle file's {@code waterfront.longship}). */
	public record Style(Block hull, Block keel, Block rail, Block mast, Block sail, Block sailAlt, Block trim, Block stem, Block bench, Block shield, Block shieldAlt) {
		public static Style defaults() {
			return new Style(Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, wool("green_wool"), wool("brown_wool"), Blocks.DARK_OAK_SLAB,
				Blocks.DARK_OAK_LOG, Blocks.SPRUCE_SLAB, wool("green_wool"), wool("brown_wool"));
		}

		private static Block wool(String path) {
			return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path));
		}
	}

	/** Where to land: the first land block, and the unit direction (water to land) the ship points along. */
	public record Shore(BlockPos land, int dx, int dz, int waterY) {}

	public static boolean enabled(Identifier faction) {
		var d = dev.elsewhere.kingdom.Factions.def(faction);
		return d != null && "longship".equals(d.army().landing().orElse(""));
	}

	/**
	 * Reads the optional {@code waterfront} object of {@code data/<ns>/kingdom_castle/<path>.json}: its {@code longship} object (hull, keel, rail, trim, stem,
	 * mast, sail as one id or a [main, stripe] pair, shield and shield_alt; a banner id gives the wool of its colour), or the older flat keys. Any missing,
	 * unknown or valuable entry keeps the default.
	 */
	public static Style styleFor(ServerLevel lv, Identifier faction) {
		Style s = Style.defaults();
		try {
			var res = lv.getServer().getResourceManager().getResource(Identifier.fromNamespaceAndPath(faction.getNamespace(), "kingdom_castle/" + faction.getPath() + ".json"));
			if (res.isEmpty()) return s;
			try (var r = res.get().openAsReader()) {
				return parse(JsonParser.parseReader(r).getAsJsonObject());
			}
		} catch (Exception e) {
			return s;
		}
	}

	/** The style of a castle file's JSON (its {@code waterfront} object); defaults for anything missing. */
	public static Style parse(JsonObject o) {
		Style s = Style.defaults();
		if (!o.has("waterfront") || !o.get("waterfront").isJsonObject()) return s;
		JsonObject w = o.getAsJsonObject("waterfront");
		if (w.has("longship") && w.get("longship").isJsonObject()) w = w.getAsJsonObject("longship");
		Block sail = s.sail(), sailAlt = s.sailAlt();
		if (w.has("sail") && w.get("sail").isJsonArray() && !w.getAsJsonArray("sail").isEmpty()) {
			var a = w.getAsJsonArray("sail");
			sail = blk(a.get(0).getAsString(), s.sail());
			sailAlt = a.size() > 1 ? blk(a.get(1).getAsString(), s.sailAlt()) : sail;
		} else if (w.has("sail") && w.get("sail").isJsonPrimitive()) {
			sail = blk(w.get("sail").getAsString(), s.sail());
		}
		return new Style(blk(w, "hull", s.hull()), blk(w, "keel", s.keel()), blk(w, "rail", s.rail()), blk(w, "mast", s.mast()), sail, sailAlt, blk(w, "trim", s.trim()),
			blk(w, "stem", s.stem()), blk(w, "bench", s.bench()), blk(w, "shield", s.shield()), blk(w, "shield_alt", s.shieldAlt()));
	}

	private static final Set<String> VALUABLE = Set.of("gold_block", "diamond_block", "emerald_block", "iron_block", "netherite_block", "lapis_block", "gilded_blackstone",
		"raw_gold_block", "raw_iron_block", "raw_copper_block", "copper_block", "amethyst_block", "beacon", "ancient_debris");

	private static Block blk(JsonObject w, String k, Block def) {
		return w.has(k) && w.get(k).isJsonPrimitive() ? blk(w.get(k).getAsString(), def) : def;
	}

	private static Block blk(String raw, Block def) {
		Identifier id = Identifier.tryParse(raw);
		if (id == null) return def;
		if (id.getPath().endsWith("_banner")) id = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_wall_banner", "_wool").replace("_banner", "_wool"));
		if (!BuiltInRegistries.BLOCK.containsKey(id) || VALUABLE.contains(id.getPath())) return def;
		Block b = BuiltInRegistries.BLOCK.getValue(id);
		return b.defaultBlockState().isAir() ? def : b;
	}

	private static boolean water(ServerLevel lv, int x, int y, int z) {
		return lv.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
	}

	/** One-time search for the water nearest the target (rings of 16 points), then the first land block walking from that water towards the target. */
	public static @Nullable Shore findShore(ServerLevel lv, BlockPos target) {
		for (int r = 6; r <= SEARCH; r += 4) {
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				int x = target.getX() + (int) Math.round(Math.cos(a) * r), z = target.getZ() + (int) Math.round(Math.sin(a) * r);
				BlockPos col = new BlockPos(x, 64, z);
				if (!lv.isLoaded(col)) continue;
				int top = lv.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, col).getY() - 1;
				if (!water(lv, x, top, z) || !water(lv, x, top - 1, z)) continue;
				int ddx = target.getX() - x, ddz = target.getZ() - z;
				int dx = Math.abs(ddx) >= Math.abs(ddz) ? Integer.signum(ddx) : 0, dz = dx == 0 ? Integer.signum(ddz) : 0;
				if (dx == 0 && dz == 0) continue;
				for (int s = 1; s <= SEARCH + 8; s++) {
					int cx = x + dx * s, cz = z + dz * s;
					BlockPos c = new BlockPos(cx, 64, cz);
					if (!lv.isLoaded(c)) break;
					int t = lv.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, c).getY() - 1;
					if (!water(lv, cx, t, cz)) {
						if (s < 2) break;
						return new Shore(new BlockPos(cx, t + 1, cz), dx, dz, top);
					}
				}
			}
		}
		return null;
	}

	private static boolean placeable(BlockState s) {
		return s.isAir() || s.canBeReplaced();
	}

	private static void put(ServerLevel lv, ShipRec rec, BlockPos p, BlockState neu) {
		if (!lv.isLoaded(p)) return;
		BlockState cur = lv.getBlockState(p);
		if (!placeable(cur)) return;
		BlockRec r = new BlockRec();
		r.x = p.getX();
		r.y = p.getY();
		r.z = p.getZ();
		r.orig = Block.BLOCK_STATE_REGISTRY.getId(cur);
		r.origKey = BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString();
		r.placed = Block.BLOCK_STATE_REGISTRY.getId(neu);
		rec.blocks.add(r);
		lv.setBlock(p, neu, 18);
	}

	/** The beached centre of a ship landing at {@code sh} (its bow stem on the last water column, so the keel block under the mast is 7 blocks out). */
	public static BlockPos beachCentre(Shore sh) {
		return new BlockPos(sh.land().getX() - sh.dx() * 7, sh.waterY(), sh.land().getZ() - sh.dz() * 7);
	}

	/**
	 * Places the beached ship at {@code shore} (bow on the beach, stern out on the water), block for block the {@link ShipShape} the sailing ship is drawn with
	 * (shields are only drawn on the sailing ship), and records it; returns the record, or null when it could not fit at all.
	 */
	public static @Nullable ShipRec place(ServerLevel lv, Shore sh, Style st, int battle) {
		ShipRec rec = new ShipRec();
		rec.until = lv.getGameTime() + LIFETIME;
		rec.battle = battle;
		BlockPos c = beachCentre(sh);
		int dx = sh.dx(), dz = sh.dz(), lx = dz, lz = -dx;
		Rotation rot = ShipShape.rotation(dx, dz);
		for (ShipShape.Part p : ShipShape.PARTS) {
			if (p.shield()) continue;
			put(lv, rec, c.offset(p.x() * lx + p.z() * dx, p.y(), p.x() * lz + p.z() * dz), ShipShape.state(p, st).rotate(rot));
		}
		if (rec.blocks.isEmpty()) return null;
		WarState.get(lv).data.ships.add(rec);
		WarState.get(lv).setDirty();
		pending = true;
		return rec;
	}

	/** Searches the shore near {@code target} and places the ship; null (nothing changed) when the faction has no landing flag or no water is near. */
	public static @Nullable Shore land(ServerLevel lv, Identifier faction, BlockPos target, int battle) {
		if (!enabled(faction)) return null;
		Shore sh = findShore(lv, target);
		if (sh == null) return null;
		place(lv, sh, styleFor(lv, faction), battle);
		return sh;
	}

	/** Puts back every block of this battle's ships (blocks a player changed since are left alone). */
	public static void restoreBattle(ServerLevel lv, int battle) {
		if (!pending) return;
		restore(lv, r -> r.battle == battle);
	}

	/** Restores ships whose 10 minutes are over (called from the war layer every 100 ticks only while a ship exists). */
	public static void expire(ServerLevel lv) {
		if (!pending) return;
		long now = lv.getGameTime();
		restore(lv, r -> r.until <= now);
	}

	/** Restores every ship now. */
	public static void restoreAll(ServerLevel lv) {
		restore(lv, r -> true);
	}

	private static void restore(ServerLevel lv, java.util.function.Predicate<ShipRec> which) {
		WarState ws = WarState.get(lv);
		for (Iterator<ShipRec> it = ws.data.ships.iterator(); it.hasNext();) {
			ShipRec s = it.next();
			if (!which.test(s)) continue;
			boolean done = true;
			for (Iterator<BlockRec> bi = s.blocks.iterator(); bi.hasNext();) {
				BlockRec r = bi.next();
				BlockPos p = new BlockPos(r.x, r.y, r.z);
				if (!lv.isLoaded(p)) {
					done = false; // retried later; never force-load chunks for a prop
					continue;
				}
				BlockState placed = Block.BLOCK_STATE_REGISTRY.byId(r.placed);
				if (placed != null && lv.getBlockState(p) == placed) {
					BlockState orig = Block.BLOCK_STATE_REGISTRY.byId(r.orig);
					if (orig == null || !BuiltInRegistries.BLOCK.getKey(orig.getBlock()).toString().equals(r.origKey)) {
						Identifier id = Identifier.tryParse(r.origKey);
						orig = id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id).defaultBlockState() : Blocks.AIR.defaultBlockState();
					}
					lv.setBlock(p, orig, 18);
				}
				bi.remove();
			}
			if (done) it.remove();
		}
		pending = !ws.data.ships.isEmpty();
		ws.setDirty();
	}

	/** On world start: any ship left from a previous run is restored as soon as its chunks load (the hook retries every 100 ticks). */
	public static void onStart(ServerLevel lv) {
		pending = !WarState.get(lv).data.ships.isEmpty();
		WaterPath.warm();
	}

	// ---- sailing longships (RIVALS-DECISIONS 18) ------------------------------------------------------------------------------------------

	/** What the raid does at each point of a voyage (the war layer's battle, or a test). */
	public interface Crew {
		/** Spawns the raiders at {@code at} (on the water at the route start); empty when the caps leave no room. */
		List<KingdomSoldier> spawn(BlockPos at);

		/** No water route: the raiders walk (fallback). */
		default void noRoute() {}

		/** The caps left no room for a crew. */
		default void noCrew() {}

		/** Every raider is ashore and the beached prop stands. */
		default void landed() {}

		/** The ship was removed before landing (chunk unload, server stop, 10-minute expiry). */
		default void lost() {}
	}

	public enum Phase { ROUTING, SAILING, LANDED, FAILED }

	public static final class Voyage {
		public final int battle;
		public final Identifier faction;
		public final Shore shore;
		public final Style style;
		final String key;
		final Crew crew;
		public WaterPath.@Nullable Job job;
		public @Nullable List<Vec3> path;
		public @Nullable UUID ship;
		public final List<UUID> raiders = new ArrayList<>();
		final Map<UUID, Boolean> virtualizable = new HashMap<>();
		public Phase phase = Phase.ROUTING;
		public long launched, landedAt;
		int hopped;

		Voyage(int battle, Identifier faction, Shore shore, Style style, String key, Crew crew) {
			this.battle = battle;
			this.faction = faction;
			this.shore = shore;
			this.style = style;
			this.key = key;
			this.crew = crew;
		}

		public boolean ashore() {
			return phase == Phase.LANDED;
		}
	}

	record Cached(@Nullable List<Vec3> path, long time) {}

	static final List<Voyage> VOYAGES = new ArrayList<>();
	static final Map<String, Cached> ROUTES = new HashMap<>();
	public static final int ROUTE_TTL = 24000;
	/** Tests and the lag report: routes computed and reused, the last finished search, war-hook nanos spent on voyages. */
	public static int routesComputed, routeCacheHits;
	public static WaterPath.@Nullable Job lastJob;
	public static long voyageNanos, voyageTicks;

	/** Entity tag of a raider aboard a ship. A chunk unload may save a crew member on its own; on load it is removed (crews are never saved). */
	public static final String CREW_TAG = "elsewhere_longship_crew";
	static final List<Entity> STRAYS = new ArrayList<>();
	private static boolean hooked;

	/** Registers the load hook that removes a saved crew member (once, from the war layer's init). */
	public static void init() {
		if (hooked) return;
		hooked = true;
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, lv) -> {
			if (e instanceof KingdomSoldier && !e.isPassenger() && e.entityTags().contains(CREW_TAG)) STRAYS.add(e);
		});
		// a ship whose chunk stops being tracked (the chunk is going away) is removed with its crew on the next tick, so a chunk that is kept
		// in memory and loaded again never brings a frozen ship back
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((e, lv) -> {
			if (e instanceof HrafnholtLongship s && !s.isRemoved()) STRAYS.add(s);
		});
	}

	public static boolean sailing() {
		return !VOYAGES.isEmpty() || !STRAYS.isEmpty();
	}

	public static List<Voyage> voyages() {
		return List.copyOf(VOYAGES);
	}

	/**
	 * Starts a voyage to {@code shore} for a raid on {@code target} (flag {@code army.landing = "longship"} only). {@code fromX/fromZ} is the side the raid
	 * comes from (towards its castle), or zero. Returns null when the flag is off or a cached or immediate check shows there is no water route: the caller then
	 * lets the raiders walk. Otherwise the route is found over the next ticks and the ship launched; {@code crew} hears how it went.
	 */
	public static @Nullable Voyage sail(ServerLevel lv, Identifier faction, BlockPos target, Shore shore, double fromX, double fromZ, int battle, Crew crew) {
		if (!enabled(faction)) return null;
		BlockPos fin = beachCentre(shore);
		String key = faction + "|" + target.asLong() + "|" + fin.asLong();
		Voyage v = new Voyage(battle, faction, shore, styleFor(lv, faction), key, crew);
		Cached c = ROUTES.get(key);
		long now = lv.getGameTime();
		if (c != null && now - c.time() < ROUTE_TTL && (c.path() == null || stillWater(lv, c.path()))) {
			routeCacheHits++;
			if (c.path() == null) return null;
			v.path = c.path();
		} else {
			v.job = WaterPath.start(lv, fin, shore.dx(), shore.dz(), shore.waterY(), fromX, fromZ);
			if (v.job == null) {
				ROUTES.put(key, new Cached(null, now));
				return null;
			}
		}
		VOYAGES.add(v);
		return v;
	}

	private static boolean stillWater(ServerLevel lv, List<Vec3> p) {
		for (Vec3 q : List.of(p.get(0), p.get(p.size() - 1))) {
			BlockPos b = BlockPos.containing(q);
			if (!lv.isLoaded(b) || !lv.getFluidState(b).is(FluidTags.WATER)) return false;
		}
		return true;
	}

	/** War hook, every tick while a voyage exists: steps a running route search (budgeted) and launches ships; sailing ships move themselves. */
	public static void tickVoyages(ServerLevel lv) {
		long t0 = System.nanoTime();
		if (!STRAYS.isEmpty()) {
			List<Entity> strays = List.copyOf(STRAYS);
			STRAYS.clear();
			for (Entity e : strays) {
				if (e.isRemoved()) continue;
				if (e instanceof HrafnholtLongship s) lost(lv, s);
				else if (!e.isPassenger()) e.discard();
			}
		}
		for (Voyage v : List.copyOf(VOYAGES)) {
			if (v.phase != Phase.ROUTING) continue;
			if (v.path == null) {
				if (v.job == null || !v.job.step()) continue;
				lastJob = v.job;
				routesComputed++;
				v.path = v.job.path;
				ROUTES.put(v.key, new Cached(v.path, lv.getGameTime()));
				if (v.path == null || v.path.size() < 2) {
					v.phase = Phase.FAILED;
					VOYAGES.remove(v);
					v.crew.noRoute();
					continue;
				}
			}
			launch(lv, v);
		}
		voyageNanos += System.nanoTime() - t0;
		voyageTicks++;
	}

	private static void launch(ServerLevel lv, Voyage v) {
		List<Vec3> path = v.path;
		HrafnholtLongship ship = KingdomEntities.LONGSHIP.create(lv, EntitySpawnReason.EVENT);
		if (ship == null) {
			fail(v);
			v.crew.noCrew();
			return;
		}
		ship.setStyle(v.style);
		ship.sail(path, ShipShape.yaw(v.shore.dx(), v.shore.dz()));
		if (!lv.addFreshEntity(ship)) {
			fail(v);
			v.crew.noCrew();
			return;
		}
		v.ship = ship.getUUID();
		Vec3 s = path.get(0);
		List<KingdomSoldier> crew = v.crew.spawn(BlockPos.containing(s.x, s.y + 1, s.z));
		for (KingdomSoldier k : crew) {
			Entity mount = k.getVehicle();
			if (mount != null) {
				k.stopRiding();
				if (mount instanceof AbstractHorse h && !h.isVehicle()) h.discard();
			}
			if (board(ship, k)) {
				v.raiders.add(k.getUUID());
				if (k.squad() != null) {
					v.virtualizable.putIfAbsent(k.getUUID(), k.squad().isVirtualizable());
					k.squad().setVirtualizable(false); // stays real on the water, however far the player is
				}
			} else {
				k.discard(); // never left swimming at the route start
			}
		}
		if (v.raiders.isEmpty()) {
			fail(v);
			ship.discard();
			v.crew.noCrew();
			return;
		}
		v.phase = Phase.SAILING;
		v.launched = lv.getGameTime();
	}

	/** Seats a soldier on the ship (false when it is full); seated raiders carry {@link #CREW_TAG}. */
	public static boolean board(HrafnholtLongship ship, KingdomSoldier k) {
		if (ship.getPassengers().size() >= ShipShape.CAPACITY || !k.startRiding(ship, true, true)) return false;
		k.addTag(CREW_TAG);
		return true;
	}

	private static void fail(Voyage v) {
		v.phase = Phase.FAILED;
		VOYAGES.remove(v);
	}

	static @Nullable Voyage of(HrafnholtLongship ship) {
		for (Voyage v : VOYAGES) if (ship.getUUID().equals(v.ship)) return v;
		return null;
	}

	/** A beached ship lets its front raider hop onto the beach (the rest move up a bench); false when nobody is left aboard. */
	static boolean hopOff(ServerLevel lv, HrafnholtLongship ship) {
		List<Entity> aboard = ship.getPassengers();
		if (aboard.isEmpty()) return false;
		Entity p = aboard.get(0);
		Voyage v = of(ship);
		int i = v == null ? 0 : v.hopped++;
		p.stopRiding();
		p.removeTag(CREW_TAG);
		Vec3 fwd = ship.local(0, 1);
		Vec3 spot = null;
		if (v != null) {
			int[] lat = {0, -1, 1, -2, 2};
			int side = lat[i % lat.length], ahead = 1 + i / lat.length;
			BlockPos land = v.shore.land();
			int x = land.getX() + v.shore.dx() * ahead + v.shore.dz() * side, z = land.getZ() + v.shore.dz() * ahead - v.shore.dx() * side;
			BlockPos g = WarBattles.ground(lv, x + 0.5, z + 0.5);
			if (g == null) g = WarBattles.ground(lv, land.getX() + 0.5, land.getZ() + 0.5);
			if (g != null) spot = Vec3.atBottomCenterOf(g);
		}
		if (spot == null) spot = ship.position().add(fwd.scale(ShipShape.HALF_LENGTH + 1.5)).add(0, 1, 0);
		p.teleportTo(spot.x, spot.y, spot.z);
		p.setYRot(ship.getYRot());
		if (p instanceof LivingEntity le) {
			le.setYBodyRot(ship.getYRot());
			le.setYHeadRot(ship.getYRot());
		}
		p.setDeltaMovement(fwd.x * 0.12, 0.32, fwd.z * 0.12); // the hop
		p.needsSync = true;
		if (v != null && p instanceof KingdomSoldier k && k.squad() != null && v.virtualizable.containsKey(k.getUUID())) k.squad().setVirtualizable(v.virtualizable.get(k.getUUID()));
		return true;
	}

	/** Everyone is ashore: the beached prop replaces the ship (same blocks, same place) and the ship is gone. */
	static void landed(ServerLevel lv, HrafnholtLongship ship) {
		Voyage v = of(ship);
		if (v != null) {
			v.phase = Phase.LANDED;
			v.landedAt = lv.getGameTime();
			VOYAGES.remove(v);
			place(lv, v.shore, v.style, v.battle);
		}
		ship.discard();
		if (v != null) v.crew.landed();
	}

	/** 10 minutes without landing: the ship and its crew are removed. */
	static void lost(ServerLevel lv, HrafnholtLongship ship) {
		for (Entity p : List.copyOf(ship.getPassengers())) {
			p.stopRiding();
			if (p instanceof KingdomSoldier) p.discard();
		}
		ship.discard();
	}

	/** Called for every removal of a ship: a voyage still at sea ends (its crew went with it). */
	static void onShipRemoved(ServerLevel lv, HrafnholtLongship ship, Entity.RemovalReason reason) {
		Voyage v = of(ship);
		if (v == null || v.phase != Phase.SAILING) return;
		fail(v);
		v.crew.lost();
	}

	/** The raid ended (or was given up): its voyage stops and its ship, if any, is removed. */
	public static void cancel(ServerLevel lv, int battle) {
		for (Voyage v : List.copyOf(VOYAGES)) {
			if (v.battle != battle) continue;
			fail(v);
			if (v.ship != null && lv.getEntity(v.ship) instanceof HrafnholtLongship s) lost(lv, s);
		}
	}

	/** Server stop: no ship (and no crew aboard one) is ever left behind; routes are forgotten. */
	public static void onServerStopping(MinecraftServer srv) {
		for (ServerLevel lv : srv.getAllLevels()) {
			for (HrafnholtLongship s : List.copyOf(lv.getEntities(KingdomEntities.LONGSHIP, e -> true))) lost(lv, s);
		}
		VOYAGES.clear();
		ROUTES.clear();
	}

	/** Tests: forget voyages and cached routes. */
	public static void resetVoyages() {
		VOYAGES.clear();
		ROUTES.clear();
	}
}
