package dev.elsewhere.gear3.s3.pennant;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.summoner.SummonerEntities;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Herdcaller's Pennant self-test block (called from the gear3 self-test). Synchronous, on its own FakePlayer and a small stone
 * pad in the sky (x -70..-58, y 199, z -70..-58; every block put back afterwards): where a standard may stand, planting by use,
 * the cooldown, one standard per player, the aura on a real Rootling through the summoner minion list (heal 1 x D, speed +10% x D,
 * in and out of range, the radius hook, Command +8), losing its ground, the full 30 s lifetime through the shared scheduler,
 * the inert entity, and the assets. Leaves no standard, no queued pulse, no minion and no modifier behind.
 */
public final class PennantSelfTest {
	private PennantSelfTest() {}

	static final String P = "Herdcaller's Pennant: ";
	static final int X0 = -70, X1 = -58, Z0 = -70, Z1 = -58, Y = 200;
	static final BlockPos BASE = new BlockPos(-64, Y, -64), BASE2 = new BlockPos(-61, Y, -64);
	static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item item = Pennant.item();
		check(P + "catalogue item registered as a PennantItem (mechanic 'pennant')", item instanceof PennantItem, item);
		check(P + "kill switch 'herdcaller_pennant' is on by default", Pennant.ENABLED);
		check(P + "standard entity elsewhere:herdcaller_standard registered: never saved, not summonable, fire immune, no loot",
			Pennant.STANDARD != null && BuiltInRegistries.ENTITY_TYPE.getKey(Pennant.STANDARD).equals(Gear3Content.id(Pennant.STANDARD_ID))
				&& !Pennant.STANDARD.canSerialize() && !Pennant.STANDARD.canSummon() && Pennant.STANDARD.fireImmune() && Pennant.STANDARD.getDefaultLootTable().isEmpty());
		check(P + "in the Summoner calling tag (calling_weapon/summoner): bonus x D(Summoner)",
			item != null && new ItemStack(item).is(Gear3Items.itemTag("elsewhere:calling_weapon/summoner")));
		check(P + "GEAR.md numbers: radius 12, 1 HP/s, +10% speed, 30 s, 60 s cooldown", Pennant.BASE_RADIUS == 12.0 && Pennant.HEAL_PER_SECOND == 1F
			&& Pennant.SPEED_BONUS == 0.10 && Pennant.LIFETIME_TICKS == 600 && Pennant.COOLDOWN_TICKS == 1200);
		assets();
		if (!(item instanceof PennantItem pi) || !Pennant.ENABLED || Pennant.STANDARD == null) return;

		int pend0 = Gear3Scheduler.pending(), active0 = Pennant.activeCount();
		if (pend0 != 0) info(P + pend0 + " scheduler task(s) were already queued by earlier tests; the lifetime check runs only this standard's");
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_pennant".getBytes(StandardCharsets.UTF_8)), "gear3_pennant"));
		p.snapTo(-63.5, Y, -61.5, 180F, 0F);
		Function<UUID, ServerPlayer> lookup0 = MinionEntity.testOwnerLookup;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : lookup0.apply(u);
		boolean free0 = GlimmerStub.testFree, deny0 = GlimmerStub.devDeny;
		Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
		ToDoubleFunction<Player> plus8 = pl -> pl == p ? 8.0 : 0.0;
		ItemStack st = new ItemStack(item);
		try {
			GlimmerStub.testFree = true;
			GlimmerStub.devDeny = false;
			MinionManager.dismissAll(p.getUUID());
			arena(l, saved);

			// ---- where a standard may stand
			check(P + "plants on a solid top with 3 blocks of room", Pennant.canPlantAt(l, BASE));
			check(P + "refuses mid-air (nothing floats)", !Pennant.canPlantAt(l, BASE.above(3)));
			set(l, BASE.above(2), Blocks.STONE.defaultBlockState());
			check(P + "refuses a spot without 3 blocks of room (nothing sticks out of a ceiling)", !Pennant.canPlantAt(l, BASE));
			set(l, BASE.above(2), Blocks.AIR.defaultBlockState());
			set(l, BASE, Blocks.SHORT_GRASS.defaultBlockState());
			check(P + "a click on grass plants in the grass, a click on a block's top plants above it",
				Pennant.target(l, BASE, Direction.UP).equals(BASE) && Pennant.target(l, BASE.below(), Direction.UP).equals(BASE) && Pennant.canPlantAt(l, BASE));
			set(l, BASE, Blocks.AIR.defaultBlockState());

			// ---- planting by use: on the stone top, cooldown 60 s
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			UseOnContext ctx = new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(BASE.below()).add(0, 0.5, 0), Direction.UP, BASE.below(), false));
			Pennant.Standard a;
			if (p.mayUseItemAt(BASE, Direction.UP, st)) {
				InteractionResult r = pi.useOn(ctx);
				a = Pennant.active(p.getUUID());
				check(P + "use on the ground plants the standard there, facing the planter; the pennant is kept and cools down 60 s",
					r.consumesAction() && a != null && a.base.equals(BASE) && !a.entity.isRemoved() && a.entity.getYRot() == p.getYRot()
						&& p.getCooldowns().isOnCooldown(st) && !st.isEmpty(), r + " active=" + a);
			} else {
				info(P + "the test pad is under spawn protection here; planting through PennantItem.plant instead of useOn");
				InteractionResult r = pi.plant(l, p, st, BASE);
				a = Pennant.active(p.getUUID());
				check(P + "planting plants the standard there; the pennant is kept and cools down 60 s",
					r.consumesAction() && a != null && a.base.equals(BASE) && !a.entity.isRemoved() && p.getCooldowns().isOnCooldown(st) && !st.isEmpty(), r);
			}
			if (a == null) return;
			check(P + "the standard stands on the block, inside the pad", near(a.entity.getY(), Y) && near(a.entity.getX(), BASE.getX() + 0.5) && near(a.entity.getZ(), BASE.getZ() + 0.5));
			check(P + "while cooling down the pennant plants nothing", pi.plant(l, p, st, BASE2) == InteractionResult.FAIL && Pennant.active(p.getUUID()) == a);

			// ---- the aura on a real minion (summoner minion list)
			MinionEntity m = MinionManager.summon(p, SummonerEntities.ROOTLING, 0);
			check(P + "a Rootling answers for the aura checks", m != null && MinionManager.minions(p).contains(m));
			if (m == null) return;
			float mult = Pennant.multiplier(p);
			m.snapTo(a.pos.x + 3, Y, a.pos.z, 0F, 0F);
			m.setHealth(m.getMaxHealth() - 5F);
			float h0 = m.getHealth();
			int n = a.pulse(true);
			check(P + "D(Summoner) is read at planting (" + String.format("%.2f", mult) + ", 0.90..1.15)", near(a.mult, mult) && mult >= 0.899F && mult <= 1.151F, a.mult);
			check(P + "a minion within 12 heals 1 x D HP per pulse", n == 1 && near(m.getHealth(), h0 + 1F * mult), h0 + " -> " + m.getHealth());
			AttributeModifier mod = m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD);
			check(P + "a minion within 12 moves +10% x D faster (one transient modifier)", mod != null && near(mod.amount(), 0.10 * mult)
				&& mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE, mod);
			m.setHealth(m.getMaxHealth());
			a.pulse(true);
			check(P + "a minion at full health stays at full health", near(m.getHealth(), m.getMaxHealth()));
			m.snapTo(a.pos.x + 14, Y, a.pos.z, 0F, 0F);
			check(P + "beyond 12 blocks: no aura, the speed modifier comes off", a.pulse(true) == 0 && m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) == null);

			// ---- radius hooks: the Marshal's Panoply "Command" +8 is re-read each pulse
			Pennant.addRadiusBonus(plus8);
			int in20 = a.pulse(false);
			check(P + "a radius hook (+8, the Command set bonus) reaches 20 blocks at the next pulse", in20 == 1 && near(a.radius(), 20) && near(a.entity.radius(), 20)
				&& m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) != null, a.radius());
			Pennant.removeRadiusBonus(plus8);
			check(P + "the hook off: back to 12 at the next pulse", a.pulse(false) == 0 && near(a.radius(), 12) && m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) == null);
			check(P + "Command bonus is wired (0 without the Marshal's Panoply)", near(Pennant.commandBonus(p), 0.0));

			// ---- one standard per player
			m.snapTo(a.pos.x + 2, Y, a.pos.z + 2, 0F, 0F);
			a.pulse(false);
			Pennant.Standard b = Pennant.plant(p, l, BASE2, 90F);
			check(P + "planting again takes the old standard down (one per player)", b != null && a.ended() && a.entity.isRemoved() && Pennant.active(p.getUUID()) == b
				&& !b.entity.isRemoved() && Pennant.activeCount() == active0 + 1);
			check(P + "the new standard's aura carries on at once (speed on, no double modifier)",
				m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) != null && m.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().filter(x -> x.id().equals(Pennant.SPEED_MOD)).count() == 1);

			// ---- the ground goes: the standard comes down at its next pulse
			BlockPos under = BASE2.below();
			set(l, under, Blocks.AIR.defaultBlockState());
			Pennant.second(b);
			check(P + "breaking the block under it uproots it at the next pulse (speed off, entity gone)", b.ended() && b.entity.isRemoved() && Pennant.active(p.getUUID()) == null
				&& m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) == null);
			set(l, under, Blocks.STONE.defaultBlockState());
			if (pend0 == 0) Gear3Scheduler.runDueWithin(s, Pennant.PULSE_TICKS); // the ended standards' last timers (no-ops)

			// ---- the full 30 s through the shared scheduler
			Pennant.Standard c = Pennant.plant(p, l, BASE, 0F);
			check(P + "planted for the lifetime check", c != null);
			if (c == null) return;
			m.setHealth(1F);
			if (pend0 == 0) {
				int ran = Gear3Scheduler.runDueWithin(s, Pennant.PULSE_TICKS);
				check(P + "30 one-second pulses through Gear3Scheduler, then it ends by itself (entity gone, speed off, nothing queued)",
					ran == Pennant.PULSES && c.pulses() == Pennant.PULSES && c.ended() && c.entity.isRemoved() && Pennant.active(p.getUUID()) == null
						&& m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) == null && Gear3Scheduler.pending() == 0, "ran=" + ran + " pulses=" + c.pulses());
				check(P + "a hurt minion beside it heals over the 30 s", m.getHealth() >= Math.min(m.getMaxHealth(), 1F + 29F * mult) - 0.01F, m.getHealth());
			} else {
				for (int i = 0; i < Pennant.PULSES && !c.ended(); i++) Pennant.second(c);
				check(P + "30 one-second pulses, then it ends by itself (entity gone, speed off)", c.pulses() == Pennant.PULSES && c.ended() && c.entity.isRemoved()
					&& Pennant.active(p.getUUID()) == null && m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Pennant.SPEED_MOD) == null, c.pulses());
			}

			// ---- inert scenery
			HerdcallerStandard e = Pennant.STANDARD.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			check(P + "the standard cannot be hit, picked, pushed, ridden or blown up, and is never saved", e != null && !e.hurtServer(l, l.damageSources().generic(), 10F)
				&& !e.isAttackable() && !e.isPickable() && !e.isPushable() && e.getPistonPushReaction() == PushReaction.IGNORE_ENTITY && e.isIgnoringBlockTriggers()
				&& !e.shouldBeSaved() && !e.canUsePortal(false) && e.noPhysics);
			if (e != null) e.discard();
		} finally {
			Pennant.removeRadiusBonus(plus8);
			Pennant.Standard left = Pennant.active(p.getUUID());
			if (left != null) left.end(false);
			if (pend0 == 0) Gear3Scheduler.runDueWithin(s, Pennant.PULSE_TICKS);
			MinionManager.dismissAll(p.getUUID());
			for (Map.Entry<BlockPos, BlockState> en : saved.entrySet()) l.setBlock(en.getKey(), en.getValue(), QUIET);
			GlimmerStub.testFree = free0;
			GlimmerStub.devDeny = deny0;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionEntity.testOwnerLookup = lookup0;
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(st));
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		check(P + "leaves nothing behind: no standard, no queued pulse", Pennant.activeCount() == active0 && Gear3Scheduler.pending() == pend0,
			"active=" + Pennant.activeCount() + " pending=" + Gear3Scheduler.pending());
		info(P + "done");
	}

	/** Stone pad at y 199 (x/z -70..-58) with 4 blocks of air above; the old blocks are saved for the clean-up. */
	private static void arena(ServerLevel l, Map<BlockPos, BlockState> saved) {
		for (int x = X0; x <= X1; x++)
			for (int z = Z0; z <= Z1; z++)
				for (int y = Y - 1; y <= Y + 3; y++) {
					BlockPos at = new BlockPos(x, y, z);
					saved.put(at, l.getBlockState(at));
					l.setBlock(at, y == Y - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), QUIET);
				}
	}

	private static void set(ServerLevel l, BlockPos at, BlockState st) {
		l.setBlock(at, st, QUIET);
	}

	private static void assets() {
		checkS(P + "standard texture: 64x64, painted flag, pole and resin finial", () -> {
			try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/entity/herdcaller_standard.png")) {
				if (in == null) return false;
				var img = ImageIO.read(in);
				return img != null && img.getWidth() == 64 && img.getHeight() == 64 && (img.getRGB(8, 10) >>> 24) != 0 && (img.getRGB(58, 20) >>> 24) != 0
					&& (img.getRGB(4, 56) >>> 24) != 0 && (img.getRGB(40, 40) >>> 24) == 0;
			} catch (java.io.IOException e) {
				return false;
			}
		});
		checkS(P + "lang: standard name, no-room and switched-off messages", () -> {
			JsonObject lang = merged("assets/elsewhere/lang/en_us.json");
			return lang.has("entity.elsewhere." + Pennant.STANDARD_ID) && lang.has("message.elsewhere.herdcaller_pennant.no_room")
				&& lang.has("message.elsewhere.herdcaller_pennant.disabled");
		});
		checkS(P + "recipe: 10 Haze Resin (2 blocks + 2) around any banner, with load conditions for the Green Haze ids", () -> {
			JsonObject r = json("data/elsewhere/recipe/" + Pennant.ID + ".json");
			if (r == null) return false;
			String t = r.toString();
			return t.contains("#minecraft:banners") && t.contains("elsewhere:haze_resin_block") && t.contains("elsewhere:haze_resin") && t.contains("fabric:load_conditions");
		});
	}

	/** Every copy of a JSON resource on the classpath, merged (first copy wins per key, like the resource stack and the merged jar). */
	static JsonObject merged(String path) {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources(path))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
						if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			return new JsonObject();
		}
		return out;
	}

	static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
