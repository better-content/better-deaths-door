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

// Actual Minecraft client review, excluded from the deployed runtime JAR.
val injuryVisual by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[injuryVisual.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[injuryVisual.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true

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
            workingDirectory(project.file("build/injury-visual"))
            args("--width", providers.gradleProperty("injuryVisualWidth").orElse("1280").get(),
                "--height", providers.gradleProperty("injuryVisualHeight").orElse("720").get())
            mods { getByName(property("mod_id") as String).source(injuryVisual) }
        }
        create("injuryHelper") {
            parent(baseClient)
            workingDirectory(project.file("build/injury-helper"))
            args("--username", "InjuryHelper", "--width", "1280", "--height", "720")
            property("injury.review.helper", "true")
            mods { getByName(property("mod_id") as String).source(injuryVisual) }
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
    mavenCentral()
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    compileOnly(files(survivalHudJar))
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(fg.deobf("curse.maven:epic-fight-mod-405076:8049910"))
    // Opt-in repository-local compatibility verification; not a pack test or deployment.
    if (providers.gradleProperty("injuryEpicTests").orNull == "true") {
        runtimeOnly(fg.deobf("curse.maven:epic-fight-mod-405076:8049910"))
    }
    if (providers.gradleProperty("injuryVisualHud").orNull != "false") {
        add(injuryVisual.runtimeOnlyConfigurationName, fg.deobf("bettercontent.visual:better-survival-hud:1.0.0"))
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

mixin {
    add(sourceSets.main.get(), "better_deaths_door.refmap.json")
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

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar under its canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") { dependsOn(stageRuntimeJar) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.test { useJUnitPlatform() }

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
