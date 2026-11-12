package dev.elsewhere.boss_ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.hud.ElsewhereHud;
import java.io.InputStreamReader;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client side of the custom boss bars. Per-player switch {@code bossbars} in the shared Elsewhere HUD settings
 * (/elsewhere_hud bossbars auto|always|hidden): Auto = framed bars for bosses listed in boss_bars.json (others stay
 * vanilla), Always = framed bars for every boss bar, Hidden = vanilla bars everywhere.
 */
public class BossUiClient implements ClientModInitializer {
	public static final Identifier DATA = Identifier.fromNamespaceAndPath("elsewhere", "boss_bars.json");
	private static Object loadedFor;
	private static Map<String, BossBarData.Entry> data = Map.of();

	@Override
	public void onInitializeClient() {
		ElsewhereHud.init();
		ElsewhereHud.registerMeter("bossbars", "bossbars");
		PolishTooltips.init();
	}

	/** Data from the resource manager (resource packs may override boss_bars.json); re-read when the manager changes. */
	public static Map<String, BossBarData.Entry> data() {
		Object rm = Minecraft.getInstance().getResourceManager();
		if (rm != loadedFor) {
			loadedFor = rm;
			BossBarFx.clearCaches();
			try {
				var res = Minecraft.getInstance().getResourceManager().getResource(DATA);
				if (res.isPresent()) {
					try (var in = new InputStreamReader(res.get().open())) {
						JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
						data = BossBarData.parse(root);
					}
				} else data = Map.of();
			} catch (Exception e) {
				data = Map.of();
			}
		}
		return data;
	}

	static String nameOfRaw(String id) {
		return Component.translatable("entity." + id.replace(':', '.')).getString();
	}
}
