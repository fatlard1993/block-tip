package justfatlard.block_tip;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * The little card that names what you are looking at.
 *
 * <p>A picture and a word, and a second line only where there is something the
 * name does not already say: how much fight is left in a mob, or whatever a mod
 * has to add about a block. The question is "what is that", and everything else
 * on the card either answers at a glance or gets cut when the line runs out.
 *
 * <p>Two rows at most, and the second only when it is carrying something. What
 * cannot be said in a glyph or a short fact does not go here.
 *
 * <p>It sits at the top of the screen, clear of the effect icons, and drops to
 * the next row of the boss bar stack whenever one is up.
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
	private static final String DETAIL_ICON_ID = "detail-icon";
	private static final String EXCHANGE_ARROW_ID = "exchange-arrow";
	private static final String RESULT_ICON_ID = "result-icon";
	private static final String MARK_ID = "mark";
	private static final String SPAWN_ID = "spawn";
	private static final String SOURCE_ID = "source";
	private static final String TOOL_ID = "tool";

	/**
	 * An item always draws sixteen pixels square, so a smaller one is a scale rather than a size.
	 * The scale is applied about the component's centre, so the box stays sixteen while the drawn
	 * icon sits {@link #ICON_INSET} inside it on every edge.
	 */
	private static final int ICON_BOX = 16;

	/**
	 * Small enough to sit inside a line of text, large enough for a block to be recognised. A flat
	 * item sprite fills its sixteen pixels, but a block renders as a three-dimensional model that
	 * sits well inside them, so a low-profile block - a carpet, a slab - loses more to the scale
	 * than a sprite does.
	 */
	private static final int ICON_DRAWN = 12;
	private static final float ICON_SCALE = (float) ICON_DRAWN / ICON_BOX;
	private static final int ICON_INSET = (ICON_BOX - ICON_DRAWN) / 2;

	private static final int PADDING = 3;

	/**
	 * Wide enough to finish a long name. "Deepslate Diamond Ore" beside a mod's name, a tool and a
	 * mark is the crowded case, and a card that cannot finish a sentence is worse than no card.
	 * Width is cheap here because the panel behind it is barely there.
	 */
	private static final int WIDTH = 212;

	/**
	 * The mod's name, small and pushed to the far end of the name's own line.
	 *
	 * <p>Right-aligned rather than trailing the name, because only the client knows how wide a
	 * translated name comes out and the server is the one placing this. Two texts on one line with
	 * a fixed split is the arrangement that needs no measuring: the name grows from the left, the
	 * mod hangs off the right, and neither can walk into the other.
	 *
	 * <p>{@link #MOD_BUDGET} is the room the text gets before the shrink, so a name is only cut
	 * short when it would still overrun after being made small, while the box itself is the
	 * {@link #MOD_VISUAL} it will occupy once shrunk.
	 *
	 * <p>Centred in that box, not flush to its right edge: the scale is applied about the
	 * component's centre, so a centred line stays put whatever its length while a right-aligned one
	 * creeps inward as it shortens.
	 */
	private static final float MOD_SCALE = 0.7f;
	private static final int MOD_VISUAL = 56;
	private static final int MOD_BUDGET = Math.round(MOD_VISUAL / MOD_SCALE);


	/** Gap between the name and whatever is parked at the end of its line. */
	private static final int GAP = 2;

	/**
	 * The corner, kept for the spawn mark whether or not there is one to draw.
	 *
	 * <p>Reserved rather than made room for. Whether mobs can appear on a block changes from one
	 * block to the next, so a slot that came and went would drag the icon and the name sideways
	 * every time a player swept their view along a wall. Ten pixels of empty corner costs a card
	 * two hundred wide nothing, and a slot that never moves is one the mark can be switched on and
	 * off inside, without rebuilding the card around it.
	 *
	 * <p>Eight, which is the skull's measured advance in the game's own font sheet - the same width
	 * as the mark at the other end of the line.
	 */
	private static final int CORNER_WIDTH = 8;

	/** Everything the card has to say starts after the corner. */
	/**
	 * Where the card's own content starts, which is the left edge and nothing further in.
	 *
	 * <p>The skull used to have a column of its own here, held open on every card so that the one
	 * card in three that carries a skull had somewhere to put it. That is a strip of empty
	 * background most of the time and a wider card always. It sits on the icon's corner now.
	 */
	private static final int CONTENT_X = PADDING;

	/** Icon on the left, name beside it, the way a tooltip reads. */
	private static final int NAME_X = CONTENT_X + ICON_DRAWN + 3;

	/** A row tall enough for the icon to sit inside without pushing the text off its baseline. */
	private static final int LINE = 10;
	private static final int HEIGHT_ONE_LINE = PADDING * 2 + LINE;

	/** Down from the top edge, clear of the effect icons that live up there. */
	private static final int BELOW_TOP = 8;

	/**
	 * Where the card goes when the thing in front of you has a bar of its own.
	 *
	 * <p>Vanilla stacks its boss bars from twelve pixels down, nineteen apart, each one a hundred
	 * and eighty-two wide. A card that lands on the next row at that width reads as the next thing
	 * in that stack; the same card at its own width, over the top of the bar, reads as two programs
	 * arguing about who owns the top of the screen.
	 *
	 * <p>The next row, less the line the card does not use. Nineteen of that stride is five pixels
	 * of bar and a line of name above it, and the card has no name to hang there - taking the row
	 * whole left it floating a title's height below a bar it is meant to be reading with.
	 *
	 * <p>One bar's worth. A player with two bars up is a player about to have a bad time anyway,
	 * and the server cannot see anybody else's bars from here.
	 */
	private static final int BOSS_BAR_WIDTH = 182;
	private static final int BOSS_BAR_TITLE = 9;
	private static final int BOSS_BAR_NEXT_ROW = 31 - BOSS_BAR_TITLE;

	/**
	 * Barely there, and no border at all.
	 *
	 * <p>A tooltip is opaque and framed because it appears where you clicked and leaves again. This
	 * one sits on screen for as long as you are looking at anything, so it has to be quiet enough
	 * to be looked past: a wash dark enough to keep white text legible against snow or sky, and
	 * nothing else. No bevel, no frame, just enough ground for the letters to sit on.
	 */
	private static final String PANEL_BACKGROUND = "#80000000";

	/**
	 * The break bar: the card's own bottom edge, filling as the block gives way.
	 *
	 * <p>Along the bottom rather than anywhere inside, because the card is already as small as it
	 * will go and a progress bar is the one thing that can be read without being looked at. Two
	 * pixels is enough to see in peripheral vision and little enough to be nothing when idle.
	 */
	private static final String PROGRESS_ID = "progress";
	private static final int PROGRESS_HEIGHT = 2;
	private static final String PROGRESS_COLOR = "#FFE0E0E0";
	/**
	 * The same two pixels, red, when the thing in front of you can bleed.
	 *
	 * <p>One edge and two meanings, because you are never mining a block and facing a mob at the
	 * same moment: the bar says how far through the block you are, or how much fight is left in
	 * the animal, and which one it is answers itself from what you are looking at. Red carries
	 * that on its own, which is why the number that used to sit in the detail row is gone - the
	 * card already declined to print health under a boss bar for the same reason.
	 */
	private static final String HEALTH_COLOR = "#FFD03A3A";
	private static final String PANEL_BORDER_NONE = "none";

	/** Dimmer than the name, because it is the footnote and not the answer. */
	private static final String DETAIL_COLOR = "#FFA0A0A0";

	/**
	 * Between two facts sharing the line.
	 *
	 * <p>A dot rather than a comma, because the facts have commas of their own - "harp, F#3" is one
	 * fact - and a separator that looks like the punctuation inside what it separates is not
	 * separating anything.
	 */
	private static final String BETWEEN = " \u00B7 ";

	/**
	 * How wide a line comes out, measured rather than guessed.
	 *
	 * <p>The server is placing this card and only the client has the font, so the width has to be
	 * worked out from here. The glyphs this card draws were read off the game's own font sheet and
	 * are listed exactly; everything else takes six, which is the widest an ordinary letter gets,
	 * and anything outside ASCII takes the full-width sixteen the fallback font actually uses.
	 *
	 * <p>The estimate errs generously on purpose: the cost of overestimating is one fact left off a
	 * card that had room for it, and the cost of underestimating is a sentence cut in half. Erring
	 * generously is not the same as erring blindly, though - a separator charged at twice its width
	 * quietly costs a fact on every line that has one.
	 */
	private static final int LETTER = 6;
	private static final int SPACE = 4;
	private static final int FALLBACK_LETTER = 16;

	/** Dimmer again, and cool rather than neutral, so it reads as a label on the card and not as
	 * another thing the card is telling you. */
	private static final String SOURCE_COLOR = "#FF7A8CA8";

	private static final String MARK_OK_COLOR = "#FF55FF55";
	private static final String MARK_WRONG_COLOR = "#FFFF5555";

	/** Vanilla's own yellow, agreeing with the star: this one costs nothing to ignore. */
	private static final String MARK_OPTIONAL_COLOR = "#FFFFFF55";

	/**
	 * One glyph per meaning, so the shape carries it and the colour only agrees.
	 *
	 * <p>The plus is the tool you hold being too soft - an errand to a furnace rather than to your
	 * hotbar. The star is the one that costs nothing to ignore. They were the same glyph in two
	 * colours until it was pointed out that red against yellow is the one distinction a good share
	 * of players cannot make, and that a screenshot in grey makes it for nobody.
	 */
	private static final String MARK_OK = "\u2714";
	private static final String MARK_WEAK = "+";
	private static final String MARK_OPTIONAL = "*";
	private static final String MARK_WRONG = "\u2718";

	/**
	 * A skull in the corner: things can appear on top of this block.
	 *
	 * <p>Dim as the detail line, and for the same reason it is a mark rather than a sentence. This
	 * is true of half the world, so it is the thing a player reads most and needs least: it has to
	 * be checkable at a glance and ignorable just as fast.
	 *
	 */
	private static final String MARK_SPAWN = "\u2620";
	private static final String SPAWN_COLOR = DETAIL_COLOR;

	/**
	 * Each glyph shrunk to land at about the same size as the others.
	 *
	 * <p>They come from different corners of the font and are drawn to different heights: a skull
	 * is nearly a full line tall, a tick a little less, and a plus is a small ASCII character
	 * sitting on the baseline. Left alone they read as three different weights of thing, when they
	 * are all meant to be the same kind of small remark.
	 */
	private static final float SPAWN_SCALE = 0.75F;
	private static final float MARK_SCALE = 0.85F;

	/** A glyph box big enough for any of them once scaled, centred on the corner it marks. */
	private static final int GLYPH_BOX = 10;

	/** What each player is currently being told, so it is only said once. */
	private static final Map<UUID, Sighted> showing = new ConcurrentHashMap<>();

	/** The bar's last drawn width, so a tick that changes nothing sends nothing. */
	private static final Map<UUID, Integer> progressWidth = new ConcurrentHashMap<>();

	/**
	 * Draw the break bar, pushed every tick rather than with the rest of the card.
	 *
	 * <p>The card is redrawn four times a second, which is plenty for words and far too slow for
	 * this: a block that takes half a second to break would fill the bar in two steps. Only one
	 * component moves, and only while somebody is actually mining, so the extra traffic is
	 * bounded by the thing that causes it.
	 */
	public static void progress(ServerPlayer player, float fraction) {
		UUID id = player.getUUID();
		Sighted current = showing.get(id);
		if (current == null) return;
		// The bottom edge is the mob's health while one is in front of you, and the two would
		// otherwise take turns writing to it every tick.
		if (current.health() >= 0F) return;

		int width = Math.round(cardWidth(current) * fraction);
		Integer last = progressWidth.get(id);
		if (last != null && last == width) return;

		progressWidth.put(id, width);
		PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
			new ComponentUpdate(PROGRESS_ID, Map.of(
				ComponentType.PROP_WIDTH, String.valueOf(width)))));
	}

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

		// The panel is sized and the rows are placed when the card is built, so anything that moves a
		// row has to be rebuilt rather than updated. Counting rows is not enough to tell: a card
		// with a detail and no mod has the same two rows as one with a mod and no detail, and they
		// put their pieces in different places.
		if (current == null || !shapeOf(current).equals(shapeOf(sighted))) {
			progressWidth.remove(id);
			show(player, sighted);
		} else {
			PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
				new ComponentUpdate(ICON_ID, Map.of(ComponentType.PROP_ITEM_ID, sighted.itemId())),
				new ComponentUpdate(LABEL_ID, Map.of(ComponentType.PROP_TEXT_KEY, sighted.nameKey())),
				new ComponentUpdate(DETAIL_ID, Map.of(
					detailOf(sighted).joined() ? ComponentType.PROP_TEXT : ComponentType.PROP_TEXT_KEY,
					detailOf(sighted).text())),
				new ComponentUpdate(DETAIL_ICON_ID, Map.of(
					ComponentType.PROP_ITEM_ID, detailIcon(sighted))),
				new ComponentUpdate(EXCHANGE_ARROW_ID, Map.of(
					ComponentType.PROP_TEXT, resultIcon(sighted).isBlank() ? "" : EXCHANGE_ARROW)),
				new ComponentUpdate(RESULT_ICON_ID, Map.of(
					ComponentType.PROP_ITEM_ID, resultIcon(sighted))),
				new ComponentUpdate(SOURCE_ID, Map.of(ComponentType.PROP_TEXT, sighted.modName())),
				new ComponentUpdate(TOOL_ID, Map.of(
					ComponentType.PROP_ITEM_ID, hasToolIcon(sighted) ? sighted.toolItem() : "")),
				new ComponentUpdate(SPAWN_ID, Map.of(
					ComponentType.PROP_TEXT, spawnText(sighted))),
				new ComponentUpdate(MARK_ID, Map.of(
					ComponentType.PROP_TEXT, markText(sighted),
					ComponentType.PROP_COLOR, markColor(sighted))),
				new ComponentUpdate(PROGRESS_ID, Map.of(
					ComponentType.PROP_WIDTH, String.valueOf(barWidth(sighted))))
			));
		}

		showing.put(id, sighted);
	}

	/** How much of the bottom edge is filled: health for a mob, nothing for a block. */
	private static int barWidth(Sighted sighted) {
		return sighted.health() < 0F ? 0 : Math.round(cardWidth(sighted) * sighted.health());
	}

	/**
	 * Everything about a card that decides where its pieces go, as a string to compare.
	 *
	 * <p>Which rows exist and in what order, plus what is reserved at the end of the name line. Two
	 * cards agreeing here can be updated in place; disagreeing on any of it means rebuilding.
	 */
	private static String shapeOf(Sighted sighted) {
		return (hasDetail(sighted) ? "d" : "-")
			// The bar's colour is set when the card is built, so block and mob are different shapes
			+ (sighted.health() >= 0F ? "h" : "-")
			+ (detailIcon(sighted).isBlank() ? "-" : "p")
			+ (resultIcon(sighted).isBlank() ? "-" : "x")
			+ (sighted.underBossBar() ? "b" : "-")
			+ (sighted.modName().isBlank() ? "-" : "m")
			+ (hasToolIcon(sighted) ? "i" : "-")
			+ (hasMark(sighted) ? "k" : "-");
	}

	/** How tall the card has to be, in rows: the name, and the detail line when there is one. */
	private static int rowsOf(Sighted sighted) {
		return hasDetail(sighted) ? 2 : 1;
	}

	/**
	 * Whether there is a second row at all.
	 *
	 * <p>A picture counts: a mod with a loaf to show and nothing to add in words still needs the row
	 * it would be drawn on, and the panel is only as tall as the rows it knows about.
	 */
	private static boolean hasDetail(Sighted sighted) {
		return !sighted.details().isEmpty();
	}

	/**
	 * The picture at the head of the second line, which belongs to the line that starts it.
	 *
	 * <p>Read off the first tip rather than stored beside the words, because a picture kept in its
	 * own field is a picture that can be drawn in front of somebody else's sentence: the mod that
	 * won the row says the words, and the only picture that can appear is the one that came with
	 * them. A first tip carrying no picture leaves the space to the text.
	 */
	private static String detailIcon(Sighted sighted) {
		return sighted.details().isEmpty() ? "" : sighted.details().getFirst().icon();
	}

	/** The second picture of an exchange, or empty when the first detail is an ordinary one. */
	private static String resultIcon(Sighted sighted) {
		return sighted.details().isEmpty() ? "" : sighted.details().getFirst().resultIcon();
	}

	/** The arrow between the two, and how much room it takes. */
	private static final String EXCHANGE_ARROW = "\u2192";
	private static final int ARROW_WIDTH = 6;

	/**
	 * Where the detail text starts: past one picture normally, past picture-arrow-picture for an
	 * exchange. Derived from the same three widths the components are placed with, so the text
	 * cannot drift out of step with what is drawn to its left.
	 */
	private static int detailTextX(Sighted sighted) {
		if (detailIcon(sighted).isBlank()) return CONTENT_X;
		if (resultIcon(sighted).isBlank()) return NAME_X;
		return NAME_X + ARROW_WIDTH + GAP + ICON_DRAWN + 3;
	}

	/** As wide as the card is, or as wide as the bar it is sitting under. */
	private static int cardWidth(Sighted sighted) {
		return sighted.underBossBar() ? BOSS_BAR_WIDTH : WIDTH;
	}

	/** How much room the second line has, which depends on whether a picture is using its start. */
	private static int detailWidth(Sighted sighted) {
		return cardWidth(sighted) - PADDING - detailTextX(sighted);
	}

	/**
	 * As many of the facts as the line will hold, in the order they were offered.
	 *
	 * <p>The first one is always shown, even if it is too long, because a card that answers nothing
	 * is worse than one that answers at length. After that the line is filled while there is room
	 * and stops the moment there is not: the list is ordered by what matters, so anything skipped
	 * to fit something shorter would be a smaller fact pushing out a bigger one.
	 */
	/**
	 * The second line, and whether it is still something a client could translate.
	 *
	 * <p>A single fact is passed on as a translation key, so a mod that supplied one gets it said
	 * in the reader's own language. Two facts joined by a dot are no longer a key and never resolve
	 * as one, so they travel as the words themselves - handing the client "mymod.tip.thing - 50%
	 * full" to look up asks it a question with no answer.
	 */
	private record Detail(String text, boolean joined) {}

	private static Detail detailOf(Sighted sighted) {
		String text = detailText(sighted);
		return new Detail(text, text.contains(BETWEEN));
	}

	private static String detailText(Sighted sighted) {
		int room = detailWidth(sighted);
		StringBuilder line = new StringBuilder();
		int width = 0;

		for (BlockTipApi.Tip tip : sighted.details()) {
			String detail = tip.line();
			if (detail.isBlank()) continue;

			if (line.isEmpty()) {
				line.append(detail);
				width = textWidth(detail);
				continue;
			}

			if (width + textWidth(BETWEEN) + textWidth(detail) > room) break;

			line.append(BETWEEN).append(detail);
			width += textWidth(BETWEEN) + textWidth(detail);
		}
		return line.toString();
	}

	private static int textWidth(String text) {
		int width = 0;
		for (int index = 0; index < text.length(); index++) {
			width += advanceOf(text.charAt(index));
		}
		return width;
	}

	/** Measured from the game's own font sheet for the glyphs this card draws. */
	private static int advanceOf(char glyph) {
		return switch (glyph) {
			case ' ' -> SPACE;
			case '\u00B7' -> 2;
			case '\u2665' -> 6;
			case '\u2714', '\u2718' -> 7;
			case '\u2620' -> 8;
			default -> glyph < 0x80 ? LETTER : FALLBACK_LETTER;
		};
	}

	/** Whether to reserve room at the end of the name line; what goes there was decided upstream. */
	private static boolean hasToolIcon(Sighted sighted) {
		return !sighted.toolItem().isBlank();
	}

	/** The corner slot exists on every card; only what is in it changes. */
	private static String spawnText(Sighted sighted) {
		return sighted.spawnable() ? MARK_SPAWN : "";
	}

	private static String markText(Sighted sighted) {
		return switch (sighted.mark()) {
			case GOOD -> MARK_OK;
			case WRONG_TIER -> MARK_WEAK;
			case OPTIONAL -> MARK_OPTIONAL;
			case BAD -> MARK_WRONG;
			case NONE -> "";
		};
	}

	/**
	 * Green for done, yellow for optional, red for something about to be lost.
	 *
	 * <p>The colour is what makes the plus readable: the same glyph says "upgrade this" in red and
	 * "this would help" in yellow, and the difference between them is whether ignoring it costs
	 * anything.
	 */
	private static String markColor(Sighted sighted) {
		return switch (sighted.mark()) {
			case GOOD -> MARK_OK_COLOR;
			case OPTIONAL -> MARK_OPTIONAL_COLOR;
			default -> MARK_WRONG_COLOR;
		};
	}

	/** Whether anything is parked at the end of the name at all. */
	private static boolean hasMark(Sighted sighted) {
		return !markText(sighted).isEmpty();
	}

	private static void show(ServerPlayer player, Sighted sighted) {
		int height = HEIGHT_ONE_LINE + (rowsOf(sighted) - 1) * LINE;
		int width = cardWidth(sighted);

		// The text sits on the first row; the icon's box is centred on that same row, which puts
		// the drawn icon level with the letters rather than towering over them.
		int labelY = PADDING + (LINE - 9) / 2;
		int iconY = PADDING + (LINE - ICON_BOX) / 2;

		int detailY = PADDING + LINE;

		// The mod's picture sits in the icon's own column, so a card carrying one reads as two rows
		// of picture-then-words rather than a line of text with something stuck on the front. Its
		// text starts where the name does; without a picture the line keeps the full width.
		Detail detail = detailOf(sighted);
		int detailTextX = detailTextX(sighted);
		int detailWidth = detailWidth(sighted);
		int detailIconY = detailY + (LINE - ICON_BOX) / 2;

		// The end of the name line is only reserved for what is actually going there, each piece
		// stacking inward from the right: a vanilla block that asks for no tool gives the whole
		// width back to its name.
		boolean hasMod = !sighted.modName().isBlank();
		boolean toolIcon = hasToolIcon(sighted);
		boolean mark = hasMark(sighted);

		// The mark rides the tool icon's top-right corner instead of taking a slot of its own, so a
		// verdict costs the line nothing beyond the picture it is a verdict about.
		int rightEdge = width - PADDING;
		int iconRight = rightEdge;
		int toolIconX = iconRight - ICON_DRAWN - ICON_INSET;

		int modRight = toolIcon ? iconRight - ICON_DRAWN - GAP : rightEdge;
		int modX = modRight - MOD_VISUAL;

		int contentLeft = hasMod ? modRight - MOD_VISUAL
			: toolIcon ? iconRight - ICON_DRAWN
			: rightEdge;
		int nameWidth = contentLeft - GAP - NAME_X;

		// Corners: the block icon's top-left for the skull, the tool icon's top-right for the mark.
		// Both are centred on the corner itself, so the glyph half-overlaps the picture the way a
		// badge sits on a shoulder.
		int iconTop = iconY + ICON_INSET;
		int spawnX = CONTENT_X - GLYPH_BOX / 2;
		int spawnY = iconTop - 3;
		int markX = toolIconX + ICON_INSET + ICON_DRAWN - GLYPH_BOX / 2;
		int markY = iconTop - 3;

		HudBuilder hud = new HudBuilder(OVERLAY_ID)
			.anchor("top_center")
			.offset(0, sighted.underBossBar() ? BOSS_BAR_NEXT_ROW : BELOW_TOP)
			.component(new ComponentBuilder(PANEL_ID, ComponentType.PANEL)
				.bounds(0, 0, width, height)
				.prop(ComponentType.PROP_BACKGROUND, PANEL_BACKGROUND)
				.prop(ComponentType.PROP_BORDER, PANEL_BORDER_NONE)
				.build())
			// Always laid down, even at nothing wide: a component that does not exist is one no
			// later update can reach, and this one is at nothing wide almost all of the time.
			.component(new ComponentBuilder(PROGRESS_ID, ComponentType.SPRITE)
				.bounds(0, height - PROGRESS_HEIGHT, barWidth(sighted), PROGRESS_HEIGHT)
				.prop(ComponentType.PROP_COLOR,
					sighted.health() >= 0F ? HEALTH_COLOR : PROGRESS_COLOR)
				// Pushed every tick, so it should land where it is told rather than spend three
				// ticks easing toward a width that has already moved on.
				.prop(ComponentType.PROP_INTERP_TICKS, "1")
				.build())
			// Plain text, like the mark at the other end: a skull is a skull in every language.
			// Sat on the block icon's top-left corner rather than beside it, so it costs the card
			// no width on the cards that do not have one.
			.component(new ComponentBuilder(SPAWN_ID, ComponentType.TEXT)
				.bounds(spawnX, spawnY, GLYPH_BOX, 9)
				.prop(ComponentType.PROP_TEXT, spawnText(sighted))
				.prop(ComponentType.PROP_ALIGN, "center")
				.prop(ComponentType.PROP_SCALE, String.valueOf(SPAWN_SCALE))
				.prop(ComponentType.PROP_COLOR, SPAWN_COLOR)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.component(new ComponentBuilder(ICON_ID, ComponentType.ITEM_ICON)
				.bounds(CONTENT_X - ICON_INSET, iconY, ICON_BOX, ICON_BOX)
				.prop(ComponentType.PROP_SCALE, String.valueOf(ICON_SCALE))
				.prop(ComponentType.PROP_ITEM_ID, sighted.itemId())
				.build())
			.component(new ComponentBuilder(LABEL_ID, ComponentType.TEXT)
				.bounds(NAME_X, labelY, nameWidth, 9)
				.prop(ComponentType.PROP_TEXT_KEY, sighted.nameKey())
				// Wrapped to one line so a long name is cut short rather than running out past the
				// panel and dragging the card off centre.
				.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(nameWidth))
				.prop(ComponentType.PROP_MAX_LINES, "1")
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			// Parked at the end of the name line beside the cross, and empty the rest of the time.
			.component(new ComponentBuilder(TOOL_ID, ComponentType.ITEM_ICON)
				.bounds(toolIconX, iconY, ICON_BOX, ICON_BOX)
				.prop(ComponentType.PROP_SCALE, String.valueOf(ICON_SCALE))
				.prop(ComponentType.PROP_ITEM_ID, toolIcon ? sighted.toolItem() : "")
				.build())
			// Always built, usually empty: a component that exists from the start can
			// be updated in place, and one that appeared later would need the whole
			// card rebuilt every time a player looked at something unusual.
			.component(new ComponentBuilder(DETAIL_ICON_ID, ComponentType.ITEM_ICON)
				.bounds(CONTENT_X - ICON_INSET, detailIconY, ICON_BOX, ICON_BOX)
				.prop(ComponentType.PROP_SCALE, String.valueOf(ICON_SCALE))
				.prop(ComponentType.PROP_ITEM_ID, detailIcon(sighted))
				.build())
			// The arrow and the second picture, built always and usually empty for the same
			// reason the first picture is: a component that exists from the start can be updated
			// in place, where one that appeared on first sight of a sheep would rebuild the card.
			.component(new ComponentBuilder(EXCHANGE_ARROW_ID, ComponentType.TEXT)
				.bounds(NAME_X, detailY, ARROW_WIDTH, 9)
				.prop(ComponentType.PROP_TEXT, resultIcon(sighted).isBlank() ? "" : EXCHANGE_ARROW)
				.prop(ComponentType.PROP_COLOR, DETAIL_COLOR)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.component(new ComponentBuilder(RESULT_ICON_ID, ComponentType.ITEM_ICON)
				.bounds(NAME_X + ARROW_WIDTH + GAP - ICON_INSET, detailIconY, ICON_BOX, ICON_BOX)
				.prop(ComponentType.PROP_SCALE, String.valueOf(ICON_SCALE))
				.prop(ComponentType.PROP_ITEM_ID, resultIcon(sighted))
				.build())
			.component(new ComponentBuilder(DETAIL_ID, ComponentType.TEXT)
				.bounds(detailTextX, detailY, detailWidth, 9)
				.prop(detail.joined() ? ComponentType.PROP_TEXT : ComponentType.PROP_TEXT_KEY, detail.text())
				.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(detailWidth))
				.prop(ComponentType.PROP_MAX_LINES, "1")
				.prop(ComponentType.PROP_COLOR, DETAIL_COLOR)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.component(new ComponentBuilder(SOURCE_ID, ComponentType.TEXT)
				.bounds(modX, labelY, MOD_VISUAL, 9)
				.prop(ComponentType.PROP_TEXT, sighted.modName())
				.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(MOD_BUDGET))
				.prop(ComponentType.PROP_MAX_LINES, "1")
				.prop(ComponentType.PROP_ALIGN, "center")
				.prop(ComponentType.PROP_SCALE, String.valueOf(MOD_SCALE))
				.prop(ComponentType.PROP_COLOR, SOURCE_COLOR)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			// Plain text rather than a translation key: a tick is a tick in every language, and
			// running it through the client's lookup would only give it a chance to come back wrong.
			.component(new ComponentBuilder(MARK_ID, ComponentType.TEXT)
				.bounds(markX, markY, GLYPH_BOX, 9)
				.prop(ComponentType.PROP_ALIGN, "center")
				.prop(ComponentType.PROP_SCALE, String.valueOf(MARK_SCALE))
				.prop(ComponentType.PROP_TEXT, markText(sighted))
				.prop(ComponentType.PROP_COLOR, markColor(sighted))
				.prop(ComponentType.PROP_SHADOW, "true")
				.build());

		PandoricalApi.hud().show(player, hud.build());
	}

	/** Drop a player's card and the memory of it. */
	public static void clear(ServerPlayer player) {
		progressWidth.remove(player.getUUID());
		if (showing.remove(player.getUUID()) != null) {
			PandoricalApi.hud().hide(player, OVERLAY_ID);
		}
	}

	/** Forget a player entirely, on disconnect. */
	public static void forget(UUID playerId) {
		showing.remove(playerId);
		progressWidth.remove(playerId);
		BreakProgress.forget(playerId);
	}
}
