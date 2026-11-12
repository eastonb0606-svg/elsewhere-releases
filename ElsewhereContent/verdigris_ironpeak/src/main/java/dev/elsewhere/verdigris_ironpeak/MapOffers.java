package dev.elsewhere.verdigris_ironpeak;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/** Explorer-style maps for survival players (/locate is operator-only): vanilla map mechanics, pointing at a boss-site structure. */
public final class MapOffers {
	private MapOffers() {}

	/** Nearest structure with one of {@code ids} (searched from {@code from}, 100 chunks), or null. */
	public static @Nullable Pair<BlockPos, Holder<Structure>> find(ServerLevel target, List<Identifier> ids, BlockPos from) {
		var reg = target.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> hs = new ArrayList<>();
		for (Identifier id : ids) reg.get(ResourceKey.create(Registries.STRUCTURE, id)).ifPresent(h -> hs.add(h));
		if (hs.isEmpty()) return null;
		return target.getChunkSource().getGenerator().findNearestMapStructure(target, HolderSet.direct(hs), from, 100, false);
	}

	/** A filled map of the nearest {@code structureId} in {@code target}, marked with a red X, or null when none is found. */
	public static @Nullable ItemStack makeMap(ServerLevel target, Identifier structureId, BlockPos from, Component name) {
		Pair<BlockPos, Holder<Structure>> r = find(target, List.of(structureId), from);
		if (r == null) return null;
		BlockPos p = r.getFirst();
		ItemStack map = MapItem.create(target, p.getX(), p.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(target, map);
		MapItemSavedData.addTargetDecoration(map, p, "+", MapDecorationTypes.TARGET_X);
		map.set(DataComponents.ITEM_NAME, name);
		return map;
	}

	/**
	 * One map offer from {@code trader}: cached per trader and structure. {@code dim} is the level the structure is in
	 * (the trader's own, the Nether or the End), {@code emeralds} the price (plus a compass, as in vanilla).
	 */
	public static Optional<MerchantOffer> offer(ElsewhereTrader trader, ResourceKey<Level> dim, String structurePath, String label, int emeralds) {
		if (trader == null || !(trader.level() instanceof ServerLevel own)) return Optional.empty();
		MinecraftServer server = own.getServer();
		ServerLevel target = server.getLevel(dim);
		if (target == null) return Optional.empty();
		Identifier id = Reg.id(structurePath);
		ItemStack map = trader.mapCache.get(dim.identifier() + "/" + structurePath);
		if (map == null) {
			BlockPos from = target == own ? trader.blockPosition() : BlockPos.ZERO;
			map = makeMap(target, id, from, Component.literal("Explorer's Map: " + label));
			if (map == null) return Optional.empty();
			trader.mapCache.put(dim.identifier() + "/" + structurePath, map.copy());
		}
		return Optional.of(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.of(new ItemCost(Items.COMPASS)), map.copy(), 2, 12, 0.05F));
	}
}
