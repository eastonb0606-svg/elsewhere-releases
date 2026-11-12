package dev.elsewhere.boss_ui;

import com.google.gson.JsonParser;
import dev.elsewhere.hud.ElsewhereHud;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 3.0 polish P5 client hook: appends {@link TooltipLines} to Elsewhere items. Kill switch:
 * {@code "polishTooltips": false} in config/elsewhere-client.json.
 */
public final class PolishTooltips {
	private PolishTooltips() {}

	private static Boolean enabled;
	private static boolean registered;

	/** Test hook: the gametest cannot press Shift. */
	public static volatile boolean forceShift;

	static boolean enabled() {
		if (enabled == null) {
			boolean on = true;
			try {
				if (Files.exists(ElsewhereHud.file())) {
					var o = JsonParser.parseString(Files.readString(ElsewhereHud.file())).getAsJsonObject();
					if (o.has("polishTooltips")) on = o.get("polishTooltips").getAsBoolean();
				}
			} catch (Exception ignored) {}
			enabled = on;
		}
		return enabled;
	}

	public static void init() {
		if (registered) return;
		registered = true;
		ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
			if (!enabled() || stack.isEmpty()) return;
			if (!"elsewhere".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) return;
			lines.addAll(TooltipLines.build(stack, shiftDown()));
		});
	}

	private static boolean shiftDown() {
		if (forceShift) return true;
		return Minecraft.getInstance().hasShiftDown();
	}
}
