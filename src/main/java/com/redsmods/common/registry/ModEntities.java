package com.redsmods.common.registry;

import com.redsmods.RedsBosses;
import com.redsmods.common.entity.Radiance;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RedsBosses.MODID);

    public static final Supplier<EntityType<Radiance>> Radiance =
            ENTITY_TYPES.register("radiance", () ->
                    EntityType.Builder.of(Radiance::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.8f) // width, height
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("radiance"));
}
