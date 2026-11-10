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

// Ensure debug.keystore is materialized from debug.keystore.base64 on clean CI runners
// before :app:validateSigningDebug executes, without committing binary keystores to git.
val debugKeystoreFile = file("${rootDir}/debug.keystore")
val debugKeystoreBase64File = file("${rootDir}/debug.keystore.base64")
if (!debugKeystoreFile.exists() && debugKeystoreBase64File.exists()) {
  val base64Content = debugKeystoreBase64File.readText(Charsets.UTF_8).trim()
  if (base64Content.isNotEmpty()) {
    debugKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(base64Content))
  }
}
