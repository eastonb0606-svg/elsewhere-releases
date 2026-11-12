package dev.elsewhere.gear3.client;

/**
 * gear3 client entry (renderers, client-only visuals). The merge (ElsewhereBeta/tools/merge.py) calls the FIRST gear3
 * {@code *Client.java} that declares {@code public static void initClient()}, so this is the only one: every gear3 item
 * with client code adds ONE line below that calls its own client class (e.g. {@code SunglassRenderers.init()}).
 * Shared file: claim it in CLAIMS-3.0.md, add your line, release.
 */
public final class Gear3Client {
	private Gear3Client() {}

	private static boolean done;

	public static void initClient() {
		if (done) return;
		done = true;
		// one line per gear3 item with client code (3.0 swarm):
		dev.elsewhere.gear3.s3.echoarrow.client.EchoArrowRenderers.register(); // s-echoarrow
		dev.elsewhere.gear3.s3.galesling.GaleSlingRenderers.init(); // s-galesling: slung stone = vanilla ThrownItemRenderer
		dev.elsewhere.gear3.s3.wolfcall.WolfcallRenderers.init(); // s-wolfcall: Warg = vanilla adult wolf model, own skin, x1.3
		dev.elsewhere.gear3.setbonus.client.SetBonusTooltips.init(); // s-setbonus-a: set/pair bonus tooltip lines (all 13 bonuses)
		dev.elsewhere.gear3.s3.harpoon.client.HarpoonRenderers.init(); // s-harpoon: Harpoon Bolt = vanilla arrow model, own texture
		dev.elsewhere.gear3.s3.hurlbat.client.HurlbatRenderer.register(); // s-hurlbat: thrown hurlbat = its own item model, tumbling / stuck
		dev.elsewhere.gear3.s3.pennant.client.PennantClient.init(); // s-pennant: Herdcaller's Standard model layer + renderer
		dev.elsewhere.gear3.s3.sunglass.SunglassRenderers.init(); // s-sunglass: Sunglass Arrow = vanilla arrow model, own texture
		dev.elsewhere.gear3.s3.starpiercer.client.StarpiercerVisuals.init(); // s-starpiercer: Starpiercer Arrow renderer (full bright) + longbow zoom
		dev.elsewhere.gear3.s3.reliquary.client.EchoKnightClient.register(); // s-reliquary: Echo Knight model layer + renderer (glow layer)
		dev.elsewhere.gear3.s3.mothlight.client.MothlightClient.init(); // s-mothlight: Mothlight Swarm model layer + renderer (full bright)
		dev.elsewhere.gear3.s3.willo.client.WillOLanternClient.init(); // s-willolantern: Marsh Wisp model layer + full-bright renderer
		dev.elsewhere.gear3.s3.totems.client.TotemRenderers.init(); // s-totems: 3 totem model layers + renderer (sentry bolt streak, obelisk sunbeam)
	}
}
