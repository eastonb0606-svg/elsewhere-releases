package dev.elsewhere.stub.overworld_bosses;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registrations that belong to the shared boss framework (the core owns them after the merge): the arena seal,
 * Boss Tokens, the per-tier signature gear and the boss damage types. STUB.
 */
public final class BossRegistry {
	public static final String NS = "elsewhere";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static final ResourceKey<DamageType> BOSS_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE, id("boss_attack"));
	/** Slams, flops and shockwaves: shields do not help (tagged bypasses_shield). */
	public static final ResourceKey<DamageType> BOSS_CRUSH = ResourceKey.create(Registries.DAMAGE_TYPE, id("boss_crush"));
	/** The only armor-bypassing boss damage: a DoT you can step (or struggle) out of. */
	public static final ResourceKey<DamageType> BOSS_DOT = ResourceKey.create(Registries.DAMAGE_TYPE, id("boss_dot"));
	public static final ResourceKey<DamageType> BOSS_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, id("boss_magic"));

	public static Block ARENA_SEAL;
	public static Item BOSS_TOKEN;
	public static SoundEvent SEAL_CLOSE;

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ARENA_SEAL = block("arena_seal", ArenaSealBlock::new, BlockBehaviour.Properties.of()
			.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().lightLevel(s -> 7).sound(SoundType.AMETHYST)
			.pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
			.isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, a) -> false));
		BOSS_TOKEN = item("boss_token", Item::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(64));
		SEAL_CLOSE = sound("arena.seal");
		SignatureGear.init();
		ArenaSeals.init();
	}

	/** Registers a block, or returns the one already registered under this id (shared ids in the merged beta). */
	public static Block block(String path, Function<BlockBehaviour.Properties, ? extends Block> factory, BlockBehaviour.Properties props) {
		Identifier id = id(path);
		if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.getValue(id);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
	}

	/** Registers an item, or returns the one already registered under this id. */
	public static Item item(String path, Function<Item.Properties, ? extends Item> factory, Item.Properties props) {
		Identifier id = id(path);
		if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static SoundEvent sound(String path) {
		Identifier id = id(path);
		if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) return BuiltInRegistries.SOUND_EVENT.getValue(id);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
