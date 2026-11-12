package dev.elsewhere.kingdom.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Client "soldier detail" option ({@code config/elsewhere-kingdom-client.json}, key {@code soldierDetail}), set in game with
 * {@code /kingdomdetail low|medium|high}:
 * <ul>
 * <li>HIGH: everything at every distance.</li>
 * <li>MEDIUM (default): no tabard beyond 32 blocks, no held items beyond 48.</li>
 * <li>LOW: no tabard beyond 12 blocks, no held items or skin overlay beyond 24, no armor beyond 40.</li>
 * </ul>
 */
public final class KingdomClientConfig {
	private KingdomClientConfig() {}

	public enum Detail { LOW, MEDIUM, HIGH }

	public static Detail detail = Detail.MEDIUM;

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere-kingdom-client.json");
	}

	static void load() {
		try {
			if (Files.exists(path())) {
				JsonObject o = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
				if (o.has("soldierDetail")) detail = Detail.valueOf(o.get("soldierDetail").getAsString().toUpperCase(Locale.ROOT));
			} else save();
		} catch (Exception e) {
			detail = Detail.MEDIUM;
		}
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("kingdomdetail")
			.executes(c -> {
				c.getSource().sendFeedback(Component.translatable("message.elsewhere.kingdom.detail", detail.name().toLowerCase(Locale.ROOT)));
				return 1;
			})
			.then(ClientCommands.argument("level", StringArgumentType.word()).suggests((c, b) -> {
				for (Detail x : Detail.values()) b.suggest(x.name().toLowerCase(Locale.ROOT));
				return b.buildFuture();
			}).executes(c -> {
				try {
					detail = Detail.valueOf(StringArgumentType.getString(c, "level").toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					c.getSource().sendError(Component.literal("low, medium or high"));
					return 0;
				}
				save();
				c.getSource().sendFeedback(Component.translatable("message.elsewhere.kingdom.detail", detail.name().toLowerCase(Locale.ROOT)));
				return 1;
			}))));
	}

	static void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), "{\n  \"soldierDetail\": \"" + detail.name().toLowerCase(Locale.ROOT) + "\"\n}\n");
		} catch (Exception ignored) {
		}
	}

	/** Distances (blocks) beyond which parts are dropped: tabard, held items, skin overlay, armor. */
	static double tabardDist() {
		return switch (detail) { case HIGH -> 1e9; case MEDIUM -> 32; case LOW -> 12; };
	}

	static double itemsDist() {
		return switch (detail) { case HIGH -> 1e9; case MEDIUM -> 48; case LOW -> 24; };
	}

	static double overlayDist() {
		return switch (detail) { case HIGH, MEDIUM -> 1e9; case LOW -> 24; };
	}

	static double armorDist() {
		return switch (detail) { case HIGH, MEDIUM -> 1e9; case LOW -> 40; };
	}
}
