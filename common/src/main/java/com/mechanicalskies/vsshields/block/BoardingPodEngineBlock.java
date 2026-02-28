package com.mechanicalskies.vsshields.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Engine block of the Boarding Pod multiblock.
 *
 * Must be adjacent to a {@link BoardingPodCockpitBlock} to form a valid
 * pod assembly. No block entity or special logic — purely structural.
 */
public class BoardingPodEngineBlock extends Block {

    public BoardingPodEngineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
