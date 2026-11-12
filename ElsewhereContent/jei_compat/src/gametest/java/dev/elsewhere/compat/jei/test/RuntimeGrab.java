package dev.elsewhere.compat.jei.test;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

public final class RuntimeGrab implements IModPlugin {
	static volatile IJeiRuntime runtime;

	@Override
	public Identifier getPluginUid() {
		return Identifier.fromNamespaceAndPath("elsewhere", "jei_test_grab");
	}

	@Override
	public void onRuntimeAvailable(final IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}
}
