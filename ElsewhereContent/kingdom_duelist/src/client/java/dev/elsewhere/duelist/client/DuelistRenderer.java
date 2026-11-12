package dev.elsewhere.duelist.client;

import dev.elsewhere.duelist.boss.HumanBoss;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

/**
 * Player model (wide arms) with the player armor layers and player-style held-item poses. A wind-up with a raised cue
 * lifts the weapon arm overhead; a crouch cue crouches. The skin is {@code textures/entity/<profile.skin>.png}.
 */
public class DuelistRenderer<T extends HumanBoss> extends HumanoidMobRenderer<T, AvatarRenderState, PlayerModel> {
	public DuelistRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), part -> new PlayerModel(part, false)), ctx.getEquipmentRenderer()));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new BossRenderState();
	}

	@Override
	public void extractRenderState(T e, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		if (state instanceof BossRenderState s) {
			s.texture = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + e.profile.skin + ".png");
			s.raised = e.armRaised();
			dev.elsewhere.duelist.client.anim.DuelAnim.extract(e, s, partialTicks);
		}
		// 26.3: the dispatcher hands every AvatarRenderState to the vanilla avatar renderer, which draws state.skin (Steve by default)
		state.skin = skinOf(Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/" + e.profile.skin + ".png"));
	}

	private static final java.util.Map<Identifier, PlayerSkin> SKINS = new java.util.HashMap<>();

	/** The same fix as the kingdom module's {@code NpcSkin} (this module does not depend on kingdom). */
	static PlayerSkin skinOf(Identifier tex) {
		if (SKINS.size() > 256) SKINS.clear();
		return SKINS.computeIfAbsent(tex, t -> PlayerSkin.insecure(new ClientAsset.ResourceTexture(t, t), null, null, PlayerModelType.WIDE));
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state instanceof BossRenderState s && s.texture != null ? s.texture : Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/duelist_test.png");
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
		HumanoidModel.ArmPose main = pose(mob, mob.getMainHandItem(), InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose off = pose(mob, mob.getOffhandItem(), InteractionHand.OFF_HAND);
		if (main.isTwoHanded()) off = mob.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		return mob.getMainArm() == arm ? main : off;
	}

	private static HumanoidModel.ArmPose pose(HumanBoss mob, ItemStack item, InteractionHand hand) {
		if (item.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
		if (hand == InteractionHand.MAIN_HAND && mob.armRaised()) return HumanoidModel.ArmPose.THROW_TRIDENT;
		if (item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item) && !(mob.isUsingItem() && mob.getUsedItemHand() == hand)) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		if (mob.isUsingItem() && mob.getUsedItemHand() == hand) {
			ItemUseAnimation a = item.getUseAnimation();
			if (a == ItemUseAnimation.BLOCK) return HumanoidModel.ArmPose.BLOCK;
			if (a == ItemUseAnimation.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
			if (a == ItemUseAnimation.CROSSBOW) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
			if (a == ItemUseAnimation.SPEAR) return HumanoidModel.ArmPose.SPEAR;
		}
		return HumanoidModel.ArmPose.ITEM;
	}

	/** Render state with the boss skin, the raised-arm flag and the 3.0 move animation (filled by {@code DuelAnim.extract}, applied after {@code PlayerModel.setupAnim}). */
	public static class BossRenderState extends AvatarRenderState {
		public Identifier texture;
		public boolean raised;
		public boolean animOn;
		public byte animKey;
		public float animW, hurtW;
		/** 3.0 B4: kingdom style index ({@code PoseTable.STYLES}), phase-aura kingdom (-1 none) and level (0 off, 1 glyphs, 2 glyphs and swirl). */
		public int animStyle, auraKind = -1, auraLevel;
		public final float[] animV = new float[dev.elsewhere.duelist.client.anim.DuelAnim.N];
	}
}
