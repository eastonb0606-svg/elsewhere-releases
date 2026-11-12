package dev.elsewhere.palehollow;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.boss.BossDrops;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModItems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.FabricComponentMapBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Links to items and attributes owned by the arsenal, summoner and greenhaze_hush modules (ARSENAL.md), all by id lookup
 * so the core runs without them: Heartwood Greatbow +1 projectile_pierce, Echo-Forged Blade +2 kill_heal, Moon-Eye
 * marks for the arsenal's +20% spell bonus, Moonsilk Thread drops, and the boss uniques Elder Bark Crown (Mother Creak)
 * and Rootcall Staff (Selenne) at 50% on your first kill, 20% after.
 */
public final class ArsenalLinks {
	public static final Identifier PROJECTILE_PIERCE = Elsewhere.id("projectile_pierce");
	public static final Identifier KILL_HEAL = Elsewhere.id("kill_heal");
	public static final Identifier MOONSILK_THREAD = Elsewhere.id("moonsilk_thread");
	public static final Identifier ELDER_BARK_CROWN = Elsewhere.id("elder_bark_crown");
	public static final Identifier ROOTCALL_STAFF = Elsewhere.id("rootcall_staff");
	public static final Identifier ECHO_FORGED_BLADE = Elsewhere.id("echo_forged_blade");

	/** For the self-test: what was applied (empty entries mean "skipped, absent in this build"). */
	public static final List<String> APPLIED = new ArrayList<>();

	private ArsenalLinks() {
	}

	public static Optional<Item> item(final Identifier id) {
		return BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(BuiltInRegistries.ITEM.getValue(id)) : Optional.empty();
	}

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(ctx -> {
			BuiltInRegistries.ATTRIBUTE.get(PROJECTILE_PIERCE).ifPresent(attr -> {
				addModifier(ctx, ModItems.HEARTWOOD_GREATBOW, attr, Elsewhere.id("greatbow_pierce"), GearStats.GREATBOW_PIERCE);
				APPLIED.add("heartwood_greatbow:projectile_pierce");
			});
			Optional<Holder.Reference<Attribute>> heal = BuiltInRegistries.ATTRIBUTE.get(KILL_HEAL);
			Optional<Item> blade = item(ECHO_FORGED_BLADE);
			if (heal.isPresent() && blade.isPresent()) {
				addModifier(ctx, blade.get(), heal.get(), Elsewhere.id("echo_forged_kill_heal"), GearStats.ECHO_FORGED_KILL_HEAL);
				APPLIED.add("echo_forged_blade:kill_heal");
			}
		});
		// 2.0: the Elder Bark Crown and Rootcall Staff are boss-kit uniques of Mother Creak / Selenne (kitUniques()), not BossDrops.
	}

	/** Adds one main-hand ADD_VALUE modifier on top of the item's existing ones (a sword keeps its damage and speed). */
	private static void addModifier(final DefaultItemComponentEvents.ModifyContext ctx, final Item item, final Holder<Attribute> attr,
		final Identifier id, final double amount) {
		ctx.modify(item, builder -> {
			ItemAttributeModifiers cur = ((FabricComponentMapBuilder)(Object)builder).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
			builder.set(DataComponents.ATTRIBUTE_MODIFIERS,
				cur.withModifierAdded(attr, new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
		});
	}

	/** Extra boss loot per participant (the Elder Bark Crown moved to {@link BossDrops} with first-kill/repeat rates). */
	public static List<ItemStack> motherCreakExtras(final RandomSource random) {
		return List.of();
	}

	/** Selenne: 4-8 Moonsilk Thread per participant (the Rootcall Staff is a {@link BossDrops} unique). */
	public static List<ItemStack> selenneExtras(final RandomSource random) {
		return item(MOONSILK_THREAD).map(i -> List.of(new ItemStack(i, 4 + random.nextInt(5)))).orElse(List.of());
	}

	/** Eyeblossom Wisp: Moonsilk Thread 40%. */
	public static List<ItemStack> wispExtras(final RandomSource random) {
		return item(MOONSILK_THREAD).filter(i -> random.nextFloat() < 0.4F).map(i -> List.of(new ItemStack(i))).orElse(List.of());
	}

	private static Method markMethod;
	private static boolean markLooked;

	/**
	 * Tells the arsenal (if present) that this player's Moon-Eye marked the mob, so its spells deal +20% to it while the
	 * Moon-Eye is an active trinket (caster only: spells, not minions or melee; the arsenal applies the bonus).
	 * Returns false when the arsenal is absent.
	 */
	public static boolean markForSpells(final LivingEntity target, final Player by) {
		if (!markLooked) {
			markLooked = true;
			try {
				markMethod = Class.forName("dev.elsewhere.arsenal.spell.Spells").getMethod("mark", LivingEntity.class, Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				markMethod = null;
			}
		}
		if (markMethod == null) {
			return false;
		}
		try {
			markMethod.invoke(null, target, by);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
