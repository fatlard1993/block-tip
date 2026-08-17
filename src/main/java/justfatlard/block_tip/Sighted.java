package justfatlard.block_tip;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * What a player is looking at, reduced to the two things worth showing: a name
 * and a picture of it.
 *
 * @param itemId the item to draw as the icon, empty when there is nothing
 *        sensible to draw
 * @param nameKey a translation key where there is one, so the client can say it
 *        in whatever language that client is set to rather than whatever
 *        language the server happens to think in, and plain text where there is
 *        not, which is how a named villager gets to be called by their name
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
		// One call covers both: it clips against blocks, then checks whether an
		// entity stood in front of the block it found. Whichever is nearer wins,
		// which is the same rule the player's own eyes are using.
		HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, Sighted::worthNaming, RANGE);

		return switch (hit.getType()) {
			case ENTITY -> ofEntity(((EntityHitResult) hit).getEntity());
			case BLOCK -> ofBlock(player, ((BlockHitResult) hit).getBlockPos());
			default -> NOTHING;
		};
	}

	private static boolean worthNaming(Entity entity) {
		return !entity.isSpectator() && entity.isPickable();
	}

	private static Sighted ofEntity(Entity entity) {
		// The pick item is what middle-click would hand you: a spawn egg for a
		// mob, the boat for a boat. Exactly the picture of the thing.
		ItemStack pick = entity.getPickResult();
		String icon = pick == null || pick.isEmpty() ? "" : idOf(pick.getItem());

		// Display name rather than type name, so a villager who has been given a
		// name is introduced by it.
		return new Sighted(icon, textOf(entity.getDisplayName()));
	}

	private static Sighted ofBlock(ServerPlayer player, net.minecraft.core.BlockPos pos) {
		BlockState state = player.level().getBlockState(pos);
		if (state.isAir()) return NOTHING;

		Block block = state.getBlock();
		return new Sighted(iconFor(block), textOf(block.getName()));
	}

	/**
	 * The item to draw for a block. Some blocks have none at all: fire, portals,
	 * the top half of a door. Those still get a name, just no picture, which is
	 * better than a picture of the wrong thing.
	 */
	private static String iconFor(Block block) {
		Item item = block.asItem();
		return item == Items.AIR ? "" : idOf(item);
	}

	private static String idOf(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id == null ? "" : id.toString();
	}

	/**
	 * The translation key where there is one, rather than the translated words,
	 * so the client says it in its own language. Anything else, including a name
	 * somebody typed, travels as it reads.
	 */
	private static String textOf(Component name) {
		if (name.getContents() instanceof TranslatableContents translatable) {
			return translatable.getKey();
		}
		return name.getString();
	}
}
