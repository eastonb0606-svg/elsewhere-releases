package dev.elsewhere.overworld_bosses.world;

import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Structure types and piece types for the four Overworld boss sites. The structures themselves, their biome tags and
 * structure sets are data: {@code data/elsewhere/worldgen/structure/*.json}, {@code worldgen/structure_set/*.json},
 * {@code tags/worldgen/biome/has_structure/*.json}.
 */
public final class OwbWorldgen {
	private static final Map<String, StructureType<OwbStructure>> TYPES = new HashMap<>();
	private static final Map<String, StructurePieceType> PIECES = new HashMap<>();
	public static final String[] KINDS = {OwbPiece.CROAKING_SHRINE, OwbPiece.SUN_CHAMBER, OwbPiece.GLACIER_SHELF, OwbPiece.WRECK_RING};

	private OwbWorldgen() {}

	public static void init() {
		dev.elsewhere.overworld_bosses.fit3.OwbFit.init(); // 3.0 fit piece type elsewhere:owb_fit (s-struct-overworld)
		for (String kind : KINDS) {
			// Each registry value must be a distinct object (non-capturing lambdas are shared singletons).
			if (!BuiltInRegistries.STRUCTURE_TYPE.containsKey(BossRegistry.id(kind))) {
				StructureType<OwbStructure> type = new StructureType<>() {
					@Override
					public com.mojang.serialization.MapCodec<OwbStructure> codec() {
						return OwbStructure.CODEC;
					}
				};
				TYPES.put(kind, Registry.register(BuiltInRegistries.STRUCTURE_TYPE, BossRegistry.id(kind), type));
			}
			if (!BuiltInRegistries.STRUCTURE_PIECE.containsKey(BossRegistry.id(kind))) {
				final String k = kind;
				StructurePieceType pt = (ctx, tag) -> new OwbPiece(tag.getStringOr("Kind", k).isEmpty() ? tag : tag);
				PIECES.put(kind, Registry.register(BuiltInRegistries.STRUCTURE_PIECE, BossRegistry.id(kind), pt));
			}
		}
	}

	public static StructureType<?> typeOf(String kind) {
		return TYPES.get(kind);
	}

	public static StructurePieceType pieceTypeOf(String kind) {
		return PIECES.get(kind);
	}

	public static ResourceKey<Structure> key(String kind) {
		return ResourceKey.create(Registries.STRUCTURE, BossRegistry.id(kind));
	}
}
