package com.bettercontent.betterdeathsdoor.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/** Client-class-free structural admission. A matching shape is not native verification of a release. */
public final class MantleRendererContract {
    public static final String OWNER = "slimeknights/mantle/client/ExtraHeartRenderHandler";
    public static final String ENTRY = "renderHealthbar";
    public static final String PRE = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre";
    public static final String DESCRIPTOR = "(L" + PRE + ";)V";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final int CAMERA = 1, HEALTH_FLOAT = 2, HEALTH = 4, DISPLAY = 8,
            ABS_FLOAT = 16, ABS = 32, MAXIMUM = 64, CHANGED = 128;
    private MantleRendererContract() {}

    public static void validateVersion(String version) {
        check(version != null && version.matches("1\\.11\\.[0-9]+"), "stable Mantle 1.11.<patch> required: " + version);
        check(new BigInteger(version.substring(5)).compareTo(BigInteger.valueOf(104)) >= 0,
                "Mantle patch must be at least 104: " + version);
    }

    public static void validate(ClassNode type) {
        check(type != null, "installed Mantle renderer missing");
        check(type.name.equals(OWNER), "renderer class identity");
        for (String name : new String[]{"lastHealth", "displayHealth", "healthBlinkTime"}) {
            List<FieldNode> fields = type.fields.stream().filter(f -> f.name.equals(name)).toList();
            check(fields.size() == 1, "unique history field " + name);
            FieldNode field = fields.get(0);
            check(field.desc.equals(name.equals("healthBlinkTime") ? "J" : "I")
                    && (field.access & Opcodes.ACC_PRIVATE) != 0 && (field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == 0,
                    "private mutable instance history field type " + name);
        }
        List<MethodNode> entries = type.methods.stream().filter(m -> m.name.equals(ENTRY)
                && m.desc.equals(DESCRIPTOR)).toList();
        check(entries.size() == 1 && (entries.get(0).access & Opcodes.ACC_STATIC) == 0 && (entries.get(0).access & Opcodes.ACC_PUBLIC) != 0, "renderer entry point " + DESCRIPTOR);
        MethodNode method = entries.get(0);
        Frame<Origin>[] frames;
        try { frames = new Analyzer<>(new Flow()).analyze(type.name, method); }
        catch (AnalyzerException | RuntimeException e) { throw failure("analyzable health flow", e); }
        int reads = 0, absorptionReads = 0, ordinaryDraws = 0, absorptionDraws = 0, cancel = -1, lastDraw = -1;
        boolean remembers = false;
        Set<String> historyReads = new java.util.HashSet<>();
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode node = method.instructions.get(i);
            Frame<Origin> frame = frames[i];
            if (node instanceof FieldInsnNode field && field.owner.equals(OWNER)) {
                if (field.getOpcode() == Opcodes.GETFIELD) historyReads.add(field.name);
                if (field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("lastHealth") && frame != null)
                    remembers |= top(frame).tags == HEALTH;
            }
            if (!(node instanceof MethodInsnNode call) || frame == null) continue;
            if (healthRead(call)) {
                reads++;
                check(top(frame).tags == CAMERA, "health receiver must originate from the camera");
                check(executable(call.getNext()) instanceof MethodInsnNode ceil && ceil(ceil), "health must use float ceil");
            }
            if (absorptionRead(call)) absorptionReads++;
            if (call.owner.equals(OWNER) && (call.name.equals("renderHearts") || call.name.equals("renderHeartsWithDamage"))) {
                check(call.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V"), "heart drawer argument contract");
                int count = frame.getStack(frame.getStackSize() - 2).tags;
                if (count == HEALTH) {
                    ordinaryDraws++;
                    if (call.name.equals("renderHeartsWithDamage"))
                        check(top(frame).tags == DISPLAY, "remembered-health drawing flow");
                }
                if (count == ABS) absorptionDraws++;
                lastDraw = i;
            }
            if (call.owner.equals(PRE) && call.name.equals("setCanceled") && call.desc.equals("(Z)V")) {
                check(executableBack(call.getPrevious()).getOpcode() == Opcodes.ICONST_1, "native overlay cancellation must be true");
                cancel = i;
            }
        }
        check(reads == 1, "exactly one native Player health read (found " + reads + ")");
        check(remembers, "rounded health must update lastHealth");
        check(historyReads.containsAll(List.of("lastHealth", "displayHealth", "healthBlinkTime")), "history must remain renderer-owned");
        check(ordinaryDraws == 2, "current health must reach both ordinary/damage drawers");
        check(absorptionReads == 1 && absorptionDraws == 1, "independent absorption read/drawing flow");
        check(cancel > lastDraw && lastDraw >= 0, "Forge overlay cancellation after native drawing");
    }

    private static boolean named(MethodInsnNode m, String official, String srg) { return m.name.equals(official) || m.name.equals(srg); }
    private static boolean healthRead(MethodInsnNode m) { return m.owner.equals(PLAYER) && named(m, "getHealth", "m_21223_") && m.desc.equals("()F"); }
    private static boolean absorptionRead(MethodInsnNode m) { return m.owner.equals(PLAYER) && named(m, "getAbsorptionAmount", "m_6103_") && m.desc.equals("()F"); }
    private static boolean ceil(MethodInsnNode m) { return m.owner.equals("net/minecraft/util/Mth") && named(m, "ceil", "m_14167_") && m.desc.equals("(F)I"); }
    private static AbstractInsnNode executable(AbstractInsnNode n) { while (n != null && n.getOpcode() <= Opcodes.NOP) n = n.getNext(); return n; }
    private static AbstractInsnNode executableBack(AbstractInsnNode n) { while (n != null && n.getOpcode() <= Opcodes.NOP) n = n.getPrevious(); return n; }
    private static Origin top(Frame<Origin> f) { return f.getStack(f.getStackSize() - 1); }
    private static void check(boolean pass, String clause) { if (!pass) throw failure(clause, null); }
    public static IllegalStateException failure(String clause, Throwable cause) {
        return new IllegalStateException("Better Death's Door / Mantle compatibility: " + clause
                + "; renderer compatibility review required. Use a supported Mantle release; the fix will not be silently disabled.", cause);
    }

    private record Origin(BasicValue basic, int tags) implements Value {
        @Override public int getSize() { return basic.getSize(); }
    }

    /** Preserve provenance through copies, control flow and arithmetic, never local-variable indices. */
    private static final class Flow extends Interpreter<Origin> {
        private final BasicInterpreter basic = new BasicInterpreter();
        Flow() { super(Opcodes.ASM9); }
        private Origin wrap(BasicValue value, int tags) { return value == null ? null : new Origin(value, tags); }
        @Override public Origin newValue(Type type) { return wrap(basic.newValue(type), 0); }
        @Override public Origin newOperation(AbstractInsnNode insn) throws AnalyzerException {
            int tags = insn instanceof FieldInsnNode f && f.owner.equals("net/minecraft/world/entity/ai/attributes/Attributes")
                    && (f.name.equals("MAX_HEALTH") || f.name.equals("f_22276_")) ? MAXIMUM : 0;
            return wrap(basic.newOperation(insn), tags);
        }
        @Override public Origin copyOperation(AbstractInsnNode insn, Origin v) throws AnalyzerException { return wrap(basic.copyOperation(insn, v.basic), v.tags); }
        @Override public Origin unaryOperation(AbstractInsnNode insn, Origin v) throws AnalyzerException {
            int tags = v.tags;
            if (insn instanceof FieldInsnNode f) tags = f.owner.equals(OWNER) && f.name.equals("displayHealth") ? DISPLAY : 0;
            else if (insn.getOpcode() != Opcodes.CHECKCAST) {
                tags &= ~CAMERA;
                if ((tags & (HEALTH | ABS)) != 0) tags |= CHANGED;
            }
            return wrap(basic.unaryOperation(insn, v.basic), tags);
        }
        @Override public Origin binaryOperation(AbstractInsnNode insn, Origin a, Origin b) throws AnalyzerException {
            int tags = (a.tags | b.tags) & ~CAMERA;
            if ((tags & (HEALTH | ABS)) != 0) tags |= CHANGED;
            return wrap(basic.binaryOperation(insn, a.basic, b.basic), tags);
        }
        @Override public Origin ternaryOperation(AbstractInsnNode insn, Origin a, Origin b, Origin c) throws AnalyzerException { return wrap(basic.ternaryOperation(insn, a.basic, b.basic, c.basic), a.tags | b.tags | c.tags); }
        @Override public Origin naryOperation(AbstractInsnNode insn, List<? extends Origin> args) throws AnalyzerException {
            int tags = args.stream().mapToInt(Origin::tags).reduce(0, (a,b) -> a | b);
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/client/Minecraft") && named(call, "getCameraEntity", "m_91288_")) tags = CAMERA;
                else if (healthRead(call)) tags = HEALTH_FLOAT;
                else if (absorptionRead(call)) tags = ABS_FLOAT;
                else if (ceil(call)) {
                    if (tags == HEALTH_FLOAT) tags = HEALTH;
                    else if (tags == ABS_FLOAT) tags = ABS;
                } else {
                    tags &= ~CAMERA;
                    if ((tags & (HEALTH | ABS)) != 0) tags |= CHANGED;
                }
            }
            return wrap(basic.naryOperation(insn, args.stream().map(Origin::basic).toList()), tags);
        }
        @Override public void returnOperation(AbstractInsnNode insn, Origin value, Origin expected) throws AnalyzerException { basic.returnOperation(insn, value.basic, expected.basic); }
        @Override public Origin merge(Origin a, Origin b) {
            int tags = a.tags | b.tags;
            if (a.tags != b.tags && (tags & (HEALTH | ABS)) != 0) tags |= CHANGED;
            return wrap(basic.merge(a.basic, b.basic), tags);
        }
    }
}
