package justfatlard.block_tip;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import justfatlard.block_tip.TipPreferences.Mode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * {@code /blocktip} on its own flips it, {@code /blocktip on}, {@code off} and
 * {@code sneak} say which way.
 *
 * <p>Bare toggle first because that is what someone types when the tips are in
 * the way and they want them gone now. The explicit forms exist because "type
 * slash blocktip on" should not be able to end with it off, which is what a bare
 * toggle does to anyone who cannot see the current state.
 *
 * <p>The bare toggle deliberately only swings between on and off. Three states on
 * one keystroke means never knowing which one is next, and sneak mode is a thing
 * you decide you want rather than a thing you cycle past.
 *
 * <p>{@code hide} and {@code show} keep a list per player of blocks not worth
 * naming to them any more. Both work on whatever is in front of you when given
 * nothing to work on, which is the form anybody actually uses: the block that
 * just told you it was stone for the hundredth time is already on your screen,
 * and typing its id out is a worse way of pointing at it than pointing at it.
 */
public final class TipCommand {
	private TipCommand() {}

	/**
	 * How many blocks one command can name.
	 *
	 * <p>Brigadier has no word for "and as many more as you like", so the argument is written out
	 * that many times. Five is enough to clear a biome's worth of scenery in one line and few
	 * enough that the line is still readable when it is wrong.
	 */
	private static final int MOST_AT_ONCE = 5;

	public static void register() {
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> build(dispatcher, registryAccess));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext blocks) {
		dispatcher.register(Commands.literal("blocktip")
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				return apply(player, TipPreferences.toggle(player.level(), player.getUUID()));
			})
			.then(Commands.literal("on").executes(context -> choose(context.getSource(), Mode.ALWAYS)))
			.then(Commands.literal("off").executes(context -> choose(context.getSource(), Mode.OFF)))
			.then(Commands.literal("sneak").executes(context -> choose(context.getSource(), Mode.SNEAKING)))
			.then(Commands.literal("hide")
				.executes(context -> inView(context.getSource(), true))
				.then(named(blocks, true, 1)))
			.then(Commands.literal("show")
				.executes(context -> inView(context.getSource(), false))
				.then(Commands.literal("all").executes(context -> showAll(context.getSource())))
				.then(named(blocks, false, 1)))
			.then(Commands.literal("hidden").executes(context -> list(context.getSource())))
			.then(Commands.literal("marks").executes(context -> marks(context.getSource()))));
	}

	/** The same argument, nested in itself, so one command can name several blocks. */
	private static ArgumentBuilder<CommandSourceStack, ?> named(CommandBuildContext blocks, boolean hide,
			int depth) {
		var argument = Commands.argument(slot(depth), BlockStateArgument.block(blocks))
			.executes(context -> change(context, hide, depth));

		if (depth < MOST_AT_ONCE) argument.then(named(blocks, hide, depth + 1));
		return argument;
	}

	private static String slot(int depth) {
		return "block" + depth;
	}

	/**
	 * Hide or show whatever they are pointing at.
	 *
	 * <p>The look is done again here rather than read off the card, because the card is exactly
	 * what is missing in the case that matters: a block already hidden shows nothing, and getting
	 * it back has to work from the same place putting it away did.
	 */
	private static int inView(CommandSourceStack source, boolean hide) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		String blockId = Sighted.inFrontOf(player).blockId();

		if (blockId.isEmpty()) {
			return said(player, hide ? "Look at a block to hide it" : "Look at a block to show it again");
		}

		boolean moved = hide
			? TipPreferences.hide(player.level(), player.getUUID(), blockId)
			: TipPreferences.show(player.level(), player.getUUID(), blockId);

		return report(player, hide, moved ? List.of(blockId) : List.of());
	}

	private static int change(CommandContext<CommandSourceStack> context, boolean hide, int count)
			throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		List<String> changed = new ArrayList<>();

		for (int depth = 1; depth <= count; depth++) {
			Block block = BlockStateArgument.getBlock(context, slot(depth)).getState().getBlock();
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (id == null) continue;

			boolean moved = hide
				? TipPreferences.hide(player.level(), player.getUUID(), id.toString())
				: TipPreferences.show(player.level(), player.getUUID(), id.toString());

			if (moved) changed.add(id.toString());
		}
		return report(player, hide, changed);
	}

	private static int report(ServerPlayer player, boolean hide, List<String> changed) {
		// A hidden block has to leave the screen now. The card sitting there naming the thing you
		// just asked never to be named again is the whole complaint, repeated.
		if (hide) TipHud.clear(player);

		if (changed.isEmpty()) return said(player, hide ? "Already hidden" : "That was not hidden");

		String what = changed.size() == 1 ? changed.getFirst() : changed.size() + " blocks";
		return said(player, (hide ? "Hiding " : "Showing ") + what);
	}

	private static int showAll(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int forgotten = TipPreferences.showAll(player.level(), player.getUUID());

		return said(player, forgotten == 0 ? "Nothing was hidden" : "Showing " + forgotten + " blocks again");
	}

	private static int list(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Set<String> hidden = TipPreferences.hiddenBy(player.level(), player.getUUID());

		return said(player, hidden.isEmpty() ? "Nothing hidden" : "Hidden: " + String.join(", ", hidden));
	}

	/**
	 * What the glyphs on the card mean.
	 *
	 * <p>The card draws six of them and the game explains none: they are learned by guessing, or by
	 * reading a README nobody on a family server has opened. A command is a poor teacher, but it is
	 * reachable from inside the game and it is one tab-completion away from the command they
	 * already know.
	 */
	private static int marks(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();

		for (String line : List.of(
			"\u2620  mobs can appear on this block",
			"\u2714  what you are holding will do",
			"+  right tool, too soft - it will break but drop nothing",
			"\u2718  wrong tool, or a crop that is not ready",
			"*  optional: the tool shown is quicker, or keeps the block itself",
			"\u2665  how much fight is left in it")) {
			said(player, line);
		}
		return 1;
	}

	private static int said(ServerPlayer player, String words) {
		player.sendSystemMessage(Component.literal(words).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int choose(CommandSourceStack source, Mode mode) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return apply(player, TipPreferences.set(player.level(), player.getUUID(), mode));
	}

	private static int apply(ServerPlayer player, Mode mode) {
		// Anything other than always-on has to take the card away immediately. Waiting for the next
		// tick to notice would leave the last thing they looked at sitting on screen, which is
		// exactly the complaint that made them type the command.
		if (mode != Mode.ALWAYS) TipHud.clear(player);

		return said(player, switch (mode) {
			case ALWAYS -> "Block tips on";
			case SNEAKING -> "Block tips while sneaking";
			case OFF -> "Block tips off";
		});
	}
}
