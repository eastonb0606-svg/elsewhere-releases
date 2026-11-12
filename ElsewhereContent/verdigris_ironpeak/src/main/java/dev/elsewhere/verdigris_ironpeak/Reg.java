package dev.elsewhere.verdigris_ironpeak;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registration helpers. Every id is in the shared {@code elsewhere} namespace. */
public final class Reg {
	public static final String NS = "elsewhere";
	/** Everything this module registered, in order, for the creative tab, the self-test and NOTES.md. */
	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	public static final Map<String, EntityType<?>> ENTITIES = new LinkedHashMap<>();
	public static final List<Holder<SoundEvent>> SOUNDS = new ArrayList<>();

	private Reg() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
		return block(name, factory, props, true);
	}

	public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props, boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		BLOCKS.put(name, block);
		if (withItem) blockItem(name, block, new Item.Properties());
		return block;
	}

	/**
	 * Shared ids (core-owned stubs: return_rift, frayed_wayfinder, tier signature gear): if another module already
	 * registered the id, reuse it and construct nothing (an unregistered Block/Item would crash at registry freeze).
	 */
	public static Block sharedBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props, boolean withItem) {
		Identifier id = id(name);
		if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.getValue(id);
		return block(name, factory, props, withItem);
	}

	public static Item sharedItem(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		Identifier id = id(name);
		if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		return item(name, factory, props);
	}

	public static Item blockItem(String name, Block block, Item.Properties props) {
		return item(name, p -> new BlockItem(block, p), props.useBlockDescriptionPrefix());
	}

	public static Item item(String name, Item.Properties props) {
		return item(name, Item::new, props);
	}

	public static <I extends Item> I item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		I item = factory.apply(props.setId(key));
		if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ITEMS.put(name, item);
		return item;
	}

	public static <I extends Item> I item(String name, BiFunction<Block, Item.Properties, I> factory, Block block, Item.Properties props) {
		return item(name, p -> factory.apply(block, p), props.useBlockDescriptionPrefix());
	}

	public static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		ENTITIES.put(name, type);
		return type;
	}

	public static <T extends LivingEntity> EntityType<T> living(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes) {
		EntityType<T> type = entity(name, builder);
		FabricDefaultAttributeRegistry.register(type, attributes);
		return type;
	}

	public static Holder<SoundEvent> sound(String name) {
		Identifier id = id(name);
		Holder<SoundEvent> holder = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		SOUNDS.add(holder);
		return holder;
	}
}
