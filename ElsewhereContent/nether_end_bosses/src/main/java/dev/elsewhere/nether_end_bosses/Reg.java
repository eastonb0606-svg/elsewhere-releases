package dev.elsewhere.nether_end_bosses;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registration helpers; every id is in the {@code elsewhere} namespace. */
public final class Reg {
	public static final String NS = "elsewhere";
	/** Everything this module registers as an item, in creative-tab order. */
	public static final List<Item> ITEMS = new ArrayList<>();

	private Reg() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static <T extends Item> T item(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ITEMS.add(item);
		return item;
	}

	public static Item item(String name, Item.Properties props) {
		return item(name, Item::new, props);
	}

	public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	public static Holder<SoundEvent> sound(String name) {
		Identifier id = id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
