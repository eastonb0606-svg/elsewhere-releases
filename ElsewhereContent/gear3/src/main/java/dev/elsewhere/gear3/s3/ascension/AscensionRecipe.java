package dev.elsewhere.gear3.s3.ascension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.gear3.Gear3Content;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * Smithing recipe {@code elsewhere:gear3_ascension} (data/elsewhere/recipe/gear3_ascension.json): empty template slot, base =
 * a piece in {@code #elsewhere:gear3_ascendable}, addition = the Ascension Shard. The result is the same piece made
 * Ascendant ({@link Ascension#ascend}); a piece that is already Ascendant does not match, so a shard is never wasted.
 */
public final class AscensionRecipe extends SimpleSmithingRecipe {
	public static final Identifier ID = Gear3Content.id("gear3_ascension");

	public static final MapCodec<AscensionRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
			Ingredient.CODEC.fieldOf("base").forGetter(o -> o.base),
			Ingredient.CODEC.fieldOf("addition").forGetter(o -> o.addition))
		.apply(i, AscensionRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, AscensionRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		Ingredient.CONTENTS_STREAM_CODEC, o -> o.base,
		Ingredient.CONTENTS_STREAM_CODEC, o -> o.addition,
		AscensionRecipe::new);
	public static final RecipeSerializer<AscensionRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final Ingredient base;
	private final Ingredient addition;

	public AscensionRecipe(Recipe.CommonInfo info, Ingredient base, Ingredient addition) {
		super(info);
		this.base = base;
		this.addition = addition;
	}

	static void register() {
		if (BuiltInRegistries.RECIPE_SERIALIZER.getValue(ID) == null) Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ID, SERIALIZER);
	}

	@Override
	public boolean matches(SmithingRecipeInput input, Level level) {
		return Ascension.ENABLED && input.template().isEmpty() && base.test(input.base()) && addition.test(input.addition()) && Ascension.canAscend(input.base());
	}

	@Override
	public ItemStack assemble(SmithingRecipeInput input) {
		return Ascension.canAscend(input.base()) ? Ascension.ascend(input.base()) : ItemStack.EMPTY;
	}

	@Override
	public Optional<Ingredient> templateIngredient() {
		return Optional.empty();
	}

	@Override
	public Ingredient baseIngredient() {
		return base;
	}

	@Override
	public Optional<Ingredient> additionIngredient() {
		return Optional.of(addition);
	}

	@Override
	public RecipeSerializer<AscensionRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		return PlacementInfo.createFromOptionals(List.of(Optional.empty(), Optional.of(base), Optional.of(addition)));
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new SmithingRecipeDisplay(SlotDisplay.Empty.INSTANCE, base.display(), addition.display(), base.display(),
			new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
	}
}
