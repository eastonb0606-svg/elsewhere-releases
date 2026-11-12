package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Kingdom music (Kevin MacLeod, CC BY 4.0): one theme per ruler plus the realm ambient. Sound ids {@code elsewhere:court_music.<key>}. */
public final class CourtMusic {
	private CourtMusic() {}

	public static final Map<Ruler, SoundEvent> THEMES = new EnumMap<>(Ruler.class);
	public static SoundEvent REALM;

	static SoundEvent reg(String key) {
		var id = CourtContent.id("court_music." + key);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** The faction's theme for a ruler slot: {@code <music prefix>.<slot>} from its file, falling back to Aldermark's. */
	public static SoundEvent theme(net.minecraft.resources.Identifier faction, Ruler r) {
		SoundEvent e = dev.elsewhere.kingdom_court.api.RealmMusic.theme(faction, r.key);
		if (e == null) e = lookup(faction, r.key);
		return e != null ? e : THEMES.get(r);
	}

	public static SoundEvent realm(net.minecraft.resources.Identifier faction) {
		SoundEvent e = dev.elsewhere.kingdom_court.api.RealmMusic.realm(faction);
		if (e == null) e = lookup(faction, "realm");
		return e != null ? e : REALM;
	}

	private static SoundEvent lookup(net.minecraft.resources.Identifier faction, String key) {
		var d = dev.elsewhere.kingdom.Factions.def(faction);
		if (d == null || d.music().isEmpty() || dev.elsewhere.kingdom.Factions.ALDERMARK.equals(faction)) return null;
		var id = net.minecraft.resources.Identifier.tryParse(d.music().get() + "." + key);
		return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id) ? BuiltInRegistries.SOUND_EVENT.getValue(id) : null;
	}

	static void init() {
		for (Ruler r : Ruler.values()) THEMES.put(r, reg(r.key));
		REALM = reg("realm");
		// 2.1: realm themes of the three rivals (240 s tracks; the sounds.json entries live in boss_music)
		for (String f : new String[] {"hrafnholt", "azmar", "mirewick"}) {
			var id = Identifier.fromNamespaceAndPath("elsewhere", f + "_music.realm");
			SoundEvent e = BuiltInRegistries.SOUND_EVENT.containsKey(id) ? BuiltInRegistries.SOUND_EVENT.getValue(id) : Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
			dev.elsewhere.kingdom_court.api.RealmMusic.registerRealm(Identifier.fromNamespaceAndPath("elsewhere", f), e);
		}
	}
}
