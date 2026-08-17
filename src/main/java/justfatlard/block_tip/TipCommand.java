package justfatlard.block_tip;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /blocktip} on its own flips it, {@code /blocktip on} and
 * {@code /blocktip off} say which way.
 *
 * <p>Bare toggle first because that is what someone types when the tips are in
 * the way and they want them gone now. The explicit forms exist because "type
 * slash blocktip on" should not be able to end with it off, which is what a bare
 * toggle does to anyone who cannot see the current state.
 */
public final class TipCommand {
	private TipCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("blocktip")
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				return apply(player, TipPreferences.toggle(player.level(), player.getUUID()));
			})
			.then(Commands.literal("on").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				return apply(player, TipPreferences.set(player.level(), player.getUUID(), true));
			}))
			.then(Commands.literal("off").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				return apply(player, TipPreferences.set(player.level(), player.getUUID(), false));
			})));
	}

	private static int apply(ServerPlayer player, boolean on) {
		// Off has to take the card away immediately. Waiting for the next tick to
		// notice would leave the last thing they looked at sitting on screen, which
		// is exactly the complaint that made them type the command.
		if (!on) TipHud.clear(player);

		player.sendSystemMessage(Component.literal(on ? "Block tips on" : "Block tips off")
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}
}
