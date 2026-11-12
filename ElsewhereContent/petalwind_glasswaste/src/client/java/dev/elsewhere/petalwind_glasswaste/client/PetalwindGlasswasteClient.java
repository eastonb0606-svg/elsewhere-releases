package dev.elsewhere.petalwind_glasswaste.client;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalMirage;
import dev.elsewhere.glasswaste.Sandswimmer;
import dev.elsewhere.petalwind.PetalwindItems;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.petalwind_glasswaste.Net;
import dev.elsewhere.petalwind_glasswaste.Reg;
import dev.elsewhere.stub.petalwind_glasswaste.SetBonus;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Client entry point for the Petalwind Isles + Glasswaste content. The core's client initializer calls {@link #initClient()}. */
public final class PetalwindGlasswasteClient {
	private PetalwindGlasswasteClient() {}

	public static int heat;
	public static boolean showHeat, glare;
	private static boolean wasJumpDown;

	public static void initClient() {
		layer("kazehana", GeneratedModels::kazehana);
		layer("sunwright_revenant", GeneratedModels::sunwrightRevenant);
		layer("tempestra", GeneratedModels::tempestra);
		layer("heliodor", GeneratedModels::heliodor);
		layer("ankhara_head", GeneratedModels::ankharaHead);
		layer("ankhara_segment", GeneratedModels::ankharaSegment);
		layer("sandswimmer_head", GeneratedModels::sandswimmerHead);
		layer("sandswimmer_segment", GeneratedModels::sandswimmerSegment);
		layer("gale_kite", GeneratedModels::galeKite);
		layer("blossom_breeze", GeneratedModels::blossomBreeze);
		layer("cumulus", GeneratedModels::cumulus);
		layer("skywhale", GeneratedModels::skywhale);
		layer("glass_scarab", GeneratedModels::glassScarab);
		layer("sun_mote", GeneratedModels::sunMote);
		layer("windwright", GeneratedModels::windwright);
		layer("dune_factor", GeneratedModels::duneFactor);

		simple(ModEntities.GALE_KITE, "gale_kite", 1.0F, 0.4F, (e, s) -> {}, true);
		simple(ModEntities.BLOSSOM_BREEZE, "blossom_breeze", 1.2F, 0.5F, (e, s) -> {}, true);
		EntityRendererRegistry.register(ModEntities.CUMULUS, ctx -> new PGRenderer<>(ctx, model(ctx, "cumulus", false), "cumulus", 1.0F, 0.4F,
			(e, s) -> {}, false, null, null, 0, 0, 1) {
			@Override
			protected void scale(PGState state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
				poseStack.scale(state.scale * 0.999F, state.scale, state.scale);
			}

			@Override
			public void extractRenderState(dev.elsewhere.petalwind.Cumulus entity, PGState state, float partialTicks) {
				super.extractRenderState(entity, state, partialTicks);
				state.scale = entity.getSize() * 0.6F;
			}
		});
		simple(ModEntities.SKYWHALE, "skywhale", 1.4F, 2.0F, (e, s) -> s.flag = !e.getItemBySlot(EquipmentSlot.BODY).isEmpty(), false);
		simple(ModEntities.GLASS_SCARAB, "glass_scarab", 1.0F, 0.4F, (e, s) -> {}, true);
		EntityRendererRegistry.register(ModEntities.SANDSWIMMER, ctx -> new PGRenderer<Sandswimmer>(ctx, model(ctx, "sandswimmer_head", false),
			"sandswimmer_head", 1.0F, 0.4F, (e, s) -> s.pose = e.isBurrowed() ? 0 : 1, false, model(ctx, "sandswimmer_segment", false),
			"sandswimmer_segment", 5, 0.55, 0.6F));
		simple(ModEntities.SUNWRIGHT_REVENANT, "sunwright_revenant", 1.15F, 0.5F, (e, s) -> {}, false);
		simple(ModEntities.SUN_MOTE, "sun_mote", 1.0F, 0.3F, (e, s) -> {}, false);
		simple(ModEntities.WINDWRIGHT, "windwright", 1.0F, 0.5F, (e, s) -> {}, false);
		simple(ModEntities.DUNE_FACTOR, "dune_factor", 1.0F, 0.5F, (e, s) -> {}, false);

		simple(ModEntities.KAZEHANA, "kazehana", 1.6F, 0.8F, (e, s) -> s.pose = e.isDormant() ? Kazehana.S_BOW : e.state(), false);
		EntityRendererRegistry.register(ModEntities.PETAL_MIRAGE, ctx -> new PGRenderer<PetalMirage>(ctx, model(ctx, "kazehana", true), "kazehana",
			1.6F, 0.0F, (e, s) -> s.pose = e.state(), true, null, null, 0, 0, 1));
		simple(ModEntities.TEMPESTRA, "tempestra", 2.0F, 0.0F, (e, s) -> s.pose = e.shielded() ? 1 : 0, true);
		simple(ModEntities.HELIODOR, "heliodor", 1.6F, 1.4F, (e, s) -> {
			s.pose = e.slamAnimation.isStarted() && e.tickCount - 20 < 0 ? 1 : 0;
			s.flag = !e.stunned();
		}, false);
		EntityRendererRegistry.register(ModEntities.ANKHARA, ctx -> new PGRenderer<Ankhara>(ctx, model(ctx, "ankhara_head", false), "ankhara_head",
			2.0F, 1.5F, (e, s) -> {
				s.pose = e.mode();
				s.flag = e.mode() == Ankhara.M_STUNNED;
			}, false, model(ctx, "ankhara_segment", false), "ankhara_segment", Ankhara.SEGMENTS, 2.3, 0.45F));

		ClientPlayNetworking.registerGlobalReceiver(Net.HeatPayload.TYPE, (payload, context) -> {
			heat = payload.heat();
			showHeat = payload.show();
			glare = payload.glare();
		});
		// Every Elsewhere display is a per-player setting (config/elsewhere-client.json; stub until the arsenal module's shared one).
		dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.registerMeter(HeatHud.METER_KEY);
		dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.registerToggle(HeatEffects.EFFECTS_KEY, true);
		dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.registerToggle(HeatEffects.REDUCE_MOTION_KEY, false);
		HudElementRegistry.addFirst(Reg.id("glasswaste_heat_effects"), HeatEffects::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Reg.id("glasswaste_heat"), HeatHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(PetalwindGlasswasteClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(HeatHud::tick);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			for (SetBonus set : SetBonus.all()) {
				if (!set.contains(stack)) continue;
				for (Component c : set.tooltip) lines.add(c.copy().withStyle(ChatFormatting.GOLD));
			}
		});
	}

	private static void layer(String name, Supplier<LayerDefinition> def) {
		ModelLayerRegistry.registerModelLayer(loc(name), def::get);
	}

	private static ModelLayerLocation loc(String name) {
		return new ModelLayerLocation(Reg.id(name), "main");
	}

	static PGModel model(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx, String name, boolean translucent) {
		return new PGModel(ctx.bakeLayer(loc(name)), name, translucent);
	}

	private static <T extends LivingEntity> void simple(EntityType<T> type, String name, float scale, float shadow, BiConsumer<T, PGState> ex,
		boolean translucent) {
		EntityRendererRegistry.register(type, ctx -> new PGRenderer<>(ctx, model(ctx, name, translucent), name, scale, shadow, ex, translucent, null, null,
			0, 0, 1));
	}

	/** Mid-air jump requests (Galeweave, Petalstep Sandals, Zephyr Wings) and the Petal Glider's extra drag. */
	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) return;
		boolean jump = mc.options.keyJump.isDown();
		if (jump && !wasJumpDown && !player.onGround() && !player.isInWater() && !player.getAbilities().flying && !player.isPassenger()) {
			boolean relevant = player.isFallFlying() ? player.getItemBySlot(EquipmentSlot.CHEST).is(PetalwindItems.ZEPHYR_WINGS)
				: player.getItemBySlot(EquipmentSlot.FEET).is(PetalwindItems.PETALSTEP_SANDALS) || player.getItemBySlot(EquipmentSlot.FEET).is(PetalwindItems.GALEWEAVE_BOOTS);
			if (relevant && player.tickCount > 5) ClientPlayNetworking.send(new Net.AirJumpPayload(player.isShiftKeyDown()));
		}
		wasJumpDown = jump;
		if (player.isFallFlying() && player.getItemBySlot(EquipmentSlot.CHEST).is(PetalwindItems.PETAL_GLIDER)) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * GearStats.PETAL_GLIDER_DRAG, v.y - GearStats.PETAL_GLIDER_SINK, v.z * GearStats.PETAL_GLIDER_DRAG);
		}
	}
}
