package dev.elsewhere.petalwind_glasswaste.s3.groups;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ARMIES.md section 6 spawn groups for the petalwind_glasswaste module (s-spawngroups, 3.0.0): bigger, rarer groups inside the vanilla
 * mob caps (6.1 rule 1, the 6.2 table) plus the group behaviour in {@link Herds3}.
 * <p>
 * Group sizes: each 6.2 creature spawns in its 3.0 group, and its weight is {@code old weight x old mean / new mean}, so
 * the expected head count per spawn pick is unchanged and the category caps still bound everything. Rows the armies lane
 * already shipped in the biome JSON (9.11) stay as data; the remaining rows are converted here, at biome load, through
 * Fabric's biome modification API, so no other lane's biome file is edited.
 * <p>
 * Kill switch {@value #KEY} (default on) in {@code config/elsewhere-overhaul3.json}, or the JVM flag
 * {@code -Delsewhere.overhaul3.}{@value #KEY}{@code =false}: every group in this module goes back to its exact 2.1 size and
 * weight (the {@link Groups3Data#OLD} table, taken from the pre-3.0 data), and no group behaviour registers.
 * Read once at class load; nothing polls.
 */
public final class SpawnGroups3 {
	private SpawnGroups3() {}

	public static final String KEY = "spawn_groups_petalwind_glasswaste";
	public static final boolean ON = on(KEY);
	static final Logger LOG = LoggerFactory.getLogger("Elsewhere/spawn_groups/petalwind_glasswaste");
	private static boolean done;

	/** One 6.2 row converted in code: the 2.1 count range and the 3.0 one. */
	public record Size(String id, int oldMin, int oldMax, int newMin, int newMax) {
		double oldMean() {
			return (oldMin + oldMax) / 2.0;
		}

		double newMean() {
			return (newMin + newMax) / 2.0;
		}
	}

	/** One 2.1 entry the off switch restores exactly: biome, type, the 3.0 entry it replaces and the 2.1 values. */
	public record Old(String biome, String id, int newMin, int newMax, int newWeight, int oldMin, int oldMax, int oldWeight) {}

	public static void init() {
		if (done) return;
		done = true;
		Herds3.rules(Groups3Data.RULES);
		BiomeModifications.create(Identifier.fromNamespaceAndPath("elsewhere", "s3_spawn_groups_petalwind_glasswaste"))
			.add(ModificationPhase.REPLACEMENTS,
				sel -> "elsewhere".equals(sel.getBiomeKey().identifier().getNamespace()) && Groups3Data.BIOMES.contains(sel.getBiomeKey().identifier().getPath()),
				(sel, ctx) -> apply(sel.getBiomeKey().identifier().getPath(), ctx.getMobSpawnSettings(), ON));
		Herds3.init();
		LOG.info("[spawn_groups] petalwind_glasswaste: {} ({} rows in code, {} JSON rows, {} grouped creatures)", ON ? "on" : "OFF (2.1 groups)",
			Groups3Data.SIZES.size(), Groups3Data.OLD.size(), Groups3Data.RULES.size());
	}

	/** Converts every matching entry of one biome; returns how many entries changed. */
	static int apply(final String biome, final BiomeModificationContext.MobSpawnSettingsContext ctx, final boolean on) {
		int changed = 0;
		for (MobCategory cat : MobCategory.values()) {
			List<Weighted<MobSpawnSettings.SpawnerData>> list = new ArrayList<>(ctx.getMobs(cat));
			for (Weighted<MobSpawnSettings.SpawnerData> w : list) {
				MobSpawnSettings.SpawnerData d = w.value();
				Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(d.type());
				if (!"elsewhere".equals(id.getNamespace())) continue;
				int[] to = convert(biome, id.getPath(), d.count().minInclusive(), d.count().maxInclusive(), w.weight(), on);
				if (to == null) continue;
				ctx.removeSpawns((c, x) -> c == cat && x == d);
				ctx.addSpawn(cat, new MobSpawnSettings.SpawnerData(d.type(), count(to[0], to[1])), to[2]);
				changed++;
			}
		}
		return changed;
	}

	static IntProvider count(final int min, final int max) {
		return min == max ? ConstantInt.of(min) : UniformInt.of(min, max);
	}

	/**
	 * The pure conversion: {@code {min, max, weight}} for one spawn entry, or null when it stays as it is.
	 * On: a 2.1 entry of a code row becomes its 3.0 group (weight scaled to keep the expected count). Off: a 3.0 entry
	 * goes back to its exact 2.1 values (table), or by the inverse formula for the code rows.
	 */
	public static int[] convert(final String biome, final String id, final int min, final int max, final int weight, final boolean on) {
		if (on) {
			for (Size s : Groups3Data.SIZES) {
				if (s.id().equals(id) && s.oldMin() == min && s.oldMax() == max) {
					return new int[] {s.newMin(), s.newMax(), Math.max(1, (int) Math.round(weight * s.oldMean() / s.newMean()))};
				}
			}
			return null;
		}
		for (Old o : Groups3Data.OLD) {
			if (o.biome().equals(biome) && o.id().equals(id) && o.newMin() == min && o.newMax() == max && o.newWeight() == weight) {
				return new int[] {o.oldMin(), o.oldMax(), o.oldWeight()};
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ config

	static boolean on(final String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (Files.isRegularFile(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (o.has(key)) return o.get(key).getAsBoolean();
			}
		} catch (Exception e) {
			// unreadable or malformed: keep the default
		}
		return true;
	}

	// ------------------------------------------------------------------ self-test

	/**
	 * Self-test checks for this module (synchronous, from the module self-test on SERVER_STARTED). {@code check} takes
	 * (ok, "name: detail"). Check names start with {@code spawn_groups_petalwind_glasswaste:}.
	 */
	public static void selfTest(final MinecraftServer server, final BiConsumer<Boolean, String> check) {
		String n = "spawn_groups_petalwind_glasswaste: ";
		check.accept(ON, n + "kill switch " + KEY + " defaults on");
		// 1. every 6.2 row has its 3.0 group in every biome where it spawns
		Map<String, Integer> found = new HashMap<>();
		List<String> bad = new ArrayList<>();
		var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
		for (String b : Groups3Data.BIOMES) {
			Biome biome = biomes.getValue(Identifier.fromNamespaceAndPath("elsewhere", b));
			if (biome == null) {
				if (!Groups3Data.FOREIGN_BIOMES.contains(b)) bad.add(b + " missing");
				continue; // another module's biome, not loaded in this module's own dev run
			}
			MobSpawnSettings s = biome.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue());
			for (MobCategory cat : MobCategory.values()) {
				for (Weighted<MobSpawnSettings.SpawnerData> w : s.getMobsInCategory(cat).unwrap()) {
					Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type());
					int[] want = Groups3Data.TARGET.get(id.getPath());
					if (!"elsewhere".equals(id.getNamespace()) || want == null) continue;
					int lo = w.value().count().minInclusive(), hi = w.value().count().maxInclusive();
					if (lo == want[0] && hi == want[1] && w.weight() > 0) found.merge(id.getPath(), 1, Integer::sum);
					else if (!Groups3Data.KEEP.contains(b + "|" + id.getPath() + "|" + lo + "|" + hi)) bad.add(b + " " + id.getPath() + " " + lo + "-" + hi + " w" + w.weight());
				}
			}
		}
		for (String id : Groups3Data.TARGET.keySet()) {
			boolean foreignOnly = Groups3Data.FOREIGN_IDS.contains(id) && Groups3Data.FOREIGN_BIOMES.stream().anyMatch(b -> biomes.getValue(Identifier.fromNamespaceAndPath("elsewhere", b)) == null);
			if (!found.containsKey(id) && !foreignOnly) bad.add(id + " spawns in no biome");
		}
		check.accept(bad.isEmpty(), n + "6.2 group sizes in every biome " + found + (bad.isEmpty() ? "" : " BAD " + bad));
		// 2. the off switch restores the exact 2.1 entries; the code rows round-trip and keep the expected head count
		List<String> trip = new ArrayList<>();
		for (Old o : Groups3Data.OLD) {
			int[] back = convert(o.biome(), o.id(), o.newMin(), o.newMax(), o.newWeight(), false);
			if (back == null || back[0] != o.oldMin() || back[1] != o.oldMax() || back[2] != o.oldWeight()) trip.add(o.biome() + "/" + o.id());
			double before = o.oldWeight() * (o.oldMin() + o.oldMax()) / 2.0, after = o.newWeight() * (o.newMin() + o.newMax()) / 2.0;
			if (Math.abs(after - before) > (o.newMin() + o.newMax()) / 2.0 + 0.01 || o.newMin() < o.oldMin()) trip.add(o.id() + " head count " + before + "->" + after);
		}
		for (Size s : Groups3Data.SIZES) {
			int[] to = convert("x", s.id(), s.oldMin(), s.oldMax(), 40, true);
			double before = 40 * s.oldMean(), after = to == null ? -1 : to[2] * s.newMean();
			if (to == null || to[0] != s.newMin() || to[1] != s.newMax() || Math.abs(after - before) > s.newMean() / 2 + 0.01 || s.newMin() < s.oldMin()) {
				trip.add(s.id() + " " + before + "->" + after);
			}
		}
		check.accept(trip.isEmpty(), n + "off switch restores exact 2.1 groups; expected head count per pick unchanged " + trip);
		// 3-5. live groups: a pack forms, a hurt member alerts the pack, a dead leader hands over
		ServerLevel level = server.overworld();
		BlockPos at = new BlockPos(Groups3Data.TEST_X, 200, 29_000);
		int cx = at.getX() >> 4, cz = at.getZ() >> 4;
		List<Entity> spawned = new ArrayList<>();
		try {
			level.setChunkForced(cx, cz, true);
			level.getChunk(cx, cz);
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("elsewhere", Groups3Data.TEST_TYPE));
			Herds3.Rule rule = Herds3.ruleFor(Groups3Data.TEST_TYPE);
			List<Mob> pack = new ArrayList<>();
			for (int i = 0; i < 4 && rule != null; i++) {
				if (type.create(level, EntitySpawnReason.NATURAL) instanceof Mob m) {
					m.setPos(at.getX() + 0.5 + i * 1.5, at.getY(), at.getZ() + 0.5);
					m.setNoGravity(true);
					level.addFreshEntity(m);
					spawned.add(m);
					Herds3.join(m, level, rule);
					pack.add(m);
				}
			}
			long leaders = pack.stream().filter(Herds3::isLeader).count();
			Mob lead = pack.stream().filter(Herds3::isLeader).findFirst().orElse(null);
			boolean follow = lead != null && pack.stream().filter(m -> m != lead).allMatch(m -> lead.getUUID().equals(Herds3.leaderId(m)) && Herds3.leaderOf(m) == lead
				&& m.getGoalSelector().getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof Herds3.FollowLeader3) == rule.steer());
			boolean grown = lead != null && (!rule.grow() || lead.getScale() > 1.1F);
			check.accept(pack.size() == 4 && leaders == 1 && follow && grown, n + "a natural pack of " + Groups3Data.TEST_TYPE + " forms one leader + 3 followers (steer "
				+ (rule == null ? "?" : rule.steer()) + ", leader scale " + (lead == null ? "?" : lead.getScale()) + ")");
			// the 10-tick decision gate: canUse is evaluated every other tick, so one in five calls decides
			Mob f0 = pack.size() > 1 ? pack.get(pack.indexOf(lead) == 0 ? 1 : 0) : null;
			int decided = 0;
			if (f0 != null) {
				int t0 = f0.tickCount;
				for (int t = 0; t < 100; t += 2) {
					f0.tickCount = t0 + t;
					if (Herds3.FollowLeader3.decides(f0)) decided++;
				}
				f0.tickCount = t0;
			}
			check.accept(decided == 10, n + "followers decide every 10 ticks, not every tick (" + decided + " decisions in 100 ticks)");
			// alert: hurt one follower with a zombie's blow; the rest of the group remembers the attacker
			Zombie z = new Zombie(level);
			z.setPos(at.getX() + 0.5, at.getY() + 4, at.getZ() + 3.5);
			z.setNoAi(true);
			z.setNoGravity(true);
			level.addFreshEntity(z);
			spawned.add(z);
			boolean alerted = false;
			level.getEntitiesOfClass(Mob.class, z.getBoundingBox().inflate(32), e -> true); // warm-up query (GOTCHAS: arsenal)
			if (f0 != null && rule != null) {
				f0.hurtServer(level, level.damageSources().mobAttack(z), 1.0F);
				alerted = rule.alert() == Herds3.Alert.NONE || pack.stream().filter(m -> m != f0).allMatch(m -> m.getLastHurtByMob() == z);
			}
			check.accept(alerted, n + "hurting one member alerts the whole group (" + (rule == null ? "?" : rule.alert()) + ")");
			// succession: the leader dies, one follower takes over, the others re-point to it
			boolean handed = false;
			if (lead != null) {
				lead.kill(level);
				UUID old = lead.getUUID();
				for (Mob m : pack) if (m != lead) Herds3.leaderOf(m);
				List<Mob> heirs = pack.stream().filter(m -> m != lead && Herds3.isLeader(m)).toList();
				Mob heir = heirs.size() == 1 ? heirs.get(0) : null;
				handed = heir != null && pack.stream().filter(m -> m != lead && m != heir).allMatch(m -> heir.getUUID().equals(Herds3.leaderId(m)) && Herds3.leaderOf(m) == heir)
					&& !old.equals(heir.getUUID());
			}
			check.accept(handed, n + "a dead leader hands the group to one follower");
			// kill switch on saved mobs: strip() removes the leader size and the group tags
			boolean stripped = false;
			if (!pack.isEmpty()) {
				Mob m = pack.get(pack.size() - 1);
				Herds3.grow(m, true);
				Herds3.strip(m);
				stripped = m.getScale() < 1.05F && m.entityTags().stream().noneMatch(t -> t.startsWith("elsewhere_herd"));
			}
			check.accept(stripped, n + "switched off, a saved leader loses its size and group tags");
		} catch (Throwable t) {
			check.accept(false, n + "live group test threw " + t);
			LOG.error("[spawn_groups] self-test", t);
		} finally {
			for (Entity e : spawned) e.discard();
			level.setChunkForced(cx, cz, false);
		}
	}
}
