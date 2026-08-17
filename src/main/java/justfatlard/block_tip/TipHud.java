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
 * progress bar: the reader this is for is six and sounding the word out, and
 * every extra line is one more thing between them and the answer. The picture
 * sits above the word, centred, so it is the thing you read when the word is
 * still hard.
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
	private static final String LABEL_ID = "label";

	private static final int ICON_SIZE = 16;
	private static final int GAP = 2;

	/** Wide enough for a long block name; the card has no background, so slack costs nothing visually. */
	private static final int WIDTH = 200;

	/** Clear of the hotbar and of the held item's own name, which lives just above it. */
	private static final int ABOVE_HOTBAR = 60;

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

		if (current == null) {
			show(player, sighted);
		} else {
			PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
				new ComponentUpdate(ICON_ID, Map.of(ComponentType.PROP_ITEM_ID, sighted.itemId())),
				new ComponentUpdate(LABEL_ID, Map.of(ComponentType.PROP_TEXT, sighted.nameKey()))
			));
		}

		showing.put(id, sighted);
	}

	private static void show(ServerPlayer player, Sighted sighted) {
		// bottom_center reads offsetX as the card's left edge relative to the screen
		// centre, so half the width to the left puts the card dead centre.
		HudBuilder hud = new HudBuilder(OVERLAY_ID)
			.anchor("bottom_center")
			.offset(-WIDTH / 2, ABOVE_HOTBAR)
			.component(new ComponentBuilder(ICON_ID, ComponentType.ITEM_ICON)
				.bounds((WIDTH - ICON_SIZE) / 2, 0, ICON_SIZE, ICON_SIZE)
				.prop(ComponentType.PROP_ITEM_ID, sighted.itemId())
				.build())
			.component(new ComponentBuilder(LABEL_ID, ComponentType.TEXT)
				.bounds(0, ICON_SIZE + GAP, WIDTH, 9)
				.prop(ComponentType.PROP_TEXT, sighted.nameKey())
				.prop(ComponentType.PROP_ALIGN, "center")
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
