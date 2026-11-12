package dev.elsewhere.boss.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Everything the boss kit keeps on disk, one JSON file per world ({@code <world>/data/elsewhere_boss_kit.json}):
 * pity counters, re-summon cooldowns, active fights and pending summon-item refunds. Every mutation saves at once, so
 * a crash or restart never loses or re-arms anything. Time is the overworld game time (persistent).
 *
 * <p><b>Persistent cooldowns for any module</b> (e.g. the Petalwind re-summons): replace the in-memory map with
 * {@code KitStore.of(server).cooldownStart("kazehana_seal", 12000)} and test with
 * {@code KitStore.of(server).cooldownRemaining("kazehana_seal") > 0}. The key can be any string (include the arena
 * position if several sites share a boss).
 */
public final class KitStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static @Nullable KitStore current;
	private static @Nullable MinecraftServer currentServer;

	/** Plain data, serialised as-is. */
	public static final class Data {
		public Map<String, Integer> pity = new HashMap<>();
		public Map<String, Long> cooldowns = new HashMap<>();
		public Map<String, Fight> fights = new HashMap<>();
		public List<Refund> refunds = new ArrayList<>();
		public Map<String, List<String>> kills = new HashMap<>();
	}

	public static final class Fight {
		public String boss;
		public String summoner;
		public String item;
		public int count;
		public String dim;
		public int x, y, z;
	}

	public static final class Refund {
		public String player;
		public String item;
		public int count;
		public long due;
		public String reason;
	}

	private final Path file;
	private final LongSupplier clock;
	private Data data = new Data();

	public KitStore(Path file, LongSupplier clock) {
		this.file = file;
		this.clock = clock;
		load();
	}

	/** The store for this server (created and loaded on first use; replaced when the server object changes). */
	public static synchronized KitStore of(MinecraftServer server) {
		if (current == null || currentServer != server) {
			Path f = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("elsewhere_boss_kit.json");
			current = new KitStore(f, () -> server.overworld().getGameTime());
			currentServer = server;
		}
		return current;
	}

	public static synchronized void forget() {
		current = null;
		currentServer = null;
	}

	public Data data() { return data; }

	public long now() { return clock.getAsLong(); }

	public void load() {
		try {
			if (Files.exists(file)) {
				Data d = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
				if (d != null) data = d;
			}
		} catch (Exception e) {
			System.err.println("[elsewhere boss kit] could not read " + file + ": " + e);
		}
		if (data.pity == null) data.pity = new HashMap<>();
		if (data.cooldowns == null) data.cooldowns = new HashMap<>();
		if (data.fights == null) data.fights = new HashMap<>();
		if (data.refunds == null) data.refunds = new ArrayList<>();
		if (data.kills == null) data.kills = new HashMap<>();
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			System.err.println("[elsewhere boss kit] could not save " + file + ": " + e);
		}
	}

	// ---- cooldowns ---------------------------------------------------------------------------------------

	public void cooldownStart(String key, int ticks) {
		data.cooldowns.put(key, now() + ticks);
		save();
	}

	/** Ticks left (0 = ready). */
	public int cooldownRemaining(String key) {
		Long due = data.cooldowns.get(key);
		if (due == null) return 0;
		long left = due - now();
		if (left <= 0) {
			data.cooldowns.remove(key);
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, left);
	}

	public boolean cooldownReady(String key) { return cooldownRemaining(key) == 0; }

	// ---- pity / kills ------------------------------------------------------------------------------------

	/** Live miss counters for one player and boss (unique id to misses); call {@link #save()} after changing. */
	public Map<String, Integer> missesView(UUID player, String boss) {
		return new PityView(player + "|" + boss + "|");
	}

	private final class PityView extends java.util.AbstractMap<String, Integer> {
		private final String prefix;

		PityView(String prefix) { this.prefix = prefix; }

		@Override public Integer get(Object k) { return data.pity.get(prefix + k); }

		@Override public Integer getOrDefault(Object k, Integer d) { return data.pity.getOrDefault(prefix + k, d); }

		@Override public Integer put(String k, Integer v) { return data.pity.put(prefix + k, v); }

		@Override public java.util.Set<Entry<String, Integer>> entrySet() {
			Map<String, Integer> m = new HashMap<>();
			data.pity.forEach((k, v) -> { if (k.startsWith(prefix)) m.put(k.substring(prefix.length()), v); });
			return m.entrySet();
		}
	}

	public boolean hasKilled(UUID player, String boss) {
		List<String> l = data.kills.get(player.toString());
		return l != null && l.contains(boss);
	}

	public void markKilled(UUID player, String boss) {
		List<String> l = data.kills.computeIfAbsent(player.toString(), k -> new ArrayList<>());
		if (!l.contains(boss)) l.add(boss);
		save();
	}

	// ---- fights and refunds ------------------------------------------------------------------------------

	public void fightStart(UUID bossEntity, String boss, @Nullable UUID summoner, ItemStack summonItem, String dim, int x, int y, int z) {
		Fight f = new Fight();
		f.boss = boss;
		f.summoner = summoner == null ? "" : summoner.toString();
		f.item = summonItem.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(summonItem.getItem()).toString();
		f.count = summonItem.getCount();
		f.dim = dim;
		f.x = x;
		f.y = y;
		f.z = z;
		data.fights.put(bossEntity.toString(), f);
		save();
	}

	public boolean fightActive(UUID bossEntity) { return data.fights.containsKey(bossEntity.toString()); }

	/** The fight ended in a win: forget it (no refund). */
	public void fightWon(UUID bossEntity) {
		if (data.fights.remove(bossEntity.toString()) != null) save();
	}

	/**
	 * The fight ended in a wipe (everyone died or left) or was interrupted by a restart: forget it and queue the summon
	 * item for the summoner after {@link BossKit#RETRY_DELAY_TICKS}. Idempotent (a second call does nothing).
	 * @return true if a refund was queued
	 */
	public boolean fightWiped(UUID bossEntity, String reason) {
		Fight f = data.fights.remove(bossEntity.toString());
		if (f == null) return false;
		if (!f.item.isEmpty() && !f.summoner.isEmpty() && f.count > 0) {
			Refund r = new Refund();
			r.player = f.summoner;
			r.item = f.item;
			r.count = f.count;
			r.due = now() + BossKit.RETRY_DELAY_TICKS;
			r.reason = reason;
			data.refunds.add(r);
		}
		save();
		return true;
	}

	/** Server start: every fight still on file was cut short by the restart; treat it as a wipe. Returns how many. */
	public int recoverInterrupted() {
		int n = 0;
		for (String id : new ArrayList<>(data.fights.keySet())) {
			if (fightWiped(UUID.fromString(id), "restart")) n++;
		}
		return n;
	}

	/** Refunds whose delay has passed; removed from the store, the caller delivers them. */
	public List<Refund> takeDueRefunds(java.util.function.Predicate<UUID> playerOnline) {
		List<Refund> out = new ArrayList<>();
		long t = now();
		for (Iterator<Refund> it = data.refunds.iterator(); it.hasNext();) {
			Refund r = it.next();
			if (r.due <= t && playerOnline.test(UUID.fromString(r.player))) {
				out.add(r);
				it.remove();
			}
		}
		if (!out.isEmpty()) save();
		return out;
	}

	public static @Nullable ItemStack stackOf(String item, int count) {
		Identifier id = Identifier.tryParse(item);
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id), count);
	}
}
