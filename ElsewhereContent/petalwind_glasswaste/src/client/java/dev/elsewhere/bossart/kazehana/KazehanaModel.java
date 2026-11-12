package dev.elsewhere.bossart.kazehana;

import dev.elsewhere.bossart.petalwind.ArtAnim;
import dev.elsewhere.boss.kazehana.Kazehana;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Kazehana, the Gale Ronin: grey-faced swordsman in a pink kimono and long haori, dark hakama, a wide straw kasa and a
 * glowing white katana (concept art boxes from bosses_art.py kazehana()). Bones: waist > body > head (hat), arms (katana),
 * saya, haori; legs on the root.
 */
public class KazehanaModel extends EntityModel<KazehanaRenderState> {
	private final ModelPart head;
	private final ModelPart haori;
	private final KeyframeAnimation idle, walk, bow, iaido, dash, combo, parry, stun, phase, gale, death;

	public KazehanaModel(ModelPart root, boolean translucent) {
		super(root, translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
		ModelPart body = root.getChild("waist").getChild("body");
		this.head = body.getChild("head");
		this.haori = body.getChild("haori");
		this.idle = KazehanaAnimations.IDLE.bake(root);
		this.walk = KazehanaAnimations.WALK.bake(root);
		this.bow = KazehanaAnimations.BOW.bake(root);
		this.iaido = KazehanaAnimations.IAIDO.bake(root);
		this.dash = KazehanaAnimations.DASH.bake(root);
		this.combo = KazehanaAnimations.COMBO.bake(root);
		this.parry = KazehanaAnimations.PARRY.bake(root);
		this.stun = KazehanaAnimations.STUN.bake(root);
		this.phase = KazehanaAnimations.PHASE.bake(root);
		this.gale = KazehanaAnimations.GALE.bake(root);
		this.death = KazehanaAnimations.DEATH.bake(root);
	}

	@Override
	public void setupAnim(KazehanaRenderState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		if (s.deathTime > 0) {
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		if (s.dormant || s.duelState == Kazehana.S_BOW) {
			this.bow.apply((long) (age * 50.0F), 1.0F);
			return;
		}
		this.idle.apply((long) (age * 50.0F), 1.0F);
		this.walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 2.0F, 2.5F);
		// the haori trails behind when he moves
		this.haori.xRot += Math.min(1.0F, s.walkAnimationSpeed) * 0.35F;
		if (s.duelState == Kazehana.S_IAIDO) ArtAnim.hold(this.iaido, s.iaido, age);
		else {
			ArtAnim.play(this.dash, s.dash, age, 0.6F);
			ArtAnim.play(this.combo, s.combo, age, 0.5F);
			ArtAnim.play(this.parry, s.parry, age, 1.0F);
			ArtAnim.play(this.stun, s.stun, age, 3.0F);
			ArtAnim.play(this.gale, s.gale, age, 0.75F);
		}
		ArtAnim.play(this.phase, s.phase, age, 2.0F);
		if (s.duelState != Kazehana.S_STUNNED && s.duelState != Kazehana.S_IAIDO && s.duelState != Kazehana.S_DASH) {
			this.head.yRot += Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
			this.head.xRot += Mth.clamp(s.xRot, -40.0F, 40.0F) * Mth.DEG_TO_RAD;
		}
	}
}
