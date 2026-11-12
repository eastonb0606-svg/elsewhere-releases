package dev.elsewhere.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player accidental-trip bookkeeping (saved with the player, kept through death):
 * playtime of the last trip, hours spent per 512x512 region (familiarity), hourly activity budgets and visited dimensions.
 */
public final class TripState {
	private record Data(long lastTrip, Map<String, Long> regions, Map<String, Long> budgets, List<String> visited) {
	}

	public static final Codec<TripState> CODEC = RecordCodecBuilder.<Data>create(i -> i.group(
		Codec.LONG.optionalFieldOf("last_trip_play_ticks", -1L).forGetter(Data::lastTrip),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("region_ticks", Map.of()).forGetter(Data::regions),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("budgets", Map.of()).forGetter(Data::budgets),
		Codec.STRING.listOf().optionalFieldOf("visited", List.of()).forGetter(Data::visited)
	).apply(i, Data::new)).xmap(TripState::fromData, TripState::toData);

	public static final int MAX_REGIONS = 256;

	/** Playtime (ticks) when the last accidental trip happened, or -1. */
	public long lastTripPlayTicks = -1L;
	/** "dimension|rx|rz" -> ticks spent there. */
	public final Map<String, Long> regionTicks = new HashMap<>();
	/** trigger id -> hourIndex * 1000 + events counted in that hour. */
	public final Map<String, Long> budgets = new HashMap<>();
	public final Set<String> visited = new HashSet<>();

	private static TripState fromData(final Data d) {
		TripState s = new TripState();
		s.lastTripPlayTicks = d.lastTrip();
		s.regionTicks.putAll(d.regions());
		s.budgets.putAll(d.budgets());
		s.visited.addAll(d.visited());
		return s;
	}

	private Data toData() {
		return new Data(this.lastTripPlayTicks, Map.copyOf(this.regionTicks), Map.copyOf(this.budgets), new ArrayList<>(this.visited));
	}

	public void addRegionTicks(final String region, final long ticks) {
		this.regionTicks.merge(region, ticks, Long::sum);
		if (this.regionTicks.size() > MAX_REGIONS) {
			// forget the least-visited region
			String least = null;
			long min = Long.MAX_VALUE;
			for (Map.Entry<String, Long> e : this.regionTicks.entrySet()) {
				if (e.getValue() < min && !e.getKey().equals(region)) {
					min = e.getValue();
					least = e.getKey();
				}
			}
			this.regionTicks.remove(least);
		}
	}

	public double hoursIn(final String region) {
		return this.regionTicks.getOrDefault(region, 0L) / 72000.0;
	}

	/** Counts an activity event; returns true if it is within the hourly budget and may roll. */
	public boolean spendBudget(final String trigger, final long playTicks, final int perHour) {
		long hour = playTicks / 72000L;
		long packed = this.budgets.getOrDefault(trigger, -1L);
		long storedHour = packed < 0 ? -1 : packed / 1000L;
		long count = storedHour == hour ? packed % 1000L : 0L;
		count++;
		this.budgets.put(trigger, hour * 1000L + Math.min(count, 999L));
		return count <= perHour;
	}
}
