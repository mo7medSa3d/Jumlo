plugins { alias(libs.plugins.kotlin.jvm); `java-library` }
kotlin { jvmToolchain(17) }

dependencies { api(project(":core-common")); testImplementation(libs.junit) }
