package dev.elsewhere.rivals.azmar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Winged-Scarab Standard (Sultana, 50 percent): plant on top of a block (a lapis-blue banner). Players within 16 blocks get
 * Absorption I, refreshed every 10 s. One per player: planting again moves it. The item is kept.
 */
public class StandardItem extends Item {
	public static final int RADIUS = 16, REFRESH = 200;
	private record Planted(ResourceKey<Level> dim, long pos) {}
	private static final Map<UUID, Planted> PLANTED = new HashMap<>();

	public StandardItem(Item.Properties p) {
		super(p);
	}

	// ---- persistence: one line per planted standard in <world>/data/elsewhere_azmar_standards.txt (player|dimension|packed pos) ----
	private static java.nio.file.Path file(net.minecraft.server.MinecraftServer srv) {
		return srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("elsewhere_azmar_standards.txt");
	}

	static void save(net.minecraft.server.MinecraftServer srv) {
		try {
			java.nio.file.Path f = file(srv);
			java.nio.file.Files.createDirectories(f.getParent());
			java.util.List<String> lines = new java.util.ArrayList<>();
			PLANTED.forEach((u, pl) -> lines.add(u + "|" + pl.dim.identifier() + "|" + pl.pos));
			java.nio.file.Files.write(f, lines);
		} catch (java.io.IOException e) {
			AzmarContent.LOG.warn("could not save planted standards", e);
		}
	}

	static void load(net.minecraft.server.MinecraftServer srv) {
		PLANTED.clear();
		try {
			java.nio.file.Path f = file(srv);
			if (!java.nio.file.Files.exists(f)) return;
			for (String line : java.nio.file.Files.readAllLines(f)) {
				String[] a = line.split("\\|");
				if (a.length != 3) continue;
				var dim = net.minecraft.resources.Identifier.tryParse(a[1]);
				if (dim == null) continue;
				PLANTED.put(UUID.fromString(a[0]), new Planted(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim), Long.parseLong(a[2])));
			}
		} catch (Exception e) {
			AzmarContent.LOG.warn("could not load planted standards", e);
		}
	}

	/** Forgets in-memory state (tests simulate a restart: save, clear, load). */
	public static void restartForTest(net.minecraft.server.MinecraftServer srv) {
		save(srv);
		PLANTED.clear();
		load(srv);
	}

	static {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(StandardItem::load);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(StandardItem::save);
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % REFRESH != 0) return;
			for (Iterator<Map.Entry<UUID, Planted>> it = PLANTED.entrySet().iterator(); it.hasNext();) {
				Planted pl = it.next().getValue();
				ServerLevel lv = srv.getLevel(pl.dim);
				BlockPos pos = BlockPos.of(pl.pos);
				if (lv == null || !lv.isLoaded(pos)) continue;
				if (!lv.getBlockState(pos).is(Blocks.BANNER.blue())) { it.remove(); continue; }
				for (ServerPlayer p : lv.players())
					if (p.blockPosition().distSqr(pos) <= RADIUS * RADIUS) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, REFRESH + 40, 0, true, true));
			}
		});
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
		Player player = ctx.getPlayer();
		if (!(ctx.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.SUCCESS;
		BlockPos pos = ctx.getClickedPos().above();
		if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
		return plant(level, player, pos) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	public static boolean plant(ServerLevel level, Player player, BlockPos pos) {
		Planted old = PLANTED.get(player.getUUID());
		if (old != null && old.dim.equals(level.dimension())) {
			BlockPos op = BlockPos.of(old.pos);
			if (level.getBlockState(op).is(Blocks.BANNER.blue())) level.removeBlock(op, false);
		}
		int rot = Math.floorMod((int) Math.floor((180.0F + player.getYRot()) * 16.0F / 360.0F + 0.5), 16);
		BlockState s = Blocks.BANNER.blue().defaultBlockState().setValue(BannerBlock.ROTATION, rot);
		if (!level.setBlock(pos, s, 3)) return false;
		PLANTED.put(player.getUUID(), new Planted(level.dimension(), pos.asLong()));
		save(level.getServer());
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.elsewhere.azmar_standard_planted"));
		return true;
	}

	public static int plantedCount() {
		return PLANTED.size();
	}
}
