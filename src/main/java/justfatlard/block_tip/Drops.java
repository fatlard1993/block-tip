package justfatlard.block_tip;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What breaking it actually gives you.
 *
 * <p>The one question the game answers only after it is too late. Glass, ice, coral and turtle eggs
 * all break into nothing and none of them look any different from the blocks that do not, so the
 * rule is learned by losing something: usually the block you crossed a world to find.
 *
 * <p>Answered by asking the loot table rather than by keeping a list of blocks, because a list is
 * wrong the moment a data pack or a mod adds a block, and being wrong here is worse than being
 * silent - a card that says you will keep something is a card somebody swings on.
 */
final class Drops {
	private Drops() {}

	/**
	 * Answers keyed by block state, because a loot table cannot change between two looks.
	 *
	 * <p>Rolling a table is not free and the look tick asks four times a second for as long as
	 * somebody stands there, but the number of distinct states anybody looks at in a session is
	 * small. It is cleared when data packs reload, which is the only thing that can change the
	 * answer while the server is up.
	 */
	private static final Map<BlockState, Keeping> CACHE = new ConcurrentHashMap<>();

	private static final Logger LOGGER = LoggerFactory.getLogger("block-tip");

	/** One complaint per block, not one per look. */
	private static final Set<Block> COMPLAINED = ConcurrentHashMap.newKeySet();

	/**
	 * What it takes to end up holding the block itself.
	 *
	 * @param byShears the block itself comes back from shears and from nothing else, the way leaves
	 *        and cobweb and grass do
	 * @param silkOnly breaking it any ordinary way leaves you with nothing at all
	 */
	record Keeping(boolean byShears, boolean silkOnly) {
		static final Keeping ORDINARY = new Keeping(false, false);
	}

	/** Data packs decide what drops, so a reload makes every answer here a guess again. */
	static void forget() {
		CACHE.clear();
		COMPLAINED.clear();
	}

	static Keeping of(ServerLevel level, BlockPos pos, BlockState state) {
		Keeping known = CACHE.get(state);
		if (known != null) return known;

		Keeping answer = probe(level, pos, state);

		// A table that would not roll is not an answer, so it is not remembered as one. Caching the
		// failure was quieter and worse: one bad roll and the card stopped warning about that block
		// for the rest of the session, which is the exact thing this class exists to prevent.
		if (answer == null) return Keeping.ORDINARY;

		CACHE.put(state, answer);
		return answer;
	}

	/**
	 * Asked as three questions of the loot table, and never as "does silk touch keep this".
	 *
	 * <p>That question is true of stone, of every ore, of grass - most of the world - and answering
	 * it would put a book on half the blocks in the game. What is worth saying is the case where
	 * the alternative is empty hands, which is why the plain roll has to come up with nothing
	 * before silk touch is worth mentioning at all.
	 *
	 * <p>Asking whether shears keep it, rather than whether shears are quick on it, is also what
	 * makes this survive a random table. Leaves drop a sapling now and then, so "did the plain roll
	 * come up empty" is a coin toss on them and "did the block itself come back" is not.
	 *
	 * <p>Probed with a netherite pickaxe so that tier never enters into it. The gate that stops a
	 * wooden pickaxe getting diamonds sits outside the loot table, so what comes back here is what
	 * the table says and nothing else.
	 */
	/** @return null where the loot table could not be asked, which is not the same as an answer */
	private static Keeping probe(ServerLevel level, BlockPos pos, BlockState state) {
		Item self = state.getBlock().asItem();
		if (self == Items.AIR) return Keeping.ORDINARY;

		try {
			ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
			List<ItemStack> plain = Block.getDrops(state, level, pos, null, null, pickaxe);

			// Something that comes back from an ordinary swing needs no advice about how to keep it.
			if (plain.stream().anyMatch(stack -> stack.is(self))) return Keeping.ORDINARY;

			ItemStack shears = new ItemStack(Items.SHEARS);
			if (holds(Block.getDrops(state, level, pos, null, null, shears), self)) {
				return new Keeping(true, false);
			}

			if (!plain.isEmpty()) return Keeping.ORDINARY;

			ItemStack silk = pickaxe.copy();
			silk.enchant(silkTouch(level), 1);

			return new Keeping(false, holds(Block.getDrops(state, level, pos, null, null, silk), self));
		} catch (Exception error) {
			// A loot table that will not roll for a question nobody asked it is not worth a crash,
			// and a card that says nothing is better than one that promises a drop.
			if (COMPLAINED.add(state.getBlock())) {
				LOGGER.warn("[block-tip] could not read what {} drops; it gets no shears or silk mark",
					state.getBlock(), error);
			}
			return null;
		}
	}

	private static boolean holds(List<ItemStack> drops, Item self) {
		return drops.stream().anyMatch(stack -> stack.is(self));
	}

	/** Whether what they are holding is the thing that would keep it. */
	static boolean silkInHand(ServerLevel level, ItemStack held) {
		return EnchantmentHelper.getItemEnchantmentLevel(silkTouch(level), held) > 0;
	}

	private static Holder<Enchantment> silkTouch(ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
	}
}
