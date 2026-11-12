package dev.elsewhere.gear3.s3.sunglass;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Sunglass Arrow (GEAR.md A5, Marksman / Trapper, G3): "passes glass, fire 3 s, +25% vs undead".
 * <ul>
 * <li>Glass: the arrow flies straight through every block in {@link #PASSES} ({@code #elsewhere:sunglass_arrow_passes}: glass blocks,
 * stained and tinted glass, glass panes) and stops at the first other block or entity. Done inside the arrow's own movement
 * ({@code mixin/SunglassArrowMixin}: its flight ray and its "am I inside a block" check skip glass), so nothing scans or polls.</li>
 * <li>Fire: a hit that deals damage sets the target alight for {@link #FIRE_SECONDS} s.</li>
 * <li>Undead ({@code #minecraft:undead}): the hit deals x{@link #UNDEAD_MULT}.</li>
 * <li>Bows, crossbows (it is in {@code #minecraft:arrows}) and dispensers shoot it; it picks up as a Sunglass Arrow.</li>
 * </ul>
 * Item {@code elsewhere:sunglass_arrow} comes from the gear3 catalogue (mechanic "sunglass_arrow" = {@link SunglassArrowItem}); this
 * class registers the entity type {@code elsewhere:sunglass_arrow} and the dispenser behaviour. Merge entry: {@link #init()} from
 * Gear3Content. Client: {@code SunglassRenderers.init()} from Gear3Client. Self-test: {@link SunglassSelfTest}.
 * Kill switch: key {@code sunglass_arrow} in {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.sunglass_arrow=false}):
 * off = the item and entity stay registered (worlds keep loading, stacks stay valid) but it flies and hits like a plain arrow
 * (glass stops it, no fire, no undead bonus, no trail). The state rides on the arrow as synced entity data, so a client with
 * another config still predicts the server's flight.
 */
public final class SunglassArrows {
	private SunglassArrows() {}

	public static final String ID = "sunglass_arrow";
	/** GEAR.md: "fire 3 s". */
	public static final float FIRE_SECONDS = 3.0F;
	/** GEAR.md: "+25% vs undead". */
	public static final float UNDEAD_MULT = 1.25F;
	/** Blocks the arrow flies through. */
	public static final TagKey<Block> PASSES = TagKey.create(Registries.BLOCK, Gear3Content.id("sunglass_arrow_passes"));
	/** Kill switch {@code sunglass_arrow}, read once. */
	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on(ID);

	public static EntityType<SunglassArrow> TYPE;
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Gear3Content.id(ID));
		// same size, tracking range and update rate as the vanilla arrow and spectral arrow
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<SunglassArrow>of(SunglassArrow::new, MobCategory.MISC)
			.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20).build(key));
		Item item = item();
		if (item != null) DispenserBlock.registerProjectileBehavior(item);
	}

	/** The catalogue item, or null when the catalogue has none. */
	public static Item item() {
		return Gear3Items.BY_ID.get(ID);
	}

	public static ItemStack stack() {
		Item i = item();
		return i == null ? ItemStack.EMPTY : new ItemStack(i);
	}

	/** Undead in the vanilla sense ({@code #minecraft:undead}: zombies, skeletons, the Wither, phantoms...). */
	public static boolean undead(Entity e) {
		return e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
	}

	/** Damage of one Sunglass Arrow hit on {@code target} before armor: x1.25 on undead. */
	public static float damageAgainst(Entity target, float amount) {
		return undead(target) ? amount * UNDEAD_MULT : amount;
	}
}
