package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.petalwind.PetalwindItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.FabricComponentMapBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * ARSENAL.md beta-2 hooks that depend on other modules by id only (skip cleanly when absent):
 * <ul>
 * <li>{@code elsewhere:dodge_chance} as real item attribute modifiers (shown in tooltips like vanilla ones): Galeweave
 * +0.03 per piece in its armor slot, Kaze Katana +0.10 in the main hand. Added through Fabric's
 * {@code DefaultItemComponentEvents.MODIFY}, which runs after every mod initialiser, so the arsenal module's attribute is
 * registered by then; the arsenal module implements the dodge itself.</li>
 * <li>item lookups for trades/drops of arsenal and summoner items, and the {@code elsewhere:spell_power} multiplier for
 * spell items (Heliograph Lens).</li>
 * </ul>
 */
public final class ArsenalHooks {
	private ArsenalHooks() {}

	/** For the self-test: which items got a dodge modifier (empty when the attribute is absent). */
	public static final List<Item> DODGE_APPLIED = new ArrayList<>();
	public static boolean dodgeSkipped;

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(ctx -> {
			Optional<Holder.Reference<Attribute>> dodge = attribute("dodge_chance");
			if (dodge.isEmpty()) {
				dodgeSkipped = true;
				PetalwindGlasswasteContent.LOGGER.info("elsewhere:dodge_chance not registered (arsenal module absent): Galeweave/Kaze Katana dodge skipped");
				return;
			}
			Holder<Attribute> attr = dodge.get();
			addDodge(ctx, attr, PetalwindItems.GALEWEAVE_HOOD, "pg_galeweave_dodge_head", GearStats.GALEWEAVE_DODGE_PER_PIECE, EquipmentSlotGroup.HEAD);
			addDodge(ctx, attr, PetalwindItems.GALEWEAVE_ROBE, "pg_galeweave_dodge_chest", GearStats.GALEWEAVE_DODGE_PER_PIECE, EquipmentSlotGroup.CHEST);
			addDodge(ctx, attr, PetalwindItems.GALEWEAVE_LEGGINGS, "pg_galeweave_dodge_legs", GearStats.GALEWEAVE_DODGE_PER_PIECE, EquipmentSlotGroup.LEGS);
			addDodge(ctx, attr, PetalwindItems.GALEWEAVE_BOOTS, "pg_galeweave_dodge_feet", GearStats.GALEWEAVE_DODGE_PER_PIECE, EquipmentSlotGroup.FEET);
			addDodge(ctx, attr, PetalwindItems.KAZE_KATANA, "pg_kaze_katana_dodge", GearStats.KAZE_KATANA_DODGE, EquipmentSlotGroup.MAINHAND);
		});
	}

	private static void addDodge(DefaultItemComponentEvents.ModifyContext ctx, Holder<Attribute> attr, Item item, String modId, double amount,
		EquipmentSlotGroup group) {
		ctx.modify(item, builder -> {
			ItemAttributeModifiers cur = ((FabricComponentMapBuilder) (Object) builder).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
			builder.set(DataComponents.ATTRIBUTE_MODIFIERS, withModifier(cur, attr, Reg.id(modId), amount, group));
		});
		DODGE_APPLIED.add(item);
	}

	/** Pure helper (self-tested): adds one ADD_VALUE modifier, replacing an older one with the same id. */
	public static ItemAttributeModifiers withModifier(ItemAttributeModifiers base, Holder<Attribute> attr, Identifier id, double amount,
		EquipmentSlotGroup group) {
		return base.withModifierAdded(attr, new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE), group);
	}

	public static Optional<Item> item(String path) {
		Identifier id = Reg.id(path);
		return BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(BuiltInRegistries.ITEM.getValue(id)) : Optional.empty();
	}

	public static ItemStack stack(String path, int count) {
		return item(path).map(i -> new ItemStack(i, count)).orElse(ItemStack.EMPTY);
	}

	public static Optional<Holder.Reference<Attribute>> attribute(String path) {
		return BuiltInRegistries.ATTRIBUTE.get(Reg.id(path));
	}

	/** The entity's value of an {@code elsewhere:} attribute, or {@code def} when it isn't registered or not on the entity. */
	public static double value(LivingEntity e, String path, double def) {
		Optional<Holder.Reference<Attribute>> a = attribute(path);
		if (a.isEmpty()) return def;
		AttributeInstance inst = e.getAttribute(a.get());
		return inst == null ? def : inst.getValue();
	}

	/** Spell damage multiplier from {@code elsewhere:spell_power} (1.0 without the arsenal module). */
	public static float spellPower(LivingEntity e) {
		return (float) Math.max(0.0, value(e, "spell_power", 1.0));
	}

	/** Failed Glimmer spend: the item does nothing and says why (ARSENAL §1; the arsenal pool also flashes its bar). */
	public static void notEnoughGlimmer(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.elsewhere.not_enough_glimmer"));
	}
}
