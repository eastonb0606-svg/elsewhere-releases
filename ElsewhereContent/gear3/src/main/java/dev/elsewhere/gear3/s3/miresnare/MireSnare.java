package dev.elsewhere.gear3.s3.miresnare;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Mechanics;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Mire Snare (GEAR.md A5, Marksman / Trapper, G2, stack 8): "placed trap: roots a non-boss mob 3 s + 6 dmg; never triggered by
 * players". Source: Mirewick's market (6 Scrip, the Mire-Trapper line in {@code rival_mirewick kingdom_trade/mirewick.json}) and a
 * crafting recipe (2 for string, Scrip, an iron ingot and mangrove roots).
 *
 * <ul>
 * <li>The item ({@code elsewhere:mirewick_mire_snare}, catalogue mechanic {@code "mire_snare"} = {@link MireSnareItem}) places the
 * {@link MireSnareBlock} on a floor, also in shallow water (waterlogged). The block entity remembers who placed it.</li>
 * <li>The first living mob whose feet enter it springs it: Snared ({@link #SNARED}: movement, jump and flying speed x0) for
 * {@link #ROOT_TICKS} and {@link #DAMAGE} damage of type {@code elsewhere:mire_snare}, credited to the placer when online (XP and
 * player-kill loot, like an arrow). Players, villagers and traders, golems, armor stands, tamed pets, summoned minions and bosses
 * never spring it.</li>
 * <li>Single use: the snare snaps shut (sprung model) and falls apart when the root ends, dropping nothing. An armed snare broken by
 * hand, or knocked off its floor, drops itself.</li>
 * <li>Lag: nothing ticks and nothing scans. The trigger is vanilla's own block-collision callback ({@code entityInside}, called only
 * for entities touching the block), and the clean-up is one scheduled block tick per spring.</li>
 * </ul>
 * Calling: none on purpose (s-callingtags' table in {@code gear3/tools/calling_tags.py}: a placed trap is not a bow, crossbow or
 * throwable, and an off-hand stack in {@code calling_weapon/marksman} would scale a vanilla bow's arrows), so the damage is flat 6.
 * Kill switch: key {@code mire_snare} in {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.mire_snare=false}):
 * off = the item, block and effect stay registered (worlds keep loading) but the item no longer places and placed snares never spring.
 * Merge entry: {@link #init()} from Gear3Content, before Gear3Items (it registers the catalogue item's factory).
 */
public final class MireSnare {
	private MireSnare() {}

	/** Item, block and block-entity id. */
	public static final String ID = "mirewick_mire_snare";
	/** Mob effect id ({@code effect.elsewhere.mire_snared}, icon {@code textures/mob_effect/mire_snared.png}). */
	public static final String EFFECT_ID = "mire_snared";
	/** Kill-switch key. */
	public static final String KILL_KEY = "mire_snare";
	/** GEAR.md: "+ 6 dmg". */
	public static final float DAMAGE = 6.0F;
	/** GEAR.md: "roots ... 3 s". The snare falls apart when the root ends. */
	public static final int ROOT_TICKS = 60;
	/** Snared tint (effect swirls): the dark Mirewick purple of the snare's cord. */
	public static final int COLOR = 0x6B2F78;
	public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, Gear3Content.id("mire_snare"));
	/** Kill switch {@code mire_snare}, read once. */
	public static final boolean ENABLED = on(KILL_KEY);

	public static MireSnareBlock BLOCK;
	public static BlockEntityType<MireSnareBlockEntity> BLOCK_ENTITY;
	public static Holder<MobEffect> SNARED;
	private static boolean done;

	/** Registers the block, its block entity, the Snared effect and the catalogue item factory. Idempotent. */
	public static void init() {
		if (done) return;
		done = true;
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Gear3Content.id(ID));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new MireSnareBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollision()
			.noOcclusion().strength(0.5F).sound(SoundType.MUDDY_MANGROVE_ROOTS).pushReaction(PushReaction.POPPED).setId(key)));
		BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Gear3Content.id(ID),
			new BlockEntityType<MireSnareBlockEntity>(MireSnareBlockEntity::new, Set.<Block>of(BLOCK)));
		MobEffect snared = new SnaredEffect();
		SNARED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Gear3Content.id(EFFECT_ID), snared);
		Gear3Mechanics.FACTORIES.put("mire_snare", props -> new MireSnareItem(BLOCK, props));
	}

	/**
	 * True when {@code e} springs a snare: a living mob that is not a player, villager or trader, golem, armor stand, tamed pet,
	 * summoned minion or boss.
	 */
	public static boolean springsFor(LivingEntity e) {
		if (e == null || !e.isAlive() || e.isSpectator()) return false;
		if (e instanceof Player || e instanceof ArmorStand || e instanceof Npc || e instanceof AbstractGolem) return false;
		if (e instanceof OwnableEntity o && o.getOwnerReference() != null) return false;
		if (MinionApi.isMinion(e)) return false;
		return !BossCheck.isBoss(e);
	}

	/** The online player who placed the snare at {@code pos}, or null. */
	public static Player owner(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof MireSnareBlockEntity be) || be.owner() == null) return null;
		return level.getServer().getPlayerList().getPlayer(be.owner());
	}

	public static DamageSource source(ServerLevel level, Player owner) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), null, owner);
	}

	/**
	 * Springs the armed snare at {@code pos} on {@code victim}: the snare snaps shut (it falls apart in {@link #ROOT_TICKS}), the
	 * victim is Snared for {@link #ROOT_TICKS} and takes {@link #DAMAGE}, credited to {@code owner} (may be null).
	 */
	public static void spring(ServerLevel level, BlockPos pos, BlockState state, LivingEntity victim, Player owner) {
		level.setBlock(pos, state.setValue(MireSnareBlock.SPRUNG, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, state.getBlock(), ROOT_TICKS);
		level.gameEvent(victim, GameEvent.BLOCK_ACTIVATE, pos);
		double x = pos.getX() + 0.5, y = pos.getY() + 0.15, z = pos.getZ() + 0.5;
		level.playSound(null, x, y, z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 1.5F);
		level.playSound(null, x, y, z, SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), x, y, z, 14, 0.3, 0.05, 0.3, 0.05);
		victim.addEffect(new MobEffectInstance(SNARED, ROOT_TICKS, 0, false, true, true), owner);
		victim.hurtServer(level, source(level, owner), DAMAGE);
	}

	/** The sprung snare's time is up: it falls apart (nothing dropped; water stays when it was waterlogged). */
	static void fallApart(ServerLevel level, BlockPos pos) {
		level.removeBlock(pos, false);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.2,
			pos.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0.02);
		level.playSound(null, pos, SoundEvents.MUD_STEP, SoundSource.BLOCKS, 0.7F, 1.1F);
	}

	/** Snared: movement, jump and flying speed x0 (ADD_MULTIPLIED_TOTAL -1), so the mob stays put but can still turn and strike. */
	static final class SnaredEffect extends MobEffect {
		SnaredEffect() {
			super(MobEffectCategory.HARMFUL, COLOR);
			addAttributeModifier(Attributes.MOVEMENT_SPEED, Gear3Content.id("effect.mire_snared.movement"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			addAttributeModifier(Attributes.JUMP_STRENGTH, Gear3Content.id("effect.mire_snared.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			addAttributeModifier(Attributes.FLYING_SPEED, Gear3Content.id("effect.mire_snared.flying"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	// ---------------------------------------------------------------- kill switch (same file and flags as arsenal's Overhaul3)

	public static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true; // a missing or malformed file keeps the default
		}
	}
}
