import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatformSettings)
            // Used to call libobjc on macOS to set NSWindow level/collectionBehavior
            // so the picker overlays fullscreen apps. Harmless on Win/Linux —
            // the helper is no-op there.
            implementation(libs.jna)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val composePackageName = "Link Opener"
// Single source of truth: `linkopener.version` in root gradle.properties.
// Consumed here (DMG metadata) and in :shared (generates BuildVersion.kt
// for the Settings UI). Bump before pushing to main.
val composePackageVersion: String = providers.gradleProperty("linkopener.version").get()
val signingIdentity: String? = findProperty("macos.signing.identity") as String?
val notarizationProfile: String? = findProperty("macos.notarization.profile") as String?

// Required by MacOsAlwaysOnTopOverFullScreen — reflects into the AWT peer
// chain to grab the underlying NSWindow pointer. We need both --add-exports
// (so the unnamed module can SEE sun.awt / sun.lwawt classes at all) and
// --add-opens (so we can setAccessible on the private CFRetainedResource.ptr
// field). Harmless warnings on non-mac OSes — the sun.lwawt.macosx package
// just doesn't exist there.
val nativeAccessJvmArgs = listOf(
    "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
)

compose.desktop {
    application {
        mainClass = "dev.hackathon.linkopener.app.MainKt"
        jvmArgs += nativeAccessJvmArgs
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = composePackageName
            packageVersion = composePackageVersion
            macOS {
                bundleID = "dev.hackathon.linkopener"
                infoPlist {
                    // CFBundleDocumentTypes for public.html and ASWebAuthenticationSessionWebBrowserSupportCapabilities
                    // are what macOS Sonoma+ uses to qualify an app as a "real" web browser
                    // for the System Settings -> Default Web Browser picker. CFBundleURLTypes
                    // alone makes Launch Services route URLs to us, but does NOT make the
                    // System Settings UI list us as a candidate.
                    extraKeysRawXml = """
                        <key>CFBundleDisplayName</key>
                        <string>Link Opener</string>
                        <key>CFBundleURLTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>Web URL</string>
                                <key>CFBundleURLSchemes</key>
                                <array>
                                    <string>http</string>
                                    <string>https</string>
                                </array>
                                <key>LSHandlerRank</key>
                                <string>Default</string>
                            </dict>
                        </array>
                        <key>CFBundleDocumentTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleTypeRole</key>
                                <string>Viewer</string>
                                <key>LSItemContentTypes</key>
                                <array>
                                    <string>public.html</string>
                                    <string>public.xhtml</string>
                                </array>
                            </dict>
                        </array>
                        <key>ASWebAuthenticationSessionWebBrowserSupportCapabilities</key>
                        <dict>
                            <key>IsSupported</key>
                            <true/>
                            <key>EphemeralBrowserSessionIsSupported</key>
                            <true/>
                        </dict>
                    """.trimIndent()
                }
                if (signingIdentity != null) {
                    signing {
                        sign.set(true)
                        identity.set(signingIdentity)
                    }
                }
            }
        }
    }
}

// Belt-and-suspenders: ensure the same args reach the gradle :desktopApp:run
// JavaExec task whether or not the compose plugin propagates application.jvmArgs
// to it. IDEA's Compose run config builds on top of this task, so this is what
// makes the in-IDE debug run honour the args.
tasks.withType<JavaExec>().configureEach {
    jvmArgs(nativeAccessJvmArgs)
}

// --- Cross-platform Windows fat-JAR -----------------------------------------
// Builds a single .jar with our code + Windows-targeted Skiko native libs
// (DLL bundled inside `skiko-awt-runtime-windows-x64`) + every other runtime
// dep. Runnable from any Windows host that has JDK 17+ on PATH:
//
//   java -jar link-opener-<ver>-windows-x64.jar
//
// Why this exists: jpackage / WiX (the path Compose Desktop's `packageMsi`
// uses) can only run ON Windows — they don't cross-compile from macOS. A
// fat-JAR is the realistic "build a Windows-runnable from a macOS host"
// shape: trade off the lack of an installer for portability + JDK
// dependency.
//
// macOS-specific code (MacOsAlwaysOnTopOverFullScreen's reflective
// AWTAccessor lookup) is gated at runtime by an `isMacOs` check, so the
// missing `--add-exports` JVM args on a vanilla Windows `java -jar`
// invocation never actually fire — the class loads fine, the side
// effects don't run.

// Standalone configuration so we don't pollute jvmRuntimeClasspath with the
// Windows Skiko jar (which would conflict with the macOS one for local
// development). All deps re-declared explicitly here, with
// `compose.desktop.windows_x64` substituted for `compose.desktop.currentOs`
// — same library set, Windows native DLL instead of macOS dylib.
val windowsUberRuntime: Configuration by configurations.creating

dependencies {
    windowsUberRuntime(project(":shared"))
    windowsUberRuntime(compose.desktop.windows_x64)
    windowsUberRuntime(libs.compose.material3)
    windowsUberRuntime(libs.compose.resources)
    windowsUberRuntime(libs.kotlinx.coroutines.swing)
    windowsUberRuntime(libs.kotlinx.serialization.json)
    windowsUberRuntime(libs.multiplatformSettings)
    windowsUberRuntime(libs.jna)
}

tasks.register<Jar>("packageWindowsUberJar") {
    group = "compose desktop"
    description = "Builds a Windows-runnable fat-JAR (cross-compiled from any host)."
    // Lazy zipTree references survive into execution time, which Gradle's
    // configuration cache can't serialise. This is a one-shot packaging
    // task (run on demand to produce a release artefact), not a hot-loop
    // build step — opting out is the simpler fix.
    notCompatibleWithConfigurationCache("Uses zipTree() over a resolved Configuration")
    archiveBaseName.set("link-opener")
    archiveClassifier.set("windows-x64")
    archiveVersion.set(composePackageVersion)

    manifest {
        attributes["Main-Class"] = "dev.hackathon.linkopener.app.MainKt"
        attributes["Implementation-Version"] = composePackageVersion
    }

    // Skip jar-signature artifacts (they invalidate when we re-pack) and
    // module-info.class (multiple modules' module-info collide; we don't
    // run as a named module anyway). Default deduplication strategy
    // would error on conflicts.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/*/module-info.class",
        "module-info.class",
    )

    // Bundle this module's compiled classes (the desktopApp jar) AND the
    // entire Windows-targeted runtime classpath. Each dep jar is unzipped
    // into the fat-jar at config time — declaring `from(zipTree(...))`
    // statically (one entry per dep) keeps Gradle's configuration cache
    // happy (no Project references survive into execution time).
    val jvmJarTask = tasks.named<Jar>("jvmJar")
    dependsOn(jvmJarTask)
    from(jvmJarTask.flatMap { it.archiveFile }.map { zipTree(it) })

    // Resolve the Windows runtime classpath now (config time) and add a
    // separate `from(zipTree(...))` for each dep jar. `windowsUberRuntime`
    // pulls `compose.desktop.windows_x64` so Skiko's Windows DLL is in
    // the resulting fat-jar instead of the host's macOS dylib.
    windowsUberRuntime.resolve().forEach { file ->
        if (file.isDirectory) {
            from(file)
        } else if (file.name.endsWith(".jar")) {
            from(zipTree(file))
        }
    }
}

if (notarizationProfile != null) {
    val dmgFile = layout.buildDirectory.file(
        "compose/binaries/main/dmg/$composePackageName-$composePackageVersion.dmg",
    )

    tasks.register<Exec>("notarizeDmgViaKeychain") {
        group = "compose desktop"
        description =
            "Submit the packaged DMG to Apple's notary service using the keychain " +
                "profile (avoids passing the app-specific password through gradle)."
        dependsOn("packageDmg")
        inputs.file(dmgFile)
        commandLine(
            "xcrun", "notarytool", "submit",
            dmgFile.get().asFile.absolutePath,
            "--keychain-profile", notarizationProfile,
            "--wait",
        )
    }

    tasks.register<Exec>("stapleDmgViaKeychain") {
        group = "compose desktop"
        description = "Staple the notarization ticket onto the DMG so Gatekeeper accepts it offline."
        dependsOn("notarizeDmgViaKeychain")
        commandLine(
            "xcrun", "stapler", "staple",
            dmgFile.get().asFile.absolutePath,
        )
    }
}
