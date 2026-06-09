package com.example.villagergunners;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * TEMPORARY EXERCISE MOD.
 *
 * Arms naturally-generated village villagers and iron golems with Just Enough Guns weapons
 * and makes them fight any hostile mob. Event handlers live in {@link GunnerEvents}, which is
 * auto-registered to the Forge event bus via {@code @Mod.EventBusSubscriber}.
 */
@Mod(VillagerGunners.MODID)
public class VillagerGunners {
    public static final String MODID = "gobacktoyourworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillagerGunners() {
        LOGGER.info("[Go Back To Your World] loaded.");
    }
}
