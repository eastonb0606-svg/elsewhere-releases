package dev.elsewhere.kingdom.client.heraldry;

import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.client.KingdomSoldierRenderer;
import dev.elsewhere.kingdom.heraldry.Heraldry;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;

/**
 * 3.0 polish P10: the soldier cape, a quad strip hung from the body (so it follows crouching and attack twists like
 * vanilla's {@code PlayerCapeModel}). Every other part is emptied. The strip is a chain of {@code segments} flat
 * 10-pixel-wide panels, each hinged on the previous one's bottom edge, with only the two broad faces (outer = SOUTH,
 * facing backwards; lining = NORTH); draw it with a culling render type so each face shows from its own side.
 * <p>{@link #setupAnim} runs at flush time with the soldier's render state: the top panel takes the lean
 * ({@link Heraldry#rootDeg}), every hinge below it the travelling wave ({@link Heraldry#jointDeg}). Two baked copies:
 * {@link #WAVE} (8 panels, near) and {@link #FLAT} (one 16-pixel panel, far). Both read the same 32x16 cape texture.
 */
public final class SoldierCapeModel extends PlayerModel {
	public static final ModelLayerLocation WAVE = new ModelLayerLocation(KingdomContent.id("soldier_cape"), "wave");
	public static final ModelLayerLocation FLAT = new ModelLayerLocation(KingdomContent.id("soldier_cape"), "flat");
	private static final Set<Direction> FACES = EnumSet.of(Direction.NORTH, Direction.SOUTH);
	private static final float DEG = (float) (Math.PI / 180.0);

	private final ModelPart[] panels;

	public SoldierCapeModel(ModelPart root, int segments) {
		super(root, false);
		this.panels = new ModelPart[segments];
		ModelPart p = this.body;
		for (int i = 0; i < segments; i++) {
			p = p.getChild("cape" + i);
			this.panels[i] = p;
		}
	}

	/** The cape layer: {@code segments} panels (a divisor of {@link Heraldry#CAPE_HEIGHT}). */
	public static LayerDefinition create(int segments) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition root = mesh.getRoot().clearRecursively();
		PartDefinition parent = root.getChild("body");
		int h = Heraldry.CAPE_HEIGHT / segments;
		float half = Heraldry.CAPE_WIDTH / 2F;
		for (int i = 0; i < segments; i++) {
			PartPose pose = i == 0 ? PartPose.offset(0F, Heraldry.CAPE_Y, Heraldry.CAPE_Z) : PartPose.offset(0F, h, 0F);
			// zero depth: the NORTH face maps to u0-9 (lining), the SOUTH face to u10-19 (outer), rows i*h..(i+1)*h
			parent = parent.addOrReplaceChild("cape" + i, CubeListBuilder.create().texOffs(0, i * h).addBox(-half, 0F, 0F, Heraldry.CAPE_WIDTH, h, 0F, FACES), pose);
		}
		return LayerDefinition.create(mesh, Heraldry.CAPE_TEX_W, Heraldry.CAPE_TEX_H);
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		if (!(state instanceof KingdomSoldierRenderer.SoldierRenderState s)) return;
		float root = Heraldry.rootDeg(s.capeLean, s.capeFlap, s.isPassenger);
		int n = this.panels.length;
		this.panels[0].xRot = (n == 1 ? Heraldry.flatDeg(root, s.capeSpeed) : root) * DEG;
		this.panels[0].zRot = s.capeLean2 * 0.5F * DEG;
		float t = s.ageInTicks + s.capePhase;
		for (int j = 1; j < n; j++) this.panels[j].xRot = Heraldry.jointDeg(j, n, t, s.capeWalk, s.capeSpeed) * DEG;
	}
}
