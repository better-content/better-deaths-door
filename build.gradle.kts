import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import java.util.jar.Manifest as JarManifest

plugins {
    idea
    jacoco
    `maven-publish`
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
}

group = property("mod_group_id") as String
version = property("mod_version") as String

base {
    archivesName.set("better-deaths-door")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

// Exact pack Mantle input, kept raw for the bytecode/SHA-1 contract.
val mantleNative by configurations.creating { isTransitive = false }
val epicNative by configurations.creating { isTransitive = false }
val mantleCoordinate = "curse.maven:mantle-74924:7563777"
val heartProfile = providers.gradleProperty("injuryHeartProfile").orNull
require(heartProfile == null || heartProfile in setOf("WITH_MAX", "NO_MAX", "DISABLE", "absent")) {
    "injuryHeartProfile must be WITH_MAX, NO_MAX, DISABLE or absent"
}

// Pin local compilation/review resolution too; JarJar's compatibility metadata remains ranged.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.github.llamalad7" && requested.name == "mixinextras-forge") useVersion("0.5.4")
    }
}

// Actual Minecraft client review, excluded from the deployed runtime JAR.
val injuryVisual by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[injuryVisual.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[injuryVisual.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[injuryVisual.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true
    jarJar.enable()

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "info")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
            mods {
                create(property("mod_id") as String) {
                    source(sourceSets.main.get())
                }
            }
        }
        val baseClient = create("client")
        create("injuryVisual") {
            parent(baseClient)
            workingDirectory(project.file(if (heartProfile == null) "build/injury-visual" else "build/heart-review-$heartProfile"))
            if (heartProfile != null) {
                property("injury.review.hearts", heartProfile)
                args("--username", "HeartReview", "--mixin.config", "better_deaths_door.heart-review.mixins.json")
            }
            args("--width", providers.gradleProperty("injuryVisualWidth").orElse("1280").get(),
                "--height", providers.gradleProperty("injuryVisualHeight").orElse("720").get())
            mods { create("better_deaths_door_review_support") { source(injuryVisual) } }
        }
        create("injuryHelper") {
            parent(baseClient)
            workingDirectory(project.file("build/injury-helper"))
            args("--username", "InjuryHelper", "--width", "1280", "--height", "720")
            property("injury.review.helper", "true")
            mods { create("better_deaths_door_review_support") { source(injuryVisual) } }
        }
        create("server") { arg("--nogui") }
        create("gameTestServer")
    }
}

// Release builds supply the canonical staged provider; local builds use its sibling checkout.
val providerDirectory = providers.environmentVariable("BC_CUSTOM_MOD_JAR_DIR").orNull
require(providerDirectory == null || providerDirectory.isNotBlank()) { "BC_CUSTOM_MOD_JAR_DIR must not be blank" }
val survivalHudJar = if (providerDirectory == null) file("../better-survival-hud/build/libs/better-survival-hud-1.0.0.jar")
    else file(providerDirectory).resolve("better-survival-hud-1.0.0.jar")
require(survivalHudJar.isFile) { "Missing provider $survivalHudJar; stage better-survival-hud or set BC_CUSTOM_MOD_JAR_DIR" }
val noticeJar = if (providerDirectory == null) file("../better-gameplay-notices/build/libs/better-gameplay-notices-1.0.0.jar")
    else file(providerDirectory).resolve("better-gameplay-notices-1.0.0.jar")
require(noticeJar.isFile) { "Missing provider $noticeJar" }

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://www.cursemaven.com")
    ivy {
        name = "injuryVisualHud"
        url = survivalHudJar.parentFile.toURI()
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.visual") }
    }
    ivy {
        name = "injuryVisualNotices"
        url = noticeJar.parentFile.toURI()
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.notices") }
    }
    mavenCentral()
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    compileOnly(files(survivalHudJar))
    compileOnly(files(noticeJar))
    add(injuryVisual.runtimeOnlyConfigurationName,
        fg.deobf("bettercontent.notices:better-gameplay-notices:1.0.0"))
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")!!)
    val extras = implementation(jarJar("io.github.llamalad7:mixinextras-forge:[0.5.4,0.6.0)")!!)!!
    jarJar.pin(extras, "0.5.4")
    add(injuryVisual.annotationProcessorConfigurationName, "org.spongepowered:mixin:0.8.5:processor")
    add(injuryVisual.annotationProcessorConfigurationName, "io.github.llamalad7:mixinextras-common:0.5.4")
    compileOnly(fg.deobf("curse.maven:epic-fight-mod-405076:8049910"))
    // Opt-in repository-local compatibility verification; not a pack test or deployment.
    if (providers.gradleProperty("injuryEpicTests").orNull == "true") {
        runtimeOnly(fg.deobf("curse.maven:epic-fight-mod-405076:8049910"))
    }
    if (providers.gradleProperty("injuryVisualHud").orNull != "false") {
        add(injuryVisual.runtimeOnlyConfigurationName, fg.deobf("bettercontent.visual:better-survival-hud:1.0.0"))
    }
    add(epicNative.name, "curse.maven:epic-fight-mod-405076:8049910")
    add(mantleNative.name, mantleCoordinate)
    if (heartProfile != null && heartProfile != "absent") {
        add(injuryVisual.runtimeOnlyConfigurationName, fg.deobf(mantleCoordinate))
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.ow2.asm:asm-tree:9.7.1")
    testImplementation("org.ow2.asm:asm-analysis:9.7.1")
}

mixin {
    add(sourceSets.main.get(), "better_deaths_door.refmap.json")
    add(injuryVisual, "better_deaths_door.heart-review.refmap.json")
    config("better_deaths_door.mixins.json")
}

tasks.processResources {
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}

val generatedRefmap = layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")
tasks.named<JavaCompile>("compileJava") {
    outputs.file(generatedRefmap)
    outputs.file(layout.buildDirectory.file("tmp/compileJava/compileJava-mappings.tsrg"))
    options.isIncremental = false
}

tasks.named<Jar>("jarJar") {
    dependsOn(tasks.named("compileJava"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(generatedRefmap) { rename { "better_deaths_door.refmap.json" } }
    exclude("better_deaths_door.heart-review.refmap.json")
    finalizedBy("reobfJarJar")
}

tasks.named<Jar>("jar") {
    dependsOn(tasks.named("compileJava"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(generatedRefmap) { rename { "better_deaths_door.refmap.json" } }
    // MixinGradle adds every source set's refmap to Jar tasks by default.
    exclude("better_deaths_door.heart-review.refmap.json")
    finalizedBy("reobfJar")
}

val verifyRuntimeRefmap by tasks.registering {
    group = "verification"
    description = "Validates all production Minecraft references in the final JarJar artifact."
    dependsOn(tasks.named("reobfJarJar"))
    val runtimeJar = layout.buildDirectory.file("reobfJarJar/output.jar")
    inputs.file(runtimeJar)
    doLast {
        val expected = mapOf(
            "ToastClockMixin" to mapOf(
                "net.minecraft.client.gui.components.toasts.ToastComponent\$ToastInstance" to "net/minecraft/client/gui/components/toasts/ToastComponent\$ToastInstance",
                "Lnet/minecraft/Util;getMillis()J" to "m_137550_", "render" to "m_280442_"),
            "ServerDeathMixin" to mapOf("die" to "m_6667_"),
            "DeathCauseAccessor" to mapOf("causeOfDeath" to "f_95907_"),
            "ToastVisibilityMixin" to mapOf("render" to "m_94920_"),
            "PlayerDamageMixin" to mapOf("actuallyHurt" to "m_6475_",
                "Lnet/minecraft/world/entity/player/Player;setHealth(F)V" to "m_21153_"),
            "MantleHealthMixin" to mapOf("Lnet/minecraft/world/entity/player/Player;getHealth()F" to "m_21223_"),
            "LivingHealthMixin" to mapOf("heal" to "m_5634_", "setHealth" to "m_21153_"),
            "PlayerMeleeBonusMixin" to mapOf("attack" to "m_5706_",
                "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageBonus(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/MobType;)F" to "m_44833_",
                "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z" to "m_6469_")
        )
        val unmappedNative = setOf("EpicColliderMixin", "EpicPhaseMixin", "EpicReachMixin", "ForgeHealthMixin")
        val namespace = "com.bettercontent.betterdeathsdoor.mixin"
        ZipFile(runtimeJar.get().asFile).use { zip ->
            fun json(name: String): Map<*, *> {
                val entry = zip.getEntry(name) ?: throw GradleException("Runtime JAR is missing $name")
                return zip.getInputStream(entry).reader().use { JsonSlurper().parse(it) } as Map<*, *>
            }
            val config = json("better_deaths_door.mixins.json")
            check(config["required"] == true && config["package"] == namespace
                    && config["refmap"] == "better_deaths_door.refmap.json"
                    && config["plugin"] == "$namespace.MantleCompatibilityPlugin") { "Invalid runtime mixin/refmap metadata" }
            val registered = listOf("mixins", "client", "server").flatMap { (config[it] as? List<*>) ?: emptyList<Any>() }
            check(registered.size == registered.toSet().size && registered.toSet() == expected.keys + unmappedNative) {
                "Registered mixins must all be covered by the runtime refmap contract: $registered"
            }
            val prefix = namespace.replace('.', '/') + "/"
            registered.forEach { check(zip.getEntry(prefix + it + ".class") != null) { "Registered runtime mixin $it is missing" } }
            check(zip.getEntry("better_deaths_door.heart-review.refmap.json") == null) { "Review-only refmap leaked into production" }
            val manifest = zip.getEntry("META-INF/MANIFEST.MF") ?: throw GradleException("Runtime manifest is missing")
            val configs = zip.getInputStream(manifest).use { JarManifest(it) }.mainAttributes.getValue("MixinConfigs")
            check(configs?.split(',')?.map(String::trim)?.contains("better_deaths_door.mixins.json") == true)
            val refmap = json("better_deaths_door.refmap.json")
            val data = refmap["data"] as? Map<*, *> ?: throw GradleException("Runtime refmap has no environments")
            check(refmap["mappings"] == data["searge"]) { "Default and production searge mappings must agree" }
            for (table in listOf(refmap["mappings"], data["searge"])) {
                val types = table as? Map<*, *> ?: throw GradleException("Runtime refmap lacks default/searge mappings")
                check(types.keys == expected.keys.map { prefix + it }.toSet()) { "Runtime refmap coverage is incomplete/stale" }
                expected.forEach { (name, selectors) ->
                    val mappings = types[prefix + name] as? Map<*, *> ?: throw GradleException("Missing $name mappings")
                    check(mappings.keys == selectors.keys) { "Incomplete/stale $name reference selectors: $mappings" }
                    selectors.forEach { (selector, alias) ->
                        val mapped = mappings[selector] as? String ?: throw GradleException("Missing $name.$selector")
                        val reference = if (mapped.startsWith("L")) mapped.substringAfter(';') else mapped
                        val member = reference.substringBefore('(').substringBefore(':')
                        check(if (alias.startsWith("net/")) mapped == alias else member == alias) {
                            "Incorrect production alias for $name.$selector: $mapped"
                        }
                        if ('(' in selector) check(mapped.substringAfter('(') == selector.substringAfter('(')) {
                            "Incorrect production descriptor for $name.$selector: $mapped"
                        }
                    }
                }
            }
        }
    }
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar under its canonical release filename."
    dependsOn(verifyRuntimeRefmap)
    from(layout.buildDirectory.file("reobfJarJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

// Independently reobfuscated support mod for stock-Forge production launch checks.
val heartReviewSupportJar by tasks.registering(Jar::class) {
    destinationDirectory.set(layout.buildDirectory.dir("heart-review-support-dev"))
    exclude("better_deaths_door.refmap.json")
    from(injuryVisual.output)
    archiveClassifier.set("heart-review-support")
    manifest.attributes["MixinConfigs"] = "better_deaths_door.heart-review.mixins.json"
}
reobf { create("heartReviewSupportJar") }
val stageHeartReviewSupportJar by tasks.registering(Copy::class) {
    dependsOn(tasks.named("reobfHeartReviewSupportJar"))
    from(layout.buildDirectory.file("reobfHeartReviewSupportJar/output.jar"))
    into(layout.buildDirectory.dir("heart-review-control"))
    rename { "better-deaths-door-heart-review-support.jar" }
}

tasks.named("assemble") { dependsOn(stageRuntimeJar) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.test {
    useJUnitPlatform()
    inputs.files(mantleNative, epicNative)
    doFirst {
        systemProperty("injury.epic.native", epicNative.singleFile.absolutePath)
        systemProperty("injury.mantle.native", mantleNative.singleFile.absolutePath)
        systemProperty("injury.main.refmap", layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json").get().asFile.absolutePath)
    }
}

// Prepare only the opt-in isolated review configuration, never the pack configuration.
tasks.matching { it.name == "prepareRunInjuryVisual" }.configureEach {
    if (heartProfile != null) dependsOn(tasks.test)
    doLast {
        if (heartProfile != null && heartProfile != "absent") {
            val config = layout.buildDirectory.file("heart-review-$heartProfile/config/mantle-client.toml").get().asFile
            config.parentFile.mkdirs()
            config.writeText("heartRenderer = \"$heartProfile\"\n")
        }
    }
}

tasks.register("headlessGameTest") {
    group = "verification"
    dependsOn(tasks.named("runGameTestServer"))
}

val syncGameTestStructures by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(layout.projectDirectory.dir("run/gameteststructures"))
}

tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach {
    dependsOn(syncGameTestStructures)
}

tasks.register("verifyFast") {
    group = "verification"
    dependsOn(tasks.named("check"), verifyRuntimeRefmap)
}

tasks.register("verifyFull") {
    group = "verification"
    dependsOn(tasks.named("verifyFast"), tasks.named("headlessGameTest"))
}
