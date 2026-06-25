package com.example.villagefightsback.client;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Derives a representative RGB colour for a banished entity's gravel, by its type id, computed once and cached:
 * <ol>
 *   <li>its SPAWN-EGG primary colour, if the type has a spawn egg (most vanilla + modded mobs, and ours);</li>
 *   <li>else the average colour of its ENTITY TEXTURE -- which covers the no-egg mobs (the wither storm, some
 *       bosses) since every renderer has a texture even without a spawn egg;</li>
 *   <li>else a neutral Limbo purple-grey.</li>
 * </ol>
 * Best-effort and fully client-side: a type that won't instantiate or whose texture can't be read falls back to
 * neutral. Used by the gravel's block/item tint handlers.
 */
public final class LimboGravelPalette {
    private LimboGravelPalette() {}

    private static final int NEUTRAL = 0x6A5A7A; // muted limbo purple-grey
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();

    public static int colorFor(String typeId) {
        if (typeId == null || typeId.isEmpty()) return NEUTRAL;
        Integer cached = CACHE.get(typeId);
        if (cached != null) return cached;
        int v = compute(typeId);
        // Don't cache a NEUTRAL that's only neutral because the client level wasn't ready for texture sampling --
        // retry once it is. (A genuine result, including a real neutral with the level present, is cached.)
        if (!(v == NEUTRAL && Minecraft.getInstance().level == null)) CACHE.put(typeId, v);
        return v;
    }

    private static int compute(String typeId) {
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(typeId));
            if (type == null) return NEUTRAL;
            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg != null) return egg.getColor(0) & 0xFFFFFF;     // spawn-egg primary colour
            Integer sampled = sampleTexture(type);
            if (sampled != null) return sampled;
        } catch (Throwable ignored) { /* fall through to neutral */ }
        return NEUTRAL;
    }

    /** Average colour of the entity's render texture (no-egg mobs like the wither storm / bosses). */
    private static Integer sampleTexture(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity dummy = null;
        try {
            dummy = type.create(mc.level);
            if (!(dummy instanceof LivingEntity living)) return null;
            EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(living);
            if (!(renderer instanceof LivingEntityRenderer)) return null;
            @SuppressWarnings({"unchecked", "rawtypes"})
            ResourceLocation tex = ((LivingEntityRenderer) renderer).getTextureLocation(living);
            if (tex == null) return null;
            Optional<Resource> res = mc.getResourceManager().getResource(tex);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                return averageColor(img);
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (dummy != null) dummy.discard();
        }
    }

    private static Integer averageColor(NativeImage img) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int abgr = img.getPixelRGBA(x, y);          // NativeImage packs as 0xAABBGGRR
                if (((abgr >> 24) & 0xFF) < 128) continue;  // skip transparent pixels
                r += abgr & 0xFF;
                g += (abgr >> 8) & 0xFF;
                b += (abgr >> 16) & 0xFF;
                n++;
            }
        }
        if (n == 0) return null;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }
}
