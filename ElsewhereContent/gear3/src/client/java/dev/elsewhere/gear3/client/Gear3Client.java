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
	}
}
