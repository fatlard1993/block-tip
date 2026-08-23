package justfatlard.block_tip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What this animal wants to be fed.
 *
 * <p>Breeding is the one thing about a farm animal a player has to look up, and the answer is
 * different for almost every mob: wheat for a cow, seeds for a chicken, a carrot for a pig, cod
 * for a cat. The card is already pointed at the animal, so it is the right place to answer.
 *
 * <p>Vanilla exposes the question but not the answer. {@code Animal.isFood} will say yes or no
 * about a stack you hand it, and there is no list to read the other way round, so the only honest
 * way to find the foods is to ask about every item there is. That is a walk of the whole item
 * registry, which is why it happens once per species and is then remembered: the answer belongs
 * to the type, not to the individual, and a field of forty sheep asks the question once.
 */
public final class Breeding {
	private Breeding() {}

	private static final Map<EntityType<?>, List<Item>> FOODS = new ConcurrentHashMap<>();

	/**
	 * The picture and the word for what this one eats, or null when it is not an animal or
	 * nothing feeds it.
	 *
	 * <p>One food, not the whole list: the card has room for a single picture, and any one of
	 * them works. The translation key rather than the words, because the client resolves it and
	 * these are vanilla item keys that every client already has.
	 */
	public static BlockTipApi.Tip of(Entity entity) {
		if (!(entity instanceof Animal animal)) return null;

		List<Item> foods = FOODS.computeIfAbsent(animal.getType(), type -> probe(animal));
		if (foods.isEmpty()) return null;

		Item food = foods.getFirst();
		Identifier id = BuiltInRegistries.ITEM.getKey(food);
		if (id == null) return null;

		return new BlockTipApi.Tip(food.getDescriptionId(), id.toString());
	}

	private static List<Item> probe(Animal animal) {
		List<Item> found = new ArrayList<>();

		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) continue;

			try {
				if (animal.isFood(new ItemStack(item))) found.add(item);
			} catch (Throwable ignored) {
				// isFood is a mod's code as often as vanilla's, and a species whose answer throws
				// should cost its own card a line, not the tick that drew it.
			}
		}
		return List.copyOf(found);
	}
}
