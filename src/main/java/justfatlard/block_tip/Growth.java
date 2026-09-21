package justfatlard.block_tip;

import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How far along the thing in front of the player is, asked in the one place for the two that want
 * it: the green fill along the bottom of the card, and the mark at the end of the name that says
 * whether it is worth breaking yet. They are the same answer, and the day they are two answers is
 * the day one of them is wrong.
 *
 * <p>A mod is asked first, so a plant that keeps its progress somewhere other than an age property
 * can be read at all, and so a mod that has put a clock on a block the game already ages can say
 * the truer thing about it.
 */
final class Growth {

	private Growth() {}

	/** How grown it is as a percentage, or -1 for anything that does not grow in stages. */
	static int percent(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		float told = BlockTipApi.grownFraction(level, pos, state, player);
		if (told >= 0.0F) return Math.round(told * 100.0F);
		return VanillaTips.growthPercent(state);
	}
}
