package dev.elsewhere.duelist;

import dev.elsewhere.duelist.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Dev mod initializer (elsewhere_duelist): the module content plus the self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		DuelistContent.init();
		if (Boolean.getBoolean("duelist.selftest")) {
			int[] ticks = {0};
			boolean[] ran = {false};
			ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
				for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) srv.overworld().setChunkForced(cx, cz, true);
			});
			ServerTickEvents.END_SERVER_TICK.register(srv -> {
				if (ran[0]) return;
				var l = srv.overworld();
				if (++ticks[0] < 400 && !l.isPositionEntityTicking(new net.minecraft.core.BlockPos(8, 100, 0))) return;
				ran[0] = true;
				SelfTest.run(srv);
			});
		}
	}
}
