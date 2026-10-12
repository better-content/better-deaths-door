package com.bettercontent.betterdeathsdoor;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RuntimeRefmapBuildContractTest {
    @Test
    void tracksMainProcessorSidecarsAndPackagesBothJarVariants() throws Exception {
        String build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(build.contains("outputs.file(generatedRefmap)"));
        assertTrue(build.contains("tmp/compileJava/compileJava-mappings.tsrg"));
        assertTrue(build.contains("options.isIncremental = false"));
        assertEquals(2, build.split("from\\(generatedRefmap\\)", -1).length - 1);
        assertTrue(build.contains("exclude(\"better_deaths_door.heart-review.refmap.json\")"));
    }

    @Test
    void validatesFinalJarJarMappingsAndRegistrationBeforeStaging() throws Exception {
        String build = Files.readString(Path.of("build.gradle.kts"));
        String gate = build.split("val verifyRuntimeRefmap by", 2)[1].split("val stageRuntimeJar by", 2)[0];
        String stage = build.split("val stageRuntimeJar by", 2)[1].split("// Independently", 2)[0];
        assertTrue(gate.contains("reobfJarJar/output.jar"));
        assertTrue(gate.contains("config[\"refmap\"] == \"better_deaths_door.refmap.json\""));
        assertTrue(gate.contains("registered.toSet() == expected.keys + unmappedNative"));
        assertTrue(gate.contains("types.keys == expected.keys.map"));
        assertTrue(gate.contains("mappings.keys == selectors.keys"));
        assertTrue(gate.contains("data[\"searge\"]"));
        assertTrue(gate.contains("if (mapped.startsWith(\"L\")) mapped.substringAfter(';') else mapped"));
        assertTrue(gate.contains("MantleHealthMixin") && gate.contains("m_21223_"));
        assertTrue(gate.contains("PlayerDamageMixin") && gate.contains("m_21153_"));
        assertFalse(gate.contains("dependsOn(stageRuntimeJar)"));
        assertTrue(stage.contains("dependsOn(verifyRuntimeRefmap)"));
    }
}
