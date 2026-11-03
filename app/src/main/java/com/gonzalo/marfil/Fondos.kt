package com.gonzalo.marfil

/** Fondos de color para toda la app (los mismos que en Atrio): colores hondos y sobrios en lugar de papel claro u oscuro. */
class Fondo(val id: String, val name: String, val oscuro: Boolean, val tones: Tones)

val FONDOS: List<Fondo> = listOf(
    // Papel de verdad: blanco roto, algo beige, con tinta parda casi negra y rojo de sello
    Fondo("papelreal", "Papel de verdad", false, t(0xF8F2DF, 0xFCF8EA, 0x2B2418, 0x8C2A22, 0x2B2418, 0x5A4E33, 0x8A7B58, 0x8C2A22, 0x9A6B12,
        sand = 0xEFE6CC, line = 0xDDD0AE, edge = 0xBFAE84, muted = 0x5E5337)),
    // Granate: vino hondo, con tinta crema y oro viejo
    Fondo("granate", "Granate", true, t(0x5A1424, 0x6C1C30, 0xF8EDE8, 0xF0C26A, 0xF8EDE8, 0xDDBFC3, 0xB88A92, 0xF0C26A, 0xE9B94F,
        sand = 0x7A2639, line = 0x8F3D52, edge = 0xA85A6E, muted = 0xD3AAB2)),
    Fondo("bosque", "Verde bosque", true, t(0x12382B, 0x1B4837, 0xEDF3E8, 0xE2C86C, 0xEDF3E8, 0xBFD2BE, 0x8FB09A, 0xE2C86C, 0xDDBF58,
        sand = 0x25573F, line = 0x37705A, edge = 0x4F8A73, muted = 0xA9C7B3)),
    Fondo("marino", "Azul marino", true, t(0x10264A, 0x193560, 0xE9EEF7, 0xE6BA5E, 0xE9EEF7, 0xBCC9E0, 0x8DA2C6, 0xE6BA5E, 0xE0B050,
        sand = 0x234474, line = 0x38598C, edge = 0x5273A6, muted = 0xA3B4D2)),
    Fondo("petroleo", "Petróleo", true, t(0x0F3D44, 0x174F58, 0xE7F2F1, 0xF2C46D, 0xE7F2F1, 0xB5D3D2, 0x86B3B3, 0xF2C46D, 0xEDBB5E,
        sand = 0x1F6370, line = 0x317B86, edge = 0x4C98A2, muted = 0x9CC6C6)),
    Fondo("berenjena", "Berenjena", true, t(0x3C1A4A, 0x4D235F, 0xF6EAF4, 0xF2A9C2, 0xF6EAF4, 0xD6B8D8, 0xA98AB4, 0xF2A9C2, 0xEDB66C,
        sand = 0x5E2E75, line = 0x74428B, edge = 0x8E5DA5, muted = 0xC7A8CE)),
    Fondo("chocolate", "Chocolate", true, t(0x3A2218, 0x4A2E22, 0xF4E8DB, 0xE9A860, 0xF4E8DB, 0xD3BBA3, 0xA88B72, 0xE9A860, 0xE2A04E,
        sand = 0x5A3A2B, line = 0x704B3A, edge = 0x8B6650, muted = 0xC2A78E)),
    Fondo("pizarra", "Pizarra", true, t(0x232D38, 0x2D3A48, 0xE8EDF2, 0xE9B867, 0xE8EDF2, 0xBAC5D1, 0x8A9AAB, 0xE9B867, 0xE2B05A,
        sand = 0x3B4B5D, line = 0x4F6176, edge = 0x6A7F96, muted = 0xA2B0BF)),
    // Claro también: mostaza hondo, con tinta casi negra
    Fondo("mostaza", "Mostaza", false, t(0xD9A53A, 0xE6B954, 0x2A1E08, 0x6E1424, 0x2A1E08, 0x5A4412, 0x8A6A1E, 0x6E1424, 0x6E1424,
        sand = 0xCB962A, line = 0xB98521, edge = 0x8F6713, muted = 0x47340B)),
)

