package dev.elsewhere.core.trophy;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.kit.BossKit;
import dev.elsewhere.core.Progress;
import dev.elsewhere.core.travel.Anchors;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.tiergear.TierGear;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * new-boss-trophies + polish-advancements (boss half): every player who helped defeat a boss (killer + players within 48
 * blocks) is credited. First kill: a Boss Trophy (statue block) and the boss's advancement; all 24 -> "Hall of Trophies".
 * Also places a Boss Waystone at the arena (new-boss-waystones) and remembers it for the credited players.
 */
public final class Trophies {
	private Trophies() {}

	/** The 24 bosses (entity id path) and the Atlas page they belong to. Mounts (Grimhorn, Ashtusk) credit their rider. */
	public static final Map<String, String> BOSSES = new LinkedHashMap<>();
	static {
		String[][] rows = {
			{"mother_creak", "pale_hollow"}, {"selenne", "pale_hollow"},
			{"kazehana", "petalwind"}, {"tempestra", "petalwind"},
			{"heliodor", "glasswaste"}, {"ankhara", "glasswaste"},
			{"verdigrim", "verdigris_sea"}, {"galvanox", "verdigris_sea"},
			{"thurgrim", "ironpeak"}, {"hrolf", "ironpeak"},
			{"percolax", "greenhaze"}, {"grumblegut", "greenhaze"},
			{"auris", "the_hush"}, {"lithara", "the_hush"},
			{"gulpmother", "overworld"}, {"strawgrim", "overworld"}, {"neferkhet", "overworld"}, {"rimeback", "overworld"}, {"vorlagh", "overworld"},
			{"grunthar", "the_nether"}, {"solaxis", "the_nether"}, {"mournmaw", "the_nether"},
			{"ossyra", "the_end"}, {"aphelion", "the_end"}};
		for (String[] r : rows) {
			BOSSES.put(r[0], r[1]);
		}
	}

	public static final double CREDIT_RADIUS = 48.0;

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && TierGear.isBoss(entity)) {
				onBossDeath(level, entity, source.getEntity() instanceof ServerPlayer p ? p : null);
			}
		});
	}

	public static String bossId(final LivingEntity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
	}

	/** Credits players, gives trophies, grants advancements, places the waystone. Returns the credited players. */
	public static List<ServerPlayer> onBossDeath(final ServerLevel level, final LivingEntity boss, final ServerPlayer killer) {
		String id = bossId(boss);
		List<ServerPlayer> credited = new ArrayList<>();
		if (!BOSSES.containsKey(id)) {
			return credited; // mounts and helpers
		}
		if (killer != null) {
			credited.add(killer);
		}
		for (ServerPlayer p : level.players()) {
			if (!credited.contains(p) && !p.isSpectator() && p.distanceToSqr(boss) < CREDIT_RADIUS * CREDIT_RADIUS) {
				credited.add(p);
			}
		}
		BlockPos stone = credited.isEmpty() ? null : Anchors.placeWaystone(level, boss.blockPosition());
		for (ServerPlayer p : credited) {
			credit(p, id);
			if (stone != null) {
				Anchors.rememberWaystone(p, level.dimension(), stone, false);
			}
		}
		return credited;
	}

	public static boolean credit(final ServerPlayer p, final String id) {
		String key = "boss:" + id;
		boolean first = !Progress.has(p, key);
		Progress.set(p, key, Long.toString(Progress.getLong(p, key, 0L) + 1));
		if (first) {
			BossKit.give(p, stack(id));
			p.sendSystemMessage(Component.translatable("message.elsewhere.trophy.earned", bossName(id)).withStyle(ChatFormatting.GOLD));
			grant(p, "boss/" + id);
			if (killedCount(p) >= BOSSES.size()) {
				grant(p, "boss/all_trophies");
			}
		}
		return first;
	}

	public static int killedCount(final ServerPlayer p) {
		int n = 0;
		for (String id : BOSSES.keySet()) {
			if (Progress.has(p, "boss:" + id)) {
				n++;
			}
		}
		return n;
	}

	public static Component bossName(final String id) {
		return Component.translatable("entity.elsewhere." + id);
	}

	public static ItemStack stack(final String id) {
		ItemStack s = new ItemStack(ModBlocks.BOSS_TROPHY.asItem());
		CompoundTag tag = new CompoundTag();
		tag.putString("boss", id);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.ITEM_NAME, Component.translatable("block.elsewhere.boss_trophy.named", bossName(id)));
		return s;
	}

	public static String bossOf(final ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d == null ? "" : d.copyTag().getStringOr("boss", "");
	}

	/** Grants every criterion of advancement {@code elsewhere:<path>}. Returns false if it doesn't exist or was already done. */
	public static boolean grant(final ServerPlayer p, final String path) {
		AdvancementHolder adv = p.level().getServer().getAdvancements().get(Elsewhere.id(path));
		if (adv == null) {
			return false;
		}
		AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
		if (progress.isDone()) {
			return false;
		}
		boolean any = false;
		for (String c : progress.getRemainingCriteria()) {
			any |= p.getAdvancements().award(adv, c);
		}
		return any;
	}
}
