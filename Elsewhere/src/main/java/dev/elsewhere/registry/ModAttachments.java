package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.travel.ReturnPoint;
import dev.elsewhere.core.trip.TripState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	public static final AttachmentType<ReturnPoint> RETURN_POINT = AttachmentRegistry.<ReturnPoint>builder()
		.persistent(ReturnPoint.CODEC).copyOnDeath().buildAndRegister(Elsewhere.id("return_point"));
	public static final AttachmentType<TripState> TRIP_STATE = AttachmentRegistry.<TripState>builder()
		.persistent(TripState.CODEC).copyOnDeath().initializer(TripState::new).buildAndRegister(Elsewhere.id("trip_state"));

	/** Per player {@code /elsewhere trips on|off} (absent = on). Same id the 0.3.0 beta overlay used, so saved choices carry over. */
	public static final AttachmentType<Boolean> TRIPS_ENABLED = AttachmentRegistry.<Boolean>builder()
		.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath().buildAndRegister(Elsewhere.id("trips_enabled"));
	/** Per player boss kill counts by entity type id (first-kill vs repeat drop rates). */
	public static final AttachmentType<java.util.Map<String, Integer>> BOSS_KILLS = AttachmentRegistry.<java.util.Map<String, Integer>>builder()
		.persistent(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.INT))
		.copyOnDeath().buildAndRegister(Elsewhere.id("boss_kills"));


	/** 2.0 per-player progress (string map): Rift Anchors, Waystones, Atlas discoveries, bounties, first trip. Copied on death. */
	public static final AttachmentType<java.util.Map<String, String>> PROGRESS = AttachmentRegistry.<java.util.Map<String, String>>builder()
		.persistent(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.STRING))
		.copyOnDeath().buildAndRegister(Elsewhere.id("progress"));

	private ModAttachments() {
	}

	public static void init() {
	}
}
