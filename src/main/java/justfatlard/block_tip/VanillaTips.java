package justfatlard.block_tip;

import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/**
 * The facts vanilla keeps to itself.
 *
 * <p>Every one of these is a number or a state the game already tracks and never
 * shows, and every one is something a player has to be told or guess wrong at
 * for a while: whether it is dark enough here for something to appear behind
 * you, whether the wheat is done, whether the copper you spent honeycomb on is
 * actually protected.
 *
 * <p>Deliberately not everything that could be shown. A tip for every block is a
 * tip for no block, and the card is one line: these are the ones where not
 * knowing costs you something. Where the game already draws the answer at the
 * size of a screen - a furnace's flame, a chest standing open - what is here is
 * the summary and not the gauge.
 */
public final class VanillaTips {
	private VanillaTips() {}

	/**
	 * Each fact registered on its own, most specific first, all of them below every mod.
	 *
	 * <p>Separately rather than as one chain that picks a winner, because the card can fit more
	 * than one and a chain has already thrown the rest away by the time it gets there: a lit
	 * furnace is also a furnace with something in it, and both are worth the same glance. The
	 * order is the order they are worth saying in, and it decides what falls off the end of a line
	 * that runs out of room.
	 *
	 * <p>Below every mod, because a mod that has gone to the trouble of registering a line knows
	 * something particular about its own block, and that beats the general facts here.
	 */
	private static final int BELOW_THE_MODS = -100;

	private static final BlockTipApi.TipProvider[] FACTS = {
		(level, pos, state, player) -> crop(state),
		(level, pos, state, player) -> spawner(level, pos),
		(level, pos, state, player) -> furnace(level, pos, state),
		(level, pos, state, player) -> container(level, pos, state),
		(level, pos, state, player) -> comparator(level, pos, state),
		(level, pos, state, player) -> redstone(state),
		(level, pos, state, player) -> copper(state),
		(level, pos, state, player) -> noteBlock(state),
		(level, pos, state, player) -> hive(level, pos, state),
		(level, pos, state, player) -> farmland(state),
	};

	public static void register() {
		for (int fact = 0; fact < FACTS.length; fact++) {
			BlockTipApi.describe(BELOW_THE_MODS - fact, FACTS[fact]);
		}
	}

	/**
	 * How far along it is, as a percentage.
	 *
	 * <p>Wheat looks nearly ripe for a long time before it is, and "growing" says only what the
	 * player can already see. A number says whether this is worth coming back for tonight or next
	 * week, and it is a percentage rather than a stage count so beetroot and wheat can be read the
	 * same way despite counting to four and to seven.
	 *
	 * @return -1 for anything that does not grow in stages
	 */
	static int growthPercent(BlockState state) {
		if (!(state.getBlock() instanceof CropBlock crop)) return -1;

		return Math.round(crop.getAge(state) * 100.0F / crop.getMaxAge());
	}

	private static String crop(BlockState state) {
		int grown = growthPercent(state);
		if (grown < 0) return null;

		return grown + "% grown";
	}

	/**
	 * What is going to come out of it.
	 *
	 * <p>The turning miniature inside is technically the answer, but reading a small spinning shape
	 * across a dark room is not the same as being told, and by the time it is close enough to
	 * identify it is close enough to have started.
	 */
	private static String spawner(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) return null;

		// The display entity is the miniature the spawner already turns inside itself, so this names
		// exactly what is being shown rather than reaching into the spawn data behind it.
		Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
		if (display == null) return null;

		return "Spawns " + display.getType().getDescription().getString();
	}

	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;

	/**
	 * Whether it is working, and if not, why not.
	 *
	 * <p>No progress bar: the flame already says it is lit and the fuel gauge is drawn the moment
	 * you open it. What cannot be seen from outside is a furnace that stopped, and the reason it
	 * usually stopped is that it ran dry with the job half done.
	 */
	private static String furnace(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) return null;

		if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
			return "Smelting";
		}

		boolean waiting = !furnace.getItem(SLOT_INPUT).isEmpty();
		boolean fuelled = !furnace.getItem(SLOT_FUEL).isEmpty();

		return waiting && !fuelled ? "Out of fuel" : "Idle";
	}

	/**
	 * Roughly how much room is left.
	 *
	 * <p>Quarters rather than a slot count: the question a chest raises in passing is "can I put
	 * this down here", and that is answered by empty, nearly full, or somewhere in between. The
	 * exact number is the comparator's own fact, and it is beside this one on the card whenever
	 * there is room for both.
	 */
	private static String container(ServerLevel level, BlockPos pos, BlockState state) {
		if (sealedLoot(level, pos)) return null;
		if (!(level.getBlockEntity(pos) instanceof Container container)) return null;

		int size = container.getContainerSize();
		if (size == 0) return null;

		int used = 0;
		for (int slot = 0; slot < size; slot++) {
			if (!container.getItem(slot).isEmpty()) used++;
		}

		if (used == 0) return "Empty";
		if (used == size) return "Full";

		// Clamped off the ends: a single item in a double chest rounds to nothing, and a chest with
		// one slot free rounds to everything, and both of those are lies.
		int quarters = Math.max(1, Math.min(3, Math.round(used * 4.0F / size)));
		return (quarters * 25) + "% full";
	}

	/**
	 * One glyph for redstone power, wherever a number of it appears.
	 *
	 * <p>Signal strength turns up in several places - what a wire carries, what a comparator would
	 * read off a container - and each of them used to name itself in words. A single mark for the
	 * idea means a player learns it once and then recognises the number anywhere it shows up,
	 * without the card spending a word on saying which kind of number it is.
	 */
	static final String POWER = "\u26A1";

	/** The number a comparator would read here, which is invisible until you have already built one. */
	private static String comparator(ServerLevel level, BlockPos pos, BlockState state) {
		if (sealedLoot(level, pos)) return null;
		if (!state.hasAnalogOutputSignal()) return null;

		return POWER + " " + state.getAnalogOutputSignal(level, pos, Direction.UP) + "/15";
	}

	/**
	 * A container still holding an unrolled loot table, which must not be touched.
	 *
	 * <p>Counting slots means calling {@code getItem}, and reading a comparator means the same thing
	 * one level down. On a loot chest either of those makes vanilla unpack the table on the spot -
	 * so merely looking at an unopened dungeon chest rolled its contents, once, for whoever happened
	 * to open it first. That also strips the table the chest is identified by, which is how Loot
	 * Ender knows to give each player their own copy: it found nothing to copy and stood aside.
	 *
	 * <p>Saying nothing is the right answer here anyway. How full a chest nobody has opened is not a
	 * fact the card should be giving away.
	 */
	private static boolean sealedLoot(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof RandomizableContainer loot
			&& loot.getLootTable() != null;
	}

	/** The number redstone is actually carrying, which nothing anywhere displays. */
	private static String redstone(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.POWER)) return null;

		return POWER + " " + state.getValue(BlockStateProperties.POWER) + "/15";
	}

	/**
	 * Waxed copper is identical to unwaxed copper in every way you can see, and
	 * the difference is the entire reason anyone applies honeycomb.
	 */
	private static String copper(BlockState state) {
		if (HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(state.getBlock())) {
			return "Waxed";
		}
		return null;
	}

	/**
	 * Twenty-five clicks starting at F sharp, which is a fact nobody memorises.
	 *
	 * <p>The raw number is what the game stores and it is useless on its own: knowing a note block
	 * is on twelve tells you nothing about whether it is in tune with the one beside it. The name
	 * is the thing anyone tuning a row of them is actually trying to work out.
	 */
	private static final String[] SEMITONES =
		{"F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F"};

	private static String noteBlock(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.NOTE)) return null;

		NoteBlockInstrument instrument = state.getValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT);
		int note = state.getValue(BlockStateProperties.NOTE);

		return instrument.getSerializedName() + ", " + SEMITONES[note % 12] + (3 + note / 12);
	}

	/** How angry opening this is about to make things. */
	private static String hive(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(BlockStateProperties.LEVEL_HONEY)) return null;

		BlockEntity blockEntity = level.getBlockEntity(pos);
		int bees = blockEntity instanceof BeehiveBlockEntity beehive ? beehive.getOccupantCount() : 0;
		int honey = state.getValue(BlockStateProperties.LEVEL_HONEY);

		String honeyPart = honey >= 5 ? "honey full" : "honey " + honey + "/5";
		return bees == 0 ? honeyPart : bees + " bees, " + honeyPart;
	}

	private static String farmland(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.MOISTURE)) return null;

		return state.getValue(BlockStateProperties.MOISTURE) > 0 ? "Watered" : "Dry";
	}

	/**
	 * Whether something can appear on top of this block.
	 *
	 * <p>Since the light rewrite, hostile mobs need block light of exactly zero,
	 * which is a rule nobody can check by eye: a torch two blocks too far leaves a
	 * square that looks lit and spawns creepers all night.
	 *
	 * <p>A corner of the card rather than a line in the queue above. It is true of half the world,
	 * which makes it the thing a player reads most and needs least, and a line can only hold one
	 * answer: as a line it lost to every tip with something particular to say, so a dark chest
	 * could report how full it was and never mention what appears on top of it. In the corner it is
	 * checked at a glance, ignored just as fast, and true at the same time as everything else.
	 */
	static boolean mobsCanSpawnOn(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.isSolidRender()) return false;

		BlockPos above = pos.above();
		if (!level.getBlockState(above).isAir()) return false;

		return level.getBrightness(LightLayer.BLOCK, above) == 0;
	}
}
