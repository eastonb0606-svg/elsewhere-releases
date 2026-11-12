package dev.elsewhere.kingdom.item;

import dev.elsewhere.kingdom.KingdomContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.jspecify.annotations.Nullable;

/**
 * Kingdom items (base module, 0.2.0). Ids are fixed; other lanes reference them by id and never register them again:
 * {@code elsewhere:aldermark_crown} (currency), {@code bodkin_bolt}, {@code whetstone}, {@code fletchers_bundle},
 * {@code sergeants_horn}, {@code oathkeepers_seal}, {@code aldermark_armor_trim_smithing_template},
 * {@code aldermark_banner_pattern}. Trim pattern and banner pattern {@code elsewhere:aldermark} are data-driven.
 */
public final class KingdomItems {
	private KingdomItems() {}

	public static Item ALDERMARK_CROWN, BODKIN_BOLT, WHETSTONE, FLETCHERS_BUNDLE, SERGEANTS_HORN, OATHKEEPERS_SEAL, ALDERMARK_TRIM_TEMPLATE, ALDERMARK_BANNER_PATTERN;
	public static final List<Item> ALL = new ArrayList<>();

	public static final ResourceKey<TrimPattern> ALDERMARK_TRIM = ResourceKey.create(Registries.TRIM_PATTERN, KingdomContent.id("aldermark"));
	public static final ResourceKey<BannerPattern> ALDERMARK_PATTERN = ResourceKey.create(Registries.BANNER_PATTERN, KingdomContent.id("aldermark"));
	public static final TagKey<BannerPattern> PATTERN_ITEM_TAG = TagKey.create(Registries.BANNER_PATTERN, KingdomContent.id("pattern_item/aldermark"));

	/** Sergeant's Horn: Strength I for 8 s to allies within 12 blocks, 90 s cooldown. */
	public static final int HORN_STRENGTH_TICKS = 160, HORN_COOLDOWN_TICKS = 1800;
	public static final double HORN_RADIUS = 12.0;
	/** Oathkeeper's Seal: stay at 3 hearts with Resistance II 2 s; shares the 30 s death-protection lockout. */
	public static final float SEAL_HEALTH = 6F;
	public static final int SEAL_RESIST_TICKS = 40, LOCKOUT_TICKS = 600;

	private static <T extends Item> T reg(String path, Function<Item.Properties, T> f, Item.Properties p) {
		ResourceKey<Item> k = ResourceKey.create(Registries.ITEM, KingdomContent.id(path));
		T item = Registry.register(BuiltInRegistries.ITEM, k, f.apply(p.setId(k)));
		ALL.add(item);
		return item;
	}

	public static void init() {
		if (ALDERMARK_CROWN != null) return;
		ALDERMARK_CROWN = reg("aldermark_crown", TooltipItem::new, new Item.Properties());
		BODKIN_BOLT = reg("bodkin_bolt", BodkinBoltItem::new, new Item.Properties());
		WHETSTONE = reg("whetstone", WhetstoneItem::new, new Item.Properties().stacksTo(16));
		FLETCHERS_BUNDLE = reg("fletchers_bundle", FletchersBundleItem::new, new Item.Properties().stacksTo(16));
		SERGEANTS_HORN = reg("sergeants_horn", SergeantsHornItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		OATHKEEPERS_SEAL = reg("oathkeepers_seal", TooltipItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		ALDERMARK_TRIM_TEMPLATE = reg("aldermark_armor_trim_smithing_template", SmithingTemplateItem::createArmorTrimTemplate, new Item.Properties().rarity(Rarity.UNCOMMON));
		ALDERMARK_BANNER_PATTERN = reg("aldermark_banner_pattern", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, ctx -> ctx.lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(PATTERN_ITEM_TAG)));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayer p) || !trySeal(p, source));
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
				int used = p.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
				Integer last = TOTEMS_USED.put(p.getUUID(), used);
				if (last != null && used > last) markDeathProtection(p);
			}
		});
	}

	// ---- shared death-protection lockout (BALANCE G4) ----------------------------------------------------------

	private static final Map<UUID, Long> LAST_PROTECTION = new HashMap<>();
	private static final Map<UUID, Integer> TOTEMS_USED = new HashMap<>();
	/** Optional extra lockout source (e.g. the core's helper): return true when the player is locked out. */
	public static @Nullable Predicate<ServerPlayer> lockoutHook;

	/** Records that a death protection (totem, Seal, an Elsewhere extra-life item) just saved this player. */
	public static void markDeathProtection(ServerPlayer p) {
		LAST_PROTECTION.put(p.getUUID(), p.level().getGameTime());
	}

	/** True when any death protection saved this player in the last 30 s (vanilla totems are seen through their use stat). */
	public static boolean isDeathProtectionLocked(ServerPlayer p) {
		Long t = LAST_PROTECTION.get(p.getUUID());
		if (t != null && p.level().getGameTime() - t < LOCKOUT_TICKS && p.level().getGameTime() >= t) return true;
		return lockoutHook != null && lockoutHook.test(p);
	}

	/** Oathkeeper's Seal in either hand: instead of dying, stay at 3 hearts with Resistance II for 2 s. True when it fired. */
	public static boolean trySeal(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
		ItemStack seal = null;
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack s = p.getItemInHand(h);
			if (s.has(DataComponents.DEATH_PROTECTION)) return false; // a real totem-like item handles it
			if (seal == null && s.is(OATHKEEPERS_SEAL)) seal = s;
		}
		if (seal == null || isDeathProtectionLocked(p)) return false;
		seal.shrink(1);
		p.setHealth(SEAL_HEALTH);
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SEAL_RESIST_TICKS, 1));
		markDeathProtection(p);
		ServerLevel l = p.level();
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
		l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY(1.0), p.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
		p.sendOverlayMessage(Component.translatable("message.elsewhere.kingdom.seal").withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ---- heraldry --------------------------------------------------------------------------------------------------

	/** Aldermark heraldry on a royal-blue field: a red chief and the white tower under a crown (if the pattern is loaded). */
	public static BannerPatternLayers aldermarkLayers(HolderGetter<BannerPattern> patterns) {
		return new BannerPatternLayers.Builder()
			.addIfRegistered(patterns, BannerPatterns.STRIPE_TOP, DyeColor.RED)
			.addIfRegistered(patterns, ALDERMARK_PATTERN, DyeColor.WHITE)
			.build();
	}

	/** Paints a shield or banner stack with the Aldermark heraldry (shields also get the blue base colour). */
	public static ItemStack applyHeraldry(ItemStack stack, HolderGetter<BannerPattern> patterns) {
		if (stack.is(Items.SHIELD)) stack.set(DataComponents.BASE_COLOR, DyeColor.BLUE);
		stack.set(DataComponents.BANNER_PATTERNS, aldermarkLayers(patterns));
		return stack;
	}

	/** An Aldermark shield (cosmetic: a vanilla shield with the kingdom banner). */
	public static ItemStack aldermarkShield(HolderGetter<BannerPattern> patterns) {
		return applyHeraldry(new ItemStack(Items.SHIELD), patterns);
	}

	/** Plain item with a one-line grey tooltip {@code item.<ns>.<id>.tooltip}. */
	public static class TooltipItem extends Item {
		public TooltipItem(Properties p) {
			super(p);
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, java.util.function.Consumer<Component> out, TooltipFlag flag) {
			out.accept(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
		}
	}
}
