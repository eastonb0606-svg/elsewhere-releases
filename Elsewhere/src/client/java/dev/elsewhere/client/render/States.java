package dev.elsewhere.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render states for every Elsewhere entity (filled by the renderers' extractRenderState). */
public final class States {
	private States() {
	}

	public static class Husk extends LivingEntityRenderState {
		public boolean frozen;
	}

	public static class Wisp extends LivingEntityRenderState {
		public float shriek;
	}

	public static class Mother extends LivingEntityRenderState {
		public boolean awake;
		public boolean watched;
		public float stun;
		public float claw;
		public float slam;
		public int hearts;
		public int phase;
	}

	public static class Selenne extends LivingEntityRenderState {
		public boolean awake;
		public float open;
		public boolean torn;
		public float beam;
		public float fullMoon;
	}

	public static class Spike extends EntityRenderState {
		public float height;
		public float yRot;
	}

	public static class Rift extends EntityRenderState {
		public int color;
		public float age;
		public int state;
		public float yRot;
	}
}
