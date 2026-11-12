package dev.elsewhere.bossart.ankhara;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.phys.Vec3;

/** Client copy of Ankhara's state plus the body segments laid along the head's trail. */
public class AnkharaRenderState extends LivingEntityRenderState {
	public final AnimationState erupt = new AnimationState();
	public final AnimationState windup = new AnimationState();
	public final AnimationState lunge = new AnimationState();
	public final AnimationState stun = new AnimationState();
	public float roarTime = -1;
	public boolean burrowed, stunned;
	/** Seconds since each pose started (-1 = not running), for the body segments. */
	public float eruptT = -1, windupT = -1, lungeT = -1, stunT = -1;
	public final List<Vec3> segments = new ArrayList<>();
	public final List<Float> segYaw = new ArrayList<>();
	public final List<Float> segPitch = new ArrayList<>();
}
