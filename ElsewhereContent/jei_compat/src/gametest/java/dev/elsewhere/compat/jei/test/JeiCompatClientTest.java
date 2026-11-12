package dev.elsewhere.compat.jei.test;

import dev.elsewhere.compat.jei.AttunementCategory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elsewhere + JEI (both sides, singleplayer): attunement pages, info pages, item list, hidden items, brewing. */
public class JeiCompatClientTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("elsewhere-jei-test");
	private final List<String> fails = new ArrayList<>();
	private Path out;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		out = FabricLoader.getInstance().getGameDir().resolve("../../../screenshots").normalize();
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("gamemode creative @a");
			ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			ctx.waitFor(mc -> RuntimeGrab.runtime != null, 2400);
			ctx.waitTicks(20);

			ctx.runOnClient(mc -> RuntimeGrab.runtime.getIngredientFilter().setFilterText("@elsewhere"));
			ctx.waitTicks(20);
			int n = ctx.computeOnClient(mc -> RuntimeGrab.runtime.getIngredientFilter().getFilteredItemStacks().size());
			boolean sealHidden = ctx.computeOnClient(mc -> RuntimeGrab.runtime.getIngredientFilter().getFilteredItemStacks().stream()
				.noneMatch(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals("arena_seal")));
			log("item list @elsewhere: " + n + " entries; arena_seal hidden: " + sealHidden);
			check(n > 100, "Elsewhere item list too small: " + n);
			check(sealHidden, "arena_seal still listed");
			shot(ctx, "items");
			ctx.runOnClient(mc -> RuntimeGrab.runtime.getIngredientFilter().setFilterText(""));

			int pages = ctx.computeOnClient(mc -> (int) RuntimeGrab.runtime.getRecipeManager().createRecipeLookup(AttunementCategory.TYPE).get().count());
			log("attunement pages: " + pages);
			check(pages == 7, "expected 7 attunement pages, got " + pages);
			ctx.runOnClient(mc -> RuntimeGrab.runtime.getRecipesGui().showTypes(List.of(AttunementCategory.TYPE)));
			ctx.waitTicks(20);
			shot(ctx, "attunement");
			show(ctx, RecipeIngredientRole.OUTPUT, "elsewhere:wayfinder_the_hush");
			shot(ctx, "attunement_hush");

			int info = ctx.computeOnClient(mc -> (int) RuntimeGrab.runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).get()
				.filter(r -> r.getIngredients().stream().anyMatch(i -> i.getItemStack().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("elsewhere")).orElse(false))).count());
			log("Elsewhere info pages: " + info);
			check(info > 100, "too few Elsewhere info pages: " + info);
			for (String id : List.of("elsewhere:heartwood_effigy", "elsewhere:abyssal_eye")) {
				infoPage(ctx, id);
				shot(ctx, "info_" + id.substring(10));
			}

			int brew = ctx.computeOnClient(mc -> (int) RuntimeGrab.runtime.getRecipeManager().createRecipeLookup(RecipeTypes.BREWING).get()
				.filter(r -> BuiltInRegistries.ITEM.getKey(r.getPotionOutput().getItem()).getNamespace().equals("elsewhere")).count());
			log("JEI brewing recipes with Elsewhere output: " + brew);
			show(ctx, RecipeIngredientRole.OUTPUT, "elsewhere:glimmer_tonic");
			shot(ctx, "glimmer_tonic");
			ctx.setScreen(() -> null);
		}
		fails.forEach(f -> LOG.error("[jeitest] FAIL {}", f));
		log("TOTAL " + (fails.isEmpty() ? "ALL PASSED" : fails.size() + " FAILED"));
		if (!fails.isEmpty()) {
			throw new AssertionError(String.join("; ", fails));
		}
	}

	private void infoPage(final ClientGameTestContext ctx, final String id) {
		ctx.runOnClient(mc -> {
			IJeiRuntime rt = RuntimeGrab.runtime;
			IRecipeManager rm = rt.getRecipeManager();
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
			if (item.isEmpty()) {
				return;
			}
			var list = rm.createRecipeLookup(RecipeTypes.INFORMATION).get()
				.filter(r -> r.getIngredients().stream().anyMatch(i -> i.getItemStack().map(s -> s.is(item.get())).orElse(false))).toList();
			if (!list.isEmpty()) {
				rt.getRecipesGui().showRecipes(rm.getRecipeCategory(RecipeTypes.INFORMATION), list, List.of());
			}
		});
		ctx.waitTicks(20);
	}

	private void show(final ClientGameTestContext ctx, final RecipeIngredientRole role, final String id) {
		ctx.runOnClient(mc -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(i -> {
			IJeiRuntime rt = RuntimeGrab.runtime;
			rt.getRecipesGui().show(rt.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(i)));
		}));
		ctx.waitTicks(20);
	}

	private void check(final boolean ok, final String msg) {
		if (!ok) {
			fails.add(msg);
		}
	}

	private void shot(final ClientGameTestContext ctx, final String name) {
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("elsewhere_jei_" + name).withDestinationDir(out).disableCounterPrefix());
	}

	private static void log(final String s) {
		LOG.info("[jeitest] {}", s);
	}
}
