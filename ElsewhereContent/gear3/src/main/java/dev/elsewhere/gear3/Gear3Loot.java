package dev.elsewhere.gear3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Item sources through Fabric's loot MODIFY event (an extra pool per drop); no other module's loot file is edited.
 * Tables named without a namespace are {@code elsewhere:}. Empty placeholder tables (so MODIFY fires for tables that
 * have no file yet) are shipped by gen_resources.py from the catalogue's {@code placeholder_tables}; a real table wins at merge.
 */
public final class Gear3Loot {
	private Gear3Loot() {}

	public record Entry(Item item, Gear3Def.Drop drop) {}

	/** full table id ("elsewhere:entities/x") -> extra drops. */
	public static final Map<String, List<Entry>> DROPS = new HashMap<>();

	public static Identifier tableId(String table) {
		return table.contains(":") ? Identifier.parse(table) : Gear3Content.id(table);
	}

	public static ResourceKey<LootTable> table(String table) {
		return ResourceKey.create(Registries.LOOT_TABLE, tableId(table));
	}

	public static void init() {
		DROPS.clear();
		for (Gear3Def d : Gear3Catalogue.DEFS) {
			Item item = Gear3Items.BY_ID.get(d.id);
			for (Gear3Def.Drop drop : d.drops) DROPS.computeIfAbsent(tableId(drop.table()).toString(), k -> new ArrayList<>()).add(new Entry(item, drop));
		}
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			List<Entry> list = DROPS.get(key.identifier().toString());
			if (list != null) for (Entry e : list) builder.withPool(pool(e));
		});
	}

	static LootPool.Builder pool(Entry e) {
		LootItem.Builder<?> entry = LootItem.lootTableItem(e.item());
		return LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(e.drop().chance())).add(entry);
	}
}
