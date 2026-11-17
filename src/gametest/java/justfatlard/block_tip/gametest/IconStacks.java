package justfatlard.block_tip.gametest;

import justfatlard.block_tip.Sighted;
import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.pandorical.api.ItemIcons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * A mod's icon provider draws one block as a particular stack: the card carries the stack's item
 * and its components, packed for Pandorical to draw, and a block it does not answer for keeps its
 * own plain item.
 */
public final class IconStacks implements FabricClientGameTest {
	static {
		BlockTipApi.icon((level, pos, state, player) -> {
			if (!state.is(Blocks.GOLD_BLOCK)) return null;
			ItemStack stack = new ItemStack(Items.GOLDEN_APPLE);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal("Pictured"));
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			return stack;
		});
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			BlockPos below = server.computeOnServer(s -> connection.getServerPlayer().blockPosition().below());
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 90");

			server.runOnServer(s -> s.overworld().setBlockAndUpdate(below, Blocks.GOLD_BLOCK.defaultBlockState()));
			context.waitTicks(10);
			Sighted gold = server.computeOnServer(s -> Sighted.inFrontOf(connection.getServerPlayer()));
			check(gold.itemId().equals("minecraft:golden_apple"), "the provider's stack was not the icon: " + gold.itemId());
			check(!gold.itemComponents().isEmpty(), "the provider's stack carried no components");
			ItemStack read = server.computeOnServer(s -> {
				ItemStack back = new ItemStack(Items.GOLDEN_APPLE);
				ItemIcons.read(gold.itemComponents(), s.registryAccess()).ifPresent(back::applyComponents);
				return back;
			});
			check(read.getHoverName().getString().equals("Pictured") && Boolean.TRUE.equals(read.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"the icon's components did not read back as the stack: " + read.getComponentsPatch());
			context.takeScreenshot("block-tip-icon-stack");

			server.runOnServer(s -> s.overworld().setBlockAndUpdate(below, Blocks.IRON_BLOCK.defaultBlockState()));
			context.waitTicks(10);
			Sighted iron = server.computeOnServer(s -> Sighted.inFrontOf(connection.getServerPlayer()));
			check(iron.itemId().equals("minecraft:iron_block") && iron.itemComponents().isEmpty(),
				"a block nobody answers for was not its own plain item: " + iron.itemId() + " " + iron.itemComponents());
			System.out.println("[block-tip-test] icon stacks passed");
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
