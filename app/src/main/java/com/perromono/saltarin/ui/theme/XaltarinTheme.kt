package com.perromono.saltarin.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
//  PALETA DE COLORES — Xaltarin
//  Inspirada en la mascota / logo oficial
// ═══════════════════════════════════════════════════════════════════════════

/** Color primario. Barras de navegación, botones principales, cabeceras. */
val BlueVibrant   = Color(0xFF2A9DFF)

/** Acento / Hover. Elementos seleccionados, estados activos, degradados. */
val BlueLuminous  = Color(0xFF73C2FF)

/** Color secundario. Fondos de tarjetas, botones secundarios, bordes. */
val BlueDeep      = Color(0xFF1858B5)

/** Fondo oscuro / Texto principal en dark mode. */
val BlueMidnight  = Color(0xFF0F1C33)

/** Alerta / Error / Desconexión. El único acento cálido (la lengua del logo). */
val PinkAccent    = Color(0xFFFE5C7D)

/** Fondo claro / Texto sobre botones azules. */
val White         = Color(0xFFFFFFFF)

// ── Tokens de superficie derivados de la paleta ─────────────────────────────
/** Superficie de tarjetas — un paso más claro que el fondo. */
val Surface1      = Color(0xFF142040)

/** Superficie elevada / inputs — un paso más claro que Surface1. */
val Surface2      = Color(0xFF1A2D55)

/** Texto secundario apagado. */
val TextMuted     = Color(0xFF5C7EA8)

/** Borde sutil. */
val Outline       = Color(0xFF1E3560)

// ═══════════════════════════════════════════════════════════════════════════
//  COLOR SCHEME — solo modo oscuro
// ═══════════════════════════════════════════════════════════════════════════
val XaltarinColorScheme = darkColorScheme(
    primary            = BlueVibrant,
    onPrimary          = White,
    primaryContainer   = BlueDeep,
    onPrimaryContainer = BlueLuminous,
    secondary          = BlueLuminous,
    onSecondary        = BlueMidnight,
    background         = BlueMidnight,
    onBackground       = White,
    surface            = Surface1,
    onSurface          = White,
    surfaceVariant     = Surface2,
    onSurfaceVariant   = TextMuted,
    outline            = Outline,
    error              = PinkAccent,
    onError            = White
)