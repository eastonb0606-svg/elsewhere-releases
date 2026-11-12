package dev.elsewhere.bossart.tempestra;

import dev.elsewhere.bossart.petalwind.ArtAnim;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Tempestra, Eye of the Gale: a pale-blue storm-cloud cube head with white eyes, three spinning rings of glowing wind rods,
 * an orbit of pink petal-stones and a tapering white wind tail over a translucent funnel (bosses_art.py tempestra()).
 * The rings pull in tight while she is shielded and swing wide when they open.
 */
public class TempestraModel extends EntityModel<TempestraRenderState> {
	private final ModelPart body, head, debris;
	private final ModelPart[] rings = new ModelPart[3];
	private final ModelPart[] tails = new ModelPart[4];
	private final List<ModelPart> rods = new ArrayList<>();
	private final KeyframeAnimation idle, volley, downburst, phase, death, bolt, strike;

	public TempestraModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.debris = body.getChild("debris");
		for (int k = 0; k < 3; k++) {
			rings[k] = body.getChild("ring" + (k + 1));
			for (int i = 1; i <= 5; i++) rods.add(rings[k].getChild("rod" + (k + 1) + "_" + i));
		}
		ModelPart t = body.getChild("tail1");
		for (int i = 0; i < 4; i++) {
			tails[i] = t;
			if (i < 3) t = t.getChild("tail" + (i + 2));
		}
		this.idle = TempestraAnimations.IDLE.bake(root);
		this.volley = TempestraAnimations.VOLLEY.bake(root);
		this.downburst = TempestraAnimations.DOWNBURST.bake(root);
		this.phase = TempestraAnimations.PHASE.bake(root);
		this.death = TempestraAnimations.DEATH.bake(root);
		this.bolt = TempestraAnimations.BOLT.bake(root);
		this.strike = TempestraAnimations.STRIKE.bake(root);
	}

	@Override
	public void setupAnim(TempestraRenderState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		this.idle.apply((long) (age * 50.0F), 1.0F);
		// shield: rods drawn in close (a cage) or swung wide (open)
		float spread = 0.72F + 0.6F * s.ringsOpen;
		for (ModelPart rod : rods) {
			rod.x *= spread;
			rod.z *= spread;
			rod.zRot += 0.35F * s.ringsOpen;
		}
		float spin = 1.0F + 0.8F * (1.0F - s.ringsOpen) + 0.4F * (s.phase - 1);
		rings[0].yRot += age * 0.07F * spin;
		rings[1].yRot -= age * 0.055F * spin;
		rings[2].yRot += age * 0.09F * spin;
		debris.yRot -= age * 0.025F;
		for (int i = 0; i < 4; i++) tails[i].yRot += age * (0.12F + 0.05F * i);
		// flight: lean into the direction of travel, tail trailing
		float speed = Math.min(1.0F, s.walkAnimationSpeed * 2.0F);
		body.xRot += speed * 0.3F;
		tails[0].xRot -= speed * 0.4F;
		head.yRot += Mth.clamp(s.yRot, -45.0F, 45.0F) * Mth.DEG_TO_RAD;
		head.xRot += Mth.clamp(s.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD;
		if (s.deathTime > 0) {
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		ArtAnim.play(this.volley, s.volley, age, 1.0F);
		ArtAnim.play(this.downburst, s.downburst, age, 2.0F);
		ArtAnim.play(this.phase, s.phaseRoar, age, 2.0F);
		ArtAnim.play(this.bolt, s.bolt, age, 0.8F);
		ArtAnim.play(this.strike, s.strike, age, 1.8F);
	}
}
