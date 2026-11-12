package dev.elsewhere.client.v2;

import dev.elsewhere.core.atlas.AtlasPayload;
import dev.elsewhere.core.trophy.Trophies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The Wayfinder Atlas (polish-atlas-a): a full-screen book, two pages per spread, one page per place. On purpose it explains
 * little: an unvisited dimension is "???" with a single riddle; a visited one gets its name and two vague hints; guardians
 * stay "???" until you helped defeat them. Arrow keys or the corner arrows turn pages; Esc closes.
 */
public class AtlasScreen extends Screen {
	/** Page keys in book order. Elsewhere dimensions first, then the vanilla ones (always known). */
	public static final List<String> PAGES = List.of("pale_hollow", "petalwind", "glasswaste", "verdigris_sea", "ironpeak", "greenhaze", "the_hush",
		"overworld", "the_nether", "the_end");
	private static final Set<String> VANILLA = Set.of("overworld", "the_nether", "the_end");
	/** Discoveries ("kind:id") that belong to a page; anything else is noted on the Overworld page. */
	private static final Map<String, String> FOUND_PAGE = Map.ofEntries(
		Map.entry("companion:hollow_stag", "pale_hollow"), Map.entry("companion:docile_scarab", "glasswaste"),
		Map.entry("companion:haze_ferret", "greenhaze"), Map.entry("event:lantern", "pale_hollow"),
		Map.entry("item:elsewhere:amber", "pale_hollow"), Map.entry("item:elsewhere:paleweave_mantle", "pale_hollow"),
		Map.entry("item:elsewhere:ghostfin", "pale_hollow"), Map.entry("item:elsewhere:petalscale_koi", "petalwind"),
		Map.entry("item:elsewhere:glassfin_darter", "glasswaste"), Map.entry("item:elsewhere:verdigris_eel", "verdigris_sea"),
		Map.entry("item:elsewhere:frostjaw_trout", "ironpeak"), Map.entry("item:elsewhere:haze_guppy", "greenhaze"),
		Map.entry("item:elsewhere:hush_lanternfish", "the_hush"));

	private static final int INK = 0xFFE9DFC6;
	private static final int INK_DIM = 0xFF9C8F78;
	private static final int INK_GOLD = 0xFFE7B95A;
	private static final int PAGE = 0xFF3A2F22;
	private static final int COVER = 0xFF1E1610;

	private final AtlasPayload data;
	private int spread;
	private int bx, by, bw, bh;

	public AtlasScreen(final AtlasPayload data) {
		super(Component.translatable("gui.elsewhere.atlas.title"));
		this.data = data;
	}

	public static int spreads() {
		return (PAGES.size() + 1) / 2;
	}

	public boolean known(final String page) {
		return VANILLA.contains(page) || this.data.dims().contains("elsewhere:" + page);
	}

	@Override
	protected void init() {
		this.bw = Math.min(this.width - 24, 400);
		this.bh = Math.min(this.height - 24, 240);
		this.bx = (this.width - this.bw) / 2;
		this.by = (this.height - this.bh) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.turn(-1)).bounds(this.bx + 6, this.by + this.bh - 22, 18, 16).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.turn(1)).bounds(this.bx + this.bw - 24, this.by + this.bh - 22, 18, 16).build());
	}

	private void turn(final int d) {
		this.spread = Math.floorMod(this.spread + d, spreads());
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.key() == 263) { // left
			this.turn(-1);
			return true;
		}
		if (event.key() == 262) { // right
			this.turn(1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float partialTick) {
		g.fill(0, 0, this.width, this.height, 0xA0000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float partialTick) {
		// cover, two pages and the spine
		g.fill(this.bx - 4, this.by - 4, this.bx + this.bw + 4, this.by + this.bh + 4, COVER);
		g.fill(this.bx, this.by, this.bx + this.bw, this.by + this.bh, PAGE);
		int mid = this.bx + this.bw / 2;
		g.fill(mid - 1, this.by + 4, mid + 1, this.by + this.bh - 4, 0xFF140F0A);
		g.outline(this.bx + 3, this.by + 3, this.bw - 6, this.bh - 6, 0xFF5A4A35);
		int pageW = this.bw / 2 - 20;
		int left = this.spread * 2;
		this.page(g, PAGES.get(left), this.bx + 12, this.by + 10, pageW);
		if (left + 1 < PAGES.size()) {
			this.page(g, PAGES.get(left + 1), mid + 9, this.by + 10, pageW);
		}
		// footer
		int trophies = 0;
		for (String b : this.data.bosses()) {
			if (Trophies.BOSSES.containsKey(b)) {
				trophies++;
			}
		}
		Component foot = Component.translatable("gui.elsewhere.atlas.trophies", trophies, Trophies.BOSSES.size());
		g.text(this.font, foot, mid - this.font.width(foot) / 2, this.by + this.bh - 18, INK_GOLD, false);
		String num = (this.spread + 1) + " / " + spreads();
		g.text(this.font, num, mid - this.font.width(num) / 2, this.by + this.bh - 8 - this.font.lineHeight / 2, INK_DIM, false);
		// the page-turn widgets go on top of the book
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private int lines(final GuiGraphicsExtractor g, final Component text, final int x, final int y, final int w, final int color) {
		int yy = y;
		for (FormattedCharSequence line : this.font.split(text, w)) {
			g.text(this.font, line, x, yy, color, false);
			yy += this.font.lineHeight + 1;
		}
		return yy;
	}

	private void page(final GuiGraphicsExtractor g, final String page, final int x, final int y, final int w) {
		boolean known = this.known(page);
		Component title = !known ? Component.literal("???") : VANILLA.contains(page)
			? Component.translatable("atlas.elsewhere." + page + ".title") : Component.translatable("dimension.elsewhere." + page);
		g.text(this.font, title, x, y, known ? INK_GOLD : INK_DIM, false);
		g.fill(x, y + this.font.lineHeight + 2, x + w, y + this.font.lineHeight + 3, 0xFF5A4A35);
		int yy = y + this.font.lineHeight + 7;
		if (!known) {
			yy = this.lines(g, Component.translatable("atlas.elsewhere." + page + ".riddle").withStyle(s -> s.withItalic(true)), x, yy, w, INK_DIM);
		} else {
			yy = this.lines(g, Component.translatable("atlas.elsewhere." + page + ".hint1"), x, yy, w, INK);
			yy = this.lines(g, Component.translatable("atlas.elsewhere." + page + ".hint2"), x, yy + 2, w, INK) + 4;
		}
		// guardians
		List<String> bosses = new ArrayList<>();
		for (Map.Entry<String, String> e : Trophies.BOSSES.entrySet()) {
			if (e.getValue().equals(page)) {
				bosses.add(e.getKey());
			}
		}
		if (!bosses.isEmpty()) {
			g.text(this.font, Component.translatable("gui.elsewhere.atlas.guardians"), x, yy, INK_DIM, false);
			yy += this.font.lineHeight + 2;
			for (String b : bosses) {
				boolean beaten = this.data.bosses().contains(b);
				Component name = beaten ? Component.literal("✔ ").append(Component.translatable("entity.elsewhere." + b)) : Component.literal("  ???");
				g.text(this.font, name, x + 4, yy, beaten ? INK : INK_DIM, false);
				yy += this.font.lineHeight + 1;
			}
			yy += 3;
		}
		// notes: discoveries that belong here
		List<Component> notes = new ArrayList<>();
		for (String f : this.data.found()) {
			String home = FOUND_PAGE.getOrDefault(f, "overworld");
			if (home.equals(page)) {
				notes.add(note(f));
			}
		}
		if (!notes.isEmpty() && yy < this.by + this.bh - 40) {
			g.text(this.font, Component.translatable("gui.elsewhere.atlas.notes"), x, yy, INK_DIM, false);
			yy += this.font.lineHeight + 2;
			for (Component n : notes) {
				if (yy > this.by + this.bh - 32) {
					break;
				}
				yy = this.lines(g, Component.literal("• ").append(n), x + 4, yy, w - 4, INK);
			}
		}
	}

	static Component note(final String found) {
		int i = found.indexOf(':');
		String kind = i < 0 ? found : found.substring(0, i);
		String id = i < 0 ? "" : found.substring(i + 1);
		if (kind.equals("item")) {
			Identifier item = Identifier.tryParse(id);
			if (item != null && BuiltInRegistries.ITEM.containsKey(item)) {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(item)).getHoverName();
			}
		}
		if (kind.equals("companion")) {
			return Component.translatable("gui.elsewhere.atlas.companion", Component.translatable("entity.elsewhere." + id));
		}
		return Component.translatableWithFallback("atlas.elsewhere.found." + found.replace(':', '.'), id);
	}
}
