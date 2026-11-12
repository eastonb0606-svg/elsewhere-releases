package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.gear3.s3.whips.WhipTags;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Marshal's Panoply set "Command" (Aldermark Commander; GEAR.md A5): Marshal's Helm + Marshal's Tabard + Marshal's
 * Tassets + the Warmarshal's Spurs (the Lord Marshal's court drop). Your whip tags last 3 s longer, and your standard's
 * aura reaches 8 blocks further.
 *
 * <p>Whip tags: registered with s-whips' {@code WhipTags.addBonusTicks}, asked once per tag; it lengthens every whip's tag
 * and its idle grace, the summoner's Gale Ribbon included (WhipMixins stamps the Ribbon's tag later). Standards: the
 * Herdcaller's Pennant adds {@link #standardRadiusBonus} at each pulse (s-pennant, {@code Pennant.commandBonus}). Both read
 * the cached bonus mask; nothing ticks. Kill switch {@code setbonus_command}.
 */
public final class MarshalCommand {
	private MarshalCommand() {}

	public static final int WHIP_TAG_BONUS_TICKS = 60;
	public static final double STANDARD_RADIUS_BONUS = 8.0;
	public static SetBonus SET;

	static void init() {
		SET = SetBonuses.register(SetBonus.of("command")
			.piece(EquipmentSlot.HEAD, "court_marshals_helm")
			.piece(EquipmentSlot.CHEST, "court_marshals_tabard")
			.piece(EquipmentSlot.LEGS, "court_marshals_tassets")
			.piece(EquipmentSlot.FEET, "court_warmarshal_spurs")
			.details(2));
		WhipTags.addBonusTicks(MarshalCommand::whipTagBonusTicks);
	}

	/** Extra whip-tag ticks for {@code owner}: 60 (3 s) x power, 0 without the set. */
	public static int whipTagBonusTicks(ServerPlayer owner) {
		if (owner == null) return 0;
		float pw = SetBonuses.power(owner, SET);
		return pw > 0F ? Math.round(WHIP_TAG_BONUS_TICKS * pw) : 0;
	}

	/** Extra standard aura radius for the standard's owner: 8 blocks x power, 0 without the set. */
	public static double standardRadiusBonus(Player owner) {
		if (owner == null) return 0.0;
		float pw = SetBonuses.power(owner, SET);
		return pw > 0F ? STANDARD_RADIUS_BONUS * pw : 0.0;
	}
}
