package dev.elsewhere.core.boss;

import dev.elsewhere.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Per-participant boss uniques with a first-kill and a repeat chance (BALANCE G7 / ARSENAL.md: e.g. 50% on your first
 * kill, 20% after). Kill counts are saved per player ({@code elsewhere:boss_kills}, by entity type id). Items are
 * suppliers so drops owned by other modules can be looked up by id and skipped when absent.
 */
public final class BossDrops {
	private BossDrops() {
	}

	public record Unique(String name, Supplier<@Nullable Item> item, float firstKillChance, float repeatChance) {
	}

	private static final Map<String, List<Unique>> UNIQUES = new HashMap<>();
	/** Self-test hook: forces the next rolls (0..1). */
	public static java.util.function.@Nullable DoubleSupplier rollOverride;

	public static void register(final EntityType<?> boss, final Unique unique) {
		UNIQUES.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(boss).toString(), k -> new ArrayList<>()).add(unique);
	}

	public static List<Unique> uniques(final EntityType<?> boss) {
		return UNIQUES.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(boss).toString(), List.of());
	}

	public static int kills(final ServerPlayer player, final EntityType<?> boss) {
		Map<String, Integer> m = player.getAttached(ModAttachments.BOSS_KILLS);
		return m == null ? 0 : m.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(boss).toString(), 0);
	}

	public static void recordKill(final ServerPlayer player, final EntityType<?> boss) {
		Map<String, Integer> m = new HashMap<>(player.getAttachedOrElse(ModAttachments.BOSS_KILLS, Map.of()));
		m.merge(BuiltInRegistries.ENTITY_TYPE.getKey(boss).toString(), 1, Integer::sum);
		player.setAttached(ModAttachments.BOSS_KILLS, m);
	}

	/** Rolls this player's uniques for one kill (first kill = no recorded kill yet). Null player: repeat chance. */
	public static List<ItemStack> roll(final EntityType<?> boss, final @Nullable ServerPlayer player, final RandomSource random) {
		boolean first = player != null && kills(player, boss) == 0;
		List<ItemStack> out = new ArrayList<>();
		for (Unique u : uniques(boss)) {
			Item item = u.item().get();
			if (item == null) {
				continue;
			}
			double roll = rollOverride != null ? rollOverride.getAsDouble() : random.nextDouble();
			if (roll < (first ? u.firstKillChance() : u.repeatChance())) {
				out.add(new ItemStack(item));
			}
		}
		return out;
	}
}
