package justfatlard.block_tip;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * How far through the block in front of them each player is.
 *
 * <p>Nothing here decides when mining stops, because nothing needs to. Each reading is stamped
 * with the tick it was taken on and vanilla takes a fresh one every tick while a player is
 * mining, so a reading that has gone stale is a player who has stopped - whether they finished
 * the block, let go, or looked away. One rule covers all three, and none of them need a hook.
 */
public final class BreakProgress {
	private BreakProgress() {}

	/**
	 * How old a reading may be and still count.
	 *
	 * <p>Two ticks rather than one, because the tip is drawn from the server tick loop and there
	 * is no guarantee about which of the two runs first.
	 */
	private static final long STALE_AFTER = 2;

	private record Reading(float progress, long tick) {}

	private static final Map<UUID, Reading> readings = new ConcurrentHashMap<>();

	/** Called from the mixin, once per tick per player who is mining. */
	public static void note(ServerPlayer player, BlockPos pos, float progress) {
		readings.put(player.getUUID(), new Reading(progress, player.level().getGameTime()));
	}

	/**
	 * How far through the block in front of them this player is, from 0 to 1.
	 *
	 * <p>Not checked against a position, because the card has none to check against and does not
	 * need one: it is rebuilt whenever what the player is looking at changes, which resets the bar
	 * on its own. What that leaves is the tick a block finally breaks - the last reading is a full
	 * one and would flash across the card of whatever was behind it - so a finished reading counts
	 * as no reading.
	 */
	public static float of(ServerPlayer player) {
		Reading reading = readings.get(player.getUUID());
		if (reading == null || reading.progress() >= 1.0F) return 0.0F;
		if (player.level().getGameTime() - reading.tick() > STALE_AFTER) return 0.0F;

		return Math.clamp(reading.progress(), 0.0F, 1.0F);
	}

	public static void forget(UUID player) {
		readings.remove(player);
	}
}
