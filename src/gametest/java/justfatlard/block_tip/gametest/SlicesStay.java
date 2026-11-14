package justfatlard.block_tip.gametest;

import justfatlard.block_tip.Slices;
import justfatlard.block_tip.api.BlockTipApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A slice another mod gives stays up while it has something to say, whatever the player looks at;
 * goes when it says nothing; and is not shown to a player who has tips off.
 */
public final class SlicesStay implements FabricClientGameTest {
	private static final AtomicReference<String> SAYING = new AtomicReference<>();

	static {
		BlockTipApi.slice(player -> SAYING.get());
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			UUID player = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
			// Looking at the sky: no card, so the slice stands on its own.
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 -90");

			SAYING.set("8.0 blocks out, level with the post");
			context.waitTicks(10);
			check(Slices.showing(player).equals(List.of("8.0 blocks out, level with the post")), "the slice is not up: " + Slices.showing(player));

			SAYING.set("9.5 blocks out, 1.0 up");
			context.waitTicks(10);
			check(Slices.showing(player).equals(List.of("9.5 blocks out, 1.0 up")), "the slice did not change with what it says: " + Slices.showing(player));

			server.runCommand("execute as @a run blocktip off");
			context.waitTicks(10);
			check(Slices.showing(player).isEmpty(), "the slice stayed up with tips off");
			server.runCommand("execute as @a run blocktip on");
			context.waitTicks(10);
			check(!Slices.showing(player).isEmpty(), "the slice did not come back with tips on");

			SAYING.set(null);
			context.waitTicks(10);
			check(Slices.showing(player).isEmpty(), "the slice stayed up with nothing to say");
			System.out.println("[block-tip-test] slices passed");
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
