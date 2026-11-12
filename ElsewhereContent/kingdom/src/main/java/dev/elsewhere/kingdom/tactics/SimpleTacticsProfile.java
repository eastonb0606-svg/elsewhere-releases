package dev.elsewhere.kingdom.tactics;

import dev.elsewhere.kingdom.UnitType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** A {@link TacticsProfile} built from values: {@code SimpleTacticsProfile.builder(id).interval(10).minOdds(1.5)...build()}. */
public final class SimpleTacticsProfile implements TacticsProfile {
	final Identifier faction;
	int interval = 20, reactMin = 8, reactMax = 20;
	double minOdds = 1.2, retreatOdds = 0.55, blunder = 0.08;
	boolean pass;
	String mob = "default";
	SignalSet sig = SignalSet.DEFAULT;
	Perception.Filter filter = Perception.Filter.ALLIES_ONLY;
	Double noise;
	final List<Behaviour> behaviours = new ArrayList<>();
	final List<CheeseCounter> cheese = new ArrayList<>();
	final Map<UnitType.Role, RoleSpec> roles = new EnumMap<>(UnitType.Role.class);
	java.util.function.Consumer<SquadBrain> onCreate = b -> { };

	private SimpleTacticsProfile(Identifier f) {
		this.faction = f;
	}

	public static Builder builder(Identifier faction) {
		return new Builder(new SimpleTacticsProfile(faction));
	}

	@Override public Identifier faction() { return faction; }
	@Override public int decisionInterval() { return interval; }
	@Override public List<Behaviour> behaviours() { return behaviours; }
	@Override public Map<UnitType.Role, RoleSpec> roles() { return roles; }
	@Override public double minLocalOdds() { return minOdds; }
	@Override public double retreatOdds() { return retreatOdds; }
	@Override public int reactionTicksMin() { return reactMin; }
	@Override public int reactionTicksMax() { return reactMax; }
	@Override public double blunderRate() { return blunder; }
	@Override public SignalSet signals() { return sig; }
	@Override public Perception.Filter intelFilter() { return filter; }
	@Override public Double intelNoise() { return noise; }
	@Override public String mobPolicy() { return mob; }
	@Override public List<CheeseCounter> cheese() { return cheese; }
	@Override public boolean passThrough() { return pass; }
	@Override public void onSquadCreated(SquadBrain b) { onCreate.accept(b); }

	public static final class Builder {
		private final SimpleTacticsProfile p;

		Builder(SimpleTacticsProfile p) {
			this.p = p;
		}

		public Builder interval(int t) { p.interval = Math.clamp(t, 10, 20); return this; }
		public Builder minOdds(double v) { p.minOdds = v; return this; }
		public Builder retreatOdds(double v) { p.retreatOdds = v; return this; }
		public Builder reaction(int min, int max) { p.reactMin = min; p.reactMax = max; return this; }
		public Builder blunder(double v) { p.blunder = v; return this; }
		public Builder passThrough(boolean v) { p.pass = v; return this; }
		public Builder mobPolicy(String v) { p.mob = v; return this; }
		public Builder signals(SignalSet s) { p.sig = s; return this; }
		public Builder intel(Perception.Filter f) { p.filter = f; return this; }
		public Builder intelNoise(double v) { p.noise = v; return this; }
		public Builder behaviour(Behaviour b) { p.behaviours.add(b); return this; }
		public Builder behaviours(List<Behaviour> l) { p.behaviours.addAll(l); return this; }
		public Builder cheese(CheeseCounter c) { p.cheese.add(c); return this; }
		public Builder role(UnitType.Role r, RoleSpec s) { p.roles.put(r, s); return this; }
		public Builder onSquadCreated(java.util.function.Consumer<SquadBrain> c) { p.onCreate = c; return this; }

		public TacticsProfile build() {
			return p;
		}
	}
}
