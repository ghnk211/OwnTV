pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // A libmpv built on this PC (OwnTV_libmpv's buildscripts/wsl_build.sh), only while
        // the build command carries -Powntv.libmpvLocalRepo=<folder> (never a file) — core's :player-core then asks
        // for version "local". Core's settings carry the same lines.
        providers.gradleProperty("owntv.libmpvLocalRepo").orNull?.let { dir ->
            maven {
                name = "LocalLibmpv"
                url = uri(file(dir))
                content { includeVersion("tv.own.owntv", "libmpv", "local") }
            }
        }
        google()
        mavenCentral()
        // OwnTV's own Maven repository — public, no login: tv.own.owntv:core and :player-core
        // (built from https://github.com/ahXN00/OwnTV_Core) and tv.own.owntv:libmpv, the mpv engine
        // (https://github.com/ahXN00/OwnTV_libmpv). Served from OwnTV_Core's gh-pages branch.
        maven {
            name = "OwnTV"
            url = uri("https://ahxn00.github.io/OwnTV_Core/maven")
            content { includeGroup("tv.own.owntv") }
        }
    }
}

// The TV app builds against the shared Core SOURCE by default — no command-line property needed.
// Gradle substitutes tv.own.owntv:core / :player-core with the included build's projects, so the
// version pinned in libs.versions.toml is irrelevant on this path (see
// docs/alignment-to-own-project-zh.md §13). The sibling checkout ../Qwn/OwnTV_Core is the one
// source of truth; Sports was removed from the app because that Core no longer carries its API.
//
// Overrides, for machines without that checkout:
//   -Powntv.corePath=<dir>       use a Core checkout somewhere else
//   -Powntv.coreArtifact=true    keep the published-artifact path (CI uses this; it needs a
//                                published Core matching the app source before it can compile)
val coreArtifactOnly = providers.gradleProperty("owntv.coreArtifact").orNull == "true"
val explicitCorePath = providers.gradleProperty("owntv.corePath").orNull?.takeIf { it.isNotBlank() }
val defaultCoreDir = settingsDir.parentFile?.resolve("Qwn/OwnTV_Core")
val coreBuildDir = when {
    coreArtifactOnly -> null
    explicitCorePath != null -> file(explicitCorePath)
    defaultCoreDir?.isDirectory == true -> defaultCoreDir
    else -> {
        logger.warn(
            "OwnTV: ../Qwn/OwnTV_Core not found; falling back to the published Core artifact. " +
                "This app source needs the Core source build — pass -Powntv.corePath=<Core checkout>.",
        )
        null
    }
}
coreBuildDir?.let { includeBuild(it) }

rootProject.name = "OwnTV"
include(":app")
// Baseline-profile generator. Test-only module: it ships nothing to users, it records
// the cold-start journey on a device and writes the profile :app packages.
include(":baselineprofile")
 