package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.stub.summoner.SetBonusStub;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.stub.summoner.TrinketStub;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The gear-driven minion perks of ARSENAL.md §5c/5d, applied by the framework. */
public final class SummonerPerks {
	private SummonerPerks() {}

	public static final String HERDCALLER_SET = "herdcaller";
	static final Identifier PACK_BOND_ARMOR = SummonerContent.id("pack_bond.armor");
	static final Identifier CROWN_HP = SummonerContent.id("elder_bark_crown.rootling_hp");
	static final Identifier BELL_SLOTS = SummonerContent.id("trinket.bell_of_the_flock");
	static final Identifier BELL_GLIMMER = SummonerContent.id("trinket.bell_of_the_flock.glimmer");
	static final Identifier HERD_FOCUS = SummonerContent.id("herd_focus");

	public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> SUMMONER_ARMOR = SummonerItems.tag("summoner_armor");
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** How many summoner armor pieces (Herdcaller, Lined Herdcaller, Elder Bark Crown, Engineer's Harness) are worn. */
	public static int summonerPieces(Player p) {
		int n = 0;
		for (EquipmentSlot s : ARMOR) if (p.getItemBySlot(s).is(SUMMONER_ARMOR)) n++;
		return n;
	}

	/** Herd focus (rev 4): -10% own damage per minion out beyond the first. */
	public static double herdFocus(Player p) {
		int n = p instanceof ServerPlayer sp ? MinionManager.count(sp) : 0;
		return GearStats.HERD_FOCUS_PER_EXTRA_MINION * Math.max(0, n - 1);
	}

	/** Arrows a summoner shoots: -10% per summoner piece worn plus Herd focus (hook for the own-damage con), floor 0. */
	public static double arrowMultiplier(Player p) {
		return Math.max(0.0, 1.0 + GearStats.OWN_DAMAGE_PER_PIECE * summonerPieces(p) + herdFocus(p));
	}

	/** Herd focus on melee and spells: one transient add_multiplied_base modifier on attack_damage and spell_power. */
	public static void updateHerdFocus(ServerPlayer p) {
		double v = herdFocus(p);
		setModifier(p.getAttribute(Attributes.ATTACK_DAMAGE), HERD_FOCUS, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		var spell = SummonerAttributes.spellPower();
		if (spell != null) setModifier(p.getAttribute(spell), HERD_FOCUS, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void setModifier(AttributeInstance inst, Identifier id, double v, AttributeModifier.Operation op) {
		if (inst == null) return;
		AttributeModifier cur = inst.getModifier(id);
		if (v == 0) {
			if (cur != null) inst.removeModifier(id);
			return;
		}
		if (cur != null && cur.amount() == v) return;
		if (cur != null) inst.removeModifier(id);
		inst.addTransientModifier(new AttributeModifier(id, v, op));
	}

	public static boolean packBond(Player owner) {
		return SetBonusStub.wearing(owner, HERDCALLER_SET);
	}

	public static boolean herdcallerBoots(Player owner) {
		ItemStack s = owner.getItemBySlot(EquipmentSlot.FEET);
		return s.is(SummonerItems.HERDCALLER_BOOTS) || s.is(SummonerItems.LINED_HERDCALLER_BOOTS);
	}

	public static boolean fireAndLightningImmune(Player owner) {
		return owner.getItemBySlot(EquipmentSlot.CHEST).is(SummonerItems.ENGINEERS_HARNESS);
	}

	public static boolean elderBarkCrown(Player owner) {
		return owner.getItemBySlot(EquipmentSlot.HEAD).is(SummonerItems.ELDER_BARK_CROWN);
	}

	public static double teleportDistance(Player owner) {
		return herdcallerBoots(owner) ? GearStats.TELEPORT_DISTANCE_BOOTS : GearStats.TELEPORT_DISTANCE;
	}

	public static double speedMultiplier(Player owner) {
		return herdcallerBoots(owner) ? 1.0 + GearStats.BOOTS_MINION_SPEED : 1.0;
	}

	/** Once per second per minion: Pack Bond (heal 1, +4 armor within 8 blocks), Elder Bark Crown (+50% Rootling HP). */
	public static void minionSecond(MinionEntity m, ServerPlayer owner) {
		boolean bond = packBond(owner) && m.distanceTo(owner) <= GearStats.PACK_BOND_RANGE;
		AttributeInstance armor = m.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			if (bond && !armor.hasModifier(PACK_BOND_ARMOR)) armor.addTransientModifier(new AttributeModifier(PACK_BOND_ARMOR, GearStats.PACK_BOND_ARMOR, AttributeModifier.Operation.ADD_VALUE));
			else if (!bond && armor.hasModifier(PACK_BOND_ARMOR)) armor.removeModifier(PACK_BOND_ARMOR);
		}
		if (bond && m.getHealth() < m.getMaxHealth()) m.heal(GearStats.PACK_BOND_HEAL_PER_S);
		if (m instanceof Rootling) {
			boolean crown = elderBarkCrown(owner);
			AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				if (crown && !hp.hasModifier(CROWN_HP)) {
					hp.addTransientModifier(new AttributeModifier(CROWN_HP, GearStats.CROWN_ROOTLING_HP, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
					m.heal((float) (GearStats.ROOTLING_HP * GearStats.CROWN_ROOTLING_HP));
				} else if (!crown && hp.hasModifier(CROWN_HP)) {
					hp.removeModifier(CROWN_HP);
					if (m.getHealth() > m.getMaxHealth()) m.setHealth(m.getMaxHealth());
				}
			}
		}
	}

	/** Pack Bond: a dying minion gives its 10 Glimmer back. */
	public static void onMinionDied(MinionEntity m, ServerPlayer owner) {
		if (packBond(owner)) GlimmerStub.refund(owner, GearStats.PACK_BOND_REFUND);
	}

	/** Bell of the Flock: +1 minion slot while it is an active trinket and at least one summoner armor piece is worn. */
	public static void updateTrinketSlots(ServerPlayer p) {
		var h = SummonerAttributes.slots();
		if (h == null) return;
		AttributeInstance inst = p.getAttribute(h);
		if (inst == null) return;
		boolean bell = TrinketStub.isActive(p, SummonerItems.BELL_OF_THE_FLOCK) && summonerPieces(p) > 0;
		var glimmer = SummonerAttributes.maxGlimmer();
		if (glimmer != null) setModifier(p.getAttribute(glimmer), BELL_GLIMMER, bell ? GearStats.BELL_MAX_GLIMMER : 0, AttributeModifier.Operation.ADD_VALUE);
		if (bell && !inst.hasModifier(BELL_SLOTS)) inst.addTransientModifier(new AttributeModifier(BELL_SLOTS, GearStats.BELL_SLOTS, AttributeModifier.Operation.ADD_VALUE));
		else if (!bell && inst.hasModifier(BELL_SLOTS)) {
			inst.removeModifier(BELL_SLOTS);
			MinionManager.enforceSlots(p);
		}
	}

	private static boolean hooks;

	/** Arrow penalty (on spawn) and the staff call being interrupted by damage. */
	public static void initHooks() {
		if (hooks) return;
		hooks = true;
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.tickCount == 0
				&& arrow.getOwner() instanceof Player p && !(e.entityTags().contains("elsewhere_summoner_scaled"))) {
				double m = arrowMultiplier(p);
				if (m < 1.0) {
					arrow.setBaseDamage(((dev.elsewhere.summoner.mixin.AbstractArrowAccessor) arrow).elsewhere_summoner$getBaseDamage() * m);
					arrow.addTag("elsewhere_summoner_scaled");
				}
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken > 0 && victim instanceof ServerPlayer p && p.isUsingItem() && p.getUseItem().getItem() instanceof dev.elsewhere.minion.MinionStaffItem) {
				p.stopUsingItem();
			}
		});
	}
}
