package dev.elsewhere.boss_ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * 3.0 polish P5: builds the "hold Shift" detail block for an item. Every number is read live from the stack's attribute
 * modifiers and data components, so gear rebalancing shows up with no change here. Pure (no client classes) so the
 * self-test can run it on a dev server. Hover-only work, nothing per frame.
 */
public final class TooltipLines {
	private TooltipLines() {}

	private static final EquipmentSlotGroup[] HAND_GROUPS = {EquipmentSlotGroup.MAINHAND};
	private static final EquipmentSlotGroup[] ARMOR_GROUPS = {EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};

	/** Empty list = nothing to say for this item. Without shift only the hint line is returned. */
	public static List<Component> build(ItemStack stack, boolean shift) {
		List<Component> out = new ArrayList<>();
		if (stack.isEmpty()) return out;
		double[] d = eval(stack, Attributes.ATTACK_DAMAGE, HAND_GROUPS);
		double[] sp = eval(stack, Attributes.ATTACK_SPEED, HAND_GROUPS);
		double[] ar = eval(stack, Attributes.ARMOR, ARMOR_GROUPS);
		double[] ta = eval(stack, Attributes.ARMOR_TOUGHNESS, ARMOR_GROUPS);
		double[] kb = eval(stack, Attributes.KNOCKBACK_RESISTANCE, ARMOR_GROUPS);
		boolean hasDmg = d[1] > 0.5, hasArmor = ar[1] > 0.5 && fold(ar, 0.0) > 0.0;
		double hit = fold(d, 1.0), rate = Math.max(0.0, fold(sp, 4.0));
		var cd = stack.get(DataComponents.USE_COOLDOWN);
		int maxDmg = stack.getMaxDamage();
		if (!hasDmg && !hasArmor && cd == null && maxDmg <= 0) return out;
		if (!shift) {
			out.add(Component.translatable("elsewhere.polish.tooltip.hold").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return out;
		}
		if (hasDmg) {
			out.add(Component.translatable("elsewhere.polish.tooltip.hit", fmt(hit)).withStyle(ChatFormatting.GOLD));
			out.add(Component.translatable("elsewhere.polish.tooltip.dps", fmt(hit * rate), fmt(rate)).withStyle(ChatFormatting.GOLD));
		}
		if (hasArmor) {
			out.add(Component.translatable("elsewhere.polish.tooltip.armor", fmt(fold(ar, 0.0)), fmt(fold(ta, 0.0))).withStyle(ChatFormatting.BLUE));
			if (kb[1] > 0.5 && fold(kb, 0.0) > 0.0) out.add(Component.translatable("elsewhere.polish.tooltip.kb", fmt(fold(kb, 0.0) * 100.0)).withStyle(ChatFormatting.BLUE));
		}
		if (cd != null) out.add(Component.translatable("elsewhere.polish.tooltip.cooldown", fmt(cd.seconds())).withStyle(ChatFormatting.AQUA));
		if (maxDmg > 0) out.add(Component.translatable("elsewhere.polish.tooltip.uses", maxDmg - stack.getDamageValue(), maxDmg).withStyle(ChatFormatting.GRAY));
		return out;
	}

	/** {add, found, 1+mulBase, mulTotal} for one attribute, folded vanilla-style. */
	static double[] eval(ItemStack stack, Holder<Attribute> attribute, EquipmentSlotGroup[] groups) {
		double[] r = {0.0, 0.0, 1.0, 1.0};
		for (EquipmentSlotGroup group : groups) {
			stack.forEachModifier(group, (attr, m, display) -> {
				if (!attr.is(attribute)) return;
				r[1] = 1.0;
				switch (m.operation()) {
					case ADD_VALUE -> r[0] += m.amount();
					case ADD_MULTIPLIED_BASE -> r[2] += m.amount();
					case ADD_MULTIPLIED_TOTAL -> r[3] *= 1.0 + m.amount();
				}
			});
		}
		return r;
	}

	/** Player base value plus the folded modifiers (base 1 attack damage, 4 attack speed, 0 for armor stats). */
	static double fold(double[] r, double base) {
		return (base + r[0]) * r[2] * r[3];
	}

	static String fmt(double v) {
		return Math.abs(v - Math.rint(v)) < 0.05 ? Integer.toString((int) Math.rint(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}
}
