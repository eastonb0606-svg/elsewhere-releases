package dev.elsewhere.lore;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

/** The three rival proclamations, each in its own voice. Every page stays under 250 characters. */
public final class RivalProclamations {
	private RivalProclamations() {}

	public static final String HRAFNHOLT_TITLE = "The Hearth-Oath", HRAFNHOLT_AUTHOR = "Halvard Ironbrow, High Jarl of Hrafnholt";
	public static final List<String> HRAFNHOLT = List.of(
		"HEAR THE HEARTH-OATH\n\nI am Halvard Ironbrow, High Jarl of Hrafnholt. Nine clan-halls swore to one fire in the fjord forests, where the pines meet the sea. The fire is still lit. I keep it.",
		"Of the Hearth-Fire.\n\nIt burns in the longhall and it has not gone out since the oath. The clans say the realm ends the night it dies. So no man of mine lets it die, and no stranger gets close.",
		"Of Ravens.\n\nThe ravens are the clans' eyes in the pines and over the fjord. Where they sit on a post in the pines, the Jarldom begins. Pass the post with your hands open and you are a guest. Pass it with an axe out and you are a story.",
		"Of Axes.\n\nStrike first or be struck. That is not a boast, it is forest arithmetic: the pines have taught us to cut first. My huskarls do not wait to be insulted.\n\nBuy peace with silver, or earn it with blood. Both are accepted.",
		"Of Thralls.\n\nThe thralls carry the stone and raise the walls. They are many, and they remember the hand that feeds them. Strike one and the village will answer you with its hundred.",
		"Of Aldermark.\n\nThe southern king plants a tower-flag on every hill the way a hound marks fenceposts. A flag is cloth. We have burned bigger. We shall see whose tower stands in spring.",
		"Of the Old Brothers.\n\nThe Ironpeak giants are no kin of ours, though the skalds call them the Old Brothers. They keep their mountain; we keep our hall. We drink to them from a distance.",
		"Of the Six.\n\nThe Jarl, the Hersir, the Hallwarden, the Weapon-Skald, the Huntmaster and the Chief in the wolf-coat. Each swore. Each holds a share of the Hird-oath, and each hits like a falling roof.",
		"Of Holmgang.\n\nA friend of the Hearth may challenge one of my captains to the holmgang. He yields at one breath left. Nobody dies, but everyone remembers.",
		"So ends the oath.\n\nSworn at the Hearth,\nHALVARD IRONBROW\nHigh Jarl\n\n(The seal is a raven's foot in cold wax.)");

	public static final String AZMAR_TITLE = "The Well-Ledger", AZMAR_AUTHOR = "Nahira Zahr-Qadir, Sultana of Azmar, the Unsetting Sun";
	public static final List<String> AZMAR = List.of(
		"IN THE NAME OF THE SUN THAT SETS NOT\n\nI am Nahira Zahr-Qadir, Sultana of Azmar. This ledger is written for travellers, who will read it slowly, and for rivals, who will read it too late.",
		"Of Wells.\n\nAzmar counts its years in wells. A well dug is a year kept. Where we raise a well-stone, there is water, and where there is water there is a road, and the road is ours.",
		"Of Patience.\n\nThe scarab rolls the sun a little each day, and the sun has never once been late. I do not hurry. Those who hurry feed those who do not.",
		"Of Ibis and Ink.\n\nMy Grand Wazir, Idris Halvane, writes what is true. What he writes, I do. What he has not yet written, I wait for. Counsel is the cheapest army.",
		"Of Falcons.\n\nLeyla Dunmar's falcon sees your camp before you have lit its fire. We do not ambush. We simply arrive having already read the ending.",
		"Of Aldermark.\n\nThe northern king's tower-flag crossed the dunes and claimed three of my wells. It is a handsome flag. I have asked the wells to remember it kindly.",
		"Of Villages.\n\nA village we take, we keep. Our walls grow a little each season, and our obelisks mark the borders. A border does not shout. It is simply there when you reach it.",
		"Of Tribute.\n\nA strong traveller may keep to the desert and we shall keep to our business. The Royal Envoy will name a fair sum in Dinars. Accept it, and we are both richer for the quiet.",
		"Of the Four.\n\nThe Sultana. The Wazir. The Falconer. The Amir of Spears. Few in number, by design. Four who see are worth four hundred who do not.",
		"So it is written.\n\nNAHIRA ZAHR-QADIR\nthe Unsetting Sun\n\n(The seal is a scarab holding a sun, pressed in gold wax that does not cool.)");

	public static final String MIREWICK_TITLE = "Notice of the Lantern Council", MIREWICK_AUTHOR = "The Lantern Council of Mirewick";
	public static final List<String> MIREWICK = List.of(
		"NOTICE FROM THE COUNTING-HOUSE\n\nThe Lantern Council of the Republic of Mirewick, being five Magnates in good standing, hereby informs all travellers of the terms of passage. Please read in full.",
		"Article One: the First Lantern.\n\nThe First Lantern is never to be extinguished. The Almoners carry oil to it each dusk. Whoever holds the Chair carries it. This is not negotiable. Most other things are.",
		"Article Two: the Dikes.\n\nWe drained the old mire with our own hands and rent, and laid polder farmland where it lay. The canals and dikes are garrisoned. The water is patient and so are we, but our patience is billed hourly.",
		"Article Three: Scrip.\n\nLantern Scrip is the only note recognised at the Counting-House. Any coin becomes Scrip at four for three. Scrip becomes other coin at par, to our friends. Friends are those who pay.",
		"Article Four: Contracts.\n\nThe Republic hires companies for the day and thanks them in writing. A friend of the Council may take a contract. A friend of the Council may also outbid one. We do keep receipts.",
		"Article Five: the Magnates.\n\nMarrow, Thwaite, Sorrel, Tolliver and Ames. Five votes, five opinions, one ledger. The Chair is held by the largest vault. Votes are weekly. Bribery is described elsewhere, in smaller print.",
		"Article Six: Aldermark.\n\nThe southern crown once taxed our polder peat. Its tower-flag still stands on three of our hills. It is noted in the ledger under 'sunk costs'. We expect repayment, eventually, in some form.",
		"Article Seven: Peat-Cutters.\n\nOur diggers are paid by the Council and housed at its expense. Should the Counting-House be raided, they will stop. We are not responsible for what comes after.",
		"Article Eight: Lanterns.\n\nOur patrols carry lanterns along the canals at night. If you see one, you have been seen, and it was courteous of us to let you know. Please keep to the quay.",
		"Signed and sealed.\n\nTHE LANTERN COUNCIL\nby order of the Chair\n\n(The seal is a lantern over a green chevron. The wax is still warm, and invoiced.)");

	public static List<String> pages(String key) {
		return switch (key) {
			case "hrafnholt" -> HRAFNHOLT;
			case "azmar" -> AZMAR;
			case "mirewick" -> MIREWICK;
			default -> Proclamation.PAGES;
		};
	}

	public static String author(String key) {
		return switch (key) {
			case "hrafnholt" -> HRAFNHOLT_AUTHOR;
			case "azmar" -> AZMAR_AUTHOR;
			case "mirewick" -> MIREWICK_AUTHOR;
			default -> Proclamation.AUTHOR;
		};
	}

	public static String title(String key) {
		return switch (key) {
			case "hrafnholt" -> HRAFNHOLT_TITLE;
			case "azmar" -> AZMAR_TITLE;
			case "mirewick" -> MIREWICK_TITLE;
			default -> Proclamation.TITLE;
		};
	}

	public static ChatFormatting colour(String key) {
		return switch (key) {
			case "hrafnholt" -> ChatFormatting.DARK_GRAY;
			case "azmar" -> ChatFormatting.GOLD;
			case "mirewick" -> ChatFormatting.DARK_GREEN;
			default -> ChatFormatting.DARK_BLUE;
		};
	}

	public static WrittenBookContent content(String key) {
		if (key.equals("aldermark")) return Proclamation.content();
		List<Filterable<Component>> pages = new ArrayList<>();
		List<String> src = pages(key);
		for (int i = 0; i < src.size(); i++) {
			Component c = Component.literal(src.get(i));
			if (i == 0) c = c.copy().withStyle(colour(key));
			pages.add(Filterable.passThrough(c));
		}
		return new WrittenBookContent(Filterable.passThrough(title(key)), author(key), 0, pages, true);
	}

	public static WrittenBookContent content(Identifier faction) {
		return content(LoreFactions.of(faction).key);
	}
}
