package dev.elsewhere.gear3.s3.whips;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.minion.Herding;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.summoner.GaleRibbonItem;
import dev.elsewhere.summoner.GearStats;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * gear3 whip mechanics (3.0 swarm, s-whips). GEAR.md A5: "whips hit and tag (whip damage x D(Summoner), Herd focus never
 * applies to whips, one whip tag per target, newest wins)", plus each whip's own mark ({@link WhipKind}).
 * <ul>
 * <li>Hit: tags the target with the whip's mark ({@link WhipTags}); the owner's minions switch to it (summoner
 * {@code MinionTargeting} reads it through {@code GaleRibbonItem.target}), ranged minions reach +4 against it, and minion
 * hits on it get the mark's bonus ({@link #minionDamage}, {@link #armorAgainst}).</li>
 * <li>Crack (right-click): the lash snaps out to full reach, keeps the tag alive (it fades 3 s after the last swing), 0.5 s
 * cooldown; the held model shows the cracked lash while it runs (items/&lt;id&gt;.json, minecraft:cooldown).</li>
 * <li>Swing: a fuller lash than a sword ({@link WhipItem#LASH}), each whip's own crack sound (gear3.whip.&lt;id&gt;).</li>
 * <li>Every whip (the Gale Ribbon too): melee damage x D(Summoner) against non-players ({@link #whipMelee}); Herd focus is
 * lifted for the whip's own hit ({@link #liftHerdFocus}).</li>
 * </ul>
 * Event-driven only: hooks run on a hit, a use, a minion hit or a damage calculation; nothing ticks or scans.
 * <p><b>Kill switch</b> {@code whip_mechanics}: {@link #ENABLED} (key {@code whip_mechanics} in {@code config/elsewhere-overhaul3.json} or
 * {@code -Delsewhere.overhaul3.whip_mechanics=false}, read once on the server). Off,
 * the four whips fall back to the Gale Ribbon's tag rules and every hook passes through.
 */
public final class Gear3Whips {
	private Gear3Whips() {}

	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on("whip_mechanics");
	/** Summoner's Herd focus attack-damage modifier (SummonerPerks.HERD_FOCUS). */
	public static final Identifier HERD_FOCUS = Identifier.fromNamespaceAndPath("elsewhere", "herd_focus");

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		for (WhipKind k : WhipKind.values()) {
			Identifier id = Identifier.fromNamespaceAndPath("elsewhere", k.soundPath());
			k.sound = BuiltInRegistries.SOUND_EVENT.containsKey(id) ? BuiltInRegistries.SOUND_EVENT.getValue(id)
				: Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		}
		if (!ENABLED) return;
		// a landed minion hit on a marked target shows the mark (one small burst per hit)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0F || WhipTags.size() == 0 || !source.is(MinionApi.MINION_DAMAGE)) return;
			if (source.getEntity() instanceof ServerPlayer owner) {
				WhipKind k = WhipTags.kindOn(owner, entity);
				if (k != null) WhipFx.minionHit(entity, k);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (WhipTags.size() > 0) WhipTags.clearTarget(entity.getUUID());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WhipTags.clear(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WhipTags.reset());
	}

	/** A whip: the four gear3 whips and the summoner's Gale Ribbon. */
	public static boolean isWhip(ItemStack s) {
		return !s.isEmpty() && (s.getItem() instanceof WhipItem || s.getItem() instanceof GaleRibbonItem);
	}

	// ---------------------------------------------------------------- the whip itself

	/** A whip hit (WhipItem.hurtEnemy, server). */
	public static void onHit(ServerPlayer owner, LivingEntity target, @Nullable WhipKind kind) {
		if (!ENABLED || kind == null) {
			GaleRibbonItem.tag(owner, target);
			return;
		}
		boolean fresh = WhipTags.tag(owner, target, kind);
		WhipFx.tagged(owner, target, kind, fresh);
	}

	/** Right-click crack (WhipItem.use, server): keeps the tag alive, 0.5 s cooldown, crack at the lash tip. */
	public static boolean crack(ServerPlayer owner, ItemStack stack, @Nullable WhipKind kind) {
		if (!ENABLED || kind == null || owner.getCooldowns().isOnCooldown(stack)) return false;
		boolean kept = WhipTags.refresh(owner);
		owner.getCooldowns().addCooldown(stack, WhipItem.CRACK_COOLDOWN);
		WhipFx.crack(owner, kind);
		return kept;
	}

	// ---------------------------------------------------------------- minion hits on a marked target (WhipMixins)

	/**
	 * MinionApi.minionDamage result for a target the owner marked with a gear3 whip: the mark's bonus on top of the
	 * minion's damage (the Herded +1 stays flat): {@code (d - herded) x (1 + pct) + flat + herded}.
	 */
	public static double minionDamage(ServerPlayer owner, LivingEntity target, double d) {
		if (!ENABLED) return d;
		WhipKind k = WhipTags.kindOn(owner, target);
		if (k == null) return d;
		double herded = Herding.isHerdedBy(owner, target) ? GearStats.HERDED_BONUS : 0.0;
		return (d - herded) * (1.0 + k.pct) + k.flat + herded;
	}

	/** Armor used against a hit: the owner's minion hits ignore the mark's armor points (Sunscourge Venom: 3). */
	public static float armorAgainst(LivingEntity victim, DamageSource source, float armor) {
		if (!ENABLED || armor <= 0F || WhipTags.size() == 0 || !source.is(MinionApi.MINION_DAMAGE)) return armor;
		if (!(source.getEntity() instanceof ServerPlayer owner)) return armor;
		WhipKind k = WhipTags.kindOn(owner, victim);
		return k == null || k.armorIgnore <= 0 ? armor : Math.max(0F, armor - k.armorIgnore);
	}

	// ---------------------------------------------------------------- the whip family's rules on the owner's own hits

	/** Whip melee x D(Summoner) (GEAR.md A5), never against players (PvP stays calling-neutral). */
	public static float whipMelee(LivingEntity victim, DamageSource source, float amount) {
		if (!ENABLED || amount <= 0F || victim instanceof Player || victim.level().isClientSide()) return amount;
		Entity e = source.getEntity();
		if (!(e instanceof ServerPlayer p) || source.getDirectEntity() != p || !source.is(DamageTypes.PLAYER_ATTACK) || !isWhip(p.getMainHandItem())) return amount;
		return amount * Calling.damageMult(p, Calling.Path.SUMMONER, victim);
	}

	/** Herd focus never applies to whips: lifted for the length of one whip attack (Player.attack), then put back. */
	public static @Nullable AttributeModifier liftHerdFocus(Player p) {
		if (!ENABLED || p.level().isClientSide() || !isWhip(p.getMainHandItem())) return null;
		AttributeInstance inst = p.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst == null) return null;
		AttributeModifier m = inst.getModifier(HERD_FOCUS);
		if (m != null) inst.removeModifier(HERD_FOCUS);
		return m;
	}

	public static void restoreHerdFocus(Player p, @Nullable AttributeModifier m) {
		if (m == null) return;
		AttributeInstance inst = p.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst != null && inst.getModifier(HERD_FOCUS) == null) inst.addTransientModifier(m);
	}
}
