package dev.elsewhere.petalwind_glasswaste;

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
public final class MapTrades {
	private MapTrades() {}

	public static @Nullable Pair<BlockPos, Holder<Structure>> find(ServerLevel target, List<Identifier> ids, BlockPos from) {
		var reg = target.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> hs = new ArrayList<>();
		for (Identifier id : ids) reg.get(ResourceKey.create(Registries.STRUCTURE, id)).ifPresent(h -> hs.add(h));
		if (hs.isEmpty()) return null;
		return target.getChunkSource().getGenerator().findNearestMapStructure(target, HolderSet.direct(hs), from, 100, false);
	}

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

	/** A trade for the map: {@code emeralds} + a compass (as in vanilla), 2 uses per restock. Empty when the structure can't be found. */
	public static Optional<MerchantOffer> offer(ServerLevel own, BlockPos from, ResourceKey<Level> dim, String structurePath, String label, int emeralds) {
		ServerLevel target = own.getServer().getLevel(dim);
		if (target == null) return Optional.empty();
		ItemStack map = makeMap(target, Reg.id(structurePath), target == own ? from : BlockPos.ZERO, Component.literal("Explorer's Map: " + label));
		if (map == null) return Optional.empty();
		return Optional.of(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.of(new ItemCost(Items.COMPASS)), map, 2, 12, 0.05F));
	}
}
