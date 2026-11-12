package dev.elsewhere.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 3.0 core structure fit: read access to a Beardifier so fit pieces can add their own per-piece beards (see CoreFit.beards). */
@Mixin(Beardifier.class)
public interface CoreFitBeardifierAccessor {
	@Accessor("pieces")
	List<Beardifier.Rigid> elsewhereCoreFit$pieces();

	@Accessor("junctions")
	List<JigsawJunction> elsewhereCoreFit$junctions();

	@Accessor("affectedBox")
	BoundingBox elsewhereCoreFit$affectedBox();
}
