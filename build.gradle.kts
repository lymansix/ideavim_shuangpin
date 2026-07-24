import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.1.3")
        testFramework(TestFrameworkType.Platform)

        // IdeaVim — compile-time dependency only. At runtime, it's declared optional
        // in plugin.xml via <depends on optional="true" config-file="vim-integration.xml">,
        // so the plugin still loads (and the vim-integration.xml is skipped) when
        // IdeaVim is not installed.
        plugin("IdeaVIM", "2.36.0")
    }
}
