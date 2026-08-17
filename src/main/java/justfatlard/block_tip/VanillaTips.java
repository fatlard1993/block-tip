package justfatlard.block_tip;

import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * knowing costs you something.
 */
public final class VanillaTips {
	private VanillaTips() {}

	public static void register() {
		// Last of all. These are true of half the world, and anything a mod has
		// gone to the trouble of registering is more specific than "it is dark".
		BlockTipApi.describe(-100, VanillaTips::describe);
	}

	private static String describe(ServerLevel level, BlockPos pos, BlockState state, net.minecraft.server.level.ServerPlayer player) {
		String crop = crop(state);
		if (crop != null) return crop;

		String redstone = redstone(state);
		if (redstone != null) return redstone;

		String copper = copper(state);
		if (copper != null) return copper;

		String note = noteBlock(state);
		if (note != null) return note;

		String hive = hive(level, pos, state);
		if (hive != null) return hive;

		String farm = farmland(state);
		if (farm != null) return farm;

		// Last, because it is true of most of the world and would otherwise drown
		// out the specific things above.
		return spawnable(level, pos, state);
	}

	/** Wheat looks nearly ripe for a long time before it is. */
	private static String crop(BlockState state) {
		if (!(state.getBlock() instanceof CropBlock cropBlock)) return null;

		return cropBlock.isMaxAge(state) ? "Ready to harvest" : "Still growing";
	}

	/** The number redstone is actually carrying, which nothing anywhere displays. */
	private static String redstone(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.POWER)) return null;

		int power = state.getValue(BlockStateProperties.POWER);
		return power == 0 ? "Carrying nothing" : "Carrying " + power + " of 15";
	}

	/**
	 * Waxed copper is identical to unwaxed copper in every way you can see, and
	 * the difference is the entire reason anyone applies honeycomb.
	 */
	private static String copper(BlockState state) {
		if (HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(state.getBlock())) {
			return "Waxed - it will not weather";
		}
		return null;
	}

	/** The instrument is the block underneath, which is the last place anyone looks. */
	private static String noteBlock(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.NOTE)) return null;

		NoteBlockInstrument instrument = state.getValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT);
		int note = state.getValue(BlockStateProperties.NOTE);
		return instrument.getSerializedName() + ", note " + note + " of 24";
	}

	/** How angry opening this is about to make things. */
	private static String hive(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(BlockStateProperties.LEVEL_HONEY)) return null;

		BlockEntity blockEntity = level.getBlockEntity(pos);
		int bees = blockEntity instanceof BeehiveBlockEntity beehive ? beehive.getOccupantCount() : 0;
		int honey = state.getValue(BlockStateProperties.LEVEL_HONEY);

		String honeyPart = honey >= 5 ? "full of honey" : "honey " + honey + " of 5";
		return bees == 0 ? honeyPart : bees + " inside, " + honeyPart;
	}

	private static String farmland(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.MOISTURE)) return null;

		return state.getValue(BlockStateProperties.MOISTURE) > 0 ? "Watered" : "Dry - needs water within four blocks";
	}

	/**
	 * Whether something can appear on top of this block.
	 *
	 * <p>Since the light rewrite, hostile mobs need block light of exactly zero,
	 * which is a rule nobody can check by eye: a torch two blocks too far leaves a
	 * square that looks lit and spawns creepers all night.
	 */
	private static String spawnable(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.isSolidRender()) return null;

		BlockPos above = pos.above();
		if (!level.getBlockState(above).isAir()) return null;

		int blockLight = level.getBrightness(LightLayer.BLOCK, above);
		return blockLight == 0 ? "Dark enough for mobs to appear here" : null;
	}
}
