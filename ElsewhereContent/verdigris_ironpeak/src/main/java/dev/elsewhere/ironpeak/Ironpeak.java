package dev.elsewhere.ironpeak;

import dev.elsewhere.stub.verdigris_ironpeak.TripTrigger;
import dev.elsewhere.stub.verdigris_ironpeak.Trips;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.Reg;
import dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Ironpeak's rules: the altitude meter and Thin Air (above Y 1100: hunger x1.5, Mining Fatigue I, freezing unless you
 * wear leather or fur or stand near a Hearthstone), clan ranks by altitude and victories, mob tier by altitude,
 * Skyiron mining speed by altitude, Test Meter tie-ins, and the accidental-trip trigger.
 */
public final class Ironpeak {
	public static final ResourceKey<Level> DIM = Fx.IRONPEAK;
	public static final int THIN_AIR_Y = 1100, ALTITUDE_TRAINING_Y = 800;
	public static final int[] RANK_HEIGHTS = {0, 300, 500, 700, 900};
	public static final String[] RANK_TITLES = {"Foothill Wanderer", "Terrace Climber", "Shelf Runner", "Clan Initiate", "Cloud Breaker", "Proven Champion", "Summit King"};
	private static final net.minecraft.resources.Identifier SKYIRON_SPEED = Reg.id("skyiron_altitude");

	public static final AttachmentType<CompoundTag> CLIMBER_DATA = AttachmentRegistry.create(Reg.id("ironpeak_climber"),
		b -> b.persistent(CompoundTag.CODEC).copyOnDeath().initializer(CompoundTag::new));

	/** First time each day you stand above Y 200 in a peaks biome: 15% (DIMENSIONS.md section 9). */
	public static final TripTrigger PEAK = new TripTrigger() {
		public String id() {
			return "ironpeak.peak";
		}

		public ResourceKey<Level> target() {
			return DIM;
		}

		public Kind kind() {
			return Kind.LOCATION;
		}

		public double baseChance(ServerPlayer player) {
			return 0.15;
		}
	};

	/** Mountain Goat: the full Climber set (the Summit Crown counts as the helmet). Fall damage -50%; the wall cling is in EndgameGear. */
	public static final dev.elsewhere.stub.verdigris_ironpeak.SetBonus MOUNTAIN_GOAT = new dev.elsewhere.stub.verdigris_ironpeak.SetBonus("climber", java.util.Map.of(
		EquipmentSlot.HEAD, IronpeakItems.CLIMBER_HELMET, EquipmentSlot.CHEST, IronpeakItems.CLIMBER_CHESTPLATE,
		EquipmentSlot.LEGS, IronpeakItems.CLIMBER_LEGGINGS, EquipmentSlot.FEET, IronpeakItems.CLIMBER_BOOTS)) {
		private final AttributeModifier fall = new AttributeModifier(Reg.id("mountain_goat_fall"), GearStats.MOUNTAIN_GOAT_FALL, AttributeModifier.Operation.ADD_VALUE);

		public void apply(ServerPlayer p) {
			var a = p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
			if (a != null && !a.hasModifier(fall.id())) a.addTransientModifier(fall);
		}

		public void remove(ServerPlayer p) {
			var a = p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
			if (a != null) a.removeModifier(fall.id());
		}

		public java.util.List<Component> tooltip() {
			return java.util.List.of(Component.translatable("set.elsewhere.climber").withStyle(ChatFormatting.GOLD),
				Component.translatable("set.elsewhere.climber.1").withStyle(ChatFormatting.BLUE),
				Component.translatable("set.elsewhere.climber.2").withStyle(ChatFormatting.BLUE));
		}
	}.alternate(EquipmentSlot.HEAD, IronpeakItems.SUMMIT_CROWN);

	private Ironpeak() {}

	public static CompoundTag data(ServerPlayer p) {
		return p.getAttachedOrCreate(CLIMBER_DATA);
	}

	public static int rank(ServerPlayer p) {
		CompoundTag d = data(p);
		if (d.getBooleanOr("thurgrim", false)) return 6;
		if (d.getBooleanOr("hrolf", false)) return 5;
		int maxY = d.getIntOr("max_y", 0), r = 0;
		for (int i = 0; i < RANK_HEIGHTS.length; i++) if (maxY >= RANK_HEIGHTS[i]) r = i;
		return r;
	}

	public static String title(int rank) {
		return RANK_TITLES[Math.max(0, Math.min(RANK_TITLES.length - 1, rank))];
	}

	public static void setVictory(ServerPlayer p, String boss) {
		int before = rank(p);
		data(p).putBoolean(boss, true);
		int after = rank(p);
		if (after > before) p.sendSystemMessage(Component.literal("Clan rank " + after + ": " + title(after)).withStyle(ChatFormatting.GOLD));
	}

	public static boolean inThinAir(Entity e) {
		return e.level().dimension() == DIM && e.getY() > THIN_AIR_Y;
	}

	/** Leather or fur: any leather armor piece, Climber armor, Crampons or the Champion's Belt. */
	public static boolean wearsFur(ServerPlayer p) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.is(ItemTags.FREEZE_IMMUNE_WEARABLES)) return true;
		}
		return false;
	}

	public static boolean nearHearthstone(Level level, BlockPos c) {
		int r = HearthstoneBlock.RADIUS;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 3, r))) {
			if (level.getBlockState(p).is(IronpeakBlocks.HEARTHSTONE)) return true;
		}
		return false;
	}

	public static void serverTick(MinecraftServer server) {
		int t = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			IceAxeItem.tickCling(p);
			boolean here = p.level().dimension() == DIM;
			if (here && inThinAir(p) && !p.isCreative() && !p.isSpectator()) {
				boolean warm = wearsFur(p) || (p.getId() + t) % 20 != 0 && data(p).getBooleanOr("warm", false);
				if ((p.getId() + t) % 20 == 0) {
					warm = wearsFur(p) || nearHearthstone(p.level(), p.blockPosition());
					data(p).putBoolean("warm", warm);
					if (!IronpeakItems.thinAirProof(p.getItemBySlot(EquipmentSlot.HEAD))) p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, true, false, true));
				}
				if (!warm) p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 3));
			}
			if ((p.getId() + t) % 20 != 0) continue;
			// Once a second.
			CompoundTag d = data(p);
			if (here) {
				int y = p.getBlockY();
				int oldRank = rank(p);
				if (y > d.getIntOr("max_y", 0)) d.putInt("max_y", y);
				int newRank = rank(p);
				if (newRank > oldRank) p.sendSystemMessage(Component.literal("Clan rank " + newRank + ": " + title(newRank)).withStyle(ChatFormatting.GOLD));
				if (y > ALTITUDE_TRAINING_Y) {
					double minutes = d.getDoubleOr("altitude_minutes", 0) + 1 / 60.0;
					d.putDouble("altitude_minutes", minutes);
					TestMeterBridge.lifestyle(p, 40 * (1 - Math.exp(-minutes / 10)));
				}
			} else if (d.getDoubleOr("altitude_minutes", 0) > 0) {
				d.putDouble("altitude_minutes", Math.max(0, d.getDoubleOr("altitude_minutes", 0) - 1 / 120.0));
			}
			updateSkyironSpeed(p);
			SummitRace.tick(p);
			if (p.getItemBySlot(EquipmentSlot.LEGS).is(IronpeakItems.CHAMPIONS_BELT)) TestMeterBridge.lifestyle(p, GearStats.BELT_TEST_METER_NGDL);
			if (p.getItemBySlot(EquipmentSlot.HEAD).is(IronpeakItems.SUMMIT_CROWN)) TestMeterBridge.lifestyleFraction(p, GearStats.CROWN_NATURAL_BONUS);
			if ((p.getId() + t) % 100 == 0 && p.level().dimension() == Level.OVERWORLD && p.getY() > 200 && isPeaks(p.level().getBiome(p.blockPosition()))) {
				long day = p.level().getOverworldClockTime() / 24000L;
				if (d.getLongOr("peak_day", -1) != day) {
					d.putLong("peak_day", day);
					Trips.fire(p, PEAK);
				}
			}
		}
	}

	static boolean isPeaks(Holder<Biome> biome) {
		return biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.STONY_PEAKS);
	}

	/** Skyiron tools mine faster the higher you are. */
	public static double skyironMultiplier(double y) {
		double f = (y - GearStats.SKYIRON_LOW_Y) / (GearStats.SKYIRON_HIGH_Y - GearStats.SKYIRON_LOW_Y);
		return 1.0 + Math.max(0, Math.min(1, f)) * (GearStats.SKYIRON_MAX - 1.0);
	}

	private static void updateSkyironSpeed(ServerPlayer p) {
		var attr = p.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		if (attr == null) return;
		Item held = p.getMainHandItem().getItem();
		double m = IronpeakItems.isSkyironTool(held) ? skyironMultiplier(p.getY()) : 1.0; // SKYIRON_MAX = 1.0 in 2.0: Quarry replaced altitude speed
		AttributeModifier current = attr.getModifier(SKYIRON_SPEED);
		if (m <= 1.0001) {
			if (current != null) attr.removeModifier(SKYIRON_SPEED);
		} else if (current == null || Math.abs(current.amount() - (m - 1)) > 0.01) {
			attr.removeModifier(SKYIRON_SPEED);
			attr.addTransientModifier(new AttributeModifier(SKYIRON_SPEED, m - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Mob tier scales with altitude: hostile mobs that spawn higher get up to +100% health and +50% damage at Y 1400. */
	public static void onMobLoad(Entity e, ServerLevel level) {
		if (level.dimension() != DIM || !(e instanceof Mob mob) || !(e instanceof Enemy) || e instanceof dev.elsewhere.stub.verdigris_ironpeak.StubBoss) return;
		double f = Math.max(0, Math.min(1, (e.getY() - 300) / 1100.0));
		if (f <= 0) return;
		var hp = mob.getAttribute(Attributes.MAX_HEALTH);
		var dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		net.minecraft.resources.Identifier id = Reg.id("ironpeak_altitude_tier");
		if (hp != null && !hp.hasModifier(id)) {
			hp.addPermanentModifier(new AttributeModifier(id, f, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			mob.setHealth(mob.getMaxHealth());
		}
		if (dmg != null && !dmg.hasModifier(id)) dmg.addPermanentModifier(new AttributeModifier(id, f * 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	/** Yak Butter Tea ritual: a bowl on a lit campfire in Ironpeak while carrying Yak Milk. */
	public static boolean brewTea(ServerPlayer p, ItemStack bowl) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(IronpeakItems.YAK_MILK)) {
				inv.setItem(i, new ItemStack(Items.BUCKET));
				bowl.shrink(1);
				inv.placeItemBackInInventory(new ItemStack(IronpeakItems.YAK_BUTTER_TEA), net.minecraft.util.Prediction.SERVER_ONLY);
				return true;
			}
		}
		p.sendOverlayMessage(Component.literal("You need Yak Milk to churn butter tea."));
		return false;
	}
}
