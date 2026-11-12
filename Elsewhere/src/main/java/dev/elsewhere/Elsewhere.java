package dev.elsewhere;

import dev.elsewhere.command.ElsewhereCommands;
import dev.elsewhere.core.boss.BossEvents;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.core.trip.AccidentalTrips;
import dev.elsewhere.core.wayfinder.Attunements;
import dev.elsewhere.palehollow.PaleHollow;
import dev.elsewhere.registry.ModAttachments;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModItems;
import dev.elsewhere.registry.ModMisc;
import dev.elsewhere.registry.ModParticles;
import dev.elsewhere.registry.ModSounds;
import dev.elsewhere.registry.ModTabs;
import dev.elsewhere.test.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint. Registers the core (Wayfinders, rifts, trips, boss framework) and the Pale Hollow content. */
public final class Elsewhere implements ModInitializer {
	public static final String MOD_ID = "elsewhere";
	public static final Logger LOG = LoggerFactory.getLogger("Elsewhere");

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ElsewhereConfig.load();
		ModSounds.init();
		ModParticles.init();
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		ModAttachments.init();
		ModMisc.init();
		ModTabs.init();

		Attunements.init();
		dev.elsewhere.core.gear.ArmorSets.init();
		RiftTravel.init();
		ReturnRifts.init();
		AccidentalTrips.init();
		BossEvents.init();
		PaleHollow.init();
		ElsewhereCommands.init();
		// 2.0 core lane
		dev.elsewhere.boss.kit.BossKit.init(); // idempotent; modules may call it too
		dev.elsewhere.tiergear.TierGear.init();
		dev.elsewhere.core.gear.GearRules.init();
		dev.elsewhere.palehollow.PaleHollowGear.init();
		dev.elsewhere.core.travel.Anchors.init();
		dev.elsewhere.core.atlas.Atlas.init();
		dev.elsewhere.core.trophy.Trophies.init();
		dev.elsewhere.v2.V2Content.init();

		if (Boolean.getBoolean("elsewhere.selftest")) {
			SelfTest.register();
		}
		LOG.info("Elsewhere {} ready: Pale Hollow, Mother Creak, Selenne", "0.1.0");
	}
}
