package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPart;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.patched.entity.PPlayerRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Optional client-only bridge for Epic Fight 20.14.17 (upstream 9d25f19).
 * Replace the vanilla maim layer's Root fallback, never add a second custom layer.
 * Native renderer supplies the current pose/armature for world and native GUI avatars;
 * do not infer combat state, rotate vanilla cubes, or independently sample animations.
 */
final class EpicMaimRenderLayer extends PatchedLayer<AbstractClientPlayer,
        AbstractClientPlayerPatch<AbstractClientPlayer>, PlayerModel<AbstractClientPlayer>, MaimRenderLayer> {
    private final PPlayerRenderer renderer;

    private EpicMaimRenderLayer(PPlayerRenderer renderer) {
        this.renderer = renderer;
    }

    static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EpicMaimRenderLayer::modify);
    }

    private static void modify(PatchedRenderersEvent.Modify event) {
        if (event.get(EntityType.PLAYER) instanceof PPlayerRenderer renderer) {
            // put(), not putIfAbsent(): initLayerLast may already have supplied Root fallback.
            // Repeated resource reloads replace this entry, rather than accumulating layers.
            renderer.addPatchedLayerAlways(MaimRenderLayer.class, new EpicMaimRenderLayer(renderer));
        }
    }

    // Offset along the *posed* surface normal. CPU drawPosed is intentional: compute draw
    // bypasses custom DrawingFunction. Native triangulated buffers still own the geometry.
    private static final Mesh.DrawingFunction MARK = (out, x, y, z, nx, ny, nz,
            light, r, g, b, a, u, v, overlay) -> {
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        float offset = length > 0 ? .002f / length : 0;
        out.vertex(x + nx * offset, y + ny * offset, z + nz * offset,
                r, g, b, a, u, v, overlay, light, nx, ny, nz);
    };

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch,
            AbstractClientPlayer player, MaimRenderLayer vanillaLayer, PoseStack pose,
            MultiBufferSource buffers, int light, OpenMatrix4f[] poses,
            float bob, float yRot, float xRot, float partialTick) {
        if (player.isInvisible() || player.isSpectator()) return;
        HumanoidMesh skin = renderer.getMeshProvider(patch).get(); // ALEX or BIPED
        for (Region region : Region.values()) {
            for (MaimType type : MaimType.values()) {
                if (ClientRevivalState.count(player.getUUID(), region, type) <= 0) continue;
                String base = MaimSurface.part(region);
                // Respect native visibility (e.g. a hidden body part), including outer skin.
                if (!skin.hasPart(base) || skin.getPart(base).isHidden()) continue;
                String outer = MaimSurface.outerPart(region);
                String surface = skin.hasPart(outer) && !skin.getPart(outer).isHidden() ? outer : base;
                draw(skin, surface, region, surface.equals(outer), patch, type, pose, buffers, light, poses);
                for (EquipmentSlot slot : armorSlots(region)) {
                    if (!(player.getItemBySlot(slot).getItem() instanceof ArmorItem armor)
                            || armor.getEquipmentSlot() != slot) continue;
                    // WearableItemLayer owns loading/baking. Reuse its real mesh, including
                    // custom armor, rather than placing skin-sized cubes beneath the armor.
                    SkinnedMesh mesh = WearableItemLayer.getCachedModel(armor);
                    if (mesh == null) mesh = skin.getHumanoidArmorModel(slot).get();
                    drawArmor(mesh, region, patch, type, pose, buffers, light, poses);
                }
            }
        }
    }

    private static void drawArmor(SkinnedMesh mesh, Region region,
            AbstractClientPlayerPatch<AbstractClientPlayer> patch, MaimType type,
            PoseStack pose, MultiBufferSource buffers, int light, OpenMatrix4f[] poses) {
        // Native leggings/boots use a single "noGroups" part. Partition complete
        // triangles by their native skinning weights, not part-name/left-right guesses.
        // The same path also covers baked/custom armor with arbitrary part names.
        java.util.List<Boolean> selected = new java.util.ArrayList<>();
        for (var part : mesh.getAllParts()) {
            if (part.isHidden()) continue;
            var vertices = part.getVertices();
            if (vertices.size() % 3 != 0) return; // unsupported topology: never emit partial triangles
            for (int start = 0; start < vertices.size(); start += 3) {
                float[] weights = new float[Region.values().length];
                for (int v = start; v < start + 3; v++) {
                    int position = vertices.get(v).position;
                    int[] joints = mesh.affectingJointIndices()[position];
                    int[] indices = mesh.affectingWeightIndices()[position];
                    for (int i = 0; i < joints.length; i++) {
                        Region bound = MaimSurface.jointRegion(patch.getArmature().searchJointById(joints[i]).getName());
                        if (bound != null) weights[bound.ordinal()] += mesh.weights()[indices[i]];
                    }
                }
                boolean show = MaimSurface.dominantRegion(weights) == region;
                selected.add(show); selected.add(show); selected.add(show);
            }
        }
        int[] vertex = {0};
        Mesh.DrawingFunction markRegion = (out, x, y, z, nx, ny, nz,
                packedLight, r, g, b, a, u, v, overlay) -> {
            if (selected.get(vertex[0]++)) MARK.draw(out, x, y, z, nx, ny, nz,
                    packedLight, r, g, b, a, MaimSurface.armorU(region, u), MaimSurface.armorV(v), overlay);
        };
        mesh.drawPosed(pose, buffers.getBuffer(EpicFightRenderTypes.getTriangulated(
                RenderType.entityTranslucent(MaimRenderLayer.texture(type)))), markRegion,
                light, 1, 1, 1, 1, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
    }

    private static EquipmentSlot[] armorSlots(Region region) {
        return switch (region) {
            case HEAD -> new EquipmentSlot[] {EquipmentSlot.HEAD};
            case TORSO, LEFT_ARM, RIGHT_ARM -> new EquipmentSlot[] {EquipmentSlot.CHEST};
            case LEFT_LEG, RIGHT_LEG -> new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.FEET};
        };
    }

    private static void draw(SkinnedMesh mesh, String surface, Region region, boolean outer,
            AbstractClientPlayerPatch<AbstractClientPlayer> patch, MaimType type,
            PoseStack pose, MultiBufferSource buffers, int light, OpenMatrix4f[] poses) {
        // Mesh assets are shared with native rendering. Never initialize/reset them here;
        // save/restore every visibility bit, even if drawing throws.
        Map<MeshPart, Boolean> hidden = new IdentityHashMap<>();
        mesh.getAllParts().forEach(part -> hidden.put(part, part.isHidden()));
        try {
            mesh.getAllParts().forEach(part -> part.setHidden(true));
            mesh.getPart(surface).setHidden(false);
            Mesh.DrawingFunction mappedMark = (out, x, y, z, nx, ny, nz,
                    packedLight, r, g, b, a, u, v, overlay) -> MARK.draw(
                    out, x, y, z, nx, ny, nz, packedLight, r, g, b, a,
                    MaimSurface.skinU(region, u, outer), MaimSurface.skinV(region, v, outer), overlay);
            mesh.drawPosed(pose, buffers.getBuffer(EpicFightRenderTypes.getTriangulated(
                    RenderType.entityTranslucent(MaimRenderLayer.texture(type)))), mappedMark,
                    light, 1, 1, 1, 1, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
        } finally {
            hidden.forEach(MeshPart::setHidden);
        }
    }
}
