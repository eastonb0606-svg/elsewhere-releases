package dev.elsewhere.beta;

import static dev.elsewhere.beta.BetaSmokeTest.check;
import static dev.elsewhere.beta.BetaSmokeTest.f;
import static dev.elsewhere.beta.BetaSmokeTest.info;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.core.trip.AccidentalTrips;
import dev.elsewhere.core.trip.TripTrigger;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.registry.ModAttachments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Beta 2 checks added to the combined smoke test: Glimmer, minions, Calling, HUD settings, trips switches, overworldContent. */
final class BetaSmokeBeta2 {
	private BetaSmokeBeta2() {}

	static void run(final MinecraftServer server) {
		BetaSmokeTest.stage("beta2 glimmer", () -> glimmer(server));
		BetaSmokeTest.stage("beta2 minions", () -> minions(server));
		BetaSmokeTest.stage("beta2 calling", () -> calling(server));
		BetaSmokeTest.stage("beta2 hud settings", () -> hudSettings());
		BetaSmokeTest.stage("beta2 trips", () -> trips(server));
		BetaSmokeTest.stage("beta2 overworldContent", () -> overworldContent(server));
		BetaSmokeTest.stage("beta2 boss bars on removal", () -> bossBars(server));
		BetaSmokeTest.stage("beta2 boss advancements", () -> bossAdvancements(server));
	}

	/** Core ships per-boss advancements; the beta overlay switches them to player_killed_entity with the real entity id. */
	static void bossAdvancements(final MinecraftServer server) {
		FakePlayer p = player(server, "BetaAdv");
		int awarded = 0, total = 0;
		for (String id : dev.elsewhere.core.trophy.Trophies.BOSSES.keySet()) {
			total++;
			var adv = server.getAdvancements().get(Identifier.parse("elsewhere:boss/" + id));
			check(adv != null, "advancement boss/" + id + " loads");
			if (adv == null) continue;
			var crit = adv.value().criteria().values().iterator().next().trigger();
			check(crit == net.minecraft.advancements.triggers.CriteriaTriggers.PLAYER_KILLED_ENTITY, "advancement boss/" + id + " uses player_killed_entity");
			var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("elsewhere:" + id)).orElse(null);
			check(type != null, "boss entity elsewhere:" + id + " registered");
			if (type == null) continue;
			try {
				var e = type.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				if (e instanceof net.minecraft.world.entity.LivingEntity le) {
					net.minecraft.advancements.triggers.CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(p, le, p.damageSources().playerAttack(p));
					if (p.getAdvancements().getOrStartProgress(adv).isDone()) awarded++;
				}
			} catch (Throwable t) {
				info("advancement kill trigger boss/" + id + ": " + t);
			}
		}
		info("boss advancement award by simulated kill (FakePlayer): " + awarded + "/" + total);
	}

	static FakePlayer player(final MinecraftServer server, final String name) {
		ServerLevel ow = server.overworld();
		FakePlayer p = BetaSmokeTest.fake(ow, name);
		p.setGameMode(GameType.SURVIVAL);
		p.getFoodData().setFoodLevel(20);
		return p;
	}

	static void glimmer(final MinecraftServer server) {
		FakePlayer p = player(server, "BetaGlimmer");
		float max = Glimmer.max(p);
		check(max >= 19.9F, f("Glimmer: fresh player max %.1f (20 expected)", max));
		Glimmer.setForTest(p, max);
		float cost = Glimmer.cost(p, 5);
		boolean spent = Glimmer.trySpend(p, 5);
		float after = Glimmer.current(p);
		check(spent && Math.abs(after - (max - cost)) < 0.06F, f("Glimmer: spending 5 raw takes %.1f (%.1f -> %.1f)", cost, max, after));
		Glimmer.setForTest(p, 1F);
		check(!Glimmer.trySpend(p, 5), "Glimmer: a spend larger than the pool fails and spends nothing");
		check(Math.abs(Glimmer.current(p) - 1F) < 0.01F, "Glimmer: failed spend left the pool untouched");
		Glimmer.markSpentForTest(p, 1000);
		for (int i = 0; i < 20; i++) Glimmer.tickPlayer(p);
		float regen = Glimmer.current(p);
		check(regen > 1.5F, f("Glimmer: regenerates after the delay (1.0 -> %.2f in 1 s, %.2f/s)", regen, Glimmer.regenPerSecond(p)));
		Glimmer.setForTest(p, 1F);
		Glimmer.markSpentForTest(p, 0);
		Glimmer.tickPlayer(p);
		check(Glimmer.current(p) <= 1.01F, "Glimmer: no regen right after a spend (delay)");
		// module stubs now point at the real pool
		Glimmer.setForTest(p, 0F);
		check(!dev.elsewhere.stub.core.GlimmerStub.trySpend(p, 3), "core GlimmerStub (Moon-Eye) uses the real pool (empty pool -> refused)");
		check(!dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.trySpend(p, 3), "petalwind_glasswaste GlimmerStub uses the real pool");
		check(!dev.elsewhere.stub.greenhaze_hush.GlimmerStub.trySpend(p, 3F), "greenhaze_hush GlimmerStub uses the real pool");
		check(!dev.elsewhere.stub.nether_end_bosses.GlimmerStub.trySpend(p, 3F), "nether_end_bosses GlimmerStub uses the real pool");
		Glimmer.setForTest(p, Glimmer.max(p));
		check(dev.elsewhere.stub.core.GlimmerStub.trySpend(p, 3) && Glimmer.current(p) < Glimmer.max(p), "core GlimmerStub spends from the real pool when full");
	}

	static void minions(final MinecraftServer server) {
		FakePlayer p = player(server, "BetaSummoner");
		AttributeInstance slots = p.getAttribute(ArsenalAttributes.MINION_SLOTS);
		AttributeInstance maxG = p.getAttribute(ArsenalAttributes.MAX_GLIMMER);
		check(slots != null && maxG != null, "players carry the arsenal attributes (minion_slots, max_glimmer)");
		if (slots == null || maxG == null) return;
		slots.setBaseValue(4);
		maxG.setBaseValue(Math.min(200, ArsenalAttributes.MAX_GLIMMER.value().sanitizeValue(200)));
		// 3.0 (GEAR.md "Minion slot cap"): the effective slot count is min(minion_slots, 1 + floor(3 (M - 0.25) / 0.75)), so
		// this gearless (Unattuned) tester has 1 slot although minion_slots is 4, and a 2nd summon replaces the 1st. The
		// 2.x expectation "three staffs stack to 3 out" is gone by design; each staff is checked on its own instead.
		int cap = MinionApi.maxSlots(p);
		check(!dev.elsewhere.arsenal.Overhaul3.CALLINGS || cap == Math.min(4, Calling.minionSlotCap(p)),
			f("3.0 slot cap: minion_slots 4, gearless (Unattuned) summoner gets %d slot(s), Calling cap %d", cap, Calling.minionSlotCap(p)));
		String[] staffs = {"rootcall_staff", "kitestring_staff", "brassback_key"};
		for (String id : staffs) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("elsewhere", id));
			if (item == null || BuiltInRegistries.ITEM.getKey(item).getPath().equals("air")) {
				check(false, "summoner item elsewhere:" + id + " registered");
				continue;
			}
			for (var m : new ArrayList<>(MinionManager.minions(p))) MinionManager.dismiss(m); // free the slot (see the cap above)
			Glimmer.setForTest(p, Glimmer.max(p));
			int before = MinionApi.activeCount(p);
			float g0 = Glimmer.current(p);
			ItemStack stack = new ItemStack(item);
			p.setItemInHand(InteractionHand.MAIN_HAND, stack);
			try {
				// staffs are a 1 s draw (use starts it, the release summons): call the release step directly
				if (item instanceof dev.elsewhere.minion.MinionStaffItem staff) staff.summonNow(p, stack);
				else stack.use(p.level(), p, InteractionHand.MAIN_HAND);
			} catch (Throwable t) {
				check(false, "using " + id + " threw " + t);
				continue;
			}
			int after = MinionApi.activeCount(p);
			float g1 = Glimmer.current(p);
			check(after == before + 1, f("%s summons a minion (%d -> %d out), Glimmer %.1f -> %.1f, reserve %d", id, before, after, g0, g1, Glimmer.reserved(p)));
			check(after != before + 1 || g1 < g0 - 0.5F || Glimmer.max(p) < g0 - 0.5F, f("%s: the summon is paid from the real Glimmer pool (%.1f -> %.1f, usable max %.1f)", id, g0, g1, Glimmer.max(p)));
		}
		int n = MinionApi.activeCount(p);
		check(n == 0 || Glimmer.reserved(p) > 0, f("live minions reserve Glimmer (%d out, %d reserved, usable max %.1f)", n, Glimmer.reserved(p), Glimmer.max(p)));
		for (var m : new ArrayList<>(MinionManager.minions(p))) MinionManager.dismiss(m);
		check(MinionApi.activeCount(p) == 0, "minions dismissed");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	static List<ItemStack> tagged(final TagKey<Item> tag) {
		List<ItemStack> out = new ArrayList<>();
		for (Item it : BuiltInRegistries.ITEM) {
			ItemStack s = new ItemStack(it);
			if (s.is(tag)) out.add(s);
		}
		return out;
	}

	static void calling(final MinecraftServer server) {
		FakePlayer p = player(server, "BetaCalling");
		for (String kind : new String[] {"caster", "summoner"}) {
			TagKey<Item> tag = kind.equals("caster") ? Calling.CASTER_ARMOR : Calling.SUMMONER_ARMOR;
			List<ItemStack> pieces = tagged(tag);
			EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (EquipmentSlot s : order) p.setItemSlot(s, ItemStack.EMPTY);
			int worn = 0;
			for (EquipmentSlot s : order) {
				for (ItemStack st : pieces) {
					if (p.getEquipmentSlotForItem(st) == s) {
						p.setItemSlot(s, st.copy());
						worn++;
						break;
					}
				}
			}
			int c = kind.equals("caster") ? Calling.caster(p) : Calling.summoner(p);
			check(pieces.size() >= 4 && c == worn && worn == 4, f("%s Calling reads worn gear: %d tagged items, %d worn, Calling %d", kind, pieces.size(), worn, c));
			for (EquipmentSlot s : order) p.setItemSlot(s, ItemStack.EMPTY);
			int c0 = kind.equals("caster") ? Calling.caster(p) : Calling.summoner(p);
			check(c0 == 0, kind + " Calling is 0 with no armor (vanilla gear ignores it)");
		}
		StringBuilder dmg = new StringBuilder();
		boolean mono = true;
		for (int c = 0; c <= 4; c++) {
			dmg.append(f(" %d:x%.2f/cost x%.2f", c, Calling.spellDamage(c), Calling.spellCost(c)));
			if (c > 0) mono &= Calling.spellDamage(c) >= Calling.spellDamage(c - 1) && Calling.spellCost(c) <= Calling.spellCost(c - 1);
		}
		check(mono, "Calling multipliers rise with pieces worn (spell damage up, cost down):" + dmg);
		float cost0 = Glimmer.cost(p, 10);
		List<ItemStack> caster = tagged(Calling.CASTER_ARMOR);
		for (ItemStack st : caster) p.setItemSlot(p.getEquipmentSlotForItem(st), st.copy());
		float cost4 = Glimmer.cost(p, 10);
		check(cost4 <= cost0, f("Glimmer cost of a 10-raw spell: %.1f with no caster gear, %.1f in full caster gear", cost0, cost4));
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) p.setItemSlot(s, ItemStack.EMPTY);
	}

	static void hudSettings() {
		// The HUD lives in the client source set; on a server we check the class is packaged and the config file is
		// well-formed when present (the client screenshot pass loads it for real).
		boolean client = FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT;
		Path cfg = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
		if (Files.exists(cfg)) {
			try {
				com.google.gson.JsonParser.parseString(Files.readString(cfg)).getAsJsonObject();
				check(true, "config/elsewhere-client.json parses");
			} catch (Exception e) {
				check(false, "config/elsewhere-client.json parses: " + e);
			}
		}
		if (!client) {
			boolean packaged = BetaSmokeBeta2.class.getClassLoader().getResource("dev/elsewhere/hud/ElsewhereHud.class") != null;
			if (packaged) check(true, "HUD settings class (dev.elsewhere.hud.ElsewhereHud) is in the jar");
			else info("HUD settings class not visible on the dedicated-server classpath (client source set); checked in the client pass");
		}
	}

	static TripTrigger anyTrigger() {
		for (TripTrigger t : AccidentalTrips.triggers().values()) if (t.kind() == TripTrigger.Kind.LOCATION) return t;
		return AccidentalTrips.triggers().values().stream().findFirst().orElse(null);
	}

	static void trips(final MinecraftServer server) {
		List<String> ids = new ArrayList<>(AccidentalTrips.triggers().keySet());
		long modules = ids.stream().filter(i -> !i.startsWith("pale_hollow") && !i.startsWith("palehollow")).count();
		check(modules >= 3, f("module trip triggers run in the core framework: %d triggers %s", ids.size(), ids));
		FakePlayer p = player(server, "BetaTripper");
		var oldRoll = AccidentalTrips.rollOverride;
		var oldPlay = AccidentalTrips.playTicksOverride;
		try {
			AccidentalTrips.playTicksOverride = pl -> 20L * 3600 * 10;
			// global spacing: a trip 10 minutes ago blocks every trigger of every module
			var state = p.getAttachedOrCreate(ModAttachments.TRIP_STATE);
			long saved = state.lastTripPlayTicks;
			state.lastTripPlayTicks = 20L * 3600 * 10 - 20L * 600;
			boolean allZero = true;
			for (TripTrigger t : AccidentalTrips.triggers().values()) allZero &= AccidentalTrips.currentChance(p, t, true) == 0.0;
			check(allZero, "global spacing: 10 min after any trip every trigger (all modules) is at 0%");
			state.lastTripPlayTicks = saved;
			TripTrigger t = anyTrigger();
			if (t == null) {
				check(false, "a trip trigger exists");
				return;
			}
			if (!GlobalTrips.overworldContent()) {
				info("overworldContent=false: /elsewhere trips on|off check skipped (all trips off)");
				return;
			}
			double chance = AccidentalTrips.currentChance(p, t, true);
			int r = server.getCommands().getDispatcher().execute("elsewhere trips off", p.createCommandSourceStack());
			AccidentalTrips.rollOverride = () -> 0.0;
			boolean fired = AccidentalTrips.fire(p, t);
			check(r == 1 && !fired && chance > 0, f("/elsewhere trips off (non-op player): %s at %.4f%% with a forced roll does not fire", t.id(), chance * 100));
			r = server.getCommands().getDispatcher().execute("elsewhere trips on", p.createCommandSourceStack());
			check(r == 1 && GlobalTrips.allowed(p), "/elsewhere trips on turns them back on");
		} catch (Exception e) {
			check(false, "trips commands threw " + e);
		} finally {
			AccidentalTrips.rollOverride = oldRoll;
			AccidentalTrips.playTicksOverride = oldPlay;
		}
	}

	static void overworldContent(final MinecraftServer server) {
		boolean on = GlobalTrips.overworldContent();
		List<Holder<StructureSet>> sets = server.overworld().getChunkSource().getGeneratorState().possibleStructureSets();
		long sites = sets.stream().filter(OverworldContent::isSiteSet).count();
		long registered = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElementIds()
			.filter(k -> OverworldContent.SITE_SETS.contains(k.identifier().toString())).count();
		info(f("overworldContent=%s: %d of %d Overworld boss-site structure sets active", on, sites, registered));
		if (on) {
			check(sites == registered, f("overworldContent=true: Overworld boss sites placed (%d/%d)", sites, registered));
		} else {
			check(sites == 0, "overworldContent=false: no Overworld boss-site structure set is placed");
			FakePlayer p = player(server, "BetaVanilla");
			TripTrigger t = anyTrigger();
			var oldRoll = AccidentalTrips.rollOverride;
			var oldPlay = AccidentalTrips.playTicksOverride;
			try {
				AccidentalTrips.rollOverride = () -> 0.0;
				AccidentalTrips.playTicksOverride = pl -> 20L * 3600 * 10;
				check(t != null && !AccidentalTrips.fire(p, t), "overworldContent=false: accidental trips never fire (forced roll)");
			} finally {
				AccidentalTrips.rollOverride = oldRoll;
				AccidentalTrips.playTicksOverride = oldPlay;
			}
		}
	}

	static void bossBars(final MinecraftServer server) {
		ServerLevel l = server.overworld();
		FakePlayer p = player(server, "BetaBarWatcher");
		int checked = 0;
		for (String id : List.of("mother_creak", "kazehana", "verdigrim", "percolax", "gulpmother", "grunthar", "solaxis", "vorlagh")) {
			Identifier key = Identifier.fromNamespaceAndPath("elsewhere", id);
			if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) continue;
			net.minecraft.world.entity.Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(key).create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (e == null) continue;
			e.snapTo(p.getX(), p.getY() + 200, p.getZ(), 0, 0);
			List<net.minecraft.server.level.ServerBossEvent> bars = BossBarSweep.bars(e);
			if (bars.isEmpty()) {
				info(id + ": no boss bar exists before the fight starts (bars are created with the fight)");
				e.discard();
				continue;
			}
			for (var b : bars) b.addPlayer(p);
			e.discard();
			boolean clear = bars.stream().allMatch(b -> b.getPlayers().isEmpty());
			check(clear, f("%s: discarding the boss (no death) clears its %d boss bar(s)", id, bars.size()));
			checked++;
		}
		check(checked >= 3, f("boss-bar removal checked on %d boss types", checked));
	}
}
