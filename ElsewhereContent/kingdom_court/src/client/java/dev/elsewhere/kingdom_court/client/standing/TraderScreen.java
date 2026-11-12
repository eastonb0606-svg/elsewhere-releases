package dev.elsewhere.kingdom_court.client.standing;

import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.standing.StandingNet;
import dev.elsewhere.kingdom_court.standing.StandingText;
import dev.elsewhere.kingdom_court.trade.TradeData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * POLISH P7: the trader dialogue. Opened by the server's {@link StandingNet.TraderOpen} when you right-click a trader, over the chat menu
 * the trader prints as before. It shows the trader's portrait (the live NPC, turning to follow the cursor), who they are and their kingdom's
 * motto, a greeting that depends on your Standing band, your Standing in that kingdom's own words with the bar, and why you pay what you pay
 * (Standing discount, Arm-Ring, haggle, war surcharge, the Thing's hours, Azmar's open tiers, a loan). Buttons lead to the existing
 * {@code /diplomacy} actions: Wares opens the chat on the clickable menu, Tribute starts the command, and the trader's own action (haggle,
 * claim rings, repair, collect a commission, accept an offer) runs it. Everything is built once in {@link #init()}; the screen never ticks.
 */
public class TraderScreen extends Screen {
	static final int MAX_W = 312, H = 196;
	static final int GOOD = 0xFF9FDC92, BAD = 0xFFF2907E;

	final StandingNet.TraderOpen data;
	final Diplomacy.Role role;
	final TradeData.Mode mode;
	final KingdomStyle style;
	final String band;
	int px, py, pw;
	private FormattedCharSequence titleLine, mottoLine;
	private final List<FormattedCharSequence> standingLines = new ArrayList<>();
	private final List<FormattedCharSequence> greeting = new ArrayList<>();
	private final List<FormattedCharSequence> priceText = new ArrayList<>();
	private final List<Integer> priceColor = new ArrayList<>();

	public TraderScreen(StandingNet.TraderOpen data) {
		super(title(data));
		this.data = data;
		this.role = role(data);
		this.mode = TradeData.Mode.values()[Math.clamp(data.mode(), 0, TradeData.Mode.values().length - 1)];
		this.style = KingdomStyle.of(data.faction(), role);
		this.band = StandingText.band(data.standing());
	}

	static Diplomacy.Role role(StandingNet.TraderOpen d) {
		return Diplomacy.Role.values()[Math.clamp(d.role(), 0, Diplomacy.Role.values().length - 1)];
	}

	static Component title(StandingNet.TraderOpen d) {
		return Component.translatable(StandingText.TITLE, Component.translatable("entity.elsewhere." + role(d).path), KingdomStyle.name(d.faction()));
	}

	/** The trader on this client, for the portrait (null if it is gone). */
	LivingEntity trader() {
		if (minecraft == null || minecraft.level == null) return null;
		Entity e = minecraft.level.getEntity(data.entity());
		return e instanceof LivingEntity l && !l.isRemoved() ? l : null;
	}

	@Override
	protected void init() {
		pw = Math.min(MAX_W, width - 16);
		px = (width - pw) / 2;
		py = Math.max(4, (height - H) / 2);
		int textW = pw - 104;
		titleLine = first(title, pw - 46);
		mottoLine = first(style.motto().withStyle(ChatFormatting.ITALIC), pw - 46);
		greeting.clear();
		List<FormattedCharSequence> g = font.split(Component.translatable(StandingText.greetKey(role, band)).withStyle(ChatFormatting.ITALIC), textW);
		for (int i = 0; i < Math.min(3, g.size()); i++) greeting.add(g.get(i));
		MutableComponent value = Component.translatable(StandingText.VALUE, style.bandTitle(band).withColor(style.accent & 0xFFFFFF),
			Component.translatable(StandingText.bandKey(band)).withColor(style.dim & 0xFFFFFF), signed(data.standing()));
		standingLines.clear();
		List<FormattedCharSequence> st = font.split(Component.translatable(StandingText.STANDING).withColor(style.dim & 0xFFFFFF).append(" ").append(value), textW);
		for (int i = 0; i < Math.min(2, st.size()); i++) standingLines.add(st.get(i)); // a long kingdom title ("Friend of the Sultana") wraps once
		priceText.clear();
		priceColor.clear();
		for (Line l : lines()) for (FormattedCharSequence s : font.split(l.text(), textW)) {
			priceText.add(s);
			priceColor.add(l.color());
		}
		buttons();
	}

	record Line(Component text, int color) {}

	Component price(String key, Object... args) {
		return Component.translatable(StandingText.priceKey(key), args);
	}

	/** Why you pay what you pay here, most important first. */
	List<Line> lines() {
		List<Line> out = new ArrayList<>();
		int t = style.text;
		if (data.has(StandingNet.HOSTILE)) {
			out.add(new Line(price("hostile"), BAD));
			if (data.has(StandingNet.OATH)) out.add(new Line(price("oath"), BAD));
			return out;
		}
		boolean sells = switch (role) {
			case ENVOY, AUCTIONEER -> false;
			default -> true;
		};
		int pct = data.pricePct();
		switch (mode) {
			case THING -> {
				if (role == Diplomacy.Role.LAWSPEAKER) out.add(data.has(StandingNet.THING_OPEN) ? new Line(price("thing_open"), GOOD) : new Line(price("thing_closed", data.detail()), t));
				standingDiscount(out);
				out.add(data.has(StandingNet.RING) ? new Line(price("ring"), GOOD) : new Line(price("ring_hint"), style.dim));
			}
			case POSTED -> {
				if (role == Diplomacy.Role.ENVOY) {
					out.add(new Line(price("envoy"), t));
					if (data.has(StandingNet.OFFER)) out.add(new Line(price("offer", Diplomacy.OFFER_DINARS), GOOD));
				} else {
					out.add(new Line(price("posted"), t));
					standingDiscount(out);
					out.add(new Line(price("tiers_" + Math.clamp(data.detail(), 0, 3)), data.detail() >= 3 ? GOOD : t));
				}
				if (data.has(StandingNet.PACT)) out.add(new Line(price("pact"), GOOD));
			}
			case FLOATING -> {
				if (role == Diplomacy.Role.AUCTIONEER) out.add(new Line(price("auction"), t));
				else {
					out.add(new Line(price("floating"), t));
					standingDiscount(out);
					if (data.has(StandingNet.WAR)) out.add(new Line(price("war"), BAD));
					if (data.has(StandingNet.HAGGLED)) out.add(new Line(price("haggled"), GOOD));
				}
				if (data.has(StandingNet.LOCKED)) out.add(new Line(price("locked"), BAD));
				else if (!data.has(StandingNet.HAGGLED)) out.add(new Line(price("chance", data.detail()), t));
				if (data.has(StandingNet.MISER)) out.add(new Line(price("miser"), style.dim));
				if (data.has(StandingNet.LATE)) out.add(new Line(price("late"), BAD));
				else if (data.has(StandingNet.OWES)) out.add(new Line(price("owes"), t));
			}
			default -> {
				out.add(new Line(price("legacy"), t));
				standingDiscount(out);
			}
		}
		if (sells) out.add(1, pct == 100 ? new Line(price("pays_list"), t) : new Line(price("pays", pct), pct < 100 ? GOOD : BAD));
		if (data.has(StandingNet.OATH)) out.add(new Line(price("oath"), BAD));
		return out;
	}

	void standingDiscount(List<Line> out) {
		int off = switch (band) {
			case "friendly" -> 5;
			case "allied" -> 15;
			default -> 0;
		};
		out.add(off > 0 ? new Line(price("discount", off), GOOD) : new Line(price("no_discount"), style.dim));
	}

	record Btn(Button.Builder builder, boolean active) {}

	void buttons() {
		List<Btn> b = new ArrayList<>();
		boolean hostile = data.has(StandingNet.HOSTILE);
		String browse = switch (role) {
			case AUCTIONEER -> "lots";
			case ENVOY -> "treasury";
			default -> "wares";
		};
		boolean browseOk = !hostile || "treasury".equals(browse);
		b.add(button(browse, () -> minecraft.gui.setScreen(new ChatScreen("", false)), browseOk ? browse : "hostile", browseOk));
		if (role == Diplomacy.Role.LAWSPEAKER || role == Diplomacy.Role.ENVOY || role == Diplomacy.Role.CLERK)
			b.add(button("tribute", () -> minecraft.gui.setScreen(new ChatScreen("/diplomacy tribute ", false)), "tribute", true));
		switch (role) {
			case LAWSPEAKER -> b.add(button("claim", () -> run("diplomacy claim"), hostile ? "hostile" : "claim", !hostile));
			case SMITH -> b.add(button("repair", () -> run("diplomacy repair"), hostile ? "hostile" : "repair", !hostile));
			case INSPECTOR -> b.add(button("pickup", () -> run("diplomacy pickup"), hostile ? "hostile" : "pickup", !hostile));
			case ENVOY -> {
				if (data.has(StandingNet.OFFER)) b.add(button("offer", () -> run("diplomacy offer"), "offer", true));
			}
			case CLERK -> {
				boolean can = !hostile && !data.has(StandingNet.LOCKED) && !data.has(StandingNet.HAGGLED);
				String tip = hostile ? "hostile" : data.has(StandingNet.LOCKED) ? "locked" : data.has(StandingNet.HAGGLED) ? "haggled" : "haggle";
				b.add(button("haggle", () -> run("diplomacy haggle"), tip, can));
			}
			default -> {}
		}
		b.add(new Btn(Button.builder(Component.translatable(StandingText.buttonKey("farewell")), x -> onClose()), true));
		int gap = 4, n = b.size(), bw = (pw - 20 - gap * (n - 1)) / n, y = py + H - 28;
		for (int i = 0; i < n; i++) {
			Button w = b.get(i).builder().bounds(px + 10 + i * (bw + gap), y, bw, 20).build();
			w.active = b.get(i).active();
			addRenderableWidget(w);
		}
	}

	Btn button(String key, Runnable action, String tip, boolean active) {
		Component tipText = Component.translatable(StandingText.tipKey(tip), "haggle".equals(tip) ? Integer.toString(data.detail()) : "");
		return new Btn(Button.builder(Component.translatable(StandingText.buttonKey(key)), x -> action.run()).tooltip(Tooltip.create(tipText)), active);
	}

	/** Runs a {@code /diplomacy} action as if typed (it acts on the nearest trader, this one) and closes the dialogue so its answer shows in chat. */
	void run(String command) {
		if (minecraft != null && minecraft.player != null) minecraft.player.connection.sendCommand(command);
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fillGradient(0, 0, width, height, 0x60000000, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, style.frame, px, py, pw, H);
		// header: crest, "Lawspeaker of the Jarldom of Hrafnholt", the kingdom's motto, a rule
		g.blitSprite(RenderPipelines.GUI_TEXTURED, style.crest, px + 11, py + 10, 16, 16);
		g.text(font, titleLine, px + 33, py + 10, style.title, false);
		g.text(font, mottoLine, px + 33, py + 21, style.dim, false);
		g.fill(px + 10, py + 33, px + pw - 10, py + 34, style.insetEdge);
		// portrait
		int bx = px + 10, by = py + 39, bw = 78, bh = 112;
		g.fill(bx, by, bx + bw, by + bh, style.inset);
		g.outline(bx, by, bw, bh, style.insetEdge);
		LivingEntity e = trader();
		if (e != null) InventoryScreen.extractEntityInInventoryFollowsMouse(g, bx + 1, by + 1, bx + bw - 1, by + bh - 1, 46, 0.0625F, mouseX, mouseY, e);
		else g.blitSprite(RenderPipelines.GUI_TEXTURED, style.crest, bx + bw / 2 - 16, by + bh / 2 - 16, 32, 32);
		// greeting, Standing and the price lines
		int x = px + 96, y = py + 40, w = pw - 104;
		for (FormattedCharSequence s : greeting) {
			g.text(font, s, x, y, style.text, false);
			y += 10;
		}
		y += 5;
		for (FormattedCharSequence s : standingLines) {
			g.text(font, s, x, y, style.text, false);
			y += 10;
		}
		y += 2;
		StandingBar.draw(g, x + 1, y, w - 2, 5, data.standing(), style, 1F);
		y += 11;
		int limit = py + H - 34;
		for (int i = 0; i < priceText.size() && y + 9 <= limit; i++) {
			g.text(font, priceText.get(i), x, y, priceColor.get(i), false);
			y += 10;
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	FormattedCharSequence first(Component c, int w) {
		List<FormattedCharSequence> l = font.split(c, w);
		return l.isEmpty() ? FormattedCharSequence.EMPTY : l.get(0);
	}

	static String signed(int v) {
		return v > 0 ? "+" + v : Integer.toString(v);
	}
}
