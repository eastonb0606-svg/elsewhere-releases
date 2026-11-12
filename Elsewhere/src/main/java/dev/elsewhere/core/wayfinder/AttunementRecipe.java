package dev.elsewhere.core.wayfinder;

import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.jspecify.annotations.Nullable;

/** {@code {"type": "elsewhere:attunement"}}: base Wayfinder + a dimension's ingredient set (see {@link Attunements}). */
public class AttunementRecipe extends CustomRecipe {
	public static final AttunementRecipe INSTANCE = new AttunementRecipe();
	public static final MapCodec<AttunementRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, AttunementRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<AttunementRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private Attunements.@Nullable Def find(final CraftingInput input) {
		int wayfinders = 0;
		Set<Item> items = new HashSet<>();
		for (int i = 0; i < input.size(); i++) {
			ItemStack s = input.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			if (s.getItem() instanceof WayfinderItem) {
				wayfinders++;
			} else {
				items.add(s.getItem());
			}
		}
		return wayfinders == 1 ? Attunements.match(items) : null;
	}

	@Override
	public boolean matches(final CraftingInput input, final Level level) {
		Attunements.Def def = this.find(input);
		if (def == null || def.result() == null) {
			return false;
		}
		if ("ender_dragon_killed".equals(def.requires())) {
			if (!(level instanceof ServerLevel server)) {
				return false;
			}
			ServerLevel end = server.getServer().getLevel(Level.END);
			EnderDragonFight fight = end == null ? null : end.getDragonFight();
			return fight != null && fight.hasPreviouslyKilledDragon();
		}
		return true;
	}

	@Override
	public ItemStack assemble(final CraftingInput input) {
		Attunements.Def def = this.find(input);
		Item result = def == null ? null : def.result();
		return result == null ? ItemStack.EMPTY : new ItemStack(result);
	}

	@Override
	public RecipeSerializer<AttunementRecipe> getSerializer() {
		return SERIALIZER;
	}
}
