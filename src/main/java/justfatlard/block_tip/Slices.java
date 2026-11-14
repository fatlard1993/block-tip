package justfatlard.block_tip;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lines that stay up whatever the player is looking at: another mod's slice of the card, for as
 * long as it has something to say (see {@link BlockTipApi#slice}).
 *
 * <p>Their own strip, hung just under the card when one is up and in its place when not, as wide as
 * the card so the two read as one thing, and dressed the same: the same faint wash, white words.
 * Rebuilt only when it moves or gains or loses a line; a line that changes is updated in place.
 */
public final class Slices {
	private Slices() {}

	private static final String OVERLAY_ID = "block-tip:slices";
	/** Between the card's bottom edge and the strip. */
	private static final int GAP = 2;
	private static final String TEXT_COLOR = "#FFFFFFFF";

	private record Shown(List<String> lines, int top, int width) {}

	private static final Map<UUID, Shown> shown = new ConcurrentHashMap<>();

	/** Bring this player's strip up to date: their slices if they are allowed tips at all, nothing if not. */
	static void update(ServerPlayer player, boolean allowed) {
		List<String> lines = allowed ? BlockTipApi.slicesFor(player) : List.of();
		if (lines.isEmpty()) {
			clear(player);
			return;
		}
		UUID id = player.getUUID();
		int bottom = TipHud.bottomOf(id);
		Shown next = new Shown(List.copyOf(lines), bottom < 0 ? TipHud.BELOW_TOP : bottom + GAP, TipHud.widthOf(id));
		Shown now = shown.get(id);
		if (next.equals(now)) return;
		shown.put(id, next);

		if (now != null && now.top() == next.top() && now.width() == next.width() && now.lines().size() == next.lines().size()) {
			List<ComponentUpdate> changes = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				changes.add(new ComponentUpdate("line" + i, Map.of(ComponentType.PROP_TEXT, lines.get(i))));
			}
			PandoricalApi.hud().update(player, OVERLAY_ID, changes);
			return;
		}

		int height = TipHud.PADDING * 2 + lines.size() * TipHud.LINE;
		HudBuilder hud = new HudBuilder(OVERLAY_ID)
			.anchor("top_center")
			.offset(0, next.top())
			.component(new ComponentBuilder("panel", ComponentType.PANEL)
				.bounds(0, 0, next.width(), height)
				.prop(ComponentType.PROP_BACKGROUND, TipHud.PANEL_BACKGROUND)
				.prop(ComponentType.PROP_BORDER, TipHud.PANEL_BORDER_NONE)
				.build());
		int textWidth = next.width() - TipHud.PADDING * 2;
		for (int i = 0; i < lines.size(); i++) {
			hud.component(new ComponentBuilder("line" + i, ComponentType.TEXT)
				.bounds(TipHud.PADDING, TipHud.PADDING + i * TipHud.LINE + (TipHud.LINE - 9) / 2, textWidth, 9)
				.prop(ComponentType.PROP_TEXT, lines.get(i))
				.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(textWidth))
				.prop(ComponentType.PROP_MAX_LINES, "1")
				.prop(ComponentType.PROP_COLOR, TEXT_COLOR)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build());
		}
		PandoricalApi.hud().show(player, hud.build());
	}

	static void clear(ServerPlayer player) {
		if (shown.remove(player.getUUID()) != null) PandoricalApi.hud().hide(player, OVERLAY_ID);
	}

	static void forget(UUID player) {
		shown.remove(player);
	}

	/** What is up for this player now, for a test to read.
	 *
	 * @hidden
	 */
	public static List<String> showing(UUID player) {
		Shown now = shown.get(player);
		return now == null ? List.of() : now.lines();
	}
}
