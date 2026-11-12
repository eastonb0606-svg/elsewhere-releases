package dev.elsewhere.core.gear;

import dev.elsewhere.Elsewhere;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Terraria-style armor sets. Per-piece perks stay on the items themselves (attribute modifiers, components);
 * a registered {@link ArmorSet} adds a full-set bonus that is applied while every piece is worn and removed
 * cleanly the moment one comes off. Tooltips show the set name, pieces worn and the bonus (client side).
 *
 * <pre>
 * ArmorSets.register(new ArmorSets.ArmorSet(Elsewhere.id("paleweave"), List.of(HELM, CHEST, LEGS, BOOTS),
 *     List.of(new ArmorSets.Bonus(Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),
 *     List.of(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false)),
 *     player -> {},  // optional per-second ability hook
 *     List.of(Component.translatable("set.elsewhere.paleweave.bonus"))));
 * </pre>
 */
public final class ArmorSets {
	public record Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
	}

	public record ArmorSet(Identifier id, List<Item> pieces, List<Bonus> bonuses, List<Supplier<MobEffectInstance>> effects,
		@Nullable Consumer<ServerPlayer> ability, List<Component> description) {
		public int worn(final LivingEntity entity) {
			int n = 0;
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot.isArmor() && this.pieces.contains(entity.getItemBySlot(slot).getItem())) {
					n++;
				}
			}
			return n;
		}

		public Identifier modifierId(final int index) {
			return Identifier.fromNamespaceAndPath(this.id.getNamespace(), "set_bonus/" + this.id.getPath() + "/" + index);
		}
	}

	private static final Map<Identifier, ArmorSet> SETS = new LinkedHashMap<>();

	private ArmorSets() {
	}

	public static ArmorSet register(final ArmorSet set) {
		SETS.put(set.id(), set);
		return set;
	}

	public static Collection<ArmorSet> all() {
		return SETS.values();
	}

	public static @Nullable ArmorSet setOf(final ItemStack stack) {
		for (ArmorSet s : SETS.values()) {
			if (s.pieces().contains(stack.getItem())) {
				return s;
			}
		}
		return null;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0 || SETS.isEmpty()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				update(player, server.getTickCount() % 20 == 0);
			}
		});
	}

	/** Applies or removes every set's bonus for one player. Public for tests. */
	public static void update(final ServerPlayer player, final boolean runAbility) {
		for (ArmorSet set : SETS.values()) {
			boolean full = set.worn(player) == set.pieces().size();
			List<Bonus> bonuses = set.bonuses();
			for (int i = 0; i < bonuses.size(); i++) {
				Bonus b = bonuses.get(i);
				AttributeInstance inst = player.getAttribute(b.attribute());
				if (inst == null) {
					continue;
				}
				Identifier mid = set.modifierId(i);
				boolean has = inst.getModifier(mid) != null;
				if (full && !has) {
					inst.addTransientModifier(new AttributeModifier(mid, b.amount(), b.operation()));
				} else if (!full && has) {
					inst.removeModifier(mid);
				}
			}
			if (full) {
				for (Supplier<MobEffectInstance> e : set.effects()) {
					player.addEffect(e.get());
				}
				if (runAbility && set.ability() != null) {
					set.ability().accept(player);
				}
			}
		}
	}

	/** Tooltip lines for a piece (called from the client tooltip callback). */
	public static List<Component> tooltip(final ItemStack stack, final @Nullable LivingEntity wearer) {
		List<Component> lines = new ArrayList<>();
		ArmorSet set = setOf(stack);
		if (set == null) {
			return lines;
		}
		int worn = wearer == null ? 0 : set.worn(wearer);
		lines.add(Component.translatable("tooltip.elsewhere.set", Component.translatable("set." + set.id().getNamespace() + "." + set.id().getPath()),
			worn, set.pieces().size()).withStyle(worn == set.pieces().size() ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.DARK_GRAY));
		for (Component c : set.description()) {
			lines.add(Component.literal("  ").append(c).withStyle(worn == set.pieces().size() ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	static {
		Elsewhere.LOG.debug("ArmorSets ready");
	}
}
