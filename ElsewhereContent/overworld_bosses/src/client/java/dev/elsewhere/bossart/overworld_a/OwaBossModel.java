package dev.elsewhere.bossart.overworld_a;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Base model for the overworld_a bosses: idle (looping), walk, one keyframe animation per synced boss action id,
 * the phase-transition roar and a death animation driven by death time.
 */
public abstract class OwaBossModel<S extends OwaRenderState> extends EntityModel<S> {
	private final KeyframeAnimation idle, walk, transition, death;
	private final KeyframeAnimation[] actions;
	protected final ModelPart rootPart;

	protected OwaBossModel(ModelPart root, AnimationDefinition idle, AnimationDefinition walk, AnimationDefinition transition,
			AnimationDefinition death, AnimationDefinition[] actions) {
		super(root);
		this.rootPart = root.getChild("root");
		this.idle = idle.bake(root);
		this.walk = walk.bake(root);
		this.transition = transition.bake(root);
		this.death = death.bake(root);
		this.actions = new KeyframeAnimation[actions.length];
		for (int i = 0; i < actions.length; i++) this.actions[i] = actions[i] == null ? null : actions[i].bake(root);
	}

	protected float walkSpeed() {
		return 2.0F;
	}

	@Override
	public void setupAnim(S state) {
		super.setupAnim(state);
		if (state.deathTicks > 0.0F) {
			this.death.apply((long) (state.deathTicks * 50.0F), 1.0F);
			return;
		}
		this.idle.apply(state.idle, state.ageInTicks);
		if (state.action == 0) this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, walkSpeed(), 2.5F);
		for (int i = 0; i < this.actions.length && i < state.actions.length; i++) {
			if (this.actions[i] != null) this.actions[i].apply(state.actions[i], state.ageInTicks);
		}
		this.transition.apply(state.transition, state.ageInTicks);
		afterAnim(state);
	}

	/** Head look, visibility toggles etc. */
	protected void afterAnim(S state) {}
}
