package dev.elsewhere.arsenal;

import dev.elsewhere.arsenal.client.GlimmerHud;
import dev.elsewhere.arsenal.client.MagicBoltRenderer;
import dev.elsewhere.arsenal.glimmer.GlimmerPayload;
import dev.elsewhere.arsenal.spell.ArsenalEntities;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.hud.ElsewhereHud;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Arsenal client: the Glimmer bar (registered with the shared {@link ElsewhereHud}), bolt renderer, tooltips. */
public final class ArsenalClient {
	private ArsenalClient() {}

	public static KeyMapping CYCLE_GLIMMER, CALLING_PANEL;
	static final boolean CALLING_TOOLTIP = Overhaul3.CALLINGS && Overhaul3.on("calling_tooltip");
	static final boolean CALLING_PANEL_ON = Overhaul3.CALLINGS && Overhaul3.on("calling_panel");

	public static void initClient() {
		ElsewhereHud.init();
		ElsewhereHud.registerMeter(GlimmerHud.METER, "glimmer");
		dev.elsewhere.arsenal.client.anim.UseAnims.init(); // P6 weapon use animations (kill switch use_anims)
		dev.elsewhere.arsenal.client.P5Tooltips.init(); // P5 stat tooltips with Shift detail (kill switch p5_tooltips)
		ClientPlayNetworking.registerGlobalReceiver(GlimmerPayload.TYPE, (payload, ctx) -> GlimmerHud.receive(payload));
		EntityRendererRegistry.register(ArsenalEntities.MAGIC_BOLT, MagicBoltRenderer::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, ArsenalContent.id("glimmer_bar"), GlimmerHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ArsenalContent.id("trinket_marks"), GlimmerHud::trinketMarks);
		CYCLE_GLIMMER = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.elsewhere.cycle_glimmer", InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(), ElsewhereHud.CATEGORY));
		if (Overhaul3.CALLINGS) ClientPlayNetworking.registerGlobalReceiver(dev.elsewhere.arsenal.spell.CallingPayload.TYPE,
			(payload, ctx) -> Calling.receive(ctx.player(), payload));
		if (CALLING_PANEL_ON) {
			HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ArsenalContent.id("calling_panel"), dev.elsewhere.arsenal.client.CallingPanel::render);
			CALLING_PANEL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.elsewhere.calling_panel", InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(), ElsewhereHud.CATEGORY));
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (CALLING_PANEL != null) while (CALLING_PANEL.consumeClick()) dev.elsewhere.arsenal.client.CallingPanel.shown = !dev.elsewhere.arsenal.client.CallingPanel.shown;
			while (CYCLE_GLIMMER.consumeClick()) {
				ElsewhereHud.Mode m = ElsewhereHud.mode(GlimmerHud.METER).next();
				ElsewhereHud.setMode(GlimmerHud.METER, m);
				if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("elsewhere.hud.mode",
					Component.translatable("elsewhere.hud.meter.glimmer"), Component.translatable("elsewhere.hud.mode." + m.key())));
			}
		});
		ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
			Item item = stack.getItem();
			var mc = Minecraft.getInstance();
			if (item instanceof CasterItem ci) {
				// P5: only arsenal casters have a description key; another module's caster (gear3) showed the raw key here
				String desc = "elsewhere.tooltip." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
				int at = 1;
				if (net.minecraft.locale.Language.getInstance().has(desc)) lines.add(at++, Component.translatable(desc).withStyle(ChatFormatting.GRAY));
				if (mc.player != null && Overhaul3.CALLINGS) {
					if (CALLING_TOOLTIP) lines.add(Math.min(lines.size(), at++), Component.literal(String.format("Caster %d%%: spells x%.2f, cost x%.2f", Math.round(Math.max(Calling.mastery(mc.player, Calling.Path.CASTER),
						ci.isStarter() ? Calling.STARTER_MIN_MASTERY : 0F) * 100), Calling.spellMult(mc.player, ci.isStarter()), Calling.costMult(mc.player, ci.isStarter()))).withStyle(ChatFormatting.LIGHT_PURPLE));
					lines.add(Math.min(lines.size(), at), Component.translatable("elsewhere.tooltip.cost", dev.elsewhere.arsenal.glimmer.Glimmer.cost(mc.player, ci.rawCost(mc.player), ci.isStarter()))
						.withStyle(ChatFormatting.DARK_PURPLE));
				} else if (mc.player != null) {
					int c = Calling.casterFor(mc.player, ci.isStarter());
					lines.add(Math.min(lines.size(), at), Component.translatable("elsewhere.calling.caster", c, Math.round(Calling.spellDamage(c) * 100),
						Math.round(Calling.spellCost(c) * 100)).withStyle(ChatFormatting.LIGHT_PURPLE));
					lines.add(Math.min(lines.size(), at + 1), Component.translatable("elsewhere.tooltip.cost", dev.elsewhere.arsenal.glimmer.Glimmer.cost(mc.player, ci.rawCost(mc.player), ci.isStarter()))
						.withStyle(ChatFormatting.DARK_PURPLE));
				}
				if (ci.isStarter() && !Overhaul3.CALLINGS) lines.add(Component.translatable("elsewhere.calling.starter").withStyle(ChatFormatting.DARK_GRAY));
			} else if (stack.is(dev.elsewhere.arsenal.spell.Calling.CASTER_ARMOR)) {
				if (!Overhaul3.CALLINGS) lines.add(1, Component.translatable("elsewhere.tooltip.caster_piece").withStyle(ChatFormatting.RED));
				else if (CALLING_TOOLTIP && mc.player != null) lines.add(1, dev.elsewhere.arsenal.client.CallingPanel.tooltip(mc.player, stack).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
				if (stack.is(ArsenalItems.tag("set/moonsilk/head")) || stack.is(ArsenalItems.tag("set/moonsilk/chest")) || stack.is(ArsenalItems.tag("set/moonsilk/legs"))
					|| stack.is(ArsenalItems.tag("set/moonsilk/feet"))) lines.add(Math.min(2, lines.size()), Component.translatable("elsewhere.tooltip.moonlit",
						dev.elsewhere.arsenal.spell.ArsenalTooltips.moonlitArgs()).withStyle(ChatFormatting.AQUA));
				if (item == ArsenalItems.STORMSAGE_MANTLE) lines.add(Math.min(2, lines.size()), Component.translatable("elsewhere.tooltip.stormsage_mantle",
					dev.elsewhere.arsenal.spell.ArsenalTooltips.mantleArgs()).withStyle(ChatFormatting.AQUA));
			} else if (CALLING_TOOLTIP && mc.player != null && dev.elsewhere.arsenal.client.CallingPanel.tooltip(mc.player, stack) != null) {
				lines.add(Math.min(1, lines.size()), dev.elsewhere.arsenal.client.CallingPanel.tooltip(mc.player, stack).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
			} else if (item == ArsenalItems.GLIMMER_TONIC || item == ArsenalItems.STRONG_GLIMMER_TONIC) {
				lines.add(1, Component.translatable("elsewhere.tooltip.tonic", dev.elsewhere.arsenal.spell.ArsenalTooltips.tonicArgs(item))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
	}
}
