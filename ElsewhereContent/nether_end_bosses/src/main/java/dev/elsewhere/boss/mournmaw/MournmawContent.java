package dev.elsewhere.boss.mournmaw;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.DevCommands;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.Reg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/** Registers Mournmaw, her tears and fireballs, her drops, and the dormant-above-the-basin summon. */
public final class MournmawContent {
	public static EntityType<Mournmaw> MOURNMAW;
	public static EntityType<SoulTear> SOUL_TEAR;
	public static EntityType<SoulFireball> SOUL_FIREBALL;
	public static Item SOUL_TEAR_ITEM;
	public static Item SOUL_FIREBALL_ITEM;
	public static Item LAMENT_HARNESS;
	public static Item SOULWICK_LANTERN;
	public static Item MOURNMAWS_BLESSING;
	public static Item MOURNMAWS_TEAR;

	private MournmawContent() {}

	public static void init() {
		MOURNMAW = Reg.entity("mournmaw", EntityType.Builder.of(Mournmaw::new, MobCategory.MONSTER).fireImmune().sized(8.0F, 8.0F).eyeHeight(4.5F).clientTrackingRange(10).notInPeaceful());
		SOUL_TEAR = Reg.entity("soul_tear", EntityType.Builder.<SoulTear>of(SoulTear::new, MobCategory.MISC).noLootTable().sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(5));
		SOUL_FIREBALL = Reg.entity("soul_fireball", EntityType.Builder.<SoulFireball>of(SoulFireball::new, MobCategory.MISC).noLootTable().sized(1.0F, 1.0F).clientTrackingRange(6).updateInterval(5));
		FabricDefaultAttributeRegistry.register(MOURNMAW, Mournmaw.createAttributes());

		SOUL_TEAR_ITEM = Reg.item("soul_tear", new Item.Properties());
		SOUL_FIREBALL_ITEM = Reg.item("soul_fireball", new Item.Properties());
		LAMENT_HARNESS = Reg.item("lament_harness", new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
			.component(DataComponents.EQUIPPABLE, Equippable.harness(DyeColor.CYAN))
			.attributes(ItemAttributeModifiers.builder().add(Attributes.FLYING_SPEED,
				new AttributeModifier(Reg.id("lament_harness_speed"), GearStats.LAMENT_HARNESS_FLYING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.BODY).build()));
		SOULWICK_LANTERN = Reg.item("soulwick_lantern", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		MOURNMAWS_BLESSING = Reg.item("mournmaws_blessing", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			.component(DataComponents.DEATH_PROTECTION, new DeathProtection(java.util.List.of(
				new ClearAllStatusEffectsConsumeEffect(),
				new ApplyStatusEffectsConsumeEffect(java.util.List.of(
					new MobEffectInstance(MobEffects.REGENERATION, GearStats.BLESSING_REGEN_TICKS, GearStats.BLESSING_REGEN_LEVEL),
					new MobEffectInstance(MobEffects.RESISTANCE, GearStats.BLESSING_RESISTANCE_TICKS, GearStats.BLESSING_RESISTANCE_LEVEL)))))));
		MOURNMAWS_TEAR = Reg.item("mournmaws_tear", new Item.Properties().rarity(Rarity.UNCOMMON));

		ArenaBuilder.BY_BOSS.put("mournmaw", MournmawArena.INSTANCE);
		DevCommands.SUMMONERS.put("mournmaw", (level, origin) -> {
			Mournmaw m = spawnDormant(level, origin);
			if (m != null) m.startFight(level, origin, null, ItemStack.EMPTY);
			return m;
		});
		// Soulwick Lantern in the offhand: invisible mobs within 16 blocks glow
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (!p.getOffhandItem().is(SOULWICK_LANTERN)) continue;
				for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(GearStats.SOULWICK_REVEAL_RADIUS), e -> e.isInvisible() && e != p)) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
				}
			}
		});
	}

	/** Puts Mournmaw, dormant and weeping, above the basin at {@code origin}. */
	public static @Nullable Mournmaw spawnDormant(ServerLevel level, BlockPos origin) {
		Mournmaw m = MOURNMAW.create(level, EntitySpawnReason.TRIGGERED);
		if (m == null) return null;
		m.snapTo(origin.getX() + 0.5, origin.getY() + 12, origin.getZ() + 0.5, 0, 0);
		m.setHome(origin);
		m.setFlag(Mournmaw.FLAG_DORMANT, true);
		level.addFreshEntity(m);
		return m;
	}
}
