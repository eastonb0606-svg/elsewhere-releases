package dev.elsewhere.gear3.s3.testmeter;

import static dev.elsewhere.gear3.dev.SelfTest.check;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Test Meter rules for every 3.0 summon (s-compat-testmeter). Generic, so summons added by any lane are covered:
 * <ul>
 * <li>No minion, totem or other summon is an {@code Enemy}: Test Meter would give it +53% max health and score its death as a monster kill.</li>
 * <li>Summon hits use the minion as the direct entity ({@link MinionApi#damageSource}), which Test Meter leaves alone, so the half bonus
 * inside {@code MinionApi.minionDamage} lands once. (Spell bolts are pre-compensated in {@code Spells.hurt}, arsenal {@code compat.TestMeterCompat}.)</li>
 * </ul>
 * Passes with and without Test Meter in run/mods. Hook: {@code TestMeterChecks.run(server)} from the gear3 self-test.
 */
public final class TestMeterChecks {
	private TestMeterChecks() {}

	public static void run(MinecraftServer server) {
		ServerLevel l = server.overworld();
		List<String> minions = new ArrayList<>(), enemies = new ArrayList<>();
		MinionEntity sample = null;
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			// summons are never in the MONSTER category (that is bosses and hostiles, whose constructors this test does not need to run)
			if (id == null || !"elsewhere".equals(id.getNamespace()) || type.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) continue;
			Entity e;
			try {
				e = type.create(l, EntitySpawnReason.COMMAND);
			} catch (RuntimeException ex) {
				continue;
			}
			if (e instanceof MinionEntity m) {
				minions.add(id.getPath());
				if (e instanceof Enemy) enemies.add(id.getPath());
				if (sample == null) sample = m;
				else e.discard();
			} else if (e != null) {
				e.discard();
			}
		}
		check("testmeter: no summon (minion, totem, standard) is an Enemy, so Test Meter adds no +53% health and no monster kill credit ("
			+ minions.size() + " summon types)", !minions.isEmpty() && enemies.isEmpty(), enemies.isEmpty() ? String.join(",", minions) : "Enemy: " + enemies);
		if (sample != null) {
			FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3-testmeter".getBytes()), "TMSummoner"));
			float f = factor(MinionApi.damageSource(l, sample, p));
			check("testmeter: a summon's hit (direct = the summon) is not scaled again by Test Meter, so its half bonus lands once", f == 1F, "factor " + f
				+ (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("testmeter") ? ", Test Meter loaded" : ", no Test Meter"));
			sample.discard();
		}
	}

	/** The factor Test Meter's own hurtServer hook applies to {@code src} (1 without Test Meter); same call as arsenal's TestMeterCompat. */
	static float factor(net.minecraft.world.damagesource.DamageSource src) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("testmeter")) return 1F;
		try {
			return ((Number) Class.forName("dev.testmeter.TestMeter").getMethod("scaleOutgoingDamage", net.minecraft.world.damagesource.DamageSource.class, float.class)
				.invoke(null, src, 1F)).floatValue();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return -1F;
		}
	}
}
