package dev.elsewhere.gear3.s3.hurlbat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3Mechanics;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hurlbat ({@code elsewhere:hrafnholt_hurlbat}, GEAR.md A5 Marksman / Skirmisher, G3): a Hrafnholt throwing axe, stack 8.
 * Use throws one (7 damage, 0.6 s between throws). It sticks where it lands and is picked back up by its thrower; a hit on a mob or
 * player breaks it half the time (otherwise it bounces off and drops). A hit on a raised shield disables that shield for 2 s.
 * Sources: the Hrafnholt Huntmaster drops 8 (50%, {@link #onDeath}); the Thing-day sells 8 for 12 Hacksilver (rival_hrafnholt
 * kingdom_trade line); crafting 3 Hacksilver + 2 sticks makes 2 (the same price, gear3 catalogue recipe).
 * <p>
 * Marksman Calling: the multiplier is fixed when the hurlbat leaves the hand and rides on the entity as arsenal's arrow mark tag
 * ({@code Calling.MARK_TAG}); arsenal's damage hook applies it at hit time, never against players. Soft link (reflection, resolved
 * once) so gear3 also compiles against the 2.0 devlibs jar.
 * <p>
 * Kill switch {@code gear3_hurlbat} ({@code "gear3_hurlbat": false} in {@code config/elsewhere-overhaul3.json} or
 * {@code -Delsewhere.overhaul3.gear3_hurlbat=false}): no throwing and no Huntmaster drop. The item, entity type, sounds and damage
 * type stay registered so worlds and clients keep matching registries. Nothing here ticks: one item use, one projectile hit, one death event.
 */
public final class Hurlbats {
	private Hurlbats() {}

	public static final String ID = "hrafnholt_hurlbat";
	public static final String CONFIG_KEY = "gear3_hurlbat";

	/** GEAR.md: thrown axe 7 dmg, 0.6 s between throws, 50% break, disables shields 2 s. */
	public static final float DAMAGE = 7.0F;
	public static final int COOLDOWN_TICKS = 12;
	public static final float BREAK_CHANCE = 0.5F;
	public static final float SHIELD_DISABLE_SECONDS = 2.0F;
	/** Throw: 1.6 blocks per tick (arrow gravity), 1.0 spread; about 15 blocks before it drops a block and a half. */
	public static final float VELOCITY = 1.6F, INACCURACY = 1.0F;
	/** Huntmaster drop: 50% for a bundle of 8 (the Thing-day bundle size). */
	public static final float HUNTMASTER_CHANCE = 0.5F;
	public static final int HUNTMASTER_COUNT = 8;
	/** The Hrafnholt Huntmaster seat and its Risen champion (rival_hrafnholt HrafnholtBosses; looked up by id, no compile dependency). */
	public static final Set<String> HUNTMASTERS = Set.of("elsewhere:hrafnholt_huntmaster", "elsewhere:hrafnholt_risen_huntmaster");

	public static final Identifier DAMAGE_ID = Gear3Content.id("hurlbat");
	public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, DAMAGE_ID);

	public static EntityType<HurlbatEntity> ENTITY;
	public static SoundEvent THROW, HIT, HIT_GROUND, BREAK;
	/** Sound event paths (assets/elsewhere/sounds.json in gear3; subtitles in en_us.json). */
	public static final String[] SOUNDS = {"gear3.hurlbat.throw", "gear3.hurlbat.hit", "gear3.hurlbat.hit_ground", "gear3.hurlbat.break"};

	private static boolean done;
	private static Boolean enabled;

	/** Catalogue mechanic "hurlbat" -> {@link HurlbatItem}. Called from Gear3Content.init() before Gear3Items.init() registers the catalogue. */
	public static void putFactory() {
		Gear3Mechanics.FACTORIES.put("hurlbat", HurlbatItem::new);
	}

	public static void init() {
		if (done) return;
		done = true;
		THROW = sound(SOUNDS[0]);
		HIT = sound(SOUNDS[1]);
		HIT_GROUND = sound(SOUNDS[2]);
		BREAK = sound(SOUNDS[3]);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Gear3Content.id(ID));
		ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<HurlbatEntity>of(HurlbatEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).noLootTable().build(key));
		ServerLivingEntityEvents.AFTER_DEATH.register(Hurlbats::onDeath);
	}

	/** Registry-collision rule (as Sfx3): an id another module already registered is reused, not registered twice. */
	private static SoundEvent sound(String path) {
		Identifier id = Gear3Content.id(path);
		return BuiltInRegistries.SOUND_EVENT.containsKey(id) ? BuiltInRegistries.SOUND_EVENT.getValue(id)
			: Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static Item item() {
		Item i = Gear3Items.BY_ID.get(ID);
		return i != null ? i : BuiltInRegistries.ITEM.getValue(Gear3Content.id(ID));
	}

	// ---------------------------------------------------------------- kill switch (read once, like arsenal's Overhaul3)

	public static boolean enabled() {
		Boolean on = enabled;
		if (on == null) enabled = on = readFlag();
		return on;
	}

	/** Self-test only. */
	static void setEnabledForTest(@Nullable Boolean on) {
		enabled = on;
	}

	private static boolean readFlag() {
		String prop = System.getProperty("elsewhere.overhaul3." + CONFIG_KEY);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (Files.isRegularFile(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (o.has(CONFIG_KEY)) return o.get(CONFIG_KEY).getAsBoolean();
			}
		} catch (Exception e) {
			// a missing or malformed file keeps the default
		}
		return true;
	}

	// ---------------------------------------------------------------- throwing

	/** Spawns one hurlbat from {@code player}'s view (server side). {@code stack} is the held stack; the caller consumes it. */
	public static HurlbatEntity throwFrom(ServerLevel level, Player player, ItemStack stack) {
		float mark = marksmanMult(player); // read while the hurlbat is still in hand (the last one empties the hand)
		ItemStack one = stack.copyWithCount(1);
		HurlbatEntity h = new HurlbatEntity(level, player, one);
		if (player.hasInfiniteMaterials()) h.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		if (mark != 1.0F) h.addTag(MARK_TAG + mark);
		Projectile.spawnProjectile(h, level, one, e -> e.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, VELOCITY, INACCURACY));
		level.playSound(null, h, THROW, SoundSource.PLAYERS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		return h;
	}

	/** True when a hurlbat that struck something breaks (50%). */
	public static boolean rollBreak(RandomSource rnd) {
		return rnd.nextFloat() < BREAK_CHANCE;
	}

	public static DamageSource damageSource(Entity direct, @Nullable Entity causing) {
		Registry<DamageType> types = direct.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
		var holder = types.get(DAMAGE_ID);
		return holder.isPresent() ? new DamageSource(holder.get(), direct, causing) : direct.damageSources().trident(direct, causing);
	}

	// ---------------------------------------------------------------- shields

	/**
	 * The shield {@code target} blocks this hit with, or null: the same test as vanilla {@code LivingEntity.applyItemBlocking}
	 * (raised past its delay, not bypassed by the damage type, the hit inside the shield's arc). Pure: changes nothing.
	 */
	public static @Nullable ItemStack blockingShield(LivingEntity target, DamageSource source, float damage) {
		ItemStack with = target.getItemBlockingWith();
		if (with == null || with.isEmpty()) return null;
		BlocksAttacks blocks = with.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || blocks.bypassedBy().map(set -> set.contains(source.typeHolder())).orElse(false)) return null;
		Vec3 from = source.getSourcePosition();
		double angle = Math.PI;
		if (from != null) {
			Vec3 view = Entity.calculateViewVector(0.0F, target.getYHeadRot());
			Vec3 to = from.subtract(target.position());
			to = new Vec3(to.x, 0.0, to.z).normalize();
			angle = Math.acos(to.dot(view));
		}
		return blocks.resolveBlockedDamage(source, damage, angle) > 0.0F ? with : null;
	}

	/** Disables {@code shield} for 2 s (times the shield's own disable scale), as an axe does: cooldown, lowered, disable sound. */
	public static void disableShield(ServerLevel level, LivingEntity target, ItemStack shield) {
		if (shield.isEmpty()) return;
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks != null) blocks.disable(level, target, SHIELD_DISABLE_SECONDS, shield);
	}

	/** Ticks a shield stays down after a hurlbat hit (vanilla rounding of {@code BlocksAttacks.disable}): 40 for a vanilla shield. */
	public static int shieldDisableTicks(BlocksAttacks blocks) {
		float s = SHIELD_DISABLE_SECONDS * blocks.disableCooldownScale();
		return s > 0.0F ? Math.round(s * 20.0F) : 0;
	}

	// ---------------------------------------------------------------- Huntmaster drop

	public static boolean isHuntmaster(EntityType<?> type) {
		return HUNTMASTERS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
	}

	static boolean killedByPlayer(LivingEntity dead, DamageSource source) {
		return source.getEntity() instanceof Player || dead.getLastHurtByPlayer() != null;
	}

	public static boolean rollHuntmasterDrop(RandomSource rnd) {
		return rnd.nextFloat() < HUNTMASTER_CHANCE;
	}

	/** Drops the bundle of 8 at the body. */
	public static @Nullable ItemEntity dropBundle(ServerLevel level, LivingEntity at) {
		return at.spawnAtLocation(level, new ItemStack(item(), HUNTMASTER_COUNT));
	}

	static void onDeath(LivingEntity dead, DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level) || !isHuntmaster(dead.getType()) || !enabled()) return;
		if (killedByPlayer(dead, source) && rollHuntmasterDrop(level.getRandom())) dropBundle(level, dead);
	}

	// ---------------------------------------------------------------- Marksman Calling (arsenal soft link)

	/** arsenal {@code Calling.MARK_TAG} ("elsewhere_mk:"); read from arsenal when present so the two never drift. */
	public static final String MARK_TAG = markTag();
	private static final MethodHandle MARKSMAN_MULT = marksmanHandle();

	private static String markTag() {
		try {
			Object v = Class.forName("dev.elsewhere.arsenal.spell.Calling").getField("MARK_TAG").get(null);
			if (v instanceof String s) return s;
		} catch (ReflectiveOperationException | LinkageError e) {
			// arsenal before 3.0: the 3.0 tag text
		}
		return "elsewhere_mk:";
	}

	private static @Nullable MethodHandle marksmanHandle() {
		try {
			return MethodHandles.publicLookup().findStatic(Class.forName("dev.elsewhere.arsenal.spell.Calling"), "marksmanMult", MethodType.methodType(float.class, Player.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/** True when arsenal's 3.0 Calling API is present (the merged jar). */
	public static boolean marksmanLinked() {
		return MARKSMAN_MULT != null;
	}

	/** arsenal {@code Calling.marksmanMult(player)}: the Marksman multiplier while a calling-tagged ranged weapon is held; 1.0 without arsenal 3.0. */
	public static float marksmanMult(Player p) {
		if (MARKSMAN_MULT == null) return 1.0F;
		try {
			return (float) MARKSMAN_MULT.invokeExact(p);
		} catch (Throwable t) {
			return 1.0F;
		}
	}
}
