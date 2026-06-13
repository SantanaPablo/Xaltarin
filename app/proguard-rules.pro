# ── Reglas generales ────────────────────────────────────────────────────────

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── OpenVPN ─────────────────────────────────────────────────────────────────

-keep class de.blinkt.openvpn.** { *; }
-keep interface de.blinkt.openvpn.** { *; }

# ── SaltarinApp (referenciada en AndroidManifest, no borrar con R8) ─────────

-keep class com.perromono.xaltarin.** { *; }

# ── Kotlin ──────────────────────────────────────────────────────────────────

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Compose ─────────────────────────────────────────────────────────────────

-dontwarn androidx.compose.**
