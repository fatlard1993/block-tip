package justfatlard.block_tip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Who wants to be told, and when.
 *
 * <p>Stored as the players who changed something rather than the players who want the default, so a
 * new player gets tips without having to discover that tips exist. That is the whole point of the
 * mod, and a feature you have to switch on is a feature for people who already knew about it.
 *
 * <p>Kept in the overworld's saved data rather than on the player, because a preference should not
 * be something you can lose by dying somewhere awkward.
 */
public final class TipPreferences {
	private TipPreferences() {}

	/**
	 * {@link #SNEAKING} is the quiet middle: the card is a genuine answer when it is wanted and a
	 * genuine obstruction when it is not, and holding sneak is already the gesture for "I am paying
	 * attention to this particular block". Not the default, because a tip nobody knows how to summon
	 * is a tip nobody gets.
	 */
	public enum Mode { ALWAYS, SNEAKING, OFF }

	/**
	 * A different key from the one used before sneak mode and hidden blocks existed.
	 *
	 * <p>What was stored then was a bare list of the players who had opted out; what is stored now
	 * is an object with three fields. The same key would mean handing the new codec a shape it
	 * cannot read, and a failed read does not leave the file alone - it starts from defaults and
	 * writes over it at the next autosave, so the old preferences would be gone before anybody
	 * noticed they had not been migrated. A new key leaves that file untouched on disk.
	 *
	 * <p>The cost is one-off and known: anybody who had turned tips off before this version has
	 * them on again, once, and turns them off again.
	 */
	private static final String STORAGE_KEY = "block_tip_preferences_2";

	private static final SavedDataType<TipPreferences.Data> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), TipPreferences.Data::new, TipPreferences.Data.CODEC, DataFixTypes.LEVEL);

	private static TipPreferences.Data data(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static Mode modeOf(ServerLevel level, UUID player) {
		TipPreferences.Data store = data(level);
		if (store.off.contains(player)) return Mode.OFF;
		if (store.sneaking.contains(player)) return Mode.SNEAKING;
		return Mode.ALWAYS;
	}

	public static boolean wants(ServerLevel level, UUID player) {
		return modeOf(level, player) != Mode.OFF;
	}

	/**
	 * Flips between off and however they had it before.
	 *
	 * <p>Turning something off and on again should give it back the way it was. Sneak mode used to
	 * be destroyed by this: off cleared the setting, on could only restore the default, and the one
	 * keystroke offered as the easy way to silence the card quietly cost a player the mode they had
	 * chosen, with nothing said about it.
	 */
	public static Mode toggle(ServerLevel level, UUID player) {
		if (wants(level, player)) return set(level, player, Mode.OFF);

		return set(level, player, data(level).sneaking.contains(player) ? Mode.SNEAKING : Mode.ALWAYS);
	}

	/** Off is remembered as a state on top of the others, so it can be lifted without erasing them. */
	public static Mode set(ServerLevel level, UUID player, Mode mode) {
		TipPreferences.Data store = data(level);

		boolean changed = switch (mode) {
			case OFF -> store.off.add(player);
			case SNEAKING -> store.off.remove(player) | store.sneaking.add(player);
			case ALWAYS -> store.off.remove(player) | store.sneaking.remove(player);
		};

		if (changed) store.setDirty();
		return mode;
	}

	/**
	 * Blocks this player would rather not be told about.
	 *
	 * <p>Per player and not per server, because the answer is about what somebody already knows.
	 * Nobody needs to be told what stone is on their thousandth day, and the player beside them on
	 * their first day does.
	 *
	 * <p>Kept as ids rather than blocks so an id from a mod that is not loaded today survives being
	 * saved and read back: uninstalling a mod for a week should not quietly empty the list.
	 */
	public static boolean hides(ServerLevel level, UUID player, String blockId) {
		if (blockId.isEmpty()) return false;

		Set<String> hidden = data(level).hidden.get(player);
		return hidden != null && hidden.contains(blockId);
	}

	/** @return true where this was not already hidden */
	public static boolean hide(ServerLevel level, UUID player, String blockId) {
		// Nothing that is not an id gets written down. Every caller today hands over something the
		// registry gave them, which is exactly why the one that eventually does not should meet a
		// closed door rather than leave a line in the world save that nothing can ever match.
		if (Identifier.tryParse(blockId) == null) return false;

		TipPreferences.Data store = data(level);
		boolean added = store.hidden.computeIfAbsent(player, ignored -> new HashSet<>()).add(blockId);

		if (added) store.setDirty();
		return added;
	}

	/** @return true where this was hidden and now is not */
	public static boolean show(ServerLevel level, UUID player, String blockId) {
		TipPreferences.Data store = data(level);
		Set<String> hidden = store.hidden.get(player);
		if (hidden == null || !hidden.remove(blockId)) return false;

		if (hidden.isEmpty()) store.hidden.remove(player);
		store.setDirty();
		return true;
	}

	/** @return how many were forgotten */
	public static int showAll(ServerLevel level, UUID player) {
		TipPreferences.Data store = data(level);
		Set<String> hidden = store.hidden.remove(player);
		if (hidden == null || hidden.isEmpty()) return 0;

		store.setDirty();
		return hidden.size();
	}

	/** Sorted, because a list somebody is reading back to themselves should be in an order. */
	public static Set<String> hiddenBy(ServerLevel level, UUID player) {
		Set<String> hidden = data(level).hidden.get(player);
		return hidden == null ? Set.of() : new TreeSet<>(hidden);
	}

	private static class Data extends SavedData {
		public static final Codec<TipPreferences.Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.listOf().optionalFieldOf("off", List.of())
				.forGetter(data -> List.copyOf(data.off)),
			UUIDUtil.CODEC.listOf().optionalFieldOf("sneaking", List.of())
				.forGetter(data -> List.copyOf(data.sneaking)),
			// Keyed by the string form of the uuid rather than the int array the others use: a map
			// key has to be a string, and this is the only one of the three that is a map.
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf())
				.optionalFieldOf("hidden", Map.of())
				.forGetter(data -> data.hidden.entrySet().stream().collect(
					java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))))
		).apply(instance, TipPreferences.Data::of));

		private final Set<UUID> off = new HashSet<>();
		private final Set<UUID> sneaking = new HashSet<>();
		private final Map<UUID, Set<String>> hidden = new HashMap<>();

		private static TipPreferences.Data of(List<UUID> off, List<UUID> sneaking,
				Map<UUID, List<String>> hidden) {
			TipPreferences.Data data = new TipPreferences.Data();
			data.off.addAll(off);
			data.sneaking.addAll(sneaking);
			hidden.forEach((player, blocks) -> data.hidden.put(player, new HashSet<>(blocks)));
			return data;
		}
	}
}
