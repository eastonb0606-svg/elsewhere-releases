package dev.elsewhere.kingdom;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Mild kingdom aggression (replaces player-driven war). Killing the kingdom's officers adds aggression to the killer
 * (unit {@code aggression}: sergeants and standard-bearers 2, captains 3; Lords/King/scouts via
 * {@link KingdomApi#addAggression}). It decays by 1 per in-game day.
 * <ul>
 * <li>At {@link #MARK_THRESHOLD}+ Aldermark soldiers attack the player on sight even while the faction is neutral.</li>
 * <li>At {@link #HUNT_THRESHOLD}+ a hunting squad (sergeant + 3-7) may set out after the player: checked once a minute,
 * chance 10% (+2% per point above, max 30%), at most one hunt per 30 min, overworld surface only, never in peaceful or
 * creative. The squad marches on the player for 5 min, then gives up and goes home (removed out of sight).</li>
 * </ul>
 */
public final class KingdomAggression {
	private KingdomAggression() {}

	public static final float MARK_THRESHOLD = 4F, HUNT_THRESHOLD = 6F, MAX = 20F, DECAY_PER_DAY = 1F;
	public static final int CHECK_TICKS = 1200, HUNT_COOLDOWN = 36000, HUNT_TICKS = 6000;
	/** Tests: force the next hunt check to succeed. */
	public static boolean forceHunt;
	public static long huntsStarted;

	static KingdomState state(ServerLevel any) {
		return KingdomState.get(any.getServer().overworld());
	}

	// ---- ledgers: one per faction (Aldermark's is the 2.0 one) --------------------------------------------------------

	public static float get(ServerLevel any, UUID player) {
		return get(any, player, Factions.ALDERMARK);
	}

	public static float get(ServerLevel any, UUID player, Identifier faction) {
		return state(any).ledger(faction).getOrDefault(player, 0F);
	}

	public static void set(ServerLevel any, UUID player, float v) {
		set(any, player, Factions.ALDERMARK, v);
	}

	public static void set(ServerLevel any, UUID player, Identifier faction, float v) {
		KingdomState st = state(any);
		Map<UUID, Float> m = st.ledger(faction);
		if (v <= 0F) m.remove(player);
		else m.put(player, Math.min(MAX, v));
		st.setDirty();
	}

	public static void add(ServerLevel any, UUID player, float amount) {
		add(any, player, Factions.ALDERMARK, amount);
	}

	public static void add(ServerLevel any, UUID player, Identifier faction, float amount) {
		set(any, player, faction, get(any, player, faction) + amount);
	}

	/** True when soldiers of {@code faction} should attack this player on sight because of the player's aggression towards that faction. */
	public static boolean isMarked(Player p, Identifier faction) {
		return p.level() instanceof ServerLevel sl && get(sl, p.getUUID(), faction) >= MARK_THRESHOLD;
	}

	static void onSoldierDied(ServerLevel level, KingdomSoldier s, DamageSource src) {
		float v = s.unit().aggressionValue();
		if (v > 0F && src.getEntity() instanceof ServerPlayer p && !p.isCreative()) add(level, p.getUUID(), Factions.ledgerOf(s.factionId()), v);
	}

	static void tick(ServerLevel overworld, KingdomState st) {
		long now = overworld.getGameTime();
		if (now % CHECK_TICKS != 0 && !forceHunt) return;
		if (now % CHECK_TICKS == 0) {
			float d = DECAY_PER_DAY * CHECK_TICKS / 24000F;
			boolean any = false;
			for (Identifier f : st.ledgerFactions()) {
				Map<UUID, Float> m = st.ledger(f);
				if (m.isEmpty()) continue;
				any = true;
				m.replaceAll((k, v) -> v - d);
				m.values().removeIf(v -> v <= 0F);
			}
			if (any) st.setDirty();
		}
		if (overworld.getDifficulty() == Difficulty.PEACEFUL) return;
		for (Identifier faction : st.ledgerFactions()) {
			if (st.isFallen(faction)) continue;
			Map<UUID, Float> ledger = st.ledger(faction);
			if (ledger.isEmpty()) continue;
			for (ServerPlayer p : List.copyOf(overworld.players())) {
				float a = ledger.getOrDefault(p.getUUID(), 0F);
				if (a < HUNT_THRESHOLD || p.isCreative() || p.isSpectator()) continue;
				if (!Standing.huntAllowed(overworld, p, faction)) continue; // rivals hunt only Hostile players who killed officers, Lords or a ruler
				if (hasHunt(st, p.getUUID(), faction)) continue;
				Long last = st.hunts(faction).get(p.getUUID());
				if (!forceHunt && last != null && now - last < HUNT_COOLDOWN) continue;
				float chance = Math.min(0.3F, 0.1F + 0.02F * (a - HUNT_THRESHOLD));
				if (!forceHunt && overworld.getRandom().nextFloat() >= chance) continue;
				if (!overworld.canSeeSky(p.blockPosition().above())) continue; // never dig in after a player underground
				if (startHunt(overworld, p, faction, a) != null) st.hunts(faction).put(p.getUUID(), now);
			}
		}
		forceHunt = false;
	}

	static boolean hasHunt(KingdomState st, UUID p) {
		return hasHunt(st, p, Factions.ALDERMARK);
	}

	static boolean hasHunt(KingdomState st, UUID p, Identifier faction) {
		for (Squad q : st.squads()) if (p.equals(q.hunt) && faction.equals(q.faction)) return true;
		return false;
	}

	/** Sends an Aldermark hunting squad after {@code p} (see {@link #startHunt(ServerLevel, ServerPlayer, Identifier, float)}). */
	public static Squad startHunt(ServerLevel level, ServerPlayer p, float aggression) {
		return startHunt(level, p, Factions.ALDERMARK, aggression);
	}

	/** Sends a hunting squad of {@code faction} after {@code p} (spawned 40-56 blocks away on the surface). Null when no spot or no room. */
	public static Squad startHunt(ServerLevel level, ServerPlayer p, Identifier faction, float aggression) {
		var r = level.getRandom();
		for (int tries = 0; tries < 8; tries++) {
			double ang = r.nextDouble() * Math.PI * 2, dist = 40 + r.nextInt(17);
			BlockPos at = BlockPos.containing(p.getX() + Math.cos(ang) * dist, p.getY(), p.getZ() + Math.sin(ang) * dist);
			if (!level.isLoaded(at) || !level.isPositionEntityTicking(at)) continue;
			at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
			if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) continue;
			int n = 3 + Math.min(4, (int) ((aggression - HUNT_THRESHOLD) / 2));
			List<KingdomSoldier> sq = KingdomApi.spawnSquad(level, at, faction, Squad.Formation.LINE, UnitTypes.SERGEANT, ArmyComposer.members(r, n, false));
			if (sq.isEmpty() || sq.get(0).squad() == null) return null;
			Squad q = sq.get(0).squad();
			long now = level.getGameTime();
			q.setHunt(p.getUUID(), now + HUNT_TICKS);
			q.setObjective(p.blockPosition());
			huntsStarted++;
			level.playSound(null, at, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 12.0F, 1.2F);
			p.sendOverlayMessage(Component.translatable("message.elsewhere.kingdom.hunt_start").withStyle(ChatFormatting.GOLD));
			KingdomEvents.HUNT_STARTED.invoker().onHunt(level, p, q);
			return q;
		}
		return null;
	}
}
