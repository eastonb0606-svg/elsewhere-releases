package dev.elsewhere.kingdom_court.ruler;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_court.CourtCombat;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.api.HpCap;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import org.jspecify.annotations.Nullable;

/**
 * A Lord or the King: a {@link KingdomSoldier} (player model, player-like shield/crit/sprint combat at full strength,
 * which the rank and file only get rarely) with a boss bar, trimmed armour and crown, phases and a unique kit.
 * <ul>
 * <li>Marshal Ysolde Varr: mounted lancer; telegraphed lane charge (horn + particle lane), stunned 3 s if she hits a
 * wall; below 50% she dismounts and fights with a war-axe.</li>
 * <li>Castellan Bertrand Holm: tower shield (blocks 95%, axes break it as for a player); drops a portcullis of iron bars
 * between you (air only, gone after 6 s); murder-hole oil (dripping telegraph, then 6 damage and fire).</li>
 * <li>Master-at-Arms Corvin Thale: gleam then a 0.7 s parry window that negates a melee hit and ripostes with a crit;
 * copies your weapon style (axe or sword).</li>
 * <li>Royal Huntsman Lisbet Ashgrove: bow; Hunter's Mark (Glowing, her arrows +30%); 3 hounds at the start and 3 more
 * at half health (at most 4 alive); snares (Slowness IV + 2 damage).</li>
 * <li>Lord Chancellor Marek Dunmere (quarter health): no weapon; slips away through "secret doors" (short teleport);
 * decrees in turn: Silence of Writ (no bows/crossbows/tridents 6 s), Tithe (+1 durability per hit 10 s), Summons (2
 * men-at-arms).</li>
 * <li>King Osric Vael: phase 1 the Court (shield-bash opener, critical leap, cleave); phase 2 the Warhorse below 66%
 * (barded horse, lane charges; hit the horse to half and he is unseated and stunned 6 s); phase 3 the Hollow Crown
 * below 33% (shield dropped, two-handed blows +3, grab-and-slam: 70% of max health, survivable from full, last guards,
 * gentle enrage +1% speed per 5 s up to +20%). Each living Lord adds 12 max health, +4% damage and one kit element.</li>
 * </ul>
 */
public class CourtRuler extends KingdomSoldier {
	public static final Identifier CROWNED_HEALTH = CourtContent.id("court_crowned_health");
	public static final Identifier CROWNED_DAMAGE = CourtContent.id("court_crowned_damage");
	public static final Identifier GATING_DAMAGE = CourtContent.id("court_lord_gating");
	public static final Identifier GREATSWORD = CourtContent.id("court_greatsword");
	public static final Identifier ENRAGE = CourtContent.id("court_enrage");

	public final Ruler ruler;
	public int phase;
	public boolean crowned;
	public @Nullable BlockPos home;
	public int stunTicks, telegraph, chargeTicks, slamTelegraph, oilTicks, leapTicks;
	public int cdA = 60, cdB = 100, cdC = 160, cdD = 200, cdE = 140, decreeIdx, enrageStacks;
	public long parryUntil;
	public boolean opened, hounds1, hounds2, gatedSpawned, leaping;
	public @Nullable Vec3 chargeDir;
	public @Nullable BlockPos oilPos;
	public final List<BlockPos> snares = new ArrayList<>();
	public long snareUntil;
	public int parriesDone, decreesCast, chargesDone, wallStuns, houndsCalled, slams, cleaves, leaps;
	public @Nullable UUID warhorse;
	private final Set<Integer> chargeHit = new HashSet<>();
	private final ServerBossEvent bar;

	public CourtRuler(EntityType<? extends CourtRuler> type, Level level, Ruler ruler) {
		super(type, level);
		this.ruler = ruler;
		this.xpReward = ruler == Ruler.KING ? 120 : 60;
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(ruler.nameKey()),
			ruler == Ruler.KING ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	}

	// ---- loadout ------------------------------------------------------------------------------------------------

	@Override
	public void applyLoadout() {
		super.applyLoadout();
		if (!(level() instanceof ServerLevel sl)) return;
		setCustomName(CourtRulers.displayName(factionId(), ruler));
		bar.setName(CourtRulers.displayName(factionId(), ruler));
		var mats = sl.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		var pats = sl.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
		ResourceKey<TrimMaterial> m = switch (ruler) {
			case KING -> TrimMaterials.GOLD;
			case MARSHAL -> TrimMaterials.REDSTONE;
			case CASTELLAN -> TrimMaterials.IRON;
			case MASTER_AT_ARMS -> TrimMaterials.DIAMOND;
			case HUNTSMAN -> TrimMaterials.EMERALD;
			case CHANCELLOR -> TrimMaterials.AMETHYST;
		};
		ResourceKey<TrimPattern> p = switch (ruler) {
			case KING -> TrimPatterns.SILENCE;
			case MARSHAL -> TrimPatterns.SENTRY;
			case CASTELLAN -> TrimPatterns.WARD;
			case MASTER_AT_ARMS -> TrimPatterns.SPIRE;
			case HUNTSMAN -> TrimPatterns.WILD;
			case CHANCELLOR -> TrimPatterns.EYE;
		};
		var mh = mats.get(m);
		var ph = pats.get(p);
		if (mh.isPresent() && ph.isPresent()) {
			ArmorTrim trim = new ArmorTrim(mh.get(), ph.get());
			for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack st = getItemBySlot(s);
				if (!st.isEmpty()) st.set(DataComponents.TRIM, trim);
			}
		}
		for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0.0F);
		if (crowned) wearCrown();
	}

	void wearCrown() {
		ItemStack crown = new ItemStack(Items.GOLDEN_HELMET);
		if (level() instanceof ServerLevel sl) {
			var mh = sl.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(TrimMaterials.GOLD);
			var ph = sl.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(TrimPatterns.SILENCE);
			if (mh.isPresent() && ph.isPresent()) crown.set(DataComponents.TRIM, new ArmorTrim(mh.get(), ph.get()));
		}
		setItemSlot(EquipmentSlot.HEAD, crown);
		setDropChance(EquipmentSlot.HEAD, 0.0F);
	}

	/** Succession: this Lord takes the throne (+15% max health, +10% damage, the crown, the King's leap and cleave). */
	public void setCrowned(boolean c) {
		if (c == crowned) return;
		crowned = c;
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
		if (c) {
			float ratio = getHealth() / getMaxHealth();
			if (!hp.hasModifier(CROWNED_HEALTH)) hp.addPermanentModifier(new AttributeModifier(CROWNED_HEALTH, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			if (!dmg.hasModifier(CROWNED_DAMAGE)) dmg.addPermanentModifier(new AttributeModifier(CROWNED_DAMAGE, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			HpCap.clamp(this);
			setHealth(Math.min(getMaxHealth(), ratio * getMaxHealth() + 0.15F * getMaxHealth()));
			wearCrown();
			if (level() instanceof ServerLevel sl && CourtState.get(sl, factionId()).site != null) home = CastleLayout.throneFront(CourtState.get(sl, factionId()).site);
		} else {
			hp.removeModifier(CROWNED_HEALTH);
			dmg.removeModifier(CROWNED_DAMAGE);
		}
	}

	/**
	 * The faction's base max health for this slot (its file's number; Aldermark's equal the 2.0 constants). Called when the
	 * ruler is spawned.
	 */
	public static final Identifier COMP_ARMOR = CourtContent.id("court_hp_comp_armor"), COMP_TOUGH = CourtContent.id("court_hp_comp_toughness"), COMP_KB = CourtContent.id("court_hp_comp_kb"),
		COMP_DMG = CourtContent.id("court_hp_comp_damage");

	/**
	 * RIVALS-DECISIONS 13, Aldermark's King and Lords: their health was scaled down to fit the 300 cap, so they get +6 armor and +3 toughness (the King +8 and +4), +0.4 knockback
	 * resistance and +25 percent damage; move cooldowns are 20 percent shorter and defensive moves (parry, portcullis) 30 percent shorter ({@link #cd}, {@link #cdDef}). Idempotent.
	 */
	public void compensate() {
		if (!dev.elsewhere.kingdom.Factions.ALDERMARK.equals(factionId())) return;
		boolean king = ruler == Ruler.KING;
		put(Attributes.ARMOR, COMP_ARMOR, king ? 8.0 : 6.0, AttributeModifier.Operation.ADD_VALUE);
		put(Attributes.ARMOR_TOUGHNESS, COMP_TOUGH, king ? 4.0 : 3.0, AttributeModifier.Operation.ADD_VALUE);
		put(Attributes.KNOCKBACK_RESISTANCE, COMP_KB, 0.4, AttributeModifier.Operation.ADD_VALUE);
		put(Attributes.ATTACK_DAMAGE, COMP_DMG, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private void put(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, Identifier id, double v, AttributeModifier.Operation op) {
		AttributeInstance i = getAttribute(a);
		if (i != null && !i.hasModifier(id)) i.addPermanentModifier(new AttributeModifier(id, v, op));
	}

	/** RIVALS-DECISIONS 13: raw damage of the ruler's own moves, +25 percent for Aldermark's rulers (the weapon swing already has it through the attack attribute). */
	public float dm(float raw) {
		return aldermark() ? raw * 1.25F : raw;
	}

	/** Castellan's tower-shield raise interval: every 60 ticks, 30 percent more often for Aldermark (46). */
	public int shieldRaiseInterval() {
		return aldermark() ? cdDef(60) : 60;
	}

	boolean aldermark() {
		return dev.elsewhere.kingdom.Factions.ALDERMARK.equals(factionId());
	}

	/** A move cooldown: 20 percent shorter for Aldermark's rulers. */
	public int cd(int ticks) {
		return aldermark() ? Math.max(1, (int) Math.round(ticks * 0.8)) : ticks;
	}

	/** A defensive move's cooldown: 30 percent shorter (used 30 percent more often). */
	public int cdDef(int ticks) {
		return aldermark() ? Math.max(1, (int) Math.round(ticks / 1.3)) : ticks;
	}

	public void applyBaseHealth() {
		compensate();
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		double base = CourtRulers.baseHealth(factionId(), ruler);
		if (hp.getBaseValue() != base) {
			hp.setBaseValue(base);
			setHealth(getMaxHealth());
		}
		HpCap.clamp(this);
	}

	/**
	 * Ruler bonus (the faction's {@code society.ruler_bonus}). The holder is the King (monarchy) or the Chair (oligarchy):
	 * {@code hp_per_lord}: +amount max health and +4% damage per living Lord (Aldermark: +12 each); {@code hp_per_other_magnate}:
	 * +amount max health per other living Magnate; {@code damage_per_champion}: +amount damage per living champion
	 * (Hrafnholt's Hird-oath, 0.15 = +15%); {@code none}.
	 */
	public void refreshGating(CourtState st) {
		boolean holder = st.isHolder(ruler);
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
		compensate();
		if (!holder) {
			dmg.removeModifier(GATING_DAMAGE);
			double b = CourtRulers.baseHealth(st.faction, ruler);
			if (hp.getBaseValue() != b) {
				hp.setBaseValue(b);
				if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
			}
			HpCap.clamp(this);
			return;
		}
		var def = st.def();
		var bonus = def == null ? dev.elsewhere.kingdom.FactionDef.RulerBonus.ALDERMARK : def.society().bonus();
		int n = st.livingLords() - (st.oligarchy() && st.alive(ruler) ? 1 : 0);
		double base = CourtRulers.baseHealth(st.faction, ruler);
		double want = base, dmgMul = 0;
		switch (bonus.type()) {
			case "hp_per_lord" -> {
				want = base + bonus.amount() * CourtRulers.hpScale(st.faction) * n;
				dmgMul = 0.04 * n;
			}
			case "hp_per_other_magnate" -> want = base + bonus.amount() * n;
			case "damage_per_champion" -> dmgMul = bonus.amount() * n;
			default -> {
			}
		}
		want = Math.min(want, Math.max(CourtRulers.healthCap(st.faction, ruler), base));
		if (hp.getBaseValue() != want) {
			hp.setBaseValue(want);
			if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
		}
		HpCap.clamp(this);
		dmg.removeModifier(GATING_DAMAGE);
		if (dmgMul > 0) dmg.addTransientModifier(new AttributeModifier(GATING_DAMAGE, dmgMul, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	// ---- save ---------------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("court_phase", phase);
		out.putInt("court_crowned", crowned ? 1 : 0);
		if (home != null) out.putLong("court_home", home.asLong());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		phase = in.getIntOr("court_phase", 0);
		crowned = in.getIntOr("court_crowned", 0) == 1;
		long h = in.getLongOr("court_home", Long.MIN_VALUE);
		home = h == Long.MIN_VALUE ? null : BlockPos.of(h);
	}

	// ---- damage ------------------------------------------------------------------------------------------------------

	public boolean parrying() {
		return level().getGameTime() <= parryUntil;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource src, float dmg) {
		Entity att = src.getEntity();
		if (parrying() && att instanceof LivingEntity la && src.getDirectEntity() == att && distanceToSqr(att) < 25) {
			parryUntil = 0;
			parriesDone++;
			level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.5F);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 12, 0.3, 0.3, 0.3, 0.2);
			pendingCrit = true;
			strike(level, la);
			return false;
		}
		return super.hurtServer(level, src, dmg);
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		bar.removeAllPlayers();
		clearSnares();
		super.remove(reason);
	}

	@Override
	public void die(DamageSource src) {
		bar.removeAllPlayers();
		super.die(src);
	}

	// ---- tick -----------------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		rulerTick(level);
	}

	void rulerTick(ServerLevel level) {
		long now = level.getGameTime();
		if (tickCount % 20 == 0) {
			CourtState st = CourtState.get(level, factionId());
			UUID want = st.rulerIds.get(ruler);
			if (!st.alive(ruler) || (want != null && !want.equals(getUUID()))) {
				discard();
				return;
			}
			setCrowned(ruler.isLord() && st.crowned == ruler && !st.oligarchy());
			refreshGating(st);
		}
		if (tickCount % 10 == 0) updateBar(level);
		tickSnares(level, now);
		if (oilTicks > 0 && --oilTicks % 5 == 0) oil(level);
		if (stunTicks > 0) {
			stunTicks--;
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (stunTicks % 10 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.1, getZ(), 4, 0.3, 0.1, 0.3, 0.0);
			return;
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive()) {
			goHome(level);
			return;
		}
		cdA--; cdB--; cdC--; cdD--; cdE--;
		double d2 = distanceToSqr(t);
		switch (ruler) {
			case MARSHAL -> marshal(level, t, d2);
			case CASTELLAN -> castellan(level, t, d2);
			case MASTER_AT_ARMS -> master(level, t, d2, 80);
			case HUNTSMAN -> huntsman(level, t, d2, now);
			case CHANCELLOR -> chancellor(level, t, d2, now);
			case KING -> king(level, t, d2, now);
		}
		if (crowned && ruler != Ruler.KING) {
			kingLeap(level, t, d2, 160);
			kingCleave(level, t, d2, 100);
		}
	}

	void goHome(ServerLevel level) {
		if (home == null || squad() != null || isPassenger() || tickCount % 100 != (getId() % 100)) return;
		if (distanceToSqr(Vec3.atBottomCenterOf(home)) > 36 && KingdomPerf.tryPath(level)) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
	}

	void updateBar(ServerLevel level) {
		bar.setProgress(Math.max(0, getHealth() / getMaxHealth()));
		boolean fighting = getTarget() != null || getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 200;
		for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!fighting || p.distanceToSqr(this) > 48 * 48 || !p.isAlive()) bar.removePlayer(p);
		if (fighting) for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) <= 40 * 40 && !bar.getPlayers().contains(p)) bar.addPlayer(p);
	}

	public int barViewers() {
		return bar.getPlayers().size();
	}

	// ---- shared moves ---------------------------------------------------------------------------------------------

	boolean foe(Entity e) {
		if (e == this || !(e instanceof LivingEntity le) || !le.isAlive() || e == getVehicle()) return false;
		if (e instanceof KingdomSoldier ks) return Factions.hostile(factionId(), ks.factionId());
		if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
		return e == getTarget() || !(e instanceof AbstractHorse) && !(e instanceof Wolf w && w.entityTags().contains(CourtLife.HOUND_TAG));
	}

	void hit(ServerLevel level, LivingEntity e, float dmg, double kb) {
		e.hurtServer(level, damageSources().mobAttack(this), dm(dmg));
		double dx = e.getX() - getX(), dz = e.getZ() - getZ();
		if (kb > 0) e.knockback(kb, -dx, -dz, damageSources().mobAttack(this), 0.0F);
	}

	void sound(ServerLevel level, SoundEvent s, float vol, float pitch) {
		level.playSound(null, blockPosition(), s, SoundSource.HOSTILE, vol, pitch);
	}

	void sound(ServerLevel level, Holder<SoundEvent> s, float vol, float pitch) {
		sound(level, s.value(), vol, pitch);
	}

	void particles(ServerLevel level, ParticleOptions p, Vec3 at, int n, double spread) {
		level.sendParticles(p, at.x, at.y, at.z, n, spread, spread * 0.5, spread, 0.02);
	}

	/** Telegraphed lane charge on a mount (Marshal; King phase 2). */
	void laneCharge(ServerLevel level, LivingEntity t, double d2, int cooldown) {
		if (!(getVehicle() instanceof LivingEntity mount)) return;
		if (telegraph > 0) {
			telegraph--;
			if (chargeDir != null) for (int i = 1; i <= 12; i += 2) particles(level, ParticleTypes.FLAME, position().add(chargeDir.scale(i)).add(0, 0.2, 0), 1, 0.1);
			if (telegraph == 0) {
				chargeTicks = 25;
				chargeHit.clear();
				chargesDone++;
			}
			return;
		}
		if (chargeTicks > 0) {
			chargeTicks--;
			if (chargeDir != null) {
				mount.setDeltaMovement(chargeDir.x * 0.85, mount.getDeltaMovement().y, chargeDir.z * 0.85);
				mount.setYRot((float) Math.toDegrees(Math.atan2(-chargeDir.x, chargeDir.z)));
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mount.getBoundingBox().inflate(0.8), this::foe))
					if (chargeHit.add(e.getId())) hit(level, e, ruler == Ruler.KING ? 11.0F : 10.0F, 1.2);
				if (mount.horizontalCollision && chargeTicks < 22) {
					chargeTicks = 0;
					stunTicks = ruler == Ruler.KING ? 40 : 60;
					wallStuns++;
					sound(level, SoundEvents.ANVIL_LAND, 0.8F, 0.6F);
				}
			}
			return;
		}
		if (cdA <= 0 && d2 > 36 && d2 < 24 * 24) {
			Vec3 v = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ()).normalize();
			chargeDir = v;
			telegraph = ruler == Ruler.KING && phase == 2 ? 12 : 20;
			cdA = cd(cooldown);
			if (dev.elsewhere.kingdom_court.Sfx3.enabled() && dev.elsewhere.kingdom_court.Sfx3.has("aldermark", "trumpet")) sound(level, dev.elsewhere.kingdom_court.Sfx3.get("aldermark", "trumpet"), 1.6F, 1.0F); else sound(level, SoundEvents.RAID_HORN, 1.2F, 1.2F);
		}
	}

	void kingLeap(ServerLevel level, LivingEntity t, double d2, int cooldown) {
		if (leaping) {
			if (onGround() && ++leapTicks > 4) {
				leaping = false;
				leaps++;
				particles(level, ParticleTypes.EXPLOSION, position(), 2, 0.5);
				sound(level, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.3F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.0, 1.0, 3.0), this::foe)) hit(level, e, 8.0F, 1.0);
			}
			return;
		}
		if (cdC <= 0 && d2 > 9 && d2 < 100 && onGround() && !isPassenger()) {
			Vec3 v = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
			setDeltaMovement(v.x * 0.2, 0.75, v.z * 0.2);
			leaping = true;
			leapTicks = 0;
			cdC = cd(cooldown);
			particles(level, ParticleTypes.CLOUD, position(), 6, 0.4);
		}
	}

	void kingCleave(ServerLevel level, LivingEntity t, double d2, int cooldown) {
		if (cdB > 0 || d2 > 12) return;
		cdB = cd(cooldown);
		cleaves++;
		swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
		particles(level, ParticleTypes.SWEEP_ATTACK, position().add(look.scale(1.5)).add(0, 1, 0), 3, 0.6);
		sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 1.0, 3.5), this::foe)) {
			Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ()).normalize();
			if (to.dot(look) > 0.3 || e == t) hit(level, e, 7.0F, 0.6);
		}
	}

	void parryCast(ServerLevel level, LivingEntity t, double d2, int cooldown, int window) {
		if (cdE > 0 || d2 > 25) return;
		cdE = cdDef(cooldown);
		parryUntil = level.getGameTime() + window;
		particles(level, ParticleTypes.END_ROD, position().add(0, 1.3, 0), 6, 0.25);
		if (dev.elsewhere.kingdom_court.Sfx3.enabled() && dev.elsewhere.kingdom_court.Sfx3.has("aldermark", "steel")) sound(level, dev.elsewhere.kingdom_court.Sfx3.get("aldermark", "steel"), 1.0F, 1.0F); else sound(level, SoundEvents.ANVIL_PLACE, 0.35F, 2.0F);
	}

	void portcullis(ServerLevel level, LivingEntity t, double d2, int cooldown) {
		if (cdD > 0 || d2 < 16 || d2 > 144) return;
		cdD = cdDef(cooldown);
		Vec3 mid = position().add(t.position()).scale(0.5);
		Vec3 perp = new Vec3(-(t.getZ() - getZ()), 0, t.getX() - getX()).normalize();
		BlockPos base = BlockPos.containing(mid);
		int placed = 0;
		for (int i = -2; i <= 2; i++) for (int y = 0; y <= 2; y++) {
			BlockPos p = BlockPos.containing(base.getX() + 0.5 + perp.x * i, base.getY() + y, base.getZ() + 0.5 + perp.z * i);
			if (level.getBlockState(p).isAir() && CourtLife.placeTemp(level, p, Blocks.IRON_BARS.defaultBlockState(), level.getGameTime() + 120)) placed++;
		}
		if (placed > 0) sound(level, SoundEvents.CHAIN_PLACE, 1.2F, 0.6F);
	}

	void decree(ServerLevel level, LivingEntity t, long now) {
		int k = decreeIdx++ % 3;
		decreesCast++;
		particles(level, ParticleTypes.ENCHANT, position().add(0, 1.5, 0), 20, 0.6);
		sound(level, SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.7F);
		if (k == 0 && t instanceof Player p) {
			CourtCombat.silence(p, now + 120);
			p.sendOverlayMessage(Component.translatable("message.elsewhere.court_decree_silence"));
		} else if (k == 1 && t instanceof Player p) {
			CourtCombat.tithe(p, now + 200);
			p.sendOverlayMessage(Component.translatable("message.elsewhere.court_decree_tithe"));
		} else {
			for (int i = 0; i < 2; i++) {
				Vec3 at = position().add(level.getRandom().nextGaussian() * 2, 0, level.getRandom().nextGaussian() * 2);
				KingdomSoldier s = KingdomApi.spawnSoldier(level, UnitTypes.MAN_AT_ARMS, factionId(), at, squad());
				if (s != null) s.setTarget(t);
			}
			if (t instanceof Player p) p.sendOverlayMessage(Component.translatable("message.elsewhere.court_decree_summons"));
		}
	}

	/** Self-test hook: runs one ability (or this ruler's whole per-tick kit) against {@code t} right now. */
	public void testAbility(ServerLevel level, String what, LivingEntity t) {
		double d2 = distanceToSqr(t);
		long now = level.getGameTime();
		switch (what) {
			case "parry" -> { cdE = 0; parryCast(level, t, Math.min(d2, 4), 80, 14); }
			case "portcullis" -> { cdD = 0; portcullis(level, t, 64, 300); }
			case "decree" -> decree(level, t, now);
			case "hounds" -> callHounds(level, t, 3);
			case "charge" -> { cdA = 0; telegraph = 0; chargeTicks = 0; laneCharge(level, t, 100, 160); }
			case "oil" -> { oilPos = t.blockPosition(); oilTicks = 0; oil(level); }
			case "snare" -> { cdB = 0; huntsman(level, t, Math.min(d2, 100), now); }
			default -> {
				switch (ruler) {
					case MARSHAL -> marshal(level, t, d2);
					case CASTELLAN -> castellan(level, t, d2);
					case MASTER_AT_ARMS -> master(level, t, d2, 80);
					case HUNTSMAN -> huntsman(level, t, d2, now);
					case CHANCELLOR -> chancellor(level, t, d2, now);
					case KING -> king(level, t, d2, now);
				}
			}
		}
	}

	// ---- per ruler ---------------------------------------------------------------------------------------------------

	void marshal(ServerLevel level, LivingEntity t, double d2) {
		if (phase == 0 && getHealth() <= getMaxHealth() * 0.5F) {
			phase = 1;
			if (getVehicle() != null) stopRiding();
			float h = getHealth();
			setUnit(CourtRulers.MARSHAL_FOOT);
			setHealth(h);
			if (dev.elsewhere.kingdom_court.Sfx3.enabled() && dev.elsewhere.kingdom_court.Sfx3.has("aldermark", "trumpet")) sound(level, dev.elsewhere.kingdom_court.Sfx3.get("aldermark", "trumpet"), 1.4F, 0.85F); else sound(level, SoundEvents.RAID_HORN, 1.0F, 0.7F);
			return;
		}
		if (phase == 0) laneCharge(level, t, d2, 160);
	}

	void castellan(ServerLevel level, LivingEntity t, double d2) {
		if (d2 < 25 && tickCount % shieldRaiseInterval() == 0) forcedShieldTicks = Math.max(forcedShieldTicks, 30);
		portcullis(level, t, d2, 300);
		if (cdB <= 0 && d2 < 144) {
			cdB = cd(200);
			oilPos = t.blockPosition();
			oilTicks = 31;
		}
	}

	void oil(ServerLevel level) {
		if (oilPos == null) return;
		Vec3 c = Vec3.atBottomCenterOf(oilPos);
		if (oilTicks > 0) {
			particles(level, ParticleTypes.DRIPPING_LAVA, c.add(0, 3, 0), 8, 1.0);
			return;
		}
		particles(level, ParticleTypes.LAVA, c, 12, 1.0);
		sound(level, SoundEvents.LAVA_POP, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(oilPos).inflate(1.5, 1.0, 1.5), this::foe)) {
			e.hurtServer(level, damageSources().mobAttack(this), dm(6.0F));
			e.igniteForSeconds(4);
		}
		oilPos = null;
	}

	void master(ServerLevel level, LivingEntity t, double d2, int parryCd) {
		parryCast(level, t, d2, parryCd, 14);
		if (tickCount % 100 == 0) {
			ItemStack want = t.getMainHandItem().is(ItemTags.AXES) ? new ItemStack(Items.DIAMOND_AXE) : new ItemStack(Items.DIAMOND_SWORD);
			if (!getMainHandItem().is(want.getItem())) setItemSlot(EquipmentSlot.MAINHAND, want);
		}
	}

	void huntsman(ServerLevel level, LivingEntity t, double d2, long now) {
		if (!hounds1) {
			hounds1 = true;
			callHounds(level, t, 3);
		} else if (!hounds2 && getHealth() <= getMaxHealth() * 0.5F) {
			hounds2 = true;
			callHounds(level, t, 3);
		}
		if (cdA <= 0 && d2 < 32 * 32) {
			cdA = cd(200);
			CourtCombat.mark(t, now + 120);
			sound(level, SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 1.4F);
		}
		if (cdB <= 0 && d2 < 20 * 20) {
			cdB = cd(240);
			clearSnares();
			for (int i = 0; i < 3; i++) {
				BlockPos p = BlockPos.containing(t.getX() + level.getRandom().nextGaussian() * 2.5, t.getY(), t.getZ() + level.getRandom().nextGaussian() * 2.5);
				snares.add(p);
			}
			snareUntil = now + 300;
		}
	}

	void callHounds(ServerLevel level, LivingEntity t, int n) {
		int alive = level.getEntitiesOfClass(Wolf.class, getBoundingBox().inflate(32), w -> w.entityTags().contains(CourtLife.HOUND_TAG)).size();
		for (int i = 0; i < n && alive < 4; i++, alive++) {
			Wolf w = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (w == null) return;
			w.snapTo(getX() + level.getRandom().nextGaussian(), getY(), getZ() + level.getRandom().nextGaussian(), 0F, 0F);
			w.setCustomName(Component.translatable("entity.elsewhere.court_royal_hound"));
			w.addTag(CourtLife.HOUND_TAG);
			level.addFreshEntity(w);
			w.setPersistentAngerTarget(EntityReference.of(t));
			w.startPersistentAngerTimer();
			w.setTarget(t);
			houndsCalled++;
		}
		if (dev.elsewhere.kingdom_court.Sfx3.enabled() && dev.elsewhere.kingdom_court.Sfx3.has("aldermark", "hunt")) sound(level, dev.elsewhere.kingdom_court.Sfx3.get("aldermark", "hunt"), 1.6F, 1.0F); else sound(level, SoundEvents.RAVAGER_ROAR, 1.0F, 1.0F);
	}

	void tickSnares(ServerLevel level, long now) {
		if (snares.isEmpty()) return;
		if (now > snareUntil) {
			clearSnares();
			return;
		}
		if (now % 5 != 0) return;
		for (int i = snares.size() - 1; i >= 0; i--) {
			BlockPos p = snares.get(i);
			if (now % 20 == 0) level.sendParticles(ParticleTypes.ASH, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 3, 0.3, 0.0, 0.3, 0.0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p).inflate(0.4, 0.5, 0.4), this::foe)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3));
				e.hurtServer(level, damageSources().mobAttack(this), dm(2.0F));
				snares.remove(i);
				break;
			}
		}
	}

	void clearSnares() {
		snares.clear();
	}

	void chancellor(ServerLevel level, LivingEntity t, double d2, long now) {
		if (d2 < 36 && cdA <= 0) {
			cdA = cd(120);
			particles(level, ParticleTypes.SMOKE, position().add(0, 1, 0), 20, 0.5);
			for (int i = 0; i < 8; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2, r = 8 + level.getRandom().nextDouble() * 6;
				if (randomTeleport(getX() + Math.cos(a) * r, getY() + 2, getZ() + Math.sin(a) * r, true, s -> true)) break;
			}
			sound(level, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 0.8F);
		} else if (d2 < 16) {
			getMoveControl().strafe(-0.6F, 0.0F);
		}
		if (cdB <= 0 && d2 < 24 * 24) {
			cdB = cd(160);
			decree(level, t, now);
		}
	}

	void king(ServerLevel level, LivingEntity t, double d2, long now) {
		CourtState st = CourtState.get(level, factionId());
		float hp = getHealth() / getMaxHealth();
		if (phase == 0 && hp <= 0.66F) {
			phase = 1;
			mountWarhorse(level);
			if (st.alive(Ruler.CHANCELLOR)) decree(level, t, now);
		}
		if (phase == 1 && (hp <= 0.33F || unseated(level))) {
			phase = 2;
			if (getVehicle() != null) {
				stopRiding();
				if (hp > 0.33F) stunTicks = 120;
			}
			setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
			if (!dmg.hasModifier(GREATSWORD)) dmg.addPermanentModifier(new AttributeModifier(GREATSWORD, 3.0, AttributeModifier.Operation.ADD_VALUE));
			sound(level, SoundEvents.ITEM_BREAK.value(), 1.5F, 0.5F);
			particles(level, ParticleTypes.ELECTRIC_SPARK, position().add(0, 2.2, 0), 20, 0.3);
			for (int i = 0; i < 3; i++) {
				KingdomSoldier g = KingdomApi.spawnSoldier(level, UnitTypes.KNIGHT, factionId(), position().add(level.getRandom().nextGaussian() * 3, 0, level.getRandom().nextGaussian() * 3), null);
				if (g != null) g.setTarget(t);
			}
			if (st.alive(Ruler.CHANCELLOR)) decree(level, t, now);
			return;
		}
		if (!gatedSpawned) {
			gatedSpawned = true;
			gatedReinforcements(level, t, st);
		}
		if (phase == 0) {
			if (!opened && d2 < 9) {
				opened = true;
				forcedShieldTicks = 0;
				hit(level, t, 5.0F, 1.5);
				sound(level, SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.6F);
			}
			kingLeap(level, t, d2, 140);
			kingCleave(level, t, d2, 80);
		} else if (phase == 1) {
			laneCharge(level, t, d2, 120);
			if (getVehicle() == null) kingCleave(level, t, d2, 80);
		} else {
			kingLeap(level, t, d2, 84);
			kingCleave(level, t, d2, 48);
			slam(level, t, d2);
			if (tickCount % 100 == 0 && enrageStacks < 20) {
				enrageStacks++;
				AttributeInstance sp = getAttribute(Attributes.MOVEMENT_SPEED);
				sp.removeModifier(ENRAGE);
				sp.addTransientModifier(new AttributeModifier(ENRAGE, 0.01 * enrageStacks, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		if (st.alive(Ruler.CASTELLAN)) portcullis(level, t, d2, 400);
		if (st.alive(Ruler.MASTER_AT_ARMS)) parryCast(level, t, d2, 140, 10);
	}

	void gatedReinforcements(ServerLevel level, LivingEntity t, CourtState st) {
		BlockPos site = st.site;
		Vec3 door = site != null ? Vec3.atBottomCenterOf(CastleLayout.hall(site).offset(0, 0, 4)) : position().add(4, 0, 4);
		if (st.alive(Ruler.MARSHAL))
			for (int i = 0; i < 2; i++) {
				KingdomSoldier s = KingdomApi.spawnSoldier(level, UnitTypes.MOUNTED_SWORDSMAN, factionId(), door.add(i * 2 - 1, 0, 0), null);
				if (s != null) s.setTarget(t);
			}
		if (st.alive(Ruler.HUNTSMAN))
			for (int i = 0; i < 2; i++) {
				Vec3 at = site != null ? Vec3.atBottomCenterOf(CastleLayout.balcony(site, i == 0)) : position().add(i * 6 - 3, 0, -3);
				KingdomSoldier s = KingdomApi.spawnSoldier(level, UnitTypes.CROSSBOWMAN, factionId(), at, null);
				if (s != null) s.setTarget(t);
			}
	}

	void mountWarhorse(ServerLevel level) {
		if (getVehicle() != null) return;
		var horse = EntityTypes.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) return;
		horse.snapTo(getX(), getY(), getZ(), getYRot(), 0F);
		horse.setTamed(true);
		horse.setPersistenceRequired();
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
		horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
		horse.setHealth(40F);
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
		horse.setCustomName(Component.translatable("entity.elsewhere.court_warhorse"));
		level.addFreshEntity(horse);
		startRiding(horse, true, false);
		warhorse = horse.getUUID();
		sound(level, SoundEvents.HORSE_ANGRY, 1.2F, 0.8F);
	}

	boolean unseated(ServerLevel level) {
		return getVehicle() instanceof LivingEntity h && h.getHealth() <= h.getMaxHealth() * 0.5F;
	}

	void slam(ServerLevel level, LivingEntity t, double d2) {
		if (slamTelegraph > 0) {
			if (--slamTelegraph == 0 && distanceToSqr(t) < 12) {
				float max = t.getMaxHealth();
				float dmg = 0.7F * max;
				if (t.getHealth() >= max - 0.5F) dmg = Math.min(dmg, t.getHealth() - 1.0F);
				t.hurtServer(level, damageSources().mobAttack(this), Math.max(1.0F, dmg));
				t.setDeltaMovement(t.getDeltaMovement().add(0, 0.6, 0));
				slams++;
				sound(level, SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
			}
			return;
		}
		if (cdD <= 0 && d2 < 6.25) {
			cdD = cd(200);   // the slam is an attack (cooldown -20 percent), not a defence
			slamTelegraph = 12;
			particles(level, ParticleTypes.ANGRY_VILLAGER, position().add(0, 2.2, 0), 3, 0.2);
		}
	}
}
