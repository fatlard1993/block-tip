package justfatlard.block_tip;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * The little card that names what you are looking at.
 *
 * <p>A picture and a word, nothing else. No tool tier, no harvest level, no
 * progress bar: the question is "what is that", and every extra line is one more
 * thing between it and the answer. The picture sits above the word, centred,
 * because it is the half that answers fastest, and the half that still answers
 * when the word is one you have not read before.
 *
 * <p>It sits just above the hotbar because that is where vanilla already writes
 * the name of whatever you are holding. Nobody has to be taught where to look.
 *
 * <p>Sent only when the answer changes. A player standing still and staring at
 * one block costs nothing, and a player sweeping their view across a wall sends
 * about one small packet per block crossed.
 */
public final class TipHud {
	private TipHud() {}

	private static final String OVERLAY_ID = "block-tip:tip";
	private static final String ICON_ID = "icon";
	private static final String PANEL_ID = "panel";
	private static final String LABEL_ID = "label";
	private static final String DETAIL_ID = "detail";

	private static final int ICON_SIZE = 16;

	/**
	 * Small. It sits over the middle of the screen for as long as you are looking
	 * at anything, so it has to be the size of a label rather than a dialog.
	 */
	private static final int WIDTH = 108;
	private static final int PADDING = 3;

	/** Icon on the left, name beside it, the way a tooltip reads. */
	private static final int NAME_X = PADDING + ICON_SIZE + 3;
	private static final int NAME_WIDTH = WIDTH - NAME_X - PADDING;

	/** The detail sits under both, using the full width: it is the longer half. */
	private static final int DETAIL_WIDTH = WIDTH - PADDING * 2;

	private static final int LINE = 9;
	private static final int HEIGHT_ONE_LINE = PADDING * 2 + ICON_SIZE;

	/** Down from the top edge, clear of the effect icons that live up there. */
	private static final int BELOW_TOP = 8;

	/** Vanilla's own tooltip palette, so the card looks like it came with the game. */
	private static final String PANEL_BACKGROUND = "#F0100010";
	private static final String PANEL_BORDER_LIGHT = "#505000FF";
	private static final String PANEL_BORDER_DARK = "#5028007F";

	/** Dimmer than the name, because it is the footnote and not the answer. */
	private static final String DETAIL_COLOR = "#FFA0A0A0";

	/** What each player is currently being told, so it is only said once. */
	private static final Map<UUID, Sighted> showing = new ConcurrentHashMap<>();

	public static void update(ServerPlayer player, Sighted sighted) {
		UUID id = player.getUUID();
		Sighted current = showing.get(id);

		if (sighted.isNothing()) {
			if (current != null) {
				showing.remove(id);
				PandoricalApi.hud().hide(player, OVERLAY_ID);
			}
			return;
		}

		if (sighted.equals(current)) return;

		// The panel is sized when it is built, so a card that gains or loses its
		// second line has to be rebuilt rather than updated. Only the shape matters:
		// the words changing is what update() is for.
		if (current == null || current.detail().isBlank() != sighted.detail().isBlank()) {
			show(player, sighted);
		} else {
			PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
				new ComponentUpdate(ICON_ID, Map.of(ComponentType.PROP_ITEM_ID, sighted.itemId())),
				new ComponentUpdate(LABEL_ID, Map.of(ComponentType.PROP_TEXT_KEY, sighted.nameKey())),
				new ComponentUpdate(DETAIL_ID, Map.of(ComponentType.PROP_TEXT_KEY, sighted.detail()))
			));
		}

		showing.put(id, sighted);
	}

	private static void show(ServerPlayer player, Sighted sighted) {
		boolean hasDetail = !sighted.detail().isBlank();
		int height = hasDetail ? HEIGHT_ONE_LINE + LINE : HEIGHT_ONE_LINE;

		// Vertically centred against the icon when it is the only line, and sat on
		// the icon's top edge when a second line has to fit beneath it.
		int labelY = PADDING + (ICON_SIZE - 9) / 2;

		HudBuilder hud = new HudBuilder(OVERLAY_ID)
			.anchor("top_center")
			.offset(0, BELOW_TOP)
			.component(new ComponentBuilder(PANEL_ID, ComponentType.PANEL)
				.bounds(0, 0, WIDTH, height)
				.prop(ComponentType.PROP_BACKGROUND, PANEL_BACKGROUND)
				.prop(ComponentType.PROP_BORDER_LIGHT, PANEL_BORDER_LIGHT)
				.prop(ComponentType.PROP_BORDER_DARK, PANEL_BORDER_DARK)
				.build())
			.component(new ComponentBuilder(ICON_ID, ComponentType.ITEM_ICON)
				.bounds(PADDING, PADDING, ICON_SIZE, ICON_SIZE)
				.prop(ComponentType.PROP_ITEM_ID, sighted.itemId())
				.build())
			.component(new ComponentBuilder(LABEL_ID, ComponentType.TEXT)
				.bounds(NAME_X, labelY, NAME_WIDTH, 9)
				.prop(ComponentType.PROP_TEXT_KEY, sighted.nameKey())
				// Wrapped to one line so a long name is cut short rather than running
				// out past the panel, which is what made the card look off-centre.
				.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(NAME_WIDTH))
				.prop(ComponentType.PROP_MAX_LINES, "1")
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			// Always built, usually empty: a component that exists from the start can
			// be updated in place, and one that appeared later would need the whole
			// card rebuilt every time a player looked at something unusual.
			.component(new ComponentBuilder(DETAIL_ID, ComponentType.TEXT)
				.bounds(PADDING, PADDING + ICON_SIZE, DETAIL_WIDTH, 9)
				.prop(ComponentType.PROP_TEXT_KEY, sighted.detail())
				.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(DETAIL_WIDTH))
				.prop(ComponentType.PROP_MAX_LINES, "1")
				.prop(ComponentType.PROP_COLOR, DETAIL_COLOR)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build());

		PandoricalApi.hud().show(player, hud.build());
	}

	/** Drop a player's card and the memory of it. */
	public static void clear(ServerPlayer player) {
		if (showing.remove(player.getUUID()) != null) {
			PandoricalApi.hud().hide(player, OVERLAY_ID);
		}
	}

	/** Forget a player entirely, on disconnect. */
	public static void forget(UUID playerId) {
		showing.remove(playerId);
	}
}
