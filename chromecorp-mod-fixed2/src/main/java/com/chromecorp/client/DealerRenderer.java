package com.chromecorp.client;

import com.chromecorp.BlackMarketDealer;
import com.chromecorp.ChromeCorp;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class DealerRenderer extends BipedEntityRenderer<BlackMarketDealer, BipedEntityModel<BlackMarketDealer>> {
    private static final Identifier TEXTURE = new Identifier(ChromeCorp.ID, "textures/entity/ripper_shady.png");

    public DealerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(BlackMarketDealer e) { return TEXTURE; }
}
