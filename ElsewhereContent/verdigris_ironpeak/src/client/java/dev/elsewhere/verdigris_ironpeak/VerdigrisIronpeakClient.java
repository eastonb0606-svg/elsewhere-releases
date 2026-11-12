package dev.elsewhere.verdigris_ironpeak;

import dev.elsewhere.boss.galvanox.GalvanoxBoss;
import dev.elsewhere.boss.galvanox.GalvanoxEntities;
import dev.elsewhere.boss.hrolf.HrolfEntities;
import dev.elsewhere.boss.thurgrim.ThurgrimEntities;
import dev.elsewhere.boss.verdigrim.VerdigrimEntities;
import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.ironpeak.mob.Rimeguard;
import dev.elsewhere.stub.verdigris_ironpeak.SetBonus;
import dev.elsewhere.verdigris_ironpeak.client.AltitudeHud;
import dev.elsewhere.verdigris_ironpeak.client.Shapes;
import dev.elsewhere.verdigris_sea.VerdigrisEntities;
import dev.elsewhere.verdigris_sea.mob.BarnacleGolem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;

/** Client entry point of the module. The core's client entry point calls {@link #initClient()} at merge. */
public final class VerdigrisIronpeakClient {
	private VerdigrisIronpeakClient() {}

	public static void initClient() {
		// Bosses
		EntityRendererRegistry.register(VerdigrimEntities.VERDIGRIM, ctx -> new Shapes.Humanoid<>(ctx, "verdigrim", 3.0F));
		EntityRendererRegistry.register(GalvanoxEntities.GALVANOX, ctx -> new Shapes.Golem<GalvanoxBoss>(ctx, "galvanox", 2.6F, b -> b.currentAttack() == GalvanoxBoss.A_HAMMER ? 10 : 0));
		EntityRendererRegistry.register(ThurgrimEntities.THURGRIM, ctx -> new Shapes.Humanoid<>(ctx, "thurgrim", 2.9F));
		EntityRendererRegistry.register(HrolfEntities.HROLF, ctx -> new Shapes.Humanoid<>(ctx, "hrolf", 1.5F));
		EntityRendererRegistry.register(HrolfEntities.GRIMHORN, ctx -> new Shapes.Goat<>(ctx, "grimhorn", 3.0F));
		EntityRendererRegistry.register(VerdigrimEntities.HYMN_TRIDENT, ThrownItemRenderer::new);
		EntityRendererRegistry.register(GalvanoxEntities.WAX_SHELL, ThrownItemRenderer::new);
		EntityRendererRegistry.register(GalvanoxEntities.ARC_BOLT, ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
		// Verdigris Sea mobs
		EntityRendererRegistry.register(VerdigrisEntities.CLOCKWORK_DROWNED, Shapes.Clockwork::new);
		EntityRendererRegistry.register(VerdigrisEntities.BARNACLE_GOLEM, ctx -> new Shapes.Golem<BarnacleGolem>(ctx, "barnacle_golem", 0.9F, g -> g.attackAnim));
		EntityRendererRegistry.register(VerdigrisEntities.TIDEWING_RAY, ctx -> new Shapes.Flier<>(ctx, "tidewing_ray", 2.6F));
		EntityRendererRegistry.register(VerdigrisEntities.BRASS_GUARDIAN, Shapes.Brass::new);
		EntityRendererRegistry.register(VerdigrisEntities.TIDEWRIGHT, WanderingTraderRenderer::new);
		// Ironpeak mobs
		EntityRendererRegistry.register(IronpeakEntities.HIGHLAND_YAK, ctx -> new Shapes.Cow<>(ctx, "highland_yak", 1.3F));
		EntityRendererRegistry.register(IronpeakEntities.IRONCLAN_RAIDER, ctx -> new Shapes.Humanoid<>(ctx, "ironclan_raider", 1.4F));
		EntityRendererRegistry.register(IronpeakEntities.CRAG_ROC, ctx -> new Shapes.Flier<>(ctx, "crag_roc", 3.0F));
		EntityRendererRegistry.register(IronpeakEntities.RIMEGUARD, ctx -> new Shapes.Golem<Rimeguard>(ctx, "rimeguard", 1.15F, r -> Math.max(r.attackAnim, r.throwAnim / 2)));
		EntityRendererRegistry.register(IronpeakEntities.CLAN_QUARTERMASTER, WanderingTraderRenderer::new);

		HudElementRegistry.addLast(Reg.id("altitude"), new AltitudeHud());
		dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.registerMeter(AltitudeHud.KEY);
		dev.elsewhere.verdigris_ironpeak.client.LongbowZoom.register();
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			SetBonus set = SetBonus.forItem(stack.getItem());
			if (set != null) lines.addAll(set.tooltip());
		});
	}
}
