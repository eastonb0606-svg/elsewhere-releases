package dev.elsewhere.boss_ui;

import dev.elsewhere.boss.kit.TelegraphNet;

/**
 * Common (both sides) init of boss_ui. merge.py calls it from BetaInit (it finds {@code *Content.init()}); the dev
 * {@link DevEntry} calls it too. Registers the P2 telegraph payload type, so the server can send telegraph shapes and
 * the client can decode them.
 */
public final class BossUiContent {
	private BossUiContent() {}

	public static void init() {
		TelegraphNet.init();
	}
}
