package dev.elsewhere.kingdom_villages;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Kingdom villages (lane K-C): occupied / besieged / free villages near the Aldermark realm, siege fire, Serfs, the
 * fortified rebuild, garrisons, outposts, roads, refugees, liberation and the Royal Steward. Entry point for the merge:
 * {@link #init()}. Ids use the {@code village_} / {@code serf} prefixes in the {@code elsewhere} namespace.
 */
public final class VillagesContent {
	private VillagesContent() {}

	public static final String NS = "elsewhere";
	public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("elsewhere-kingdom-villages");
	private static boolean done;

	public static Block SIEGE_FIRE, CHARRED_PLANKS, ASH, ROYAL_COFFER;
	public static Item CHARRED_PLANKS_ITEM, ASH_ITEM, ROYAL_COFFER_ITEM, ROYAL_PURSE;
	public static EntityType<Serf> SERF;
	public static ResourceKey<PoiType> COFFER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id("royal_coffer"));
	public static ResourceKey<VillagerProfession> STEWARD = ResourceKey.create(Registries.VILLAGER_PROFESSION, id("royal_steward"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static void init() {
		if (done) return;
		done = true;
		SIEGE_FIRE = block("village_siege_fire", SiegeFireBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.FIRE)
			.replaceable().noCollision().instabreak().lightLevel(s -> 15).sound(SoundType.WOOL).pushReaction(PushReaction.POPPED).noLootTable());
		CHARRED_PLANKS = block("village_charred_planks", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.instrument(NoteBlockInstrument.BASS).strength(1.5F, 2.0F).sound(SoundType.WOOD).ignitedByLava());
		ASH = block("village_ash", AshBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY)
			.replaceable().noCollision().strength(0.1F).sound(SoundType.SAND).pushReaction(PushReaction.POPPED));
		ROYAL_COFFER = block("village_royal_coffer", RoyalCofferBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(2.5F, 6.0F).sound(SoundType.WOOD).noLootTable());
		CHARRED_PLANKS_ITEM = blockItem(CHARRED_PLANKS);
		ASH_ITEM = blockItem(ASH);
		ROYAL_COFFER_ITEM = blockItem(ROYAL_COFFER);
		ROYAL_PURSE = item("village_royal_purse", Item::new, new Item.Properties().stacksTo(64));

		Identifier serfId = id("serf");
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(serfId)) {
			@SuppressWarnings("unchecked") EntityType<Serf> t = (EntityType<Serf>) BuiltInRegistries.ENTITY_TYPE.getValue(serfId);
			SERF = t;
		} else {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, serfId);
			SERF = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(Serf::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).build(key));
			FabricDefaultAttributeRegistry.register(SERF, Serf.createAttributes());
		}

		// Royal Coffer job site: only UNSEALED coffers are a point of interest, so a Steward can only appear once the town is
		// liberated (the rebuild places it sealed). One ticket: at most one Steward per coffer / town.
		List<BlockState> open = new ArrayList<>();
		for (BlockState s : ROYAL_COFFER.getStateDefinition().getPossibleStates()) if (!s.getValue(RoyalCofferBlock.SEALED)) open.add(s);
		if (!BuiltInRegistries.POINT_OF_INTEREST_TYPE.containsKey(COFFER_POI.identifier())) PoiHelper.register(COFFER_POI.identifier(), 1, 1, open);
		if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(STEWARD.identifier())) {
			Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, STEWARD, new VillagerProfession(
				Component.translatable("entity.elsewhere.villager.royal_steward"),
				h -> h.is(COFFER_POI), h -> h.is(COFFER_POI), ImmutableSet.of(), ImmutableSet.of(),
				SoundEvents.VILLAGER_WORK_CARTOGRAPHER, new Int2ObjectOpenHashMap<>()));
		}

		FactionVillages.init();
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(Protect::allowDamage);
		ServerTickEvents.END_LEVEL_TICK.register(VillageManager::tickLevel);
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			WarBridge.listen(VillageManager::onVillageChanged);
			CourtBridge.listen();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> VillageManager.reset());
		CommandRegistrationCallback.EVENT.register((d, ctx, env) -> AldermarkCommand.register(d));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl && !player.isSpectator()) PlayerMarks.mark(sl, pos);
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel sl && entity instanceof Villager v && !player.isSpectator()) {
				if (VillageManager.isCaptured(sl, v)) {
					player.sendOverlayMessage(Component.translatable("message.elsewhere.village.captured"));
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		dev.elsewhere.kingdom.KingdomEvents.SOLDIER_DIED.register((level, soldier, source) -> VillageManager.onSoldierDied(level, soldier));
	}

	static Block block(String path, Function<BlockBehaviour.Properties, ? extends Block> f, BlockBehaviour.Properties p) {
		Identifier id = id(path);
		if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.getValue(id);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, key, f.apply(p.setId(key)));
	}

	static Item blockItem(Block b) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(b, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	static Item item(String path, Function<Item.Properties, ? extends Item> f, Item.Properties p) {
		Identifier id = id(path);
		if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, f.apply(p.setId(key)));
	}

	/** An item registered by another lane (K-A: Crowns, Bodkin Bolts, Oathkeeper's Seal, ...), first id that exists, or null. */
	public static Item optionalItem(String... paths) {
		for (String p : paths) {
			Identifier id = p.contains(":") ? Identifier.parse(p) : id(p);
			if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		}
		return null;
	}
}
