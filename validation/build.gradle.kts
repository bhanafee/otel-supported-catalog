plugins {
    base
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Entries whose OTel-supported floor has no stable release on Maven Central yet.
// They are correct against the supported-libraries page but resolve only once the
// upstream stable release ships; treated as warnings, not failures. Remove an alias
// here when its stable version is published.
val unresolvableYet = mapOf(
    "activej.http" to "ActiveJ 6.0+ has only 6.0-rc* prereleases published",
)

// Resolves every library alias in its OWN detached configuration so that distinct
// strict ranges on the SAME module (e.g. couchbase-client2 vs 3, resteasy variants,
// jetty-httpclient9 vs 12) don't conflict with each other — each is validated in
// isolation. Resolution is non-transitive: we only assert that a version satisfying
// the catalog's strict range actually exists and is fetchable.
val validateCatalog by tasks.registering {
    group = "verification"
    description = "Resolves every library in the OTel-supported version catalog."

    doLast {
        val aliases = catalog.libraryAliases
        val failures = mutableListOf<String>()
        var resolved = 0

        val skipped = mutableListOf<String>()

        aliases.forEach { alias ->
            val dep = catalog.findLibrary(alias).get().get()
            try {
                configurations
                    .detachedConfiguration(dependencies.create(dep))
                    .apply {
                        isTransitive = false
                        // Supply standard JVM attributes so libraries published with
                        // Gradle Module Metadata variants (e.g. guava jre/android) can
                        // be selected unambiguously.
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                            attribute(
                                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                                objects.named(TargetJvmEnvironment.STANDARD_JVM),
                            )
                        }
                    }
                    .resolve()
                resolved++
            } catch (e: Exception) {
                val cause = generateSequence(e as Throwable) { it.cause }
                    .map { it.message?.trim() }
                    .firstOrNull { !it.isNullOrBlank() }
                    ?.lineSequence()?.first()
                if (alias in unresolvableYet) {
                    skipped += "  - $alias  ->  ${dep.module}:${dep.versionConstraint}  (${unresolvableYet[alias]})"
                } else {
                    failures += "  - $alias  ->  ${dep.module}:${dep.versionConstraint}\n      $cause"
                }
            }
        }

        logger.lifecycle(
            "Resolved $resolved/${aliases.size} libraries; ${catalog.bundleAliases.size} bundles defined."
        )
        if (skipped.isNotEmpty()) {
            logger.warn("Skipped (no stable release yet):\n" + skipped.joinToString("\n"))
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                "${failures.size} catalog entr${if (failures.size == 1) "y" else "ies"} failed to resolve:\n" +
                    failures.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(validateCatalog)
}
