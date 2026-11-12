package dev.elsewhere.gear3;

import net.minecraft.resources.Identifier;

/**
 * Elsewhere 3.0 new items (GEAR.md catalogue). Everything is driven by one data file, {@code /elsewhere_gear3/catalogue.json},
 * which the Python generators in {@code gear3/tools} also read (models, lang, recipes, tags, JEI lines) and which the
 * self-test checks against. Merge entry: {@link #init()}. Kill switch: exclude the module "gear3" in modules.json.
 */
public final class Gear3Content {
	private Gear3Content() {}

	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("elsewhere", path);
	}

	public static void init() {
		if (done) return;
		done = true;
		dev.elsewhere.arsenal.ArsenalAttributes.init(); // idempotent; our attrs refer to arsenal's attributes
		Gear3Catalogue.load();
		dev.elsewhere.gear3.s3.miresnare.MireSnare.init(); // s-miresnare: block + block entity + Snared effect + "mire_snare" factory (before Gear3Items)
		dev.elsewhere.gear3.s3.pennant.Pennant.init(); // s-pennant: entity type elsewhere:herdcaller_standard + "pennant" factory (before Gear3Items)
		dev.elsewhere.gear3.s3.hurlbat.Hurlbats.putFactory(); // s-hurlbat: "hurlbat" mechanic -> HurlbatItem (before Gear3Items)
		dev.elsewhere.gear3.s3.sunglass.SunglassArrows.init(); // s-sunglass: entity type elsewhere:sunglass_arrow + "sunglass_arrow" factory with dispenser behaviour (before Gear3Items)
		dev.elsewhere.gear3.s3.starpiercer.Starpiercer.register(); // s-starpiercer: entity type elsewhere:starpiercer_arrow, 4 sound events, "starpiercer" factory, Aphelion drop safety (before Gear3Items)
		dev.elsewhere.gear3.s3.reliquary.EchoReliquary.init(); // s-reliquary: "echo_reliquary" factory + entity type elsewhere:echo_knight, 2-slot weight, Mute Archivist offer hook (before Gear3Items)
		dev.elsewhere.gear3.s3.mothlight.Mothlight.init(); // s-mothlight: "mothlight_staff" factory, entity type elsewhere:mothlight_swarm + attributes, Mothdust effect (before Gear3Items)
		dev.elsewhere.gear3.s3.willo.WillOLantern.init(); // s-willolantern: "will_o_lantern" factory, entity type elsewhere:marsh_wisp, wisp-light chunk record, ON_CAST memory, Chair-holder drop (before Gear3Items)
		dev.elsewhere.gear3.s3.ascension.Ascension.init(); // s-ascension: "ascension_shard" factory, component elsewhere:gear3_ascended, smithing serializer elsewhere:gear3_ascension, boss/ruler drops, set bonus II predicate (before Gear3Items)
		Gear3Items.init();
		Gear3Mechanics.init();
		Gear3LightBlocks.init(); // chunk attachment type + chunk-load hook (self-healing Glimmer Lantern lights)
		Gear3Loot.init();
		dev.elsewhere.gear3.s3.echoarrow.EchoArrows.init(); // s-echoarrow: entity type elsewhere:echo_arrow + dispenser behaviour (after Gear3Items)
		dev.elsewhere.gear3.s3.galesling.GaleSling.init(); // s-galesling: entity type elsewhere:gale_sling_stone + 3 sound events
		dev.elsewhere.gear3.s3.wolfcall.Wolfcall.init(); // s-wolfcall: entity type elsewhere:hrafnholt_warg + attributes, 7 sound events, Gudrun/Wolf-Handler drops
		dev.elsewhere.gear3.setbonus.a.SetBonusesA.init(); // s-setbonus-a: SetBonus registry/events (SetBonuses.init) + 7 set/pair bonuses
		dev.elsewhere.gear3.s3.whips.Gear3Whips.init(); // s-whips: 4 whip crack sound events + mark events (minion hit, death, logout); kill switch overhaul3 key whip_mechanics
		dev.elsewhere.gear3.s3.harpoon.HarpoonBolts.init(); // s-harpoon: entity type elsewhere:tidebrass_harpoon_bolt + dispenser behaviour (after Gear3Items)
		dev.elsewhere.gear3.s3.hurlbat.Hurlbats.init(); // s-hurlbat: entity type elsewhere:hrafnholt_hurlbat, 4 sound events, Huntmaster drop (after Gear3Items)
		dev.elsewhere.gear3.setbonus.b.SetBonusesB.init(); // s-setbonus-b: Command, Outrider, Steady Aim, Starsight, Zenith, Wolf-Fury (after SetBonusesA and Gear3Whips)
		dev.elsewhere.gear3.s3.totems.Totems.init(); // s-totems: entity types elsewhere:bramble_totem / clockwork_sentry / azmar_sunglass_obelisk + attributes, Budtender offer hook; kill switch overhaul3 key gear3_totems
	}
}
