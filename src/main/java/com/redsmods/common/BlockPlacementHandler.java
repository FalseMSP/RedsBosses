package com.redsmods.common;

import com.redsmods.common.entity.Radiance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.List;

public class BlockPlacementHandler {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        // Only handle on server side
        if (event.getLevel().isClientSide()) return;

        // Check if the entity placing the block is a player
        if (event.getEntity() instanceof Player player) {
            BlockPos placedPos = event.getPos();

            // Find all Radiance bosses in the area and notify them
            List<Radiance> nearbyBosses = event.getLevel().getEntitiesOfClass(Radiance.class,
                    new AABB(placedPos.getX() - 50, placedPos.getY() - 50, placedPos.getZ() - 50,
                            placedPos.getX() + 50, placedPos.getY() + 50, placedPos.getZ() + 50));

            for (Radiance boss : nearbyBosses) {
                boss.onBlockPlaced(placedPos);
            }
        }
    }
}