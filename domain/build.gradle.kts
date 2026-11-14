plugins { id("org.jetbrains.kotlin.jvm") }
kotlin {
    jvmToolchain(17)
    sourceSets.named("test") { kotlin.srcDir("../parity/kotlin") }
}
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { systemProperty("stayar.root", rootProject.projectDir.absolutePath) }
