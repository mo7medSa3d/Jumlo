# Dependency notices and qualification boundary

This archive contains project source, XML resources, test code and text evidence. It does not contain a Gradle distribution, Android SDK, third-party fonts, a PDFBox binary, encrypted-vault binary, or APK.

Direct dependencies are declared by coordinates and pinned versions in `gradle/libs.versions.toml`; Gradle resolves them on a properly provisioned build machine. This execution did not resolve their transitive graph. Before distribution, generate and review the complete third-party license inventory and retain required notices. No claim of a completed license/security audit is made.

The missing official Gradle wrapper can be restored explicitly with the supplied checksum-verifying script. Gradle's distribution/wrapper has its own notices; retain them. The app's ownership/license is not changed by this implementation handoff.
