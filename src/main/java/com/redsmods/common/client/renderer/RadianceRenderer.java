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

    public RadianceRenderer(EntityRendererProvider.Context context) {
        super(context, new RadianceModel<>(context.bakeLayer(RadianceModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Radiance entity) {
        return TEXTURE;
    }
}
