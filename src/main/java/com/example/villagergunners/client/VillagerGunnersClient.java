package com.example.villagergunners.client;

import com.example.villagergunners.GunUtil;
import com.example.villagergunners.VillagerGunners;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: attaches the decorative gun render layer to villager and iron golem renderers. */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VillagerGunnersClient {
    private VillagerGunnersClient() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        // Villager: mirror vanilla CrossedArmsItemLayer's transform (translate 0,0.4,-0.4 + flip)
        // so the revolver sits in the folded arms. No bone needed.
        LivingEntityRenderer<Villager, VillagerModel<Villager>> villager = event.getRenderer(EntityType.VILLAGER);
        if (villager != null) {
            villager.addLayer(new HeldGunLayer<>(villager, GunUtil.REVOLVER, null,
                    0.0F, 0.4F, -0.4F, 180.0F, 0.0F, 0.0F, 1.0F, ItemDisplayContext.GROUND));
        }

        // Iron golem: no vanilla item layer exists, so anchor to the right arm. Offsets are a starting
        // point and likely need tuning from a screenshot.
        LivingEntityRenderer<IronGolem, IronGolemModel<IronGolem>> golem = event.getRenderer(EntityType.IRON_GOLEM);
        if (golem != null) {
            golem.addLayer(new HeldGunLayer<>(golem, GunUtil.ROCKET_LAUNCHER, "right_arm",
                    0.0F, 1.4F, 0.2F, 0.0F, 0.0F, 0.0F, 1.0F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        }
    }
}
