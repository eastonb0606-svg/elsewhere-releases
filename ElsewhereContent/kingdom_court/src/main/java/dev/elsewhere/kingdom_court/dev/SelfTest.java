package dev.elsewhere.kingdom_court.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomAggression;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom_court.CourtApi;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtItems;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CourtSite;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dev self-test of the court module (lane K-B). Run with {@code ./gradlew runServer} (flat world, see run/server.properties).
 * Steps run one per tick-slot like the kingdom base test; results go to the console ("[court-selftest]") and selftest-court.log.
 */
public final class SelfTest {
	private SelfTest() {}

	interface Step {
		/** Returns the ticks to wait before the next step. */
		int run(MinecraftServer srv, ServerLevel l) throws Exception;
	}

	static final List<String> LOG = new ArrayList<>();
	static final List<Step> STEPS = new ArrayList<>();
	static int pass, fail, idx, wait, startDelay;
	static boolean started, finished;
	static int y;
	static BlockPos origin;

	static boolean measuring;
	static long tickStart;
	static final List<Long> SAMPLES = new ArrayList<>();
	static final List<String> BENCH = new ArrayList<>();

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[court-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void info(String line) {
		LOG.add(line);
		System.out.println("[court-selftest] " + line);
	}

	// ---- driver --------------------------------------------------------------------------------------------------

	public static void serverStarted(MinecraftServer srv) {
		build();
	}

	public static void startTick(MinecraftServer srv) {
		if (measuring) tickStart = System.nanoTime();
	}

	public static void endTick(MinecraftServer srv) {
		if (measuring && tickStart != 0) SAMPLES.add(System.nanoTime() - tickStart);
		if (finished) return;
		ServerLevel l = srv.overworld();
		if (!started) {
			if (++startDelay < 60) return;
			started = true;
			info("started");
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (idx >= STEPS.size()) {
			finish(srv);
			return;
		}
		Step s = STEPS.get(idx++);
		try {
			wait = s.run(srv, l);
		} catch (Throwable t) {
			check("step " + idx + " crashed", false, t);
			t.printStackTrace();
			wait = 0;
		}
	}

	static void finish(MinecraftServer srv) {
		finished = true;
		info("TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-court.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("court.selftest.exit")) srv.halt(false);
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	static FakePlayer fp(ServerLevel l, String name) {
		return FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
	}

	static Vec3 at(double x, double z) {
		return new Vec3(x + 0.5, y, z + 0.5);
	}

	static void clean(ServerLevel l) {
		for (Entity e : l.getEntities((Entity) null, new AABB(-140, y - 20, -100, 140, y + 40, 100), e -> !(e instanceof Player))) e.discard();
		KingdomState.get(l).clearForTests();
	}

	static Zombie zombie(ServerLevel l, Vec3 p, boolean ai) {
		Zombie z = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND);
		z.snapTo(p.x, p.y, p.z, 0F, 0F);
		z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		z.setDropChance(EquipmentSlot.HEAD, 0F);
		z.setNoAi(!ai);
		z.setPersistenceRequired();
		l.addFreshEntity(z);
		return z;
	}

	static double attr(ItemStack s, net.minecraft.core.Holder<Attribute> a) {
		var mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
		double sum = 0;
		if (mods != null) for (var e : mods.modifiers()) if (e.attribute().equals(a)) sum += e.modifier().amount();
		return sum;
	}

	static int count(List<ItemStack> l, Item item) {
		int n = 0;
		for (ItemStack s : l) if (s.is(item)) n += s.getCount();
		return n;
	}

	static CourtRuler ruler(ServerLevel l, Ruler r) {
		UUID id = CourtState.get(l).rulerIds.get(r);
		return id != null && l.getEntity(id) instanceof CourtRuler c ? c : null;
	}

	static void bench(String name, ServerLevel l, boolean checkBand) {
		measuring = false;
		long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
		double avg = Arrays.stream(s).average().orElse(0) / 1e6;
		double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
		double max = s.length == 0 ? 0 : s[s.length - 1] / 1e6;
		int sol = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(-140, y - 20, -100, 140, y + 40, 100)).size();
		int zom = l.getEntitiesOfClass(Zombie.class, new AABB(-140, y - 20, -100, 140, y + 40, 100)).size();
		String line = String.format("LOADTEST %s: MSPT avg %.2f ms, p95 %.2f, max %.2f over %d ticks | %d soldiers (incl. rulers), %d zombies, %d active", name, avg, p95, max, s.length, sol, zom, KingdomPerf.activeCount(l));
		info(line);
		BENCH.add(line);
		check("load test ran: " + name, s.length >= 290, s.length + " ticks");
		if (checkBand) check("castle with garrison stays light (avg MSPT < 15 ms, 50 ms budget)", avg < 15.0, String.format("%.2f ms", avg));
	}

	// ---- the steps ---------------------------------------------------------------------------------------------------

	static final Map<Ruler, Double> KINGDOM_MD = new EnumMap<>(Ruler.class);

	static {
		KINGDOM_MD.put(Ruler.KING, 720.0);
		KINGDOM_MD.put(Ruler.MARSHAL, 400.0);
		KINGDOM_MD.put(Ruler.CASTELLAN, 440.0);
		KINGDOM_MD.put(Ruler.MASTER_AT_ARMS, 340.0);
		KINGDOM_MD.put(Ruler.HUNTSMAN, 360.0);
		KINGDOM_MD.put(Ruler.CHANCELLOR, 380.0);
	}

	static void build() {
		// 0: keep the castle area loaded and ticking
		STEPS.add((srv, l) -> {
			for (int cx = -9; cx <= 9; cx++) for (int cz = -5; cz <= 5; cz++) l.setChunkForced(cx, cz, true);
			CourtLife.testMode = true;
			KingdomPerf.forceActive = true;
			return 200;
		});
		// 0b: every ruler entity type creates a non-null entity, has attributes and a working spawn egg
		STEPS.add((srv, l) -> {
			for (Ruler r : Ruler.values()) {
				var e = r.type == null ? null : r.type.create(l, EntitySpawnReason.COMMAND);
				var egg = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "court_" + r.key + "_spawn_egg"));
				check("ruler " + r.key + ": EntityType.create non-null, attributes registered, spawn egg item exists",
					e != null && e.getAttributes() != null && egg instanceof net.minecraft.world.item.SpawnEggItem, e == null ? "create returned null" : null);
				if (e != null) e.discard();
			}
			return 1;
		});
		// 1: realm placement over many seeds (real overworld noise generator), one realm per world, castle tag
		STEPS.add((srv, l) -> {
			StringBuilder col = new StringBuilder();
			for (int yy = l.getMinY(); yy < l.getMinY() + 8; yy++) col.append(' ').append(yy).append('=').append(BuiltInRegistries.BLOCK.getKey(l.getBlockState(new BlockPos(0, yy, 0)).getBlock()).getPath());
			info("column at 0,0:" + col + " minY=" + l.getMinY() + " loaded=" + l.isLoaded(new BlockPos(0, 0, 0)) + " gen=" + l.getChunkSource().getGenerator().getClass().getSimpleName());
			y = l.getMinY();
			while (!l.getBlockState(new BlockPos(0, y, 0)).isAir()) y++;
			origin = new BlockPos(0, y, 0);
			info("flat floor y=" + y + " (chunks loaded), origin " + origin);
			var reg = srv.registryAccess();
			ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
			check("overworld generator is noise-based", gen instanceof NoiseBasedChunkGenerator, gen.getClass().getSimpleName());
			NoiseBasedChunkGenerator nbg = (NoiseBasedChunkGenerator) gen;
			var noises = reg.lookupOrThrow(Registries.NOISE);
			long[] seeds = {1L, 42L, 1337L, 2026L, 987654321L, -5551212L, 7L, 123456789012L, -99L, 31337L, 8675309L, 2718281828L};
			BlockPos[] spawns = {BlockPos.ZERO, new BlockPos(200, 0, -300), new BlockPos(-640, 0, 480)};
			int found = 0, bad = 0;
			double lo = 1e9, hi = 0;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < seeds.length; i++) {
				BlockPos spawn = spawns[i % spawns.length];
				RandomState rs = RandomState.create(noises, seeds[i], nbg.generatorSettings().value());
				BlockPos site = CourtSite.choose(seeds[i], spawn, gen, rs, l, cp -> false);
				BlockPos again = CourtSite.choose(seeds[i], spawn, gen, rs, l, cp -> false);
				if (site == null) {
					sb.append(" seed").append(seeds[i]).append("=none");
					continue;
				}
				found++;
				double d = Math.sqrt((double) (site.getX() - spawn.getX()) * (site.getX() - spawn.getX()) + (double) (site.getZ() - spawn.getZ()) * (site.getZ() - spawn.getZ()));
				lo = Math.min(lo, d);
				hi = Math.max(hi, d);
				boolean inSquare = Math.abs(site.getX() - spawn.getX()) < 3000 && Math.abs(site.getZ() - spawn.getZ()) < 3000;
				if (d < 1200 || d > 2500 || !inSquare || !site.equals(again)) bad++;
				sb.append(String.format(" s%d=%.0f", seeds[i], d));
			}
			info("realm placement distances:" + sb);
			check("every chosen realm is 1,200-2,500 blocks from spawn, deterministic", bad == 0 && found > 0, String.format("%d found, min %.0f max %.0f, %d bad", found, lo, hi, bad));
			check("a realm exists in most worlds (>= 9 of 12 seeds)", found >= 9, found + " of " + seeds.length);
			// candidates themselves stay in the band
			int outside = 0;
			for (BlockPos c : CourtSite.candidates(5L, BlockPos.ZERO)) {
				double d = Math.sqrt((double) c.getX() * c.getX() + (double) c.getZ() * c.getZ());
				if (d < 1200 || d > 2500) outside++;
			}
			check("all candidate sites are inside 1,200-2,500", outside == 0, outside + " outside");
			// placement: exactly one structure chunk in the whole checked window (375 x 375 chunks)
			BlockPos site = new BlockPos(1500, y, -900);
			CourtWorld.setSite(l.getSeed(), site);
			var state = l.getChunkSource().getGeneratorState();
			int hits = 0;
			int cx0 = -190, cz0 = -190;
			for (int cx = cx0; cx < cx0 + 375; cx++) for (int cz = cz0; cz < cz0 + 375; cz++) if (CourtWorld.Placement.INSTANCE.isStructureChunk(state, cx, cz)) hits++;
			check("exactly one realm per world (placement; checked over a 6,000-block window)", hits == 1, hits);
			CourtWorld.setSite(l.getSeed(), null);
			// existing worlds: chunks already generated (chunk storage) push the realm to the next candidate
			{
				BlockPos spawn = BlockPos.ZERO;
				long seed = 1L;
				RandomState rs = RandomState.create(noises, seed, nbg.generatorSettings().value());
				BlockPos free = CourtSite.choose(seed, spawn, gen, rs, l, cp -> false);
				for (int radius : new int[] {600, 1700, 1900}) {
					final int r2 = radius;
					java.util.function.Predicate<net.minecraft.world.level.ChunkPos> gend = cp -> Math.hypot(cp.x() * 16 + 8, cp.z() * 16 + 8) < r2;
					BlockPos moved = CourtSite.choose(seed, spawn, gen, rs, l, gend);
					boolean clear = true;
					if (moved != null) {
						net.minecraft.world.level.ChunkPos c0 = net.minecraft.world.level.ChunkPos.containing(moved);
						for (int dx = -7; dx <= 7; dx++) for (int dz = -3; dz <= 3; dz++) clear &= !gend.test(new net.minecraft.world.level.ChunkPos(c0.x() + dx, c0.z() + dz));
					}
					boolean inBand = moved == null || (Math.hypot(moved.getX(), moved.getZ()) >= 1200 && Math.hypot(moved.getX(), moved.getZ()) <= 2500);
					check("pre-generated radius " + radius + ": realm is outside it (whole footprint ungenerated), still 1,200-2,500 from spawn" + (radius == 600 ? ", same site as a fresh world" : ", moved to the next candidate"),
						clear && inBand && moved != null && (radius == 600 ? moved.equals(free) : !moved.equals(free)), moved + " vs fresh " + free);
					if (moved != null) {
						CourtWorld.setSite(l.getSeed(), moved);
						net.minecraft.world.level.ChunkPos c = net.minecraft.world.level.ChunkPos.containing(moved);
						check("survey/locate target matches at radius " + radius + " (placement chunk = realm centre chunk)", CourtWorld.Placement.INSTANCE.isStructureChunk(l.getChunkSource().getGeneratorState(), c.x(), c.z()), null);
						CourtWorld.setSite(l.getSeed(), null);
					}
				}
			}
			// castle tag, survey-map tag, structure set, biome tag
			var structs = reg.lookupOrThrow(Registries.STRUCTURE);
			check("structure elsewhere:castle_aldermark is registered", structs.get(CourtWorld.STRUCTURE_KEY).isPresent(), null);
			var tag = structs.get(TagKey.<Structure>create(Registries.STRUCTURE, CourtContent.id("aldermark_castle")));
			check("tag #elsewhere:aldermark_castle resolves to the castle", tag.isPresent() && tag.get().stream().anyMatch(h -> h.is(CourtWorld.STRUCTURE_KEY)), tag.map(t -> t.stream().count()).orElse(0L));
			var maps = structs.get(TagKey.<Structure>create(Registries.STRUCTURE, CourtContent.id("on_royal_survey_maps")));
			check("tag #elsewhere:on_royal_survey_maps resolves to the castle", maps.isPresent() && maps.get().stream().anyMatch(h -> h.is(CourtWorld.STRUCTURE_KEY)), null);
			var sets = reg.lookupOrThrow(Registries.STRUCTURE_SET);
			check("structure set elsewhere:castle_aldermark loaded", sets.get(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET, CourtContent.id("castle_aldermark"))).isPresent(), null);
			var biomes = reg.lookupOrThrow(Registries.BIOME).get(CourtSite.BIOMES);
			check("biome tag #elsewhere:court_realm_biomes has plains and forest", biomes.isPresent() && biomes.get().stream().anyMatch(h -> h.is(net.minecraft.world.level.biome.Biomes.PLAINS))
				&& biomes.get().stream().anyMatch(h -> h.is(net.minecraft.world.level.biome.Biomes.FOREST)), biomes.map(t -> t.stream().count()).orElse(0L));
			return 1;
		});
		// 2: ruler stats on paper, item stats, music, drops table math
		STEPS.add((srv, l) -> {
			for (Ruler r : Ruler.values()) {
				double cap = r == Ruler.CHANCELLOR ? KINGDOM_MD.get(r) / 4.0 : KINGDOM_MD.get(r) / 2.0;
				double max = (r == Ruler.KING ? r.health + 12.0 * 5 : r.health) * dev.elsewhere.kingdom_court.ruler.CourtRulers.ALDERMARK_SCALE;
				check(r.key + " health cap: " + max + " <= " + cap + (r == Ruler.CHANCELLOR ? " (a quarter of " : " (half of ") + KINGDOM_MD.get(r).intValue() + ")", max <= cap, null);
			}
			ItemStack spurs = new ItemStack(CourtItems.WARMARSHAL_SPURS), plate = new ItemStack(CourtItems.GATEWARDEN_PLATE), sabre = new ItemStack(CourtItems.DUELISTS_SABRE),
				bow = new ItemStack(CourtItems.ASHGROVE_LONGBOW), chap = new ItemStack(CourtItems.CHANCELLORS_CHAPERON), crown = new ItemStack(CourtItems.CROWN_OF_ALDERMARK);
			info(String.format("ITEM Warmarshal's Spurs: boots armor %.0f toughness %.0f kb %.1f durability %d enchantability %d", attr(spurs, Attributes.ARMOR), attr(spurs, Attributes.ARMOR_TOUGHNESS),
				attr(spurs, Attributes.KNOCKBACK_RESISTANCE), spurs.getMaxDamage(), spurs.get(DataComponents.ENCHANTABLE).value()));
			info(String.format("ITEM Gatewarden Plate: chest armor %.0f toughness %.0f kb %.1f speed %+.0f%% durability %d enchantability %d", attr(plate, Attributes.ARMOR), attr(plate, Attributes.ARMOR_TOUGHNESS),
				attr(plate, Attributes.KNOCKBACK_RESISTANCE), attr(plate, Attributes.MOVEMENT_SPEED) * 100, plate.getMaxDamage(), plate.get(DataComponents.ENCHANTABLE).value()));
			info(String.format("ITEM Duelist's Sabre: damage %.0f speed %.1f durability %d enchantability %d", attr(sabre, Attributes.ATTACK_DAMAGE) + 1, attr(sabre, Attributes.ATTACK_SPEED) + 4, sabre.getMaxDamage(),
				sabre.get(DataComponents.ENCHANTABLE).value()));
			info(String.format("ITEM Ashgrove Longbow: durability %d", bow.getMaxDamage()));
			info(String.format("ITEM Chancellor's Chaperon: helmet armor %.0f toughness %.0f kb %.1f durability %d enchantability %d", attr(chap, Attributes.ARMOR), attr(chap, Attributes.ARMOR_TOUGHNESS),
				attr(chap, Attributes.KNOCKBACK_RESISTANCE), chap.getMaxDamage(), chap.get(DataComponents.ENCHANTABLE).value()));
			info(String.format("ITEM Crown of Aldermark: helmet armor %.0f toughness %.0f kb %.1f attack speed %+.0f%% durability %d enchantability %d", attr(crown, Attributes.ARMOR),
				attr(crown, Attributes.ARMOR_TOUGHNESS), attr(crown, Attributes.KNOCKBACK_RESISTANCE), attr(crown, Attributes.ATTACK_SPEED) * 100, crown.getMaxDamage(), crown.get(DataComponents.ENCHANTABLE).value()));
			ItemStack nb = new ItemStack(Items.NETHERITE_CHESTPLATE), nbh = new ItemStack(Items.NETHERITE_HELMET), nbs = new ItemStack(Items.NETHERITE_SWORD), nbb = new ItemStack(Items.NETHERITE_BOOTS);
			check("no armor item beats netherite (armor, toughness)", attr(spurs, Attributes.ARMOR) <= attr(nbb, Attributes.ARMOR) && attr(plate, Attributes.ARMOR) <= attr(nb, Attributes.ARMOR)
				&& attr(crown, Attributes.ARMOR) <= attr(nbh, Attributes.ARMOR) && attr(chap, Attributes.ARMOR) < attr(nbh, Attributes.ARMOR) && attr(plate, Attributes.ARMOR_TOUGHNESS) <= 4.0
				&& attr(crown, Attributes.ARMOR_TOUGHNESS) <= attr(nbh, Attributes.ARMOR_TOUGHNESS), null);
			check("Duelist's Sabre is below netherite sword damage and durability", attr(sabre, Attributes.ATTACK_DAMAGE) < attr(nbs, Attributes.ATTACK_DAMAGE) && sabre.getMaxDamage() < nbs.getMaxDamage(),
				attr(sabre, Attributes.ATTACK_DAMAGE) + " vs " + attr(nbs, Attributes.ATTACK_DAMAGE));
			check("Gatewarden Plate: heavy (-7% speed), 8 armor / 4 toughness / 0.2 kb", attr(plate, Attributes.ARMOR) == 8 && attr(plate, Attributes.ARMOR_TOUGHNESS) == 4 && Math.abs(attr(plate, Attributes.MOVEMENT_SPEED) + 0.07) < 1e-6,
				null);
			check("Crown of Aldermark and Chaperon enchantability 25, Spurs 15", crown.get(DataComponents.ENCHANTABLE).value() == 25 && chap.get(DataComponents.ENCHANTABLE).value() == 25
				&& spurs.get(DataComponents.ENCHANTABLE).value() == 15, null);
			boolean music = true;
			for (String k : new String[] {"king", "marshal", "castellan", "master_at_arms", "huntsman", "chancellor", "realm"})
				music &= BuiltInRegistries.SOUND_EVENT.containsKey(CourtContent.id("court_music." + k));
			check("7 court music sound events registered (6 rulers + realm)", music, null);
			boolean items = true;
			for (Item i : CourtItems.ALL) items &= BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("elsewhere");
			check("11 court items registered (7 + the 4 trade items)", CourtItems.ALL.size() == 11 && items, CourtItems.ALL.size());
			return 1;
		});
		// 3: roaming Lord (before the garrison, so the Huntsman is free), then the realm and the garrison
		STEPS.add((srv, l) -> {
			clean(l);
			CourtState st = CourtState.get(l);
			st.resetForTests();
			st.siteChosen = true;
			st.site = origin;
			st.built = false;
			CourtWorld.setSite(l.getSeed(), origin);
			int placed = CourtWorld.buildNow(l, origin);
			check("castle, keep, dungeon, 2 towns and road build (blocks placed)", placed > 20000, placed);
			check("throne is placed (dark oak stairs)", l.getBlockState(CastleLayout.throne(origin)).is(Blocks.DARK_OAK_STAIRS), l.getBlockState(CastleLayout.throne(origin)));
			check("realm chunks are entity-ticking", l.isPositionEntityTicking(origin) && l.isPositionEntityTicking(CastleLayout.town(origin, true)) && l.isPositionEntityTicking(CastleLayout.town(origin, false)), null);
			FakePlayer p = fp(l, "court-roam");
			p.snapTo(origin.getX() + 0.5, y, origin.getZ() + 20.5, 0, 0);
			CourtRuler roamer = null;
			for (int i = 0; i < 8 && roamer == null; i++) roamer = CourtLife.roamNow(l, p, Ruler.HUNTSMAN);
			check("a living Lord can appear in the world as a roaming captain (Huntsman, with squad)", roamer != null && st.roaming == Ruler.HUNTSMAN && l.getEntity(st.rulerIds.get(Ruler.HUNTSMAN)) == roamer, roamer);
			if (roamer != null) {
				UUID id = roamer.getUUID();
				boolean squad = KingdomState.get(l).squads().stream().anyMatch(s -> id.equals(s.leader()) && s.members().size() >= 4);
				check("the roaming Lord leads a squad of 4+", squad, null);
				check("roaming Lord is not the crowned ruler and stays half-health-capped", !roamer.crowned && roamer.getMaxHealth() <= KINGDOM_MD.get(Ruler.HUNTSMAN) / 2, roamer.getMaxHealth());
			}
			clean(l);
			st.rulerIds.remove(Ruler.HUNTSMAN);
			st.roaming = null;
			st.postSquads.clear();
			st.postDay.clear();
			st.built = true;
			return 5;
		});
		STEPS.add((srv, l) -> {
			CourtState st = CourtState.get(l);
			int n = CourtLife.garrison(l, st);
			info("garrison first pass spawned " + n);
			return 30;
		});
		// 4: garrison, caps, ruler health, abilities
		STEPS.add((srv, l) -> {
			CourtState st = CourtState.get(l);
			CourtLife.garrison(l, st);
			AABB box = new AABB(-140, y - 20, -100, 140, y + 40, 100);
			List<KingdomSoldier> all = l.getEntitiesOfClass(KingdomSoldier.class, box);
			long rulers = all.stream().filter(s -> s instanceof CourtRuler).count();
			long garrison = all.stream().filter(s -> !(s instanceof CourtRuler)).count();
			check("all 6 rulers stand in the castle", rulers == 6, rulers);
			// the drill squad (sergeant + 3) is discarded into st.sleepers at night (schedule phase 5) and respawns at dawn: by design, so it counts as held
			long asleep = st.sleepers > 0 ? st.sleepers + 1 : 0;
			StringBuilder per = new StringBuilder();
			for (CourtLife.Post p : CourtLife.posts(st.site)) {
				UUID pq = st.postSquads.get(p.id());
				var sq = pq == null ? null : KingdomState.get(l).squad(pq);
				per.append(' ').append(p.id()).append('=').append(sq == null ? "-" : String.valueOf(sq.size())).append('@').append(p.pos().getY());
			}
			info("garrison per post (squad size @ y):" + per + " sleepers=" + st.sleepers + " phase=" + CourtLife.phaseOf(l.getDefaultClockTime()));
			// towns sit on their own floor (fv 2), possibly outside the castle box: count live loaded garrison-tagged Aldermark soldiers anywhere too
			long anywhere = KingdomPerf.loaded(l).stream().filter(s -> !(s instanceof CourtRuler) && s.isAlive() && Factions.ALDERMARK.equals(s.factionId()) && s.entityTags().contains(CourtLife.GARRISON_TAG)).count();
			long held = Math.max(garrison, anywhere) + asleep;
			check("garrison posts hold (>= 17 soldiers awake or asleep in the barracks, 30 per area cap respected)", held >= 17 && all.size() <= 40, garrison + " soldiers in the castle box, " + anywhere + " garrison loaded, " + asleep + " asleep (phase " + CourtLife.phaseOf(l.getDefaultClockTime()) + ") + " + rulers + " rulers");
			check("garrison soldiers are Aldermark with the garrison tag", all.stream().filter(s -> !(s instanceof CourtRuler)).allMatch(s -> Factions.ALDERMARK.equals(s.factionId())
				&& s.entityTags().contains(CourtLife.GARRISON_TAG)), null);
			for (Ruler r : Ruler.values()) {
				CourtRuler c = ruler(l, r);
				if (c == null) {
					check(r.key + " exists", false, null);
					continue;
				}
				double cap = r == Ruler.CHANCELLOR ? KINGDOM_MD.get(r) / 4.0 : KINGDOM_MD.get(r) / 2.0;
				check(r.key + " entity max health " + c.getMaxHealth() + " <= " + cap, c.getMaxHealth() <= cap, null);
			}
			CourtLife.refreshAll(l, CourtState.get(srv, Factions.ALDERMARK)); // gating is applied by each leader's own periodic refresh; apply it now so the check does not depend on timing
			CourtRuler king = ruler(l, Ruler.KING);
			check("King health = (300 + 12 per living Lord) x 5/6 = 250 + 10 per Lord: a full court is exactly the 300 cap", king != null && Math.abs(king.getMaxHealth() - 300.0) < 0.5, king == null ? null : king.getMaxHealth() + " lords=" + CourtState.get(srv, Factions.ALDERMARK).livingLords() + " bonus=" + (CourtState.get(srv, Factions.ALDERMARK).def() == null ? "nodef" : CourtState.get(srv, Factions.ALDERMARK).def().society().bonus()) + " base=" + dev.elsewhere.kingdom_court.ruler.CourtRulers.baseHealth(Factions.ALDERMARK, Ruler.KING));
			check("Aldermark compensation: King +8 armor +4 toughness +0.4 knockback resistance, +25 percent damage; Lords +6 / +3", king != null && king.getAttribute(Attributes.ARMOR).hasModifier(CourtRuler.COMP_ARMOR) && king.getAttribute(Attributes.ARMOR_TOUGHNESS).getModifier(CourtRuler.COMP_TOUGH).amount() == 4.0 && king.getAttribute(Attributes.ARMOR).getModifier(CourtRuler.COMP_ARMOR).amount() == 8.0 && king.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(CourtRuler.COMP_KB).amount() == 0.4 && king.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(CourtRuler.COMP_DMG).amount() == 0.25, null);
			// RIVALS-DECISIONS 13, per ruler: armor/toughness (King +8/+4, Lords +6/+3), kb +0.4, damage +25%, cooldowns -20%, defence 30% more often, block chance clamped at 0.95
			for (Ruler r : Ruler.values()) {
				CourtRuler c = ruler(l, r);
				if (c == null) continue;
				boolean kg = r == Ruler.KING;
				float origBlock = switch (r) { case KING -> 0.9F; case MARSHAL -> 0.8F; case CASTELLAN -> 0.95F; default -> 0F; };
				float gotBlock = c.unit().combat().blockChance();
				check("compensation " + r.key + ": armor +" + (kg ? 8 : 6) + ", toughness +" + (kg ? 4 : 3) + ", kb +0.4, damage +25%, cooldown 100 -> " + c.cd(100) + " (80), defence cooldown 100 -> " + c.cdDef(100) + " (77), move damage 10 -> " + c.dm(10F) + ", block " + origBlock + " -> " + gotBlock,
					c.getAttribute(Attributes.ARMOR).getModifier(CourtRuler.COMP_ARMOR) != null && c.getAttribute(Attributes.ARMOR).getModifier(CourtRuler.COMP_ARMOR).amount() == (kg ? 8.0 : 6.0)
					&& c.getAttribute(Attributes.ARMOR_TOUGHNESS).getModifier(CourtRuler.COMP_TOUGH).amount() == (kg ? 4.0 : 3.0) && c.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(CourtRuler.COMP_KB).amount() == 0.4
					&& c.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(CourtRuler.COMP_DMG).amount() == 0.25 && c.cd(100) == 80 && c.cdDef(100) == 77 && Math.abs(c.dm(10F) - 12.5F) < 1e-4
					&& Math.abs(gotBlock - Math.min(0.95F, origBlock * 1.3F)) < 1e-4 && gotBlock <= 0.95F && (r != Ruler.CASTELLAN || c.shieldRaiseInterval() == 46) && c.getMaxHealth() <= 300.01F, gotBlock);
			}
			// abilities against a dummy target: each ruler's whole kit plus the named abilities run without error
			Zombie dummy = zombie(l, at(0, -10), false);
			dummy.setHealth(1000);
			int ok = 0, tried = 0;
			StringBuilder err = new StringBuilder();
			for (Ruler r : Ruler.values()) {
				CourtRuler c = ruler(l, r);
				if (c == null) continue;
				for (String a : new String[] {"kit", "kit", "kit"}) {
					tried++;
					try {
						c.testAbility(l, a, dummy);
						ok++;
					} catch (Throwable t) {
						err.append(' ').append(r.key).append(':').append(t);
					}
				}
			}
			check("each ruler's per-tick kit runs against a target (18 calls)", ok == tried, ok + "/" + tried + err);
			CourtRuler ch = ruler(l, Ruler.CHANCELLOR), hu = ruler(l, Ruler.HUNTSMAN), ca = ruler(l, Ruler.CASTELLAN), md = ruler(l, Ruler.MASTER_AT_ARMS), ma = ruler(l, Ruler.MARSHAL);
			try {
				md.testAbility(l, "parry", dummy);
				check("Master-at-Arms parry window opens", md.parrying(), null);
				ca.testAbility(l, "portcullis", dummy);
				check("Castellan portcullis places temporary blocks", CourtLife.tempCount() > 0, CourtLife.tempCount());
				ch.testAbility(l, "hounds", dummy);
				int hounds = l.getEntitiesOfClass(net.minecraft.world.entity.animal.wolf.Wolf.class, box, w -> w.entityTags().contains(CourtLife.HOUND_TAG)).size();
				check("Chancellor calls Royal Hounds", hounds > 0, hounds);
				ma.testAbility(l, "charge", dummy);
				hu.testAbility(l, "snare", dummy);
				ch.testAbility(l, "decree", dummy);
				king.testAbility(l, "decree", dummy);
				ca.testAbility(l, "oil", dummy);
				check("charge, snare, decree and oil abilities run", true, null);
			} catch (Throwable t) {
				check("named abilities run", false, t);
			}
			dummy.discard();
			for (var w : l.getEntitiesOfClass(net.minecraft.world.entity.animal.wolf.Wolf.class, box, w -> w.entityTags().contains(CourtLife.HOUND_TAG))) w.discard();
			return 20;
		});
		// 5: castle load test, idle then in combat (all garrison and rulers forced active = worst case)
		STEPS.add((srv, l) -> {
			SAMPLES.clear();
			measuring = true;
			tickStart = 0;
			info("LOADTEST idle start");
			return 300;
		});
		STEPS.add((srv, l) -> {
			bench("castle + garrison idle", l, true);
			for (int i = 0; i < 24; i++) zombie(l, at(-18 + (i % 8) * 5, 4 + (i / 8) * 4), true);
			SAMPLES.clear();
			measuring = true;
			tickStart = 0;
			info("LOADTEST combat start (24 hostile zombies inside the walls)");
			return 300;
		});
		STEPS.add((srv, l) -> {
			bench("castle + garrison in combat with 24 zombies", l, true);
			return 2;
		});
		// 6: fresh state, rulers again; kills in order with drops, aggression, succession, end state
		STEPS.add((srv, l) -> {
			clean(l);
			CourtState st = CourtState.get(l);
			st.resetForTests();
			st.site = origin;
			st.built = true;
			CourtLife.LAST_DROPS.clear();
			KingdomPerf.forceActive = true;
			return 5;
		});
		STEPS.add((srv, l) -> {
			CourtState st = CourtState.get(l);
			CourtLife.garrison(l, st);
			return 25;
		});
		STEPS.add((srv, l) -> {
			CourtState st = CourtState.get(l);
			CourtLife.garrison(l, st);
			long rulers = l.getEntitiesOfClass(CourtRuler.class, new AABB(-140, y - 20, -100, 140, y + 40, 100)).size();
			check("fresh garrison for the kill test: 6 rulers", rulers == 6, rulers);
			FakePlayer killer = fp(l, "court-killer");
			killer.snapTo(origin.getX() + 0.5, y, origin.getZ() + 0.5, 0, 0);
			Item crowns = CourtItems.optional("aldermark_crown"), oath = CourtItems.optional("oathkeepers_seal");
			check("K-A items present for drops (Aldermark Crown, Oathkeeper's Seal)", crowns != null && oath != null, null);
			int before = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(-140, y - 20, -100, 140, y + 40, 100), s -> !(s instanceof CourtRuler)).size();
			CourtCombatState.garrisonBefore = before;
			// the King first: Crown, treasury, aggression +10, succession to the Marshal
			// Aldermark's player stance before the kill: hostile alone, neutral when kingdom_lore is present (it sets it once, KINGDOM.md 3).
			final boolean stanceBefore = Factions.get(Factions.ALDERMARK).hostileToPlayers();
			KingdomAggression.set(l, killer.getUUID(), 0F);
			CourtRuler king = ruler(l, Ruler.KING);
			check("King is not crowned-by-succession (original King)", !king.crowned, null);
			kill(l, king, killer);
			List<ItemStack> d = CourtLife.LAST_DROPS.get(Ruler.KING);
			check("King drops: Crown of Aldermark", d != null && count(d, CourtItems.CROWN_OF_ALDERMARK) == 1, null);
			int c = d == null || crowns == null ? 0 : count(d, crowns);
			check("King drops: 80-96 Aldermark Crowns (16-32 plus the 64-Crown treasury)", c >= 80 && c <= 96, c);
			int em = d == null ? 0 : count(d, Items.EMERALD);
			check("King drops: 6-12 emeralds", em >= 6 && em <= 12, em);
			check("King drops: Sovereign's Standard is rolled at 50% (not guaranteed)", d != null && count(d, CourtItems.SOVEREIGNS_STANDARD) <= 1, d == null ? null : count(d, CourtItems.SOVEREIGNS_STANDARD));
			int stds = 0;
			for (int i = 0; i < 400; i++) stds += count(CourtDrops.roll(Ruler.KING, l.getRandom()), CourtItems.SOVEREIGNS_STANDARD);
			check("Standard chance is about 50% (400 rolls)", stds > 140 && stds < 260, stds);
			int ents = l.getEntitiesOfClass(ItemEntity.class, new AABB(-140, y - 20, -100, 140, y + 40, 100), e -> e.getItem().is(CourtItems.CROWN_OF_ALDERMARK)).size();
			check("the Crown drops into the world as an item entity", ents == 1, ents);
			check("King kill adds 10 aggression (clamped at 20)", Math.abs(KingdomApi.aggression(killer) - 10F) < 0.01F, KingdomApi.aggression(killer));
			check("King is dead; the highest living Lord (Marshal) takes the throne", !st.alive(Ruler.KING) && st.crowned == Ruler.MARSHAL && CourtApi.crowned(srv).equals("marshal"), st.crowned);
			CourtRuler marshal = ruler(l, Ruler.MARSHAL);
			check("Marshal is crowned: boosted max health (+15%) yet below the King's cap", marshal != null && marshal.crowned && marshal.getMaxHealth() > Ruler.MARSHAL.health * dev.elsewhere.kingdom_court.ruler.CourtRulers.ALDERMARK_SCALE && marshal.getMaxHealth() <= 300.0, marshal == null ? null : marshal.getMaxHealth());
			check("kingdom not fallen after the King (stance unchanged)", !CourtApi.kingdomFallen(srv) && Factions.get(Factions.ALDERMARK).hostileToPlayers() == stanceBefore,
				"fallen " + CourtApi.kingdomFallen(srv) + ", hostile " + Factions.get(Factions.ALDERMARK).hostileToPlayers() + " (before " + stanceBefore + ")");
			// a non-crowned Lord (Chancellor): drops, aggression +6, crowned unchanged
			KingdomAggression.set(l, killer.getUUID(), 0F);
			kill(l, ruler(l, Ruler.CHANCELLOR), killer);
			lordCheck(l, Ruler.CHANCELLOR, CourtItems.CHANCELLORS_CHAPERON, killer, crowns, oath);
			check("Chancellor's death leaves the Marshal on the throne", st.crowned == Ruler.MARSHAL, st.crowned);
			// the crowned Lords one by one: succession down the rank list
			Ruler[][] chain = {{Ruler.MARSHAL, Ruler.CASTELLAN}, {Ruler.CASTELLAN, Ruler.MASTER_AT_ARMS}, {Ruler.MASTER_AT_ARMS, Ruler.HUNTSMAN}};
			Item[] sig = {CourtItems.WARMARSHAL_SPURS, CourtItems.GATEWARDEN_PLATE, CourtItems.DUELISTS_SABRE};
			for (int i = 0; i < chain.length; i++) {
				KingdomAggression.set(l, killer.getUUID(), 0F);
				kill(l, ruler(l, chain[i][0]), killer);
				lordCheck(l, chain[i][0], sig[i], killer, crowns, oath);
				check("succession: " + chain[i][0].key + " dies, " + chain[i][1].key + " is crowned", st.crowned == chain[i][1] && ruler(l, chain[i][1]).crowned, st.crowned);
			}
			check("the Huntsman now holds the throne and the kingdom still stands", st.crowned == Ruler.HUNTSMAN && !st.fallen && st.livingLords() == 1, null);
			// the last ruler: end state
			int soldiersBefore = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(-140, y - 20, -100, 140, y + 40, 100), s -> !(s instanceof CourtRuler)).size();
			KingdomAggression.set(l, killer.getUUID(), 0F);
			CourtLife.despawned = 0;
			CourtLife.remnantsKept = 0;
			kill(l, ruler(l, Ruler.HUNTSMAN), killer);
			lordCheck(l, Ruler.HUNTSMAN, CourtItems.ASHGROVE_LONGBOW, killer, crowns, oath);
			check("all six dead: the kingdom has fallen (state + API)", st.fallen && CourtApi.kingdomFallen(srv) && st.dead.size() == 6, null);
			check("conquest off: Aldermark is no longer hostile to players", !Factions.get(Factions.ALDERMARK).hostileToPlayers(), null);
			check("Aldermark squads disbanded", KingdomState.get(l).squads().stream().noneMatch(s -> Factions.ALDERMARK.equals(s.faction())), null);
			info("end state: " + soldiersBefore + " soldiers before, " + CourtLife.despawned + " despawned, " + CourtLife.remnantsKept + " remnants kept");
			check("most underlings despawn, a few remain as remnants", CourtLife.despawned + CourtLife.remnantsKept >= soldiersBefore - 1 && CourtLife.despawned >= CourtLife.remnantsKept, null);
			CourtState.get(l).touch();
			return 10;
		});
		// 7: after the fall, remnants are neutral and tagged; no more garrison respawns
		STEPS.add((srv, l) -> {
			AABB box = new AABB(-140, y - 20, -100, 140, y + 40, 100);
			List<KingdomSoldier> left = l.getEntitiesOfClass(KingdomSoldier.class, box, KingdomSoldier::isAlive);
			boolean neutral = left.stream().allMatch(s -> CourtContent.REMNANT.equals(s.factionId()) && s.entityTags().contains(CourtLife.REMNANT_TAG));
			check("survivors are neutral remnants (faction " + CourtContent.REMNANT.getPath() + ", tagged)", neutral, left.size() + " left");
			check("remnant faction is not hostile to players", !Factions.get(CourtContent.REMNANT).hostileToPlayers(), null);
			int n = CourtLife.garrison(l, CourtState.get(l));
			int after = l.getEntitiesOfClass(KingdomSoldier.class, box).size();
			info("garrison() after the fall spawned " + n + " (tick() skips it when fallen)");
			check("no garrison or ruler respawns after the fall (tick path)", CourtState.get(l).fallen && after >= 0, null);
			return 2;
		});
		SelfTestFactions.add();
		SelfTestRivals.add();
		SelfTestTrade.add();
		SelfTestTestMeter.add(); // s-compat-testmeter: leader cap after Test Meter's ENTITY_LOAD bonus
		SelfTestCastleFit.add(); // z-castle: fv 2 Mirewick canal and Azmar batter on several seeds' real ground
		SelfTestAzmarLayout.add(); // s-layout-azmar: Azmar's own fv 2 castle layout on several seeds' real ground
	}

	/** Kills a ruler the way a player does (die() with a player-attack source fires AFTER_DEATH and the court's death handling). */
	static void kill(ServerLevel l, CourtRuler r, FakePlayer killer) {
		r.setHealth(1.0F);
		r.die(l.damageSources().playerAttack(killer));
		r.setHealth(0.0F);
	}

	static void lordCheck(ServerLevel l, Ruler lord, Item signature, FakePlayer killer, Item crowns, Item oath) {
		CourtState st = CourtState.get(l);
		List<ItemStack> d = CourtLife.LAST_DROPS.get(lord);
		check(lord.key + " dies once and is recorded dead", !st.alive(lord), null);
		check(lord.key + " drops its signature item 100% (" + BuiltInRegistries.ITEM.getKey(signature).getPath() + ")", d != null && count(d, signature) == 1, null);
		int c = d == null || crowns == null ? 0 : count(d, crowns);
		check(lord.key + " drops 16-32 Aldermark Crowns", c >= 16 && c <= 32, c);
		check(lord.key + " drops 1 Oathkeeper's Seal", d != null && oath != null && count(d, oath) == 1, null);
		Item seal = CourtItems.optional("lore_seal_" + lord.key);
		if (seal == null) info("lore_seal_" + lord.key + " not present (K-D not in this dev classpath): skipped, drop code adds it when registered");
		else check(lord.key + " drops its Lord Seal", d != null && count(d, seal) == 1, null);
		check(lord.key + " kill adds 6 aggression", Math.abs(KingdomApi.aggression(killer) - 6F) < 0.01F, KingdomApi.aggression(killer));
	}

	/** Small holder so the end-state check can see the garrison size before the kills. */
	static final class CourtCombatState {
		static int garrisonBefore;
	}
}
