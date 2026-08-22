package justfatlard.block_tip;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
			// The break bar every tick, the words four times a second. A bar redrawn at the
			// card's own rate would fill in two steps on anything softer than stone.
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					TipHud.progress(player, BreakProgress.of(player));
				} catch (Throwable error) {
					LOGGER.error("[{}] Failed to update break progress for {}",
						MOD_ID, player.getName().getString(), error);
				}
			}

			if (server.getTickCount() % INTERVAL_TICKS != 0) return;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					tick(player);
				} catch (Throwable error) {
					// Throwable, not Exception. A provider deep enough to overflow the stack throws an
					// Error, and an Error let out of here does not spoil one card: it ends the tick,
					// and with it the server. Nothing this mod does is worth that.
					LOGGER.error("[{}] Failed to update tip for {}", MOD_ID, player.getName().getString(), error);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			TipHud.forget(handler.getPlayer().getUUID()));

		// What a block drops is a data pack's answer, so a reload makes ours a guess.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> Drops.forget());

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}

	private static void tick(ServerPlayer player) {
		ServerLevel level = player.level();

		TipPreferences.Mode mode = TipPreferences.modeOf(level, player.getUUID());

		if (mode == TipPreferences.Mode.OFF
			|| (mode == TipPreferences.Mode.SNEAKING && !player.isShiftKeyDown())) {
			TipHud.clear(player);
			return;
		}

		// Spectators are usually looking through things rather than at them, and a
		// card naming whatever is behind a wall is noise.
		if (player.isSpectator()) {
			TipHud.clear(player);
			return;
		}

		Sighted sighted = Sighted.inFrontOf(player);

		// Asked here rather than inside the look itself, so that the command can still name what
		// somebody is pointing at when they want it back.
		if (TipPreferences.hides(level, player.getUUID(), sighted.blockId())) {
			TipHud.clear(player);
			return;
		}

		TipHud.update(player, sighted);
	}
}
