package dev.elsewhere.core.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where a player stood before their last trip into an Elsewhere dimension. Saved on the player, survives death. */
public record ReturnPoint(ResourceKey<Level> dimension, Vec3 pos, float yRot) {
	public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
		Vec3.CODEC.fieldOf("pos").forGetter(ReturnPoint::pos),
		Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(ReturnPoint::yRot)
	).apply(i, ReturnPoint::new));
}
