//package com.redsmods.common.client;
//
//import com.redsmods.RedsBosses;
//import com.redsmods.common.client.model.RadianceModel;
//import com.redsmods.common.client.renderer.RadianceRenderer;
//import com.redsmods.common.registry.ModEntities;
//import net.neoforged.api.distmarker.Dist;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//
//@EventBusSubscriber(modid = RedsBosses.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//public class ClientSetup {
//
//    @SubscribeEvent
//    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
//        event.registerEntityRenderer(ModEntities.Radiance.get(), RadianceRenderer::new);
//    }
//
//    @SubscribeEvent
//    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
//        event.registerLayerDefinition(RadianceModel.LAYER_LOCATION, RadianceModel::createBodyLayer);
//    }
//}
