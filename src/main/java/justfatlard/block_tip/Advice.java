package justfatlard.block_tip;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What you should be holding, and whether what you are holding will do.
 *
 * <p>Two pieces that have to be decided together: a picture of a tool, and a glyph saying what the
 * picture means. A pickaxe on its own is ambiguous - it could be an instruction, a warning, or a
 * suggestion - and the whole difference between them is what it costs to ignore it.
 *
 * @param mark the verdict; see {@link Mark}
 * @param toolItem the tool to draw beside it, or empty when the verdict says it all
 */
record Advice(Mark mark, String toolItem) {

	/**
	 * The one-glyph verdict at the end of the name: can I act on this now.
	 *
	 * <p>Not only about tools. A ripe crop and a swung-at-with-the-right-pickaxe block are the same
	 * answer to the player, and a block wants at most one of these at a time, so they share the
	 * slot: crops need no tool and tools do not ripen.
	 */
	enum Mark { NONE, GOOD, WRONG_TIER, BAD, OPTIONAL }

	static final Advice NOTHING = new Advice(Mark.NONE, "");

	private static final String SHEARS = "minecraft:shears";

	/**
	 * The picture for an enchantment, there being no such item as a silk touch.
	 *
	 * <p>A book is what you would go and get: the enchantment lives on one until somebody puts it on
	 * a tool, and every player who has ever wanted silk touch has stood in front of a table looking
	 * at one.
	 */
	private static final String SILK_TOUCH = "minecraft:enchanted_book";

	/**
	 * Ordered by what ignoring it costs, most expensive first.
	 *
	 * <p>A block that gives nothing without silk touch has more to say than a block that would come
	 * apart faster with a pickaxe, and there is one slot: the thing you cannot undo speaks first.

	 */
	static Advice on(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		int grown = VanillaTips.growthPercent(state);
		if (grown >= 0) return new Advice(grown >= 100 ? Mark.GOOD : Mark.BAD, "");

		ItemStack held = player.getMainHandItem();
		Drops.Keeping keeping = Drops.of(level, pos, state);

		// Shears before silk touch: both keep a leaf block, and one of them does not need a table,
		// a book and thirty levels first.
		if (keeping.byShears() && !held.is(Items.SHEARS) && !Drops.silkInHand(level, held)) {
			return new Advice(Mark.OPTIONAL, SHEARS);
		}

		if (keeping.silkOnly()) {
			return Drops.silkInHand(level, held)
				? new Advice(Mark.GOOD, "")
				: new Advice(Mark.BAD, SILK_TOUCH);
		}

		if (state.requiresCorrectToolForDrops()) {
			if (held.isCorrectToolForDrops(state)) return new Advice(Mark.GOOD, "");

			// A picture of the pickaxe you are already holding is not news, but a picture of the one
			// that would work is: the tier is the whole answer when the family is already right.
			return new Advice(helps(held, state) ? Mark.WRONG_TIER : Mark.BAD, toolItemFor(state));
		}

		// Advice, and only where advice is worth anything: a block that comes apart the instant you
		// touch it is not made quicker by holding the right thing.
		if (state.getDestroySpeed(level, pos) <= 0.0F) return NOTHING;

		String tool = toolItemFor(state);
		if (tool.isEmpty() || helps(held, state)) return NOTHING;

		return new Advice(Mark.OPTIONAL, tool);
	}

	/**
	 * Whether this item is the right family of tool for the block, tier aside.
	 *
	 * <p>A tool's speed comes from the block being in the tool's mineable tag, and the tier only
	 * ever gates the drop. So an axe swung at stone is as slow as a fist, while a wooden pickaxe on
	 * diamond ore is quick and still gets nothing: faster than bare hands is exactly the line
	 * between holding the wrong thing and holding a worn-out version of the right thing.
	 */
	private static boolean helps(ItemStack held, BlockState state) {
		return held.getDestroySpeed(state) > 1.0F;
	}

	/**
	 * The weakest tool that would actually work here, as something to draw.
	 *
	 * <p>A picture of an iron pickaxe carries the whole rule - this kind of tool, at least this
	 * good - in the space a word would take, and carries it in every language. Blocks that name no
	 * tier take the wooden one, which is the honest picture of "any of these will do".
	 */
	private static String toolItemFor(BlockState state) {
		String tool = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "pickaxe"
			: state.is(BlockTags.MINEABLE_WITH_AXE) ? "axe"
			: state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "shovel"
			: state.is(BlockTags.MINEABLE_WITH_HOE) ? "hoe"
			: null;
		if (tool == null) return "";

		String tier = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "diamond"
			: state.is(BlockTags.NEEDS_IRON_TOOL) ? "iron"
			: state.is(BlockTags.NEEDS_STONE_TOOL) ? "stone"
			: "wooden";

		return "minecraft:" + tier + "_" + tool;
	}
}
