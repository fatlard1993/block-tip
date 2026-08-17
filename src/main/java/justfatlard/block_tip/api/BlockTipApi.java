package justfatlard.block_tip.api;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a mod add one line to what the player sees when they look at something.
 *
 * <p>A block-tip card names a thing. Some things also have a fact that cannot be
 * seen by looking at them, and those are exactly the ones a player never learns:
 * a crafter that keeps a template looks like a crafter, a piston set to push
 * three looks like a piston. The name answers "what is that" and this answers
 * "and what is odd about it".
 *
 * <p>One line, deliberately. The card is two lines of a picture and a word and
 * stays readable because it is small; a mod that wants a paragraph wants a book.
 *
 * <p>Two ways in. {@link #line} for a fact that is true of a block wherever it
 * stands, which is most of them. {@link #describe} for a fact that depends on
 * the block in front of you, which costs a call every time somebody looks at
 * anything, so keep it cheap and return null early.
 */
public final class BlockTipApi {
	private BlockTipApi() {}

	private static final Map<String, String> STATIC_LINES = new HashMap<>();
	private static final List<TipProvider> PROVIDERS = new ArrayList<>();

	/**
	 * A fixed line for a block, by registry id.
	 *
	 * @param blockId e.g. {@code "minecraft:crafter"}
	 * @param text    plain words, or a translation key the client will resolve
	 */
	public static void line(String blockId, String text) {
		STATIC_LINES.put(blockId, text);
	}

	/** A line worked out from the block itself. Return null to say nothing. */
	public static void describe(TipProvider provider) {
		PROVIDERS.add(provider);
	}

	/**
	 * The line to show, or null. Called on the look tick, so the static map is
	 * consulted first and providers only run when nothing simpler answered.
	 *
	 * @hidden used by block-tip itself
	 */
	public static String detailFor(ServerLevel level, BlockPos pos, BlockState state,
			ServerPlayer player, String blockId) {
		String fixed = STATIC_LINES.get(blockId);
		if (fixed != null) return fixed;

		for (TipProvider provider : PROVIDERS) {
			try {
				String line = provider.describe(level, pos, state, player);
				if (line != null && !line.isBlank()) return line;
			} catch (Exception | LinkageError error) {
				// A third party's provider is not allowed to break looking at things.
			}
		}
		return null;
	}

	@FunctionalInterface
	public interface TipProvider {
		String describe(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
	}
}
