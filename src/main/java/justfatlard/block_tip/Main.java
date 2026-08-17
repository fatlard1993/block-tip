package justfatlard.block_tip;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "block-tip";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Four times a second. A tip that lags behind where you are looking feels
	 * broken, and one recomputed every tick is twenty raycasts a second per player
	 * to answer a question that changes at the speed of a turning head.
	 */
	private static final int INTERVAL_TICKS = 5;

	@Override
	public void onInitialize() {
		TipCommand.register();
		VanillaTips.register();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL_TICKS != 0) return;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					tick(player);
				} catch (Exception error) {
					LOGGER.error("[{}] Failed to update tip for {}", MOD_ID, player.getName().getString(), error);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			TipHud.forget(handler.getPlayer().getUUID()));

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}

	private static void tick(ServerPlayer player) {
		ServerLevel level = player.level();

		if (!TipPreferences.wants(level, player.getUUID())) {
			TipHud.clear(player);
			return;
		}

		// Spectators are usually looking through things rather than at them, and a
		// card naming whatever is behind a wall is noise.
		if (player.isSpectator()) {
			TipHud.clear(player);
			return;
		}

		TipHud.update(player, Sighted.inFrontOf(player));
	}
}
