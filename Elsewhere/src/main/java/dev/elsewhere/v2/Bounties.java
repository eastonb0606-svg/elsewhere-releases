package dev.elsewhere.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.Progress;
import dev.elsewhere.core.trophy.Trophies;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * new-bounty-boards: data-driven bounties per trader ({@code data/<ns>/elsewhere_bounty/<trader>.json}). Each day (Overworld
 * day) every trader offers 3 of its bounties; a player can finish each once per day, so at most 3 per trader per day (not
 * grindy). Sneak-use the trader to see them; claim with the [Claim] link ({@code /elsewhere_bounty claim <trader> <slot>}).
 * Rewards are the dimension's currency and XP only, never gear or boss items (never progress-skipping).
 * File: {"trader": "elsewhere:sap_tapper", "currency": "elsewhere:pale_bark", "bounties": [
 *   {"type": "deliver", "item": "minecraft:resin_clump", "count": 16, "pay": 4, "xp": 20},
 *   {"type": "slay", "entity": "elsewhere:creaking_husk", "count": 4, "pay": 6, "xp": 40}]}
 * Entries whose item/entity/currency id is not registered are skipped (modules may be absent); a missing currency pays emeralds.
 */
public final class Bounties {
	private Bounties() {}

	public static final int PER_DAY = 3;
	public static final int MAX_PAY = 12;
	public static final int MAX_XP = 80;

	public record Bounty(String type, String target, int count, int pay, int xp) {}

	public record Board(String trader, String currency, List<Bounty> bounties) {}

	public static final Map<String, Board> BOARDS = new LinkedHashMap<>();

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(Bounties::load);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, rm, ok) -> load(server));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && sp.isShiftKeyDown()) {
				Board b = BOARDS.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
				if (b != null) {
					show(sp, b);
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p) {
				onKill(p, entity);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dispatcher.register(Commands.literal("elsewhere_bounty")
			.then(Commands.literal("claim").then(Commands.argument("trader", StringArgumentType.string()).then(Commands.argument("slot", IntegerArgumentType.integer(0, PER_DAY - 1))
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					claim(p, StringArgumentType.getString(c, "trader"), IntegerArgumentType.getInteger(c, "slot"));
					return 1;
				}))))));
	}

	public static void load(final MinecraftServer server) {
		BOARDS.clear();
		Map<Identifier, Resource> found = server.getResourceManager().listResources("elsewhere_bounty", id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
			try (Reader r = e.getValue().openAsReader()) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				String trader = o.get("trader").getAsString();
				Identifier tid = Identifier.tryParse(trader);
				if (tid == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(tid)) {
					continue;
				}
				List<Bounty> list = new ArrayList<>();
				for (JsonElement el : o.getAsJsonArray("bounties")) {
					JsonObject b = el.getAsJsonObject();
					String type = b.get("type").getAsString();
					String target = b.has("item") ? b.get("item").getAsString() : b.get("entity").getAsString();
					Identifier id = Identifier.tryParse(target);
					boolean ok = id != null && ("deliver".equals(type) ? BuiltInRegistries.ITEM.containsKey(id) : BuiltInRegistries.ENTITY_TYPE.containsKey(id));
					if (ok) {
						list.add(new Bounty(type, target, Math.max(1, b.get("count").getAsInt()), Math.min(MAX_PAY, b.get("pay").getAsInt()),
							Math.min(MAX_XP, b.get("xp").getAsInt())));
					}
				}
				if (!list.isEmpty()) {
					BOARDS.put(trader, new Board(trader, o.get("currency").getAsString(), list));
				}
			} catch (Exception ex) {
				Elsewhere.LOG.warn("Bounty file {} skipped: {}", e.getKey(), ex.toString());
			}
		}
		Elsewhere.LOG.info("Bounty boards: {} traders", BOARDS.size());
	}

	public static long day(final MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	/** Today's offers for a board (3, or fewer if the board has fewer). Same for every player. */
	public static List<Bounty> today(final MinecraftServer server, final Board b) {
		List<Bounty> pool = new ArrayList<>(b.bounties());
		java.util.Collections.shuffle(pool, new Random(day(server) * 31L + b.trader().hashCode()));
		return pool.subList(0, Math.min(PER_DAY, pool.size()));
	}

	private static String key(final MinecraftServer server, final Board b, final int slot) {
		return "bty:" + day(server) + ":" + b.trader() + ":" + slot;
	}

	public static void show(final ServerPlayer p, final Board b) {
		MinecraftServer server = p.level().getServer();
		p.sendSystemMessage(Component.translatable("message.elsewhere.bounty.title").withStyle(ChatFormatting.GOLD));
		List<Bounty> list = today(server, b);
		for (int i = 0; i < list.size(); i++) {
			Bounty y = list.get(i);
			String k = key(server, b, i);
			boolean done = "done".equals(Progress.get(p, k));
			MutableComponent line = Component.literal(" " + (i + 1) + ". ").append(describe(y, (int)Progress.getLong(p, k + ":kills", 0L)))
				.append(Component.translatable("message.elsewhere.bounty.reward", y.pay(), Component.translatable(currencyItem(b).getDescriptionId()), y.xp()).withStyle(ChatFormatting.GRAY));
			if (done) {
				line.append(Component.translatable("message.elsewhere.bounty.done").withStyle(ChatFormatting.DARK_GREEN));
			} else {
				final String cmd = "/elsewhere_bounty claim \"" + b.trader() + "\" " + i;
				line.append(Component.literal(" ")).append(Component.translatable("message.elsewhere.bounty.claim").withStyle(s -> s.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent.RunCommand(cmd))));
			}
			p.sendSystemMessage(line);
		}
	}

	private static Component describe(final Bounty y, final int kills) {
		Identifier id = Identifier.parse(y.target());
		if ("deliver".equals(y.type())) {
			return Component.translatable("message.elsewhere.bounty.deliver", y.count(), Component.translatable(BuiltInRegistries.ITEM.getValue(id).getDescriptionId()));
		}
		return Component.translatable("message.elsewhere.bounty.slay", y.count(), BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription(), Math.min(kills, y.count()));
	}

	public static Item currencyItem(final Board b) {
		Identifier id = Identifier.tryParse(b.currency());
		return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : Items.EMERALD;
	}

	private static void onKill(final ServerPlayer p, final Entity dead) {
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
		MinecraftServer server = p.level().getServer();
		for (Board b : BOARDS.values()) {
			List<Bounty> list = today(server, b);
			for (int i = 0; i < list.size(); i++) {
				if ("slay".equals(list.get(i).type()) && list.get(i).target().equals(type)) {
					String k = key(server, b, i) + ":kills";
					Progress.set(p, k, Long.toString(Progress.getLong(p, k, 0L) + 1));
				}
			}
		}
	}

	/** Claims one of today's bounties. Returns true if paid. */
	public static boolean claim(final ServerPlayer p, final String trader, final int slot) {
		MinecraftServer server = p.level().getServer();
		Board b = BOARDS.get(trader);
		if (b == null) {
			return false;
		}
		List<Bounty> list = today(server, b);
		if (slot < 0 || slot >= list.size()) {
			return false;
		}
		String k = key(server, b, slot);
		if ("done".equals(Progress.get(p, k))) {
			p.sendSystemMessage(Component.translatable("message.elsewhere.bounty.already").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Bounty y = list.get(slot);
		if ("deliver".equals(y.type())) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(y.target()));
			int have = p.getInventory().countItem(item);
			if (have < y.count()) {
				p.sendSystemMessage(Component.translatable("message.elsewhere.bounty.missing").withStyle(ChatFormatting.RED));
				return false;
			}
			int left = y.count();
			for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (s.is(item)) {
					int take = Math.min(left, s.getCount());
					s.shrink(take);
					left -= take;
				}
			}
		} else if (Progress.getLong(p, k + ":kills", 0L) < y.count()) {
			p.sendSystemMessage(Component.translatable("message.elsewhere.bounty.missing").withStyle(ChatFormatting.RED));
			return false;
		}
		cleanup(p, server);
		Progress.set(p, k, "done");
		dev.elsewhere.boss.kit.BossKit.give(p, new ItemStack(currencyItem(b), y.pay()));
		p.giveExperiencePoints(y.xp());
		p.sendSystemMessage(Component.translatable("message.elsewhere.bounty.paid").withStyle(ChatFormatting.GREEN));
		Trophies.grant(p, "bounty");
		return true;
	}

	/** Drops bounty records of earlier days. */
	private static void cleanup(final ServerPlayer p, final MinecraftServer server) {
		String today = "bty:" + day(server) + ":";
		Map<String, String> m = new HashMap<>(Progress.view(p));
		if (m.keySet().removeIf(k -> k.startsWith("bty:") && !k.startsWith(today))) {
			p.setAttached(dev.elsewhere.registry.ModAttachments.PROGRESS, m);
		}
	}

	public static @Nullable Board board(final String trader) {
		return BOARDS.get(trader);
	}
}
