package justfatlard.block_tip;

import java.util.ArrayList;
import java.util.List;
import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a player is looking at, reduced to the two things worth showing: a name
 * and a picture of it.
 *
 * @param itemId the item to draw as the icon, empty when there is nothing
 *        sensible to draw
 * @param blockId the block's registry id, empty for creatures and for nothing:
 *        what a player names when they ask never to be told about this again
 * @param nameKey a translation key where there is one, so the client can say it
 *        in whatever language that client is set to rather than whatever
 *        language the server happens to think in, and plain text where there is
 *        not, which is how a named villager gets to be called by their name
 * @param details what the name does not already say, most important first: a
 *        living thing's remaining health, whatever
 *        {@link justfatlard.block_tip.api.BlockTipApi} has to add about this
 *        block or this creature, and empty for the vast majority of blocks about
 *        which there is nothing to add. The card shows as many as its line will
 *        hold, so the order is what survives a card that runs out of room.
 *        Each carries its own picture, so a loaf can only ever be drawn at the
 *        head of the sentence that brought it
 * @param spawnable whether hostile mobs can appear on top of this block, which
 *        is drawn as a mark in the card's corner rather than as words
 * @param underBossBar whether this player has a boss bar on screen at all, which
 *        is where the card would otherwise sit. Not the same question as whether
 *        the bar belongs to what they are looking at: that one decides what the
 *        card says, this one only decides where it sits
 * @param modName which mod this came from, empty for vanilla
 * @param mark the one-glyph verdict on whether this can be acted on now; see
 *        {@link Advice.Mark}
 * @param toolItem the tool to draw beside the mark, or empty when there is
 *        nothing worth showing
 */
public record Sighted(String blockId, String itemId, String nameKey, List<BlockTipApi.Tip> details,
		boolean spawnable, boolean underBossBar, String modName, Advice.Mark mark, String toolItem) {

	/** How far a tip will reach. A little past arm's length, so it answers before you arrive. */
	private static final double RANGE = 6.0;

	/** Nothing in range, or nothing worth naming. */
	public static final Sighted NOTHING =
		new Sighted("", "", "", List.of(), false, false, "", Advice.Mark.NONE, "");

	public boolean isNothing() {
		return this.nameKey.isEmpty();
	}

	public static Sighted inFrontOf(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));

		// OUTLINE, not the collision shape. A flower, a torch, a blade of grass and a carpet all
		// collide with nothing, so a collision clip passes straight through them and names the dirt
		// behind instead of the thing being looked at. Outline is the shape the crosshair itself is
		// drawn around, which makes this agree with what the player can see they are pointing at.
		BlockHitResult blockHit = player.level().clip(new ClipContext(
			eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

		Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

		// Bounded by the block, so an entity only wins when it genuinely stands in front of it.
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			player, eye, stop, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			Sighted::worthNaming, eye.distanceToSqr(stop));

		if (entityHit != null) return ofEntity(player, entityHit.getEntity());
		if (blockHit.getType() == HitResult.Type.BLOCK) return ofBlock(player, blockHit.getBlockPos());
		return NOTHING;
	}

	private static boolean worthNaming(Entity entity) {
		return !entity.isSpectator() && entity.isPickable();
	}

	private static Sighted ofEntity(ServerPlayer player, Entity entity) {
		// The pick item is what middle-click would hand you: a spawn egg for a
		// mob, the boat for a boat. Exactly the picture of the thing.
		ItemStack pick = entity.getPickResult();
		String icon = pick == null || pick.isEmpty() ? standIn(entity) : idOf(pick.getItem());

		// The mod is read off the entity type, not off the pick item: a modded mob whose pick result
		// is a vanilla spawn egg still came from the mod.
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

		// Display name rather than type name, so a villager who has been given a
		// name is introduced by it.
		// Whose bar it is and whether one is up are different questions with different answers, and
		// the card needs both: the first decides whether repeating the health adds anything, the
		// second decides whether sitting at the top of the screen lands on top of something.
		boolean ownBar = BossBars.over(player.level(), entity);
		boolean underBar = ownBar || BossBars.anyShowing(player);

		List<BlockTipApi.Tip> details = detailsOf(player, entity, ownBar);
		String modName = typeId == null ? "" : ModNames.of(typeId.toString());

		// A bar across the top of the screen already names it and draws its health. Where that
		// leaves the card with nothing of its own to say, it says nothing: the whole difference
		// between two things sharing a corner and two things arguing over it is whether the second
		// one turns up when it has something to add.
		if (ownBar && details.isEmpty() && modName.isEmpty()) return NOTHING;

		return new Sighted("", icon, textOf(entity.getDisplayName()), details, false, underBar,
			modName, Advice.Mark.NONE, "");
	}

	/**
	 * The health, and whatever a mod has to add about this particular creature.
	 *
	 * <p>Both, rather than one winning: they answer different questions, and how much fight is left
	 * in something is worth knowing at the same moment as how you are meant to deal with it. Health
	 * leads, being the shorter and the one that changes while you watch.
	 */
	private static List<BlockTipApi.Tip> detailsOf(ServerPlayer player, Entity entity, boolean ownBar) {
		List<BlockTipApi.Tip> details = new ArrayList<>(2);

		// The bar over the top of the screen is a health bar with a number's worth of precision
		// already. Printing the same health under it would be the card's only contribution being
		// the thing that was never missing.
		String health = ownBar ? "" : healthOf(entity);
		if (!health.isEmpty()) details.add(BlockTipApi.Tip.of(health));

		String added = BlockTipApi.detailForEntity(entity, player);
		if (added != null && !added.isBlank()) details.add(BlockTipApi.Tip.of(added));

		return List.copyOf(details);
	}

	/**
	 * A picture for the creatures middle-click will not hand you.
	 *
	 * <p>Only players, and only because a player is the one thing you look at expecting a face.
	 * Everything else without a pick item is a mechanism or a projectile, and a blank square beside
	 * its name is the truth about it.
	 *
	 * <p>A plain head rather than their own skin: the card carries an item id and nothing else, so
	 * this is a picture of "a person" while the name beside it says which one.
	 */
	private static String standIn(Entity entity) {
		return entity instanceof Player ? "minecraft:player_head" : "";
	}

	/**
	 * How much fight is left in it, for anything that can be fought.
	 *
	 * <p>Rounded up, so a mob one blow from death never reads as already dead, and shown against its
	 * maximum because the number alone says nothing: eight is a wounded zombie and a healthy bat.
	 * Boats and item frames have no health and get the blank line every block gets.
	 */
	private static String healthOf(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return "";

		return Mth.ceil(living.getHealth()) + "/" + Mth.ceil(living.getMaxHealth()) + " \u2665";
	}

	private static Sighted ofBlock(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return NOTHING;

		Block block = state.getBlock();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);

		String id = blockId == null ? "" : blockId.toString();
		// Named either way; only what the card would read out of the inside of it is withheld.
		List<BlockTipApi.Tip> tips = BlockTipApi.mayInspect(level, pos, state, player)
			? BlockTipApi.tipsFor(level, pos, state, player, id)
			: List.of();

		// A mod's stand-in wins: it is only ever set for blocks that have no item
		// of their own, where the alternative is an empty square.
		String override = BlockTipApi.iconOverride(id);
		String icon = override != null ? override : iconFor(block);

		Advice advice = Advice.on(player, level, pos, state);

		return new Sighted(id, icon, textOf(block.getName()), tips,
			VanillaTips.mobsCanSpawnOn(level, pos, state), BossBars.anyShowing(player), ModNames.of(id),
			advice.mark(), advice.toolItem());
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
