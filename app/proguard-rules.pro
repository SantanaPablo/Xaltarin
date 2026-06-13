# ── Reglas generales ────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── OpenVPN ─────────────────────────────────────────────────────────────────
-keep class de.blinkt.openvpn.** { *; }
-keep interface de.blinkt.openvpn.** { *; }

# ── SaltarinApp ──────────────────────────────────────────────────────────────
-keep class com.perromono.saltarin.** { *; }
-keep class com.perromono.xaltarin.** { *; }

# ── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Compose ─────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**
