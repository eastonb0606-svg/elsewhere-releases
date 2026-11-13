package dev.elsewhere.gear3.s3.censer.client;

import dev.elsewhere.gear3.s3.censer.ChoirlightCenser;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** s-censer client: tells the censer's fx whether the using player is the local player in the first-person camera. */
public final class CenserClient {
	private CenserClient() {}

	public static void init() {
		ChoirlightCenser.FIRST_PERSON = e -> {
			Minecraft mc = Minecraft.getInstance();
			return e == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
		};
	}
}
