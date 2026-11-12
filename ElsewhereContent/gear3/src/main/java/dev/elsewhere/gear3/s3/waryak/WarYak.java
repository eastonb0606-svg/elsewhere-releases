package dev.elsewhere.gear3.s3.waryak;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.minion.MinionSlots;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * War-Yak Horn (GEAR.md A5, Summoner / Elite, G4): calls a War-Yak, a 2-slot minion of the Ironpeak clans (80 HP,
 * armor 12) that charges every 1.5 s for 9 damage and tramples up to 3 mobs in its path. Built on the summoner
 * module's minion API (MinionEntity, MinionManager, MinionStaffItem: 4-minion cap, Herd focus, Summoner Calling
 * damage / timer / reserve) plus gear3's weighted slots ({@link MinionSlots}).
 * <p>Kill switch: {@code "gear3_war_yak": false} in {@code config/elsewhere-overhaul3.json} (or
 * {@code -Delsewhere.overhaul3.gear3_war_yak=false}): the horn calls nothing and the Quartermaster stops selling it.
 * Registry ids stay (never removed).
 */
public final class WarYak {
	private WarYak() {}

	public static final String KEY = "gear3_war_yak";
	public static final boolean ENABLED = on(KEY);

	public static final String HORN_ID = "ironpeak_war_yak_horn";
	public static final String ENTITY_ID = "war_yak";

	// ---- GEAR.md A5 numbers (2 slots: 80 HP, armor 12, 9 dmg / 1.5 s charge, tramples through up to 3 mobs)
	public static final int SLOTS = 2;
	public static final double HP = 80, ARMOR = 12, DAMAGE = 9;
	public static final int ATTACK_TICKS = 30;
	public static final int MAX_TRAMPLE = 3;
	/** Walk speed (a heavy beast: slower than a Rootling 0.30, a little faster than the Brassback 0.25). */
	public static final double SPEED = 0.27;
	/** A charge starts within this distance of its target (blocks, centre to centre) ... */
	public static final double CHARGE_RANGE = 7.0;
	/** ... runs this far past it (the trample) ... */
	public static final double CHARGE_OVERRUN = 2.5;
	/** ... never further than this in all ... */
	public static final double CHARGE_MAX_RUN = 9.0;
	/** ... at this speed (blocks per tick). */
	public static final double CHARGE_SPEED = 0.75;
	/** Knockback strength of a trample hit on a non-boss mob (vanilla knockback units; knockback resistance applies). */
	public static final double TRAMPLE_KNOCKBACK = 0.9;
	/** The horn blast at the start of a call is heard once per this many ticks per player (no spam when a call is cancelled). */
	public static final int BLAST_TICKS = 60;

	public static EntityType<WarYakEntity> TYPE;
	public static SoundEvent HORN_CALL, AMBIENT, HURT, DEATH, STEP, CHARGE, TRAMPLE;

	private static final Map<UUID, Long> LAST_BLAST = new HashMap<>();
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		Identifier rid = Gear3Content.id(ENTITY_ID);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) {
			@SuppressWarnings("unchecked")
			EntityType<WarYakEntity> t = (EntityType<WarYakEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
			TYPE = t;
		} else {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
			TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<WarYakEntity>of(WarYakEntity::new, MobCategory.MISC).sized(1.3F, 1.7F).eyeHeight(1.35F)
					.clientTrackingRange(10).noSave().noLootTable().build(key));
			FabricDefaultAttributeRegistry.register(TYPE, WarYakEntity.createAttributes());
		}
		HORN_CALL = sound("item.war_yak_horn.call");
		AMBIENT = sound("entity.war_yak.ambient");
		HURT = sound("entity.war_yak.hurt");
		DEATH = sound("entity.war_yak.death");
		STEP = sound("entity.war_yak.step");
		CHARGE = sound("entity.war_yak.charge");
		TRAMPLE = sound("entity.war_yak.trample");
		MinionSlots.init();
		MinionSlots.setWeight(TYPE, SLOTS);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> LAST_BLAST.clear());
	}

	private static SoundEvent sound(String name) {
		Identifier id = Gear3Content.id(name);
		if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) return BuiltInRegistries.SOUND_EVENT.getValue(id);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** The war-horn blast when a call starts (heard about 64 blocks away); at most once per {@link #BLAST_TICKS} per player. */
	public static boolean hornBlast(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long last = LAST_BLAST.get(p.getUUID());
		if (last != null && now - last >= 0 && now - last < BLAST_TICKS) return false;
		LAST_BLAST.put(p.getUUID(), now);
		p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), HORN_CALL, SoundSource.PLAYERS, 4.0F, 1.0F);
		return true;
	}

	/** Self-test: forget the blast timers. */
	public static void resetBlasts() {
		LAST_BLAST.clear();
	}

	// ---------------------------------------------------------------- kill switch (read once, at class load; nothing polls)

	private static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true; // a missing or malformed file keeps the default (on)
		}
	}
}
