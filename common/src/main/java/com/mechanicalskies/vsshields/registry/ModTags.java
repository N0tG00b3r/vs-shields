package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static final TagKey<Block> CANNON_BLOCKS = TagKey.create(
            Registries.BLOCK, new ResourceLocation(VSShieldsMod.MOD_ID, "cannon_blocks"));

    public static final TagKey<Block> CRITICAL_BLOCKS = TagKey.create(
            Registries.BLOCK, new ResourceLocation(VSShieldsMod.MOD_ID, "critical_blocks"));
}
