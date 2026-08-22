package com.example.villagefightsback;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The Limbo Shield: a shield that hits back.
 *
 * <p>Ten times a vanilla shield's durability ({@value #USES} against 336), and anything that lands a melee
 * blow on it is thrown off exactly the way a player is thrown off the Village Brain Node -- same launch, same
 * sound, same puff of cloud. That is deliberate reuse rather than a coincidence: the village already has one
 * gesture for "you do not get to touch this", and the shield is that gesture in the player's hands. The bash
 * itself lives in {@link ShieldBashEvents}, which is where the block event arrives.
 *
 * <p>It is a real {@link ShieldItem}, so every piece of vanilla shield behaviour comes free: the use
 * animation, the damage reduction, the axe disable, the banner overlay hooks, and -- the part that matters
 * for a mob wielding one -- the Guard Villagers AI, which raises whatever shield it finds in an offhand
 * without knowing or caring what mod it came from.
 *
 * <p>Repaired with Limbo Membrane like the rest of the Limbo family, and permanently foiled like the rest of
 * it too.
 */
public class LimboShieldItem extends ShieldItem {
    /** Ten vanilla shields. A Limbo tool is meant to outlast the fight it was brought to. */
    public static final int USES = 3360;

    public LimboShieldItem(Properties properties) {
        super(properties.durability(USES));
    }

    /**
     * Limbo Membrane, as every other Limbo piece takes.
     *
     * <p>{@code ShieldItem.isValidRepairItem} is hardcoded to the planks tag, so without this override a
     * Limbo Shield would be repaired with oak, which is not the joke anybody wants.
     */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return Ingredient.of(ModItems.LIMBO_MEMBRANE.get()).test(repair);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Rendered with vanilla's shield model, not as a sprite.
     *
     * <p>That is what gives it the shield behaviour the player already knows -- edge-on at the arm while
     * carried, flat in front when raised -- because it is literally vanilla's model under vanilla's
     * transforms. See {@link com.example.villagefightsback.client.ShieldItemRenderer}.
     */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.example.villagefightsback.client.ShieldItemRenderer.instance();
            }
        });
    }

    /**
     * Not the vanilla "shield" name lookup.
     *
     * <p>{@code ShieldItem.getDescriptionId} inspects the banner colour on the stack and answers
     * {@code item.minecraft.shield.<colour>} when it finds one, which for our shield would resolve to a
     * vanilla lang key and print "Shield". Ours is not a banner-carrying shield, so the plain registry name
     * is the right answer and the one the tooltip and creative tab need.
     */
    @Override
    public String getDescriptionId(ItemStack stack) {
        return this.getDescriptionId();
    }
}
