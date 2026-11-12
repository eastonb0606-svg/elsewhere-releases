package dev.elsewhere.greenhaze_hush.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 3.0 greenhaze_hush fit: read access to a Beardifier so new-style pieces can add their own rules (see GhBeards). Unique names per module. */
@Mixin(Beardifier.class)
public interface GhBeardifierAccessor {
	@Accessor("pieces")
	List<Beardifier.Rigid> elsewhereGhFit$pieces();

	@Accessor("junctions")
	List<JigsawJunction> elsewhereGhFit$junctions();

	@Accessor("affectedBox")
	BoundingBox elsewhereGhFit$affectedBox();
}
