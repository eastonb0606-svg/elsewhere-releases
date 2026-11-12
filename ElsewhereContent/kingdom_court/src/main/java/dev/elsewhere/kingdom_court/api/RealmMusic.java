package dev.elsewhere.kingdom_court.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

/**
 * Realm-music hook. The court plays a faction's realm theme to a player who walks within 112 blocks of its castle (looping every 4 minutes, stopped
 * when they leave) and a leader's theme in the fight. Three ways in, strongest first:
 * <ol>
 * <li>{@link #ENTER} / {@link #LEAVE} events: return true from ENTER to say "I play it myself" (the boss_music lane does this once it has tracks): the court then plays nothing;</li>
 * <li>{@link #registerRealm} / {@link #registerTheme}: your own {@code SoundEvent}s;</li>
 * <li>the faction file's {@code music} prefix: sound events {@code <prefix>.realm} and {@code <prefix>.<slot>} (Aldermark keeps {@code elsewhere:court_music.*}).</li>
 * </ol>
 */
public final class RealmMusic {
	private RealmMusic() {}

	public interface Enter { boolean onEnter(ServerPlayer player, Identifier faction); }
	public interface Leave { void onLeave(ServerPlayer player, Identifier faction); }

	/** A player came into a realm (or the 4-minute loop is due). Return true when your handler plays the music. */
	public static final Event<Enter> ENTER = EventFactory.createArrayBacked(Enter.class, ls -> (p, f) -> {
		boolean handled = false;
		for (Enter x : ls) handled |= x.onEnter(p, f);
		return handled;
	});
	public static final Event<Leave> LEAVE = EventFactory.createArrayBacked(Leave.class, ls -> (p, f) -> { for (Leave x : ls) x.onLeave(p, f); });

	static final Map<String, SoundEvent> REALM = new ConcurrentHashMap<>();
	static final Map<String, SoundEvent> THEMES = new ConcurrentHashMap<>();

	public static void registerRealm(Identifier faction, SoundEvent sound) {
		REALM.put(faction.toString(), sound);
	}

	/** The theme played while {@code slot} (a leader slot key such as "king") of the faction fights. */
	public static void registerTheme(Identifier faction, String slot, SoundEvent sound) {
		THEMES.put(faction + "|" + slot, sound);
	}

	public static @Nullable SoundEvent realm(Identifier faction) {
		return REALM.get(faction.toString());
	}

	public static @Nullable SoundEvent theme(Identifier faction, String slot) {
		return THEMES.get(faction + "|" + slot);
	}
}
