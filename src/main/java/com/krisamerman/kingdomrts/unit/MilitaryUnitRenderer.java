package com.krisamerman.kingdomrts.unit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

// Client-only. Draws a MilitaryUnit with the vanilla zombie model and skin (placeholder art).
public class MilitaryUnitRenderer extends HumanoidMobRenderer<MilitaryUnit, HumanoidModel<MilitaryUnit>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public MilitaryUnitRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(MilitaryUnit unit) {
        return TEXTURE;
    }
}
