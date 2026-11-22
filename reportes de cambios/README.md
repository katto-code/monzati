# Reportes de cambios

Un archivo por versión de la app. La versión es la de `gradle.properties` (`monzatti.versionName` y
`monzatti.versionCode`); cada reporte dice qué cambió, cómo se verificó y qué falta.

| Versión | Código | Fecha | Resumen |
|---|---|---|---|
| [1.1.0](v1.1.0.md) | 2 | 22 nov 2026 | Corrección de los hallazgos del análisis: seguridad, tienda, panel del socio, closet, medidas y diseño |
| [1.0.0](v1.0.0.md) | 1 | 21 nov 2026 | Versión del demo inicial y análisis honesto que dio origen a la 1.1.0 |

## Cómo agregar una versión nueva

1. Sube `monzatti.versionCode` (+1) y `monzatti.versionName` en `gradle.properties`.
2. Copia el reporte anterior como `v<versión>.md` y reescribe sus secciones.
3. Agrega la fila arriba de esta tabla (la más nueva primero).
