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

tasks.named<Jar>("jarJar") {
    exclude("better_deaths_door.heart-review.refmap.json")
    finalizedBy("reobfJarJar")
}

tasks.named<Jar>("jar") {
    // MixinGradle adds every source set's refmap to Jar tasks by default.
    exclude("better_deaths_door.heart-review.refmap.json")
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar under its canonical release filename."
    dependsOn(tasks.named("reobfJarJar"))
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
    inputs.files(mantleNative)
    doFirst {
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
    dependsOn(tasks.named("check"))
}

tasks.register("verifyFull") {
    group = "verification"
    dependsOn(tasks.named("verifyFast"), tasks.named("headlessGameTest"))
}
