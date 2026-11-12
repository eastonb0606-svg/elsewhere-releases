package dev.elsewhere.kingdom.client;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

/**
 * Vanilla player model (wide arms) with the player armor layers, held items (shield, bow, crossbow, spear poses like a
 * player's) and a {@link TabardLayer} tinted with the faction colour. Texture = the unit type's {@code skin}.
 */
public class KingdomSoldierRenderer extends HumanoidMobRenderer<KingdomSoldier, AvatarRenderState, PlayerModel> {
	public KingdomSoldierRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), part -> new PlayerModel(part, false)), ctx.getEquipmentRenderer()));
		// the tabard is added to the vanilla avatar renderer by AvatarLayers (26.3 never calls this renderer's own layers)
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new SoldierRenderState();
	}

	@Override
	public void extractRenderState(KingdomSoldier e, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		NpcSkin.apply(state, e.skin()); // the vanilla AvatarRenderer draws state.skin; see NpcSkin
		double d2 = state.distanceToCameraSq;
		if (state instanceof SoldierRenderState s) {
			s.texture = e.skin();
			s.tabardColor = 0xFF000000 | e.tabardColor();
			double t = KingdomClientConfig.tabardDist();
			s.showTabard = d2 < t * t;
			SoldierNameplates.apply(e, s);
		}
		// client LOD ("soldier detail"): drop the costly extras with distance
		double od = KingdomClientConfig.overlayDist(), id = KingdomClientConfig.itemsDist(), ad = KingdomClientConfig.armorDist();
		boolean overlay = d2 < od * od;
		state.showHat = state.showJacket = state.showLeftPants = state.showRightPants = state.showLeftSleeve = state.showRightSleeve = overlay;
		if (d2 >= id * id) {
			state.rightHandItemState.clear();
			state.leftHandItemState.clear();
		}
		if (d2 >= ad * ad) {
			state.headEquipment = state.chestEquipment = state.legsEquipment = state.feetEquipment = ItemStack.EMPTY;
		}
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state instanceof SoldierRenderState s && s.texture != null ? s.texture : dev.elsewhere.kingdom.UnitType.DEFAULT_SKIN;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(KingdomSoldier mob, HumanoidArm arm) {
		HumanoidModel.ArmPose main = pose(mob, mob.getMainHandItem(), InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose off = pose(mob, mob.getOffhandItem(), InteractionHand.OFF_HAND);
		if (main.isTwoHanded()) off = mob.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		return mob.getMainArm() == arm ? main : off;
	}

	private static HumanoidModel.ArmPose pose(KingdomSoldier mob, ItemStack item, InteractionHand hand) {
		if (item.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
		if (item.is(Items.CROSSBOW) && (mob.isChargingCrossbow() || CrossbowItem.isCharged(item)) && !(mob.isUsingItem() && mob.getUsedItemHand() == hand))
			return CrossbowItem.isCharged(item) ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
		if (mob.isUsingItem() && mob.getUsedItemHand() == hand) {
			ItemUseAnimation a = item.getUseAnimation();
			if (a == ItemUseAnimation.BLOCK) return HumanoidModel.ArmPose.BLOCK;
			if (a == ItemUseAnimation.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
			if (a == ItemUseAnimation.CROSSBOW) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
			if (a == ItemUseAnimation.SPEAR) return HumanoidModel.ArmPose.SPEAR;
		}
		if (item.is(Items.BOW) && mob.isAggressive()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
		return HumanoidMobRenderer.usesSpearPose(item, hand == InteractionHand.MAIN_HAND ? mob.getMainArm() : mob.getMainArm().getOpposite(), mob)
			? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.ITEM;
	}

	/** Render state with the soldier's skin and tabard tint. */
	public static class SoldierRenderState extends AvatarRenderState {
		public Identifier texture;
		public int tabardColor = 0xFF2E4C9E;
		public boolean showTabard = true;
		/** 3.0 P3 nameplate level of detail this frame (Nameplate.NONE, COMPACT or FULL); set by {@link SoldierNameplates#apply}. */
		public byte plate;
	}
}
