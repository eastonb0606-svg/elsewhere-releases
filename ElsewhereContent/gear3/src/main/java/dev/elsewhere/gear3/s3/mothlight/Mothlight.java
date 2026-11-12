package dev.elsewhere.gear3.s3.mothlight;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/**
 * Mothlight Staff (GEAR.md A5, Summoner / Swarm, G3): "1 slot = 3 moths, 6 HP each, 1.5 dmg per 0.75 s; moth hits: target
 * -10% damage 3 s (non-boss)". Source: Selenne 30% (loot hook) and a Pale Bark + Moonsilk Thread recipe (gear3 catalogue).
 * <ul>
 * <li>The staff is a summoner {@code MinionStaffItem} ({@link MothlightStaffItem}): the 1 s call, 10 Glimmer and 1 durability,
 * sneak-use sends the moths home. It is in {@code #elsewhere:calling_weapon/summoner} (tools/calling_tags.py).</li>
 * <li>It calls one {@link MothlightSwarm}: three moths that share ONE minion slot (one entry in the summoner's MinionManager, so
 * one slot, one Glimmer reserve and one Herd-focus step). Each moth has its own 6 HP: one blow fells one moth at most, a fallen
 * moth never comes back, and every living moth strikes for 1.5 every 0.75 s.</li>
 * <li>Moth hits give non-boss targets {@link #MOTHDUST} for 3 s: every hit the dusted mob deals (melee, its arrows, its blasts)
 * does 10% less ({@link #outgoing}, called from {@code mixin.mothlight.MothdustMixin} on the server damage entry point).</li>
 * </ul>
 * Nothing here ticks or scans: the swarm is an ordinary minion (summoner AI), the dust is a timed mob effect, and the damage hook
 * is one effect lookup per hit. Merge entry: {@link #init()} from Gear3Content. Client: {@code s3.mothlight.client.MothlightClient}.
 * Kill switch: key {@code mothlight_staff} in {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.mothlight_staff=false}):
 * off = item, entity and effect stay registered (worlds keep loading), but the staff calls nothing and Mothdust does nothing.
 */
public final class Mothlight {
	private Mothlight() {}

	public static final String STAFF_ID = "mothlight_staff", SWARM_ID = "mothlight_swarm", DUST_ID = "mothdust";
	/** Moths per swarm (one minion slot). */
	public static final int MOTHS = 3;
	/** HP of each moth (GEAR.md: 6 HP each); the swarm's max health is {@code MOTHS * MOTH_HP}. */
	public static final double MOTH_HP = 6.0;
	/** Damage of each moth's strike (GEAR.md: 1.5 dmg per 0.75 s), before minion_damage and the Summoner Calling. */
	public static final double MOTH_DAMAGE = 1.5;
	/** 0.75 s between flurries. */
	public static final int ATTACK_TICKS = 15;
	/** Mothdust: 3 s, -10% damage dealt. */
	public static final int DUST_TICKS = 60;
	public static final float DUST_FACTOR = 0.90F;
	/** Pale silver-lilac (effect colour). */
	public static final int DUST_COLOR = 0xD9D3EE;
	/** Kill switch {@code mothlight_staff}, read once (the self-test flips it to check the off state). */
	public static boolean enabled = on(STAFF_ID);

	public static EntityType<MothlightSwarm> SWARM;
	public static Holder<MobEffect> MOTHDUST;
	private static boolean done;

	/** Mothdust: a harmful marker effect (moth-wing dust); the -10% is applied by {@link #outgoing}. Floating white-ash particles. */
	static class Mothdust extends MobEffect {
		Mothdust() {
			super(MobEffectCategory.HARMFUL, DUST_COLOR, ParticleTypes.WHITE_ASH);
		}
	}

	public static void init() {
		if (done) return;
		done = true;
		Identifier sid = Gear3Content.id(SWARM_ID);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(sid)) {
			@SuppressWarnings("unchecked")
			EntityType<MothlightSwarm> existing = (EntityType<MothlightSwarm>) BuiltInRegistries.ENTITY_TYPE.getValue(sid);
			SWARM = existing;
		} else {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, sid);
			// like the summoner minions: not saved with the world, no loot table, client tracking 10 chunks
			SWARM = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<MothlightSwarm>of(MothlightSwarm::new, MobCategory.MISC)
				.sized(0.9F, 0.6F).eyeHeight(0.3F).clientTrackingRange(10).noSave().noLootTable().build(key));
			FabricDefaultAttributeRegistry.register(SWARM, MothlightSwarm.createAttributes());
		}
		Identifier did = Gear3Content.id(DUST_ID);
		MOTHDUST = BuiltInRegistries.MOB_EFFECT.get(did).map(h -> (Holder<MobEffect>) h)
			.orElseGet(() -> Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, did, new Mothdust()));
	}

	/** Moth hits dust non-bosses only (GEAR.md "(non-boss)"). */
	public static boolean dusts(LivingEntity target) {
		return !BossCheck.isBoss(target);
	}

	/** A moth hit landed: Mothdust for 3 s on a living non-boss (refreshed by every later hit). */
	public static void dust(LivingEntity target, Entity by) {
		if (!enabled || MOTHDUST == null || !target.isAlive() || !dusts(target)) return;
		target.addEffect(new MobEffectInstance(MOTHDUST, DUST_TICKS, 0, false, true, true), by);
	}

	/**
	 * Server damage entry point ({@code LivingEntity.hurtServer}, before armor): a hit whose attacker carries Mothdust deals 10%
	 * less. The attacker is the source's causing entity, so a dusted skeleton's arrows and a dusted creeper's blast count too.
	 */
	public static float outgoing(LivingEntity victim, DamageSource source, float amount) {
		if (!enabled || MOTHDUST == null || amount <= 0F || victim.level().isClientSide()) return amount;
		if (source.getEntity() instanceof LivingEntity by && by != victim && by.hasEffect(MOTHDUST)) return amount * DUST_FACTOR;
		return amount;
	}

	/** Same rule as arsenal's / summoner's Overhaul3.on: JVM flag, then config/elsewhere-overhaul3.json, default on. */
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
