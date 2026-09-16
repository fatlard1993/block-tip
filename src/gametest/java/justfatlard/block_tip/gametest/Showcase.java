package justfatlard.block_tip.gametest;

import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;

/**
 * The pictures for the readme and the mod page: the card a crop puts up, and the one a villager
 * does. The HUD stays on, because the card is part of it.
 *
 * <p>Run it under xvfb-run; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			// Every recipe unlocked up front: otherwise the wheat that goes in hand for the second
			// picture pops a "New Recipe Unlocked" toast straight over the card.
			server.runCommand("recipe give @a *");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();

			// A field of wheat part grown, which is the card's own example: the growth is a green
			// edge along the bottom of it rather than a line of text.
			server.runCommand("fill %d %d %d %d %d %d minecraft:farmland[moisture=7]"
				.formatted(x - 3, y - 1, z - 6, x + 3, y - 1, z - 2));
			server.runCommand("fill %d %d %d %d %d %d minecraft:wheat[age=5]"
				.formatted(x - 3, y, z - 6, x + 3, y, z - 2));
			server.runCommand("setblock %d %d %d minecraft:water".formatted(x, y - 1, z - 4));

			// Nothing in hand, and long enough for the game-mode line to fade out of the chat:
			// the card is the subject and everything else in the HUD is clutter around it.
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			server.runCommand("item replace entity @a weapon.offhand with minecraft:air");
			// The chat panel keeps its own corner and 26.3 exposes no way to wipe it from here,
			// so the shot waits for the lines in it to fade instead.
			look(server, x + 0.5, y + 1, z + 1.5, x + 0.5, y + 0.4, z - 3.0);
			context.waitTicks(220);
			shoot(context, "crop-card");

			// And a creature, which is named by its own name and says what it wants.
			server.runCommand("summon minecraft:cow %d %d %d {NoAI:1b,CustomName:'\"Buttercup\"'}"
				.formatted(x, y, z - 4));
			// Put the wheat in hand rather than give it: a give pops a "New Recipe Unlocked" toast
			// straight over the card the picture is of.
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:wheat");
			look(server, x + 0.5, y, z + 0.5, x + 0.5, y + 0.9, z - 4.0);
			context.waitTicks(60);
			shoot(context, "creature-card");
		}
	}

	/**
	 * Stand the camera at one place and point it at another. The camera's y is the feet, so it
	 * looks from 1.62 above where it stands.
	 */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
