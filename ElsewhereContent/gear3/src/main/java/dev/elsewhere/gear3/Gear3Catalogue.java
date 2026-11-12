package dev.elsewhere.gear3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads {@code /elsewhere_gear3/catalogue.json}: {@code {"items": [ {...} ]}}. */
public final class Gear3Catalogue {
	private Gear3Catalogue() {}

	public static final String RESOURCE = "/elsewhere_gear3/catalogue.json";
	public static final List<Gear3Def> DEFS = new ArrayList<>();

	public static void load() {
		DEFS.clear();
		try (InputStream in = Gear3Catalogue.class.getResourceAsStream(RESOURCE)) {
			if (in == null) throw new IllegalStateException("missing " + RESOURCE);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("items")) DEFS.add(new Gear3Def(e.getAsJsonObject()));
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + RESOURCE, e);
		}
	}

	public static Gear3Def byId(String id) {
		for (Gear3Def d : DEFS) if (d.id.equals(id)) return d;
		return null;
	}
}
