package dev.elsewhere.beta;

import static dev.elsewhere.beta.BetaSmokeTest.check;
import static dev.elsewhere.beta.BetaSmokeTest.f;
import static dev.elsewhere.beta.BetaSmokeTest.info;

import dev.elsewhere.core.travel.RiftEntity;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.core.wayfinder.AttunedWayfinderItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** 0.4.0-beta checks added to the combined smoke test and the production check. */
final class BetaSmoke040 {
	private BetaSmoke040() {}

	static void run(final MinecraftServer server) {
		BetaSmokeTest.stage("0.4 wayfinder home", () -> wayfinderHome(server));
		BetaSmokeTest.stage("0.4 herding crook enchantments", () -> herdingCrook(server));
		BetaSmokeTest.stage("0.4 maxMinionsPerPlayer", () -> minionCap(server));
		BetaSmokeTest.stage("0.4 nether/end boss arenas", () -> netherEndArenas(server));
	}

	static Item item(final String id) {
		Identifier key = Identifier.fromNamespaceAndPath("elsewhere", id);
		return BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.getValue(key) : null;
	}

	// ------------------------------------------------------------------ Wayfinder: out and home again, the real way
	/** Ticks the rift until it has taken the player (its 3 s opening, then the pull); the world isn't ticking during a stage. */
	static boolean tickRiftUntilGone(final RiftEntity rift, final FakePlayer p, final Level from) {
		// the owner stands where they used the Wayfinder (the rift opens 1.6 blocks ahead) and must be pulled through on its own
		for (int i = 0; i < 200 && p.level() == from; i++) {
			if (rift.isRemoved()) break;
			rift.tick();
		}
		if (p.level() == from) {
			info(f("rift at %s (age %d, removed %s) did not pull its owner standing %.1f blocks away", rift.blockPosition().toShortString(), rift.age(),
				rift.isRemoved(), Math.sqrt(rift.distanceToSqr(p))));
		}
		return p.level() != from;
	}

	static void wayfinderHome(final MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		int sx = 700, sz = 700;
		overworld.getChunk(sx >> 4, sz >> 4);
		BlockPos start = new BlockPos(sx, overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz), sz);
		List<AttunedWayfinderItem> wayfinders = new ArrayList<>();
		for (Item it : BuiltInRegistries.ITEM) if (it instanceof AttunedWayfinderItem a && server.getLevel(a.target()) != null) wayfinders.add(a);
		check(wayfinders.size() >= 7, f("%d attuned Wayfinders with a loaded dimension", wayfinders.size()));
		for (AttunedWayfinderItem w : wayfinders) {
			String dim = w.target().identifier().getPath();
			FakePlayer p = BetaSmokeBeta2.player(server, "BetaWayHome_" + dim);
			try {
				p.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
				ItemStack stack = new ItemStack(w);
				p.setItemInHand(InteractionHand.MAIN_HAND, stack);
				RiftEntity before = RiftTravel.lastRift;
				InteractionResult r1 = p.gameMode.useItem(p, overworld, stack, InteractionHand.MAIN_HAND);
				RiftEntity out = RiftTravel.lastRift;
				boolean opened = out != null && out != before;
				boolean arrived = opened && tickRiftUntilGone(out, p, overworld) && p.level().dimension().equals(w.target());
				check(arrived, f("Wayfinder %s used in the Overworld (%s): rift opened %s, player now in %s", dim, r1, opened, p.level().dimension().identifier()));
				if (!arrived) continue;
				ServerLevel there = p.level();
				ItemStack held = p.getMainHandItem();
				// right after arriving: the shared cooldown may still run (the item says so); after it, the second use opens a rift home
				boolean cooling = p.getCooldowns().isOnCooldown(held);
				RiftEntity b2 = RiftTravel.lastRift;
				InteractionResult rNow = p.gameMode.useItem(p, there, held, InteractionHand.MAIN_HAND);
				boolean openedNow = RiftTravel.lastRift != b2;
				info(f("Wayfinder %s: second use right after arriving: cooldown %s, result %s, rift %s", dim, cooling, rNow, openedNow));
				RiftEntity home;
				if (openedNow) {
					home = RiftTravel.lastRift;
				} else {
					for (int i = 0; i < 20 * 120 && p.getCooldowns().isOnCooldown(held); i++) p.getCooldowns().tick();
					RiftEntity b3 = RiftTravel.lastRift;
					InteractionResult r2 = p.gameMode.useItem(p, there, p.getMainHandItem(), InteractionHand.MAIN_HAND);
					home = RiftTravel.lastRift != b3 ? RiftTravel.lastRift : null;
					if (home == null) {
						check(false, f("Wayfinder %s used inside its dimension (cooldown over, %s) opens a rift home", dim, r2));
						continue;
					}
				}
				boolean back = tickRiftUntilGone(home, p, there) && p.level() == overworld;
				double dist = p.position().distanceTo(Vec3.atBottomCenterOf(start));
				check(back && dist < 64, f("Wayfinder %s used inside its dimension opens a rift home: back in the Overworld %.0f blocks from the start", dim, dist));
			} catch (Throwable t) {
				BetaSmokeTest.LOG.error("[betatest] wayfinder home {} threw", dim, t);
				check(false, "Wayfinder " + dim + " home threw " + t);
			} finally {
				if (p.level() != overworld) RiftTravel.teleport(p, overworld, Vec3.atBottomCenterOf(start), 0.0F);
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
		}
	}

	// ------------------------------------------------------------------ Herding Crook: Knockback, Unbreaking, Mending only
	static final Set<String> CROOK_ALLOWED = Set.of("minecraft:knockback", "minecraft:unbreaking", "minecraft:mending");
	static final Set<String> CROOK_FORBIDDEN = Set.of("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods", "minecraft:looting",
		"minecraft:fire_aspect", "minecraft:sweeping_edge");

	static void herdingCrook(final MinecraftServer server) {
		Item crook = item("herding_crook");
		if (crook == null) {
			check(false, "elsewhere:herding_crook is registered");
			return;
		}
		ItemStack st = new ItemStack(crook);
		var reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Set<String> anvil = new TreeSet<>(), primary = new TreeSet<>(), curses = new TreeSet<>();
		reg.listElements().forEach(h -> {
			String id = h.key().identifier().toString();
			Enchantment e = h.value();
			boolean curse = h.is(EnchantmentTags.CURSE);
			// the real checks (anvil, books, /enchant, enchant_randomly loot) go through Fabric's ALLOW_ENCHANTING via canBeEnchantedWith
			if (st.canBeEnchantedWith(h, net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE)) (curse ? curses : anvil).add(id);
			if (st.canBeEnchantedWith(h, net.fabricmc.fabric.api.item.v1.EnchantingContext.PRIMARY)) primary.add(id);
		});
		Set<String> bad = new TreeSet<>(anvil);
		bad.removeAll(CROOK_ALLOWED);
		check(anvil.containsAll(CROOK_ALLOWED) && bad.isEmpty(), f("Herding Crook takes only Knockback, Unbreaking, Mending (anvil/books): %s%s", anvil,
			bad.isEmpty() ? "" : " - not allowed: " + bad));
		if (!curses.isEmpty()) info("Herding Crook also accepts curses: " + curses);
		Set<String> forbiddenPrimary = new TreeSet<>(primary);
		forbiddenPrimary.retainAll(CROOK_FORBIDDEN);
		Set<String> table = new TreeSet<>();
		RandomSource rnd = RandomSource.create(40L);
		var pool = reg.get(EnchantmentTags.IN_ENCHANTING_TABLE);
		if (pool.isPresent()) {
			for (int i = 0; i < 300; i++) {
				for (EnchantmentInstance ei : EnchantmentHelper.selectEnchantment(rnd, st, 5 + rnd.nextInt(26), pool.get().stream())) {
					table.add(ei.enchantment().unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
				}
			}
		}
		Set<String> badTable = new TreeSet<>(table);
		badTable.removeAll(CROOK_ALLOWED);
		check(forbiddenPrimary.isEmpty() && badTable.isEmpty(), f("Herding Crook at the enchanting table (300 rolls, levels 5-30): %s%s", table,
			badTable.isEmpty() && forbiddenPrimary.isEmpty() ? "" : " - not allowed: " + badTable + " primary " + forbiddenPrimary));
	}

	// ------------------------------------------------------------------ summoner server config maxMinionsPerPlayer (0..4)
	static void minionCap(final MinecraftServer server) {
		Class<?> cfg;
		try {
			cfg = Class.forName("dev.elsewhere.summoner.SummonerConfig");
		} catch (ClassNotFoundException e) {
			check(false, "summoner config maxMinionsPerPlayer exists (dev.elsewhere.summoner.SummonerConfig)");
			return;
		}
		try {
			var get = cfg.getMethod("maxMinionsPerPlayer");
			var set = cfg.getMethod("setMaxMinionsPerPlayer", int.class);
			int live = (Integer)get.invoke(null);
			java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-summoner.json");
			boolean inCore = false;
			try {
				dev.elsewhere.ElsewhereConfig.class.getField("maxMinionsPerPlayer");
				inCore = true;
			} catch (NoSuchFieldException ignored) {
				// summoner's own file
			}
			check(live >= 0 && live <= 4 && (inCore || java.nio.file.Files.exists(file)), f("maxMinionsPerPlayer = %d (0..4), from %s", live,
				inCore ? "config/elsewhere.json" : "config/elsewhere-summoner.json" + (java.nio.file.Files.exists(file) ? "" : " (missing)")));
			FakePlayer p = BetaSmokeBeta2.player(server, "BetaMinionCap");
			var slots = p.getAttribute(dev.elsewhere.arsenal.ArsenalAttributes.MINION_SLOTS);
			var maxG = p.getAttribute(dev.elsewhere.arsenal.ArsenalAttributes.MAX_GLIMMER);
			if (slots == null || maxG == null) {
				check(false, "players carry minion_slots / max_glimmer");
				return;
			}
			slots.setBaseValue(4);
			maxG.setBaseValue(Math.min(200, dev.elsewhere.arsenal.ArsenalAttributes.MAX_GLIMMER.value().sanitizeValue(200)));
			Item staff = item("rootcall_staff");
			if (!(staff instanceof dev.elsewhere.minion.MinionStaffItem ms)) {
				check(false, "rootcall_staff is a minion staff");
				return;
			}
			try {
				for (int cap : new int[] {2, 0}) {
					set.invoke(null, cap);
					for (int i = 0; i < 4; i++) {
						dev.elsewhere.arsenal.glimmer.Glimmer.setForTest(p, dev.elsewhere.arsenal.glimmer.Glimmer.max(p));
						ItemStack st = new ItemStack(staff);
						p.setItemInHand(InteractionHand.MAIN_HAND, st);
						ms.summonNow(p, st);
					}
					int n = dev.elsewhere.minion.MinionApi.activeCount(p);
					check(n <= cap, f("maxMinionsPerPlayer=%d with 4 gear slots: 4 summons leave %d minion(s) out (limit %d)", cap, n,
						dev.elsewhere.minion.MinionApi.maxSlots(p)));
					for (var m : new ArrayList<>(dev.elsewhere.minion.MinionManager.minions(p))) dev.elsewhere.minion.MinionManager.dismiss(m);
				}
				set.invoke(null, 9);
				int clamped = (Integer)get.invoke(null);
				check(clamped <= 4, f("maxMinionsPerPlayer can't go above 4 (set 9 -> %d)", clamped));
			} finally {
				set.invoke(null, live);
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
		} catch (ReflectiveOperationException e) {
			check(false, "summoner config API (maxMinionsPerPlayer/setMaxMinionsPerPlayer): " + e);
		}
	}

	// ------------------------------------------------------------------ Nether / End boss arenas in world generation
	static final List<String> NE_BOSSES = List.of("grunthar", "solaxis", "mournmaw", "ossyra", "aphelion", "kazehana", "tempestra", "heliodor", "ankhara");

	/** Structure id prefix per boss (alpha.2: Petalwind/Glasswaste sites are structures too). */
	static String structurePrefix(final String boss) {
		return switch (boss) {
			case "kazehana" -> "petal_pagoda";
			case "tempestra" -> "eye_of_the_gale";
			case "heliodor" -> "sunwright_ziggurat";
			case "ankhara" -> "sunpit";
			default -> boss;
		};
	}

	static ServerLevel levelFor(final MinecraftServer server, final String boss) {
		return switch (boss) {
			case "kazehana", "tempestra" -> server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.parse("elsewhere:petalwind")));
			case "heliodor", "ankhara" -> server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.parse("elsewhere:glasswaste")));
			case "ossyra", "aphelion" -> server.getLevel(Level.END);
			default -> server.getLevel(Level.NETHER);
		};
	}

	static void netherEndArenas(final MinecraftServer server) {
		var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var placed = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
		for (String boss : NE_BOSSES) {
			ServerLevel level = levelFor(server, boss);
			if (level == null) {
				check(false, boss + ": its dimension is loaded");
				continue;
			}
			List<Holder.Reference<net.minecraft.world.level.levelgen.structure.Structure>> st = structures.listElements()
				.filter(h -> h.key().identifier().getNamespace().equals("elsewhere") && h.key().identifier().getPath().startsWith(structurePrefix(boss))).toList();
			List<Holder.Reference<net.minecraft.world.level.levelgen.placement.PlacedFeature>> pf = placed.listElements()
				.filter(h -> h.key().identifier().getNamespace().equals("elsewhere") && h.key().identifier().getPath().startsWith(boss)).toList();
			if (!st.isEmpty()) {
				for (var h : st) arenaStructure(level, boss, h);
			} else if (!pf.isEmpty()) {
				for (var h : pf) arenaFeature(level, boss, h);
			} else {
				check(false, boss + ": an arena structure or placed feature is registered");
			}
		}
	}

	static void arenaStructure(final ServerLevel level, final String boss, final Holder.Reference<net.minecraft.world.level.levelgen.structure.Structure> h) {
		String id = h.key().identifier().toString();
		boolean active = level.getChunkSource().getGeneratorState().possibleStructureSets().stream()
			.anyMatch(set -> set.value().structures().stream().anyMatch(e -> e.structure().equals(h)));
		BlockPos origin = level.dimension().equals(Level.END) ? new BlockPos(1500, 64, 0) : new BlockPos(7000, 64, -7000); // a fresh area
		long t0 = System.nanoTime();
		BlockPos at = active ? level.findNearestMapStructure(net.minecraft.core.HolderSet.direct(h), origin, 160, false) : null;
		long ms = (System.nanoTime() - t0) / 1_000_000;
		if (at == null) {
			check(false, f("%s: %s %s in %s (locate within 160 chunks of %s: none, %d ms)", boss, id, active ? "is in a structure set" : "is NOT in any structure set",
				level.dimension().identifier(), origin.toShortString(), ms));
			return;
		}
		var chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
		var start = chunk.getStartForStructure(h.value());
		boolean ok = start != null && start.isValid();
		check(ok, f("%s: %s located at %s (%.0f blocks from %s, %d ms) and generated (%d piece(s))", boss, id, at.toShortString(),
			Math.sqrt(at.distSqr(origin)), origin.toShortString(), ms, ok ? start.getPieces().size() : 0));
	}

	static void arenaFeature(final ServerLevel level, final String boss, final Holder.Reference<net.minecraft.world.level.levelgen.placement.PlacedFeature> h) {
		String id = h.key().identifier().toString();
		// wired into a biome of this dimension?
		List<String> inBiomes = new ArrayList<>();
		for (Holder<net.minecraft.world.level.biome.Biome> b : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
			boolean has = b.value().getGenerationSettings().features().stream().anyMatch(step -> step.stream().anyMatch(x -> x.equals(h) || x.value() == h.value()));
			if (has) inBiomes.add(b.unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
		}
		check(!inBiomes.isEmpty(), f("%s: placed feature %s generates in %s biomes %s", boss, id, level.dimension().identifier(), inBiomes));
		if (inBiomes.isEmpty()) return;
		// and its builder places an arena at a real spot of such a biome (the rarity filter skipped)
		BlockPos origin = level.dimension().equals(Level.END) ? new BlockPos(1500, 64, 0) : new BlockPos(0, 64, 0);
		var found = level.findClosestBiome3d(b -> b.unwrapKey().map(k -> inBiomes.contains(k.identifier().getPath())).orElse(false), origin, 3200, 32, 64);
		if (found == null) {
			check(false, f("%s: no %s biome within 3200 blocks of %s", boss, inBiomes, origin.toShortString()));
			return;
		}
		BlockPos p = found.getFirst();
		boolean placedOk = false;
		RandomSource rnd = RandomSource.create(boss.hashCode());
		int tries = 0;
		for (int dx = 0; dx < 4 && !placedOk; dx++) {
			for (int dz = 0; dz < 4 && !placedOk; dz++) {
				BlockPos q = p.offset(dx * 48, 0, dz * 48);
				for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.getChunk((q.getX() >> 4) + cx, (q.getZ() >> 4) + cz);
				tries++;
				placedOk = h.value().feature().value().place(level, level.getChunkSource().getGenerator(), rnd, q);
			}
		}
		check(placedOk, f("%s: its arena builds at a %s spot near %s (%d tries)", boss, found.getSecond().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"),
			p.toShortString(), tries));
	}

	static String key(final Holder<?> h) {
		return h.unwrapKey().map(k -> ((ResourceKey<?>)k).identifier().toString()).orElse("?");
	}
}
