package dev.elsewhere.bossart.ossyra;

import dev.elsewhere.stub.nether_end_bosses.BossRenderState;

/** Ossyra's render state: the smoothed lid opening (0 closed .. 1 open) and whether she hangs from the ceiling. */
public class OssyraArtRenderState extends dev.elsewhere.bossart.nether.HurtAnim.State {
	public float openAmount;
	public boolean onCeiling;
}
