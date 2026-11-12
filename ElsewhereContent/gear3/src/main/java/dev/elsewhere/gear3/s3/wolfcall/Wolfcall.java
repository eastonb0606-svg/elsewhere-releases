package dev.elsewhere.gear3.s3.wolfcall;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * Wolf-Call Horn (GEAR.md A5, Summoner / Warband, G3, V+S hybrid): "1 slot: Warg 26 HP, armor 6, 5 dmg / 1 s; howl: +10% attack
 * speed to you within 8, x H". Source: Gudrun 30%, Wolf-Handler.
 * <ul>
 * <li>The horn ({@link WolfcallHornItem}, catalogue mechanic {@code wolfcall_horn}) is a summoner staff blown like a goat horn: hold
 * use for the 1 s call, then one {@link WargEntity} answers with a howl. It goes through the summoner minion API
 * ({@code MinionManager.summon}): 10 Glimmer, one minion slot per Warg, the slot cap (Summoner mastery, slot gear, hard cap 4; the
 * oldest minion leaves when you are over it), Herd focus (-10% own damage per minion beyond the first), the battle timer, the
 * Glimmer reserve and every other minion rule. Blow it again for the next Warg of the pack; sneak-use sends the pack home.</li>
 * <li>Howl: while you stand within {@link #HOWL_RANGE} blocks of any of your Wargs you get +{@link #HOWL_ATTACK_SPEED} x H attack
 * speed, H = {@code Calling.hybrid(Vanguard, Summoner)}. It is one transient attribute modifier, refreshed by each Warg in the
 * minion framework's once-per-second hook and when a Warg leaves; nothing scans or polls.</li>
 * <li>Drops (one death event, no loot-table edits in other modules): Gudrun Ulfhednar ({@code elsewhere:hrafnholt_chief} and her
 * Risen form) {@link #GUDRUN_CHANCE}, Hrafnholt Wolf-Handlers {@link #HANDLER_CHANCE}, player kills only, honouring doMobLoot.</li>
 * </ul>
 * Merge entry: {@link #init()} from Gear3Content. Client: {@code WolfcallRenderers.init()} from Gear3Client.
 * Kill switch: key {@code wolfcall_horn} in {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.wolfcall_horn=false}):
 * off = the item and the Warg stay registered (worlds keep loading) but the horn calls nothing, no howl bonus and no drops.
 */
public final class Wolfcall {
	private Wolfcall() {}

	public static final String ID = "hrafnholt_wolfcall_horn";
	public static final String WARG_ID = "hrafnholt_warg";
	/** Kill switch {@code wolfcall_horn}, read once. */
	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on("wolfcall_horn");

	// ---- Warg (GEAR.md: 26 HP, armor 6, 5 dmg / 1 s; 1 slot)
	public static final double WARG_HP = 26.0, WARG_ARMOR = 6.0, WARG_DAMAGE = 5.0, WARG_SPEED = 0.33, WARG_REACH = 2.0;
	public static final int WARG_ATTACK_TICKS = 20;
	/** Renderer scale over the vanilla wolf model (the hitbox is scaled the same). */
	public static final float WARG_SCALE = 1.3F;
	// ---- howl
	public static final double HOWL_RANGE = 8.0;
	public static final double HOWL_ATTACK_SPEED = 0.10;
	/** Least ticks between two howl sounds for one owner (the bonus switching on, a Warg answering the horn). */
	public static final int HOWL_SOUND_GAP_TICKS = 200;
	public static final Identifier HOWL_MOD = Gear3Content.id("wolfcall_horn.howl");
	// ---- drops (GEAR.md source: "Gudrun 30%; Wolf-Handler")
	public static final float GUDRUN_CHANCE = 0.30F, HANDLER_CHANCE = 0.05F;
	public static final String GUDRUN = "elsewhere:hrafnholt_chief", GUDRUN_RISEN = "elsewhere:hrafnholt_risen_chief", WOLF_HANDLER = "elsewhere:hrafnholt_wolf_handler";

	public static EntityType<WargEntity> WARG;
	public static SoundEvent HORN_BLOW, WARG_HOWL, WARG_AMBIENT, WARG_GROWL, WARG_HURT, WARG_DEATH, WARG_PANT;

	/** owner -> game time of their last howl sound (weak keys: a logged-out player is forgotten with their object). */
	private static final Map<Player, Long> LAST_HOWL = new WeakHashMap<>();
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		Identifier wid = Gear3Content.id(WARG_ID);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(wid)) throw new IllegalStateException("gear3 wolfcall: entity id already registered: " + wid);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, wid);
		// minion rules like the summoner module's minions: not saved with the world, no loot table
		WARG = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<WargEntity>of(WargEntity::new, MobCategory.MISC)
			.sized(0.6F * WARG_SCALE, 0.85F * WARG_SCALE).eyeHeight(0.68F * WARG_SCALE).clientTrackingRange(10).noSave().noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(WARG, WargEntity.createAttributes());
		HORN_BLOW = sound("item.hrafnholt_wolfcall_horn.blow");
		WARG_HOWL = sound("entity.hrafnholt_warg.howl");
		WARG_AMBIENT = sound("entity.hrafnholt_warg.ambient");
		WARG_GROWL = sound("entity.hrafnholt_warg.growl");
		WARG_HURT = sound("entity.hrafnholt_warg.hurt");
		WARG_DEATH = sound("entity.hrafnholt_warg.death");
		WARG_PANT = sound("entity.hrafnholt_warg.pant");
		ServerLivingEntityEvents.AFTER_DEATH.register(Wolfcall::onDeath);
	}

	private static SoundEvent sound(String path) {
		Identifier id = Gear3Content.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** The catalogue item, or null when the catalogue has none. */
	public static @Nullable Item item() {
		return Gear3Items.BY_ID.get(ID);
	}

	// ---------------------------------------------------------------- howl

	/** H = min(1, min(M_vanguard, M_summoner) / 0.60) (GEAR.md A1 hybrid strength). */
	public static float hybrid(Player owner) {
		return Calling.hybrid(owner, Calling.Path.VANGUARD, Calling.Path.SUMMONER);
	}

	/** The howl's attack-speed bonus for this owner: +10% x H (add_multiplied_base, so it adds to other percentage bonuses). */
	public static double howlBonus(Player owner) {
		return HOWL_ATTACK_SPEED * hybrid(owner);
	}

	/** True if a live Warg of this owner (other than {@code gone}) stands within {@link #HOWL_RANGE} blocks of them. */
	public static boolean wargNear(ServerPlayer owner, @Nullable WargEntity gone) {
		for (MinionEntity m : MinionManager.minions(owner)) {
			if (m instanceof WargEntity w && w != gone && w.isAlive() && w.level() == owner.level() && w.distanceToSqr(owner) <= HOWL_RANGE * HOWL_RANGE) return true;
		}
		return false;
	}

	/**
	 * Sets or clears the owner's howl modifier. Called by each Warg once per second (the minion framework's per-second hook), when a
	 * Warg is called and when one leaves. Returns true when the bonus was switched on by this call.
	 */
	public static boolean refreshHowl(ServerPlayer owner, @Nullable WargEntity gone) {
		AttributeInstance inst = owner.getAttribute(Attributes.ATTACK_SPEED);
		if (inst == null) return false;
		double want = ENABLED && wargNear(owner, gone) ? howlBonus(owner) : 0.0;
		AttributeModifier cur = inst.getModifier(HOWL_MOD);
		if (want <= 0.0) {
			if (cur != null) inst.removeModifier(HOWL_MOD);
			return false;
		}
		if (cur != null && Math.abs(cur.amount() - want) < 1.0E-6) return false;
		if (cur != null) inst.removeModifier(HOWL_MOD);
		inst.addTransientModifier(new AttributeModifier(HOWL_MOD, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		return cur == null;
	}

	/** The current howl modifier amount on this player (0 when none). */
	public static double howlAmount(Player p) {
		AttributeInstance inst = p.getAttribute(Attributes.ATTACK_SPEED);
		AttributeModifier m = inst == null ? null : inst.getModifier(HOWL_MOD);
		return m == null ? 0.0 : m.amount();
	}

	/** A Warg howls (sound and a puff of breath), at most once per {@link #HOWL_SOUND_GAP_TICKS} per owner unless {@code force}. */
	public static boolean howl(ServerLevel level, ServerPlayer owner, WargEntity w, boolean force) {
		long now = level.getGameTime();
		Long last = LAST_HOWL.get(owner);
		if (!force && last != null && now - last < HOWL_SOUND_GAP_TICKS && now >= last) return false;
		LAST_HOWL.put(owner, now);
		level.playSound(null, w.getX(), w.getY(), w.getZ(), WARG_HOWL, SoundSource.NEUTRAL, 1.6F, 0.92F + w.getRandom().nextFloat() * 0.16F);
		level.sendParticles(ParticleTypes.POOF, w.getX(), w.getY() + w.getBbHeight() * 0.8, w.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
		return true;
	}

	/** After the horn's call: the new Warg answers with a howl and the owner's bonus is set at once. */
	public static void onCalled(ServerPlayer owner, MinionEntity m) {
		if (!(m instanceof WargEntity w) || !(owner.level() instanceof ServerLevel sl)) return;
		howl(sl, owner, w, true);
		refreshHowl(owner, null);
	}

	// ---------------------------------------------------------------- drops

	/** Drop chance of the horn for a kill of this entity type (0 for everything but Gudrun and the Wolf-Handler). */
	public static float dropChance(EntityType<?> type) {
		return dropChance(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
	}

	/** Drop chance by entity type id ("elsewhere:hrafnholt_chief" ...). */
	public static float dropChance(String entityTypeId) {
		return switch (entityTypeId) {
			case GUDRUN, GUDRUN_RISEN -> GUDRUN_CHANCE;
			case WOLF_HANDLER -> HANDLER_CHANCE;
			default -> 0F;
		};
	}

	/** One roll; true = the horn drops. Split out so the self-test can check the rate. */
	public static boolean rollDrop(String entityTypeId, RandomSource rnd) {
		float c = dropChance(entityTypeId);
		return c > 0F && rnd.nextFloat() < c;
	}

	static boolean killedByPlayer(LivingEntity e, DamageSource src) {
		return src.getEntity() instanceof Player || e.getLastHurtByPlayer() != null;
	}

	private static void onDeath(LivingEntity e, DamageSource src) {
		if (!ENABLED || !(e.level() instanceof ServerLevel sl)) return;
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		if (dropChance(id) <= 0F || !killedByPlayer(e, src) || !sl.getGameRules().get(GameRules.MOB_DROPS)) return;
		Item horn = item();
		if (horn != null && rollDrop(id, sl.getRandom())) e.spawnAtLocation(sl, new ItemStack(horn));
	}
}
