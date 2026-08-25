package justfatlard.block_tip;

import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What this creature gives you, and what you have to be holding to get it.
 *
 * <p>Breeding answers "what does it eat"; this answers the other question a farm animal raises.
 * Shears on a sheep, a bucket on a cow, a bowl on a mooshroom - common knowledge to anybody who
 * already knows, and invisible to anybody who does not. Two pictures and an arrow say it without
 * a sentence, and in whatever language the player happens to read.
 *
 * <p><b>A short table rather than a probe.</b> Breeding can ask {@code isFood} about every item
 * there is because vanilla exposes that question; there is no equivalent for "would this
 * interaction do anything", and the only way to find out is to perform it. So these are written
 * down, and written down narrowly: a pair belongs here when the two pictures are the whole
 * explanation, not merely when an interaction exists.
 *
 * <p>Conditions are asked of the individual, not the species. A sheared sheep is not offering
 * wool and a calf has no milk, and a card that says otherwise is worse than one that says nothing.
 */
public final class Interactions {
	private Interactions() {}

	/** The tool to hold and the thing it yields, or null where there is nothing worth saying. */
	public static BlockTipApi.Tip of(Entity entity) {
		return switch (entity) {
			// A mooshroom is a cow as well, so it has to answer first or it never answers at all.
			case MushroomCow mooshroom when !mooshroom.isBaby() -> pair(Items.BOWL,
				mooshroom.getVariant() == MushroomCow.Variant.BROWN
					? Items.SUSPICIOUS_STEW : Items.MUSHROOM_STEW);
			case Cow cow when !cow.isBaby() -> pair(Items.BUCKET, Items.MILK_BUCKET);
			case Goat goat when !goat.isBaby() -> pair(Items.BUCKET, Items.MILK_BUCKET);
			// The colour it would actually give up, not white standing in for all sixteen.
			case Sheep sheep when !sheep.isSheared() && !sheep.isBaby() ->
				new BlockTipApi.Tip("", id(Items.SHEARS),
					"minecraft:" + sheep.getColor().getSerializedName() + "_wool");
			default -> null;
		};
	}

	/**
	 * The line carries no words: the two pictures are the sentence and the arrow between them is
	 * the verb. Anything a translation could add is already in the icons.
	 */
	private static BlockTipApi.Tip pair(Item held, Item yielded) {
		return new BlockTipApi.Tip("", id(held), id(yielded));
	}

	private static String id(Item item) {
		var key = BuiltInRegistries.ITEM.getKey(item);
		return key == null ? "" : key.toString();
	}
}
