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
val composePackageVersion = "1.0.0"
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
