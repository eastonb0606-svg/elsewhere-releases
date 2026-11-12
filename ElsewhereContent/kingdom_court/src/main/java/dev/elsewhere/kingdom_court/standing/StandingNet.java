package dev.elsewhere.kingdom_court.standing;

import dev.elsewhere.kingdom_court.CourtContent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * POLISH P7 (s-p7-standing): the three small payloads behind the trader dialogue screen and the Standing chip. Each is below 64 bytes
 * (POLISH budget rule 9; the self-test encodes the worst case) and is sent on an event only, never per tick:
 * <ul>
 * <li>{@link TraderOpen} (server to client): one per right-click on a trader. It is the trade data the screen shows: Standing, the price
 * multiplier this player pays at this trader, the modifier flags and one per-culture number.</li>
 * <li>{@link Query} (client to server): the client's crosshair landed on a ruler whose kingdom it has no fresh Standing for.</li>
 * <li>{@link Reply} (server to client): the one Standing int for that ruler's kingdom, plus three flags.</li>
 * </ul>
 * The types are registered on both sides whatever the kill switches say (so a client and server with different switches still agree on the
 * protocol); the switches only decide whether anything is sent or answered.
 */
public final class StandingNet {
	private StandingNet() {}

	/** POLISH rule 9: every new payload stays below this many bytes. */
	public static final int MAX_BYTES = 64;
	/** Longest faction id sent (ids are short, e.g. "elsewhere:aldermark_remnant"; a longer one is cut, which only costs its style). */
	public static final int MAX_FACTION = 40;

	// ---- TraderOpen flags ----------------------------------------------------------------------------------------------------
	/** The faction refuses to trade with this player (Hostile band). */
	public static final int HOSTILE = 1;
	/** Hrafnholt: an Arm-Ring in the pack takes 10 percent off. */
	public static final int RING = 1 << 1;
	/** Mirewick: haggled with this trader today, 20 percent off. */
	public static final int HAGGLED = 1 << 2;
	/** Mirewick: a failed haggle shut this trader to the player until tomorrow. */
	public static final int LOCKED = 1 << 3;
	/** Mirewick: the republic is at war, +20 percent on every line. */
	public static final int WAR = 1 << 4;
	/** Hrafnholt: the Thing is open now. */
	public static final int THING_OPEN = 1 << 5;
	/** Oathbreaker: this Standing cannot rise for now. */
	public static final int OATH = 1 << 6;
	/** Azmar: the player holds this week's pact. */
	public static final int PACT = 1 << 7;
	/** Azmar: the Envoy's tribute offer is open to this player. */
	public static final int OFFER = 1 << 8;
	/** Mirewick: the player owes the Counting-House a loan. */
	public static final int OWES = 1 << 9;
	/** Mirewick: that loan is overdue. */
	public static final int LATE = 1 << 10;
	/** Mirewick: this trader serves a miser Magnate (haggling 15 points harder). */
	public static final int MISER = 1 << 11;

	// ---- Reply flags -------------------------------------------------------------------------------------------------------------
	/** The ruler's soldiers attack this player on sight right now. */
	public static final int R_ATTACKS = 1;
	/** Oathbreaker lock (as {@link #OATH}). */
	public static final int R_OATH = 1 << 1;
	/** The kingdom's Standing is capped below Friendly until its ruler has been killed once (Aldermark's rule). */
	public static final int R_CAPPED = 1 << 2;

	/**
	 * Server to client: open the trader dialogue. {@code entity} is the trader's entity id, {@code faction} its faction id, {@code role} the
	 * {@link dev.elsewhere.kingdom_court.api.Diplomacy.Role} ordinal, {@code mode} the faction's {@link dev.elsewhere.kingdom_court.trade.TradeData.Mode}
	 * ordinal, {@code standing} -100..100, {@code pricePct} what this player pays at this
	 * trader in percent of the list price (Standing discount times every trader-wide modifier; Mirewick's per-line market offsets are on the
	 * chat menu lines), {@code flags} the bits above and {@code detail} one per-culture number: days until the Thing (Hrafnholt), stock tiers
	 * open 0-3 (Azmar), haggle chance in percent (Mirewick), else 0.
	 */
	public record TraderOpen(int entity, String faction, byte role, byte mode, int standing, short pricePct, short flags, byte detail) implements CustomPacketPayload {
		public static final Type<TraderOpen> TYPE = new Type<>(CourtContent.id("trader_dialogue"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TraderOpen> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TraderOpen::entity,
			ByteBufCodecs.stringUtf8(MAX_FACTION), TraderOpen::faction,
			ByteBufCodecs.BYTE, TraderOpen::role,
			ByteBufCodecs.BYTE, TraderOpen::mode,
			ByteBufCodecs.VAR_INT, TraderOpen::standing,
			ByteBufCodecs.SHORT, TraderOpen::pricePct,
			ByteBufCodecs.SHORT, TraderOpen::flags,
			ByteBufCodecs.BYTE, TraderOpen::detail,
			TraderOpen::new);

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<TraderOpen> type() {
			return TYPE;
		}
	}

	/** Client to server: "what is my Standing with the kingdom of the ruler with this entity id?". */
	public record Query(int entity) implements CustomPacketPayload {
		public static final Type<Query> TYPE = new Type<>(CourtContent.id("standing_query"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Query> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Query::entity, Query::new);

		@Override
		public Type<Query> type() {
			return TYPE;
		}
	}

	/** Server to client: the player's Standing with {@code faction} (the ruler's kingdom), and {@code R_*} flags. */
	public record Reply(int entity, String faction, int standing, byte flags) implements CustomPacketPayload {
		public static final Type<Reply> TYPE = new Type<>(CourtContent.id("standing_reply"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Reply::entity,
			ByteBufCodecs.stringUtf8(MAX_FACTION), Reply::faction,
			ByteBufCodecs.VAR_INT, Reply::standing,
			ByteBufCodecs.BYTE, Reply::flags,
			Reply::new);

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<Reply> type() {
			return TYPE;
		}
	}

	private static boolean done;

	/** Registers the three payload types and the query handler. Called once from {@code CourtContent.init()} (both sides). */
	public static void init() {
		if (done) return;
		done = true;
		PayloadTypeRegistry.clientboundPlay().register(TraderOpen.TYPE, TraderOpen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Reply.TYPE, Reply.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Query.TYPE, Query.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Query.TYPE, (payload, ctx) -> StandingQuery.handle(ctx.player(), payload.entity()));
		StandingCommand.register();
	}

	/** Cuts a faction id to what the codecs accept. */
	static String clip(String faction) {
		return faction.length() <= MAX_FACTION ? faction : faction.substring(0, MAX_FACTION);
	}
}
