package dev.elsewhere.boss.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The one boss drop rule (DECISIONS-2.0 {@code bal-loot-standard} + {@code bal-loot-pity}), reusable by every boss.
 *
 * <p><b>Rates.</b> First kill (per player): signature 100%, every other unique 50%. Later kills: signature 67%, every
 * other unique 33%. Bad-luck protection: each miss of a non-signature unique adds +10% to that unique's chance for that
 * player, up to a 60% ceiling; a drop resets it. Extra-life items ({@link Kind#EXTRA_LIFE}, e.g. Ankh, Mournmaw's
 * Blessing) keep a low repeat rate: 100% on the first kill, 5% afterwards, no pity.
 *
 * <p><b>Using it from a boss</b> (one call in the boss's own loot method, then add its guaranteed non-unique drops):
 * <pre>
 * out.addAll(BossRewards.rollUniques(player, bossId, firstKill, List.of(
 *     DropRule.signature("tyrants_tusk", () -> new ItemStack(TUSK)),
 *     DropRule.other("ashtusk_harness", () -> new ItemStack(HARNESS)),
 *     DropRule.extraLife("blessing", () -> new ItemStack(BLESSING))));
 * </pre>
 * Then deliver with {@link BossRewards#dropLoot} (ground drops, no chests) and {@link BossRewards#awardXp}.
 * {@code uniqueId} only needs to be stable per boss (it keys the pity counter). Exactly one entry should be the
 * signature. The pure {@link #roll} works on a plain miss map so it can be simulated in tests.
 */
public final class DropRule {
	private DropRule() {}

	public static final float FIRST_SIGNATURE = 1.0F, FIRST_OTHER = 0.5F;
	public static final float REPEAT_SIGNATURE = 0.67F, REPEAT_OTHER = 0.33F;
	public static final float PITY_STEP = 0.10F, PITY_CAP = 0.60F;
	public static final float EXTRA_LIFE_FIRST = 1.0F, EXTRA_LIFE_REPEAT = 0.05F;

	public enum Kind { SIGNATURE, OTHER, EXTRA_LIFE }

	public record Unique(String id, Kind kind, Supplier<ItemStack> stack) {}

	public static Unique signature(String id, Supplier<ItemStack> s) { return new Unique(id, Kind.SIGNATURE, s); }

	public static Unique other(String id, Supplier<ItemStack> s) { return new Unique(id, Kind.OTHER, s); }

	public static Unique extraLife(String id, Supplier<ItemStack> s) { return new Unique(id, Kind.EXTRA_LIFE, s); }

	/** Chance of one unique dropping. {@code misses} = consecutive misses of this unique by this player (ignored for signature / extra-life). */
	public static float chance(Kind kind, boolean firstKill, int misses) {
		return switch (kind) {
			case SIGNATURE -> firstKill ? FIRST_SIGNATURE : REPEAT_SIGNATURE;
			case EXTRA_LIFE -> firstKill ? EXTRA_LIFE_FIRST : EXTRA_LIFE_REPEAT;
			case OTHER -> {
				float base = firstKill ? FIRST_OTHER : REPEAT_OTHER;
				yield Math.max(base, Math.min(PITY_CAP, base + PITY_STEP * misses));
			}
		};
	}

	/** Pure roll. {@code misses} is read and updated (keys = unique ids). */
	public static List<ItemStack> roll(boolean firstKill, Map<String, Integer> misses, List<Unique> uniques, RandomSource random) {
		List<ItemStack> out = new ArrayList<>();
		for (Unique u : uniques) {
			int m = u.kind() == Kind.OTHER ? misses.getOrDefault(u.id(), 0) : 0;
			if (random.nextFloat() < chance(u.kind(), firstKill, m)) {
				out.add(u.stack().get());
				if (u.kind() == Kind.OTHER) misses.put(u.id(), 0);
			} else if (u.kind() == Kind.OTHER) {
				misses.put(u.id(), m + 1);
			}
		}
		return out;
	}
}
