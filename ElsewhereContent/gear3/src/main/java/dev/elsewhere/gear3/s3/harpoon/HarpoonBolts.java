package dev.elsewhere.gear3.s3.harpoon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tidebrass Harpoon Bolt (GEAR.md A5, Marksman / Trapper, G3; 3.0 swarm lane s-harpoon). Crossbow ammo
 * ({@code elsewhere:tidebrass_harpoon_bolt}, registered from the gear3 catalogue with mechanic {@code harpoon_bolt}):
 * <ul>
 * <li>a hit pulls a non-boss target 3 blocks toward the shooter (never closer than 1.5 blocks; players too, never bosses,
 * never a rider); without a shooter (a dispenser) the target is pulled back along the bolt's path, toward the dispenser;</li>
 * <li>x1.5 damage against a target that is in water; the bolt keeps its speed underwater (trident inertia);</li>
 * <li>crossbows and dispensers fire it, bows don't (item tag {@code elsewhere:crossbow_only_ammo}, {@code HarpoonBowMixin}).</li>
 * </ul>
 * Lag: nothing ticks. The pull, its sound and its particle line run once, on the bolt's own successful hit.
 * Kill switch: {@code "gear3_harpoon_bolt": false} in {@code config/elsewhere-overhaul3.json} (or
 * {@code -Delsewhere.overhaul3.gear3_harpoon_bolt=false}): the bolt is then a plain crossbow arrow (no pull, no water bonus,
 * vanilla water drag); the item and the entity stay registered so worlds keep them.
 */
public final class HarpoonBolts {
	private HarpoonBolts() {}

	public static final String ID = "tidebrass_harpoon_bolt";
	public static final String KILL_SWITCH = "gear3_harpoon_bolt";
	public static final boolean ON = on(KILL_SWITCH);

	/** GEAR.md: "pulls non-boss target 3 blocks". */
	public static final double PULL_BLOCKS = 3.0;
	/** A pull never brings the target closer than this to the shooter (no yanking mobs into your face). */
	public static final double PULL_KEEP_AWAY = 1.5;
	/** Hop given with the pull (blocks/tick up): just enough to lift the target off the ground so it slides. */
	public static final double PULL_HOP = 0.2;
	/**
	 * Horizontal launch speed per block of pull. Calibrated on vanilla 26.3 living-entity physics (block friction 0.6 x 0.91 on
	 * the ground, 0.91 in the air, gravity 0.08, vertical drag 0.98) with the {@link #PULL_HOP} hop: a body on flat ground
	 * slides 3.0 blocks. The self-test measures it on a real entity.
	 */
	public static final double PULL_PER_BLOCK = 0.2484;
	/** GEAR.md: "x1.5 in water". */
	public static final float WATER_MULT = 1.5F;
	/** Water inertia of the bolt (a trident's): a harpoon keeps flying underwater; a vanilla arrow has 0.6. */
	public static final float WATER_INERTIA = 0.99F;
	/** A shooter further than this (or in another dimension) does not anchor the pull; the bolt's path does. */
	public static final double OWNER_RANGE = 64.0;
	/** Item tag: arrows (in {@code #minecraft:arrows}) that only crossbows and dispensers fire, never bows. */
	public static final TagKey<Item> CROSSBOW_ONLY = TagKey.create(Registries.ITEM, Gear3Content.id("crossbow_only_ammo"));
	/** What a bow accepts: vanilla's arrows minus the crossbow-only ones (one cached predicate, no allocation per call). */
	public static final Predicate<ItemStack> BOW_AMMO = ProjectileWeaponItem.ARROW_ONLY.and(s -> !s.is(CROSSBOW_ONLY));

	public static EntityType<HarpoonBolt> TYPE;

	/** Gear3Content.init(), after Gear3Items.init(): the entity type and the dispenser behaviour. */
	public static void init() {
		if (TYPE != null) return;
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Gear3Content.id(ID));
		// same size, tracking and update rate as vanilla's arrows
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<HarpoonBolt>of(HarpoonBolt::new, MobCategory.MISC).noLootTable()
			.sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20).build(key));
		if (item() instanceof HarpoonBoltItem bolt) DispenserBlock.registerProjectileBehavior(bolt);
	}

	/** The registered item (null before Gear3Items.init or when the catalogue has no such entry). */
	public static @Nullable Item item() {
		return Gear3Items.BY_ID.get(ID);
	}

	/** Bow ammo filter (HarpoonBowMixin): vanilla's arrow predicate keeps its own object identity when it is the input. */
	public static Predicate<ItemStack> bowAmmo(Predicate<ItemStack> original) {
		return original == ProjectileWeaponItem.ARROW_ONLY ? BOW_AMMO : original.and(s -> !s.is(CROSSBOW_ONLY));
	}

	// ---------------------------------------------------------------- the pull

	/** Can a harpoon bolt pull this entity? Alive, living, not a spectator, not riding anything (its mount steers it), not a boss. */
	public static boolean canPull(Entity e) {
		return e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && !le.isPassenger() && !BossCheck.isBoss(le);
	}

	/** Blocks a target {@code dist} blocks from the shooter is pulled: 3, but never closer than 1.5 to the shooter. */
	public static double pullDistance(double dist) {
		return Math.max(0.0, Math.min(PULL_BLOCKS, dist - PULL_KEEP_AWAY));
	}

	/** Horizontal launch speed that slides a body {@code blocks} blocks on flat ground (see {@link #PULL_PER_BLOCK}). */
	public static double pullSpeed(double blocks) {
		return blocks * PULL_PER_BLOCK;
	}

	/** The bolt hit and hurt {@code target} (HarpoonBolt.doPostHurtEffects): pull it toward the shooter. Returns the blocks pulled (0 = not pulled). */
	public static double onHit(ServerLevel level, AbstractArrow bolt, LivingEntity target) {
		Entity owner = bolt.getOwner();
		Vec3 anchor = owner != null && owner != target && owner.level() == level && owner.distanceToSqr(target) <= OWNER_RANGE * OWNER_RANGE ? owner.position() : null;
		return pull(target, anchor, bolt.getDeltaMovement());
	}

	/**
	 * Pulls {@code target} toward {@code anchor} (the shooter) or, with no anchor, back against {@code flight} (the bolt's
	 * velocity, so toward where it came from). Sets the target's horizontal velocity (replacing the hit's knockback, so several
	 * bolts of one multishot volley never stack) plus the {@link #PULL_HOP} hop; a player gets it as a velocity packet
	 * ({@code needsSync}). Plays the reel sound and a short particle line along the pull. Returns the blocks pulled.
	 */
	public static double pull(LivingEntity target, @Nullable Vec3 anchor, Vec3 flight) {
		if (!canPull(target)) return 0.0;
		Vec3 dir;
		double blocks;
		if (anchor != null) {
			Vec3 d = anchor.subtract(target.position()).multiply(1.0, 0.0, 1.0);
			double dist = d.length();
			if (dist < 1.0E-3) return 0.0;
			dir = d.scale(1.0 / dist);
			blocks = pullDistance(dist);
		} else {
			Vec3 back = new Vec3(-flight.x, 0.0, -flight.z);
			if (back.lengthSqr() < 1.0E-6) return 0.0;
			dir = back.normalize();
			blocks = PULL_BLOCKS;
		}
		if (blocks <= 0.0) return 0.0;
		double v = pullSpeed(blocks);
		target.setDeltaMovement(dir.x * v, PULL_HOP, dir.z * v);
		target.needsSync = true;
		if (target.level() instanceof ServerLevel sl) fx(sl, target, dir, blocks);
		return blocks;
	}

	/** One reel sound and a 5-particle line from the target along the pull (copper scrape sparks; bubbles in water). Once per hit. */
	private static void fx(ServerLevel level, LivingEntity target, Vec3 dir, double blocks) {
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.7F);
		boolean wet = target.isInWater();
		double y = target.getY() + target.getBbHeight() * 0.5;
		for (int i = 0; i <= 4; i++) {
			double t = blocks * i / 4.0;
			level.sendParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.SCRAPE, target.getX() + dir.x * t, y, target.getZ() + dir.z * t, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- kill switch (same file and flag as arsenal's Overhaul3)

	/**
	 * Reads one key of {@code config/elsewhere-overhaul3.json} (default on), or the JVM flag {@code -Delsewhere.overhaul3.<key>}.
	 * Same rules as arsenal's {@code Overhaul3.on}; read once, at class load. Kept here so gear3 does not need a newer arsenal.
	 */
	static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true; // a missing or malformed file keeps the default
		}
	}
}
