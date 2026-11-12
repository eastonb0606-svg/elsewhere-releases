package dev.elsewhere.duelist.boss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

/**
 * 3.0 B4 self-test helper: proves in code that every move of a boss winds up and strikes with its own pose key. For each move it
 * runs the real wind-up path on a boss that is NOT in the level (no AI, no side effects: the move's fire is replaced by nothing) and
 * reads the synced byte twice: at the wind-up (key set, no strike bit) and right after the move fires (same key, strike bit).
 * Rival modules call {@link #probeType} from their own self-tests with the ids their bosses script outside {@link SignatureMove}.
 */
public final class PoseProbe {
	private PoseProbe() {}

	/** Base-kit moves every human boss may wind up (swing, shield bash, sprint crit, thrown weapon, pearl hop, last stand). */
	public static final List<String> BASE = List.of("swing", "bash", "crit", "throw", "pearl", "last_stand");

	/** One move of one boss. {@code key} is what the server picked; {@code windup}/{@code strike} are the synced bytes. */
	public record Result(String boss, String move, boolean signature, byte key, boolean explicit, byte windup, byte strike) {
		public boolean ok() {
			return key != MovePose.NONE && explicit && MovePose.key(windup) == key && !MovePose.struck(windup) && MovePose.key(strike) == key && MovePose.struck(strike);
		}

		@Override
		public String toString() {
			return boss + ":" + move + "=" + MovePose.name(key) + (explicit ? "" : "(guessed)") + (ok() ? "" : " windup=" + windup + " strike=" + strike);
		}
	}

	/** Creates the boss of {@code type} (not added to the level) and probes its signatures, the base kit and {@code scripted}. */
	public static List<Result> probeType(ServerLevel level, EntityType<? extends HumanBoss> type, Collection<String> scripted) {
		HumanBoss b = type.create(level, EntitySpawnReason.COMMAND);
		if (b == null) return List.of(new Result(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)), "<create>", false, MovePose.NONE, false, (byte) 0, (byte) 0));
		try {
			b.setPos(0.5, level.getMinY() + 1, 0.5);
			if (b.getMainHandItem().isEmpty() && b.profile.melee != null) b.setItemSlot(EquipmentSlot.MAINHAND, b.profile.melee.get());
			return probe(b, scripted);
		} finally {
			b.discard();
		}
	}

	/** Probes every signature move of {@code b}, then the base kit and {@code scripted} ids (each once). */
	public static List<Result> probe(HumanBoss b, Collection<String> scripted) {
		String boss = BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath();
		List<Result> out = new ArrayList<>();
		Set<String> done = new LinkedHashSet<>();
		for (SignatureMove m : b.profile.signatures) {
			if (!done.add(m.id())) continue;
			b.pendingPose = m.pose(b);
			out.add(run(b, boss, m.id(), m.label(), true, m.cue(b)));
		}
		Set<String> rest = new LinkedHashSet<>(BASE);
		rest.addAll(scripted);
		for (String id : rest) {
			if (!done.add(id)) continue;
			out.add(run(b, boss, id, id, false, b.style().cue(false, true)));
		}
		b.telegraph.cancel();
		b.syncAction();
		return out;
	}

	private static Result run(HumanBoss b, String boss, String id, String label, boolean sig, Telegraph.Cue cue) {
		int ticks = 14;
		b.telegraph.start(b, id, label, ticks, Telegraph.Weight.SIGNATURE, 0F, cue, true, () -> {});
		byte wind = b.poseByte();
		for (int i = 0; i < ticks + 1 && b.telegraph.active(); i++) {
			b.telegraph.tick(b);
			b.syncAction();
		}
		byte strike = b.poseByte();
		byte key = MovePose.key(wind);
		boolean explicit = MovePose.explicit(id) || (sig && key != MovePose.NONE && key == MovePose.key(strike) && !"".equals(id) && overridesPose(b, id));
		return new Result(boss, id, sig, key, explicit, wind, strike);
	}

	/** A signature move that overrides {@link SignatureMove#pose} names its key itself, which counts as explicit. */
	private static boolean overridesPose(HumanBoss b, String id) {
		for (SignatureMove m : b.profile.signatures) {
			if (!m.id().equals(id)) continue;
			try {
				return m.getClass().getMethod("pose", HumanBoss.class).getDeclaringClass() != SignatureMove.class;
			} catch (NoSuchMethodException e) {
				return false;
			}
		}
		return false;
	}

	/** Swing-key sanity for a weapon: the plain swing never comes out as a gesture key. */
	public static boolean swingIsWeaponKey(ItemStack weapon) {
		for (int n = 0; n < 3; n++) {
			byte k = MovePose.swing(weapon, n);
			if (k != MovePose.OVERHEAD && k != MovePose.SWEEP && k != MovePose.THRUST && k != MovePose.SLAM) return false;
		}
		return true;
	}
}
