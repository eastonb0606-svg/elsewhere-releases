package dev.elsewhere.arsenal;

import dev.elsewhere.boss.kit.BossKit;
import dev.elsewhere.boss.kit.BossRewards;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.boss.kit.KitStore;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Gear sources without touching other modules, using the shared boss drop rule (DropRule/BossRewards): these are
 * non-signature uniques, so 50% on a player's first kill, 33% on repeats, plus pity. The boss's own (empty) loot table
 * file is kept so other modules' tables at the same path stay valid; the roll happens on the boss's death.
 */
public final class ArsenalLoot {
	private ArsenalLoot() {}

	public static ResourceKey<LootTable> table(String boss) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("elsewhere", "entities/" + boss));
	}

	/** boss entity id path -> unique. */
	static java.util.Map<String, Supplier<ItemStack>> uniques() {
		java.util.Map<String, Supplier<ItemStack>> m = new java.util.LinkedHashMap<>();
		m.put("heliodor", () -> new ItemStack(ArsenalItems.MIRRORGLASS_PRISM));
		m.put("auris", () -> new ItemStack(ArsenalItems.HUSHWARD_BELL));
		return m;
	}

	public static void init() {
		BossKit.init();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) return;
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
			if (!"elsewhere".equals(id.getNamespace())) return;
			Supplier<ItemStack> s = uniques().get(id.getPath());
			if (s == null) return;
			List<ServerPlayer> who = new java.util.ArrayList<>();
			if (source.getEntity() instanceof ServerPlayer sp) who.add(sp);
			else for (ServerPlayer p : level.players()) if (p.distanceToSqr(entity) < 48 * 48 && !p.isSpectator()) who.add(p);
			for (ServerPlayer p : who) rollFor(p, level, entity, id.getPath(), s);
		});
	}

	/** One player's roll for one kill (also used by the self-test). Returns the drops. */
	public static List<ItemStack> rollFor(ServerPlayer p, ServerLevel level, LivingEntity at, String boss, Supplier<ItemStack> s) {
		boolean first = !KitStore.of(level.getServer()).hasKilled(p.getUUID(), boss);
		List<ItemStack> out = BossRewards.rollUniques(p, boss, first, List.of(DropRule.other(boss + "_unique", s)));
		BossRewards.dropLoot(level, at.position(), out);
		return out;
	}
}
