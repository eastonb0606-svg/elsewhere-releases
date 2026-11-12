package dev.elsewhere.gear3.s3.miresnare;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mire Snare self-test block (s-miresnare), called from the gear3 self-test. Synchronous, on a FakePlayer, in its own corner of
 * the arena (x 24..31, z 72): registration, placing from the item, who springs it, the spring itself (Snared 3 s, speed 0, 6 damage
 * of the mire_snare type), single use, the fall-apart tick, loot, kill credit, waterlogging, losing the floor, resources and lang.
 * Everything it builds or spawns is removed at the end.
 */
public final class MireSnareSelfTest {
	private MireSnareSelfTest() {}

	static final String P = "Mire Snare: ";

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.fromString("3e5a7c1d-2b4f-4a6e-8c0d-5a7e1c3f0001"), "mire_snare_selftest"));
		BlockPos at = new BlockPos(24, 100, 72), wet = at.east(3), drop = at.east(6);
		List<BlockPos> built = new ArrayList<>();
		List<Entity> spawned = new ArrayList<>();
		Item item = Gear3Items.BY_ID.get(MireSnare.ID);
		try {
			check(P + "block, block entity, Snared effect and mire_snare damage type registered; the catalogue item is its block item, stack 8",
				MireSnare.BLOCK != null && BuiltInRegistries.BLOCK.getValue(Gear3Content.id(MireSnare.ID)) == MireSnare.BLOCK
					&& BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Gear3Content.id(MireSnare.ID)) == MireSnare.BLOCK_ENTITY
					&& BuiltInRegistries.MOB_EFFECT.containsKey(Gear3Content.id(MireSnare.EFFECT_ID))
					&& l.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(MireSnare.DAMAGE_TYPE).isPresent()
					&& item instanceof MireSnareItem bi && bi.getBlock() == MireSnare.BLOCK && MireSnare.BLOCK.asItem() == item
					&& new ItemStack(item).getMaxStackSize() == 8, item);
			if (!(item instanceof MireSnareItem)) return;
			check(P + "kill switch mire_snare is on by default", MireSnare.ENABLED);

			for (BlockPos f : new BlockPos[] {at.below(), drop.below(), wet.below(), wet.north(), wet.south(), wet.east(), wet.west()}) {
				l.setBlock(f, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				built.add(f);
			}
			built.add(at);
			built.add(wet);
			built.add(drop);
			checkS(P + "placed from the item on a floor (one used up), placer recorded in the block entity; no floor, no snare", () -> {
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
				InteractionResult r = item.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP,
					at.below(), false)));
				BlockState bs = l.getBlockState(at);
				return r.consumesAction() && bs.is(MireSnare.BLOCK) && !bs.getValue(MireSnareBlock.SPRUNG) && !bs.getValue(MireSnareBlock.WATERLOGGED) && p.getMainHandItem().getCount() == 1
					&& l.getBlockEntity(at) instanceof MireSnareBlockEntity be && p.getUUID().equals(be.owner())
					&& !MireSnare.BLOCK.defaultBlockState().canSurvive(l, at.above(4));
			});

			LivingEntity villager = EntityTypes.VILLAGER.create(l, EntitySpawnReason.COMMAND), golem = EntityTypes.IRON_GOLEM.create(l, EntitySpawnReason.COMMAND);
			LivingEntity wither = EntityTypes.WITHER.create(l, EntitySpawnReason.COMMAND), stand = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			LivingEntity husk0 = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND), pig0 = EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND);
			Wolf pet = EntityTypes.WOLF.create(l, EntitySpawnReason.COMMAND);
			pet.tame(p);
			check(P + "never springs for players, villagers, golems, armor stands, tamed pets or bosses; does for monsters and game",
				!MireSnare.springsFor(p) && !MireSnare.springsFor(villager) && !MireSnare.springsFor(golem) && !MireSnare.springsFor(stand) && !MireSnare.springsFor(pet)
					&& !MireSnare.springsFor(wither) && MireSnare.springsFor(husk0) && MireSnare.springsFor(pig0));

			p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			l.getBlockState(at).entityInside(l, at, p, InsideBlockEffectApplier.NOOP, true);
			check(P + "a player standing in it leaves it armed", l.getBlockState(at).is(MireSnare.BLOCK) && !l.getBlockState(at).getValue(MireSnareBlock.SPRUNG));
			p.snapTo(40.5, 100, 40.5, 0, 0);

			Mob husk = mob(l, EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND), at, spawned);
			husk.getAttribute(Attributes.ARMOR).setBaseValue(0); // a husk's natural 2 armor would shave the 6 to 5.9
			float hp = husk.getHealth();
			l.getBlockState(at).entityInside(l, at, husk, InsideBlockEffectApplier.NOOP, true);
			MobEffectInstance snared = husk.getEffect(MireSnare.SNARED);
			check(P + "a husk stepping in is Snared for 3 s (move, jump and fly speed 0) and takes 6 mire_snare damage",
				snared != null && snared.getDuration() == MireSnare.ROOT_TICKS && near(husk.getAttributeValue(Attributes.MOVEMENT_SPEED), 0)
					&& near(husk.getAttributeValue(Attributes.JUMP_STRENGTH), 0) && near(hp - husk.getHealth(), MireSnare.DAMAGE)
					&& husk.getLastDamageSource() != null && husk.getLastDamageSource().is(MireSnare.DAMAGE_TYPE),
				"hp " + hp + " -> " + husk.getHealth() + ", speed " + husk.getAttributeValue(Attributes.MOVEMENT_SPEED));

			Mob pig = mob(l, EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND), at, spawned);
			float php = pig.getHealth();
			BlockState shut = l.getBlockState(at);
			shut.entityInside(l, at, pig, InsideBlockEffectApplier.NOOP, true);
			check(P + "single use: sprung, its 3 s fall-apart tick is queued, and the next mob walks through unharmed",
				shut.is(MireSnare.BLOCK) && shut.getValue(MireSnareBlock.SPRUNG) && l.getBlockTicks().hasScheduledTick(at, MireSnare.BLOCK) && near(pig.getHealth(), php)
					&& !pig.hasEffect(MireSnare.SNARED));
			l.getBlockState(at).tick(l, at, l.getRandom());
			check(P + "falls apart when the root ends", l.getBlockState(at).isAir(), l.getBlockState(at));

			checkS(P + "loot: an armed snare drops itself, a sprung one nothing", () -> {
				List<ItemStack> armed = Block.getDrops(MireSnare.BLOCK.defaultBlockState(), l, drop, null);
				List<ItemStack> sprung = Block.getDrops(MireSnare.BLOCK.defaultBlockState().setValue(MireSnareBlock.SPRUNG, true), l, drop, null);
				return armed.size() == 1 && armed.get(0).is(item) && armed.get(0).getCount() == 1 && sprung.isEmpty();
			});

			l.setBlock(drop, MireSnare.BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			Mob game = mob(l, EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND), drop, spawned);
			float ghp = game.getHealth();
			MireSnare.spring(l, drop, l.getBlockState(drop), game, p);
			check(P + "the placer gets the kill credit (the damage source names them)", game.getLastDamageSource() != null && game.getLastDamageSource().getEntity() == p
				&& near(ghp - game.getHealth(), MireSnare.DAMAGE));
			l.setBlock(drop, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

			checkS(P + "placed in shallow water it is waterlogged, and the water stays when it falls apart", () -> {
				l.setBlock(wet, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
				item.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(wet.below()).add(0, 0.5, 0), Direction.UP, wet.below(), false)));
				BlockState ws = l.getBlockState(wet);
				if (!ws.is(MireSnare.BLOCK) || !ws.getValue(MireSnareBlock.WATERLOGGED) || l.getFluidState(wet).getType() != Fluids.WATER) return false;
				Mob frog = mob(l, EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND), wet, spawned);
				MireSnare.spring(l, wet, ws, frog, null);
				l.getBlockState(wet).tick(l, wet, l.getRandom());
				return l.getBlockState(wet).is(Blocks.WATER) && l.getFluidState(wet).isSource();
			});

			checkS(P + "an armed snare pops off when its floor goes", () -> {
				l.setBlock(drop, MireSnare.BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				l.setBlock(drop.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				return l.getBlockState(drop).isAir();
			});

			checkS(P + "blockstate, armed and sprung models, block textures, Snared icon, loot table and damage type present", () -> {
				JsonObject bs = json("assets/elsewhere/blockstates/" + MireSnare.ID + ".json");
				if (bs == null) return false;
				String v = bs.toString();
				if (!v.contains("elsewhere:block/" + MireSnare.ID + "\"") || !v.contains("elsewhere:block/" + MireSnare.ID + "_sprung")) return false;
				for (String m : new String[] {MireSnare.ID, MireSnare.ID + "_sprung"}) {
					JsonObject model = json("assets/elsewhere/models/block/" + m + ".json");
					if (model == null || !model.has("elements") || model.getAsJsonArray("elements").size() < 4) return false;
					for (var t : model.getAsJsonObject("textures").entrySet()) {
						String ref = t.getValue().getAsString();
						if (!ref.startsWith("elsewhere:block/") || !png("assets/elsewhere/textures/block/" + ref.substring("elsewhere:block/".length()) + ".png", 16)) return false;
					}
				}
				return png("assets/elsewhere/textures/mob_effect/" + MireSnare.EFFECT_ID + ".png", 18) && res("data/elsewhere/loot_table/blocks/" + MireSnare.ID + ".json")
					&& res("data/elsewhere/damage_type/mire_snare.json");
			});
			checkS(P + "lang: block, Snared effect and both death messages", () -> {
				JsonObject lang = mergedJson("assets/elsewhere/lang/en_us.json");
				for (String k : new String[] {"block.elsewhere." + MireSnare.ID, "effect.elsewhere." + MireSnare.EFFECT_ID, "death.attack.elsewhere.mire_snare",
					"death.attack.elsewhere.mire_snare.player"})
					if (!lang.has(k)) return false;
				return true;
			});

			JsonObject trade = mergedJson("data/elsewhere/kingdom_trade/mirewick.json");
			if (!trade.has("lines")) info(P + "Mirewick's trade stock is not in this environment (rival_mirewick); its 6 Scrip line is checked in the merged jar");
			else checkS(P + "Mirewick's market sells it for 6 Scrip", () -> {
				for (JsonElement e : trade.getAsJsonArray("lines")) {
					JsonObject o = e.getAsJsonObject();
					if (("elsewhere:" + MireSnare.ID).equals(o.get("item").getAsString())) return o.get("price").getAsInt() == 6;
				}
				return false;
			});
		} finally {
			for (Entity e : spawned) e.discard();
			for (BlockPos b : built) l.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(10))) e.discard();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
	}

	static Mob mob(ServerLevel l, Mob m, BlockPos at, List<Entity> spawned) {
		m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		l.addFreshEntity(m);
		spawned.add(m);
		return m;
	}

	static boolean res(String path) {
		return MireSnare.class.getResource("/" + path) != null;
	}

	static boolean png(String path, int size) {
		try (InputStream in = MireSnare.class.getResourceAsStream("/" + path)) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			return img != null && img.getWidth() == size && img.getHeight() == size;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	static JsonObject json(String path) {
		try (InputStream in = MireSnare.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** Every copy of a resource on the classpath, merged (the merged Elsewhere jar and each devlib can carry one). */
	static JsonObject mergedJson(String path) {
		JsonObject out = new JsonObject();
		try {
			for (URL u : Collections.list(MireSnare.class.getClassLoader().getResources(path))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
						if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			// unreadable copies are skipped; the checks then fail on the missing keys
		}
		return out;
	}
}
