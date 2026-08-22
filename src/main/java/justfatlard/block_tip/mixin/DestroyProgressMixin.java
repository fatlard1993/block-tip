package justfatlard.block_tip.mixin;

import justfatlard.block_tip.BreakProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reads how far through a block the player is, from the one place that knows.
 *
 * <p>The server keeps no running total: it recomputes the fraction every tick while somebody is
 * mining and throws it away again, keeping only the ten-step crack stage it broadcasts. Ten steps
 * is too coarse to draw a bar with, and re-deriving the fraction here would mean re-deriving when
 * mining started and stopped as well - two more things to get wrong, to answer a question vanilla
 * has already answered.
 *
 * <p>So this takes the number on its way past. No stop signal is needed: the value is stamped with
 * the tick it was taken on, and anything not refreshed within a tick or two is no longer mining.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class DestroyProgressMixin {

	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "incrementDestroyProgress", at = @At("RETURN"), require = 1)
	private void blockTip$noteProgress(BlockState state, BlockPos pos, int startTick,
			CallbackInfoReturnable<Float> callback) {
		BreakProgress.note(this.player, pos, callback.getReturnValueF());
	}
}
