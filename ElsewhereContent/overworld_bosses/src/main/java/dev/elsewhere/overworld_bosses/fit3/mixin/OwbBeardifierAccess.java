package dev.elsewhere.overworld_bosses.fit3.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 3.0 owb fit: read access to a Beardifier so the boss sites' fit pieces can add their own per-piece rules (see OwbBeards). */
@Mixin(Beardifier.class)
public interface OwbBeardifierAccess {
	@Accessor("pieces")
	List<Beardifier.Rigid> elsewhereOwbFit$pieces();

	@Accessor("junctions")
	List<JigsawJunction> elsewhereOwbFit$junctions();

	@Accessor("affectedBox")
	BoundingBox elsewhereOwbFit$affectedBox();
}
