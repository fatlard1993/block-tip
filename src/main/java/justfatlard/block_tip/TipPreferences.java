package justfatlard.block_tip;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Who has asked not to be told.
 *
 * <p>Stored as the list of players who opted out rather than the list who opted
 * in, so a new player gets tips without having to discover that tips exist. That
 * is the whole point of the mod, and a feature you have to switch on is a feature
 * for people who already knew about it.
 *
 * <p>Kept in the overworld's saved data rather than on the player, because a
 * preference should not be something you can lose by dying somewhere awkward.
 */
public final class TipPreferences {
	private TipPreferences() {}

	private static final String STORAGE_KEY = "block_tip_preferences";

	private static final SavedDataType<TipPreferences.Data> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), TipPreferences.Data::new, TipPreferences.Data.CODEC, DataFixTypes.LEVEL);

	private static TipPreferences.Data data(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static boolean wants(ServerLevel level, UUID player) {
		return !data(level).optedOut.contains(player);
	}

	/** @return true when tips are now on for this player */
	public static boolean toggle(ServerLevel level, UUID player) {
		return set(level, player, !wants(level, player));
	}

	/** @return true when tips are now on for this player */
	public static boolean set(ServerLevel level, UUID player, boolean on) {
		TipPreferences.Data store = data(level);
		boolean changed = on ? store.optedOut.remove(player) : store.optedOut.add(player);
		if (changed) store.setDirty();
		return on;
	}

	private static class Data extends SavedData {
		public static final Codec<TipPreferences.Data> CODEC = UUIDUtil.CODEC.listOf()
			.xmap(TipPreferences.Data::of, data -> List.copyOf(data.optedOut));

		private final Set<UUID> optedOut = new HashSet<>();

		private static TipPreferences.Data of(List<UUID> players) {
			TipPreferences.Data data = new TipPreferences.Data();
			data.optedOut.addAll(players);
			return data;
		}
	}
}
