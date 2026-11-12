package dev.elsewhere.registry;

import dev.elsewhere.palehollow.item.RootAttackersEffect;

/** Registrations that do not fit elsewhere (forces static init of types registered in their own classes). */
public final class ModMisc {
	private ModMisc() {
	}

	public static void init() {
		RootAttackersEffect.TYPE.toString();
	}
}
