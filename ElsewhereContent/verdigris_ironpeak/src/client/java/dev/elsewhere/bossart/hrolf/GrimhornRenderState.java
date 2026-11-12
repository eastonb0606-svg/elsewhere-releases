package dev.elsewhere.bossart.hrolf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

@Environment(EnvType.CLIENT)
public class GrimhornRenderState extends LivingEntityRenderState {
	/** GrimhornEntity.IDLE / WINDUP / CHARGE / STUNNED. */
	public int goatState;
}
