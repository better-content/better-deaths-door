package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Real pinned native UV triangles versus authored PNG alpha, not a synthetic mesh fixture. */
class MaimNativeUvTest {
    private record Triangle(float[] u, float[] v) {}
    private record Influence(int joint, float weight) {}

    private JarFile nativeJar() throws Exception {
        Path path = Path.of(Objects.requireNonNull(System.getProperty("injury.epic.native")));
        assertEquals("faa8eddad9d80adb9c4ba81fd0a8a21c81989d3d", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path))));
        return new JarFile(path.toFile());
    }

    private JsonObject model(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry("assets/epicfight/animmodels/" + name + ".json");
        assertNotNull(entry, name);
        try (var in = jar.getInputStream(entry)) {
            return JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private List<Triangle> triangles(JsonObject vertices, String part, Region region, boolean outer, boolean armor) {
        JsonArray uv = vertices.getAsJsonObject("uvs").getAsJsonArray("array");
        JsonArray indices = vertices.getAsJsonObject("parts").getAsJsonObject(part).getAsJsonArray("array");
        assertEquals(0, indices.size() % 9, "Complete triangles, three position/UV/normal index triples");
        List<Triangle> triangles = new ArrayList<>();
        for (int start = 0; start < indices.size(); start += 9) {
            float[] u = new float[3], v = new float[3];
            for (int corner = 0; corner < 3; corner++) {
                int index = indices.get(start + corner * 3 + 1).getAsInt();
                float rawU = uv.get(index * 2).getAsFloat(), rawV = uv.get(index * 2 + 1).getAsFloat();
                u[corner] = armor ? MaimSurface.armorU(region, rawU) : MaimSurface.skinU(region, rawU, outer);
                v[corner] = armor ? MaimSurface.armorV(rawV) : MaimSurface.skinV(region, rawV, outer);
            }
            triangles.add(new Triangle(u, v));
        }
        return triangles;
    }

    private int occupied(BufferedImage image, List<Triangle> triangles) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) == 0) continue;
            float u = (x + .5f) / image.getWidth(), v = (y + .5f) / image.getHeight();
            if (triangles.stream().anyMatch(t -> contains(t, u, v))) count++;
        }
        return count;
    }

    private boolean contains(Triangle t, float u, float v) {
        float area = cross(t.u[1] - t.u[0], t.v[1] - t.v[0], t.u[2] - t.u[0], t.v[2] - t.v[0]);
        if (Math.abs(area) < 1e-8f) return false;
        float a = cross(t.u[1] - u, t.v[1] - v, t.u[2] - u, t.v[2] - v) / area;
        float b = cross(t.u[2] - u, t.v[2] - v, t.u[0] - u, t.v[0] - v) / area;
        float c = 1 - a - b;
        return a >= -1e-5f && b >= -1e-5f && c >= -1e-5f;
    }

    private float cross(float ax, float ay, float bx, float by) { return ax * by - ay * bx; }

    private BufferedImage texture(MaimType type) throws Exception {
        BufferedImage image = ImageIO.read(Path.of("src/main/resources/assets/better_deaths_door/textures/entity/maim_"
                + type.name().toLowerCase(Locale.ROOT) + ".png").toFile());
        assertNotNull(image);
        assertEquals(64, image.getWidth()); assertEquals(64, image.getHeight());
        return image;
    }

    @Test void everyStandardAndSlimClothingSurfaceSamplesExistingAnatomicalMarks() throws Exception {
        try (var jar = nativeJar()) {
            for (String name : new String[] {"biped", "biped_slim_arm"}) {
                JsonObject vertices = model(jar, "entity/" + name).getAsJsonObject("vertices");
                for (Region region : Region.values()) for (MaimType type : MaimType.values()) {
                    BufferedImage image = texture(type);
                    String outer = MaimSurface.outerPart(region);
                    assertTrue(occupied(image, triangles(vertices, MaimSurface.part(region), region, false, false)) > 0,
                            name + " " + region + " " + type + " base");
                    assertEquals(0, occupied(image, triangles(vertices, outer, region, false, false)),
                            "Original defect: outer clothing UVs sample fully transparent marks " + name + " " + region);
                    assertTrue(occupied(image, triangles(vertices, outer, region, true, false)) > 0,
                            name + " " + region + " " + type + " remapped outer");
                }
            }
        }
    }

    private List<List<Influence>> influences(JsonObject vertices) {
        JsonArray counts = vertices.getAsJsonObject("vcounts").getAsJsonArray("array");
        JsonArray indices = vertices.getAsJsonObject("vindices").getAsJsonArray("array");
        JsonArray weights = vertices.getAsJsonObject("weights").getAsJsonArray("array");
        List<List<Influence>> result = new ArrayList<>();
        int offset = 0;
        for (var count : counts) {
            List<Influence> position = new ArrayList<>();
            for (int i = 0; i < count.getAsInt(); i++, offset += 2)
                position.add(new Influence(indices.get(offset).getAsInt(), weights.get(indices.get(offset + 1).getAsInt()).getAsFloat()));
            result.add(position);
        }
        assertEquals(indices.size(), offset);
        return result;
    }

    @Test void nativeArmorAndUngroupedLegMeshesUse64By32AtlasAndAnatomicalJointOwnership() throws Exception {
        try (var jar = nativeJar()) {
            JsonArray jointNames = model(jar, "entity/biped").getAsJsonObject("armature").getAsJsonArray("joints");
            for (String name : new String[] {"helmet", "chestplate", "leggins", "boots"}) {
                JsonObject vertices = model(jar, "armor/" + name).getAsJsonObject("vertices");
                var influences = influences(vertices);
                Map<Region, List<Triangle>> regions = new EnumMap<>(Region.class);
                for (var part : vertices.getAsJsonObject("parts").entrySet()) {
                    JsonArray indices = part.getValue().getAsJsonObject().getAsJsonArray("array");
                    Map<Region, List<Triangle>> mapped = new EnumMap<>(Region.class);
                    // Partition the actual native noGroups triangles by the same joint weights
                    // the renderer uses; no invented left/right part names or joint indices.
                    for (int start = 0; start < indices.size(); start += 9) {
                        float[] weights = new float[Region.values().length];
                        for (int corner = 0; corner < 3; corner++) {
                            int position = indices.get(start + corner * 3).getAsInt();
                            for (Influence influence : influences.get(position)) {
                                Region bound = MaimSurface.jointRegion(jointNames.get(influence.joint).getAsString());
                                assertNotNull(bound, "All pinned biped joints must have an anatomical owner");
                                weights[bound.ordinal()] += influence.weight;
                            }
                        }
                        Region region = MaimSurface.dominantRegion(weights);
                        assertNotNull(region);
                        Triangle triangle = mapped.computeIfAbsent(region,
                                key -> triangles(vertices, part.getKey(), key, false, true)).get(start / 9);
                        regions.computeIfAbsent(region, ignored -> new ArrayList<>()).add(triangle);
                    }
                }
                for (var region : regions.entrySet()) for (MaimType type : MaimType.values())
                    assertTrue(occupied(texture(type), region.getValue()) > 0,
                            name + " " + region.getKey() + " " + type + " must sample nontransparent existing marks");
                if (name.equals("helmet")) assertTrue(regions.containsKey(Region.HEAD));
                if (name.equals("chestplate")) assertTrue(regions.keySet().containsAll(Set.of(Region.TORSO, Region.LEFT_ARM, Region.RIGHT_ARM)));
                if (name.equals("leggins") || name.equals("boots")) assertTrue(regions.keySet().containsAll(Set.of(Region.LEFT_LEG, Region.RIGHT_LEG)));
            }
        }
    }
}
