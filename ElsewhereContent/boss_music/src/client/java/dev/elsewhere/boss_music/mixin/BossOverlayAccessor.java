package dev.elsewhere.boss_music.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * P12: the boss bars on screen (a private field of the overlay), read by {@code BossMusicService} to tell a fighting boss
 * from one standing by. Replaces the old reflection on the field, which would break silently on a mapping change; an
 * accessor fails loudly at startup instead. boss_ui's injector mixin on the same class is unaffected.
 */
@Mixin(BossHealthOverlay.class)
public interface BossOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> elsewhere$getEvents();
}
