package dev.elsewhere.stub.overworld_bosses.client;

import dev.elsewhere.stub.overworld_bosses.client.mixin.BossHealthOverlayAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the boss music hook. A boss bar whose name is the translatable entity name
 * {@code entity.elsewhere.<boss>} selects the sound event {@code elsewhere:music.boss.<boss>} (registered by the
 * module). The track overrides ambient music and stops when the bar goes (win, reset or leaving). STUB.
 */
public final class BossMusic {
	private static final Map<String, Music> CACHE = new HashMap<>();

	private BossMusic() {}

	public static @Nullable Music current(Minecraft mc) {
		if (mc.player == null || mc.gui == null || mc.gui.hud == null) return null;
		Map<java.util.UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).elsewhere$events();
		for (LerpingBossEvent e : events.values()) {
			if (e.getName().getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("entity.elsewhere.")) {
				String boss = tc.getKey().substring("entity.elsewhere.".length());
				Music m = CACHE.computeIfAbsent(boss, BossMusic::lookup);
				if (m != null) return m;
			}
		}
		return null;
	}

	private static @Nullable Music lookup(String boss) {
		Identifier id = Identifier.fromNamespaceAndPath("elsewhere", "music.boss." + boss);
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.get(id).map(h -> (Holder<SoundEvent>) h).orElse(null);
		return holder == null ? null : new Music(holder, 0, 0, true);
	}
}
