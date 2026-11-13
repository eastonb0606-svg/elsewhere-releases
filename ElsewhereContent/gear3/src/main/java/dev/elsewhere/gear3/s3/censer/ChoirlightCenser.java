package dev.elsewhere.gear3.s3.censer;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.arsenal.spell.Spells;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Choirlight Censer (GEAR.md A5, Caster / Support, G4; swarm lane s-censer). Hold use to swing it: a channel that spends
 * 3 Glimmer a second; each second, other players and your own minions and pets within 6 blocks heal 1.5 HP and you
 * heal 0.5 HP. You walk at 40% while swinging it (the vanilla {@code use_effects} component; using any other item is 20%).
 *
 * <p>Lag: no global scan, no new tick hook. The channel is the user's own item-use tick (vanilla only calls
 * {@link #onUseTick} for the entity that is using the item); a pulse runs at most once a second per channelling player
 * and does one 6-block box query around them. Client visuals (incense smoke, gold sparks, the 6-block ring) are spawned
 * on the client in the same use tick, so nothing is sent for them; {@code ClientLevel.addParticle} drops particles more
 * than 32 blocks from the camera and follows the particle setting. The heal notes are one server particle per healed
 * ally per second, which the server only sends to players within 32 blocks.
 *
 * <p>The pulse is gated per player by game time ({@link #NEXT_PULSE}), not by the use counter, so tapping use cannot
 * pulse faster than once a second. The first pulse of a channel counts as the cast for {@link Spells#afterCast}
 * (Lamplighter, Will-o'-Lantern and the other on-cast hooks); later pulses do not. Out of Glimmer, the channel stops and
 * the censer cools down for a second (the stop reaches the client through the synced using-item flag).
 *
 * <p>Kill switch: key {@value #KILL_KEY} in {@code config/elsewhere-overhaul3.json}. Off: using the censer does nothing
 * (the item stays registered, so worlds keep it).
 */
public class ChoirlightCenser extends CasterItem {
	public static final String KILL_KEY = "choirlight_censer";
	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on(KILL_KEY);

	// numbers from GEAR.md A5 (choirlight_censer)
	public static final int COST = 3, PULSE_TICKS = 20, FAIL_COOLDOWN = 20;
	public static final float ALLY_HEAL = 1.5F, SELF_HEAL = 0.5F, WALK_SPEED = 0.4F;
	public static final double RADIUS = 6.0;
	/** The pulse chime walks a small choir chord (1, major third, fifth, major third). */
	private static final float[] CHORD = {1.0F, 1.26F, 1.5F, 1.26F};

	/** Server only: game time of each channelling player's next allowed pulse. Weak keys: an entry goes with its player object. */
	private static final Map<Player, Long> NEXT_PULSE = new WeakHashMap<>();

	public ChoirlightCenser(Properties props) {
		super(props.component(DataComponents.USE_EFFECTS, new UseEffects(false, true, WALK_SPEED)), PULSE_TICKS, false);
	}

	@Override
	public int rawCost(Player p) {
		return COST;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Both sides always start the channel (like a bow), so client and server agree; only the server ends it (no
		// Glimmer, or the kill switch), and that reaches the client through the synced using-item flag.
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BRUSH; // the arm sways the censer to and fro (first and third person)
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return APPROXIMATELY_INFINITE_USE_DURATION;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		int elapsed = getUseDuration(stack, user) - remaining;
		if (level.isClientSide()) {
			clientFx(level, user, elapsed);
			return;
		}
		if (user instanceof ServerPlayer p && level instanceof ServerLevel sl) channel(sl, p, stack, p.getUsedItemHand(), elapsed, sl.getGameTime());
	}

	/** A one-off cast (CasterItem.castNow, or a Will-o'-Lantern recast through castFree): one pulse at the current spell power scale. */
	@Override
	protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
		if (ENABLED) pulse(level, p, Spells.powerScale());
	}

	/**
	 * One channel tick on the server. Pulses when the player's next pulse is due: spends the Calling-scaled cost of 3,
	 * heals, takes 1 durability. Returns 1 when it pulsed, 0 when no pulse was due, -1 when the channel stopped for want of
	 * Glimmer (nothing spent or healed; the bar flashes, the censer cools down {@value #FAIL_COOLDOWN} ticks).
	 */
	public static int channel(ServerLevel level, ServerPlayer p, ItemStack stack, InteractionHand hand, int elapsed, long now) {
		if (!ENABLED) {
			stop(p, stack);
			return -1;
		}
		Long next = NEXT_PULSE.get(p);
		if (next != null && now < next) return 0;
		if (!Glimmer.trySpend(p, COST)) { // not enough: nothing spent, the Glimmer bar flashes and clinks
			stop(p, stack);
			return -1;
		}
		Glimmer.consumeMoonlit(p); // the Moonlit bonus is a damage bonus; a heal never uses it
		NEXT_PULSE.put(p, now + PULSE_TICKS);
		pulse(level, p, 1.0F);
		if (elapsed < PULSE_TICKS) Spells.afterCast(p, stack.getItem()); // first pulse of this channel = the cast
		stack.hurtAndBreak(1, p, hand);
		return 1;
	}

	/** Ends the channel and cools the censer down, so a held use key does not restart it every 4 ticks. */
	private static void stop(ServerPlayer p, ItemStack stack) {
		p.stopUsingItem();
		p.getCooldowns().addCooldown(stack, FAIL_COOLDOWN);
	}

	/**
	 * Heals every ally ({@link #isAlly}) within {@value #RADIUS} blocks by 1.5 x {@code scale} and the censer's holder by
	 * 0.5 x {@code scale}; a note rises over each healed ally. Returns how many allies were healed (the holder not counted).
	 */
	public static int pulse(ServerLevel level, ServerPlayer p, float scale) {
		int healed = 0;
		long beat = level.getGameTime() / PULSE_TICKS;
		float note = (float) (beat % CHORD.length) * 6F / 24F;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RADIUS), e -> isAlly(p, e))) {
			if (e.distanceToSqr(p) > RADIUS * RADIUS || e.getHealth() >= e.getMaxHealth()) continue;
			e.heal(ALLY_HEAL * scale);
			// count 0: one note, its colour from the x "speed" (vanilla note-block particle colouring)
			level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.35, e.getZ(), 0, note, 0, 0, 1.0);
			healed++;
		}
		if (p.getHealth() < p.getMaxHealth()) p.heal(SELF_HEAL * scale);
		level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.45F, CHORD[(int) (beat % CHORD.length)]);
		level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.2F, 1.6F);
		return healed;
	}

	/**
	 * Allies of the censer's holder: other players (unless both are on scoreboard teams that are not allied), and the
	 * holder's own minions and pets ({@link OwnableEntity} owned by them). Never the holder, hostile mobs, or another
	 * player's minions.
	 */
	public static boolean isAlly(Player holder, LivingEntity e) {
		if (e == holder || !e.isAlive()) return false;
		if (e instanceof Player o) {
			if (o.isSpectator()) return false;
			// Look teams up by scoreboard name: Fabric's FakePlayer overrides getTeam() to always return null, which made
			// rival-team fake players (and the self-test) count as allies.
			var sb = holder.level().getScoreboard();
			PlayerTeam a = sb.getPlayersTeam(holder.getScoreboardName()), b = sb.getPlayersTeam(o.getScoreboardName());
			return a == null || b == null || a.isAlliedTo(b);
		}
		if (e instanceof OwnableEntity o) {
			EntityReference<LivingEntity> owner = o.getOwnerReference();
			return owner != null && holder.getUUID().equals(owner.getUUID());
		}
		return false;
	}

	/** Self-test: forget a player's pulse timer. */
	public static void forgetForTest(Player p) {
		NEXT_PULSE.remove(p);
	}

	// ---------------------------------------------------------------- client visuals (run on the client only)

	/** Where the swinging censer hangs: in front of the using hand at hip height, swaying forward and back on a 10-tick beat. */
	public static Vec3 censerPos(LivingEntity user, int elapsed) {
		float yaw = user.getYRot() * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		boolean rightHand = (user.getUsedItemHand() == InteractionHand.MAIN_HAND) == (user.getMainArm() == HumanoidArm.RIGHT);
		double sway = Mth.sin(elapsed * 0.628F) * 0.3;
		return user.position().add(0, user.getBbHeight() * 0.45, 0).add(fwd.scale(0.45 + sway)).add(right.scale(rightHand ? 0.38 : -0.38));
	}

	/** Set by the gear3 client entry: true when the user is the local player in the first-person camera. Always false on a server. */
	public static volatile java.util.function.Predicate<LivingEntity> FIRST_PERSON = e -> false;

	/** First person: the held censer as seen on screen, low in the lower right of the view (in front of the eye, to the hand side, below it). */
	public static Vec3 handPos(LivingEntity user, int elapsed) {
		float yaw = user.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		boolean rightHand = (user.getUsedItemHand() == InteractionHand.MAIN_HAND) == (user.getMainArm() == HumanoidArm.RIGHT);
		double sway = Mth.sin(elapsed * 0.628F) * 0.06;
		return user.getEyePosition().add(user.getLookAngle().scale(0.7 + sway)).add(right.scale(rightHand ? 0.3 : -0.3)).add(0, -0.3, 0);
	}

	private static void clientFx(Level level, LivingEntity user, int elapsed) {
		if (!ENABLED) return;
		boolean ring = elapsed % PULSE_TICKS == 0; // checked before the 3-tick gate below: 20 is not a multiple of 3, the ring only came every 3 s
		if (elapsed % 3 != 0 && !ring) return;
		RandomSource r = level.getRandom();
		Vec3 c = censerPos(user, elapsed);
		// first person: the hip-height anchor is below the bottom of the screen, so the fx spawn at the visible held item instead
		Vec3 hand = FIRST_PERSON.test(user) ? handPos(user, elapsed) : c;
		if (elapsed % 3 == 0) level.addParticle(ParticleTypes.WHITE_SMOKE, hand.x, hand.y, hand.z, (r.nextDouble() - 0.5) * 0.02, 0.035, (r.nextDouble() - 0.5) * 0.02);
		if (elapsed % 3 == 0) {
			// gold sparks at the hand: two small gold dust motes drifting up (dust has its own sprite, so nothing can render untextured)
			net.minecraft.core.particles.DustParticleOptions spark = new net.minecraft.core.particles.DustParticleOptions(0xFFD24A, 0.9F);
			for (int i = 0; i < 2; i++) {
				level.addParticle(spark, hand.x + (r.nextDouble() - 0.5) * 0.18, hand.y + r.nextDouble() * 0.12, hand.z + (r.nextDouble() - 0.5) * 0.18,
					(r.nextDouble() - 0.5) * 0.03, 0.03 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.03);
			}
		}
		if (elapsed % 6 == 0) level.addParticle(ParticleTypes.WAX_ON, hand.x, hand.y + 0.1, hand.z, 0, 0.02, 0);
		if (ring) {
			// the reach of the choirlight: a ring of gold sparks at 6 blocks, once a second
			net.minecraft.core.particles.DustParticleOptions gold = new net.minecraft.core.particles.DustParticleOptions(0xFFD24A, 1.5F); // dust lives scale x longer, so the ring outlasts the 1 s beat
			for (int i = 0; i < 64; i++) {
				double a = i * Math.PI * 2 / 64;
				double x = user.getX() + Math.cos(a) * RADIUS, z = user.getZ() + Math.sin(a) * RADIUS;
				level.addParticle(gold, x, user.getY() + 0.3, z, 0, 0, 0);
				if (i % 2 == 0) level.addParticle(ParticleTypes.WAX_ON, x, user.getY() + 0.5, z, 0, 0.02, 0);
			}
		}
	}
}
