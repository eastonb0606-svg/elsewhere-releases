package dev.elsewhere.core.wayfinder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.Elsewhere;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Data-driven attunements: {@code data/elsewhere/attunement/<dimension>.json}
 * <pre>{"dimension": "elsewhere:pale_hollow", "ingredients": ["minecraft:resin_clump", ...], "requires": "none", "notes": "..."}</pre>
 * A base Wayfinder plus one or more of each listed ingredient (and nothing else, any layout) crafts
 * {@code elsewhere:wayfinder_<dimension path>} through the {@link AttunementRecipe}.
 */
public final class Attunements {
	public record Def(ResourceKey<Level> dimension, List<Item> ingredients, String requires, String notes) {
		public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Def::dimension),
			BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("ingredients").forGetter(Def::ingredients),
			Codec.STRING.optionalFieldOf("requires", "none").forGetter(Def::requires),
			Codec.STRING.optionalFieldOf("notes", "").forGetter(Def::notes)
		).apply(i, Def::new));

		public Set<Item> ingredientSet() {
			return new HashSet<>(this.ingredients);
		}

		public @Nullable Item result() {
			Identifier id = Elsewhere.id("wayfinder_" + this.dimension.identifier().getPath());
			return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		}
	}

	private static volatile Map<Identifier, Def> defs = Map.of();

	private Attunements() {
	}

	public static Map<Identifier, Def> all() {
		return defs;
	}

	/** Finds the attunement whose ingredient set equals {@code items} exactly. */
	public static @Nullable Def match(final Set<Item> items) {
		for (Def d : defs.values()) {
			if (d.ingredientSet().equals(items)) {
				return d;
			}
		}
		return null;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Elsewhere.id("attunement"), AttunementRecipe.SERIALIZER);
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Elsewhere.id("attunements"),
			new SimpleJsonResourceReloadListener<Def>(Def.CODEC, FileToIdConverter.json("attunement")) {
				@Override
				protected void apply(final Map<Identifier, Def> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					defs = new LinkedHashMap<>(prepared);
					Elsewhere.LOG.info("Loaded {} Wayfinder attunements", defs.size());
				}
			});
	}

	public static boolean isWayfinder(final ItemStack stack) {
		return stack.getItem() instanceof WayfinderItem;
	}

	public static ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipeKey(final String path) {
		return ResourceKey.create(Registries.RECIPE, Elsewhere.id(path));
	}
}
