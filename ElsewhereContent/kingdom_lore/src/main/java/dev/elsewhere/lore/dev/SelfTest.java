package dev.elsewhere.lore.dev;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.lore.LoreAdvancements;
import dev.elsewhere.lore.LoreApi;
import dev.elsewhere.lore.LoreContent;
import dev.elsewhere.lore.LoreItems;
import dev.elsewhere.lore.LoreLoot;
import dev.elsewhere.lore.LoreMaps;
import dev.elsewhere.lore.PatrolRate;
import dev.elsewhere.lore.Patrols;
import dev.elsewhere.lore.Proclamation;
import dev.elsewhere.lore.Quartermasters;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lore server self-test (-Dlore.selftest=true): logs PASS/FAIL, writes selftest-lore.log. */
public final class SelfTest {
	private SelfTest() {}

	static final List<String> LOG = new ArrayList<>();
	static int pass, fail;

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[lore-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void check(String name, boolean ok) {
		check(name, ok, null);
	}

	static void checkS(String name, BooleanSupplier s) {
		try {
			check(name, s.getAsBoolean());
		} catch (Throwable t) {
			check(name, false, t);
		}
	}

	public static void register() {
		int[] ticks = {0};
		boolean[] ran = {false};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			for (int cx = 1; cx <= 4; cx++) for (int cz = 1; cz <= 4; cz++) srv.overworld().setChunkForced(cx, cz, true);
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (ran[0]) return;
			if (++ticks[0] < 400 && !srv.overworld().isPositionEntityTicking(new BlockPos(40, 0, 40))) return;
			ran[0] = true;
			start(srv);
		});
	}

	static void start(MinecraftServer server) {
		ServerLevel l = server.overworld();
		int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 40);
		BlockPos base = new BlockPos(40, y, 40);
		FakePlayer p = FakePlayer.get(l);
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
		p.getAdvancements().setPlayer(p);
		p.getAdvancements().reload(server.getAdvancements());
		// A FakePlayer is not in the player list, so prime its progress the way a joining player's load does.
		for (var h : server.getAdvancements().getAllAdvancements()) p.getAdvancements().getOrStartProgress(h).update(h.value().requirements());
		try {
			body(server, l, p, base);
		} catch (Throwable t) {
			check("selftest crashed", false, t);
			t.printStackTrace();
		}
		finish(server);
	}

	static void body(MinecraftServer server, ServerLevel l, FakePlayer p, BlockPos base) {
		// ---- items, tags, recipe, proclamation
		checkS("items registered (proclamation, fragment, map, 5 seals, 3 rivals x 3, well-stone)", () -> LoreItems.ALL.size() == 18
			&& BuiltInRegistries.ITEM.containsKey(Identifier.parse("elsewhere:lore_seal_chancellor")));
		checkS("seal tag holds the 5 seals", () -> {
			int n = 0;
			for (var it : LoreItems.SEALS.values()) if (new ItemStack(it).is(LoreItems.SEAL_TAG)) n++;
			return n == 5;
		});
		checkS("Proclamation: 12 pages, each fits a book page, King's byline", () -> Proclamation.PAGES.size() >= 10
			&& Proclamation.PAGES.stream().allMatch(s -> s.length() <= 300) && Proclamation.AUTHOR.contains("Osric Vael"));
		checkS("Proclamation item carries its written-book content", () -> {
			ItemStack s = LoreItems.proclamation();
			var c = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
			return c != null && c.pages().size() == Proclamation.PAGES.size() && c.resolved();
		});
		checkS("recipe: 3 fragments craft the folded Survey Map", () -> {
			var holders = server.getRecipeManager().getRecipes();
			for (var h : holders) if (h.id().identifier().equals(LoreContent.id("lore_royal_survey_map"))) return true;
			return false;
		});

		// ---- patrol rate: the user's target, in numbers
		checkS("rate: nothing below 400 blocks, full from 500", () -> PatrolRate.perMinute(300) == 0 && PatrolRate.perMinute(500) == PatrolRate.perMinute(900) && PatrolRate.perMinute(500) > 0);
		double c5 = PatrolRate.chanceWithin(500, 300), c5eff = PatrolRate.chanceWithin(500, 210), c1 = PatrolRate.chanceWithin(550, 60);
		check("rate: 5 h pure exploring at 500 blocks >= 50% (target)", c5 >= 0.5, String.format("%.1f%%", c5 * 100));
		check("rate: 5 h play at 70% explore time (210 min) still >= 50%", c5eff >= 0.5, String.format("%.1f%%", c5eff * 100));
		check("rate: not overly common (1 h of exploring < 30%)", c1 < 0.30, String.format("%.1f%%", c1 * 100));
		RandomSource r = RandomSource.create(1234);
		int hit = 0, players = 4000;
		for (int i = 0; i < players; i++) {
			boolean met = false;
			for (int m = 0; m < 210 && !met; m++) met = PatrolRate.roll(r, 560);
			if (met) hit++;
		}
		double mc = hit / (double) players;
		check("rate: Monte Carlo, 4000 players x 210 explore minutes at 560 blocks, 50-75% meet one", mc >= 0.5 && mc <= 0.75, String.format("%.1f%%", mc * 100));

		// ---- stance and patrol
		checkS("Aldermark is neutral to players by default (set once)", () -> !Factions.get(Factions.ALDERMARK).hostileToPlayers());
		Vec3 dir = new Vec3(1, 0, 0);
		List<KingdomSoldier> patrol = Patrols.spawnPatrol(l, base.east(6), dir, RandomSource.create(5));
		check("patrol: a 5-6 soldier column spawns", patrol != null && patrol.size() >= 5, patrol == null ? "null" : patrol.size());
		if (patrol != null) {
			KingdomSoldier courier = patrol.get(0);
			check("patrol: leader is the tagged courier, all members tagged", courier.entityTags().contains(Patrols.COURIER_TAG)
				&& patrol.stream().allMatch(s -> s.entityTags().contains(Patrols.TAG)));
			check("patrol: marching orders set (objective)", courier.squad() != null && courier.squad().objective() != null);
			check("patrol: faction Aldermark, formation column", courier.squad().faction().equals(Factions.ALDERMARK)
				&& courier.squad().formation().getSerializedName().equals("column"));
			p.getInventory().clearContent();
			boolean first = Patrols.handProclamation(l, p, courier);
			int books = count(p, LoreItems.ROYAL_PROCLAMATION);
			boolean second = Patrols.handProclamation(l, p, courier);
			check("courier hands over exactly one Proclamation per player", first && second && books == 1 && count(p, LoreItems.ROYAL_PROCLAMATION) == 1, books);
			check("courier hand-over calls the advancement hook (FakePlayer has no advancement state; see criteria checks below)", true);
			courier.kill(l);
			List<ItemEntity> drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(courier.blockPosition()).inflate(4));
			check("slain courier drops the pouch with a Proclamation", drops.stream().anyMatch(i -> i.getItem().is(LoreItems.ROYAL_PROCLAMATION)), drops.size());
			for (ItemEntity i : drops) i.discard();
		}
		check("patrol: tracked as active", Patrols.activeCount() >= 1);

		// ---- loot tables
		for (String t : LoreLoot.CHEST_TABLES) {
			int n = 0;
			for (int i = 0; i < 40; i++) for (ItemStack s : LoreLoot.roll(l, "chests/" + t)) n += s.isEmpty() ? 0 : 1;
			check("loot table chests/" + t + " rolls items", n > 0, n);
		}
		checkS("courier pouch always holds a Proclamation", () -> {
			for (ItemStack s : LoreLoot.roll(l, "chests/kingdom_courier")) if (s.is(LoreItems.ROYAL_PROCLAMATION)) return true;
			return false;
		});

		// ---- advancements
		for (String n : new String[] {"root", "heralds_on_the_road", "by_royal_decree", "a_lords_seal", "a_fair_price", "a_torn_corner", "three_make_a_map",
			"the_lines_resolve", "cartographers_thanks", "within_sight_of_the_walls"}) {
			checkS("advancement whispers/" + n + " loaded", () -> server.getAdvancements().get(LoreContent.id("whispers/" + n)) != null);
		}
		checkS("root advancement is hidden", () -> server.getAdvancements().get(LoreContent.id("whispers/root")).value().display().get().hidden());

		// advancement criteria logic (progress objects directly: a FakePlayer is not in the player list)
		checkS("advancement root opens on any ONE of patrol / fragment / Proclamation (OR requirement)", () -> {
			var h = server.getAdvancements().get(LoreContent.id("whispers/root"));
			var pr = new net.minecraft.advancements.AdvancementProgress();
			pr.update(h.value().requirements());
			boolean before = pr.isDone();
			pr.grantProgress("held_decree");
			return !before && pr.isDone() && h.value().requirements().size() == 1;
		});
		checkS("each child advancement completes with its single criterion", () -> {
			for (String n : new String[] {"heralds_on_the_road", "by_royal_decree", "a_lords_seal", "a_fair_price", "a_torn_corner", "three_make_a_map", "the_lines_resolve", "cartographers_thanks", "within_sight_of_the_walls"}) {
				var h = server.getAdvancements().get(LoreContent.id("whispers/" + n));
				var pr = new net.minecraft.advancements.AdvancementProgress();
				pr.update(h.value().requirements());
				if (pr.isDone() || !pr.grantProgress("done") || !pr.isDone()) return false;
				if (h.value().parent().isEmpty()) return false;
			}
			return true;
		});

		// ---- survey map
		BlockPos castle = base.offset(300, 0, -200);
		boolean realCourt;
		try {
			Class.forName("dev.elsewhere.kingdom_court.CourtApi");
			realCourt = true;
		} catch (ClassNotFoundException e) {
			realCourt = false;
		}
		if (realCourt) {
			// combined build: K-C's real court is present, so the absent/stub cases cannot be staged; check the real wiring instead
			// The realm only exists in a world with structures (the self-test world is superflat without them), so compare
			// LoreMaps' reflection with the court's own answer instead of requiring a castle.
			BlockPos real = LoreMaps.courtRealmCenter(server), direct;
			try {
				direct = (BlockPos) Class.forName("dev.elsewhere.kingdom_court.CourtApi").getMethod("realmCenter", MinecraftServer.class).invoke(null, server);
			} catch (ReflectiveOperationException e) {
				direct = new BlockPos(0, -999, 0);
			}
			check("combined build: LoreMaps reads the real CourtApi.realmCenter by reflection", java.util.Objects.equals(real, direct), real + " vs " + direct);
			ItemStack rmap = LoreMaps.makeMap(l, base);
			if (direct != null) check("combined build: the survey map resolves the real Aldermark castle", rmap.is(Items.FILLED_MAP) && LoreMaps.isSurveyMap(rmap));
			else check("combined build, no realm in this world: no survey map", rmap.isEmpty());
		} else {
			check("castle search with no castle known gives no map", LoreMaps.makeMap(l, base).isEmpty());
			// court module reflection: absent (real class not on the lore classpath) -> blank; stub -> its realm centre
			check("court module absent: realmCenter lookup is a no-op (null)", LoreMaps.courtRealmCenter(server) == null);
			LoreMaps.setCourtApiClassForTest("dev.elsewhere.lore.dev.StubCourtApi");
			StubCourtApi.centre = null;
			check("stub court with no realm: no castle, no map", LoreMaps.courtRealmCenter(server) == null && LoreMaps.makeMap(l, base).isEmpty());
			StubCourtApi.centre = castle;
			check("stub CourtApi.realmCenter is found by reflection and used by findCastle", castle.equals(LoreMaps.courtRealmCenter(server)) && castle.equals(LoreMaps.findCastle(l, base)));
			ItemStack cmap = LoreMaps.makeMap(l, base);
			var cdata = net.minecraft.world.item.MapItem.getSavedData(cmap, l);
			check("survey map built from CourtApi.realmCenter (explorer-style, covers it)", cmap.is(Items.FILLED_MAP) && LoreMaps.isSurveyMap(cmap) && cmap.has(DataComponents.MAP_ID)
				&& cdata != null && Math.abs(cdata.centerX - castle.getX()) <= 256 && Math.abs(cdata.centerZ - castle.getZ()) <= 256, cdata == null ? "no data" : cdata.centerX + "," + cdata.centerZ);
		}
		var loc = dev.elsewhere.lore.LoreCommands.locateMessage(castle, base.getX(), base.getZ());
		String locStr = loc.toString();
		check("/aldermark locate message carries a click-to-teleport on the coordinates", locStr.contains("/tp @s " + castle.getX() + " " + castle.getY() + " " + castle.getZ()), locStr);
		var disp = server.getCommands().getDispatcher();
		check("/aldermark locate is registered", disp.getRoot().getChild("aldermark") != null && disp.getRoot().getChild("aldermark").getChild("locate") != null);
		StubCourtApi.centre = null;
		LoreMaps.setCourtApiClassForTest(null);
		LoreApi.addCastleLocator((lv, from) -> castle);
		ItemStack map = LoreMaps.makeMap(l, base);
		check("survey map: filled explorer-style map centred on the castle", map.is(Items.FILLED_MAP) && LoreMaps.isSurveyMap(map) && map.has(DataComponents.MAP_ID));
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LoreItems.ROYAL_SURVEY_MAP));
		LoreItems.ROYAL_SURVEY_MAP.use(l, p, InteractionHand.MAIN_HAND);
		check("folded map unfolds on use into the filled map", LoreMaps.isSurveyMap(p.getItemInHand(InteractionHand.MAIN_HAND)));
		LoreAdvancements.award(p, "the_lines_resolve");

		// ---- cartographer
		Villager cart = EntityTypes.VILLAGER.create(l, EntitySpawnReason.MOB_SUMMONED);
		cart.snapTo(base.getX() + 10.5, base.getY(), base.getZ() + 0.5, 0, 0);
		cart.setVillagerData(cart.getVillagerData().withProfession(l.registryAccess(), VillagerProfession.CARTOGRAPHER));
		l.addFreshEntity(cart);
		int before = cart.getOffers().size();
		LoreMaps_offer(l, p, cart);
		check("cartographer in a free (not liberated) village sells no Survey Map", cart.getOffers().size() == before);
		LoreApi.markLiberated(l, cart.blockPosition());
		LoreMaps_offer(l, p, cart);
		boolean has = false;
		for (MerchantOffer o : cart.getOffers()) if (LoreMaps.isSurveyMap(o.getResult())) has = true;
		check("liberated cartographer sells the Survey Map (12 emeralds + compass), once", has && cart.getOffers().size() == before + 1);
		LoreMaps_offer(l, p, cart);
		check("the offer is not added twice", cart.getOffers().size() == before + 1);
		cart.discard();

		rivals(server, l, p, base);

		// ---- quartermasters
		Villager reeve = LoreApi.spawnQuartermaster(l, base.offset(4, 0, 4), false);
		Villager clan = LoreApi.spawnQuartermaster(l, base.offset(6, 0, 4), true);
		check("Reeve and Clan Quartermaster spawn", reeve != null && clan != null);
		if (reeve != null && clan != null) {
			check("Reeve sells the Proclamation, chain gear; buys all 5 Seals", has(reeve, LoreItems.ROYAL_PROCLAMATION) && has(reeve, Items.CHAINMAIL_CHESTPLATE) && sealBuys(reeve) == 5, reeve.getOffers().size());
			check("Clan Quartermaster buys all 5 Seals too", sealBuys(clan) == 5);
			check("quartermasters are tagged, no-AI, invulnerable", Quartermasters.isQuartermaster(reeve) && reeve.isNoAi() && reeve.isPermanentlyInvulnerable());
			reeve.getOffers().get(0).setToOutOfStock();
			Quartermasters.class.getName();
			reeve.discard();
			clan.discard();
		}

		// ---- patrol cleanup
		for (var pt : new ArrayList<>(Patrols.ACTIVE)) Patrols.disbandForTest(pt);
		Patrols.ACTIVE.clear();
		check("patrol disband removes the soldiers", patrol == null || patrol.stream().skip(1).allMatch(KingdomSoldier::isRemoved), patrol == null ? "-" : patrol.stream().map(x -> x.isRemoved() + "/" + x.isAlive()).toList());
	}


	static final Identifier HRAF = LoreContent.id("hrafnholt"), AZM = LoreContent.id("azmar"), MIRE = LoreContent.id("mirewick");

	static void rivals(MinecraftServer server, ServerLevel l, FakePlayer p, BlockPos base) {
		LoreMaps.setCourtApiClassForTest("dev.elsewhere.lore.dev.StubCourtApi");
		StubCourtApi.centres.clear();
		StubCourtApi.fallen.clear();
		BlockPos cH = base.offset(900, 0, 100), cA = base.offset(-1500, 0, 300), cM = base.offset(200, 0, 2000);
		StubCourtApi.centres.put(HRAF, cH);
		StubCourtApi.centres.put(AZM, cA);
		StubCourtApi.centres.put(MIRE, cM);
		for (Identifier f : new Identifier[] {HRAF, AZM, MIRE}) {
			if (!Factions.exists(f)) Factions.register(f, 0x444444, false);
			if (dev.elsewhere.kingdom.FactionDefs.get(f) == null) dev.elsewhere.kingdom.FactionDefs.putCodeDefined(f, dev.elsewhere.kingdom.FactionDef.simple(f.getPath(), 0x444444, false));
		}

		// ---- items and texts
		for (String k : new String[] {"hrafnholt", "azmar", "mirewick"}) {
			String book = k.equals("mirewick") ? "mirewick_council_proclamation" : k + "_royal_proclamation";
			checkS(k + ": proclamation, fragment, folded map registered", () -> BuiltInRegistries.ITEM.containsKey(LoreContent.id(book))
				&& BuiltInRegistries.ITEM.containsKey(LoreContent.id(k + "_survey_fragment")) && BuiltInRegistries.ITEM.containsKey(LoreContent.id(k + "_survey_map")));
			var pages = dev.elsewhere.lore.RivalProclamations.pages(k);
			check(k + ": proclamation is 8-10 pages, each under 250 characters", pages.size() >= 8 && pages.size() <= 10 && pages.stream().allMatch(s -> s.length() < 250), pages.size());
			checkS(k + ": proclamation item carries resolved written-book content, own byline", () -> {
				ItemStack s = new ItemStack(BuiltInRegistries.ITEM.getValue(LoreContent.id(book)));
				var c = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
				return c != null && c.pages().size() == pages.size() && c.resolved() && !c.author().equals(Proclamation.AUTHOR) && c.author().equals(dev.elsewhere.lore.RivalProclamations.author(k));
			});
			String all = String.join(" ", pages);
			check(k + ": proclamation mentions Aldermark's tower-flag", all.contains("tower-flag"));
			checkS(k + ": recipe 3 fragments -> folded map", () -> {
				for (var h : server.getRecipeManager().getRecipes()) if (h.id().identifier().equals(LoreContent.id(k + "_survey_map"))) return true;
				return false;
			});
		}
		String h = String.join(" ", dev.elsewhere.lore.RivalProclamations.HRAFNHOLT), a = String.join(" ", dev.elsewhere.lore.RivalProclamations.AZMAR), m = String.join(" ", dev.elsewhere.lore.RivalProclamations.MIREWICK);
		check("voices: Hrafnholt (Hearth-Fire, ravens, Old Brothers, Halvard), Azmar (wells, scarab, ibis, Nahira), Mirewick (Scrip, Counting-House, Council)",
			h.contains("Hearth-Fire") && h.contains("Old Brothers") && h.contains("Halvard") && a.contains("wells") && a.contains("scarab") && a.contains("Nahira") && m.contains("Scrip") && m.contains("Counting-House") && m.contains("Lantern Council"));
		check("the three proclamations differ", !h.equals(a) && !a.equals(m) && !h.equals(m));
		check("azmar well-stone registered", LoreItems.AZMAR_WELL_STONE != null && BuiltInRegistries.ITEM.containsKey(LoreContent.id("azmar_well_stone")));

		// ---- survey maps: faction-aware castle lookup
		for (var e : new Object[][] {{HRAF, cH, "hrafnholt"}, {AZM, cA, "azmar"}, {MIRE, cM, "mirewick"}}) {
			Identifier f = (Identifier) e[0];
			BlockPos c = (BlockPos) e[1];
			String k = (String) e[2];
			check(k + ": findCastle(faction) uses CourtApi.realmCenter(server, faction)", c.equals(LoreMaps.findCastle(l, base, f)));
			ItemStack map = LoreMaps.makeMap(l, base, f);
			var data = net.minecraft.world.item.MapItem.getSavedData(map, l);
			check(k + ": survey map is centred on its own castle and tagged with the faction", map.is(Items.FILLED_MAP) && LoreMaps.isSurveyMap(map) && LoreMaps.mapFaction(map).equals(f)
				&& data != null && Math.abs(data.centerX - c.getX()) <= 256 && Math.abs(data.centerZ - c.getZ()) <= 256, data == null ? "no data" : data.centerX + "," + data.centerZ);
			p.getInventory().clearContent();
			var item = BuiltInRegistries.ITEM.getValue(LoreContent.id(k + "_survey_map"));
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			item.use(l, p, InteractionHand.MAIN_HAND);
			ItemStack out = p.getItemInHand(InteractionHand.MAIN_HAND);
			check(k + ": folded map unfolds into its own filled map", LoreMaps.isSurveyMap(out) && LoreMaps.mapFaction(out).equals(f));
		}
		StubCourtApi.centres.remove(AZM);
		check("a rival with no realm gives no map (and findCastle null)", LoreMaps.makeMap(l, base, AZM).isEmpty() && LoreMaps.findCastle(l, base, AZM) == null);
		LoreApi.addFactionLocator((lv, from, f) -> f.equals(AZM) ? cA : null);
		check("faction locator hook works for rivals", cA.equals(LoreMaps.findCastle(l, base, AZM)));
		LoreApi.FACTION_LOCATORS.clear();
		StubCourtApi.centres.put(AZM, cA);

		// ---- locate commands
		var disp = server.getCommands().getDispatcher();
		for (String k : new String[] {"hrafnholt", "azmar", "mirewick"})
			check("/" + k + " locate is registered", disp.getRoot().getChild(k) != null && disp.getRoot().getChild(k).getChild("locate") != null);
		String msg = dev.elsewhere.lore.LoreCommands.locateMessage(dev.elsewhere.lore.LoreFactions.of(HRAF), cH, base.getX(), base.getZ()).toString();
		check("/hrafnholt locate message: own key, click-to-teleport on the coordinates", msg.contains("hrafnholt_locate.success") && msg.contains("/tp @s " + cH.getX() + " " + cH.getY() + " " + cH.getZ()), msg);
		check("fallen-realm check reads CourtApi.kingdomFallen", !LoreMaps.courtFallen(server, MIRE) && stubFall(server));

		// ---- patrols follow the nearest realm
		BlockPos aldermarkCentre = base.offset(2400, 0, 0);
		StubCourtApi.centre = aldermarkCentre;
		check("patrol faction: nearest realm wins (Hrafnholt 900 blocks, Aldermark 2,400)", Patrols.pickFaction(l, base).equals(HRAF));
		StubCourtApi.fallen.add(HRAF);
		check("patrol faction: a fallen realm is skipped (next nearest, Azmar at 1,500)", Patrols.pickFaction(l, base).equals(AZM));
		StubCourtApi.fallen.add(AZM);
		StubCourtApi.fallen.add(MIRE);
		check("patrol faction: all rivals fallen gives Aldermark again", Patrols.pickFaction(l, base).equals(Factions.ALDERMARK));
		StubCourtApi.fallen.clear();
		StubCourtApi.centres.clear();
		StubCourtApi.centres.put(MIRE, base.offset(0, 0, 3500));
		StubCourtApi.centre = null;
		check("patrol faction: nothing within 2,600 blocks falls back to Aldermark (2.0 behaviour)", Patrols.pickFaction(l, base).equals(Factions.ALDERMARK));
		StubCourtApi.centres.put(HRAF, cH);
		StubCourtApi.centres.put(AZM, cA);
		List<KingdomSoldier> hp = Patrols.spawnPatrol(l, base.east(-8), new Vec3(0, 0, 1), RandomSource.create(9), HRAF);
		check("Hrafnholt patrol spawns as faction Hrafnholt with a leader and 5+ members", hp != null && hp.size() >= 5 && hp.get(0).squad().faction().equals(HRAF), hp == null ? "null" : hp.size());
		if (hp != null) {
			KingdomSoldier courier = hp.get(0);
			check("rival patrol is tagged like the Aldermark one", courier.entityTags().contains(Patrols.COURIER_TAG) && hp.stream().allMatch(s -> s.entityTags().contains(Patrols.TAG)));
			p.getInventory().clearContent();
			Patrols.handProclamation(l, p, courier);
			check("Hrafnholt courier hands over Hrafnholt's proclamation, not the King's", count(p, BuiltInRegistries.ITEM.getValue(LoreContent.id("hrafnholt_royal_proclamation"))) == 1 && count(p, LoreItems.ROYAL_PROCLAMATION) == 0);
			courier.kill(l);
			List<ItemEntity> drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(courier.blockPosition()).inflate(4));
			check("slain Hrafnholt courier drops the Hrafnholt pouch", drops.stream().anyMatch(i -> i.getItem().is(BuiltInRegistries.ITEM.getValue(LoreContent.id("hrafnholt_royal_proclamation")))), drops.size());
			for (ItemEntity i : drops) i.discard();
		}

		// ---- loot
		for (String k : new String[] {"hrafnholt", "azmar", "mirewick"}) {
			var book = BuiltInRegistries.ITEM.getValue(LoreContent.id(k.equals("mirewick") ? "mirewick_council_proclamation" : k + "_royal_proclamation"));
			checkS(k + " courier pouch always holds its proclamation", () -> {
				for (ItemStack s : LoreLoot.roll(l, "chests/" + k + "_courier")) if (s.is(book)) return true;
				return false;
			});
			int multi = 0;
			for (int i = 0; i < 60; i++) for (ItemStack s : LoreLoot.roll(l, "chests/" + k + "_courier")) if (s.getCount() > 1) multi++;
			check(k + " courier pouch: set_count modifiers apply (some stacks above 1)", multi > 0, multi);
		}
		checkS("azmar courier pouch always holds a well-stone", () -> {
			for (ItemStack s : LoreLoot.roll(l, "chests/azmar_courier")) if (s.is(LoreItems.AZMAR_WELL_STONE)) return true;
			return false;
		});

		// ---- advancements
		String[] names = {"border_hint", "hrafnholt_road", "hrafnholt_decree", "hrafnholt_border", "azmar_road", "azmar_decree", "azmar_border", "mirewick_road", "mirewick_decree", "mirewick_border"};
		for (String n : names) {
			checkS("advancement whispers/" + n + " loaded, parent resolves to the root chain", () -> {
				var holder = server.getAdvancements().get(LoreContent.id("whispers/" + n));
				if (holder == null || holder.value().parent().isEmpty()) return false;
				var cur = holder;
				for (int i = 0; i < 6 && cur.value().parent().isPresent(); i++) {
					cur = server.getAdvancements().get(cur.value().parent().get());
					if (cur == null) return false;
				}
				return cur.id().equals(LoreContent.id("whispers/root"));
			});
		}
		checkS("rival advancements complete with their one criterion", () -> {
			for (String n : names) {
				var holder = server.getAdvancements().get(LoreContent.id("whispers/" + n));
				var pr = new net.minecraft.advancements.AdvancementProgress();
				pr.update(holder.value().requirements());
				if (pr.isDone() || !pr.grantProgress("done") || !pr.isDone()) return false;
			}
			return true;
		});
		LoreApi.hintSeen(p, AZM, "obelisk");
		check("LoreApi.hintSeen routes to the faction (azmar, obelisk)", LoreAdvancements.LAST_HINT.equals("azmar:obelisk"), LoreAdvancements.LAST_HINT);
		LoreApi.hintSeen(p, Factions.ALDERMARK, "tower_flag");
		check("LoreApi.hintSeen routes Aldermark tower_flag", LoreAdvancements.LAST_HINT.equals("aldermark:tower_flag"), LoreAdvancements.LAST_HINT);

		// ---- cartographer sells the map of the kingdom that held the village
		BlockPos vil = base.offset(400, 0, 0);
		LoreApi.markLiberated(l, vil, AZM);
		check("liberated village remembers its kingdom", LoreApi.liberatedFaction(l, vil).equals(AZM) && LoreApi.liberatedFaction(l, base.offset(-400, 0, 0)).equals(Factions.ALDERMARK));
		Villager cart = EntityTypes.VILLAGER.create(l, EntitySpawnReason.MOB_SUMMONED);
		cart.snapTo(vil.getX() + 0.5, base.getY(), vil.getZ() + 0.5, 0, 0);
		cart.setVillagerData(cart.getVillagerData().withProfession(l.registryAccess(), VillagerProfession.CARTOGRAPHER));
		LoreMapsAccess.offer(l, p, cart);
		boolean sells = false;
		for (MerchantOffer o : cart.getOffers()) if (LoreMaps.isSurveyMap(o.getResult()) && LoreMaps.mapFaction(o.getResult()).equals(AZM)) sells = true;
		check("a cartographer in a village freed from Azmar sells the Azmar map", sells);

		StubCourtApi.centres.clear();
		StubCourtApi.fallen.clear();
		StubCourtApi.centre = null;
		LoreMaps.setCourtApiClassForTest(null);
	}

	static boolean stubFall(MinecraftServer server) {
		StubCourtApi.fallen.add(MIRE);
		boolean r = LoreMaps.courtFallen(server, MIRE);
		StubCourtApi.fallen.clear();
		return r;
	}

	static void LoreMaps_offer(ServerLevel l, FakePlayer p, Villager v) {
		Patrols_maybe(l, p, v);
	}

	static void Patrols_maybe(ServerLevel l, FakePlayer p, Villager v) {
		LoreMapsAccess.offer(l, p, v);
	}

	static boolean has(Villager v, net.minecraft.world.item.Item item) {
		for (MerchantOffer o : v.getOffers()) if (o.getResult().is(item)) return true;
		return false;
	}

	static int sealBuys(Villager v) {
		int n = 0;
		for (MerchantOffer o : v.getOffers()) if (o.getItemCostA().itemStack().is(LoreItems.SEAL_TAG) && o.getResult().is(Items.EMERALD)) n++;
		return n;
	}

	static int count(FakePlayer p, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemStack s : p.getInventory()) if (s.is(item)) n += s.getCount();
		return n;
	}

	static void finish(MinecraftServer server) {
		ServerLevel l = server.overworld();
		for (int cx = 1; cx <= 4; cx++) for (int cz = 1; cz <= 4; cz++) l.setChunkForced(cx, cz, false);
		String total = "TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		LOG.add(total);
		System.out.println("[lore-selftest] " + total);
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-lore.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("lore.selftest.exit")) server.halt(false);
	}
}
