package dev.elsewhere.kingdom_court.npc;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A non-combat kingdom NPC (Lawspeaker, Royal Envoy, Clerk of the Counting-House): invulnerable, stands where it is placed and talks. */
public class DiplomatNpc extends KingdomSoldier {
	public final Diplomacy.Role role;

	public DiplomatNpc(EntityType<? extends KingdomSoldier> type, Level level, Diplomacy.Role role) {
		super(type, level);
		this.role = role;
		setPermanentlyInvulnerable(true);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return false;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	public static final String PATRON_TAG = "kingdom_patron:";

	/** The Magnate this trader serves (a leader slot key), or null. Saved as an entity tag. A miser's trader is harder to haggle with. */
	public @org.jspecify.annotations.Nullable String patron() {
		for (String t : entityTags()) if (t.startsWith(PATRON_TAG)) return t.substring(PATRON_TAG.length());
		return null;
	}

	public void setPatron(@org.jspecify.annotations.Nullable String slot) {
		entityTags().removeIf(t -> t.startsWith(PATRON_TAG));
		if (slot != null) addTag(PATRON_TAG + slot);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		dev.elsewhere.kingdom_court.trade.TradeUi.interact(sp, this);
		return InteractionResult.SUCCESS;
	}
}
