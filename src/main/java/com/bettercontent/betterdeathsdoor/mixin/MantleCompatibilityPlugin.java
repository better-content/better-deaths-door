package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.compat.MantleRendererContract;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Early loading metadata and ASM only: never resolves Minecraft client classes on a server. */
public final class MantleCompatibilityPlugin implements IMixinConfigPlugin {
    private static final String MIXIN = "com.bettercontent.betterdeathsdoor.mixin.MantleHealthMixin";
    private static final System.Logger LOG = System.getLogger("better_deaths_door.mantle");
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!MIXIN.equals(mixinClassName)) return true;
        if (FMLLoader.getDist() != Dist.CLIENT) return false;
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) throw MantleRendererContract.failure("Forge loading metadata unavailable", null);
        var mantle = mods.getMods().stream().filter(mod -> mod.getModId().equals("mantle")).findFirst();
        String version = mantle.map(mod -> mod.getVersion().toString()).orElse(null);
        boolean apply = admit(true, version, () -> {
            try { return MixinService.getService().getBytecodeProvider().getClassNode(targetClassName, true); }
            catch (Exception e) { throw MantleRendererContract.failure("installed Mantle renderer cannot be read", e); }
        });
        LOG.log(System.Logger.Level.INFO, "Mantle compatibility: {0}; structural admission {1} (not native verification)",
                version == null ? "absent" : version, apply ? "accepted" : "not required");
        return apply;
    }

    /** Pure admission seam: absent/server cases must not even request target bytecode. */
    public static boolean admit(boolean client, String version, Supplier<ClassNode> target) {
        if (!client || version == null) return false;
        MantleRendererContract.validateVersion(version);
        MantleRendererContract.validate(target.get());
        return true;
    }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
        if (MIXIN.equals(mixinClassName)) MantleRendererContract.validate(targetClass);
    }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
}
