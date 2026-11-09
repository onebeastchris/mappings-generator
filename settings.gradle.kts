pluginManagement {
    repositories {
        mavenCentral()

        gradlePluginPortal()

        maven("https://maven.fabricmc.net") {
            name = "Fabric"
        }
    }
}

rootProject.name = "mappings-generator"

// DRAFT wiring: Paletter (Bedrock-only palettes with bundled vanilla data, canonical ids and the generated
// BedrockBlocks/BedrockItems constants) validates the generated Bedrock targets and names the constants emitted into
// Geyser's Items.java. It is consumed from a local checkout through a composite build until it is published: Paletter
// must be checked out next to this repository as ../Paletter/Paletter-main. Replace with the published artifact
// (org.geysermc.paletter:paletter in libs.versions.toml) before merging.
includeBuild("../Paletter/Paletter-main") {
    dependencySubstitution {
        substitute(module("org.geysermc.paletter:paletter")).using(project(":paletter"))
    }
}
