package dev.elsewhere.kingdom.tactics;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a {@link Perception} (FIELD-TACTICS 1.3, 1.10): at most 8 candidates per soldier and 30 line-of-sight rays per decision, one entity query on a 40-block box, one terrain
 * scan per 100 ticks. Reads only what a player could see (held and worn items, mount, elytra); it imports no Standing or inventory API, and an unseen contact is a ghost
 * that is never targeted (test 6.24).
 */
public final class Perceiver {
	private Perceiver() {}

	public static final int MAX_RAYS = 30, SIGHT = 32, SIGHT_ARCHER = 48, GHOST_TICKS = 100;
	static final Comparator<Perception.Contact> NEAR = Comparator.comparingDouble(c -> c.ageTicks());

	static String path(ItemStack s) {
		return s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
	}

	public static Perception.LoadoutSummary loadout(LivingEntity e) {
		int tier = 0;
		for (EquipmentSlot sl : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			String p = path(e.getItemBySlot(sl));
			int t = p.contains("netherite") ? 5 : p.contains("diamond") ? 4 : p.contains("iron") ? 3 : (p.contains("chainmail") || p.contains("gold") || p.contains("copper")) ? 2 : p.contains("leather") ? 1 : 0;
			tier = Math.max(tier, t);
		}
		String main = path(e.getMainHandItem()), off = path(e.getOffhandItem());
		String w = main.endsWith("_sword") ? "sword" : main.endsWith("_axe") ? "axe" : main.contains("mace") ? "mace" : main.endsWith("_spear") ? "spear" : main.contains("trident") ? "trident"
			: main.equals("bow") || main.equals("crossbow") ? "bow" : main.isEmpty() ? "none" : "other";
		boolean shield = main.equals("shield") || off.equals("shield");
		boolean ranged = w.equals("bow") || off.equals("bow");
		return new Perception.LoadoutSummary(tier, w, shield, ranged, e.getVehicle() != null, path(e.getItemBySlot(EquipmentSlot.CHEST)).contains("elytra"),
			main.equals("ender_pearl") || off.equals("ender_pearl"));
	}

	static double soldierValue(KingdomSoldier s) {
		var u = s.unit();
		double w = u.officer() ? 1.25 : u.role().ranged() ? 0.9 : (u.role() == dev.elsewhere.kingdom.UnitType.Role.LANCER || u.role() == dev.elsewhere.kingdom.UnitType.Role.CAVALRY) ? 1.3 : 1.0;
		return w * Math.max(0.05, s.getHealth() / s.getMaxHealth());
	}

	static boolean sees(KingdomSoldier s, LivingEntity e) {
		double r = s.role().ranged() ? SIGHT_ARCHER : SIGHT;
		return s.distanceToSqr(e) <= r * r && s.hasLineOfSight(e);
	}

	/** 3.0 shared candidate cache: one entity query per 16-block cell, reused by every squad deciding there within {@link #CACHE_TICKS}. */
	static final int CACHE_TICKS = 5;
	private static final java.util.Map<Long, List<LivingEntity>> NEARBY = new java.util.HashMap<>();
	private static long nearbyTick = Long.MIN_VALUE;
	private static ServerLevel nearbyLevel;
	public static long queries, cacheHits;
	/** Set by the armies overhaul (kill switch {@code enabled}); off = one query per decision as in 2.1. */
	public static boolean shareQueries;

	/** Drops the shared candidates (called when a living entity joins a level, so a new contact is never missed). */
	public static void invalidate() {
		NEARBY.clear();
	}

	/** Living entities around the cell of {@code c}: a superset of every 40x12 box centred in that cell, padded for movement within the cache window. */
	static List<LivingEntity> nearby(ServerLevel level, Vec3 c, long now) {
		if (!shareQueries) {
			queries++;
			return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(40, 12, 40), LivingEntity::isAlive);
		}
		if (level != nearbyLevel || now - nearbyTick >= CACHE_TICKS || now < nearbyTick) {
			NEARBY.clear();
			nearbyTick = now;
			nearbyLevel = level;
		}
		int cx = (int) Math.floor(c.x / 16), cy = (int) Math.floor(c.y / 16), cz = (int) Math.floor(c.z / 16);
		long key = ((long) cx & 0x3FFFFFL) << 42 | ((long) cz & 0x3FFFFFL) << 20 | ((long) cy & 0xFFFFFL);
		List<LivingEntity> l = NEARBY.get(key);
		if (l != null) {
			cacheHits++;
			return l;
		}
		queries++;
		AABB cell = new AABB(cx * 16, cy * 16, cz * 16, cx * 16 + 16, cy * 16 + 16, cz * 16 + 16).inflate(40 + 8, 12 + 8, 40 + 8);
		l = level.getEntitiesOfClass(LivingEntity.class, cell, LivingEntity::isAlive);
		NEARBY.put(key, l);
		return l;
	}

	public static Perception build(ServerLevel level, SquadBrain b, List<KingdomSoldier> bodies, long now) {
		Squad squad = b.squad;
		SquadMemory mem = b.memory;
		Vec3 centre = Vec3.ZERO;
		for (KingdomSoldier s : bodies) centre = centre.add(s.position());
		centre = bodies.isEmpty() ? Vec3.ZERO : centre.scale(1.0 / bodies.size());
		KingdomSoldier self = bodies.isEmpty() ? null : bodies.get(0);
		// friends
		List<Perception.Friendly> friends = new ArrayList<>();
		double mine = 0;
		for (KingdomSoldier s : bodies) {
			boolean shield = path(s.getOffhandItem()).equals("shield");
			friends.add(new Perception.Friendly(s.getUUID(), s.position(), s.getHealth() / s.getMaxHealth(), b.roleOf(s.getUUID()), shield, s.role().ranged(), squad.leaderEntity(level) == s));
			mine += soldierValue(s);
		}
		double vet = dev.elsewhere.kingdom.war.WarApi.veterancyOrZero(level.getServer(), squad.faction());
		mine *= 1 + vet;
		// candidates: one query on a 40-block box
		AABB box = new AABB(centre, centre).inflate(40, 12, 40);
		List<LivingEntity> cands = new ArrayList<>();
		for (LivingEntity e : nearby(level, centre, now))
			if (e.isAlive() && !e.isRemoved() && e.getBoundingBox().intersects(box) && !(e instanceof KingdomSoldier ks && ks.factionId().equals(squad.faction()))) cands.add(e);
		final Vec3 cc = centre;
		cands.sort(Comparator.comparingDouble(e -> e.distanceToSqr(cc)));
		List<Perception.Contact> contacts = new ArrayList<>();
		java.util.Set<UUID> seenNow = new java.util.HashSet<>();
		int rays = 0;
		double sigma = b.intelNoise();
		double theirs = 0;
		int taken = 0;
		for (LivingEntity e : cands) {
			if (taken >= 8 * Math.max(1, bodies.size()) || taken >= 24) break;
			String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
			boolean hazard = e instanceof Creeper || type.equals("warden") || type.equals("enderman");
			boolean legal = KingdomTargeting.canAttack(squad.faction(), squad, self, e);
			if (!legal && !hazard) continue;
			if (hazard && !legal && e.distanceToSqr(cc) > 24 * 24) continue;
			taken++;
			boolean visible = false;
			for (KingdomSoldier s : bodies) {
				if (rays >= MAX_RAYS) break;
				double r = s.role().ranged() ? SIGHT_ARCHER : SIGHT;
				if (s.distanceToSqr(e) > r * r) continue;
				rays++;
				if (s.hasLineOfSight(e)) {
					visible = true;
					break;
				}
			}
			Types.ContactKind kind = e instanceof Player ? Types.ContactKind.PLAYER : e instanceof KingdomSoldier ? Types.ContactKind.KINGDOM_SOLDIER : Types.ContactKind.HOSTILE_MOB;
			Perception.LoadoutSummary lo = visible && e.distanceToSqr(cc) < 24 * 24 ? loadout(e) : null;
			double hpf = e.getHealth() / Math.max(1f, e.getMaxHealth());
			double threat = e instanceof Player pl ? (lo == null ? 1.5 : lo.pcv(hpf)) : e instanceof KingdomSoldier ks ? soldierValue(ks) : (hazard ? 0.0 : 0.6) * hpf;
			boolean boat = e.getVehicle() instanceof AbstractBoat;
			boolean primed = e instanceof Creeper cr && (cr.isIgnited() || cr.getSwellDir() > 0);
			boolean ranged = lo != null ? lo.ranged() : type.equals("skeleton") || type.equals("stray") || type.equals("pillager");
			boolean comm = e instanceof KingdomSoldier ks2 && ks2.isOfficer();
			Perception.Contact c = new Perception.Contact(e.getUUID(), kind, e.position(), 0, visible, lo, threat, e.isInWater(), e.getY() > cc.y + 2.5, type, primed, hpf, ranged,
				lo != null && lo.shield(), comm, false, boat);
			if (visible) {
				seenNow.add(e.getUUID());
				mem.lastSeen.put(e.getUUID(), c);
				contacts.add(c);
				if (!hazard) {
					double noise = mem.noise.computeIfAbsent(e.getUUID(), id -> Math.exp(new Random(id.getLeastSignificantBits() ^ b.squadId.getMostSignificantBits()).nextGaussian() * sigma));
					theirs += threat * noise;
				}
				if (e instanceof Player pl) {
					SquadMemory.PlayerFile f = mem.player(pl.getUUID());
					f.lastSeen = now;
					if (lo != null) f.loadout = lo;
				}
			}
		}
		// ghosts: unseen contacts remembered for 100 ticks (never targeted)
		for (var it = mem.lastSeen.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			if (seenNow.contains(en.getKey())) continue;
			Perception.Contact old = en.getValue();
			int age = (int) (now - b.lastPerceptionTick) + old.ageTicks();
			if (age > GHOST_TICKS * 3) {
				it.remove();
				continue;
			}
			Perception.Contact g = new Perception.Contact(old.id(), old.kind(), old.lastSeen(), age, false, old.loadout(), old.estThreat(), old.inWater(), old.onHighGround(), old.type(), old.primed(),
				old.hpFraction(), old.ranged(), old.shielded(), old.commander(), old.banner(), old.inBoat());
			en.setValue(g);
			if (age <= GHOST_TICKS) contacts.add(g);
		}
		for (Perception.IntelSource src : b.intel) {
			if (!b.profile.intelFilter().allows(src)) continue;
			for (Perception.Contact c : src.contacts(now)) if (!seenNow.contains(c.id())) {
				contacts.add(c);
				theirs += c.estThreat();
			}
		}
		if (seenNow.isEmpty() && contacts.stream().noneMatch(Perception.Contact::visibleNow)) {
			if (mem.encounterStart >= 0 && now - mem.encounterStart > 1200) mem.endEncounter();
		} else if (mem.encounterStart < 0) mem.encounterStart = now;
		double ratio = theirs <= 1e-6 ? 99 : mine / theirs;
		double conf = sigma <= 0.1 ? 0.95 : sigma <= 0.3 ? 0.7 : 0.4;
		Perception.TerrainRead tr = b.terrainRead(level, centre, now);
		boolean night = level.getGameTime() % 24000 > 13000 && level.getGameTime() % 24000 < 23000;
		return new Perception(now, centre, contacts, friends, new Perception.OddsEstimate(mine, theirs, ratio, conf), tr,
			new Perception.Environment(night, level.isRaining(), level.isThundering()), new ArrayList<>(mem.recent), b.intel);
	}

	/** Scans a ring around the centre for the best ledge (height +3 or more) and notes water or lava; cheap (24 heightmap reads), cached by the brain for 100 ticks. */
	public static Perception.TerrainRead scan(ServerLevel level, Vec3 c) {
		BlockPos best = null;
		boolean water = false, lava = false;
		for (int r = 8; r <= 16; r += 4) {
			for (int a = 0; a < 8; a++) {
				double ang = a * Math.PI / 4;
				int x = (int) Math.floor(c.x + Math.cos(ang) * r), z = (int) Math.floor(c.z + Math.sin(ang) * r);
				if (!level.isLoaded(new BlockPos(x, (int) c.y, z))) continue;
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos below = new BlockPos(x, h - 1, z);
				var fs = level.getFluidState(below);
				if (!fs.isEmpty()) {
					if (fs.is(net.minecraft.tags.FluidTags.LAVA)) lava = true;
					else water = true;
					continue;
				}
				if (h >= c.y + 3 && h <= c.y + 8 && (best == null || h > best.getY())) best = new BlockPos(x, h, z);
			}
		}
		return new Perception.TerrainRead(best, water, lava, false);
	}
}
