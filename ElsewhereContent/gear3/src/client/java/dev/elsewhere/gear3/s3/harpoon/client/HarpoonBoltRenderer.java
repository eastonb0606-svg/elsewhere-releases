package dev.elsewhere.gear3.s3.harpoon.client;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.harpoon.HarpoonBolt;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/**
 * The flying / stuck Harpoon Bolt: vanilla's arrow model (two crossed planes plus the fletching cross, same size, shake and
 * culling as an arrow) with its own 32x32 sheet: barbed copper Tidebrass head, verdigris collar, dark shaft, teal fletching
 * (tools/harpoon_bolt_entity.py, drawn from the item sprite's palette).
 */
public class HarpoonBoltRenderer extends ArrowRenderer<HarpoonBolt, ArrowRenderState> {
	public static final Identifier TEXTURE = Gear3Content.id("textures/entity/projectiles/tidebrass_harpoon_bolt.png");

	public HarpoonBoltRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	protected Identifier getTextureLocation(ArrowRenderState state) {
		return TEXTURE;
	}

	@Override
	public ArrowRenderState createRenderState() {
		return new ArrowRenderState();
	}
}
