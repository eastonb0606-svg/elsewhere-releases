package dev.elsewhere.greenhaze_hush;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/** Registration helpers. Every id lives in the shared {@code elsewhere} namespace. */
public final class Reg {
	private Reg() {}

	public static final String NS = "elsewhere";
	/** Creative-tab order, split by dimension. */
	public static final List<Item> GREENHAZE_TAB = new ArrayList<>();
	public static final List<Item> HUSH_TAB = new ArrayList<>();
	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	/** Which tab new items go to. */
	public static List<Item> currentTab = GREENHAZE_TAB;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		return block(name, factory, props, true);
	}

	public static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props, boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		if (BuiltInRegistries.BLOCK.containsKey(key.identifier())) {
			// shared id already registered by the core or an earlier module: reuse it, construct nothing
			Block existing = BuiltInRegistries.BLOCK.getValue(key.identifier());
			BLOCKS.put(name, existing);
			if (withItem && BuiltInRegistries.ITEM.containsKey(key.identifier())) ITEMS.put(name, BuiltInRegistries.ITEM.getValue(key.identifier()));
			return existing;
		}
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		BLOCKS.put(name, block);
		if (withItem) item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	public static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		if (BuiltInRegistries.ITEM.containsKey(key.identifier())) {
			Item existing = BuiltInRegistries.ITEM.getValue(key.identifier());
			ITEMS.put(name, existing);
			return existing;
		}
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ITEMS.put(name, item);
		currentTab.add(item);
		return item;
	}

	public static Item item(String name) {
		return item(name, Item::new, new Item.Properties());
	}

	public static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static Holder<SoundEvent> sound(String name) {
		Identifier id = id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
