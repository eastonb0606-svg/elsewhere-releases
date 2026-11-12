package dev.elsewhere.gear3.dev;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3Loot;
import dev.elsewhere.gear3.s3.censer.ChoirlightCenser;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Choirlight Censer checks (s-censer). Synchronous, on two FakePlayers of its own, at x 60..70 z 70 (forced test chunks). */
public final class CenserChecks {
	private CenserChecks() {}

	static final String N = "Choirlight Censer: ";

	static Wolf wolf(ServerLevel l, Vec3 at, FakePlayer owner, float health) {
		Wolf w = EntityTypes.WOLF.create(l, EntitySpawnReason.COMMAND);
		w.snapTo(at.x, at.y, at.z, 0, 0);
		w.setNoAi(true);
		w.tame(owner);
		l.addFreshEntity(w);
		w.setHealth(health);
		return w;
	}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item item = Gear3Items.BY_ID.get("choirlight_censer");
		check(N + "registered with its own item class", item instanceof ChoirlightCenser, item);
		if (!(item instanceof ChoirlightCenser censer)) return;
		if (!ChoirlightCenser.ENABLED) {
			SelfTest.info(N + "kill switch '" + ChoirlightCenser.KILL_KEY + "' is off; mechanic checks skipped");
			return;
		}
		Vec3 at = new Vec3(60.5, 100, 70.5);
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.fromString("5c3e45e2-6c1a-4f7e-9d1a-c3e45e2c0001"), "censer_selftest"));
		FakePlayer other = FakePlayer.get(l, new GameProfile(UUID.fromString("5c3e45e2-6c1a-4f7e-9d1a-c3e45e2c0002"), "censer_other"));
		p.snapTo(at.x, at.y, at.z, 0, 0);
		other.snapTo(at.x + 2, at.y, at.z, 0, 0);
		ChoirlightCenser.forgetForTest(p);
		ItemStack st = new ItemStack(item);
		p.setItemInHand(InteractionHand.MAIN_HAND, st);

		check(N + "Caster channel: 3 Glimmer a pulse, one pulse a second, held as long as use is held, brush-style swing",
			censer.rawCost(p) == 3 && ChoirlightCenser.PULSE_TICKS == 20 && censer.getUseDuration(st, p) >= 72000 && censer.getUseAnimation(st) == ItemUseAnimation.BRUSH);
		UseEffects fx = st.get(DataComponents.USE_EFFECTS);
		check(N + "you walk at 40% while swinging it (use_effects speed 0.4, no sprint; other items 20%)", fx != null && near(fx.speedMultiplier(), 0.4) && !fx.canSprint(), fx);
		check(N + "heals allies within 6 blocks 1.5 HP/s and you 0.5 HP/s (GEAR.md numbers)",
			near(ChoirlightCenser.ALLY_HEAL, 1.5) && near(ChoirlightCenser.SELF_HEAL, 0.5) && near(ChoirlightCenser.RADIUS, 6));
		censer.use(l, p, InteractionHand.MAIN_HAND);
		check(N + "use starts the channel", p.isUsingItem() && p.getUseItem().getItem() == item);

		Wolf mine = wolf(l, at.add(4, 0, 0), p, 4F); // your minion / pet, 4 blocks
		Wolf far = wolf(l, at.add(0, 0, 7), p, 4F); // yours, but 7 blocks away
		Wolf theirs = wolf(l, at.add(-3, 0, 0), other, 4F); // another player's pet
		Mob husk = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		husk.snapTo(at.x, at.y, at.z + 3, 0, 0);
		husk.setNoAi(true);
		l.addFreshEntity(husk);
		husk.setHealth(10F);
		l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(8)); // warm-up query (GOTCHAS: first query after spawning)
		p.setHealth(10F);
		Glimmer.setForTest(p, 20F);
		float g0 = Glimmer.current(p), cost = Glimmer.cost(p, ChoirlightCenser.COST);
		long t = l.getGameTime() + 1000;

		int r1 = ChoirlightCenser.channel(l, p, st, InteractionHand.MAIN_HAND, 0, t);
		check(N + "a pulse heals your minion within 6 blocks by 1.5 HP and you by 0.5 HP", r1 == 1 && near(mine.getHealth(), 5.5) && near(p.getHealth(), 10.5),
			r1 + " wolf " + mine.getHealth() + " you " + p.getHealth());
		check(N + "hostile mobs, another player's pet and anything beyond 6 blocks are not healed", near(far.getHealth(), 4) && near(theirs.getHealth(), 4) && near(husk.getHealth(), 10),
			far.getHealth() + "/" + theirs.getHealth() + "/" + husk.getHealth());
		check(N + "each pulse spends the Calling-scaled cost of 3 Glimmer", near(Glimmer.current(p), g0 - cost), g0 + " -> " + Glimmer.current(p) + " (cost " + cost + ")");
		check(N + "each pulse takes 1 durability", st.getDamageValue() == 1, st.getDamageValue());

		int r2 = ChoirlightCenser.channel(l, p, st, InteractionHand.MAIN_HAND, 1, t);
		int r3 = ChoirlightCenser.channel(l, p, st, InteractionHand.MAIN_HAND, 19, t + 19);
		boolean held = near(mine.getHealth(), 5.5) && st.getDamageValue() == 1;
		int r4 = ChoirlightCenser.channel(l, p, st, InteractionHand.MAIN_HAND, 20, t + 20);
		check(N + "at most one pulse a second however often it is used (game-time gate per player)", r2 == 0 && r3 == 0 && held && r4 == 1 && near(mine.getHealth(), 7),
			r2 + "," + r3 + "," + r4 + " wolf " + mine.getHealth());

		Glimmer.setForTest(p, 0F);
		mine.setHealth(4F);
		int r5 = ChoirlightCenser.channel(l, p, st, InteractionHand.MAIN_HAND, 40, t + 40);
		check(N + "out of Glimmer: no heal, the channel stops and the censer cools down 1 s", r5 == -1 && !p.isUsingItem() && p.getCooldowns().isOnCooldown(st) && near(mine.getHealth(), 4),
			r5 + " using " + p.isUsingItem());
		p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(st));

		checkS(N + "a Will-o'-Lantern recast (castFree at 40%) heals 40%: 0.6 HP", () -> {
			mine.setHealth(4F);
			censer.castFree(l, p, st, 0.4F);
			return near(mine.getHealth(), 4.6);
		});

		checkS(N + "other players are allies, except on rival scoreboard teams; spectators never", () -> {
			if (!ChoirlightCenser.isAlly(p, other) || ChoirlightCenser.isAlly(p, p) || ChoirlightCenser.isAlly(p, husk) || ChoirlightCenser.isAlly(p, theirs)) return false;
			Scoreboard sb = l.getScoreboard();
			for (String name : new String[] {"censer_test_a", "censer_test_b"}) {
				PlayerTeam old = sb.getPlayerTeam(name); // left over from an interrupted run of this test world
				if (old != null) sb.removePlayerTeam(old);
			}
			PlayerTeam a = sb.addPlayerTeam("censer_test_a"), b = sb.addPlayerTeam("censer_test_b");
			try {
				sb.addPlayerToTeam(p.getScoreboardName(), a);
				sb.addPlayerToTeam(other.getScoreboardName(), b);
				boolean rivals = !ChoirlightCenser.isAlly(p, other);
				sb.addPlayerToTeam(other.getScoreboardName(), a);
				boolean mates = ChoirlightCenser.isAlly(p, other);
				return rivals && mates;
			} finally {
				sb.removePlayerTeam(a);
				sb.removePlayerTeam(b);
			}
		});

		checkS(N + "incense hangs from the using hand (visual anchor within 1 block of the hand, 0.6-1.2 blocks up)", () -> {
			p.startUsingItem(InteractionHand.MAIN_HAND);
			for (int e = 0; e < 20; e++) {
				Vec3 c = ChoirlightCenser.censerPos(p, e);
				double up = c.y - p.getY(), flat = Math.hypot(c.x - p.getX(), c.z - p.getZ());
				if (up < 0.6 || up > 1.2 || flat > 1.0 || flat < 0.2) return false;
			}
			return true;
		});

		List<Gear3Loot.Entry> lith = Gear3Loot.DROPS.get(Gear3Loot.tableId("entities/lithara").toString());
		check(N + "Lithara drops it 30% of the time (loot hook)", lith != null && lith.stream().anyMatch(e -> e.item() == item && near(e.drop().chance(), 0.3)), lith);
		check(N + "never duplicated by Test Meter transmutation (testmeter:transmutation_excluded)",
			st.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("testmeter", "transmutation_excluded"))));

		p.stopUsingItem();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ChoirlightCenser.forgetForTest(p);
		for (LivingEntity e : new LivingEntity[] {mine, far, theirs, husk}) e.discard();
		SelfTest.info(N + "checks done");
	}
}
