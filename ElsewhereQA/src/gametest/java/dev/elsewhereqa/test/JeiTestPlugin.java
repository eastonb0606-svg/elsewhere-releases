package dev.elsewhereqa.test;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/** Test-only JEI plugin: hands the JEI runtime to {@link JeiShots}. Only JEI ever creates it. */
public final class JeiTestPlugin implements IModPlugin {
	static volatile IJeiRuntime runtime;

	@Override
	public Identifier getPluginUid() {
		return Identifier.fromNamespaceAndPath("elsewhereqa-gametest", "jei_test");
	}

	@Override
	public void onRuntimeAvailable(final IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}
}
