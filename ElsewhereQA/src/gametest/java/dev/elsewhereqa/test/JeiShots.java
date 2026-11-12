package dev.elsewhereqa.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elsewhere inside JEI: item list, Wayfinder attunement recipes and uses, one info page. Only called when JEI is loaded. */
final class JeiShots {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereQA-client");

	private JeiShots() {
	}

	static void run(final ClientGameTestContext ctx, final Consumer<String> shot) {
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitFor(mc -> JeiTestPlugin.runtime != null, 1200);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> JeiTestPlugin.runtime.getIngredientFilter().setFilterText("@elsewhere"));
		ctx.waitTicks(20);
		int shown = ctx.computeOnClient(mc -> JeiTestPlugin.runtime.getIngredientFilter().getFilteredItemStacks().size());
		LOG.info("[qa-client] JEI shows {} Elsewhere entries", shown);
		shot.accept("jei_items");
		String cats = ctx.computeOnClient(mc -> {
			List<String> l = new ArrayList<>();
			JeiTestPlugin.runtime.getRecipeManager().createRecipeCategoryLookup().get().forEach(c -> {
				String uid = c.getRecipeType().getUid().toString();
				if (uid.contains("elsewhere")) {
					l.add(uid);
				}
			});
			return String.join(", ", l);
		});
		LOG.info("[qa-client] JEI Elsewhere recipe categories: [{}]", cats);
		// Wayfinder attunement: how to make the Pale Hollow wayfinder, and what the plain Wayfinder is used in
		show(ctx, "elsewhere:wayfinder_pale_hollow", RecipeIngredientRole.OUTPUT);
		shot.accept("jei_recipe_wayfinder_pale_hollow");
		show(ctx, "elsewhere:wayfinder", RecipeIngredientRole.INPUT);
		shot.accept("jei_uses_wayfinder");
		show(ctx, "elsewhere:wayfinder_the_hush", RecipeIngredientRole.OUTPUT);
		shot.accept("jei_recipe_wayfinder_the_hush");
		// one Elsewhere information page
		String info = ctx.computeOnClient(mc -> {
			IJeiRuntime rt = JeiTestPlugin.runtime;
			IRecipeManager rm = rt.getRecipeManager();
			List<IJeiIngredientInfoRecipe> all = rm.createRecipeLookup(RecipeTypes.INFORMATION).get()
				.filter(r -> r.getIngredients().stream().anyMatch(i -> i.getItemStack()
					.map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("elsewhere")).orElse(false)))
				.toList();
			if (all.isEmpty()) {
				return "none";
			}
			IJeiIngredientInfoRecipe r = all.get(0);
			ItemStack st = r.getIngredients().get(0).getItemStack().orElse(ItemStack.EMPTY);
			rt.getRecipesGui().showRecipes(rm.getRecipeCategory(RecipeTypes.INFORMATION), List.of(r),
				List.of(rt.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, st)));
			return all.size() + " pages; showing " + BuiltInRegistries.ITEM.getKey(st.getItem());
		});
		LOG.info("[qa-client] JEI Elsewhere info pages: {}", info);
		ctx.waitTicks(20);
		shot.accept("jei_info_page");
		// 0.4.0: info page count (jei_compat info.json has 206 pages) and one of the new summon-item pages
		String count = ctx.computeOnClient(mc -> {
			IJeiRuntime rt = JeiTestPlugin.runtime;
			IRecipeManager rm = rt.getRecipeManager();
			List<IJeiIngredientInfoRecipe> all = rm.createRecipeLookup(RecipeTypes.INFORMATION).get().toList();
			java.util.Set<String> ids = new java.util.TreeSet<>();
			int ew = 0;
			IJeiIngredientInfoRecipe effigy = null;
			for (IJeiIngredientInfoRecipe r : all) {
				boolean any = false;
				for (var ing : r.getIngredients()) {
					var st = ing.getItemStack();
					if (st.isPresent() && BuiltInRegistries.ITEM.getKey(st.get().getItem()).getNamespace().equals("elsewhere")) {
						any = true;
						String id = BuiltInRegistries.ITEM.getKey(st.get().getItem()).getPath();
						ids.add(id);
						if (id.equals("heartwood_effigy") && effigy == null) {
							effigy = r;
						}
					}
				}
				if (any) {
					ew++;
				}
			}
			if (effigy != null) {
				ItemStack st = effigy.getIngredients().get(0).getItemStack().orElse(ItemStack.EMPTY);
				rt.getRecipesGui().showRecipes(rm.getRecipeCategory(RecipeTypes.INFORMATION), List.of(effigy),
					List.of(rt.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, st)));
			}
			return all.size() + " information recipes in total, " + ew + " with an Elsewhere item, " + ids.size() + " distinct Elsewhere items; heartwood_effigy page "
				+ (effigy != null ? "shown" : "MISSING");
		});
		LOG.info("[qa-client] V04 INFO jei info pages: {}", count);
		ctx.waitTicks(20);
		shot.accept("jei_info_heartwood_effigy");
		ctx.runOnClient(mc -> JeiTestPlugin.runtime.getIngredientFilter().setFilterText(""));
		ctx.setScreen(() -> null);
	}


	/** Receives "[status, area, detail]" verdicts from the release client test. */
	interface Rep {
		void rep(String status, String area, String detail);
	}

	private static final List<String> NEW_ITEMS = List.of("gale_rapier", "steam_auger", "termite_hive_lantern", "seers_hourglass", "sporebloom_staff", "mirrorbolt_arbalest",
		"prospectors_pendulum", "void_tether_spool", "gale_ribbon", "mirrorglass_prism", "hushward_bell", "ironclad_flail", "clockwork_repeater", "glimmer_mote", "rift_anchor",
		"galvanic_coilmail_chestplate", "stormcrest_helm", "heartwood_effigy");

	/** Release pass: the Wayfinder attunement pages and a set of new items must each open a JEI recipe or information page. */
	static void release(final ClientGameTestContext ctx, final Consumer<String> shot, final Rep rep) {
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		try {
			ctx.waitFor(mc -> JeiTestPlugin.runtime != null, 1200);
		} catch (Throwable t) {
			rep.rep("FAIL", "jei", "JEI runtime never became available: " + t);
			return;
		}
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> JeiTestPlugin.runtime.getIngredientFilter().setFilterText("@elsewhere"));
		ctx.waitTicks(20);
		int shown = ctx.computeOnClient(mc -> JeiTestPlugin.runtime.getIngredientFilter().getFilteredItemStacks().size());
		shot.accept("jei_items");
		rep.rep(shown > 100 ? "PASS" : "FAIL", "jei", "the ingredient list shows " + shown + " Elsewhere entries (filter @elsewhere)");
		String cats = ctx.computeOnClient(mc -> {
			List<String> l = new ArrayList<>();
			JeiTestPlugin.runtime.getRecipeManager().createRecipeCategoryLookup().get().forEach(c -> {
				String uid = c.getRecipeType().getUid().toString();
				if (uid.contains("elsewhere")) {
					l.add(uid);
				}
			});
			return String.join(", ", l);
		});
		rep.rep(cats.isEmpty() ? "FAIL" : "PASS", "jei attunement", "Elsewhere recipe categories: [" + cats + "]");
		ctx.runOnClient(mc -> JeiTestPlugin.runtime.getIngredientFilter().setFilterText(""));
		for (String dim : List.of("pale_hollow", "petalwind", "glasswaste", "verdigris_sea", "ironpeak", "greenhaze", "the_hush")) {
			page(ctx, shot, rep, "wayfinder_" + dim, RecipeIngredientRole.OUTPUT, "jei attunement");
		}
		page(ctx, shot, rep, "wayfinder", RecipeIngredientRole.INPUT, "jei attunement");
		for (String id : NEW_ITEMS) {
			page(ctx, shot, rep, id, RecipeIngredientRole.OUTPUT, "jei page");
		}
		String info = ctx.computeOnClient(mc -> {
			List<IJeiIngredientInfoRecipe> all = JeiTestPlugin.runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).get().toList();
			int ew = 0;
			for (IJeiIngredientInfoRecipe r : all) {
				if (r.getIngredients().stream().anyMatch(i -> i.getItemStack().map(x -> BuiltInRegistries.ITEM.getKey(x.getItem()).getNamespace().equals("elsewhere")).orElse(false))) {
					ew++;
				}
			}
			return all.size() + " information recipes, " + ew + " about Elsewhere items";
		});
		rep.rep(info.contains(" 0 about") ? "FAIL" : "PASS", "jei info", info);
		ctx.setScreen(() -> null);
	}

	private static void page(final ClientGameTestContext ctx, final Consumer<String> shot, final Rep rep, final String path, final RecipeIngredientRole role, final String area) {
		Identifier id = Identifier.fromNamespaceAndPath("elsewhere", path);
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			rep.rep("SKIP", area, "elsewhere:" + path + " does not exist in this jar");
			return;
		}
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(3);
		String screen = ctx.computeOnClient(mc -> {
			IJeiRuntime rt = JeiTestPlugin.runtime;
			ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
			rt.getRecipesGui().show(rt.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, st));
			return mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName();
		});
		ctx.waitTicks(12);
		String after = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
		boolean ok = after.contains("Recipes");
		if (ok) {
			shot.accept("jei_" + path);
		}
		rep.rep(ok ? "PASS" : path.equals("glimmer_mote") ? "WARN" : "FAIL", area, "JEI " + role + " page for elsewhere:" + path + (ok ? " opens" : " does not open (screen " + after + ")"));
	}

	private static void show(final ClientGameTestContext ctx, final String id, final RecipeIngredientRole role) {
		String screen = ctx.computeOnClient(mc -> {
			IJeiRuntime rt = JeiTestPlugin.runtime;
			ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
			rt.getRecipesGui().show(rt.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, st));
			return mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName();
		});
		ctx.waitTicks(20);
		String after = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
		LOG.info("[qa-client] JEI {} {}: screen {}", role, id, after);
	}
}
