package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.Overhaul3;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 3.0 polish P5 (s-p5-tooltips): the stat lines of every arsenal item that has numbers (casters, the Clockwork Repeater,
 * tonics, the Glimmerstone Focus and the Moonsilk/boss armor perks). One always-visible summary line plus a "hold Shift"
 * detail block. Every number is an argument read from the code the item really runs on ({@link ArsenalStats},
 * {@link GearV2}, {@link RepeaterItem}, the caster's own use delay and Glimmer cost); the lang strings carry no digits.
 * Pure (no client classes), so the arsenal self-test checks it on the dev server. Lines are built once per item and
 * cached (tooltips are rebuilt every frame while hovered). Gear3 casters (on {@link CasterItem}) get the generic
 * cooldown line. Kill switch: {@code p5_tooltips} in config/elsewhere-overhaul3.json (read by the client hook).
 */
public final class ArsenalTooltips {
	private ArsenalTooltips() {}

	/** The always-visible summary and the Shift detail of one item. */
	public record Lines(List<Component> summary, List<Component> detail) {
		public static final Lines NONE = new Lines(List.of(), List.of());

		public boolean isEmpty() {
			return summary.isEmpty() && detail.isEmpty();
		}
	}

	public static final String HOLD = "elsewhere.p5.hold";
	private static final Map<Item, Lines> CACHE = new IdentityHashMap<>();

	/** The cached lines for this stack's item (empty for items without stats). */
	public static Lines of(ItemStack stack) {
		if (stack.isEmpty()) return Lines.NONE;
		Item item = stack.getItem();
		synchronized (CACHE) {
			Lines l = CACHE.get(item);
			if (l == null) {
				l = build(item);
				CACHE.put(item, l);
			}
			return l;
		}
	}

	/** Summary plus (Shift) the detail, or the hint line without Shift. What a player sees, minus the hint de-duplication. */
	public static List<Component> lines(ItemStack stack, boolean shift) {
		Lines l = of(stack);
		List<Component> out = new ArrayList<>(l.summary());
		if (shift) out.addAll(l.detail());
		else if (!l.detail().isEmpty()) out.add(hint());
		return out;
	}

	public static Component hint() {
		return Component.translatable(HOLD).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
	}

	// ------------------------------------------------------------------------------------------------ per item

	private static Lines build(Item item) {
		List<Component> s = new ArrayList<>(), d = new ArrayList<>();
		if (item == ArsenalItems.AMETHYST_WAND && item instanceof CasterItem c) {
			s.add(spell(ArsenalStats.WAND_DAMAGE, c.delayTicks));
			d.add(line("elsewhere.p5.wand.bolt", ArsenalStats.WAND_RANGE, ArsenalStats.WAND_SPEED));
			d.add(baseCost(c.rawCost(null)));
			d.add(Overhaul3.CALLINGS ? line("elsewhere.p5.starter", pct(Calling.STARTER_MIN_MASTERY))
				: line("elsewhere.p5.starter_legacy", ArsenalStats.STARTER_MIN_CALLING));
			d.add(line("elsewhere.p5.scaling"));
		} else if (item == ArsenalItems.SUNLANCE_STAFF && item instanceof CasterItem c) {
			s.add(spell(ArsenalStats.SUNLANCE_DAMAGE, c.delayTicks));
			d.add(line("elsewhere.p5.sunlance.line", ArsenalStats.SUNLANCE_TARGETS, ArsenalStats.SUNLANCE_RANGE));
			d.add(line("elsewhere.p5.sunlance.fire", ArsenalStats.SUNLANCE_FIRE_SECONDS));
			d.add(baseCost(ArsenalStats.SUNLANCE_COST));
			d.add(line("elsewhere.p5.sunlance.cost", ArsenalStats.SUNLANCE_COST_DAY, ArsenalStats.SUNLANCE_COST_DARK));
			d.add(line("elsewhere.p5.scaling"));
		} else if (item == ArsenalItems.STORMCOIL_STAFF && item instanceof CasterItem c) {
			s.add(spell(ArsenalStats.STORMCOIL_DAMAGE, c.delayTicks));
			d.add(line("elsewhere.p5.stormcoil.arcs", ArsenalStats.STORMCOIL_ARCS, ArsenalStats.STORMCOIL_ARC_RADIUS, ArsenalStats.STORMCOIL_ARC_DAMAGE,
				ArsenalStats.STORMCOIL_ARCS_WET));
			d.add(line("elsewhere.p5.range", ArsenalStats.STORMCOIL_RANGE));
			d.add(baseCost(c.rawCost(null)));
			d.add(con("elsewhere.p5.stormcoil.self", ArsenalStats.STORMCOIL_SELF_ARC_RADIUS, ArsenalStats.STORMCOIL_SELF_ARC_DAMAGE));
		} else if (item == ArsenalItems.SCULK_RESONATOR && item instanceof CasterItem c) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.charged", ArsenalStats.RESONATOR_DAMAGE, secs(ArsenalStats.RESONATOR_CHARGE_TICKS)));
			d.add(line("elsewhere.p5.resonator.line", ArsenalStats.RESONATOR_RANGE));
			d.add(line("elsewhere.p5.resonator.armor", pct(ArsenalStats.RESONATOR_ARMOR_IGNORED), ArsenalStats.RESONATOR_KNOCKBACK));
			d.add(baseCost(c.rawCost(null)));
			d.add(line("elsewhere.p5.scaling"));
		} else if (item == ArsenalItems.MIRRORGLASS_PRISM) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.prism", GearV2.PRISM_DPS_LOW, GearV2.PRISM_DPS_HIGH));
			d.add(line("elsewhere.p5.prism.ramp", GearV2.PRISM_DPS_HIGH, secs(GearV2.PRISM_RAMP_TICKS)));
			d.add(line("elsewhere.p5.prism.beam", GearV2.PRISM_RANGE));
			d.add(line("elsewhere.p5.prism.cost", GearV2.PRISM_COST));
			d.add(line("elsewhere.p5.scaling"));
		} else if (item == ArsenalItems.HUSHWARD_BELL) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.bell", bellAbsorb(), secs(GearV2.BELL_WARD_TICKS)));
			d.add(line("elsewhere.p5.bell.ring", secs(GearV2.BELL_CHARGE_TICKS)));
			d.add(line("elsewhere.p5.bell.silence", GearV2.BELL_RADIUS));
			d.add(baseCost(GearV2.BELL_COST));
			d.add(con("elsewhere.p5.bell.once"));
		} else if (item == ArsenalItems.CLOCKWORK_REPEATER) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.repeater", RepeaterItem.BURST, secs(RepeaterItem.RELOAD_TICKS)));
			d.add(line("elsewhere.p5.repeater.gap", secs(RepeaterItem.GAP)));
			d.add(line("elsewhere.p5.repeater.cooldown", secs(RepeaterItem.COOLDOWN + RepeaterItem.BURST * RepeaterItem.GAP)));
			d.add(con("elsewhere.p5.repeater.ench"));
		} else if (item == ArsenalItems.GLIMMER_TONIC || item == ArsenalItems.STRONG_GLIMMER_TONIC) {
			// summary = ArsenalClient's tonic line (restore, surge x, surge s: tonicArgs)
			d.add(con("elsewhere.p5.tonic.dazed", pct(ArsenalStats.DAZED_SPELL_DAMAGE), secs(ArsenalStats.DAZED_TICKS), secs(ArsenalStats.DAZED_MAX_TICKS)));
			d.add(line("elsewhere.p5.tonic.drink", secs(ArsenalStats.TONIC_DRINK_TICKS)));
		} else if (item == ArsenalItems.GLIMMERSTONE_FOCUS) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.focus", ArsenalStats.FOCUS_MAX, pct(ArsenalStats.FOCUS_REGEN)));
			d.add(line("elsewhere.p5.trinket", ArsenalStats.MAX_ACTIVE_TRINKETS));
			d.add(Overhaul3.CALLINGS ? line("elsewhere.p5.focus.needs", pct(Calling.FOCUS_MIN_MASTERY)) : line("elsewhere.p5.focus.needs_piece"));
		} else if (isMoonsilk(item)) {
			// summary = ArsenalClient's Moonlit line (moonlitArgs)
			d.add(line("elsewhere.p5.moonlit.regen", secs(ArsenalStats.REGEN_DELAY_MOONLIT_TICKS), secs(ArsenalStats.REGEN_DELAY_TICKS)));
			d.add(con("elsewhere.p5.moonlit.minions"));
		} else if (item instanceof CasterItem c && c.delayTicks > 0) {
			// another module's caster (gear3): its use delay is real CasterItem data; the Calling cost line shows its cost
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.cooldown", secs(c.delayTicks)));
		}
		if (s.isEmpty() && d.isEmpty()) return Lines.NONE;
		return new Lines(List.copyOf(s), List.copyOf(d));
	}

	static boolean isMoonsilk(Item item) {
		return item == ArsenalItems.MOONSILK_HOOD || item == ArsenalItems.MOONSILK_ROBE || item == ArsenalItems.MOONSILK_LEGGINGS || item == ArsenalItems.MOONSILK_BOOTS
			|| item == ArsenalItems.LINED_MOONSILK_HOOD || item == ArsenalItems.LINED_MOONSILK_ROBE || item == ArsenalItems.LINED_MOONSILK_LEGGINGS
			|| item == ArsenalItems.LINED_MOONSILK_BOOTS;
	}

	/** The Hushward ward in hit points: the vanilla Absorption effect's own max-absorption modifier at the Bell's amplifier. */
	public static double bellAbsorb() {
		double[] hp = {0};
		MobEffects.ABSORPTION.value().createModifiers(GearV2.BELL_ABSORB_AMPLIFIER, (attr, mod) -> {
			if (attr.is(Attributes.MAX_ABSORPTION)) hp[0] += mod.amount();
		});
		return hp[0];
	}

	/** Arguments of ArsenalClient's existing tonic line {@code elsewhere.tooltip.tonic}: restore, regen surge x, surge seconds. */
	public static Object[] tonicArgs(Item tonic) {
		return new Object[] {fmt(tonic == ArsenalItems.STRONG_GLIMMER_TONIC ? ArsenalStats.STRONG_TONIC_RESTORE : ArsenalStats.TONIC_RESTORE),
			fmt(ArsenalStats.TONIC_SURGE_MULT), fmt(secs(ArsenalStats.TONIC_SURGE_TICKS))};
	}

	/** Argument of the existing Moonlit line {@code elsewhere.tooltip.moonlit}: the next-spell bonus in percent. */
	public static Object[] moonlitArgs() {
		return new Object[] {fmt(pct(ArsenalStats.MOONLIT_BONUS - 1F))};
	}

	/** Argument of the existing Stormsage Mantle line {@code elsewhere.tooltip.stormsage_mantle}: the airborne regen multiplier. */
	public static Object[] mantleArgs() {
		return new Object[] {fmt(ArsenalStats.MANTLE_AIR_REGEN_MULT)};
	}

	/** The real use delay of a caster (0 for charged/channelled ones): exactly what {@link CasterItem#castNow} sets. */
	public static int delayTicks(CasterItem c) {
		return c.delayTicks;
	}

	// ------------------------------------------------------------------------------------------------ helpers

	private static Component spell(double damage, int delayTicks) {
		return line(ChatFormatting.GOLD, "elsewhere.p5.spell", damage, secs(delayTicks));
	}

	private static Component baseCost(int cost) {
		return line("elsewhere.p5.base_cost", cost);
	}

	private static Component line(String key, Object... args) {
		return line(ChatFormatting.GRAY, key, args);
	}

	private static Component con(String key, Object... args) {
		return line(ChatFormatting.RED, key, args);
	}

	private static Component line(ChatFormatting color, String key, Object... args) {
		Object[] a = new Object[args.length];
		for (int i = 0; i < args.length; i++) a[i] = args[i] instanceof Number n ? fmt(n.doubleValue()) : args[i];
		return Component.translatable(key, a).withStyle(st -> st.withColor(color).withItalic(false));
	}

	/** Ticks to seconds (a number, formatted by {@link #fmt}). */
	public static double secs(int ticks) {
		return ticks / 20.0;
	}

	/** A fraction as a percentage number (0.4 -> 40). */
	public static double pct(double fraction) {
		return fraction * 100.0;
	}

	/** Up to two decimals, trailing zeros dropped: 10, 0.4, 1.25, 12.5. Locale-free. */
	public static String fmt(double v) {
		double r = Math.round(v * 100.0) / 100.0;
		if (r == 0) r = 0; // no "-0"
		return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
	}
}
