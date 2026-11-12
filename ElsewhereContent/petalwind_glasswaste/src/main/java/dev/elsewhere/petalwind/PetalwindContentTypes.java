package dev.elsewhere.petalwind;

import dev.elsewhere.petalwind_glasswaste.Reg;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Petalwind block entity types. */
public final class PetalwindContentTypes {
	private PetalwindContentTypes() {}

	public static BlockEntityType<UpdraftVentBlockEntity> UPDRAFT_VENT;

	public static void init() {
		UPDRAFT_VENT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Reg.id("updraft_vent"),
			FabricBlockEntityTypeBuilder.create(UpdraftVentBlockEntity::new, PetalwindBlocks.UPDRAFT_VENT).build());
	}
}
