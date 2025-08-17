package com.redsmods.common.client.renderer;

import com.redsmods.RedsBosses;
import com.redsmods.common.client.model.RadianceModel;
import com.redsmods.common.entity.Radiance;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RadianceRenderer extends MobRenderer<Radiance, RadianceModel<Radiance>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RedsBosses.MODID, "textures/entity/stone.png");
    private static final ResourceLocation TEXTURE2 =
            ResourceLocation.fromNamespaceAndPath(RedsBosses.MODID, "textures/entity/phase_2.png");

    public RadianceRenderer(EntityRendererProvider.Context context) {
        super(context, new RadianceModel<>(context.bakeLayer(RadianceModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Radiance entity) {
        return switch (entity.state) {
            case DEACTIVATED_IDOL -> TEXTURE;
            case ARENA_BUILDING -> TEXTURE;
            case ACTIVATED_IDOL -> TEXTURE;
            case TRANSITION_TO_RADIANCE -> TEXTURE;
            case ARENA_BUILDING_2 -> TEXTURE2;
            case RADIANCE -> TEXTURE2;
            default -> TEXTURE;
        };
    }
}
