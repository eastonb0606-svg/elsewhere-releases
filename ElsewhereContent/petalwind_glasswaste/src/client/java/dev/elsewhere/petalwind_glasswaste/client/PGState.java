package dev.elsewhere.petalwind_glasswaste.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

/** Render state shared by every model in this module. */
public class PGState extends LivingEntityRenderState {
	/** Entity-specific pose state (Kazehana's duel state, Ankhara's mode, ...). */
	public int pose;
	public boolean flag;
	public float attack;
	public boolean translucent;
	/** Segment positions relative to the entity (serpents). */
	public final List<Vec3> segments = new ArrayList<>();
	public final List<Float> segmentYaw = new ArrayList<>();
}
