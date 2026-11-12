package dev.elsewhere.gear3.s3.willo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3Mechanics;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Will-o'-Lantern (GEAR.md A5, Caster table: {@code mirewick_will_o_lantern}, C+S hybrid, Ritualist, G4; 3.0 swarm s-willolantern).
 *
 * <p>Hold use for the 1 s call (10 Glimmer, 1 minion slot) and a Marsh Wisp ({@link MarshWisp}, a summoner minion) floats at your
 * shoulder. Every 4 s while it has a target it recasts your last damage spell at that target at 40% power, times the
 * Ritualist hybrid strength {@code H(caster, summoner)}. It lights the ground around you (a level-10 light that follows it,
 * {@link WispLights}). One wisp per player; sneak-use sends it home.
 *
 * <p>Event-driven only: the last spell is remembered from arsenal's {@code Spells.ON_CAST}; the recast is the minion's own
 * attack timer; the light moves at most every 10 ticks and only when the wisp changed block. Nothing scans or polls.
 *
 * <p>Kill switches (config/elsewhere-overhaul3.json or {@code -Delsewhere.overhaul3.<key>=false}, read once): {@code will_o_lantern}
 * (the whole item: calling, recasts, Chair drop, light) and {@code will_o_lantern_light} (only the light blocks).
 */
public final class WillOLantern {
	private WillOLantern() {}

	public static final String ITEM_ID = "mirewick_will_o_lantern", WISP_ID = "marsh_wisp";
	/** GEAR.md: 10 Glimmer, 1 slot; recasts every 4 s at 40% x H. */
	public static final int SUMMON_COST = 10, RECAST_TICKS = 80;
	public static final float RECAST_POWER = 0.40F;
	/** The wisp stays by its owner, so its reach covers the summoner targeting range (16) plus the hover offset. */
	public static final double RECAST_RANGE = 18.0;
	/** GEAR.md source: the Mirewick Chair-holder, 30%. */
	public static final float CHAIR_DROP = 0.30F;
	/** Spells the wisp may echo: damage spells only (no teleports, wards, heals, lights, channels or crowd control, whose effects a 40% scale can't shrink). */
	public static final TagKey<Item> RECASTABLE = TagKey.create(Registries.ITEM, Gear3Content.id("wisp_recast"));
	static final Identifier SCALE_ID = Gear3Content.id("gear3/will_o_lantern.recast_scale");
	static final String MAGNATE_CLASS = "dev.elsewhere.rivals.mirewick.boss.MagnateBoss";

	public static final boolean ENABLED = on("will_o_lantern");
	public static final boolean LIGHT = ENABLED && on("will_o_lantern_light");

	public static EntityType<MarshWisp> WISP;
	/** Owner -> the last recastable spell they cast (server). Cleared when they leave and on server stop. */
	static final Map<UUID, Item> LAST = new HashMap<>();
	/** Self-test counters. */
	public static int recasts, chairDrops;
	private static boolean done;

	/** Registers the wisp entity, the item mechanic and the hooks. Must run before {@code Gear3Items.init()} (one line in Gear3Content). Idempotent. */
	public static void init() {
		if (done) return;
		done = true;
		WISP = registerWisp();
		Gear3Mechanics.FACTORIES.put("will_o_lantern", WillOLanternItem::new);
		WispLights.init();
		Spells.ON_CAST.add(WillOLantern::onCast);
		ServerPlayerEvents.LEAVE.register(p -> LAST.remove(p.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
		ServerLivingEntityEvents.AFTER_DEATH.register(WillOLantern::onDeath);
	}

	@SuppressWarnings("unchecked")
	private static EntityType<MarshWisp> registerWisp() {
		Identifier id = Gear3Content.id(WISP_ID);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return (EntityType<MarshWisp>) BuiltInRegistries.ENTITY_TYPE.getValue(id);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		EntityType<MarshWisp> t = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(MarshWisp::new, MobCategory.MISC)
			.sized(0.5F, 0.5F).eyeHeight(0.25F).clientTrackingRange(8).noSave().noLootTable().fireImmune().build(key));
		FabricDefaultAttributeRegistry.register(t, MarshWisp.createAttributes());
		return t;
	}

	public static @Nullable Item item() {
		return Gear3Items.BY_ID.get(ITEM_ID);
	}

	// ---------------------------------------------------------------- the last spell (Spells.ON_CAST; castFree recasts never fire it)

	public static boolean recastable(@Nullable Item item) {
		return item instanceof CasterItem && new ItemStack(item).is(RECASTABLE);
	}

	static void onCast(ServerPlayer p, Item item) {
		if (!ENABLED || !recastable(item)) return;
		Item prev = LAST.put(p.getUUID(), item);
		if (prev == item) return;
		MarshWisp w = wispOf(p);
		if (w == null) return;
		p.sendOverlayMessage(Component.translatable("message.elsewhere.will_o_lantern.echo", new ItemStack(item).getHoverName()));
		p.level().sendParticles(ParticleTypes.GLOW, w.getX(), w.getY() + w.getBbHeight() * 0.5, w.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
	}

	public static @Nullable Item lastSpell(Player p) {
		return LAST.get(p.getUUID());
	}

	/** Self-test hook: what the wisp remembers (null forgets). */
	public static void rememberForTest(Player p, @Nullable Item item) {
		if (item == null) LAST.remove(p.getUUID());
		else LAST.put(p.getUUID(), item);
	}

	public static @Nullable MarshWisp wispOf(ServerPlayer p) {
		for (MinionEntity m : MinionManager.minions(p)) if (m instanceof MarshWisp w && w.isAlive()) return w;
		return null;
	}

	// ---------------------------------------------------------------- the recast

	/** Recast power for this owner: 40% x H(caster, summoner). */
	public static float recastScale(Player p) {
		return RECAST_POWER * hybrid(p);
	}

	/**
	 * The wisp recasts its owner's last damage spell at {@code target}: the owner's view is turned to the target for this one
	 * synchronous cast (and restored before anything is sent), and the cast goes through arsenal's {@code castFree} (no Glimmer,
	 * cooldown, durability or ON_CAST) at {@link #recastScale}. Needs the owner to see the target. Returns true if it cast.
	 */
	public static boolean recast(ServerLevel level, ServerPlayer owner, LivingEntity from, LivingEntity target) {
		if (!ENABLED || !target.isAlive()) return false;
		Item spell = LAST.get(owner.getUUID());
		if (!(spell instanceof CasterItem caster) || !recastable(spell) || !owner.hasLineOfSight(target)) return false;
		float scale = recastScale(owner);
		if (scale <= 0F) return false;
		float yRot = owner.getYRot(), xRot = owner.getXRot(), head = owner.getYHeadRot();
		boolean ok;
		MarshWisp wisp = from instanceof MarshWisp w ? w : null;
		try {
			if (wisp != null) wisp.echoing = true; // never intercepts its own echo
			aimAt(owner, target.getBoundingBox().getCenter());
			ok = castScaled(level, owner, caster, new ItemStack(spell), scale);
		} finally {
			if (wisp != null) wisp.echoing = false;
			owner.setYRot(yRot);
			owner.setXRot(xRot);
			owner.setYHeadRot(head);
		}
		if (!ok) return false;
		recasts++;
		Vec3 a = from.position().add(0, from.getBbHeight() * 0.5, 0), b = target.getBoundingBox().getCenter(), d = b.subtract(a);
		int steps = (int) Mth.clamp(d.length() / 0.7, 2, 24);
		for (int i = 1; i <= steps; i++) {
			Vec3 q = a.add(d.scale(i / (double) steps));
			level.sendParticles(ParticleTypes.GLOW, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	static void aimAt(ServerPlayer p, Vec3 at) {
		Vec3 eye = p.getEyePosition();
		double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z, h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	// 3.0 arsenal: CasterItem.castFree(level, player, stack, scale) and Calling.hybrid(p, CASTER, SUMMONER). Looked up by
	// reflection because gear3's dev classpath (devlibs) can be the 2.x jar without them; the merged 3.0 jar always has them.
	private static final Method CAST_FREE = method(CasterItem.class, "castFree", net.minecraft.server.level.ServerLevel.class, ServerPlayer.class, ItemStack.class, float.class);
	private static final Method CAST = declared(CasterItem.class, "cast", net.minecraft.server.level.ServerLevel.class, ServerPlayer.class, ItemStack.class, boolean.class);
	private static Method hybridM;
	private static Object pathCaster, pathSummoner;
	private static boolean hybridLooked, warned;

	/** True when arsenal has the 3.0 {@code castFree} hook (else the 2.x fallback scales spell power for the cast). */
	public static boolean hasCastFree() {
		return CAST_FREE != null;
	}

	static boolean castScaled(ServerLevel level, ServerPlayer owner, CasterItem caster, ItemStack stack, float scale) {
		try {
			if (CAST_FREE != null) {
				if (!spellHooks()) return false; // castFree is a no-op with arsenal's spell_hooks switch off: no fake echo
				CAST_FREE.invoke(caster, level, owner, stack, scale);
				return true;
			}
			// 2.x arsenal: scale spell_power for this synchronous cast (bolts that land later are not scaled there)
			AttributeInstance sp = ArsenalAttributes.SPELL_POWER == null ? null : owner.getAttribute(ArsenalAttributes.SPELL_POWER);
			if (CAST == null || sp == null) return false;
			sp.addOrUpdateTransientModifier(new AttributeModifier(SCALE_ID, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			try {
				CAST.invoke(caster, level, owner, stack, false);
			} finally {
				sp.removeModifier(SCALE_ID);
			}
			return true;
		} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
			if (!warned) {
				warned = true;
				System.out.println("[elsewhere] Will-o'-Lantern: recast failed: " + (e instanceof InvocationTargetException ite ? ite.getCause() : e));
			}
			return false;
		}
	}

	private static Boolean spellHooks;

	/** arsenal 3.0 {@code Overhaul3.SPELL_HOOKS} (castFree's own switch); true where the field can't be read. */
	static boolean spellHooks() {
		if (spellHooks == null) {
			try {
				spellHooks = Class.forName("dev.elsewhere.arsenal.Overhaul3").getField("SPELL_HOOKS").getBoolean(null);
			} catch (ReflectiveOperationException | RuntimeException e) {
				spellHooks = Boolean.TRUE;
			}
		}
		return spellHooks;
	}

	/** {@code H(caster, summoner)} from arsenal's 3.0 Calling (1.0 where the arsenal has no hybrid rule). */
	public static float hybrid(Player p) {
		if (!hybridLooked) {
			hybridLooked = true;
			try {
				Class<?> calling = Class.forName("dev.elsewhere.arsenal.spell.Calling");
				Class<?> path = Class.forName("dev.elsewhere.arsenal.spell.Calling$Path");
				hybridM = calling.getMethod("hybrid", Player.class, path, path);
				pathCaster = path.getField("CASTER").get(null);
				pathSummoner = path.getField("SUMMONER").get(null);
				// arsenal's callings switch off: no Callings, so no hybrid penalty either
				if (!Class.forName("dev.elsewhere.arsenal.Overhaul3").getField("CALLINGS").getBoolean(null)) hybridM = null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				hybridM = null;
			}
		}
		if (hybridM == null) return 1.0F;
		try {
			return Mth.clamp(((Number) hybridM.invoke(null, p, pathCaster, pathSummoner)).floatValue(), 0F, 1F);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0F;
		}
	}

	private static @Nullable Method method(Class<?> c, String name, Class<?>... args) {
		try {
			return c.getMethod(name, args);
		} catch (NoSuchMethodException | RuntimeException e) {
			return null;
		}
	}

	private static @Nullable Method declared(Class<?> c, String name, Class<?>... args) {
		try {
			Method m = c.getDeclaredMethod(name, args);
			m.setAccessible(true);
			return m;
		} catch (NoSuchMethodException | RuntimeException e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- source: the Mirewick Chair-holder, 30% (rival_mirewick, soft link)

	static void onDeath(LivingEntity e, DamageSource source) {
		if (!ENABLED || !(e.level() instanceof ServerLevel sl) || !isChairHolder(e)) return;
		Item it = item();
		if (it == null || sl.getRandom().nextFloat() >= CHAIR_DROP) return;
		e.spawnAtLocation(sl, new ItemStack(it));
		chairDrops++;
	}

	/** A Mirewick Magnate who held the Chair when it died (MagnateBoss keeps {@code chairAtDeath} because the court clears {@code chair} inside die()). */
	static boolean isChairHolder(Object e) {
		return e.getClass().getName().equals(MAGNATE_CLASS) && chairFlag(e);
	}

	private static final Map<String, Field> FIELDS = new HashMap<>();

	/** {@code chair || chairAtDeath} on the object (any declared boolean field of that name up the class chain). */
	public static boolean chairFlag(Object o) {
		return bool(o, "chair") || bool(o, "chairAtDeath");
	}

	private static boolean bool(Object o, String name) {
		String key = o.getClass().getName() + "#" + name;
		Field f = FIELDS.get(key);
		if (f == null && !FIELDS.containsKey(key)) {
			for (Class<?> c = o.getClass(); c != null && f == null; c = c.getSuperclass()) {
				try {
					Field g = c.getDeclaredField(name);
					if (g.getType() == boolean.class) {
						g.setAccessible(true);
						f = g;
					}
				} catch (NoSuchFieldException | RuntimeException ignored) {
					// keep looking up the chain
				}
			}
			FIELDS.put(key, f);
		}
		try {
			return f != null && f.getBoolean(o);
		} catch (IllegalAccessException | RuntimeException e) {
			return false;
		}
	}

	// ---------------------------------------------------------------- kill switches (same file and flags as arsenal/summoner Overhaul3)

	public static boolean on(String key) {
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
