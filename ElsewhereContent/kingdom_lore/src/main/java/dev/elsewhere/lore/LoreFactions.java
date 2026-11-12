package dev.elsewhere.lore;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** The four kingdoms' lore identities: ids, short keys, and the items each one owns (filled in by {@link LoreItems}). */
public final class LoreFactions {
	private LoreFactions() {}

	public static final Identifier ALDERMARK = LoreContent.id("aldermark"), HRAFNHOLT = LoreContent.id("hrafnholt"),
		AZMAR = LoreContent.id("azmar"), MIREWICK = LoreContent.id("mirewick");

	public static final class Lore {
		public final Identifier id;
		public final String key;
		public Item proclamation, fragment, map;

		Lore(Identifier id, String key) {
			this.id = id;
			this.key = key;
		}

		public boolean rival() {
			return !id.equals(ALDERMARK);
		}
	}

	public static final Map<Identifier, Lore> ALL = new LinkedHashMap<>();

	static {
		for (Identifier id : new Identifier[] {ALDERMARK, HRAFNHOLT, AZMAR, MIREWICK}) ALL.put(id, new Lore(id, id.getPath()));
	}

	/** The lore record for a faction id; anything unknown reads as Aldermark (the 2.0 behaviour). */
	public static Lore of(@Nullable Identifier id) {
		Lore l = id == null ? null : ALL.get(id);
		return l != null ? l : ALL.get(ALDERMARK);
	}

	public static @Nullable Lore exact(@Nullable Identifier id) {
		return id == null ? null : ALL.get(id);
	}

	public static Lore fromString(@Nullable String s) {
		if (s == null || s.isEmpty()) return of(null);
		try {
			return of(Identifier.parse(s));
		} catch (RuntimeException e) {
			return of(null);
		}
	}
}
