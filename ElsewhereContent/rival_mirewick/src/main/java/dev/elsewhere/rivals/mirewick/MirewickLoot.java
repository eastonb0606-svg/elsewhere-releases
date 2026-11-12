package dev.elsewhere.rivals.mirewick;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomEvents;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.mirewick.item.MirewickItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mirewick soldier drops (RIVALS.md 4.1): Lantern Scrip, 1-3 for rank and file, 4-8 for officers; Wardmen drop the Mirewick trim
 * template (8 percent), Lantern-Bearers the banner pattern (15 percent), and Pikemaster Dunstan Reeve (a Wardman named so) the Reedcutter Pike (20 percent, damaged).
 * Free Company sellswords are not Mirewick soldiers and drop none of this.
 */
public final class MirewickLoot {
	private MirewickLoot() {}

	public static final String DUNSTAN = "Dunstan Reeve";

	public static void init() {
		KingdomEvents.SOLDIER_DIED.register(MirewickLoot::onDied);
		// Until the court's garrison calls spawnDunstan itself, the keep's gate captain is promoted in place (once per world, never reborn).
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 200 == 11) adoptDunstan(srv);
		});
	}

	/** Court garrison tag on soldiers it posted. */
	static final String GARRISON_TAG = "elsewhere_court_garrison";

	/** Promotes one garrison Reeve or Wardman of the Mirewick castle to Pikemaster Dunstan Reeve if the world has none yet. Returns him, or null. */
	public static @Nullable KingdomSoldier adoptDunstan(MinecraftServer srv) {
		MirewickState st = MirewickState.get(srv);
		if (st.dunstan != null) return null;
		ServerLevel level = srv.overworld();
		for (KingdomSoldier s : KingdomPerf.loaded(level)) {
			if (!s.isAlive() || !s.factionId().equals(MirewickContent.FACTION) || !s.entityTags().contains(GARRISON_TAG) || s.hasCustomName()) continue;
			String u = s.unitId().getPath();
			if (!u.equals("mirewick_reeve") && !u.equals("mirewick_wardman")) continue;
			dress(s);
			st.dunstan = s.getUUID();
			st.setDirty();
			return s;
		}
		return null;
	}

	static @Nullable ServerPlayer killer(DamageSource src) {
		return src.getEntity() instanceof ServerPlayer p ? p : null;
	}

	public static void onDied(ServerLevel level, KingdomSoldier s, DamageSource src) {
		if (!s.factionId().equals(MirewickContent.FACTION) || killer(src) == null) return;
		for (ItemStack st : roll(s, level.getRandom().nextDouble(), level.getRandom().nextDouble(), level.getRandom().nextInt(5), level.getRandom().nextInt(3))) s.spawnAtLocation(level, st);
	}

	/** The drops for a dead Mirewick soldier given three uniform draws and the scrip rolls (pure, for tests). */
	public static java.util.List<ItemStack> roll(KingdomSoldier s, double a, double b, int officerRoll, int lineRoll) {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		boolean officer = s.isOfficer();
		out.add(new ItemStack(MirewickItems.SCRIP, officer ? 4 + officerRoll : 1 + lineRoll));
		String unit = s.unitId().getPath();
		if (unit.equals("mirewick_wardman") && a < 0.08) out.add(new ItemStack(MirewickItems.TRIM_TEMPLATE));
		if (isDunstan(s) && b < 0.20) {
			ItemStack pike = new ItemStack(MirewickItems.REEDCUTTER_PIKE);
			pike.setDamageValue(pike.getMaxDamage() / 2);
			out.add(pike);
		}
		if (unit.equals("mirewick_lantern_bearer") && a < 0.15) out.add(new ItemStack(MirewickItems.BANNER_PATTERN));
		return out;
	}

	public static boolean isDunstan(KingdomSoldier s) {
		return s.hasCustomName() && s.getCustomName().getString().equals(DUNSTAN);
	}

	/** Spawns Pikemaster Dunstan Reeve (the keep's elite non-boss captain): a Wardman with the pike, 1.5x health. The court's castle garrison may call this. */
	public static @Nullable KingdomSoldier spawnDunstan(ServerLevel level, Vec3 pos) {
		KingdomSoldier s = KingdomApi.spawnSoldier(level, Identifier.fromNamespaceAndPath("elsewhere", "mirewick_wardman"), MirewickContent.FACTION, pos, null);
		if (s == null) return null;
		dress(s);
		MirewickState st = MirewickState.get(level.getServer());
		if (st.dunstan == null) {
			st.dunstan = s.getUUID();
			st.setDirty();
		}
		return s;
	}

	/** Name, pike and 1.5x health. */
	static void dress(KingdomSoldier s) {
		s.setCustomName(Component.literal(DUNSTAN));
		s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MirewickItems.REEDCUTTER_PIKE));
		s.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		var hp = s.getAttribute(Attributes.MAX_HEALTH);
		hp.setBaseValue(hp.getBaseValue() * 1.5);
		s.setHealth(s.getMaxHealth());
	}
}
