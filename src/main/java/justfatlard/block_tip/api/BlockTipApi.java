package justfatlard.block_tip.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
 * <p>One line each, deliberately. The card is two lines of a picture and a word
 * and stays readable because it is small; a mod that wants a paragraph wants a
 * book. Several mods can speak about the same block, and as many of their lines
 * as fit are shown side by side, so keep yours short enough to share.
 *
 * <p>Two ways in. {@link #line} for a fact that is true of a block wherever it
 * stands, which is most of them. {@link #describe} for a fact that depends on
 * the block in front of you, which costs a call every time somebody looks at
 * anything, so keep it cheap and return null early.
 *
 * <p>{@link #describeEntity} is the same errand pointed at whatever is walking
 * around: the card names mobs and players too, and a living thing can be just as
 * silent about what you are supposed to do with it as a block can.
 */
public final class BlockTipApi {
	private BlockTipApi() {}

	private static final Logger LOGGER = LoggerFactory.getLogger("block-tip");

	/**
	 * Providers already reported as broken.
	 *
	 * <p>A provider that throws throws on every look, four times a second, for as long as somebody
	 * stands there. Saying so once names the mod that needs fixing; saying so every time buries the
	 * log it would have been found in.
	 */
	private static final Set<Class<?>> COMPLAINED = new HashSet<>();

	private static void broke(Object provider, Throwable error) {
		if (COMPLAINED.add(provider.getClass())) {
			LOGGER.warn("[block-tip] {} threw while describing something; its tips are being skipped",
				provider.getClass().getName(), error);
		}
	}

	private static final Map<String, String> STATIC_LINES = new HashMap<>();
	private static final Map<String, String> ICON_OVERRIDES = new HashMap<>();
	private record Ranked(int priority, TipIllustrator provider) {}

	private static final List<Ranked> PROVIDERS = new ArrayList<>();

	/**
	 * What a card has room to say about a block: a line, and a picture in front of it.
	 *
	 * <p>The picture is a second answer and not a decoration, which is why it travels with the words
	 * that won rather than being registered on its own. A crafter loaded with a bread pattern is a
	 * crafter that makes bread, and a picture of a loaf says that in the space "makes bread" would
	 * take, in every language, for every one of the thousands of things a crafter could be set to.
	 *
	 * @param line the words, or empty where the picture is the whole answer
	 * @param icon an item id to draw at the head of the line, or empty for none
	 * @param resultIcon a second item id, drawn after an arrow: what holding the first one gets you
	 */
	public record Tip(String line, String icon, String resultIcon) {
		/** A tip with words and at most one picture, which is nearly all of them. */
		public Tip(String line, String icon) {
			this(line, icon, "");
		}

		public static Tip of(String line) {
			return new Tip(line, "", "");
		}

		public boolean isBlank() {
			return this.line.isBlank() && this.icon.isBlank() && this.resultIcon.isBlank();
		}

		/** Two pictures with an arrow between: hold this, get that. */
		public boolean isExchange() {
			return !this.icon.isBlank() && !this.resultIcon.isBlank();
		}
	}

	/**
	 * A fixed line for a block, by registry id.
	 *
	 * @param blockId e.g. {@code "minecraft:crafter"}
	 * @param text    plain words, or a translation key the client will resolve
	 */
	public static void line(String blockId, String text) {
		STATIC_LINES.put(blockId, text);
	}

	/**
	 * The item to draw for a block that has none of its own.
	 *
	 * <p>Most blocks are their own picture, because most blocks are something you
	 * can hold. A block that only ever exists because worldgen placed it has no
	 * item at all, and the card was drawing an empty square where the answer
	 * should be.
	 *
	 * @param blockId e.g. {@code "better-trees-justfatlard:oak_leaf_stairs"}
	 * @param itemId  what to show instead, e.g. {@code "minecraft:oak_leaves"}
	 */
	public static void icon(String blockId, String itemId) {
		ICON_OVERRIDES.put(blockId, itemId);
	}

	/** @hidden used by block-tip itself */
	public static String iconOverride(String blockId) {
		return ICON_OVERRIDES.get(blockId);
	}

	private static final Set<String> BOSS_BARS = new HashSet<>();

	/**
	 * Say that this creature draws a boss bar of its own.
	 *
	 * <p>The card and the bar live in the same corner of the screen, so one that knows about the
	 * other can sit under it at its width and leave the health to it. Vanilla's two are known here
	 * already; there is no tag or interface that would let this be worked out, so a mod's boss has
	 * to say so.
	 *
	 * @param entityTypeId e.g. {@code "cloud-kingdoms-justfatlard:storm_titan"}
	 */
	public static void bossBar(String entityTypeId) {
		BOSS_BARS.add(entityTypeId);
	}

	/** @hidden used by block-tip itself */
	public static boolean drawsBossBar(String entityTypeId) {
		return BOSS_BARS.contains(entityTypeId);
	}

	private static final List<BarCheck> BAR_CHECKS = new ArrayList<>();

	/**
	 * Answer whether this player has a bar of yours on screen right now.
	 *
	 * <p>{@link #bossBar} covers the case where the bar belongs to the thing being looked at. This
	 * covers the commoner one: a bar that is simply up. A village, a raid, a timer - the card has
	 * no way to know, because a boss bar is a field inside whatever owns it and nothing central
	 * lists them. Asked on the look tick, so answer it cheaply.
	 *
	 * <p>A predicate rather than a pair of "shown"/"hidden" calls, so there is no second copy of
	 * the truth here to fall out of step with yours, and nothing to clean up when a player leaves.
	 */
	public static void bossBarCheck(BarCheck check) {
		BAR_CHECKS.add(check);
	}

	/** @hidden used by block-tip itself */
	public static boolean anyBossBar(ServerPlayer player) {
		for (BarCheck check : BAR_CHECKS) {
			try {
				if (check.showing(player)) return true;
			} catch (Exception | LinkageError error) {
				broke(check, error);
			}
		}
		return false;
	}

	@FunctionalInterface
	public interface BarCheck {
		boolean showing(ServerPlayer player);
	}

	/** A line worked out from the block itself. Return null to say nothing. */
	public static void describe(TipProvider provider) {
		describe(0, provider);
	}

	/**
	 * The same, for a fact that is better shown than written.
	 *
	 * <p>One provider rather than a second registration beside {@link #describe}, so the picture
	 * cannot end up belonging to a different mod's words: whoever wins the line brings their own.
	 */
	public static void illustrate(TipIllustrator provider) {
		illustrate(0, provider);
	}

	public static void illustrate(int priority, TipIllustrator provider) {
		PROVIDERS.add(new Ranked(priority, provider));
		PROVIDERS.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
	}

	/**
	 * The same, but said before or after other people's.
	 *
	 * <p>Needed because one block can be several things at once and there is only
	 * one line: a chest can be the village's and also one you have already
	 * emptied, and which of those is worth saying depends on which is news. Higher
	 * priorities speak first, and the first answer wins.
	 *
	 * <p>Registration order would decide it otherwise, and registration order is
	 * whichever mod happened to initialise first.
	 */
	public static void describe(int priority, TipProvider provider) {
		illustrate(priority, (level, pos, state, player) -> {
			String line = provider.describe(level, pos, state, player);
			return line == null ? null : Tip.of(line);
		});
	}

	/**
	 * Everything anybody has to say about this block, most important first.
	 *
	 * <p>All of them rather than the first, because the card knows how wide it is and this does
	 * not: a chest that is a village's and also nearly full has two facts worth having and room
	 * for both, and deciding here which one to drop would be deciding it blind. Priority still
	 * settles the order, so what gets cut when the line runs out is the least important thing.
	 *
	 * <p>Capped, since a line has a length and a hundred providers cannot all be on it. Duplicates
	 * are dropped: two mods that reach the same conclusion about a block should say it once.
	 *
	 * @hidden used by block-tip itself
	 */
	public static List<Tip> tipsFor(ServerLevel level, BlockPos pos, BlockState state,
			ServerPlayer player, String blockId) {
		List<Tip> found = new ArrayList<>();

		String fixed = STATIC_LINES.get(blockId);
		if (fixed != null) found.add(Tip.of(fixed));

		for (Ranked ranked : PROVIDERS) {
			if (found.size() >= MOST_LINES) break;

			try {
				Tip tip = ranked.provider().describe(level, pos, state, player);
				if (tip == null || tip.isBlank()) continue;
				// Only words can be duplicates. Two tips that are each a picture and nothing else have
				// the same empty line and are not the same answer, and dropping the second as a
				// repeat threw away a picture nobody could see had gone.
				if (!tip.line().isBlank()
					&& found.stream().anyMatch(seen -> seen.line().equals(tip.line()))) continue;

				found.add(tip);
			} catch (Exception | LinkageError error) {
				// A third party's provider is not allowed to break looking at things.
				broke(ranked.provider(), error);
			}
		}
		return found;
	}

	/** More than the widest card could ever fit, and few enough to be cheap to gather. */
	private static final int MOST_LINES = 6;

	private static final List<Inspector> INSPECTORS = new ArrayList<>();

	/**
	 * Say whether this player may be told anything about this block.
	 *
	 * <p>The card reads block entities: how full a chest is, the exact number a comparator would
	 * give, whether a furnace has run dry, what a spawner makes. On a server where that is nobody
	 * else's business, the owner of the chest has no say in it - every switch this mod has belongs
	 * to the person looking, not to the person looked at.
	 *
	 * <p>A claim or protection mod can close that here. Refusing leaves the block named, because a
	 * name is what anybody standing there can already see; what it withholds is everything the card
	 * would otherwise have read out of the inside of it.
	 */
	public static void inspection(Inspector inspector) {
		INSPECTORS.add(inspector);
	}

	/** @hidden used by block-tip itself */
	public static boolean mayInspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		for (Inspector inspector : INSPECTORS) {
			try {
				if (!inspector.mayInspect(level, pos, state, player)) return false;
			} catch (Exception | LinkageError error) {
				// A guard that throws is a guard that said no.
				broke(inspector, error);
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	public interface Inspector {
		boolean mayInspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
	}

	@FunctionalInterface
	public interface TipProvider {
		String describe(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
	}

	@FunctionalInterface
	public interface TipIllustrator {
		Tip describe(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
	}

	private static final List<EntityTipProvider> ENTITY_PROVIDERS = new ArrayList<>();

	/**
	 * A line about the thing in front of you that happens to be alive. Return null to say nothing.
	 *
	 * <p>No fixed-line form to match {@link #line}: an entity that is worth saying something about
	 * is almost always worth saying it about conditionally, because entities have state and blocks
	 * of one id mostly do not. A hint about how to trade with somebody is worth showing right up
	 * until they are already trading.
	 *
	 * <p>Called on the look tick, same as the block providers, so the same rule applies: cheap, and
	 * return null early.
	 */
	public static void describeEntity(EntityTipProvider provider) {
		ENTITY_PROVIDERS.add(provider);
	}

	/**
	 * The line to add for an entity, or null.
	 *
	 * <p>No priorities here yet, because nothing has needed to argue: registration order decides,
	 * and the first answer wins.
	 *
	 * @hidden used by block-tip itself
	 */
	public static String detailForEntity(Entity entity, ServerPlayer player) {
		for (EntityTipProvider provider : ENTITY_PROVIDERS) {
			try {
				String line = provider.describe(entity, player);
				if (line != null && !line.isBlank()) return line;
			} catch (Exception | LinkageError error) {
				// A third party's provider is not allowed to break looking at things.
				broke(provider, error);
			}
		}
		return null;
	}

	/**
	 * @param entity what is being looked at
	 * @param player who is looking, because the answer often depends on them
	 */
	@FunctionalInterface
	public interface EntityTipProvider {
		String describe(Entity entity, ServerPlayer player);
	}
}
