package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.entity.BoardingPodEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.ThrownItemRenderer

/**
 * Temporary renderer — displays the BoardingPodEntity as a floating cockpit item.
 * Replace with a proper 3D model renderer once the OBJ model is ready.
 */
class BoardingPodRenderer(ctx: EntityRendererProvider.Context) : ThrownItemRenderer<BoardingPodEntity>(ctx)
