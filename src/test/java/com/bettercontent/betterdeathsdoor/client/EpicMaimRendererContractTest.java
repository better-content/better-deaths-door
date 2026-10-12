package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.state.Region;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Native API/dispatch proof only. This cannot establish human visual acceptance. */
class EpicMaimRendererContractTest {
    private static final String ROOT = "yesman/epicfight/";

    private JarFile nativeJar() throws Exception {
        String path = System.getProperty("injury.epic.native");
        assertNotNull(path, "Prepare the declared raw Epic Fight input, not historical outputs");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        assertEquals("faa8eddad9d80adb9c4ba81fd0a8a21c81989d3d",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
        return new JarFile(path);
    }

    private ClassNode read(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(ROOT + path + ".class");
        assertNotNull(entry, path);
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(entry)) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private String asset(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(entry, path);
        try (var stream = jar.getInputStream(entry)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private boolean constant(MethodNode method, String value) {
        for (var instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode ldc && value.equals(ldc.cst)) return true;
        }
        return false;
    }

    @Test void optionalAdmissionIsExactAndEveryRegionHasDistinctNativeParts() {
        assertTrue(MaimSurface.supportsEpic("20.14.17"));
        for (String version : new String[] {"20.14.16", "20.14.18", "21.0", "", "20.14.17-custom"})
            assertFalse(MaimSurface.supportsEpic(version));
        assertFalse(MaimSurface.supportsEpic(null));
        assertEquals(6, java.util.Arrays.stream(Region.values()).map(MaimSurface::part).distinct().count());
        assertEquals(6, java.util.Arrays.stream(Region.values()).map(MaimSurface::outerPart).distinct().count());
        assertEquals(Region.LEFT_LEG, MaimSurface.jointRegion("Thigh_L"));
        assertEquals(Region.LEFT_LEG, MaimSurface.jointRegion("Knee_L"));
        assertEquals(Region.RIGHT_LEG, MaimSurface.jointRegion("Leg_R"));
        assertEquals(Region.LEFT_ARM, MaimSurface.jointRegion("Elbow_L"));
        assertEquals(Region.RIGHT_ARM, MaimSurface.jointRegion("Shoulder_R"));
        assertEquals(Region.HEAD, MaimSurface.jointRegion("Head"));
        assertEquals(Region.TORSO, MaimSurface.jointRegion("Root"));
        assertEquals(Region.TORSO, MaimSurface.jointRegion("Chest"));
        assertNull(MaimSurface.jointRegion("foreign_joint"));
    }

    @Test void mixedJointTrianglesHaveOneAnatomicalOwnerAndUnknownWeightsHaveNone() {
        float[] weights = new float[Region.values().length];
        assertNull(MaimSurface.dominantRegion(weights));
        for (Region region : Region.values()) {
            java.util.Arrays.fill(weights, .1f);
            weights[region.ordinal()] = 2.7f;
            assertEquals(region, MaimSurface.dominantRegion(weights));
        }
        java.util.Arrays.fill(weights, 0);
        weights[Region.LEFT_LEG.ordinal()] = 1.5f;
        weights[Region.RIGHT_LEG.ordinal()] = 1.5f;
        Region owner = MaimSurface.dominantRegion(weights);
        assertNotNull(owner);
        assertEquals(1, java.util.Arrays.stream(Region.values()).filter(r -> r == owner).count(),
                "A boundary triangle must not emit marks into both legs");
    }

    @Test void pinnedNativePlayerMeshHasBothStandardAndSlimAndAnatomicalSurfaces() throws Exception {
        try (var jar = nativeJar()) {
            var mesh = read(jar, "client/mesh/HumanoidMesh");
            Set<String> fields = mesh.fields.stream().map(f -> f.name).collect(Collectors.toSet());
            for (Region region : Region.values()) {
                assertTrue(fields.contains(MaimSurface.part(region)));
                assertTrue(fields.contains(MaimSurface.outerPart(region)));
            }
            var provider = method(read(jar, "client/renderer/patched/entity/PPlayerRenderer"), "getMeshProvider");
            assertTrue(constant(provider, "slim"));
            Set<String> providers = new java.util.HashSet<>();
            for (var instruction : provider.instructions)
                if (instruction instanceof FieldInsnNode field && field.owner.equals(ROOT + "api/client/model/Meshes")) providers.add(field.name);
            assertEquals(Set.of("ALEX", "BIPED"), providers);
            // Inspect the pinned native assets themselves: shader/joint-weighted mesh
            // partition names, not an independently invented model fixture.
            for (String model : new String[] {"biped", "biped_slim_arm"}) {
                String asset = asset(jar, "assets/epicfight/animmodels/entity/" + model + ".json");
                for (Region region : Region.values()) {
                    assertTrue(asset.contains("\"" + MaimSurface.part(region) + "\""), model + " " + region);
                    assertTrue(asset.contains("\"" + MaimSurface.outerPart(region) + "\""), model + " " + region);
                }
                assertTrue(asset.contains("\"vindices\""));
                assertTrue(asset.contains("\"weights\""));
            }
            for (String model : new String[] {"helmet", "chestplate", "leggins", "boots"}) {
                String asset = asset(jar, "assets/epicfight/animmodels/armor/" + model + ".json");
                if (model.equals("leggins") || model.equals("boots")) {
                    assertTrue(asset.contains("\"noGroups\""), "Native leg armor must use joint-weight partitioning");
                    assertTrue(asset.contains("\"vindices\""));
                    continue;
                }
                for (Region region : switch (model) {
                    case "helmet" -> new Region[] {Region.HEAD};
                    case "chestplate" -> new Region[] {Region.TORSO, Region.LEFT_ARM, Region.RIGHT_ARM};
                    default -> new Region[] {Region.LEFT_LEG, Region.RIGHT_LEG};
                }) assertTrue(asset.contains("\"" + MaimSurface.part(region) + "\""), model + " " + region);
            }
            assertTrue(calls(method(read(jar, "client/renderer/patched/layer/WearableItemLayer"), "getCachedModel"),
                    "java/util/Map", "get"));
        }
    }

    @Test void pinnedDispatchReplacesVanillaFallbackAndSuppliesActualPoses() throws Exception {
        try (var jar = nativeJar()) {
            var renderer = read(jar, "client/renderer/patched/entity/PatchedLivingEntityRenderer");
            assertTrue(calls(method(renderer, "addPatchedLayerAlways"), "java/util/Map", "put"));
            assertTrue(calls(method(renderer, "initLayerLast"), ROOT + "client/renderer/patched/entity/PatchedLivingEntityRenderer", "addPatchedLayer"));
            assertTrue(calls(method(renderer, "renderLayer"), ROOT + "client/renderer/patched/layer/PatchedLayer", "renderLayer"));
            assertTrue(calls(method(renderer, "render"), ROOT + "api/model/Armature", "getPoseMatrices"));
            var posed = method(read(jar, "api/client/model/SkinnedMesh"), "drawPosed");
            assertTrue(calls(posed, ROOT + "api/client/model/Mesh$DrawingFunction", "draw"));
            assertTrue(calls(posed, ROOT + "api/model/Armature", "searchJointById"));
            assertTrue(calls(posed, ROOT + "api/animation/Joint", "getToOrigin"));
            var events = method(read(jar, "client/events/engine/RenderEngine$Events"), "renderLivingEvent");
            assertTrue(calls(events, ROOT + "client/world/capabilites/entitypatch/player/LocalPlayerPatch", "setModelYRotInGui"));
            assertTrue(calls(events, ROOT + "client/world/capabilites/entitypatch/player/LocalPlayerPatch", "disableModelYRotInGui"));
            assertTrue(calls(events, ROOT + "world/capabilities/entitypatch/LivingEntityPatch", "overrideRender"));
            assertTrue(calls(events, ROOT + "client/events/engine/RenderEngine", "renderEntityArmatureModel"));
        }
    }

    @Test void productionRoutingKeepsVanillaAndUsesOneNativeReplacementForAllAvatars() throws Exception {
        String vanilla = Files.readString(Path.of("src/main/java/com/bettercontent/betterdeathsdoor/client/MaimRenderLayer.java"));
        String animated = Files.readString(Path.of("src/main/java/com/bettercontent/betterdeathsdoor/client/EpicMaimRenderLayer.java"));
        assertTrue(vanilla.contains("getModContainerById(\"epicfight\")"));
        assertTrue(vanilla.contains("renderer.addLayer(new MaimRenderLayer"));
        assertTrue(vanilla.contains("skin = model(slim, .6f)"), "Marks must clear the vanilla .5 clothing shell");
        assertTrue(vanilla.contains("to.visible = from.visible"));
        assertTrue(animated.contains("addPatchedLayerAlways(MaimRenderLayer.class"));
        assertFalse(animated.contains("addCustomLayer("));
        assertTrue(animated.contains("player.isInvisible() || player.isSpectator()"));
        assertTrue(animated.contains("ClientRevivalState.count(player.getUUID(), region, type) <= 0"));
        assertTrue(animated.contains("renderer.getMeshProvider(patch).get()"));
        assertTrue(animated.contains("WearableItemLayer.getCachedModel(armor)"));
        assertTrue(animated.contains("patch.getArmature(), poses"));
        assertTrue(animated.contains("mesh.affectingJointIndices()[position]"));
        assertTrue(animated.contains("mesh.weights()[indices[i]]"));
        assertTrue(animated.contains("vertices.size() % 3 != 0"));
        assertTrue(animated.contains("selected.add(show); selected.add(show); selected.add(show)"));
        assertTrue(animated.contains("hidden.forEach(MeshPart::setHidden)"));
        assertFalse(animated.contains("isBattleMode"), "Native overrideRender, not guessed combat state, owns dispatch");
        assertFalse(animated.contains("Minecraft.getInstance().screen"), "Native inventory/Mend avatars must share world routing");
    }
}
