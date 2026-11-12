package dev.elsewhere.gear2;

import dev.elsewhere.gear2.item.SteamAuger;
import dev.elsewhere.stub.gear2.TrinketStub;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 3.0 polish P5 (s-p5-tooltips): stat lines for every gear2 weapon, tool, trinket and armor set. One always-visible
 * summary (the special move with its numbers) plus a "hold Shift" detail block. Every number is an argument read from
 * the code the item runs on ({@link Gear2Stats}, the stack's own attribute modifiers, Termites' real face, the Auger's
 * fuel and the Spool's anchor); the lang strings and the reworded lore carry no digits, so a rebalance can never leave a
 * stale number. Pure (no client classes), checked by the gear2 self-test; static lines are built once per item.
 * Kill switch: {@code p5_tooltips} in config/elsewhere-overhaul3.json (read by the client hook).
 */
public final class Gear2Tooltips {
	private Gear2Tooltips() {}

	public record Lines(List<Component> summary, List<Component> detail) {
		public static final Lines NONE = new Lines(List.of(), List.of());

		public boolean isEmpty() {
			return summary.isEmpty() && detail.isEmpty();
		}
	}

	public static final String HOLD = "elsewhere.p5.hold";
	private static final Map<Item, Lines> CACHE = new IdentityHashMap<>();

	/** Lines for this stack: the cached per-item lines, plus the per-stack ones (Auger fuel, Spool anchor) when it has them. */
	public static Lines of(ItemStack stack) {
		if (stack.isEmpty()) return Lines.NONE;
		Item item = stack.getItem();
		Lines base;
		synchronized (CACHE) {
			base = CACHE.get(item);
			if (base == null) {
				base = build(item);
				CACHE.put(item, base);
			}
		}
		Component extra = perStack(stack);
		if (extra == null) return base;
		List<Component> d = new ArrayList<>(base.detail());
		d.add(extra);
		return new Lines(base.summary(), d);
	}

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
		if (item == Gear2Items.IRONCLAD_FLAIL) {
			double hit = attackDamage(item), peak = Gear2Stats.FLAIL_CHAIN_STEP * Gear2Stats.FLAIL_CHAIN_MAX;
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.flail", pct(Gear2Stats.FLAIL_CHAIN_STEP), secs(Gear2Stats.FLAIL_CHAIN_WINDOW), pct(peak)));
			d.add(line("elsewhere.p5.flail.peak", hit * (1 + peak), hit));
			d.add(con("elsewhere.p5.flail.backward", pct(-Gear2Stats.FLAIL_BACKWARD_SLOW)));
		} else if (item == Gear2Items.GALE_RAPIER) {
			double hit = attackDamage(item);
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.rapier", Gear2Stats.RAPIER_LUNGE, hit + Gear2Stats.RAPIER_LUNGE));
			d.add(con("elsewhere.p5.rapier.still", pct(Gear2Stats.RAPIER_STILL_MULT), hit * Gear2Stats.RAPIER_STILL_MULT));
		} else if (item == Gear2Items.MIRRORBOLT_ARBALEST) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.arbalest", Gear2Stats.ARBALEST_DAMAGE, Gear2Stats.ARBALEST_RELOAD_SECONDS, Gear2Stats.ARBALEST_PIERCE));
			d.add(line("elsewhere.p5.range", Gear2Stats.ARBALEST_RANGE));
			d.add(line("elsewhere.p5.arbalest.quick"));
		} else if (item == Gear2Items.STEAM_AUGER) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.auger", Gear2Stats.AUGER_SPEED, Gear2Stats.AUGER_SPEED / Gear2Stats.NETHERITE_PICK_SPEED));
			d.add(con("elsewhere.p5.auger.reach", reach(item)));
		} else if (item == Gear2Items.TERMITE_HIVE_LANTERN) {
			int side = faceSide();
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.termite", side, side, secs(Gear2Stats.TERMITE_SPREAD_TICKS)));
			d.add(line("elsewhere.p5.termite.speed", Gear2Stats.TERMITE_SPEED, pct(Gear2Stats.TERMITE_SPEED / Gear2Stats.NETHERITE_PICK_SPEED)));
		} else if (item == Gear2Items.SEERS_HOURGLASS) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.hourglass", secs(Gear2Stats.HOURGLASS_LEAD_TICKS)));
			d.add(line("elsewhere.p5.trinket", TrinketStub.MAX_ACTIVE));
		} else if (item == Gear2Items.PROSPECTORS_PENDULUM) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.pendulum", Gear2Stats.PENDULUM_RANGE, Gear2Stats.PENDULUM_TYPES));
			d.add(line("elsewhere.p5.pendulum.swing", secs(Gear2Stats.PENDULUM_PING_TICKS)));
			d.add(line("elsewhere.p5.trinket", TrinketStub.MAX_ACTIVE));
		} else if (item == Gear2Items.VOID_TETHER_SPOOL) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.tether", Gear2Stats.TETHER_RANGE, Gear2Stats.TETHER_HUNGER, Gear2Stats.TETHER_COOLDOWN / 1200.0));
		} else if (isSet(item, Gear2Items.COILMAIL_HELMET, Gear2Items.COILMAIL_CHESTPLATE, Gear2Items.COILMAIL_LEGGINGS, Gear2Items.COILMAIL_BOOTS)) {
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.capacitor", Gear2Stats.CAPACITOR_MAX, secs(Gear2Stats.CAPACITOR_FADE_TICKS)));
			d.add(line("elsewhere.p5.capacitor.discharge", Gear2Stats.CAPACITOR_DAMAGE_PER_CHARGE, Gear2Stats.CAPACITOR_TARGETS, Gear2Stats.CAPACITOR_RADIUS));
			d.add(con("elsewhere.p5.coilmail.lightning", pct(Gear2Stats.COILMAIL_LIGHTNING_PER_PIECE)));
		} else if (isSet(item, Gear2Items.AMBERSTILL_HELMET, Gear2Items.AMBERSTILL_CHESTPLATE, Gear2Items.AMBERSTILL_LEGGINGS, Gear2Items.AMBERSTILL_BOOTS)) {
			double worn = setTotal(Attributes.ARMOR_TOUGHNESS, Gear2Items.AMBERSTILL_HELMET, Gear2Items.AMBERSTILL_CHESTPLATE, Gear2Items.AMBERSTILL_LEGGINGS,
				Gear2Items.AMBERSTILL_BOOTS);
			s.add(line(ChatFormatting.GOLD, "elsewhere.p5.brace", Gear2Stats.STILLNESS_TOUGHNESS, secs(Gear2Stats.STILLNESS_TICKS), worn + Gear2Stats.STILLNESS_TOUGHNESS));
			d.add(line(Overhaul3.REBALANCE_GEAR2 ? "elsewhere.p5.brace.keep" : "elsewhere.p5.brace.legacy"));
			d.add(con("elsewhere.p5.brace.sprint", worn));
		}
		if (s.isEmpty() && d.isEmpty()) return Lines.NONE;
		return new Lines(List.copyOf(s), List.copyOf(d));
	}

	/** Per-stack line: the Auger's fuel left, the Spool's anchor. Null for everything else. */
	static Component perStack(ItemStack stack) {
		if (stack.is(Gear2Items.STEAM_AUGER)) return line("elsewhere.p5.auger.fuel", Gear2Stats.AUGER_BLOCKS_PER_COAL, SteamAuger.fuel(stack));
		if (stack.is(Gear2Items.VOID_TETHER_SPOOL)) {
			GlobalPos a = stack.get(Gear2Items.TETHER_ANCHOR);
			return a == null ? line("elsewhere.p5.tether.none")
				: line("elsewhere.p5.tether.anchor", a.pos().getX(), a.pos().getY(), a.pos().getZ(), a.dimension().identifier().toString());
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------ real stats

	/** Main-hand attack damage of the item as the game computes it (player base 1 + the item's modifiers). */
	public static double attackDamage(Item item) {
		return new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE.value().getDefaultValue(), EquipmentSlot.MAINHAND);
	}

	/** Block reach with the item in the main hand (player default + the item's modifier). */
	public static double reach(Item item) {
		return new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.BLOCK_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE.value().getDefaultValue(), EquipmentSlot.MAINHAND);
	}

	/** The total of one attribute over a set's pieces, each in its own armor slot. */
	public static double setTotal(Holder<Attribute> attr, Item... pieces) {
		double t = 0;
		for (Item i : pieces) {
			ItemStack st = new ItemStack(i);
			var eq = st.get(DataComponents.EQUIPPABLE);
			if (eq == null) continue;
			t += st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(attr, 0.0, eq.slot());
		}
		return t;
	}

	/** Side of the square face the termites eat: Termites' own neighbour list plus the mined block. */
	public static int faceSide() {
		return (int) Math.round(Math.sqrt(Termites.face(BlockPos.ZERO, Direction.Axis.Y).size() + 1));
	}

	static boolean isSet(Item item, Item... pieces) {
		for (Item p : pieces) if (item == p) return true;
		return false;
	}

	// ------------------------------------------------------------------------------------------------ helpers

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

	public static double secs(int ticks) {
		return ticks / 20.0;
	}

	public static double pct(double fraction) {
		return fraction * 100.0;
	}

	/** Up to two decimals, trailing zeros dropped: 7, 0.15, 12.5. Locale-free. */
	public static String fmt(double v) {
		double r = Math.round(v * 100.0) / 100.0;
		if (r == 0) r = 0;
		return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
	}
}
