import java.util.Base64

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}


abstract class MaterializeDebugKeystoreTask : DefaultTask() {
  @get:InputFile
  @get:Optional
  abstract val base64File: RegularFileProperty

  @get:OutputFile
  abstract val destinationKeystore: RegularFileProperty

  @TaskAction
  fun materialize() {
    val dest = destinationKeystore.get().asFile
    val b64 = base64File.orNull?.asFile
    if (!dest.exists() || dest.length() == 0L) {
      if (b64 != null && b64.exists()) {
        val base64Content = b64.readText(Charsets.UTF_8).trim()
        if (base64Content.isNotEmpty()) {
          val decodedBytes = Base64.getMimeDecoder().decode(base64Content)
          dest.writeBytes(decodedBytes)
        }
      }
    }
  }
}

// Materialize immediately at Gradle evaluation/configuration time so that Android's signingConfig finds it
val rootDirFile = rootDir
val debugKeystoreFile = File(rootDirFile, "debug.keystore")
val debugKeystoreBase64File = File(rootDirFile, "debug.keystore.base64")
if (!debugKeystoreFile.exists() || debugKeystoreFile.length() == 0L) {
  if (debugKeystoreBase64File.exists()) {
    val base64Content = debugKeystoreBase64File.readText(Charsets.UTF_8).trim()
    if (base64Content.isNotEmpty()) {
      val decodedBytes = Base64.getMimeDecoder().decode(base64Content)
      debugKeystoreFile.writeBytes(decodedBytes)
    }
  }
}

val materializeDebugKeystoreTask = tasks.register<MaterializeDebugKeystoreTask>("materializeDebugKeystore") {
  description = "Materializes debug.keystore from debug.keystore.base64 before signing validation."
  group = "build setup"
  base64File.set(layout.projectDirectory.file("debug.keystore.base64"))
  destinationKeystore.set(layout.projectDirectory.file("debug.keystore"))
}

gradle.projectsEvaluated {
  allprojects {
    tasks.matching { it.name in listOf("validateSigningDebug", "testDebugUnitTest", "assembleDebug", "lintDebug") }.configureEach {
      dependsOn(materializeDebugKeystoreTask)
    }
  }
}


