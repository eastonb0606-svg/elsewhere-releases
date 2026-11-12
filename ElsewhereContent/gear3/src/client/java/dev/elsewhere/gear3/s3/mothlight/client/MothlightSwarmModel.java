package dev.elsewhere.gear3.s3.mothlight.client;

import dev.elsewhere.gear3.Gear3Content;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Mothlight Swarm model (64x32, cutout): three pale moths, each a striped 2x2x4 body, a little head, two feathery antennae and
 * two flat 8x6 wings with an amber eyespot. They circle the swarm's centre like moths round a lamp, fluttering fast; on a
 * flurry each moth in turn darts forward and back; moths beyond the living count are hidden, and the one that just fell
 * drops with folded wings for half a second.
 */
public class MothlightSwarmModel extends EntityModel<MothlightSwarmRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Gear3Content.id("mothlight_swarm"), "main");
	private static final float TAU = (float) (Math.PI * 2);
	/** Model y of the swarm's centre (24 = feet; at scale 0.45 this is about 0.3 blocks up). */
	private static final float CENTRE_Y = 13F;
	private static final float LUNGE_TICKS = 7F, LUNGE_REACH = 14F, FALL_TICKS = 10F;

	private final ModelPart[] moths = new ModelPart[3];
	private final ModelPart[] rightWings = new ModelPart[3], leftWings = new ModelPart[3];

	public MothlightSwarmModel(ModelPart root) {
		super(root);
		for (int i = 0; i < 3; i++) {
			moths[i] = root.getChild("moth" + i);
			rightWings[i] = moths[i].getChild("right_wing");
			leftWings[i] = moths[i].getChild("left_wing");
		}
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int i = 0; i < 3; i++) {
			PartDefinition m = root.addOrReplaceChild("moth" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -1F, -1F, 2F, 2F, 4F),
				PartPose.offset(0F, CENTRE_Y, 0F));
			m.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5F, -0.5F, -1F, 1F, 1F, 1F), PartPose.offset(0F, -0.3F, -1F));
			m.addOrReplaceChild("antennae", CubeListBuilder.create().texOffs(0, 12).addBox(-1.5F, 0F, -2F, 3F, 0F, 2F),
				PartPose.offsetAndRotation(0F, -0.8F, -2F, 0.5F, 0F, 0F));
			// both wings share one symmetric UV patch (top and bottom faces of a flat box)
			m.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(16, 0).addBox(-8F, 0F, -3F, 8F, 0F, 6F), PartPose.offset(-1F, -1F, 0.5F));
			m.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(16, 0).addBox(0F, 0F, -3F, 8F, 0F, 6F), PartPose.offset(1F, -1F, 0.5F));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(MothlightSwarmRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks + (s.seed & 63) * 3.1F;
		for (int i = 0; i < 3; i++) {
			ModelPart m = moths[i];
			boolean living = i < s.moths;
			boolean falling = !living && i == s.moths && s.sinceFall >= 0F && s.sinceFall < FALL_TICKS;
			m.visible = living || falling;
			if (!m.visible) continue;
			boolean reversed = i == 1;
			float r = 9F + 2F * i;
			float a = (reversed ? -t : t) * (0.13F + 0.02F * i) + i * TAU / 3F;
			float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
			float y = CENTRE_Y + Mth.sin(t * 0.21F + i * 2.1F) * 2.2F + Mth.sin(t * 0.53F + i) * 0.6F;
			float yaw = reversed ? -a : (float) Math.PI - a;
			float flap = 0.15F + Mth.sin(t * 1.7F + i * 1.3F) * 0.85F;
			if (falling) {
				float f = s.sinceFall / FALL_TICKS;
				y += f * f * 12F;
				flap = 1.25F; // wings folded up
				m.xRot = f * 1.2F;
			} else {
				m.xRot = 0.1F * Mth.sin(t * 0.3F + i);
				// flurry: each moth darts forward (model -z, toward what the swarm faces) and back, one after another
				float p = (s.sinceFlurry - i * 1.5F) / LUNGE_TICKS;
				if (p > 0F && p < 1F) {
					float lunge = Mth.sin(p * (float) Math.PI);
					x *= 1F - 0.6F * lunge;
					z = z * (1F - 0.6F * lunge) - LUNGE_REACH * lunge;
					y = Mth.lerp(lunge, y, CENTRE_Y);
					yaw += (float) Math.IEEEremainder(-yaw, TAU) * lunge;
					flap = 0.6F + Mth.sin(t * 3.1F) * 0.5F; // a quick buzz while striking
				}
			}
			m.x = x;
			m.y = y;
			m.z = z;
			m.yRot = yaw;
			m.zRot = 0F;
			rightWings[i].zRot = -flap;
			leftWings[i].zRot = flap;
		}
	}
}
