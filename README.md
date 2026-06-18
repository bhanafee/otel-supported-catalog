# OpenTelemetry-supported libraries — Gradle version catalog

`gradle/libs.versions.toml` is a [Gradle version catalog](https://docs.gradle.org/current/userguide/platforms.html)
that lists every library and framework the **OpenTelemetry Java agent** can
auto-instrument (zero-code), with each version constrained to the range the agent
actually supports.

Source of truth:
<https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/>

## What problem it solves

When you run your app with the OTel Java agent (`-javaagent:opentelemetry-javaagent.jar`),
instrumentation only kicks in for libraries within supported version ranges. Upgrade a
framework past the supported window and you silently lose spans/metrics for it.

This catalog turns that silent gap into a **build-time failure**. Every entry uses a
Gradle [`strictly`](https://docs.gradle.org/current/userguide/rich_versions.html)
version constraint:

```toml
spring-webmvc = { strictly = "[3.1,)" }      # Spring Web MVC 3.1+
resteasy      = { strictly = "[3.0, 6.0[" }  # RESTEasy 3.0+ (4.0[ = jakarta cutoff, excluded)
```

If anything in your dependency graph tries to resolve a version outside that range,
the build fails instead of producing an app the agent can't fully observe.

## How to use it

### 1. Wire the catalog into your build

If this file lives in your project's `gradle/` directory, Gradle picks it up
automatically as the `libs` catalog — nothing to configure.

To consume it from **another** project, point `settings.gradle(.kts)` at it:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    versionCatalogs {
        create("otel") {
            from(files("../otel/gradle/libs.versions.toml"))
        }
    }
}
```

(Or publish it as a catalog artifact and `from("com.example:otel-catalog:<version>")`.)

### 2. Declare dependencies through the catalog

Reference individual libraries:

```kotlin
dependencies {
    implementation(libs.spring.webmvc)
    implementation(libs.hikaricp)
    runtimeOnly(libs.logback.classic)
}
```

Or pull a whole themed group via a bundle:

```kotlin
dependencies {
    implementation(libs.bundles.spring)      // webmvc, webflux, data, context, batch, …
    implementation(libs.bundles.jdbc.pools)  // hikaricp, c3p0, dbcp2, druid, ucp, vibur, tomcat-jdbc
}
```

Available bundles: `http-clients`, `spring`, `spring-messaging`, `messaging`,
`jdbc-pools`, `nosql`, `search`, `reactive`, `aws`, `logging`, `metrics`.

### 3. Enforce the constraint even on transitive versions

`strictly` already fails the build on a direct out-of-range version. To also catch a
**transitive** dependency dragging a framework out of range, apply the catalog version
as a constraint:

```kotlin
dependencies {
    constraints {
        implementation(libs.spring.webmvc)   // forces the supported range graph-wide
    }
}
```

If two parts of the graph demand mutually incompatible strict versions, Gradle reports
a resolution conflict — which is exactly the signal you want: something wants a version
the agent can't instrument.

### 4. Verify

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew build
```

A clean resolution means every framework on the classpath sits inside an
OTel-instrumentable range.

## Reading the version ranges

| Page wording | Catalog encoding | Meaning |
|---|---|---|
| `X+` | `strictly = "[X,)"` | X or newer |
| `X+ (not including Y+)` | `strictly = "[X, Y["` | X up to, but excluding, Y |
| `A-B.x` | `strictly = "[A, B+1["` | closed window |

The `(not including Y+)` upper bounds are almost always the **javax → jakarta**
cutoff: the version where the library moved to the `jakarta.*` namespace and the agent
dropped support. These are encoded for CXF JAX-RS/JAX-WS, JAX-WS, RESTEasy, MyFaces,
Mojarra, and Spring Web Services.

## javax vs jakarta

Spec/API entries use the namespace the OTel page actually links to:

- `servlet-api` → `javax.servlet:javax.servlet-api`
- `jms-api` → `javax.jms:javax.jms-api`
- `jaxws-api` → `javax.xml.ws:jaxws-api`

Libraries that support **both** namespaces (e.g. Spring Web MVC via `webmvc-5.3` and
`webmvc-6.0`) are left open-ended — no cutoff.

Where a framework ships multiple major lines, both are available as separate aliases,
e.g. `apache-httpclient4` / `apache-httpclient5`, `cassandra-driver3` / `cassandra-driver4`,
`couchbase-client2` / `couchbase-client3`, `jetty-httpclient9` / `jetty-httpclient12`,
`rxjava1`/`rxjava2`/`rxjava3`, `aws-sdk1-core` / `aws-sdk2-core`. Bundles include only
the modern member to avoid pulling conflicting versions into one resolution.

## Not included (instrumented without a dependency)

JDK- and spec-level instrumentations need no catalog entry — the agent instruments them
in place: HttpURLConnection, Java HTTP Client/Server, Java Executors, `java.util.logging`,
the Java Platform runtime, and JDBC (bring your own driver).

## Keeping it current

The supported-libraries list changes with every agent release; this catalog reflects the
page as of **2026-06-18**. When you upgrade the agent, refresh the catalog against the
page. Note the published page is lossy through HTML-to-markdown converters — parse the raw
HTML to get exact versions and the namespace each entry links to:

```bash
curl -s "https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/" -o /tmp/otel.html
# parse <tr>/<td> cells for versions; inspect <a href=…> for the javax/jakarta namespace
```
