// Standalone harness that exercises real dependency resolution of every entry in
// the OTel-supported version catalog. It references the catalog one level up.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "catalog-validation"
