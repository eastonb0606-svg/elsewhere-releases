package dev.elsewhere.duelist.foresight;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * A boss bar whose move-name label is a foresight hint: players who see ({@link Foresight#sees}) get "Name · Move", the
 * rest get "Name". {@link #getName()} is the full title (what a player with foresight gear reads). Name packets go out
 * only when what a player sees changes: a new label, a new base name, or that player's sight changing.
 */
public class ForesightBossEvent extends ServerBossEvent {
	/** Bars that have at least one player (a player's sight change re-sends only these). */
	private static final Set<ForesightBossEvent> LIVE = new LinkedHashSet<>();
	/** Test hook: every per-player name packet (player, name sent). */
	public static @Nullable BiConsumer<ServerPlayer, Component> sentHook;

	private Component base;
	private String label = "";

	public ForesightBossEvent(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
		super(id, name, color, overlay);
		this.base = name;
	}

	/** The title for a label: "Name · Move" ({@link Foresight#MARK} on the move part), or the plain name. */
	public static Component title(Component base, String label) {
		if (label.isEmpty()) return base;
		return Component.empty().append(base).append(Component.literal(" · " + label).withStyle(s -> s.withInsertion(Foresight.MARK)));
	}

	/** Sets the base name and the move label ("" = none); sends each watching player the title they may see. */
	public void setLabelled(Component newBase, String newLabel) {
		String l = newLabel == null ? "" : newLabel;
		boolean baseChanged = !Objects.equals(newBase, base);
		boolean labelChanged = !l.equals(label);
		if (!baseChanged && !labelChanged) return;
		base = newBase;
		label = l;
		this.name = title(base, label);
		if (!isVisible()) return;
		for (ServerPlayer p : new ArrayList<>(getPlayers())) {
			if (baseChanged || Foresight.sees(p)) send(p, nameFor(p));
		}
	}

	/** A plain name with no label (vanilla API: callers that only set a name). */
	@Override
	public void setName(Component c) {
		setLabelled(c, "");
	}

	/** What this player is shown: the full title with sight, otherwise the base name. */
	public Component nameFor(ServerPlayer p) {
		return label.isEmpty() || Foresight.sees(p) ? title(base, label) : base;
	}

	public String label() {
		return label;
	}

	public Component baseName() {
		return base;
	}

	@Override
	public void addPlayer(ServerPlayer p) {
		boolean had = getPlayers().contains(p);
		// the add packet carries the title: build it with what this player may see
		Component full = this.name;
		this.name = nameFor(p);
		try {
			super.addPlayer(p);
		} finally {
			this.name = full;
		}
		if (!had && getPlayers().contains(p)) {
			LIVE.add(this);
			if (isVisible() && sentHook != null) sentHook.accept(p, nameFor(p));
		}
	}

	@Override
	public void removePlayer(ServerPlayer p) {
		super.removePlayer(p);
		if (getPlayers().isEmpty()) LIVE.remove(this);
	}

	@Override
	public void removeAllPlayers() {
		super.removeAllPlayers();
		LIVE.remove(this);
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible == isVisible()) return;
		if (!visible || label.isEmpty()) {
			super.setVisible(visible);
			return;
		}
		// shown again mid-label: everyone gets the plain bar, then players who see get the label
		Component full = this.name;
		this.name = base;
		try {
			super.setVisible(true);
		} finally {
			this.name = full;
		}
		for (ServerPlayer p : new ArrayList<>(getPlayers())) if (Foresight.sees(p)) send(p, full);
	}

	/** This player's sight changed: re-send their title if a label is up. */
	void refresh(ServerPlayer p) {
		if (!isVisible() || label.isEmpty() || !getPlayers().contains(p)) return;
		send(p, nameFor(p));
	}

	private void send(ServerPlayer p, Component shown) {
		Component full = this.name;
		this.name = shown;
		try {
			p.connection.send(ClientboundBossEventPacket.createUpdateNamePacket(this));
		} finally {
			this.name = full;
		}
		if (sentHook != null) sentHook.accept(p, shown);
	}

	static void sightChanged(ServerPlayer p) {
		if (LIVE.isEmpty()) return;
		for (ForesightBossEvent e : new ArrayList<>(LIVE)) e.refresh(p);
	}

	/** Test hook: the live bars this player watches. */
	public static List<ForesightBossEvent> barsOf(ServerPlayer p) {
		List<ForesightBossEvent> out = new ArrayList<>();
		for (ForesightBossEvent e : LIVE) if (e.getPlayers().contains(p)) out.add(e);
		return out;
	}

	static void clearLive() {
		LIVE.clear();
	}
}
