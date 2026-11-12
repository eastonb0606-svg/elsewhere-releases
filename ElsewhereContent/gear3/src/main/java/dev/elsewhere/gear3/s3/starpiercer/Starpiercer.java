package dev.elsewhere.gear3.s3.starpiercer;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Mechanics;
import dev.elsewhere.gear3.Gear3Scheduler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Aphelion's Starpiercer (GEAR.md A5, Marksman / Sniper, G5, "Aphelion 30%"): a bow with a 1.6 s draw, arrows x1.6 faster,
 * pierce +2, no gravity for the first 32 blocks, sprinting cancels the draw, and a longbow zoom (client).
 * <p>
 * Plain arrows become {@link StarpiercerArrow} (its own entity, texture and star trail). Every other arrow (tipped, spectral,
 * the 3.0 special arrows) keeps its own entity and only gets the flight rules: speed, pierce and the weightless 32 blocks
 * ({@link #fly}). Nothing ticks here: the item reacts to use/release, the own arrow measures its own flight, and a foreign
 * arrow gets one scheduled gravity restore (plus a tag that heals it on load if the server stopped first).
 * <p>
 * Merge entry: {@link #register()}, called from {@code Gear3Content.init()} BEFORE {@code Gear3Items.init()} (it adds the
 * catalogue mechanic "starpiercer"). Kill switch: the gear3 module (exclude gear3 in modules.json), like every gear3 item.
 */
public final class Starpiercer {
	private Starpiercer() {}

	public static final String ID = "aphelion_starpiercer";
	/** Full draw in ticks (1.6 s; a vanilla bow is 20). */
	public static final int DRAW_TICKS = 32;
	/** Arrow launch speed multiplier (vanilla bow: power x 3.0). */
	public static final float SPEED = 1.6F;
	/** Extra Piercing levels on every arrow it shoots. */
	public static final int PIERCE = 2;
	/** Arrows fly with no gravity for this many blocks from where they were loosed. */
	public static final double STRAIGHT_BLOCKS = 32.0;
	/** Longbow zoom (the Roc-Feather Longbow's user-decided values): 1.5x at full draw, 2x when also sneaking. */
	public static final float ZOOM = 1.5F, ZOOM_SNEAK = 2.0F, ZOOM_SNEAK_BLEND_SECONDS = 0.25F;
	/** Upper bound for a foreign arrow's weightless window (a very weak shot never covers 32 blocks). */
	public static final int MAX_STRAIGHT_TICKS = 100;
	/** On foreign arrows while they are weightless: a server stop before the scheduled restore is healed on load. */
	public static final String TAG = "elsewhere_starpiercer";
	public static final Identifier APHELION = Identifier.fromNamespaceAndPath("elsewhere", "aphelion");
	/** Foreign arrows whose gravity restore is queued in this session (a tagged arrow loading while not in here is healed). */
	private static final Set<UUID> PENDING = new HashSet<>();

	public static EntityType<StarpiercerArrow> ARROW;
	public static Holder<SoundEvent> SHOOT, DRAW_FULL, CANCEL, HIT;
	private static boolean done;

	public static void register() {
		if (done) return;
		done = true;
		Gear3Mechanics.FACTORIES.put("starpiercer", StarpiercerItem::new);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Gear3Content.id("starpiercer_arrow"));
		ARROW = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<StarpiercerArrow>of(StarpiercerArrow::new, MobCategory.MISC)
			.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20).build(key));
		SHOOT = sound("item.aphelion_starpiercer.shoot");
		DRAW_FULL = sound("item.aphelion_starpiercer.draw_full");
		CANCEL = sound("item.aphelion_starpiercer.cancel");
		HIT = sound("entity.starpiercer_arrow.hit");
		// a foreign arrow saved while still weightless (server stopped before its scheduled restore): give it its gravity back
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow a && a.entityTags().contains(TAG) && !PENDING.contains(a.getUUID())) restoreGravity(a);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
		// Aphelion can die over the void (its islands fall in phase 3): its loot-table drop of this bow is moved onto solid ground
		ServerLivingEntityEvents.AFTER_DEATH.register(Starpiercer::onDeath);
	}

	private static Holder<SoundEvent> sound(String path) {
		Identifier id = Gear3Content.id(path);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static Item item() {
		return BuiltInRegistries.ITEM.getValue(Gear3Content.id(ID));
	}

	// ---------------------------------------------------------------- draw

	/** Ticks of real drawing as the vanilla bow formula sees them: a full 1.6 s draw counts as the bow's 20 ticks. */
	public static int effectiveHeld(int held) {
		return Math.max(0, held) * 20 / DRAW_TICKS;
	}

	/** Launch speed for a vanilla bow power (0..1). */
	public static float velocity(float power) {
		return power * 3.0F * SPEED;
	}

	/** Draw progress 0..1 over the 1.6 s draw (zoom easing, client). */
	public static float drawProgress(float ticksUsing) {
		return Math.max(0F, Math.min(ticksUsing / DRAW_TICKS, 1F));
	}

	/** Longbow zoom factor (1 = none): eases in with the square of the draw to 1.5x, to 2x while sneaking at full draw ({@code sneakBlend} 0..1). */
	public static float zoom(float progress, float sneakBlend) {
		float p = Math.max(0F, Math.min(progress, 1F)), s = Math.max(0F, Math.min(sneakBlend, 1F));
		float target = ZOOM + (ZOOM_SNEAK - ZOOM) * s;
		return Math.min(ZOOM_SNEAK, 1F + p * p * (target - 1F));
	}

	// ---------------------------------------------------------------- flight

	/** Applies the flight rules to an arrow it just shot (server): pierce +2 and, for a foreign arrow, the weightless window. */
	public static void fly(AbstractArrow arrow) {
		((dev.elsewhere.gear3.mixin.starpiercer.StarpiercerArrowAccess) arrow).elsewhere$starpiercerPierce((byte) Math.min(127, arrow.getPierceLevel() + PIERCE));
		if (arrow instanceof StarpiercerArrow own) {
			own.launch();
			return;
		}
		if (!(arrow.level() instanceof ServerLevel level)) return;
		arrow.setNoGravity(true);
		arrow.addTag(TAG);
		final UUID uuid = arrow.getUUID();
		PENDING.add(uuid);
		Gear3Scheduler.after(level.getServer(), straightTicks(arrow.getDeltaMovement().length()), () -> {
			PENDING.remove(uuid);
			restoreGravity(arrow);
		});
	}

	/**
	 * Ticks an arrow launched at {@code speed} blocks/tick needs to cover {@link #STRAIGHT_BLOCKS} in air (drag 0.99 per tick),
	 * at least 1, at most {@link #MAX_STRAIGHT_TICKS}.
	 */
	public static int straightTicks(double speed) {
		if (speed <= 1.0E-4) return 1;
		double f = 1.0 - STRAIGHT_BLOCKS * (1.0 - 0.99) / speed;
		if (f <= 0) return MAX_STRAIGHT_TICKS;
		return (int) Math.max(1, Math.min(MAX_STRAIGHT_TICKS, Math.ceil(Math.log(f) / Math.log(0.99))));
	}

	/** Gives a foreign arrow its gravity back (an arrow that was unloaded meanwhile keeps the tag and is healed when it loads again). */
	public static void restoreGravity(AbstractArrow arrow) {
		if (arrow.isRemoved()) return;
		arrow.removeTag(TAG);
		arrow.setNoGravity(false);
	}

	public static boolean pending(AbstractArrow arrow) {
		return PENDING.contains(arrow.getUUID());
	}

	// ---------------------------------------------------------------- loot safety

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !APHELION.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return;
		Player killer = source.getEntity() instanceof ServerPlayer p ? p : level.getNearestPlayer(entity, 160);
		final Vec3 at = entity.position();
		// one tick later, so the loot-table drops exist whatever the event order; one bounded query on a boss death
		Gear3Scheduler.after(level.getServer(), 1, () -> secureDrops(level, at, killer));
	}

	/** Moves dropped Starpiercers near {@code at} that have no ground under them onto the killer (or leaves them). Returns how many moved. */
	public static int secureDrops(ServerLevel level, Vec3 at, Player killer) {
		Item bow = item();
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(12), e -> e.getItem().is(bow));
		int moved = 0;
		for (ItemEntity e : drops) {
			e.setUnlimitedLifetime();
			e.setGlowingTag(true);
			if (!overVoid(level, e.position()) || killer == null || killer.level() != level) continue;
			e.teleportTo(killer.getX(), killer.getY() + 0.5, killer.getZ());
			e.setDeltaMovement(Vec3.ZERO);
			moved++;
		}
		return moved;
	}

	/** True when the column under {@code pos} has no block that could catch an item (it would fall out of the world). */
	public static boolean overVoid(ServerLevel level, Vec3 pos) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
		return top <= level.getMinY();
	}
}
