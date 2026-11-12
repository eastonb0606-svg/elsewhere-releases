package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.boss.MovePose;
import dev.elsewhere.duelist.boss.PoseProbe;
import dev.elsewhere.duelist.boss.PoseTable;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TestDuelist;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 3.0 P4/B4 (s-p4-rivalanims): the one-byte pose sync and the per-move keyframes. Every key has its own wind-up and strike and
 * no two keys look alike; the four kingdom styles differ; the plain swing follows the weapon; the byte is set at wind-up start,
 * gets the strike bit when the move fires, clears after the strike window, clears at once on a cancel, and changes for
 * back-to-back wind-ups of the same move; every move this self-test wound up has its own table entry.
 */
final class SelfTestPose {
	private SelfTestPose() {}

	/** Ids that only this module's test code winds up (not boss moves). */
	private static final Set<String> TEST_ONLY = Set.of("t", "big");

	static void add(H h) {
		h.test("3.0 B4 pose keys and kingdom styles").check("every key has its own wind-up and strike pose (" + (MovePose.NAMES.length - 1) + " keys)", () -> {
			for (int k = 1; k < MovePose.NAMES.length; k++) {
				float[][] p = PoseTable.POSES[k];
				if (p == null || p.length != 2 || p[0].length != PoseTable.N || p[1].length != PoseTable.N || PoseTable.distance(p[0], p[1]) < 1.0F) {
					h.info("[duelist-selftest] INFO pose key " + MovePose.name((byte) k) + " is missing or has no motion");
					return false;
				}
			}
			return true;
		}).check("no two keys look alike (wind-up plus strike differ by at least 1.0 rad summed)", () -> {
			for (int a = 1; a < MovePose.NAMES.length; a++) for (int b = a + 1; b < MovePose.NAMES.length; b++) {
				float d = PoseTable.distance(PoseTable.POSES[a][0], PoseTable.POSES[b][0]) + PoseTable.distance(PoseTable.POSES[a][1], PoseTable.POSES[b][1]);
				if (d < 1.0F) {
					h.info("[duelist-selftest] INFO keys " + MovePose.name((byte) a) + " and " + MovePose.name((byte) b) + " too close: " + d);
					return false;
				}
			}
			return true;
		}).check("four kingdom styles from the type path (aldermark, hrafnholt, azmar, mirewick), each with its own timing or reach", () -> {
			if (PoseTable.kindOf("hrafnholt_jarl") != PoseTable.HRAFNHOLT || PoseTable.kindOf("hrafnholt_risen_hersir") != PoseTable.HRAFNHOLT
					|| PoseTable.kindOf("azmar_sultana") != PoseTable.AZMAR || PoseTable.kindOf("mirewick_ames") != PoseTable.MIREWICK
					|| PoseTable.kindOf("test_duelist") != PoseTable.ALDERMARK || PoseTable.STYLES.length != 4) return false;
			for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) {
				PoseTable.Style x = PoseTable.STYLES[a], y = PoseTable.STYLES[b];
				if (x.amp() == y.amp() && x.snap() == y.snap() && x.recover() == y.recover() && x.lean() == y.lean() && x.twist() == y.twist()) return false;
			}
			return true;
		}).check("the plain swing follows the weapon: axe chops, spear stabs, mace slams, sword cycles three", () -> {
			ItemStack axe = new ItemStack(Items.IRON_AXE), spear = new ItemStack(Items.DIAMOND_SPEAR), mace = new ItemStack(Items.MACE), sword = new ItemStack(Items.DIAMOND_SWORD);
			boolean ok = MovePose.swing(axe, 0) == MovePose.OVERHEAD && MovePose.swing(axe, 2) == MovePose.SWEEP
					&& MovePose.swing(spear, 0) == MovePose.THRUST && MovePose.swing(spear, 1) == MovePose.THRUST
					&& MovePose.swing(mace, 1) == MovePose.SLAM
					&& MovePose.swing(sword, 0) == MovePose.OVERHEAD && MovePose.swing(sword, 1) == MovePose.SWEEP && MovePose.swing(sword, 2) == MovePose.THRUST;
			return ok && PoseProbe.swingIsWeaponKey(axe) && PoseProbe.swingIsWeaponKey(spear) && PoseProbe.swingIsWeaponKey(mace) && PoseProbe.swingIsWeaponKey(ItemStack.EMPTY);
		}).check("sync rule: wind-up byte held, strike bit kept " + MovePose.STRIKE_TICKS + " ticks after the move fires, then 0; a cancel is 0 at once", () -> {
			byte w = MovePose.pack(MovePose.SLAM, true), s = (byte) (w | MovePose.STRIKE);
			return MovePose.synced(true, w, -999) == w && MovePose.synced(false, s, 0) == s && MovePose.synced(false, s, MovePose.STRIKE_TICKS) == s
					&& MovePose.synced(false, s, MovePose.STRIKE_TICKS + 1) == 0 && MovePose.synced(false, (byte) 0, 1) == 0 && MovePose.synced(false, w, 1) == 0
					&& MovePose.key(s) == MovePose.SLAM && MovePose.struck(s) && !MovePose.struck(w) && MovePose.pack(MovePose.SLAM, false) != w;
		});
		final TestDuelist[] b = new TestDuelist[1];
		final byte[] seen = new byte[3];
		h.test("3.0 B4 pose byte on a live boss (wind-up, strike, back-to-back, signature probe)").run(() -> {
			b[0] = h.boss(H.base("pose").build(), 0, 0);
			b[0].passive = true;
			b[0].startTelegraph("oath_cleave", "Oath Cleave", 10, Telegraph.Weight.ORDINARY, 0F, b[0].style().cue(false, true), true, () -> {});
			seen[0] = b[0].poseByte();
		}).wait(12).check("wind-up: the byte carries the overhead key, no strike bit", () -> MovePose.key(seen[0]) == MovePose.OVERHEAD && !MovePose.struck(seen[0]))
			.check("strike: same key with the strike bit once the move fired", () -> MovePose.key(b[0].poseByte()) == MovePose.OVERHEAD && MovePose.struck(b[0].poseByte()))
			.run(() -> {
				b[0].startTelegraph("oath_cleave", "Oath Cleave", 10, Telegraph.Weight.ORDINARY, 0F, b[0].style().cue(false, true), true, () -> {});
				seen[1] = b[0].poseByte();
				b[0].startTelegraph("oath_cleave", "Oath Cleave", 10, Telegraph.Weight.ORDINARY, 0F, b[0].style().cue(false, true), true, () -> {});
				seen[2] = b[0].poseByte();
			}).check("back-to-back wind-ups of the same move still change the synced byte", () -> seen[1] != seen[2] && MovePose.key(seen[1]) == MovePose.key(seen[2]))
			.check("the duelist test boss: every signature and base-kit move winds up and strikes with its own key", () -> {
				TestDuelist probe = DuelistContent.TEST_DUELIST.create(h.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				if (probe == null) return false;
				probe.setPos(0.5, H.Y + 1, 40.5);
				List<PoseProbe.Result> r = PoseProbe.probe(probe, List.of("test_slam", "look"));
				probe.discard();
				StringBuilder sb = new StringBuilder();
				boolean ok = !r.isEmpty();
				for (PoseProbe.Result x : r) {
					sb.append(x).append(' ');
					ok &= x.ok();
				}
				h.info("[duelist-selftest] INFO poses " + sb.toString().trim());
				return ok;
			});
		h.test("3.0 B4 every move wound up in this self-test has its own pose key").run(() -> {}).check("audit of every wind-up so far", () -> {
			if (Telegraph.audit == null) return true;
			Set<String> ids = new LinkedHashSet<>();
			for (Object[] a : Telegraph.audit) ids.add(String.valueOf(a[0]));
			boolean ok = true;
			for (String id : ids) {
				if (TEST_ONLY.contains(id) || MovePose.explicit(id)) continue;
				h.info("[duelist-selftest] INFO move without its own pose key: " + id);
				ok = false;
			}
			h.info("[duelist-selftest] INFO pose audit: " + ids.size() + " distinct move ids wound up");
			return ok;
		});
	}
}
