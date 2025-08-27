// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Disponibiliza os plugins, mas não aplica globalmente
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false // manter "apply false" NÃO ativa Kotlin no app
}
