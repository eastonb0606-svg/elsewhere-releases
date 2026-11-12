package dev.elsewhere.gear3.s3.ascension;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Catalogue;
import dev.elsewhere.gear3.Gear3Def;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

/** Self-test block for the Ascension Shard (s-ascension). Synchronous on the server thread; check names start "ascension: ". */
public final class AscensionTests {
	private AscensionTests() {}

	private static final String P = "ascension: ";

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item shard = Ascension.shard();
		Item helm = Gear3Items.BY_ID.get("galeplate_helmet");
		check(P + "shard registered as elsewhere:gear3_ascension_shard", shard != null && shard != Items.AIR && Ascension.ASCENDED != null);
		if (shard == null || shard == Items.AIR || helm == null || Ascension.ASCENDED == null) {
			info(P + "shard, marker or Galeplate Helmet missing: the rest of the block is skipped");
			return;
		}
		ItemStack sh = new ItemStack(shard);
		check(P + "shard is epic, fire-resistant, stacks to 64 and glints", sh.getRarity() == Rarity.EPIC && sh.has(DataComponents.DAMAGE_RESISTANT)
			&& sh.getMaxStackSize() == 64 && Boolean.TRUE.equals(sh.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
			sh.getRarity() + " fire=" + sh.has(DataComponents.DAMAGE_RESISTANT) + " stack=" + sh.getMaxStackSize());
		check(P + "kill switch \"ascension\" is on (default config)", Ascension.ENABLED);
		check(P + "set bonus II reads the Ascendant marker", SetBonuses.ascendant.test(Ascension.ascend(new ItemStack(helm)))
			&& !SetBonuses.ascendant.test(new ItemStack(helm)));
		check(P + "recipe serializer elsewhere:gear3_ascension registered",
			BuiltInRegistries.RECIPE_SERIALIZER.getValue(AscensionRecipe.ID) == AscensionRecipe.SERIALIZER);
		checkS(P + "recipe elsewhere:gear3_ascension loaded (no template, base #elsewhere:gear3_ascendable, addition the shard)", () -> {
			Optional<RecipeHolder<?>> h = s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, AscensionRecipe.ID));
			return h.isPresent() && h.get().value() instanceof AscensionRecipe r && r.templateIngredient().isEmpty()
				&& r.additionIngredient().map(a -> a.test(new ItemStack(shard))).orElse(false) && r.baseIngredient().test(new ItemStack(helm));
		});

		// ---- which pieces ascend
		checkS(P + "every gear3 armor piece is in #elsewhere:gear3_ascendable", () -> {
			List<String> miss = new ArrayList<>();
			for (Gear3Def d : Gear3Catalogue.DEFS)
				if ("armor".equals(d.kind) && Gear3Items.BY_ID.get(d.id) != null && !new ItemStack(Gear3Items.BY_ID.get(d.id)).is(Ascension.ASCENDABLE)) miss.add(d.id);
			if (!miss.isEmpty()) info(P + "not ascendable: " + miss);
			return miss.isEmpty();
		});
		checkS(P + "every registered set-bonus piece is ascendable (set bonus II is reachable for every set)", () -> {
			List<String> miss = new ArrayList<>();
			int n = 0;
			for (SetBonus b : SetBonuses.ALL)
				for (int i = 0; i < b.pieceCount(); i++)
					for (Identifier id : b.accepted(i)) {
						if (!BuiltInRegistries.ITEM.containsKey(id)) continue; // that module is not in this environment
						n++;
						if (!new ItemStack(BuiltInRegistries.ITEM.getValue(id)).is(Ascension.ASCENDABLE)) miss.add(b.id + ":" + id);
					}
			if (!miss.isEmpty()) info(P + "set pieces not ascendable: " + miss);
			info(P + n + " registered set-bonus pieces checked");
			return miss.isEmpty() && n > 0;
		});
		check(P + "a netherite helmet, a gear3 weapon and the shard itself are not ascendable", !Ascension.canAscend(new ItemStack(Items.NETHERITE_HELMET))
			&& (Gear3Items.BY_ID.get("bellbreaker_maul") == null || !Ascension.canAscend(new ItemStack(Gear3Items.BY_ID.get("bellbreaker_maul"))))
			&& !Ascension.canAscend(new ItemStack(shard)));

		// ---- the recipe through the real recipe manager
		ItemStack plain = new ItemStack(helm);
		ItemAttributeModifiers before = plain.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		Optional<RecipeHolder<SmithingRecipe>> found = s.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, plain, new ItemStack(shard)), l);
		check(P + "Galeplate Helmet + shard (empty template) finds the ascension recipe", found.isPresent() && found.get().value() instanceof AscensionRecipe);
		ItemStack asc = found.isPresent() ? found.get().value().assemble(new SmithingRecipeInput(ItemStack.EMPTY, plain, new ItemStack(shard))) : ItemStack.EMPTY;
		ItemAttributeModifiers after = asc.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double t0 = before.compute(Attributes.ARMOR_TOUGHNESS, 0, EquipmentSlot.HEAD), t1 = after.compute(Attributes.ARMOR_TOUGHNESS, 0, EquipmentSlot.HEAD);
		check(P + "the result is the same item, Ascendant, toughness +1 (2.5 -> 3.5)", asc.is(helm) && Ascension.isAscended(asc) && near(t1, t0 + 1.0) && near(t0, 2.5),
			"toughness " + t0 + " -> " + t1);
		check(P + "armor and knockback resistance unchanged, toughness raised in place (no extra modifier)",
			near(after.compute(Attributes.ARMOR, 0, EquipmentSlot.HEAD), before.compute(Attributes.ARMOR, 0, EquipmentSlot.HEAD))
				&& near(after.compute(Attributes.KNOCKBACK_RESISTANCE, 0, EquipmentSlot.HEAD), before.compute(Attributes.KNOCKBACK_RESISTANCE, 0, EquipmentSlot.HEAD))
				&& after.modifiers().size() == before.modifiers().size()
				&& near(after.compute(Attributes.ARMOR_TOUGHNESS, 0, EquipmentSlot.CHEST), 0));
		checkS(P + "gold \"Ascendant\" name wraps the piece's name, one gold lore line added, glint on", () -> {
			Component n = asc.get(DataComponents.ITEM_NAME);
			ItemLore lb = plain.getOrDefault(DataComponents.LORE, ItemLore.EMPTY), la = asc.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
			boolean name = n != null && n.getContents() instanceof TranslatableContents tc && tc.getKey().equals(Ascension.NAME_KEY) && tc.getArgs().length == 1
				&& n.getStyle().getColor() != null && n.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD));
			boolean lore = la.lines().size() == lb.lines().size() + 1 && la.lines().get(la.lines().size() - 1).getContents() instanceof TranslatableContents lt
				&& lt.getKey().equals(Ascension.LORE_KEY);
			return name && lore && Boolean.TRUE.equals(asc.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		});
		checkS(P + "enchantments, damage and a custom name survive ascension", () -> {
			Holder<Enchantment> prot = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
			ItemStack in = new ItemStack(helm);
			in.enchant(prot, 4);
			in.setDamageValue(37);
			in.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
			ItemStack out = Ascension.ascend(in);
			return EnchantmentHelper.getItemEnchantmentLevel(prot, out) == 4 && out.getDamageValue() == 37
				&& "Old Faithful".equals(out.getHoverName().getString()) && Ascension.isAscended(out) && !Ascension.isAscended(in);
		});
		check(P + "an Ascendant piece does not match again (no shard is wasted)", Ascension.isAscended(asc)
			&& s.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, asc, new ItemStack(shard)), l)
				.map(h -> !(h.value() instanceof AscensionRecipe)).orElse(true));
		check(P + "a netherite helmet + shard makes nothing; a template in the slot is not the ascension recipe",
			s.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.NETHERITE_HELMET), new ItemStack(shard)), l).isEmpty()
				&& s.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(helm), new ItemStack(shard)), l)
					.map(h -> !(h.value() instanceof AscensionRecipe)).orElse(true));
		checkS(P + "save/load (NBT) keeps the Ascendant piece exactly", () -> {
			var ops = s.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			Tag t = ItemStack.CODEC.encodeStart(ops, asc).getOrThrow();
			ItemStack back = ItemStack.CODEC.parse(ops, t).getOrThrow();
			return ItemStack.matches(back, asc) && Ascension.isAscended(back);
		});

		// ---- a real smithing table menu, taken by a player
		FakePlayer p = FakePlayer.get(l);
		checkS(P + "smithing menu: result appears, taking it uses one shard and the piece", () -> {
			SmithingMenu m = new SmithingMenu(0, p.getInventory());
			m.getSlot(1).set(new ItemStack(helm));
			m.getSlot(2).set(new ItemStack(shard, 2));
			ItemStack res = m.getSlot(3).getItem().copy();
			boolean shown = res.is(helm) && Ascension.isAscended(res) && m.getSlot(3).mayPickup(p);
			if (shown) m.getSlot(3).onTake(p, res);
			boolean took = m.getSlot(1).getItem().isEmpty() && m.getSlot(2).getItem().getCount() == 1 && m.getSlot(3).getItem().isEmpty();
			m.getSlot(1).set(res); // an Ascendant piece + the last shard: no second result
			boolean again = m.getSlot(3).getItem().isEmpty() && m.getSlot(2).getItem().getCount() == 1;
			for (int i = 0; i < 4; i++) m.getSlot(i).set(ItemStack.EMPTY);
			return shown && took && again;
		});

		// ---- set bonus II
		SetBonus sc = SetBonuses.get("single_combat");
		Item[] gale = {helm, Gear3Items.BY_ID.get("galeplate_chestplate"), Gear3Items.BY_ID.get("galeplate_leggings"), Gear3Items.BY_ID.get("galeplate_boots")};
		if (sc == null || gale[1] == null || gale[2] == null || gale[3] == null) info(P + "Single Combat set not registered here: set bonus II check skipped");
		else checkS(P + "Single Combat: tier 1 with plain Galeplate, still 1 with three Ascendant pieces, 2 with all four", () -> {
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			try {
				for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new ItemStack(gale[i]));
				int t1x = SetBonuses.tier(p, sc);
				for (int i = 0; i < 3; i++) p.setItemSlot(slots[i], Ascension.ascend(new ItemStack(gale[i])));
				int t3 = SetBonuses.tier(p, sc);
				p.setItemSlot(slots[3], Ascension.ascend(new ItemStack(gale[3])));
				int t4 = SetBonuses.tier(p, sc);
				if (t1x != 1 || t3 != 1 || t4 != 2) info(P + "tiers " + t1x + "/" + t3 + "/" + t4);
				return t1x == 1 && t3 == 1 && t4 == 2;
			} finally {
				for (EquipmentSlot e : slots) p.setItemSlot(e, ItemStack.EMPTY);
			}
		});

		// ---- drops
		check(P + "drop rule: Auris, Lithara, Ossyra, Aphelion 1 each; a T4 boss and a zombie 0",
			AscensionSources.shards("elsewhere:auris", Set.of(), false) == 1 && AscensionSources.shards("elsewhere:lithara", Set.of(), false) == 1
				&& AscensionSources.shards("elsewhere:ossyra", Set.of(), false) == 1 && AscensionSources.shards("elsewhere:aphelion", Set.of(), false) == 1
				&& AscensionSources.shards("elsewhere:galvanox", Set.of(), false) == 0 && AscensionSources.shards("minecraft:zombie", Set.of(), false) == 0);
		check(P + "drop rule: the Aldermark King, a King-slot leader and Mirewick's Chair 1; Lords and other Magnates 0",
			AscensionSources.shards("elsewhere:court_king", Set.of(), false) == 1 && AscensionSources.shards("elsewhere:court_marshal", Set.of(), false) == 0
				&& AscensionSources.shards("elsewhere:azmar_sultana", Set.of("kingdom_court_leader:azmar|king"), false) == 1
				&& AscensionSources.shards("elsewhere:azmar_amir", Set.of("kingdom_court_leader:azmar|marshal"), false) == 0
				&& AscensionSources.shards("elsewhere:mirewick_magnate", Set.of("kingdom_court_leader:mirewick|ledger"), true) == 1
				&& AscensionSources.shards("elsewhere:mirewick_magnate", Set.of("kingdom_court_leader:mirewick|ledger"), false) == 0
				&& AscensionSources.shards("minecraft:zombie", Set.of("some_other_tag"), true) == 0);
		checkS(P + "a player's kill of a ruler drops one glowing shard; a kill with no player drops none", () -> {
			String tag = "elsewhere_ascension_selftest";
			AscensionSources.testTag = tag;
			double x = 72.5, y = 120.0, z = 72.5;
			AABB box = new AABB(x - 4, y - 4, z - 4, x + 4, y + 4, z + 4);
			try {
				clearItems(l, box);
				Mob a = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND), b = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND);
				if (a == null || b == null) return false;
				for (Mob m : new Mob[] {a, b}) {
					m.snapTo(x, y, z, 0F, 0F);
					m.setNoAi(true);
					m.setNoGravity(true);
					m.addTag(tag);
					l.addFreshEntity(m);
				}
				a.hurtServer(l, l.damageSources().playerAttack(p), 1000F);
				int byPlayer = shardsNear(l, box, shard);
				boolean glow = !l.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(shard)).isEmpty()
					&& l.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(shard)).stream().allMatch(ItemEntity::hasGlowingTag);
				clearItems(l, box);
				b.hurtServer(l, l.damageSources().genericKill(), 1000F);
				int noPlayer = shardsNear(l, box, shard);
				a.discard();
				b.discard();
				clearItems(l, box);
				if (byPlayer != 1 || noPlayer != 0) info(P + "shards: player kill " + byPlayer + ", no-player kill " + noPlayer);
				return !a.isAlive() && !b.isAlive() && byPlayer == 1 && glow && noPlayer == 0;
			} finally {
				AscensionSources.testTag = null;
			}
		});

		// ---- lang and JEI
		checkS(P + "lang: name, lore1, the Ascendant name pattern and lore line", () -> {
			String[] keys = {"item.elsewhere." + Ascension.ID, "item.elsewhere." + Ascension.ID + ".lore1", Ascension.NAME_KEY, Ascension.LORE_KEY};
			for (String k : keys) if (!langHas(k)) {
				info(P + "missing lang key " + k);
				return false;
			}
			return true;
		});
		checkS(P + "JEI line says where shards come from and what they do", () -> {
			try (InputStream in = AscensionTests.class.getResourceAsStream("/elsewhere_gear3/jei_info.json")) {
				if (in == null) return false;
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonArray lines = o.getAsJsonArray("elsewhere:" + Ascension.ID);
				if (lines == null || lines.isEmpty()) return false;
				String all = lines.toString();
				return all.contains("Aphelion") && all.contains("ruler") && all.contains("smithing") && !all.contains("see the Elsewhere wiki");
			} catch (Exception e) {
				return false;
			}
		});
	}

	private static int shardsNear(ServerLevel l, AABB box, Item shard) {
		l.getEntities((net.minecraft.world.entity.Entity) null, box); // warm-up query (GOTCHAS: first query after addFreshEntity)
		int n = 0;
		for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(shard))) n += ie.getItem().getCount();
		return n;
	}

	private static void clearItems(ServerLevel l, AABB box) {
		for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, box, e -> true)) ie.discard();
	}

	private static boolean langHas(String key) {
		try {
			Enumeration<URL> urls = AscensionTests.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
			while (urls.hasMoreElements()) {
				try (InputStream in = urls.nextElement().openStream()) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					if (o.has(key) && !o.get(key).getAsString().isBlank()) return true;
				}
			}
		} catch (Exception ignored) {
			// fall through
		}
		return false;
	}

}
