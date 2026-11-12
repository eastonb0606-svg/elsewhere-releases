package dev.elsewhere.petalwind_glasswaste;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registration helpers. Every id lives in the shared {@code elsewhere} namespace. */
public final class Reg {
	private Reg() {}

	public static final String NS = "elsewhere";
	/** Items in registration order, per creative tab ("petalwind" or "glasswaste"). */
	public static final Map<String, List<Item>> TAB_ITEMS = new LinkedHashMap<>();
	public static String currentTab = "petalwind";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
		return block(name, factory, props, true);
	}

	public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props, boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		if (withItem) item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	public static <I extends Item> I item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		I item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		TAB_ITEMS.computeIfAbsent(currentTab, k -> new ArrayList<>()).add(item);
		return item;
	}

	public static Item item(String name, Item.Properties props) {
		return item(name, Item::new, props);
	}

	/**
	 * Shared ids (owned by the core or the first module in load order): if the id is already registered, return that item
	 * and construct nothing; otherwise register ours.
	 */
	public static Item sharedItem(String name, java.util.function.Supplier<Item.Properties> props) {
		Identifier id = id(name);
		if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		return item(name, Item::new, props.get());
	}

	public static Item item(String name) {
		return item(name, Item::new, new Item.Properties());
	}

	public static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static <T extends LivingEntity> EntityType<T> living(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes) {
		EntityType<T> type = entity(name, builder);
		FabricDefaultAttributeRegistry.register(type, attributes);
		return type;
	}

	public static <T extends Mob> void spawnEgg(String name, EntityType<T> type) {
		item(name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}

	public static <B extends Block> B blockWithItem(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props,
		BiFunction<Block, Item.Properties, Item> itemFactory) {
		B block = block(name, factory, props, false);
		item(name, p -> itemFactory.apply(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}
}
