package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.state.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Region-aligned marks shared by world rendering and the inventory avatar. */
@Mod.EventBusSubscriber(modid = RevivalMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MaimRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation OTHER = texture("other");
    private final PlayerModel<AbstractClientPlayer> skin;
    private final PlayerModel<AbstractClientPlayer> armor;

    private MaimRenderLayer(PlayerRenderer renderer, boolean slim) {
        super(renderer);
        skin = model(slim, .32f);
        armor = model(slim, 1.2f);
    }
    private static PlayerModel<AbstractClientPlayer> model(boolean slim, float inflation) {
        return new PlayerModel<>(LayerDefinition.create(
                PlayerModel.createMesh(new CubeDeformation(inflation), slim), 64, 64).bakeRoot(), slim);
    }
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String name : event.getSkins()) {
            if (event.getPlayerSkin(name) instanceof PlayerRenderer renderer)
                renderer.addLayer(new MaimRenderLayer(renderer, "slim".equals(name)));
        }
    }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation(RevivalMod.MOD_ID, "textures/entity/maim_" + name + ".png");
    }
    private static ResourceLocation texture(MaimType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "cracked", "burnt", "opened", "low_oxygen", "hypoxia" -> texture(name);
            default -> OTHER;
        };
    }
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        boolean any = false;
        for (Region region : Region.values())
            for (MaimType type : MaimType.values())
                any |= ClientRevivalState.count(player.getUUID(), region, type) > 0;
        if (!any) return;
        PlayerModel<AbstractClientPlayer> base = getParentModel();
        base.copyPropertiesTo(skin); base.copyPropertiesTo(armor);
        copyParts(base, skin); copyParts(base, armor);
        for (Region region : Region.values()) {
            boolean covered = switch (region) {
                case HEAD -> !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
                case TORSO, LEFT_ARM, RIGHT_ARM -> !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
                case LEFT_LEG, RIGHT_LEG -> !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                        || !player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            };
            ModelPart part = part(covered ? armor : skin, region);
            for (MaimType type : MaimType.values()) {
                if (ClientRevivalState.count(player.getUUID(), region, type) == 0) continue;
                part.render(pose, buffers.getBuffer(RenderType.entityTranslucent(texture(type))),
                        light, OverlayTexture.NO_OVERLAY);
            }
        }
    }
    private static void copyParts(PlayerModel<AbstractClientPlayer> from,
            PlayerModel<AbstractClientPlayer> to) {
        to.head.copyFrom(from.head); to.body.copyFrom(from.body);
        to.leftArm.copyFrom(from.leftArm); to.rightArm.copyFrom(from.rightArm);
        to.leftLeg.copyFrom(from.leftLeg); to.rightLeg.copyFrom(from.rightLeg);
    }
    private static ModelPart part(PlayerModel<AbstractClientPlayer> model, Region region) {
        return switch (region) {
            case HEAD -> model.head;
            case TORSO -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }
}
