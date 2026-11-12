package dev.elsewhere.boss.kit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * ARMIES 6.3 "more bodies, less each": the shared boss add-wave helper and group brain (s-bossadds, 3.0.0).
 *
 * <p>Byte-identical copies live in every boss module that has adds (greenhaze_hush, verdigris_ironpeak, overworld_bosses,
 * nether_end_bosses); same package, so the merged jar keeps one. Never edit one copy alone: edit greenhaze_hush's and copy it.
 * API and per-boss numbers: {@code ElsewhereContent/_swarm/BOSSADDS-API.md}.
 *
 * <ul>
 *   <li><b>Cap:</b> at most 8 live adds, +3 per extra player, and never past the arena budget (boss + parts + adds &lt;= 24).</li>
 *   <li><b>Fodder:</b> 25-40% of today's add HP, no armor, dies in 1-2 hits of the tier's reference weapon, less damage, no drops or XP.</li>
 *   <li><b>Elites:</b> today's adds unchanged, at most 2 + 1 per extra player.</li>
 *   <li><b>Waves:</b> 3-6 at a time from visible sources, telegraphed {@link #TELEGRAPH_TICKS} ticks ahead (sound, particles, a line).</li>
 *   <li><b>Brain:</b> one decision every {@link #BRAIN_EVERY} ticks hands out attack tokens per player inside today's add-DPS budget;
 *       the rest hold a ring around their player. A per-player damage window ({@link #WINDOW} ticks) is the hard fairness cap.</li>
 *   <li><b>Despawn:</b> {@link Pack#clear} on boss death, reset or discard; adds that load after their boss is gone are removed.</li>
 * </ul>
 * Event-driven: nothing scans the world per tick. The pack's own tick is a counter check; its work runs on the 10-tick decision,
 * a telegraph step every 5 ticks while a wave is coming, and on Fabric events (damage, entity load/unload, loot).
 *
 * <p>Kill switch: {@code boss_adds3} (all modules) or {@code boss_adds3.<module>} in {@code config/elsewhere-overhaul3.json}, or
 * {@code -Delsewhere.overhaul3.boss_adds3[.<module>]=false}. Off = the boss runs its 2.x add code; no pack is made.
 */
public final class AddWaves {
	private AddWaves() {}

	public static final int CAP_BASE = 8, CAP_PER_EXTRA = 3, ELITE_BASE = 2, ELITE_PER_EXTRA = 1;
	public static final int WAVE_MIN = 3, WAVE_MAX = 6;
	public static final int BRAIN_EVERY = 10, TELEGRAPH_TICKS = 30, WINDOW = 60;
	public static final int ARENA_ENTITY_BUDGET = 24, PROJECTILE_BUDGET = 24;
	public static final float FODDER_HP_MIN = 0.25F, FODDER_HP_MAX = 0.40F, FODDER_SCALE = 0.8F;
	public static final double HOLD_RADIUS = 6.0;
	/** Reference player hits per boss tier (iron, diamond, netherite sword) for the 1-2 hit fodder rule. */
	public static final double REF_HIT_T1 = 6, REF_HIT_T2 = 7, REF_HIT_T3 = 8;
	public static final String TAG_PREFIX = "elsewhere_add3_of_", TAG_FODDER = "elsewhere_add3_fodder", TAG_ELITE = "elsewhere_add3_elite";

	// ------------------------------------------------------------------------------------------------ kill switch

	private static final Map<String, Boolean> SWITCH = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> TEST_SWITCH = new ConcurrentHashMap<>();
	private static volatile JsonObject file;
	private static volatile boolean fileRead;

	/** {@code boss_adds3[.<module>]}, read once per module; default on. */
	public static boolean enabled(String module) {
		Boolean t = TEST_SWITCH.get(module);
		if (t != null) return t;
		return SWITCH.computeIfAbsent(module, m -> key("boss_adds3." + m, key("boss_adds3", true)));
	}

	/** Self-tests: force the switch for one module ({@code null} = back to config). */
	public static void setEnabledForTest(String module, Boolean on) {
		if (on == null) TEST_SWITCH.remove(module);
		else TEST_SWITCH.put(module, on);
	}

	private static boolean key(String key, boolean def) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		if (!fileRead) {
			try {
				Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
				if (Files.isRegularFile(p)) file = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			} catch (Exception e) {
				file = null;
			}
			fileRead = true;
		}
		try {
			if (file != null && file.has(key)) return file.get(key).getAsBoolean();
		} catch (RuntimeException e) {
			// a malformed value keeps the default
		}
		return def;
	}

	// ------------------------------------------------------------------------------------------------ the rules (pure)

	/** Live add cap: 8, +3 per extra player, and boss + parts + adds within the arena budget. */
	public static int cap(int players, int parts) {
		int n = Math.max(1, players);
		return Math.max(0, Math.min(CAP_BASE + CAP_PER_EXTRA * (n - 1), ARENA_ENTITY_BUDGET - 1 - Math.max(0, parts)));
	}

	/** Elite cap: 2, +1 per extra player. */
	public static int eliteCap(int players) {
		return ELITE_BASE + ELITE_PER_EXTRA * (Math.max(1, players) - 1);
	}

	/** Wave size: 3 + 1 per extra player, 3..6, never past the free room; 0 = no wave (less than 3 free). */
	public static int waveSize(int players, int room) {
		int want = Math.min(WAVE_MAX, WAVE_MIN + Math.max(1, players) - 1);
		int n = Math.min(want, room);
		return n < WAVE_MIN ? 0 : n;
	}

	/** Fodder HP fraction for an add with {@code todayHp}: the spec's wish, lowered until 2 reference hits kill it, kept in 25-40%. */
	public static float fodderHpFrac(Spec spec, double todayHp) {
		double twoHits = 2 * spec.refHit / Math.max(1, todayHp);
		return (float) Math.max(FODDER_HP_MIN, Math.min(FODDER_HP_MAX, Math.min(spec.fodderHp, twoHits)));
	}

	/** Fodder max HP for an add with {@code todayHp} (half-heart steps). */
	public static double fodderHp(Spec spec, double todayHp) {
		return Math.max(1, Math.floor(todayHp * fodderHpFrac(spec, todayHp) * 2) / 2.0);
	}

	/** Hits of {@code refHit} raw damage to kill {@code hp} behind {@code armor} (vanilla armor formula, no toughness). */
	public static int hitsToKill(double hp, double armor, double refHit) {
		double a = Math.max(0, Math.min(20, armor));
		double dealt = refHit * (1 - Math.min(20, Math.max(a / 5, a - refHit / 2)) / 25);
		return dealt <= 0 ? Integer.MAX_VALUE : (int) Math.ceil(hp / dealt - 1e-9);
	}

	/** One hit as a player takes it at this difficulty (vanilla Player.hurtServer scaling). */
	public static float difficultyHit(Difficulty d, float raw) {
		if (d == Difficulty.PEACEFUL) return 0;
		if (d == Difficulty.EASY) return Math.min(raw / 2 + 1, raw);
		if (d == Difficulty.HARD) return raw * 3 / 2;
		return raw;
	}

	/** Today's worst add pressure on one player in one {@link #WINDOW}: the solo cap of today's adds all landing their hits. */
	public static float budgetPerWindow(Spec spec, Difficulty d) {
		return spec.todayCapSolo * difficultyHit(d, spec.todayHit) * WINDOW / (float) Math.max(1, spec.todayInterval);
	}

	// ------------------------------------------------------------------------------------------------ per-boss data

	/** A boss's numbers. {@code today*} are measured from the 2.x add (see BOSSADDS-API.md "baselines"). */
	public static final class Spec {
		public final String module, bossId;
		/** Live add cap today with one player (the 2.x base). */
		public final int todayCapSolo;
		/** Raw pressure of one hit of today's add (damage, or haze for Percolax's buds) and the ticks between two of its hits. */
		public final float todayHit;
		public final int todayInterval;
		/** Wished fodder HP and damage fractions of today's add. */
		public final float fodderHp, fodderDmg;
		/** Reference player hit for the boss tier ({@link #REF_HIT_T1}..). */
		public final double refHit;
		/** Boss parts counted against the arena budget (tentacles, heads...). */
		public final int parts;

		public Spec(String module, String bossId, int todayCapSolo, float todayHit, int todayInterval, float fodderHp, float fodderDmg, double refHit, int parts) {
			this.module = module;
			this.bossId = bossId;
			this.todayCapSolo = todayCapSolo;
			this.todayHit = todayHit;
			this.todayInterval = todayInterval;
			this.fodderHp = fodderHp;
			this.fodderDmg = fodderDmg;
			this.refHit = refHit;
			this.parts = parts;
		}
	}

	/** What the wave looks like: the line fighters read, the particle at each source and the sound when it opens. */
	public record Theme(String call, ParticleOptions particle, SoundEvent sound, float pitch) {}

	/** Visible spawn points for a wave of {@code count} (portals, eggs, cracks, a rail the crew climbs). May return fewer; they are reused. */
	@FunctionalInterface
	public interface Sources {
		List<Vec3> pick(ServerLevel level, int count, RandomSource random);
	}

	/** Makes one add at {@code at} (not yet added to the level). {@code elite} = today's add; else the same type, made fodder by the pack. */
	@FunctionalInterface
	public interface Factory {
		Mob create(ServerLevel level, Vec3 at, boolean elite);
	}

	/** Optional ground mark at each source while the wave is coming (the module's Telegraph decal). */
	@FunctionalInterface
	public interface Marker {
		void mark(ServerLevel level, Vec3 at, int ticks);
	}

	// ------------------------------------------------------------------------------------------------ live registry + events

	private static final Map<UUID, Pack> PACKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Pack> ADDS = new ConcurrentHashMap<>();
	private static final List<Orphan> ORPHANS = new ArrayList<>();
	/** Peak live boss projectiles per boss id (census from entity load/unload; self-test reads it). */
	public static final Map<String, Integer> PEAK_PROJECTILES = new ConcurrentHashMap<>();
	private static boolean inited;

	private record Orphan(ServerLevel level, Mob mob, UUID boss, long due) {}

	/** Registers the Fabric events once. Idempotent; called by every pack and by the modules' init. */
	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Player p) || ADDS.isEmpty()) return true;
			Entity a = source.getEntity();
			if (a == null) return true;
			Pack pk = ADDS.get(a.getUUID());
			if (pk == null || !(p.level() instanceof ServerLevel sl)) return true;
			return pk.admit(a, p.getUUID(), sl.getGameTime(), amount, false);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile pr) {
				if (PACKS.isEmpty()) return;
				Entity owner = pr.getOwner();
				Pack pk = owner == null ? null : PACKS.get(owner.getUUID());
				if (pk != null) pk.projectileLoaded(pr);
				return;
			}
			if (!(entity instanceof Mob m)) return;
			UUID boss = bossOf(m);
			if (boss == null) return;
			Pack pk = PACKS.get(boss);
			if (pk != null) pk.readopt(m);
			else synchronized (ORPHANS) { ORPHANS.add(new Orphan(level, m, boss, level.getGameTime() + 100)); }
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Projectile pr && !PACKS.isEmpty()) {
				Entity owner = pr.getOwner();
				Pack pk = owner == null ? null : PACKS.get(owner.getUUID());
				if (pk != null) pk.projectileUnloaded(pr);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ORPHANS.isEmpty() || server.getTickCount() % 20 != 0) return;
			synchronized (ORPHANS) {
				for (Iterator<Orphan> it = ORPHANS.iterator(); it.hasNext(); ) {
					Orphan o = it.next();
					Pack pk = PACKS.get(o.boss);
					if (pk != null) { pk.readopt(o.mob); it.remove(); continue; }
					if (o.level.getGameTime() < o.due) continue;
					Entity b = o.level.getEntity(o.boss);
					if (b == null || !b.isAlive()) { if (!o.mob.isRemoved()) o.mob.discard(); }
					it.remove();
				}
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Entity e = context.getOptional(LootContextParams.THIS_ENTITY);
			if (e != null && !(e instanceof Player) && e.entityTags().contains(TAG_FODDER)) drops.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PACKS.clear();
			ADDS.clear();
			synchronized (ORPHANS) { ORPHANS.clear(); }
		});
	}

	/** The boss UUID an add belongs to (from its tag), or null. */
	public static UUID bossOf(Entity e) {
		for (String t : e.entityTags()) {
			if (t.startsWith(TAG_PREFIX)) {
				try {
					return UUID.fromString(t.substring(TAG_PREFIX.length()));
				} catch (IllegalArgumentException ex) {
					return null;
				}
			}
		}
		return null;
	}

	public static boolean isFodder(Entity e) {
		return e.entityTags().contains(TAG_FODDER);
	}

	public static boolean isElite(Entity e) {
		return e.entityTags().contains(TAG_ELITE);
	}

	/** The pack that owns this add, or null. */
	public static Pack packOf(Entity add) {
		return ADDS.get(add.getUUID());
	}

	/**
	 * Non-damage pressure from an add (Percolax's bud bursts add haze): true = apply it to that player. Same window and budget as
	 * damage. Mobs that are not tracked adds always pass.
	 */
	public static boolean allowPressure(Entity add, Player target, float amount) {
		Pack pk = ADDS.get(add.getUUID());
		if (pk == null || !(target.level() instanceof ServerLevel sl)) return true;
		return pk.admit(add, target.getUUID(), sl.getGameTime(), amount, true);
	}

	// ------------------------------------------------------------------------------------------------ the pack

	/** One boss's adds: waves, the brain, the fairness window and despawn. Make it in the boss constructor when {@link #enabled}. */
	public static final class Pack {
		public final Mob boss;
		public final Spec spec;
		private final Theme theme;
		private final Sources sources;
		private final Factory factory;
		private Marker marker;
		private final List<Mob> adds = new ArrayList<>();
		private final Map<UUID, UUID> token = new HashMap<>();
		private final Map<UUID, ArrayDeque<float[]>> window = new HashMap<>();
		private final List<Entity> projectiles = new ArrayList<>();
		private List<Vec3> pendingAt = List.of();
		private int pendingTicks = -1, pendingSize, pendingElites, players = 1;
		private boolean registered;
		/** Test and debug counters. */
		public int wavesCalled, wavesSpawned, lastWaveSize, lastWaveElites, lastTelegraphTicks, brainRuns, admitted, refused, despawned, engaged, holding;

		public Pack(Mob boss, Spec spec, Theme theme, Sources sources, Factory factory) {
			init();
			this.boss = boss;
			this.spec = spec;
			this.theme = theme;
			this.sources = sources;
			this.factory = factory;
		}

		public Pack marker(Marker m) {
			this.marker = m;
			return this;
		}

		public String tag() {
			return TAG_PREFIX + boss.getUUID();
		}

		private void register() {
			// the boss UUID is final once it is in a level (a loaded boss gets its saved UUID after the constructor)
			if (registered) return;
			registered = true;
			PACKS.put(boss.getUUID(), this);
			synchronized (ORPHANS) {
				for (Iterator<Orphan> it = ORPHANS.iterator(); it.hasNext(); ) {
					Orphan o = it.next();
					if (o.boss.equals(boss.getUUID())) { readopt(o.mob); it.remove(); }
				}
			}
		}

		public List<Mob> adds() {
			prune();
			return adds;
		}

		public int live() {
			prune();
			return adds.size();
		}

		public int elites() {
			prune();
			int n = 0;
			for (Mob m : adds) if (isElite(m)) n++;
			return n;
		}

		public int fodder() {
			return live() - elites();
		}

		public boolean wavePending() {
			return pendingTicks >= 0;
		}

		public int pendingSize() {
			return pendingTicks >= 0 ? pendingSize : 0;
		}

		public int liveProjectiles() {
			projectiles.removeIf(e -> e.isRemoved());
			return projectiles.size();
		}

		public int capFor(int fighters) {
			return cap(fighters, spec.parts);
		}

		/**
		 * Starts a telegraphed wave for this party. False when one is already coming or there is no room for 3. The adds appear at
		 * the sources {@link #TELEGRAPH_TICKS} ticks later, on the boss's own tick ({@link #tick}).
		 */
		public boolean callWave(ServerLevel level, List<ServerPlayer> fighters) {
			register();
			prune();
			if (pendingTicks >= 0) return false;
			players = Math.max(1, fighters.size());
			int room = capFor(players) - adds.size();
			int size = waveSize(players, room);
			if (size == 0) return false;
			List<Vec3> at = sources.pick(level, size, boss.getRandom());
			if (at == null || at.isEmpty()) return false;
			int eliteRoom = Math.max(0, eliteCap(players) - elites());
			pendingElites = Math.min(eliteRoom, Math.max(1, size / 3));
			pendingSize = size;
			pendingAt = new ArrayList<>(at);
			pendingTicks = TELEGRAPH_TICKS;
			lastTelegraphTicks = TELEGRAPH_TICKS;
			wavesCalled++;
			for (Vec3 v : pendingAt) {
				level.playSound(null, v.x, v.y, v.z, theme.sound(), SoundSource.HOSTILE, 1.6F, theme.pitch());
				if (marker != null) marker.mark(level, v, TELEGRAPH_TICKS);
			}
			Component line = Component.literal(theme.call()).withStyle(ChatFormatting.GOLD);
			for (ServerPlayer p : fighters) p.sendOverlayMessage(line);
			return true;
		}

		/** Call from the boss's server tick (alive, fight running). Cheap: a telegraph step every 5 ticks, the brain every 10. */
		public void tick(ServerLevel level, List<ServerPlayer> fighters) {
			register();
			if (pendingTicks >= 0) {
				if (pendingTicks % 5 == 0) {
					for (Vec3 v : pendingAt) level.sendParticles(theme.particle(), v.x, v.y + 0.6, v.z, 10, 0.35, 0.6, 0.35, 0.02);
				}
				if (pendingTicks == 0) spawnWave(level, fighters);
				pendingTicks--;
			}
			if ((boss.tickCount + (boss.getId() & 7)) % BRAIN_EVERY == 0) think(level, fighters);
		}

		private void spawnWave(ServerLevel level, List<ServerPlayer> fighters) {
			prune();
			int n = Math.max(1, fighters.isEmpty() ? players : fighters.size());
			int count = Math.min(pendingSize, capFor(n) - adds.size());
			int made = 0, madeElite = 0;
			for (int i = 0; i < count && !pendingAt.isEmpty(); i++) {
				boolean elite = madeElite < pendingElites && elites() < eliteCap(n);
				Vec3 at = pendingAt.get(i % pendingAt.size());
				Mob m = factory.create(level, at, elite);
				if (m == null) continue;
				adopt(level, m, elite, true);
				made++;
				if (elite) madeElite++;
				level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 6, 0.3, 0.3, 0.3, 0.02);
			}
			lastWaveSize = made;
			lastWaveElites = madeElite;
			if (made > 0) wavesSpawned++;
			pendingAt = List.of();
			think(level, fighters);
		}

		/**
		 * Takes a mob into the pack (wave adds, or a boss's own extra add that should count, despawn and be fair). {@code fodder stats}
		 * applies the fodder numbers when not elite. Adds it to the level if it is not there yet.
		 */
		public void adopt(ServerLevel level, Mob m, boolean elite, boolean applyFodderStats) {
			register();
			m.addTag(tag());
			m.addTag(elite ? TAG_ELITE : TAG_FODDER);
			if (!elite && applyFodderStats) makeFodder(m, spec);
			m.setPersistenceRequired();
			ADDS.put(m.getUUID(), this);
			if (!adds.contains(m)) adds.add(m);
			if (level.getEntity(m.getUUID()) != m) level.addFreshEntity(m);
		}

		/** Fodder numbers on a fresh add: HP 25-40% (1-2 hits), no armor, less damage, smaller, no drops or XP. */
		public static void makeFodder(Mob m, Spec spec) {
			AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				hp.removeModifiers();
				hp.setBaseValue(fodderHp(spec, hp.getBaseValue()));
			}
			AttributeInstance armor = m.getAttribute(Attributes.ARMOR);
			if (armor != null) { armor.removeModifiers(); armor.setBaseValue(0); }
			AttributeInstance tough = m.getAttribute(Attributes.ARMOR_TOUGHNESS);
			if (tough != null) { tough.removeModifiers(); tough.setBaseValue(0); }
			AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
			if (dmg != null) dmg.setBaseValue(Math.max(0.5, dmg.getBaseValue() * spec.fodderDmg));
			AttributeInstance scale = m.getAttribute(Attributes.SCALE);
			if (scale != null) scale.setBaseValue(FODDER_SCALE);
			for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				m.setItemSlot(s, ItemStack.EMPTY);
			}
			m.setDropChance(EquipmentSlot.MAINHAND, 0F);
			m.setDropChance(EquipmentSlot.OFFHAND, 0F);
			m.skipDropExperience();
			m.setHealth(m.getMaxHealth());
		}

		private void readopt(Mob m) {
			if (m.isRemoved() || !m.isAlive()) return;
			ADDS.put(m.getUUID(), this);
			if (!adds.contains(m)) adds.add(m);
		}

		private void prune() {
			for (Iterator<Mob> it = adds.iterator(); it.hasNext(); ) {
				Mob m = it.next();
				if (m.isAlive() && !m.isRemoved()) continue;
				it.remove();
				ADDS.remove(m.getUUID(), this);
				token.remove(m.getUUID());
			}
		}

		/** The group brain: attack tokens per player inside today's budget; everyone else holds a ring. Runs every 10 ticks. */
		public void think(ServerLevel level, List<ServerPlayer> fighters) {
			brainRuns++;
			prune();
			engaged = 0;
			holding = 0;
			if (adds.isEmpty()) return;
			List<ServerPlayer> ps = new ArrayList<>();
			for (ServerPlayer p : fighters) if (p.isAlive() && !p.isSpectator()) ps.add(p);
			if (ps.isEmpty()) {
				for (Mob m : adds) { m.setTarget(null); token.remove(m.getUUID()); }
				return;
			}
			players = ps.size();
			Difficulty d = level.getDifficulty();
			float budget = budgetPerWindow(spec, d);
			float[] used = new float[ps.size()];
			List<Mob> order = new ArrayList<>(adds);
			// elites first (one per player before any fodder), then fodder, then the rest of the elites
			List<Mob> elites = new ArrayList<>(), fodder = new ArrayList<>();
			for (Mob m : order) (isElite(m) ? elites : fodder).add(m);
			List<Mob> firstElites = new ArrayList<>(), lateElites = new ArrayList<>();
			boolean[] hasElite = new boolean[ps.size()];
			for (Mob m : elites) {
				int i = nearest(ps, m);
				if (!hasElite[i]) { hasElite[i] = true; firstElites.add(m); } else lateElites.add(m);
			}
			List<Mob> queue = new ArrayList<>(firstElites);
			fodder.sort((a, b) -> Double.compare(a.distanceToSqr(ps.get(nearest(ps, a))), b.distanceToSqr(ps.get(nearest(ps, b)))));
			queue.addAll(fodder);
			queue.addAll(lateElites);
			for (Mob m : queue) {
				float w = pressure(m, d);
				int best = -1;
				double bestScore = Double.MAX_VALUE;
				UUID had = token.get(m.getUUID());
				for (int i = 0; i < ps.size(); i++) {
					if (used[i] + w > budget + 1e-3F) continue;
					double score = (used[i] + w) / Math.max(1e-3, budget) + Math.sqrt(m.distanceToSqr(ps.get(i))) / 32.0;
					if (had != null && had.equals(ps.get(i).getUUID())) score -= 0.25; // keep a token where it is (no flicker)
					if (score < bestScore) { bestScore = score; best = i; }
				}
				if (best >= 0) {
					ServerPlayer p = ps.get(best);
					used[best] += w;
					token.put(m.getUUID(), p.getUUID());
					if (m.getTarget() != p) m.setTarget(p);
					engaged++;
				} else {
					token.remove(m.getUUID());
					ServerPlayer p = ps.get(nearest(ps, m));
					if (m.getTarget() != null) m.setTarget(null);
					Vec3 off = m.position().subtract(p.position()).multiply(1, 0, 1);
					Vec3 dir = off.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : off.normalize();
					Vec3 hold = p.position().add(dir.scale(HOLD_RADIUS));
					if (m.position().distanceToSqr(hold) > 4) m.getNavigation().moveTo(hold.x, hold.y, hold.z, 1.0);
					holding++;
				}
			}
		}

		private static int nearest(List<ServerPlayer> ps, Entity e) {
			int best = 0;
			double bd = Double.MAX_VALUE;
			for (int i = 0; i < ps.size(); i++) {
				double dd = ps.get(i).distanceToSqr(e);
				if (dd < bd) { bd = dd; best = i; }
			}
			return best;
		}

		/** One add's share of the window budget: its hit as the player takes it, over today's interval. */
		public float pressure(Mob m, Difficulty d) {
			float hit;
			AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
			if (dmg != null && dmg.getValue() > 0) hit = (float) dmg.getValue();
			else hit = isElite(m) ? spec.todayHit : spec.todayHit * spec.fodderDmg;
			return difficultyHit(d, hit) * WINDOW / (float) Math.max(1, spec.todayInterval);
		}

		/** Does this add hold an attack token on that player right now? */
		public boolean hasToken(Entity add, UUID player) {
			return player.equals(token.get(add.getUUID()));
		}

		/**
		 * The hard fairness cap: a hit (or pressure) from one of this pack's adds lands only while the player's add total in the last
		 * {@link #WINDOW} ticks stays inside today's budget. {@code amount} is what the player takes (difficulty already applied).
		 */
		public boolean admit(Entity add, UUID player, long now, float amount, boolean pressure) {
			ArrayDeque<float[]> q = window.computeIfAbsent(player, k -> new ArrayDeque<>());
			while (!q.isEmpty() && q.peekFirst()[0] <= now - WINDOW) q.pollFirst();
			float sum = 0;
			for (float[] h : q) sum += h[1];
			Difficulty d = boss.level().getDifficulty();
			float budget = pressure ? spec.todayCapSolo * spec.todayHit * WINDOW / (float) Math.max(1, spec.todayInterval) : budgetPerWindow(spec, d);
			if (sum + amount > budget + 1e-3F) { refused++; return false; }
			q.addLast(new float[] {now, amount});
			admitted++;
			return true;
		}

		/** Add damage a player took from this pack in the last window (tests). */
		public float windowTotal(UUID player, long now) {
			ArrayDeque<float[]> q = window.get(player);
			if (q == null) return 0;
			float sum = 0;
			for (float[] h : q) if (h[0] > now - WINDOW) sum += h[1];
			return sum;
		}

		private void projectileLoaded(Projectile pr) {
			projectiles.removeIf(e -> e.isRemoved());
			if (!projectiles.contains(pr)) projectiles.add(pr);
			PEAK_PROJECTILES.merge(spec.bossId, projectiles.size(), Math::max);
		}

		private void projectileUnloaded(Projectile pr) {
			projectiles.remove(pr);
		}

		/** Boss died, reset or was discarded: every add goes with it (puff of smoke), and the pack forgets its state. */
		public int clear(ServerLevel level) {
			int n = 0;
			for (Mob m : new ArrayList<>(adds)) {
				ADDS.remove(m.getUUID(), this);
				if (!m.isRemoved()) {
					level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
					m.discard();
					n++;
				}
			}
			adds.clear();
			token.clear();
			window.clear();
			pendingTicks = -1;
			pendingAt = List.of();
			despawned += n;
			PACKS.remove(boss.getUUID(), this);
			registered = false;
			return n;
		}

		/** From the boss's {@code remove(reason)}: clears on kill or discard, keeps the adds when the chunk only unloads. */
		public void onBossRemoved(ServerLevel level, Entity.RemovalReason reason) {
			if (reason.shouldDestroy()) clear(level);
			else PACKS.remove(boss.getUUID(), this);
			if (!reason.shouldDestroy()) registered = false;
		}
	}
}
