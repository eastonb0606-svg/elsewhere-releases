package dev.elsewhere.lore;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/** Survey Map logic: locating the castle, building the explorer-style filled map, and the liberated cartographer's offer. */
public final class LoreMaps {
	private LoreMaps() {}

	public static final TagKey<Structure> CASTLE_TAG = TagKey.create(Registries.STRUCTURE, LoreContent.id("on_royal_survey_maps"));
	public static final String MARKER = "lore_survey_map";
	public static final String OFFER_TAG = "lore_survey_offer";
	/** Search radius in chunks (4096 blocks): the realm is 1,200-2,500 blocks out, one per 6,000-block square. */
	public static final int SEARCH_CHUNKS = 256;

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && entity instanceof Villager v) maybeOfferMap(sl, sp, v);
			return InteractionResult.PASS;
		});
	}

	/** Castle of {@code faction} nearest {@code from}. Aldermark keeps the 2.0 path; rivals use faction locators, then CourtApi. */
	public static @Nullable BlockPos findCastle(ServerLevel level, BlockPos from, @Nullable Identifier faction) {
		if (faction == null || faction.equals(LoreFactions.ALDERMARK)) return findCastle(level, from);
		BlockPos best = null;
		for (LoreApi.FactionLocator f : LoreApi.FACTION_LOCATORS) {
			BlockPos p;
			try {
				p = f.find(level, from, faction);
			} catch (RuntimeException e) {
				continue;
			}
			if (p != null && (best == null || p.distSqr(from) < best.distSqr(from))) best = p;
		}
		if (best != null) return best;
		return courtRealmCenter(level.getServer(), faction);
	}

	public static @Nullable BlockPos findCastle(ServerLevel level, BlockPos from) {
		BlockPos best = null;
		for (BiFunction<ServerLevel, BlockPos, BlockPos> f : LoreApi.CASTLE_LOCATORS) {
			BlockPos p;
			try {
				p = f.apply(level, from);
			} catch (RuntimeException e) {
				continue;
			}
			if (p != null && (best == null || p.distSqr(from) < best.distSqr(from))) best = p;
		}
		if (best != null) return best;
		// The castle uses custom placement: findNearestMapStructure and /locate return null for it. Ask the court module.
		return courtRealmCenter(level.getServer());
	}

	/** Court API class, resolved by reflection (module may be absent). Replaced only by the self-test stub. */
	static volatile String courtApiClass = "dev.elsewhere.kingdom_court.CourtApi";
	private static String resolvedFor;
	private static java.lang.reflect.Method realmCenterMethod;

	/** {@code CourtApi.realmCenter(server)} by guarded reflection; null if the court module is absent, broken, or the world has no realm. */
	public static synchronized @Nullable BlockPos courtRealmCenter(MinecraftServer server) {
		String name = courtApiClass;
		try {
			if (!name.equals(resolvedFor)) {
				resolvedFor = name;
				realmCenterMethod = null;
				realmCenterMethod = Class.forName(name).getMethod("realmCenter", MinecraftServer.class);
			}
			if (realmCenterMethod == null) return null;
			return (BlockPos) realmCenterMethod.invoke(null, server);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			LoreContent.LOG.warn("CourtApi.realmCenter lookup failed: {}", e.toString());
			return null;
		}
	}

	private static String resolvedFor2;
	private static java.lang.reflect.Method realmCenter2, fallen2;

	private static synchronized void resolve2() throws ReflectiveOperationException {
		String name = courtApiClass;
		if (name.equals(resolvedFor2)) return;
		resolvedFor2 = name;
		realmCenter2 = null;
		fallen2 = null;
		Class<?> c = Class.forName(name);
		realmCenter2 = c.getMethod("realmCenter", MinecraftServer.class, Identifier.class);
		try {
			fallen2 = c.getMethod("kingdomFallen", MinecraftServer.class, Identifier.class);
		} catch (NoSuchMethodException e) {
			fallen2 = null;
		}
	}

	/** {@code CourtApi.realmCenter(server, faction)} by guarded reflection; null when absent, unplaced or failing. */
	public static synchronized @Nullable BlockPos courtRealmCenter(MinecraftServer server, Identifier faction) {
		if (faction.equals(LoreFactions.ALDERMARK)) return courtRealmCenter(server);
		try {
			resolve2();
			return realmCenter2 == null ? null : (BlockPos) realmCenter2.invoke(null, server, faction);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			LoreContent.LOG.warn("CourtApi.realmCenter(faction) lookup failed: {}", e.toString());
			return null;
		}
	}

	/** {@code CourtApi.kingdomFallen(server, faction)}; false when the court module is absent. */
	public static synchronized boolean courtFallen(MinecraftServer server, Identifier faction) {
		try {
			resolve2();
			return fallen2 != null && (Boolean) fallen2.invoke(null, server, faction);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** Self-test only: point the reflection at a stub class (null restores the real CourtApi). */
	public static synchronized void setCourtApiClassForTest(@Nullable String cls) {
		courtApiClass = cls == null ? "dev.elsewhere.kingdom_court.CourtApi" : cls;
		resolvedFor2 = null;
	}

	/** The filled Royal Survey Map centred on the castle nearest {@code from}; EMPTY if none is known. */
	public static ItemStack makeMap(ServerLevel level, BlockPos from) {
		return makeMap(level, from, LoreFactions.ALDERMARK);
	}

	/** The filled Survey Map centred on {@code faction}'s castle nearest {@code from}; EMPTY if none is known. */
	public static ItemStack makeMap(ServerLevel level, BlockPos from, Identifier faction) {
		BlockPos castle = findCastle(level, from, faction);
		if (castle == null) return ItemStack.EMPTY;
		LoreFactions.Lore lf = LoreFactions.of(faction);
		ItemStack map = MapItem.create(level, castle.getX(), castle.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, castle, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable(lf.rival() ? "item.elsewhere." + lf.key + "_survey_map_filled" : "item.elsewhere.lore_royal_survey_map_filled"));
		CustomData.update(DataComponents.CUSTOM_DATA, map, t -> {
			t.putBoolean(MARKER, true);
			t.putString("lore_faction", lf.id.toString());
		});
		return map;
	}

	/** Gives a liberated village's cartographer the Survey Map offer, once. */
	static void maybeOfferMap(ServerLevel level, ServerPlayer player, Villager v) {
		if (!LoreConfig.cartographerSellsMap) return;
		if (!v.getVillagerData().profession().is(VillagerProfession.CARTOGRAPHER)) return;
		if (v.entityTags().contains(OFFER_TAG)) return;
		if (!LoreApi.isLiberated(level, v.blockPosition())) return;
		Identifier fac = LoreApi.liberatedFaction(level, v.blockPosition());
		ItemStack map = makeMap(level, v.blockPosition(), fac);
		if (map.isEmpty()) return;
		v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 12), Optional.of(new ItemCost(Items.COMPASS, 1)), map, 2, 15, 0.05F));
		v.addTag(OFFER_TAG);
		LoreAdvancements.award(player, "cartographers_thanks");
	}

	public static void offerForTest(ServerLevel l, ServerPlayer p, Villager v) {
		maybeOfferMap(l, p, v);
	}

	public static boolean isSurveyMap(ItemStack s) {
		return s.is(Items.FILLED_MAP) && s.has(DataComponents.CUSTOM_DATA) && s.get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr(MARKER, false);
	}

	/** Which kingdom's castle a Survey Map stack points at (Aldermark for old maps). */
	public static Identifier mapFaction(ItemStack s) {
		if (!isSurveyMap(s)) return LoreFactions.ALDERMARK;
		return LoreFactions.fromString(s.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr("lore_faction", "")).id;
	}
}
