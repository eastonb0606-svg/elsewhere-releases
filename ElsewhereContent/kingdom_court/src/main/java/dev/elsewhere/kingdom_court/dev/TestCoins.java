package dev.elsewhere.kingdom_court.dev;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The currencies of the self-test factions ("Scrip" and "Dinar" in the check names). They are vanilla items, so they exist in every build the
 * self-test runs in: the lane's dev classpath and the merged 3.0 jar. (Until 3.0 they were {@code elsewhere:test_scrip}/{@code test_dinar},
 * registered only by the lane's DevEntry; the merge drops DevEntry, so every currency check failed in the merged jar.) Neither item is a real
 * faction's currency, a barter good, a shop line or a drop of any court test, and they differ from the kingdom TestFactions' gold nugget.
 */
final class TestCoins {
	private TestCoins() {}

	/** Currency of the Scrip-paying test factions. */
	static final String SCRIP = "minecraft:prismarine_shard";
	/** Currency of the Dinar-paying test factions. */
	static final String DINAR = "minecraft:prismarine_crystals";

	static Item scrip() {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(SCRIP));
	}

	static Item dinar() {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(DINAR));
	}
}
