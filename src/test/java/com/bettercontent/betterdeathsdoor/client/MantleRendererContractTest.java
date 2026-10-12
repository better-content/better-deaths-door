package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.compat.MantleRendererContract;
import com.bettercontent.betterdeathsdoor.mixin.MantleCompatibilityPlugin;
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

class MantleRendererContractTest {
    private ClassNode nativeRenderer() throws Exception {
        Path artifact = Path.of(System.getProperty("injury.mantle.native"));
        assertEquals("8a3f948f8c505ee92dae915ec0a1593594eb2667",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(artifact))));
        ClassNode type = new ClassNode();
        try (JarFile jar = new JarFile(artifact.toFile())) {
            new ClassReader(jar.getInputStream(jar.getJarEntry(MantleRendererContract.OWNER + ".class"))).accept(type, 0);
        }
        return type;
    }
    private MethodNode entry(ClassNode n) { return n.methods.stream().filter(m -> m.name.equals("renderHealthbar")).findFirst().orElseThrow(); }
    private MethodInsnNode health(ClassNode n) {
        for (AbstractInsnNode i : entry(n).instructions)
            if (i instanceof MethodInsnNode m && m.name.equals("m_21223_")) return m;
        throw new AssertionError("health call missing from fixture");
    }
    private void reject(ClassNode type, String clause) {
        var e = assertThrows(IllegalStateException.class, () -> MantleRendererContract.validate(type));
        assertTrue(e.getMessage().contains(clause), e.getMessage());
        assertTrue(e.getMessage().contains("compatibility review required"));
    }
    @Test void pinnedNativeFixtureIsStructurallySupported() throws Exception { MantleRendererContract.validate(nativeRenderer()); }
    @Test void officialAndSrgNamesAreSupported() throws Exception {
        ClassNode type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof MethodInsnNode m) {
            m.name = switch (m.name) {
                case "m_21223_" -> "getHealth"; case "m_6103_" -> "getAbsorptionAmount";
                case "m_14167_" -> "ceil"; case "m_91288_" -> "getCameraEntity"; default -> m.name;
            };
        }
        MantleRendererContract.validate(type);
    }
    @Test void localRenumberingDebugTablesAndNopsAreNotCompatibilityBoundaries() throws Exception {
        ClassNode type = nativeRenderer(); MethodNode m = entry(type);
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i instanceof VarInsnNode v && v.var >= 3) v.var += 11;
            if (i instanceof IincInsnNode v && v.var >= 3) v.var += 11;
            if (i.getOpcode() >= 0) m.instructions.insertBefore(i, new InsnNode(Opcodes.NOP));
        }
        m.maxLocals += 11; m.localVariables = null; m.visibleLocalVariableAnnotations = null;
        MantleRendererContract.validate(type);
    }
    @Test void missingAndDuplicateHealthReadsAreRejected() throws Exception {
        ClassNode missing = nativeRenderer(); health(missing).name = "otherHealth";
        reject(missing, "exactly one native Player health read");
        ClassNode duplicate = nativeRenderer(); MethodInsnNode h = health(duplicate);
        InsnList extra = new InsnList(); extra.add(new VarInsnNode(Opcodes.ALOAD, 5));
        // Obtain the actual camera-player slot from the instruction before the pinned read, not a test constant.
        ((VarInsnNode)extra.getFirst()).var = ((VarInsnNode)h.getPrevious()).var;
        extra.add(new MethodInsnNode(h.getOpcode(), h.owner, h.name, h.desc, h.itf));
        extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/Mth", "m_14167_", "(F)I", false));
        extra.add(new InsnNode(Opcodes.POP)); entry(duplicate).instructions.insertBefore(h.getPrevious(), extra);
        reject(duplicate, "exactly one native Player health read");
    }
    @Test void historyShapeAndRoundingChangesAreRejected() throws Exception {
        ClassNode type = nativeRenderer(); type.fields.removeIf(f -> f.name.equals("displayHealth"));
        reject(type, "history field displayHealth");
        type = nativeRenderer(); type.fields.stream().filter(f -> f.name.equals("healthBlinkTime")).findFirst().orElseThrow().desc = "I";
        reject(type, "history field type healthBlinkTime");
        type = nativeRenderer(); ((MethodInsnNode)health(type).getNext()).name = "m_14143_";
        reject(type, "health must use float ceil");
    }
    @Test void alteredHealthDrawAndRememberedFlowAreRejected() throws Exception {
        ClassNode type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof FieldInsnNode f
                && f.name.equals("lastHealth") && f.getOpcode() == Opcodes.PUTFIELD) {
            AbstractInsnNode value = f.getPrevious(); entry(type).instructions.set(value, new InsnNode(Opcodes.ICONST_0));
        }
        reject(type, "rounded health must update lastHealth");
        type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof MethodInsnNode m && m.name.equals("renderHeartsWithDamage")) m.name = "otherDraw";
        reject(type, "both ordinary/damage drawers");
    }
    @Test void immutableHistoryAndNonCameraHealthAreRejected() throws Exception {
        ClassNode type = nativeRenderer();
        type.fields.stream().filter(f -> f.name.equals("lastHealth")).findFirst().orElseThrow().access |= Opcodes.ACC_FINAL;
        reject(type, "mutable instance history");
        type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof MethodInsnNode m && m.name.equals("m_91288_")) m.name = "otherEntity";
        reject(type, "health receiver must originate from the camera");
    }
    @Test void arithmeticChangesToOrdinaryHealthAreRejected() throws Exception {
        ClassNode type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof MethodInsnNode m && m.name.equals("renderHeartsWithDamage")) {
            InsnList altered = new InsnList(); altered.add(new InsnNode(Opcodes.ICONST_1)); altered.add(new InsnNode(Opcodes.IADD));
            entry(type).instructions.insert(m.getPrevious().getPrevious(), altered);
        }
        reject(type, "both ordinary/damage drawers");
    }
    @Test void installedMantleMissingTargetIsExplicitlyRejected() {
        var error = assertThrows(IllegalStateException.class, () -> MantleCompatibilityPlugin.admit(true, "1.11.104", () -> null));
        assertTrue(error.getMessage().contains("installed Mantle renderer missing"));
    }
    @Test void cancellationAndAbsorptionChangesAreRejected() throws Exception {
        ClassNode type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof MethodInsnNode m && m.name.equals("setCanceled")) m.name = "otherCancel";
        reject(type, "overlay cancellation");
        type = nativeRenderer();
        for (AbstractInsnNode i : entry(type).instructions) if (i instanceof MethodInsnNode m && m.name.equals("m_6103_")) m.name = "otherAbsorb";
        reject(type, "independent absorption");
    }
    @Test void stablePatchAdmissionIsNotAHashOrExactVersionAllowlist() throws Exception {
        for (String version : new String[]{"1.11.104", "1.11.105", "1.11.999"})
            assertTrue(MantleCompatibilityPlugin.admit(true, version, () -> { try { return nativeRenderer(); } catch (Exception e) { throw new RuntimeException(e); } }));
        for (String version : new String[]{"1.11.103", "1.12.0", "1.11.105-beta", "1.11.105-SNAPSHOT", "garbage"})
            assertThrows(IllegalStateException.class, () -> MantleRendererContract.validateVersion(version));
    }
    @Test void absentMantleAndServerNeverRequestClientBytecode() {
        assertFalse(MantleCompatibilityPlugin.admit(true, null, () -> { throw new AssertionError("absent target read"); }));
        assertFalse(MantleCompatibilityPlugin.admit(false, "1.11.104", () -> { throw new AssertionError("server client target read"); }));
    }
    @Test void pluginInitializationAndOtherMixinsDoNotResolveClientClasses() throws Exception {
        String name = "com.bettercontent.betterdeathsdoor.mixin.MantleCompatibilityPlugin";
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String n, boolean resolve) throws ClassNotFoundException {
                if (n.startsWith("net.minecraft.client.")) throw new AssertionError("client class resolved: " + n);
                if (!n.equals(name)) return super.loadClass(n, resolve);
                Class<?> found = findLoadedClass(n);
                if (found == null) try (var in = getParent().getResourceAsStream(n.replace('.', '/') + ".class")) {
                    byte[] b = in.readAllBytes(); found = defineClass(n, b, 0, b.length);
                } catch (Exception e) { throw new ClassNotFoundException(n, e); }
                return found;
            }
        };
        var plugin = Class.forName(name, true, loader).getConstructor().newInstance();
        plugin.getClass().getMethod("onLoad", String.class).invoke(plugin, "irrelevant");
        assertEquals(true, plugin.getClass().getMethod("shouldApplyMixin", String.class, String.class).invoke(plugin, "unrelated", "ForgeHealthMixin"));
    }
    @Test void compiledOperationCallsItsDelegateExactlyOnceAndDoesNotUseSlots() throws Exception {
        ClassNode mixin = new ClassNode();
        try (var in = getClass().getClassLoader().getResourceAsStream("com/bettercontent/betterdeathsdoor/mixin/MantleHealthMixin.class")) {
            new ClassReader(in).accept(mixin, 0);
        }
        MethodNode method = mixin.methods.stream().filter(m -> m.name.endsWith("semanticHealth")).findFirst().orElseThrow();
        int calls = 0;
        for (AbstractInsnNode i : method.instructions) if (i instanceof MethodInsnNode m && m.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation") && m.name.equals("call")) calls++;
        assertEquals(1, calls);
        var annotation = method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/WrapOperation;")).findFirst().orElseThrow();
        for (String key : new String[]{"require", "expect", "allow"}) assertEquals(1, annotation.values.get(annotation.values.indexOf(key) + 1));
        assertEquals(false, annotation.values.get(annotation.values.indexOf("remap") + 1));
        assertFalse(mixin.methods.stream().flatMap(m -> java.util.stream.Stream.concat(
                m.visibleAnnotations == null ? java.util.stream.Stream.empty() : m.visibleAnnotations.stream(),
                m.invisibleAnnotations == null ? java.util.stream.Stream.empty() : m.invisibleAnnotations.stream()))
                .anyMatch(a -> a.desc.endsWith("/ModifyVariable;")));
        var refmap = com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("injury.main.refmap")))).getAsJsonObject();
        assertEquals("Lnet/minecraft/world/entity/player/Player;m_21223_()F", refmap.getAsJsonObject("data")
                .getAsJsonObject("searge").getAsJsonObject("com/bettercontent/betterdeathsdoor/mixin/MantleHealthMixin")
                .get("Lnet/minecraft/world/entity/player/Player;getHealth()F").getAsString());
    }
}
