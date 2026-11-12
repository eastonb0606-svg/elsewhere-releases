package dev.elsewhere.kingdom.client.idle;

import dev.elsewhere.kingdom.client.AvatarLayers;

/** 3.0 polish P9 client init: the prop layer on the vanilla avatar renderers. Called once from {@code KingdomClient.initClient()}. */
public final class IdleClient {
	private IdleClient() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		AvatarLayers.add((parent, models) -> new IdlePropLayer(parent));
	}
}
