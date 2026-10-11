package com.bettercontent.betterdeathsdoor.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Inspect the exact production dependency, not source literals or an arbitrary mapped cache. */
class MantleRendererContractTest {
    private static final String OWNER = "slimeknights/mantle/client/ExtraHeartRenderHandler";
    private static final String PRE = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre";

    @Test void pinnedNativeRendererOwnsCurrentHealthHistoryAndOverlayCancellation() throws Exception {
        Path artifact = Path.of(System.getProperty("injury.mantle.native"));
        assertEquals("8a3f948f8c505ee92dae915ec0a1593594eb2667",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(artifact))),
                "Mantle pin changed; review renderer compatibility before updating this contract");
        ClassNode type = new ClassNode();
        try (JarFile jar = new JarFile(artifact.toFile())) {
            new ClassReader(jar.getInputStream(jar.getJarEntry(OWNER + ".class"))).accept(type, 0);
        }
        for (String name : new String[]{"lastHealth", "displayHealth", "healthBlinkTime"}) {
            FieldNode field = type.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
            assertEquals(name.equals("healthBlinkTime") ? "J" : "I", field.desc);
            assertTrue((field.access & Opcodes.ACC_PRIVATE) != 0);
            assertEquals(0, field.access & Opcodes.ACC_STATIC);
        }
        MethodNode method = type.methods.stream().filter(m -> m.name.equals("renderHealthbar")
                && m.desc.equals("(L" + PRE + ";)V")).findFirst().orElseThrow();
        int healthReads = 0, draws = 0, cancelIndex = -1, lastDrawIndex = -1;
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode node = method.instructions.get(i);
            if (!(node instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("net/minecraft/world/entity/player/Player") && call.name.equals("m_21223_")
                    && call.desc.equals("()F")) {
                healthReads++;
                AbstractInsnNode ceil = executable(call.getNext());
                assertInstanceOf(MethodInsnNode.class, ceil);
                MethodInsnNode rounding = (MethodInsnNode) ceil;
                assertEquals("net/minecraft/util/Mth", rounding.owner);
                assertEquals("m_14167_", rounding.name);
                assertEquals("(F)I", rounding.desc);
                VarInsnNode store = assertInstanceOf(VarInsnNode.class, executable(ceil.getNext()));
                assertEquals(Opcodes.ISTORE, store.getOpcode());
                assertEquals(7, store.var);
            }
            if (call.owner.equals(OWNER) && (call.name.equals("renderHearts")
                    || call.name.equals("renderHeartsWithDamage") || call.name.equals("renderHeartRow"))) {
                draws++; lastDrawIndex = i;
            }
            if (call.owner.equals(PRE) && call.name.equals("setCanceled") && call.desc.equals("(Z)V")) {
                assertEquals(Opcodes.ICONST_1, executableBack(call.getPrevious()).getOpcode());
                cancelIndex = i;
            }
        }
        assertEquals(1, healthReads);
        assertTrue(draws > 0, "Mantle must draw its own hearts");
        assertTrue(cancelIndex > lastDrawIndex, "Mantle must cancel Forge after drawing");
        for (String field : new String[]{"lastHealth", "displayHealth", "healthBlinkTime"}) {
            boolean reads = false;
            for (AbstractInsnNode node : method.instructions) {
                if (node instanceof FieldInsnNode access && access.owner.equals(OWNER) && access.name.equals(field)
                        && access.getOpcode() == Opcodes.GETFIELD) reads = true;
            }
            assertTrue(reads, "Renderer no longer consumes " + field);
        }
    }

    private static AbstractInsnNode executable(AbstractInsnNode node) {
        while (node != null && node.getOpcode() < 0) node = node.getNext();
        return node;
    }
    private static AbstractInsnNode executableBack(AbstractInsnNode node) {
        while (node != null && node.getOpcode() < 0) node = node.getPrevious();
        return node;
    }
}
