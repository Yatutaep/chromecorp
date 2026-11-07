package com.chromecorp.client;

import com.chromecorp.ChromeCorp;
import com.chromecorp.RipperEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class RipperRenderer extends BipedEntityRenderer<RipperEntity, BipedEntityModel<RipperEntity>> {
    private static final Identifier CLINIC = new Identifier(ChromeCorp.ID, "textures/entity/ripper_clinic.png");
    private static final Identifier SHADY = new Identifier(ChromeCorp.ID, "textures/entity/ripper_shady.png");

    public RipperRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(RipperEntity e) { return e.isShady() ? SHADY : CLINIC; }
}
