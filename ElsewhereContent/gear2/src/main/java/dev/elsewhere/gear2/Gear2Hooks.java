package dev.elsewhere.gear2;

import dev.elsewhere.gear2.item.ProspectorsPendulum;
import dev.elsewhere.stub.gear2.SetBonusStub;
import dev.elsewhere.stub.gear2.TrinketStub;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server hooks for every gear2 ability and con. The damage hook is called by {@code Gear2LivingEntityMixin}. */
public final class Gear2Hooks {
	private Gear2Hooks() {}

	public static final Identifier FLAIL_BACKWARD = Gear2Content.id("ironclad_flail.backward");
	private static final ResourceKey<DamageType> SPELL = ResourceKey.create(Registries.DAMAGE_TYPE, Gear2Content.id("spell"));

	/** Ironclad Flail chain per player: last target, last hit tick, chain length 0..5. */
	public record Chain(UUID target, long tick, int length) {}

	private static final Map<UUID, Chain> CHAINS = new HashMap<>();

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && !blocked && base > 0) Capacitor.onHurt(p, source);
		});
		ServerTickEvents.END_SERVER_TICK.register(Gear2Hooks::tick);
	}

	static void tick(MinecraftServer server) {
		Capacitor.tick(server);
		Termites.tick(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			flailBackward(p);
			Amberstill.update(p, p.level().getGameTime());
			if ((server.getTickCount() + p.getId()) % Gear2Stats.PENDULUM_PING_TICKS == 0) pendulumPing(p);
		}
	}

	// ------------------------------------------------------------------------------------------------ damage

	/** Melee hit by a player with their main-hand weapon (not projectiles, spells, thorns...). */
	public static boolean playerMelee(DamageSource source) {
		return source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p && source.is(DamageTypes.PLAYER_ATTACK);
	}

	/** Lightning, plus the Stormcoil Staff's chain lightning (arsenal spell damage from a staff wielder). */
	public static boolean lightning(DamageSource source) {
		if (source.is(DamageTypeTags.IS_LIGHTNING)) return true;
		return source.is(SPELL) && source.getEntity() instanceof LivingEntity l
			&& net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(l.getMainHandItem().getItem()).getPath().equals("stormcoil_staff");
	}

	public static int coilmailPieces(Player p) {
		return SetBonusStub.pieces(p, Gear2Items.COILMAIL_SET);
	}

	/** Incoming damage on {@code victim} before armor; returns the new amount. */
	public static float modifyIncoming(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0) return amount;
		if (victim instanceof Player vp && lightning(source)) {
			int n = coilmailPieces(vp);
			if (n > 0) amount *= 1.0F + Gear2Stats.COILMAIL_LIGHTNING_PER_PIECE * n;
		}
		if (playerMelee(source) && source.getEntity() instanceof ServerPlayer p) {
			Amberstill.onAction(p, p.level().getGameTime());
			ItemStack weapon = p.getMainHandItem();
			if (weapon.is(Gear2Items.IRONCLAD_FLAIL)) amount *= flailHit(p, victim);
			else if (weapon.is(Gear2Items.GALE_RAPIER)) amount = rapierHit(p, victim, amount);
			if (Capacitor.charges(p) > 0) Capacitor.discharge(p, victim);
		}
		return amount;
	}

	// ------------------------------------------------------------------------------------------------ Ironclad Flail

	/** Registers a flail hit and returns its multiplier: +10% per hit on the same target within 2 s, up to +50%. */
	public static float flailHit(ServerPlayer p, LivingEntity target) {
		long now = p.level().getGameTime();
		Chain c = CHAINS.get(p.getUUID());
		int len = c != null && c.target().equals(target.getUUID()) && now - c.tick() <= Gear2Stats.FLAIL_CHAIN_WINDOW
			? Math.min(c.length() + 1, Gear2Stats.FLAIL_CHAIN_MAX) : 0;
		CHAINS.put(p.getUUID(), new Chain(target.getUUID(), now, len));
		return 1.0F + Gear2Stats.FLAIL_CHAIN_STEP * len;
	}

	public static int flailChain(Player p) {
		Chain c = CHAINS.get(p.getUUID());
		return c == null ? -1 : c.length();
	}

	/** A swing at nothing (the 26.3 punch packet) resets the chain. */
	public static void onPunch(ServerPlayer p) {
		Amberstill.onAction(p, p.level().getGameTime());
		if (p.getMainHandItem().is(Gear2Items.IRONCLAD_FLAIL)) CHAINS.remove(p.getUUID());
	}

	/** Con: 20% slower while moving backward with the flail in hand. */
	public static void flailBackward(ServerPlayer p) {
		Input in = p.getLastClientInput();
		boolean slow = p.getMainHandItem().is(Gear2Items.IRONCLAD_FLAIL) && in.backward() && !in.forward();
		AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) return;
		boolean has = speed.getModifier(FLAIL_BACKWARD) != null;
		if (slow && !has) speed.addTransientModifier(new AttributeModifier(FLAIL_BACKWARD, Gear2Stats.FLAIL_BACKWARD_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		else if (!slow && has) speed.removeModifier(FLAIL_BACKWARD);
	}

	// ------------------------------------------------------------------------------------------------ Gale Rapier

	public static boolean standingStill(ServerPlayer p) {
		Input in = p.getLastClientInput();
		Vec3 v = p.getKnownMovement();
		return !in.forward() && !in.backward() && !in.left() && !in.right() && v.horizontalDistanceSqr() < 1.0E-4;
	}

	/** Sprinting hit: +3 lunge damage and a short forward dash. Standing still: -25%. */
	public static float rapierHit(ServerPlayer p, LivingEntity target, float amount) {
		if (p.isSprinting()) {
			Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize().scale(Gear2Stats.RAPIER_DASH);
			p.push(look.x, 0.05, look.z);
			p.needsSync = true;
			return amount + Gear2Stats.RAPIER_LUNGE;
		}
		return standingStill(p) ? amount * Gear2Stats.RAPIER_STILL_MULT : amount;
	}

	// ------------------------------------------------------------------------------------------------ trinkets

	/** Prospector's Pendulum swing every 5 s while it is an active trinket; 1 durability when it finds ore. */
	public static List<ProspectorsPendulum.Find> pendulumPing(ServerPlayer p) {
		ItemStack s = TrinketStub.activeStack(p, Gear2Items.PROSPECTORS_PENDULUM);
		if (s.isEmpty()) return List.of();
		List<ProspectorsPendulum.Find> finds = ProspectorsPendulum.scan(p.level(), p.blockPosition());
		if (!finds.isEmpty()) {
			ProspectorsPendulum.swing(p, finds.get(0));
			s.hurtAndBreak(1, p.level(), p, broken -> {});
		}
		return finds;
	}
}
