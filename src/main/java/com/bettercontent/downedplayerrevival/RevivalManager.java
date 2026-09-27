package com.bettercontent.downedplayerrevival;

import com.bettercontent.downedplayerrevival.api.event.InjuryEvent;
import com.bettercontent.downedplayerrevival.network.RevivalNetwork;
import com.bettercontent.downedplayerrevival.state.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Sole authority for the current body. Every mutation runs on the logical server thread. */
public final class RevivalManager {
    public static final float SENTINEL = .0001f;
    private static final String LEGACY = "downed_player_revival:state";
    private static final String RECAP = "downed_player_revival:recap";
    private static final String FINALIZED = "downed_player_revival:finalized";
    private static final List<Region> TREATMENT_ORDER = List.of(Region.LEFT_ARM, Region.RIGHT_ARM,
        Region.HEAD, Region.TORSO, Region.LEFT_LEG, Region.RIGHT_LEG);
    private static final String HANDS_ON_CARE = "downed_player_revival:care";
    private static final Map<ServerPlayer, BodyState> STATES = new IdentityHashMap<>();
    private static final Set<ServerPlayer> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ServerPlayer> HEALED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ServerPlayer> TERMINATING = Collections.newSetFromMap(new IdentityHashMap<>());
    private record PendingRolledMaim(Region region, MaimType type) {}
    private static final Map<ServerPlayer, PendingRolledMaim> PENDING_ROLLED_MAIMS = new IdentityHashMap<>();
    private static final Map<UUID, Treatment> TREATMENTS = new HashMap<>();
    private static final ThreadLocal<Integer> INTERNAL_WRITE = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> HEAL_CALL = ThreadLocal.withInitial(() -> 0);
    private static final UUID TORSO = UUID.fromString("72f6e692-6ad8-40da-aa2f-491394e8d200");
    private static final UUID LEGS = UUID.fromString("72f6e692-6ad8-40da-aa2f-491394e8d201");
    private static final UUID DAMAGE = UUID.fromString("72f6e692-6ad8-40da-aa2f-491394e8d202");
    private static final UUID REACH = UUID.fromString("72f6e692-6ad8-40da-aa2f-491394e8d203");
    private RevivalManager() {}

    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static boolean eligible(ServerPlayer p) { return !p.isCreative() && !p.isSpectator() && !isFinalized(p); }
    public static BodyState state(ServerPlayer p) {
        return STATES.computeIfAbsent(p, player -> {
            BodyState body = BodyState.load(player.getPersistentData().getCompound(BodyState.ROOT_TAG), now(player));
            if (player.getPersistentData().contains(LEGACY)) {
                body.enterDoor(now(player), RevivalConfig.tuning());
                player.getPersistentData().remove(LEGACY);
                player.setForcedPose(null);
                setHealthInternal(player, SENTINEL);
                player.getPersistentData().put(BodyState.ROOT_TAG, body.save());
            }
            return body;
        });
    }
    public static BodySnapshot snapshot(ServerPlayer p) {
        return BodySnapshot.of(p.getUUID(), p.getHealth(), p.getMaxHealth(), state(p), now(p), RevivalConfig.tuning());
    }
    private static void save(ServerPlayer p) { p.getPersistentData().put(BodyState.ROOT_TAG, state(p).save()); }
    public static boolean isFinalized(ServerPlayer p) { return p.getPersistentData().getBoolean(FINALIZED); }
    public static boolean isTerminating(ServerPlayer p) { return TERMINATING.contains(p); }
    public static void setHealthInternal(ServerPlayer p, float health) {
        INTERNAL_WRITE.set(INTERNAL_WRITE.get() + 1);
        try { p.setHealth(health); } finally { INTERNAL_WRITE.set(INTERNAL_WRITE.get() - 1); }
    }
    public static void beginHeal() { HEAL_CALL.set(HEAL_CALL.get() + 1); }
    public static void endHeal() { HEAL_CALL.set(Math.max(0, HEAL_CALL.get() - 1)); }
    public static float filterHealthWrite(LivingEntity entity, float requested) {
        if (INTERNAL_WRITE.get() > 0 || !(entity instanceof ServerPlayer p) || p.server == null || isFinalized(p)) return requested;
        BodyState body = state(p);
        if (body.atDoor() && requested > p.getHealth()) {
            if (!body.tryHeal(now(p))) return p.getHealth();
            HEALED.add(p);
            save(p);
            return HEAL_CALL.get() > 0 ? Math.max(0, requested - SENTINEL) : requested;
        }
        return requested;
    }
    public static void afterHealthWrite(LivingEntity entity) {
        if (INTERNAL_WRITE.get() > 0 || !(entity instanceof ServerPlayer p) || !HEALED.remove(p)) return;
        refresh(p);
        MinecraftForge.EVENT_BUS.post(new InjuryEvent.Healed(p, snapshot(p)));
    }

    public static void acceptedHit(ServerPlayer p, DamageSource source) {
        if (!eligible(p)) return;
        interruptParticipant(p, "Treatment interrupted: damage taken");
        BodySnapshot before = snapshot(p);
        state(p).addTrauma(now(p), RevivalConfig.tuning());
        DIRTY.add(p);
        BodySnapshot after = snapshot(p);
        if (after.traumaCount() > before.traumaCount())
            MinecraftForge.EVENT_BUS.post(new InjuryEvent.TraumaIncreased(p, before, after, source));
    }
    public static void applyDamageHealth(ServerPlayer p, DamageSource source, float proposed) {
        if (!eligible(p) || bypasses(source)) { setHealthInternal(p, proposed); return; }
        BodyState body = state(p);
        if (!body.atDoor() && proposed > 0) { setHealthInternal(p, proposed); return; }
        Region region = region(p, source);
        MaimType type = type(source);
        var result = body.resolveHit(proposed <= 0, region, type, now(p), p.getRandom()::nextDouble, RevivalConfig.tuning());
        if (result == BodyState.HitResult.FINAL_DEATH) {
            TERMINATING.add(p);
            PENDING_ROLLED_MAIMS.put(p, new PendingRolledMaim(region, type));
            setHealthInternal(p, 0);
            return;
        }
        setHealthInternal(p, SENTINEL);
        save(p); DIRTY.add(p); applyModifiers(p);
        if (result == BodyState.HitResult.ENTERED_DOOR) MinecraftForge.EVENT_BUS.post(new InjuryEvent.EnteredDoor(p, snapshot(p), source));
        Maim maim = body.activeMaims().get(body.activeMaims().size() - 1);
        announceMaim(p, maim);
    }
    private static void announceMaim(ServerPlayer p, Maim maim) {
        MinecraftForge.EVENT_BUS.post(new InjuryEvent.MaimAdded(p, snapshot(p), maim));
        String consequence = switch (maim.region()) {
            case HEAD -> "death risk increased";
            case TORSO -> "maximum HP reduced";
            case LEFT_ARM, RIGHT_ARM -> "melee damage and reach weakened";
            case LEFT_LEG, RIGHT_LEG -> "movement weakened";
        };
        if (p.connection != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal(
            com.bettercontent.downedplayerrevival.network.BodyView.label(maim.type()) + " "
                + maim.region().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                + " — " + consequence), true);
        p.level().playSound(null, p, maim.type() == MaimType.CRACKED ? SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR :
            maim.type() == MaimType.BURNT ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.PLAYER_ATTACK_CRIT,
            SoundSource.PLAYERS, .45f, .7f);
    }
    public static void afterDamage(ServerPlayer p) {
        if (DIRTY.remove(p) && !TERMINATING.contains(p) && !isFinalized(p)) { save(p); refresh(p); }
    }
    /** Catch direct eligible die() calls that did not pass ordinary damage application. */
    public static void interceptedDirectDeath(ServerPlayer p, DamageSource source) {
        acceptedHit(p, source); applyDamageHealth(p, source, 0); afterDamage(p);
    }
    public static boolean bypasses(DamageSource source) {
        String id = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse(source.getMsgId());
        return RevivalConfig.BYPASS_TYPES.get().contains(id) || RevivalConfig.BYPASS_TYPES.get().contains(source.getMsgId())
            || source.is(TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(RevivalMod.MOD_ID, "bypasses_deaths_door")));
    }
    public static Region region(ServerPlayer p, DamageSource source) {
        Vec3 origin = source.getDirectEntity() instanceof LivingEntity attacker
            ? attacker.position().add(0, attacker.getBbHeight() / 2., 0) : source.getSourcePosition();
        Vec3 incoming = source.getDirectEntity() instanceof Projectile projectile && projectile.getDeltaMovement().lengthSqr() > .000001
            ? projectile.getDeltaMovement().scale(-1) : origin != null ? origin.subtract(p.position().add(0, p.getBbHeight() / 2., 0)) : Vec3.ZERO;
        if (incoming.lengthSqr() < .000001 && source.getEntity() != null) incoming = source.getEntity().position().subtract(p.position());
        Vec3 right = new Vec3(-Math.cos(Math.toRadians(p.getYRot())), 0, -Math.sin(Math.toRadians(p.getYRot())));
        double horizontal = incoming.horizontalDistance();
        double side = horizontal <= .000001 ? 0 : incoming.dot(right) / horizontal;
        double elevation = Math.toDegrees(Math.atan2(incoming.y, horizontal));
        return DamageSelection.selectRegion(new DamageSelection.Context(side, elevation, source.is(DamageTypeTags.IS_FALL), source.is(DamageTypeTags.IS_EXPLOSION)), p.getRandom().nextDouble());
    }
    public static MaimType type(DamageSource source) {
        String id = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse(source.getMsgId());
        Map<String, MaimType> overrides = new HashMap<>();
        for (String entry : RevivalConfig.TYPE_OVERRIDES.get()) { int split = entry.lastIndexOf('='); overrides.put(entry.substring(0, split), MaimType.valueOf(entry.substring(split + 1))); }
        Set<String> tags = new HashSet<>();
        if (source.is(DamageTypeTags.IS_FIRE)) tags.add("fire");
        if (source.is(DamageTypeTags.IS_FREEZING)) tags.add("freezing");
        if (source.is(DamageTypeTags.IS_FALL)) tags.add("fall");
        for (String tag : List.of("burnt", "cracked", "opened", "heat", "acid", "corrosion", "freezing", "friction", "blunt", "crushing", "piercing", "cutting", "tear")) {
            if (source.is(TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(RevivalMod.MOD_ID, tag)))) tags.add(tag);
        }
        ItemStack held = source.getEntity() instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
        if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) held = com.bettercontent.downedplayerrevival.compat.EpicInjuryCompat.usedItem(source, held);
        boolean armed = held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem || held.getItem() instanceof TridentItem
            || held.is(itemTag("weapons")) || held.is(TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools/swords")))
            || held.is(TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools/axes")));
        if (held.is(itemTag("blunt_weapons"))) { tags.add("blunt"); armed = true; }
        return DamageSelection.selectType(id, tags, source.is(DamageTypeTags.IS_PROJECTILE), armed, overrides);
    }
    private static TagKey<Item> itemTag(String name) { return TagKey.create(Registries.ITEM, new ResourceLocation(RevivalMod.MOD_ID, name)); }

    public static void refresh(ServerPlayer p) {
        if (isFinalized(p)) return;
        applyModifiers(p);
        if (p.connection != null) RevivalNetwork.sync(p, snapshot(p));
    }
    private static void applyModifiers(ServerPlayer p) {
        BodySnapshot s = snapshot(p);
        modifier(p, Attributes.MAX_HEALTH, TORSO, s.regionalReduction(Region.TORSO));
        modifier(p, Attributes.MOVEMENT_SPEED, LEGS, BodyRules.movementReduction(s.count(Region.LEFT_LEG), s.count(Region.RIGHT_LEG), s.traumaCount(), s.tuning()));
        double arms = s.regionalReduction(Region.LEFT_ARM);
        modifier(p, Attributes.ATTACK_DAMAGE, DAMAGE, arms);
        modifier(p, ForgeMod.ENTITY_REACH.get(), REACH, arms);
        modifier(p, ForgeMod.BLOCK_REACH.get(), REACH, arms);
        if (p.getHealth() > p.getMaxHealth()) setHealthInternal(p, p.getMaxHealth());
    }
    private static void modifier(ServerPlayer p, Attribute attribute, UUID id, double reduction) {
        AttributeInstance instance = p.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier previous = instance.getModifier(id);
        double amount = -Math.min(1, Math.max(0, reduction));
        if (previous != null && previous.getAmount() == amount) return;
        if (previous != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, "Regional maiming", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isFinalized(p)) continue;
            boolean expired = state(p).expireTrauma(now(p));
            if (expired) save(p);
            if (expired || p.tickCount % 5 == 0) refresh(p);
        }
        for (Treatment treatment : List.copyOf(TREATMENTS.values())) tickTreatment(server, treatment);
    }
    public static boolean validTreatmentTarget(ServerPlayer viewer, ServerPlayer subject) {
        return subject != null && viewer.isAlive() && subject.isAlive() && !isFinalized(viewer) && !isFinalized(subject)
            && viewer.level() == subject.level() && (viewer == subject || viewer.distanceToSqr(subject) <= Math.pow(Math.min(RevivalConfig.INTERACTION_DISTANCE.get(), viewer.getAttributeValue(ForgeMod.ENTITY_REACH.get())), 2) && viewer.hasLineOfSight(subject));
    }
    public static void openBody(ServerPlayer viewer, ServerPlayer subject) {
        if (!validTreatmentTarget(viewer, subject)) return;
        if (!RevivalNetwork.isViewing(viewer, subject)) cancelTreatment(viewer, "");
        RevivalNetwork.sendBody(viewer, subject, snapshot(subject));
    }
    public static void closeBody(ServerPlayer viewer) {
        cancelTreatment(viewer, "Treatment canceled"); RevivalNetwork.closeBody(viewer);
    }
    public static List<Region> treatmentOrder() { return TREATMENT_ORDER; }
    public static boolean isTreating(ServerPlayer healer, ServerPlayer subject) {
        Treatment treatment = TREATMENTS.get(healer.getUUID());
        return treatment != null && treatment.subject.equals(subject.getUUID());
    }
    public static void startTreatment(ServerPlayer viewer, ServerPlayer subject) {
        if (!validTreatmentTarget(viewer, subject) || !RevivalNetwork.isViewing(viewer, subject)) return;
        if (TREATMENTS.containsKey(viewer.getUUID())) return;
        Maim maim = nextMaim(viewer, subject);
        if (maim == null) { RevivalNetwork.treatmentStatus(viewer, subject, 0, "No active injuries"); return; }
        TREATMENTS.put(viewer.getUUID(), new Treatment(viewer.getUUID(), subject.getUUID(), maim));
        RevivalNetwork.treatmentStatus(viewer, subject, 0, "Treatment started");
        RevivalNetwork.refreshViewing(viewer, subject);
    }
    public static void cancelTreatment(ServerPlayer viewer, String reason) {
        Treatment previous = TREATMENTS.remove(viewer.getUUID());
        if (previous != null && viewer.connection != null) {
            ServerPlayer target = viewer.server.getPlayerList().getPlayer(previous.subject);
            RevivalNetwork.treatmentStatus(viewer, target == null ? viewer : target, 0, reason);
            if (target != null) RevivalNetwork.refreshViewing(viewer, target);
        }
    }
    private static void interruptParticipant(ServerPlayer p, String reason) {
        for (Treatment treatment : List.copyOf(TREATMENTS.values())) {
            if (treatment.healer.equals(p.getUUID()) || treatment.subject.equals(p.getUUID())) {
                ServerPlayer healer = p.server.getPlayerList().getPlayer(treatment.healer);
                if (healer != null) cancelTreatment(healer, reason); else TREATMENTS.remove(treatment.healer);
            }
        }
    }
    private static Maim nextMaim(ServerPlayer healer, ServerPlayer subject) {
        List<Region> order = treatmentOrder();
        return state(subject).activeMaims().stream().min(Comparator
            .comparingInt((Maim m) -> order.indexOf(m.region()))
            .thenComparingLong(Maim::tick).thenComparingLong(Maim::id)).orElse(null);
    }
    private static void tickTreatment(MinecraftServer server, Treatment treatment) {
        ServerPlayer healer = server.getPlayerList().getPlayer(treatment.healer), subject = server.getPlayerList().getPlayer(treatment.subject);
        if (healer == null) { TREATMENTS.remove(treatment.healer); return; }
        if (!validTreatmentTarget(healer, subject) || !RevivalNetwork.isViewing(healer, subject)) { cancelTreatment(healer, "Treatment interrupted: target out of reach"); return; }
        boolean stillPresent = state(subject).activeMaims().stream().anyMatch(m -> m.id() == treatment.maimId);
        if (!stillPresent) {
            Maim next = nextMaim(healer, subject);
            if (next == null) { cancelTreatment(healer, "Treatment complete"); return; }
            treatment.select(next);
        }
        treatment.progress += 1 / (20 * snapshot(healer).treatmentSeconds());
        if (treatment.progress < 1) {
            if (healer.tickCount % 2 == 0) RevivalNetwork.treatmentStatus(healer, subject, (float) treatment.progress, "Applying treatment");
            return;
        }
        TreatmentRecord record = state(subject).cureOldest(treatment.region, treatment.type, HANDS_ON_CARE, now(subject)).orElseThrow();
        save(subject); refresh(subject);
        subject.level().playSound(null, subject, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, .65f, .85f);
        MinecraftForge.EVENT_BUS.post(new InjuryEvent.Treated(subject, snapshot(subject), healer, record));
        Maim next = nextMaim(healer, subject);
        if (next == null) cancelTreatment(healer, "Treatment complete");
        else { treatment.select(next); RevivalNetwork.treatmentStatus(healer, subject, 0, "Continuing treatment"); }
    }
    private static final class Treatment {
        final UUID healer, subject; long maimId; Region region; MaimType type; double progress;
        Treatment(UUID healer, UUID subject, Maim maim) { this.healer = healer; this.subject = subject; select(maim); }
        void select(Maim maim) { maimId = maim.id(); region = maim.region(); type = maim.type(); progress = 0; }
    }

    public static void finalDeath(ServerPlayer p, DamageSource source) {
        if (isFinalized(p)) return;
        BodySnapshot recap = snapshot(p);
        CompoundTag saved = new CompoundTag(); saved.put("body", state(p).save()); saved.putLong("tick", recap.serverTick());
        saved.putFloat("maxHealth", recap.maxHealth()); saved.put("tuning", saveTuning(recap.tuning()));
        saved.put("damage", DamageLedger.snapshot(p).save());
        boolean failedProc = TERMINATING.contains(p);
        p.getPersistentData().put(RECAP, saved);
        p.getPersistentData().putBoolean(FINALIZED, true);
        interruptParticipant(p, "Treatment interrupted: life ended");
        state(p).clear(); p.getPersistentData().remove(BodyState.ROOT_TAG);
        DIRTY.remove(p); HEALED.remove(p); TERMINATING.remove(p); PENDING_ROLLED_MAIMS.remove(p);
        if (p.connection != null) {
            RevivalNetwork.sync(p, snapshot(p));
            RevivalNetwork.sendRecap(p, recap, DamageLedger.Summary.load(saved.getCompound("damage")));
            if (failedProc) RevivalNetwork.send(p, new com.bettercontent.downedplayerrevival.network.UiControlPacket("failed-proc", "", 0, 0));
        }
        MinecraftForge.EVENT_BUS.post(new InjuryEvent.FinalDeath(p, recap, source));
    }
    public static void canceledDeath(ServerPlayer p) {
        TERMINATING.remove(p);
        PendingRolledMaim pending = PENDING_ROLLED_MAIMS.remove(p);
        if (state(p).atDoor() && p.getHealth() <= 0) setHealthInternal(p, SENTINEL);
        if (pending != null && !isFinalized(p)) {
            // A canceled final-death event survived this accepted hit: its consequence is still guaranteed.
            Maim maim = state(p).addMaim(pending.region(), pending.type(), now(p));
            DIRTY.remove(p);
            save(p);
            refresh(p);
            announceMaim(p, maim);
        }
    }
    public static void sendRecap(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData().getCompound(RECAP);
        if (tag.isEmpty()) return;
        long tick = tag.getLong("tick");
        RevivalNetwork.sendRecap(p, BodySnapshot.of(p.getUUID(), 0, tag.getFloat("maxHealth"), BodyState.load(tag.getCompound("body"), tick), tick, loadTuning(tag.getCompound("tuning"))), DamageLedger.Summary.load(tag.getCompound("damage")));
    }
    private static CompoundTag saveTuning(BodyTuning t) {
        CompoundTag tag = new CompoundTag();
        double[] values = {t.globalDeathPerMaim(), t.headDeathPerMaim(), t.restingFunctionalMultiplier(), t.traumaIncrement(),
            t.torsoLimit(), t.torsoHalfSaturation(), t.legLimit(), t.legHalfSaturation(), t.armIncrement(), t.treatmentBaseSeconds(), t.treatmentArmSeconds()};
        for (int i = 0; i < values.length; i++) tag.putDouble("v" + i, values[i]);
        tag.putInt("healing", t.healingLockTicks()); tag.putInt("trauma", t.traumaLifetimeTicks()); return tag;
    }
    private static BodyTuning loadTuning(CompoundTag tag) {
        if (tag.isEmpty()) return BodyTuning.DEFAULT;
        return new BodyTuning(tag.getDouble("v0"), tag.getDouble("v1"), tag.getDouble("v2"), tag.getDouble("v3"),
            tag.getDouble("v4"), tag.getDouble("v5"), tag.getDouble("v6"), tag.getDouble("v7"), tag.getDouble("v8"),
            tag.getDouble("v9"), tag.getDouble("v10"), tag.getInt("healing"), tag.getInt("trauma"));
    }
    public static void login(ServerPlayer p) { refresh(p); if (isFinalized(p)) sendRecap(p); }
    public static void logout(ServerPlayer p) {
        interruptParticipant(p, "Treatment interrupted: player disconnected"); RevivalNetwork.closeBody(p);
        if (!isFinalized(p)) save(p);
        STATES.remove(p); DIRTY.remove(p); HEALED.remove(p); TERMINATING.remove(p); PENDING_ROLLED_MAIMS.remove(p);
    }
    public static void clonePlayer(ServerPlayer old, ServerPlayer replacement, boolean death) {
        STATES.remove(replacement);
        DamageLedger.cloneLife(old, replacement, death);
        replacement.getPersistentData().remove(RECAP); replacement.getPersistentData().remove(FINALIZED);
        if (death) { replacement.getPersistentData().remove(BodyState.ROOT_TAG); STATES.put(replacement, new BodyState()); }
        else { STATES.put(replacement, state(old).copy()); save(replacement); }
        STATES.remove(old); TERMINATING.remove(old); PENDING_ROLLED_MAIMS.remove(old); DIRTY.remove(old); HEALED.remove(old);
        refresh(replacement);
    }
    public static void shutdown() { STATES.clear(); DIRTY.clear(); HEALED.clear(); TERMINATING.clear(); PENDING_ROLLED_MAIMS.clear(); TREATMENTS.clear(); RevivalNetwork.clear(); }

    /** Explicit admin fixture command; callers restrict this to isolated review players. */
    public static void debugScenario(ServerPlayer p, String fixture) {
        fixture = fixture.replace('_', '-');
        if (!Set.of("healthy", "mixed", "severe", "long-history", "door", "healing-lock", "trauma-expiry", "final-death").contains(fixture)) throw new IllegalArgumentException("Unknown injury fixture");
        if (fixture.equals("final-death")) { p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE); return; }
        interruptParticipant(p, "Review scenario reset");
        p.getPersistentData().remove(FINALIZED); p.getPersistentData().remove(RECAP);
        state(p).clear(); TERMINATING.remove(p); PENDING_ROLLED_MAIMS.remove(p);
        if (!fixture.equals("healthy")) {
            for (Region region : Region.values()) {
                int count = fixture.equals("severe") ? 4 : 1;
                for (int i = 0; i < count; i++) state(p).addMaim(region, MaimType.values()[region.ordinal() % 3], now(p));
            }
            if (fixture.equals("long-history")) {
                for (int i = 0; i < 120; i++) { state(p).addMaim(Region.LEFT_LEG, MaimType.CRACKED, now(p)); state(p).cureOldest(Region.LEFT_LEG, MaimType.CRACKED, "minecraft:stick", now(p)); }
            }
            for (int i = 0; i < 5; i++) state(p).addTrauma(fixture.equals("trauma-expiry") ? now(p) - RevivalConfig.tuning().traumaLifetimeTicks() + 20L * (i + 1) : now(p), RevivalConfig.tuning());
        }
        if (fixture.equals("door") || fixture.equals("healing-lock") || fixture.equals("severe")) { state(p).enterDoor(now(p), RevivalConfig.tuning()); setHealthInternal(p, SENTINEL); }
        else { applyModifiers(p); setHealthInternal(p, p.getMaxHealth()); }
        save(p); refresh(p);
    }
}
