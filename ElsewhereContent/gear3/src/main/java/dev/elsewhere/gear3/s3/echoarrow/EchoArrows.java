package dev.elsewhere.gear3.s3.echoarrow;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Echo Arrow (GEAR.md A5, Marksman / Trapper, G4): "pulse: mobs within 6 blocks glow 5 s; silent flight".
 * <ul>
 * <li>On its first impact (block or entity) the arrow sends one echo pulse: every mob within {@link #RADIUS} blocks of the impact
 * (nearest point of its hitbox) gets Glowing for {@link #GLOW_TICKS}, credited to the shooter. Players are never marked.</li>
 * <li>Silent flight: the arrow is a silent entity (no hit or landing sound, no "ding" to the shooter) and it dampens vibrations,
 * so neither its loosing nor its landing reaches sculk sensors, shriekers or Wardens.</li>
 * <li>Client: a faint sculk trail, and at the pulse a ring of sculk pops plus one vibration line from the impact to each revealed
 * mob; the shooter alone hears a soft sculk click (one entity event per arrow; nothing ticks on the server).</li>
 * </ul>
 * Item {@code elsewhere:echo_arrow} comes from the gear3 catalogue (mechanic "echo_arrow" = {@link EchoArrowItem}); this class
 * registers the entity type {@code elsewhere:echo_arrow} and the dispenser behaviour. Merge entry: {@link #init()} from Gear3Content.
 * Kill switch: key {@code echo_arrow} in {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.echo_arrow=false}):
 * off = the item and entity stay registered (worlds keep loading) but it flies and lands like a plain arrow, with no pulse.
 */
public final class EchoArrows {
	private EchoArrows() {}

	public static final String ID = "echo_arrow";
	/** Pulse radius in blocks (GEAR.md: "mobs within 6 blocks"). */
	public static final double RADIUS = 6.0;
	/** Glowing time in ticks (GEAR.md: "glow 5 s"). */
	public static final int GLOW_TICKS = 100;
	/** Entity event sent once at the pulse: the client draws the echo (ring + vibration lines). Not used by Entity/AbstractArrow. */
	public static final byte EVENT_PULSE = 61;
	/** Most vibration lines one pulse draws on the client. */
	public static final int MAX_LINES = 12;
	/** Kill switch {@code echo_arrow}, read once. */
	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on(ID);

	public static EntityType<EchoArrowEntity> TYPE;
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Gear3Content.id(ID));
		// same size, tracking range and update rate as the vanilla arrow and spectral arrow
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<EchoArrowEntity>of(EchoArrowEntity::new, MobCategory.MISC)
			.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20).build(key));
		Item item = item();
		if (item != null) DispenserBlock.registerProjectileBehavior(item);
	}

	/** The catalogue item, or null when the catalogue has none (then the arrow picks up as nothing). */
	public static Item item() {
		return Gear3Items.BY_ID.get(ID);
	}

	public static ItemStack stack() {
		Item i = item();
		return i == null ? ItemStack.EMPTY : new ItemStack(i);
	}

	/** Squared distance from {@code p} to the nearest point of {@code box} (0 inside). */
	public static double distSqr(AABB box, Vec3 p) {
		double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}

	/** True when the pulse at {@code at} reaches {@code e} (a living mob whose hitbox is within {@link #RADIUS}). */
	public static boolean reaches(Mob e, Vec3 at) {
		return e.isAlive() && distSqr(e.getBoundingBox(), at) <= RADIUS * RADIUS;
	}

	/**
	 * The echo pulse (server): every mob within {@link #RADIUS} of {@code at} glows for {@link #GLOW_TICKS}, credited to
	 * {@code source} (the shooter, else the arrow). One box query per impact; returns how many mobs were marked.
	 */
	public static int pulse(ServerLevel level, Vec3 at, Entity source) {
		if (!ENABLED) return 0;
		int n = 0;
		for (Mob m : level.getEntitiesOfClass(Mob.class, AABB.ofSize(at, RADIUS * 2 + 2, RADIUS * 2 + 2, RADIUS * 2 + 2), e -> reaches(e, at))) {
			m.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0), source);
			n++;
		}
		return n;
	}
}
