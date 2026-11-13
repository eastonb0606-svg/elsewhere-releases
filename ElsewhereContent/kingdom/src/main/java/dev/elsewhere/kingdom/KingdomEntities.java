package dev.elsewhere.kingdom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * One entity type per built-in unit ({@code elsewhere:kingdom_<unit>}), all using {@link KingdomSoldier}; the entity
 * type only picks the default unit type, the NBT/API can set any unit on any of them.
 * Spawn eggs {@code elsewhere:kingdom_<unit>_spawn_egg} and the creative tab {@code elsewhere:kingdom}.
 */
public final class KingdomEntities {
	private KingdomEntities() {}

	/** Entity type to its default unit id. Add your own with {@link #registerSoldierType}. */
	public static final Map<EntityType<? extends KingdomSoldier>, Identifier> TYPES = new LinkedHashMap<>();
	public static final List<Item> EGGS = new ArrayList<>();

	public static EntityType<KingdomSoldier> MAN_AT_ARMS, KNIGHT, LANCER, MOUNTED_SWORDSMAN, ARCHER, CROSSBOWMAN, SERGEANT;
	/** 0.2.0 units. */
	public static EntityType<KingdomSoldier> CAPTAIN, STANDARD_BEARER, HERALD, SURGEON, SQUIRE, PIKEMAN;
	/** {@code elsewhere:hrafnholt_longship}: the sailing raid ship (RIVALS-DECISIONS 18); spawned only by the war layer, never by /summon or eggs. */
	public static EntityType<dev.elsewhere.kingdom.war.HrafnholtLongship> LONGSHIP;
	/** The built-in soldier types (their loot tables are {@code elsewhere:entities/kingdom_<unit>}). */
	public static final List<EntityType<KingdomSoldier>> BUILT_IN = new ArrayList<>();

	@SuppressWarnings("unchecked")
	static void init() {
		MAN_AT_ARMS = registerSoldierType("kingdom_man_at_arms", UnitTypes.MAN_AT_ARMS, KingdomSoldier::new);
		KNIGHT = registerSoldierType("kingdom_knight", UnitTypes.KNIGHT, KingdomSoldier::new);
		LANCER = registerSoldierType("kingdom_lancer", UnitTypes.LANCER, KingdomSoldier::new);
		MOUNTED_SWORDSMAN = registerSoldierType("kingdom_mounted_swordsman", UnitTypes.MOUNTED_SWORDSMAN, KingdomSoldier::new);
		ARCHER = registerSoldierType("kingdom_archer", UnitTypes.ARCHER, KingdomSoldier::new);
		CROSSBOWMAN = registerSoldierType("kingdom_crossbowman", UnitTypes.CROSSBOWMAN, KingdomSoldier::new);
		SERGEANT = registerSoldierType("kingdom_sergeant", UnitTypes.SERGEANT, KingdomSoldier::new);
		CAPTAIN = registerSoldierType("kingdom_captain", UnitTypes.CAPTAIN, KingdomSoldier::new);
		STANDARD_BEARER = registerSoldierType("kingdom_standard_bearer", UnitTypes.STANDARD_BEARER, KingdomSoldier::new);
		HERALD = registerSoldierType("kingdom_herald", UnitTypes.HERALD, KingdomSoldier::new);
		SURGEON = registerSoldierType("kingdom_surgeon", UnitTypes.SURGEON, KingdomSoldier::new);
		SQUIRE = registerSoldierType("kingdom_squire", UnitTypes.SQUIRE, KingdomSoldier::new);
		PIKEMAN = registerSoldierType("kingdom_pikeman", UnitTypes.PIKEMAN, KingdomSoldier::new);
		ResourceKey<EntityType<?>> shipKey = ResourceKey.create(Registries.ENTITY_TYPE, KingdomContent.id("hrafnholt_longship"));
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(shipKey.identifier())) LONGSHIP = (EntityType<dev.elsewhere.kingdom.war.HrafnholtLongship>) BuiltInRegistries.ENTITY_TYPE.getValue(shipKey.identifier());
		else LONGSHIP = Registry.register(BuiltInRegistries.ENTITY_TYPE, shipKey, EntityType.Builder.<dev.elsewhere.kingdom.war.HrafnholtLongship>of(dev.elsewhere.kingdom.war.HrafnholtLongship::new,
			MobCategory.MISC).sized(5.0F, 2.0F).fireImmune().noSummon().noLootTable().clientTrackingRange(10).updateInterval(2).build(shipKey));
		BUILT_IN.addAll(List.of(MAN_AT_ARMS, KNIGHT, LANCER, MOUNTED_SWORDSMAN, ARCHER, CROSSBOWMAN, SERGEANT, CAPTAIN, STANDARD_BEARER, HERALD, SURGEON, SQUIRE, PIKEMAN));
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.kingdom"))
			.icon(() -> new ItemStack(EGGS.get(1)))
			.displayItems((params, out) -> {
				EGGS.forEach(out::accept);
				dev.elsewhere.kingdom.item.KingdomItems.ALL.forEach(out::accept);
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KingdomContent.id("kingdom"), tab);
	}

	/**
	 * Registers a soldier entity type (player-sized, MobCategory.MISC so it never uses the natural mob cap), its
	 * attributes and a spawn egg {@code <path>_spawn_egg} shown in the kingdom tab. The client side must call
	 * {@code KingdomClient.registerSoldierRenderer(type)} (built-in types are done). Use it for Lords/the King by passing
	 * a subclass factory.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends KingdomSoldier> EntityType<T> registerSoldierType(String path, Identifier defaultUnit, EntityType.EntityFactory<T> factory) {
		Identifier rid = KingdomContent.id(path);
		EntityType<T> type;
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) {
			type = (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
		} else {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
			type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(factory, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).vehicleAttachment(net.minecraft.world.entity.Avatar.DEFAULT_VEHICLE_ATTACHMENT).clientTrackingRange(10).build(key));
			FabricDefaultAttributeRegistry.register(type, KingdomSoldier.createAttributes());
			Identifier eggId = KingdomContent.id(path + "_spawn_egg");
			ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, eggId);
			EGGS.add(Registry.register(BuiltInRegistries.ITEM, ik, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(ik))));
		}
		TYPES.put(type, defaultUnit);
		return type;
	}

	/** Unit id to the entity type (class) that fields it, registered with {@link #registerUnitClass}. */
	private static final Map<Identifier, EntityType<? extends KingdomSoldier>> UNIT_CLASSES = new LinkedHashMap<>();

	/**
	 * Binds a unit id to a custom soldier entity type (a unique soldier or boss subclass of {@link KingdomSoldier}) that was registered with {@link #registerSoldierType} or
	 * elsewhere: {@link KingdomApi#spawnSoldier(net.minecraft.server.level.ServerLevel, Identifier, Identifier, net.minecraft.world.phys.Vec3, Squad)} for that unit (or a roster
	 * entry whose {@code unit} redirects to it) then creates that class. Several units may share one type.
	 */
	public static void registerUnitClass(Identifier unit, EntityType<? extends KingdomSoldier> type) {
		UNIT_CLASSES.put(unit, type);
	}

	/** Default unit id for an entity type (falls back to man-at-arms). */
	public static Identifier defaultUnit(EntityType<?> type) {
		Identifier u = TYPES.get(type);
		return u != null ? u : UnitTypes.MAN_AT_ARMS;
	}

	/** The registered soldier entity type whose default unit is {@code unit}, or the man-at-arms type. */
	public static EntityType<? extends KingdomSoldier> typeFor(Identifier unit) {
		EntityType<? extends KingdomSoldier> reg = UNIT_CLASSES.get(unit);
		if (reg != null) return reg;
		for (var e : TYPES.entrySet()) if (e.getValue().equals(unit)) return e.getKey();
		return MAN_AT_ARMS;
	}
}
