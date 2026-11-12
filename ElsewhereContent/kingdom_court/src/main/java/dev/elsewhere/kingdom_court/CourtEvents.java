package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Court events for the other kingdom lanes. */
public final class CourtEvents {
	private CourtEvents() {}

	public interface RulerDied { void onRulerDied(ServerLevel level, Ruler ruler, @Nullable ServerPlayer killer); }
	public interface Fallen { void onFallen(MinecraftServer server); }
	public interface Town { void onTown(ServerLevel level, BlockPos marketPos); }

	public static final Event<RulerDied> RULER_DIED = EventFactory.createArrayBacked(RulerDied.class, ls -> (l, r, k) -> { for (RulerDied x : ls) x.onRulerDied(l, r, k); });
	/** All six rulers are dead: conquest must stop (K-C), patrols stop (K-D). */
	public static final Event<Fallen> KINGDOM_FALLEN = EventFactory.createArrayBacked(Fallen.class, ls -> s -> { for (Fallen x : ls) x.onFallen(s); });
	public interface FactionFallen { void onFallen(MinecraftServer server, net.minecraft.resources.Identifier faction); }
	/** 2.1: any faction's leaders are all dead (also fires for Aldermark, after {@link #KINGDOM_FALLEN}). {@link #KINGDOM_FALLEN} itself stays Aldermark-only. */
	public static final Event<FactionFallen> FACTION_FALLEN = EventFactory.createArrayBacked(FactionFallen.class, ls -> (s, f) -> { for (FactionFallen x : ls) x.onFallen(s, f); });
	/** A realm town was populated (first visit): market position. */
	public static final Event<Town> TOWN_POPULATED = EventFactory.createArrayBacked(Town.class, ls -> (l, p) -> { for (Town x : ls) x.onTown(l, p); });

	// ---- 2.1 oligarchy, brain bosses ---------------------------------------------------------------------------------

	/** Why the Chair (First Lantern) changed. */
	public enum ChairReason { VAULT_WEEK, CHAIR_DIED, VACANCY_ENDED, SEAT_REFILLED, STARTED }

	/** The council's Chair changed ({@code oldChair} / {@code newChair} are leader slot keys; null = vacant, e.g. for the 1-day vacancy after the Chair died). */
	public interface ChairChanged { void onChairChanged(ServerLevel level, Identifier faction, @Nullable String oldChair, @Nullable String newChair, ChairReason reason); }
	public static final Event<ChairChanged> CHAIR_CHANGED = EventFactory.createArrayBacked(ChairChanged.class, ls -> (l, f, o, n, r) -> { for (ChairChanged x : ls) x.onChairChanged(l, f, o, n, r); });

	/** A council vote was held (war, peace, alliance or hiring). */
	public interface CouncilVote { void onVote(MinecraftServer server, Identifier faction, dev.elsewhere.kingdom_court.api.Oligarchy.Topic topic, dev.elsewhere.kingdom_court.api.Oligarchy.VoteResult result); }
	public static final Event<CouncilVote> COUNCIL_VOTE = EventFactory.createArrayBacked(CouncilVote.class, ls -> (s, f, t, r) -> { for (CouncilVote x : ls) x.onVote(s, f, t, r); });

	/** A dead seat was bought by a new family (same role and kit, a new name). {@code slot} is the leader slot key. */
	public interface SeatRefilled { void onSeatRefilled(ServerLevel level, Identifier faction, String slot, String familyName); }
	public static final Event<SeatRefilled> SEAT_REFILLED = EventFactory.createArrayBacked(SeatRefilled.class, ls -> (l, f, s, n) -> { for (SeatRefilled x : ls) x.onSeatRefilled(l, f, s, n); });

	/** Dark Lanterns: every Magnate died inside the refill window; the Republic falls for good. Fires before {@link #FACTION_FALLEN}. */
	public interface DarkLanterns { void onDarkLanterns(MinecraftServer server, Identifier faction); }
	public static final Event<DarkLanterns> DARK_LANTERNS = EventFactory.createArrayBacked(DarkLanterns.class, ls -> (s, f) -> { for (DarkLanterns x : ls) x.onDarkLanterns(s, f); });

	/** A leader whose slot is a brain role (the faction file's {@code army.brain.brain_roles}) died ({@code alive=false}) or its seat came back ({@code alive=true}). */
	public interface BrainRole { void onBrainRole(MinecraftServer server, Identifier faction, String slot, boolean alive); }
	public static final Event<BrainRole> BRAIN_ROLE = EventFactory.createArrayBacked(BrainRole.class, ls -> (s, f, r, a) -> { for (BrainRole x : ls) x.onBrainRole(s, f, r, a); });

	/** A leader yielded in a duel (alive, at 10 percent health). */
	public interface DuelEnded { void onDuelEnded(ServerLevel level, ServerPlayer challenger, Identifier faction, String slot, boolean playerWon); }
	public static final Event<DuelEnded> DUEL_ENDED = EventFactory.createArrayBacked(DuelEnded.class, ls -> (l, p, f, s, w) -> { for (DuelEnded x : ls) x.onDuelEnded(l, p, f, s, w); });

	/** How a seat changed hands: ruler_succession (Lord to ruler), risen (Hrafnholt champion promoted from a Hird-Captain), heir (Mirewick family heir), vacant_forever (no heir left). */
	public enum SuccessionKind { RULER_SUCCESSION, RISEN, HEIR, VACANT_FOREVER }

	public interface Succession { void onSuccession(MinecraftServer server, Identifier faction, String seat, SuccessionKind kind); }
	/** Fired for every succession (also relayed to the war lane by reflection: KingdomApi.onSuccession(server, faction, seat, kindName) if it exists). */
	public static final Event<Succession> SUCCESSION = EventFactory.createArrayBacked(Succession.class, ls -> (s, f, seat, k) -> { for (Succession x : ls) x.onSuccession(s, f, seat, k); });
}
