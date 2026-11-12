package dev.elsewhere.lore;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

/** The Royal Proclamation: a written book in the King's own voice (Osric Vael, the Hundredth Crown). */
public final class Proclamation {
	private Proclamation() {}

	public static final String TITLE = "A Royal Proclamation";
	public static final String AUTHOR = "Osric Vael, King of Aldermark";

	/** One entry per page; each stays under 250 characters so it fits a book page. */
	public static final List<String> PAGES = List.of(
		"BY ORDER OF THE CROWN\n\nA Proclamation of Osric Vael, the Hundredth Crown, King of Aldermark, Warden of the Marches, Keeper of the Road.\n\nTo be read at every gate, well and market cross.",
		"Hear this.\n\nAldermark was a tower in a field before it was a kingdom. I am the hundredth to wear its crown, and I do not mean to be the last.\n\nWe keep what we hold. We hold more each year.",
		"Of Travellers.\n\nThe road is the King's. Walk it, trade upon it, sleep beside it, and no banner of mine will trouble you.\n\nLift a hand against my soldiers and I shall lift a hundred in answer. Patience is a courtesy I extend once.",
		"Of Villages.\n\nWhere the bell has gone quiet, my soldiers have come to keep the peace. Peace is heavy and takes many hands to carry. My serfs carry it gladly, or at least they carry it.\n\nA village may be freed. I shall remember who did the freeing.",
		"Of Outposts.\n\nWhere there is an empty hill, there shall be a tower. Where there is a tower, there shall be a road. Where there is a road, there shall be a tax.\n\nI count my towers each spring. The number has not yet gone down.",
		"Of the Lords Five.\n\nYsolde Varr, Marshal, commands my field.\nBertrand Holm, Castellan, keeps my keep.\nCorvin Thale, Master-at-Arms, taught your sword its manners.",
		"Lisbet Ashgrove, Huntsman, is never seen first.\nMarek Dunmere, Lord Chancellor, has read everything you ever signed.\n\nEach rides under a device of his own and answers to no one but me.",
		"Of the Seals.\n\nEach Lord bears a seal of wax and Glimmer. Without it no ledger is honest. A seal taken from a Lord's hand, the Quartermaster will honour; that is to say, he will price it.",
		"Of the Throne.\n\nA Lord alive stands beside me. A Lord dead is a stone out of my wall. Strike them down and I shall grow thin.\n\nShould I fall, the highest of the Lords takes the crown, and he shall be hungrier than I.",
		"Of the End.\n\nShould all six of us fall, Aldermark is a field and a tower and some tired men who will forget the words. Few remember a kingdom that has no one left to say its name.",
		"Of the Survey.\n\nThree torn pieces of my surveyors' chart, joined, will show the way to my seat. I do not say this out of kindness. A king who cannot be found has no one to rule.",
		"To whoever reads this by the hand of a courier:\n\nHe carried it loyally. Walk well, stranger.\n\nSigned in my own hand,\nOSRIC VAEL\nSealed with the Hundredth Crown.\n\n(The wax is still soft.)");

	public static WrittenBookContent content() {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int i = 0; i < PAGES.size(); i++) {
			Component c = Component.literal(PAGES.get(i));
			if (i == 0) c = Component.literal(PAGES.get(i)).withStyle(ChatFormatting.DARK_BLUE);
			pages.add(Filterable.passThrough(c));
		}
		return new WrittenBookContent(Filterable.passThrough(TITLE), AUTHOR, 0, pages, true);
	}
}
