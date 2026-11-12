package dev.elsewhere.kingdom_villages;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Multipliers;
import dev.elsewhere.kingdom.Serfs;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.UnitTypes;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a faction means to the villages layer: its rebuild style (shape), banner colour, Serf-tier variant and the village
 * policy numbers (wall growth, integration, unrest). Numbers come, in order, from the war layer's effective brain
 * ({@code WarApi.brainField}), the faction file's {@code society.serfs}, and the style file
 * {@code data/<ns>/kingdom_village_style/<name>.json}; the built-in defaults are the WAR-AI.md table. Aldermark's profile is
 * exactly the 2.0 behaviour.
 */
public final class FactionVillages {
	private FactionVillages() {}

	public enum Shape { ALDERMARK, HRAFNHOLT, AZMAR, MIREWICK }

	public enum SerfKind {
		SERF("serf", 18, 1.0), THRALL("thrall", 18, 2.0), PORTER("porter", 18, 0.4), PEAT_CUTTER("peat_cutter", 18, 1.0);

		public final String key;
		public final double health, defaultCount;

		SerfKind(String key, double health, double defaultCount) {
			this.key = key;
			this.health = health;
			this.defaultCount = defaultCount;
		}

		public static SerfKind of(String s) {
			for (SerfKind k : values()) if (k.key.equalsIgnoreCase(s) || k.name().equalsIgnoreCase(s)) return k;
			return SERF;
		}
	}

	public record Policy(double fortRate, double fortCap, boolean borderOnly, int integDays, double unrest) {}

	public record Profile(Identifier faction, Shape shape, SerfKind serfKind, DyeColor banner, Policy policy, double serfCount, double serfHealth,
		double levyFraction, Identifier levyWeapon, double sizeMult, double rebuildSpeed) {
		public boolean isAldermark() {
			return faction.equals(Factions.ALDERMARK) || shape == Shape.ALDERMARK;
		}

		/** Sieges of free villages burn only for Aldermark (2.0); the rivals take villages without breaking or burning a block (FIELD-TACTICS 3.2, 4, 5). */
		public boolean siegeBurns() {
			return shape == Shape.ALDERMARK;
		}

		/** Azmar never targets the golems of a village it holds or is taking; they stay neutral until they attack (FIELD-TACTICS 3.2). */
		public boolean protectsGolems() {
			return shape == Shape.AZMAR;
		}
	}

	/** One style file ({@code data/<ns>/kingdom_village_style/<name>.json}). All fields optional; the war layer and the faction file own serfs and army size. */
	public record StyleDef(Optional<String> shape, Optional<String> banner, Optional<Double> fortRate, Optional<Double> fortCap, Optional<Boolean> fortBorderOnly,
		Optional<Integer> integDays, Optional<Double> unrest, Optional<Double> rebuildSpeed) {
		public static final Codec<StyleDef> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("shape").forGetter(StyleDef::shape),
			Codec.STRING.optionalFieldOf("banner").forGetter(StyleDef::banner),
			Codec.DOUBLE.optionalFieldOf("fort_rate").forGetter(StyleDef::fortRate),
			Codec.DOUBLE.optionalFieldOf("fort_cap").forGetter(StyleDef::fortCap),
			Codec.BOOL.optionalFieldOf("fort_border_only").forGetter(StyleDef::fortBorderOnly),
			Codec.INT.optionalFieldOf("integ_days").forGetter(StyleDef::integDays),
			Codec.DOUBLE.optionalFieldOf("unrest").forGetter(StyleDef::unrest),
			Codec.DOUBLE.optionalFieldOf("rebuild_speed").forGetter(StyleDef::rebuildSpeed)
		).apply(i, StyleDef::new));
	}

	private static Map<Identifier, StyleDef> loaded = Map.of();
	private static final Map<Identifier, StyleDef> CODE = new HashMap<>();
	private static final Map<Identifier, Profile> CACHE = new HashMap<>();

	static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(VillagesContent.id("kingdom_village_styles"),
			new SimpleJsonResourceReloadListener<StyleDef>(StyleDef.CODEC, FileToIdConverter.json("kingdom_village_style")) {
				@Override
				protected void apply(final Map<Identifier, StyleDef> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					loaded = Map.copyOf(prepared);
					CACHE.clear();
				}
			});
	}

	/** Dev/test: define a style from code (kept until {@link #clearCode}). */
	public static void putCode(Identifier faction, StyleDef def) {
		CODE.put(faction, def);
		CACHE.clear();
	}

	public static void clearCode() {
		CODE.clear();
		CACHE.clear();
	}

	public static void invalidate() {
		CACHE.clear();
	}

	static Shape shapeOfPath(String path) {
		return switch (path) {
			case "hrafnholt" -> Shape.HRAFNHOLT;
			case "azmar" -> Shape.AZMAR;
			case "mirewick" -> Shape.MIREWICK;
			default -> Shape.ALDERMARK;
		};
	}

	static Policy defaultPolicy(Shape s) {
		return switch (s) { // WAR-AI.md section 3 tuned values
			case HRAFNHOLT -> new Policy(0.0, 0.10, false, 12, 0.80);
			case AZMAR -> new Policy(0.010, 0.90, true, 4, 0.30);
			case MIREWICK -> new Policy(0.002, 0.20, false, 10, 0.60);
			default -> new Policy(0.002, 0.25, false, 8, 0.50);
		};
	}

	static DyeColor defaultBanner(Shape s) {
		return switch (s) {
			case HRAFNHOLT -> DyeColor.GREEN;
			case AZMAR -> DyeColor.CYAN;
			case MIREWICK -> DyeColor.PURPLE;
			default -> DyeColor.BLUE;
		};
	}

	/** The profile for a faction id string (blank or unknown = Aldermark). */
	public static Profile of(@Nullable String faction) {
		return of(faction == null || faction.isEmpty() ? Factions.ALDERMARK : Identifier.parse(faction));
	}

	public static Profile of(Identifier faction) {
		Profile p = CACHE.get(faction);
		if (p == null) {
			p = build(faction);
			CACHE.put(faction, p);
		}
		return p;
	}

	private static Profile build(Identifier faction) {
		StyleDef sd = CODE.containsKey(faction) ? CODE.get(faction) : loaded.get(faction);
		if (sd == null) sd = loaded.get(VillagesContent.id(faction.getPath()));
		Shape shape = sd != null && sd.shape().isPresent() ? parseShape(sd.shape().get()) : shapeOfPath(faction.getPath());
		Policy dp = defaultPolicy(shape);
		DyeColor banner = defaultBanner(shape);
		if (sd != null && sd.banner().isPresent()) {
			DyeColor c = DyeColor.byName(sd.banner().get(), null);
			if (c != null) banner = c;
		}
		// Serf tier: the war layer's Serfs.variant (the faction file's society.serfs wins there)
		Serfs.Variant v = Serfs.variant(faction);
		SerfKind kind = switch (v.type().getPath()) {
			case "thrall" -> SerfKind.THRALL;
			case "porter" -> SerfKind.PORTER;
			case "peat_cutter" -> SerfKind.PEAT_CUTTER;
			default -> SerfKind.SERF;
		};
		double count = v.countMultiplier(), health = v.health();
		double levy = v.levy().map(l -> (double) l.fraction()).orElse(0.0);
		Identifier weapon = v.levy().map(FactionDef.Levy::weapon).orElse(Identifier.parse("minecraft:stone_axe"));
		double fortRate = dp.fortRate(), fortCap = dp.fortCap(), unrest = dp.unrest();
		boolean border = dp.borderOnly();
		int integ = dp.integDays();
		double speed = kind == SerfKind.THRALL ? 1.5 : 1.0;
		if (sd != null) {
			if (sd.fortRate().isPresent()) fortRate = sd.fortRate().get();
			if (sd.fortCap().isPresent()) fortCap = sd.fortCap().get();
			if (sd.fortBorderOnly().isPresent()) border = sd.fortBorderOnly().get();
			if (sd.integDays().isPresent()) integ = sd.integDays().get();
			if (sd.unrest().isPresent()) unrest = sd.unrest().get();
			if (sd.rebuildSpeed().isPresent()) speed = sd.rebuildSpeed().get();
		}
		double size = faction.equals(Factions.ALDERMARK) ? 1.0 : Multipliers.size(faction);
		return new Profile(faction, shape, kind, banner, new Policy(fortRate, fortCap, border, integ, unrest), count, health, levy, weapon, size, speed);
	}

	static Shape parseShape(String s) {
		for (Shape v : Shape.values()) if (v.name().equalsIgnoreCase(s)) return v;
		return Shape.ALDERMARK;
	}

	/** Policy numbers with the war layer's effective brain (boss-death degradation included) applied when the war layer is present. */
	public static Policy livePolicy(@Nullable MinecraftServer server, Profile p) {
		if (server == null || p.isAldermark()) return p.policy();
		double fr = WarBridge.brainField(server, p.faction(), "fort_rate", p.policy().fortRate());
		double fc = WarBridge.brainField(server, p.faction(), "fort_cap", p.policy().fortCap());
		double integ = WarBridge.brainField(server, p.faction(), "integ", p.policy().integDays());
		double un = WarBridge.brainField(server, p.faction(), "unrest", p.policy().unrest());
		return new Policy(fr, fc, p.policy().borderOnly(), (int) Math.round(integ), un);
	}

	// ---- army composition ---------------------------------------------------------------------------------------------

	/** Scales a count by the faction's army size multiplier (never below 1). Aldermark returns {@code n} unchanged. */
	public static int scaled(Profile p, int n) {
		if (p.isAldermark()) return n;
		return Math.max(1, (int) Math.round(n * p.sizeMult()));
	}

	/**
	 * Member unit ids (base ids; the faction roster resolves them) for {@code n} soldiers: Aldermark keeps its fixed 2.0 lists; a
	 * rival rolls its own makeup through the war layer's {@code ArmyComposer} (no mounted units in a village force).
	 */
	public static List<Identifier> members(Identifier faction, Profile p, int n, RandomSource rnd, List<Identifier> aldermarkMix) {
		if (faction.equals(Factions.ALDERMARK)) return aldermarkMix;
		return ArmyComposer.members(rnd, n, false, faction);
	}

	public static Identifier sergeant(Identifier faction) {
		return UnitTypes.SERGEANT;
	}

	public static @Nullable Item levyWeapon(Profile p) {
		return BuiltInRegistries.ITEM.containsKey(p.levyWeapon()) ? BuiltInRegistries.ITEM.getValue(p.levyWeapon()) : null;
	}
}
