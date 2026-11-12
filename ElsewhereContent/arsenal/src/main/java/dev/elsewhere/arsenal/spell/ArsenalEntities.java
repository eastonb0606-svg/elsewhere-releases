package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalContent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ArsenalEntities {
	private ArsenalEntities() {}

	public static EntityType<MagicBolt> MAGIC_BOLT;

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ArsenalContent.id("magic_bolt"));
		MAGIC_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<MagicBolt>of(MagicBolt::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(1).noSave()
				.build(key));
	}
}
