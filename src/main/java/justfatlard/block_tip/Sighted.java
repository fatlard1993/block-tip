package justfatlard.block_tip;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * What a player is looking at, reduced to the two things worth showing: a name
 * and a picture of it.
 *
 * @param itemId the item to draw as the icon, empty when the block has none
 * @param nameKey the block's translation key, so the client can say it in
 *        whatever language that client is set to rather than whatever language
 *        the server happens to think in
 */
public record Sighted(String itemId, String nameKey) {

	/** How far a tip will reach. A little past arm's length, so it answers before you arrive. */
	private static final double RANGE = 6.0;

	/** Nothing in range, or nothing worth naming. */
	public static final Sighted NOTHING = new Sighted("", "");

	public boolean isNothing() {
		return this.nameKey.isEmpty();
	}

	public static Sighted inFrontOf(ServerPlayer player) {
		HitResult hit = player.pick(RANGE, 0.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) return NOTHING;

		BlockState state = player.level().getBlockState(((BlockHitResult) hit).getBlockPos());
		if (state.isAir()) return NOTHING;

		Block block = state.getBlock();
		return new Sighted(iconFor(block), nameKeyOf(block));
	}

	/**
	 * The item to draw for a block. Some blocks have none at all: fire, portals,
	 * the top half of a door. Those still get a name, just no picture, which is
	 * better than a picture of the wrong thing.
	 */
	private static String iconFor(Block block) {
		Item item = block.asItem();
		if (item == Items.AIR) return "";

		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id == null ? "" : id.toString();
	}

	/**
	 * The translation key rather than the translated words. Read off the name
	 * component instead of calling getDescriptionId, because a few blocks build
	 * their own name and that is the one worth showing.
	 */
	private static String nameKeyOf(Block block) {
		Component name = block.getName();
		if (name.getContents() instanceof TranslatableContents translatable) {
			return translatable.getKey();
		}
		return name.getString();
	}
}
