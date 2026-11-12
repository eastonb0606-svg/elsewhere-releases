package dev.elsewhere.boss_music.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the realm guard fade a realm track it did not create. */
@Mixin(AbstractSoundInstance.class)
public interface SoundVolumeAccessor {
	@Accessor("volume")
	float elsewhere$getVolume();

	@Accessor("volume")
	void elsewhere$setVolume(float v);
}
