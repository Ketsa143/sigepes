# Sprint Backlog · Sprint 2 (MVP v1.1)

| Dato | Valor |
|---|---|
| Fechas | 19 → 30 de octubre de 2026 |
| Objetivo | Control avanzado (cuotas, geocontrol, alertas), trazabilidad y denuncias, más el primer despliegue en la nube |
| Atributos de calidad | Disponibilidad, Seguridad, Rendimiento y Usabilidad ([escenarios y tácticas](atributos-calidad.md)) |
| Compromiso | **9 historias, 57 puntos**, frente a 39 del Sprint 1: **+46 % de velocidad** |
| Jira | Tablero SLGEP, sprint *SIGEP Sprint 2* |

## A. Historias por integrante

| Integrante | Historia (Jira) | Puntos | Prioridad | Plan |
|---|---|---|---|---|
| **Keppler Sánchez** (Tech Lead) | HU-21 Despliegue continuo en la nube (SLGEP-59) | 8 | Must | 19–29 oct |
| | HU-04 Geolocalización y zonas restringidas (SLGEP-61) | 8 | Must | 21–27 oct |
| | HU-18 Reportes y estadísticas oficiales (SLGEP-67) | 5 | Should | 27–29 oct |
| **Manuel Ladino** (PM) | HU-07 Control de cuotas por especie (SLGEP-60) | 8 | Must | 19–23 oct |
| | HU-11 Alertas y notificaciones (SLGEP-63) | 5 | Should | 22–27 oct |
| | HU-09 Trazabilidad con QR (SLGEP-62) | 8 | Should | 26–29 oct |
| **Andrés Galvis** (QA) | HU-10 Denuncias de pesca ilegal (SLGEP-64) | 5 | Should | 19–23 oct |
| | HU-12 Tablero de indicadores (SLGEP-65) | 5 | Should | 26–29 oct |
| | HU-15 Gestión documental versionada (SLGEP-66) | 5 | Should | 26–29 oct |

Reparto: Keppler 21 puntos, Manuel 21 y Andrés 15. Andrés además ejecuta los 45 casos de prueba
`TC-HUxx-0n`, 5 por historia.

## B. Dependencias (enlaces *Blocks* en Jira)

```
HU-07 Cuotas ──► HU-09 Trazabilidad   (solo una captura dentro de cuota puede volverse lote)
      │
      ├──────► HU-11 Alertas ◄── HU-04 Geolocalización   (alertas de zona restringida)
      │              ▲
      │              └────────── HU-10 Denuncias         (alerta "nueva denuncia")
      │
      └──────► HU-12 Indicadores ──► HU-18 Reportes      (agregados compartidos)

HU-21 Despliegue ──► HU-15 Documentos   (almacenamiento durable: volumen /var/data)
```

Orden de trabajo resultante: primero HU-21, HU-07 y HU-10; luego HU-04 y HU-11; al final HU-12,
HU-09, HU-15 y HU-18.

## C. Criterios de aceptación

El texto completo de cada criterio está en la descripción de la historia en Jira. Solo llevan
**[atributo]** los criterios que corresponden a uno de los 12 escenarios comprobables de
[atributos-calidad.md](atributos-calidad.md); los demás son criterios funcionales.

| Historia | Criterios de aceptación |
|---|---|
| HU-21 | AC-21.1 el CI bloquea el merge si falla · AC-21.2 contenedores de producción y Flyway · **AC-21.3 [Disponibilidad · D1]** health check y reinicio automático · AC-21.4 HTTPS, secretos fuera del repositorio y CORS · **AC-21.5 [Rendimiento · R1, R3]** 50 usuarios con p95 < 1 s y caché/CDN · AC-21.6 rollback en un paso |
| HU-07 | AC-07.1 crear cuota · AC-07.2 descuento automático · AC-07.3 bloqueo por cuota agotada · AC-07.4 alertas al 80 % y 95 % · AC-07.5 saldo acumulado · **AC-07.6 [Disponibilidad · D3]** catálogo y cuotas sin conexión · **AC-07.7 [Usabilidad · U3]** mensaje claro al exceder la cuota |
| HU-04 | AC-04.1 GPS cada 5 min · AC-04.2 polígonos de zonas · AC-04.3 alerta inmediata · AC-04.4 mapa de la faena · AC-04.5 prefiltro de zona · AC-04.6 cola de posiciones sin conexión · **AC-04.7 [Usabilidad · U1]** mapa usable a 375 px con capas Mapa/Satélite/Relieve |
| HU-09 | AC-09.1 lote y QR · AC-09.2 solo origen legal · AC-09.3 eventos de custodia · AC-09.4 verificación pública · AC-09.5 cadena de hash y sin datos personales · AC-09.6 vista pública en caché |
| HU-11 | AC-11.1 tipos de alerta · AC-11.2 bandeja y contador · AC-11.3 críticas no descartables · AC-11.4 audiencia por rol · AC-11.5 alertas persistidas · AC-11.6 entrega por eventos (SSE) |
| HU-10 | AC-10.1 formulario con fotos · AC-10.2 modo anónimo · AC-10.3 código de seguimiento · AC-10.4 flujo del inspector · AC-10.5 anonimato, EXIF y roles · AC-10.6 envío al reconectar · **AC-10.7 [Usabilidad · U2]** estado de conexión visible |
| HU-12 | AC-12.1 indicadores · AC-12.2 filtros · AC-12.3 solo datos validados · AC-12.4 gráficos accesibles · AC-12.5 agregados en base de datos · **AC-12.6 [Seguridad · S2]** solo autoridad (403 al pescador) |
| HU-15 | AC-15.1 carga · AC-15.2 versiones · AC-15.3 vencimientos · AC-15.4 búsqueda · AC-15.5 tipo real, enlaces firmados y auditoría · AC-15.6 volumen persistente |
| HU-18 | AC-18.1 catálogo · AC-18.2 filtros · AC-18.3 PDF/CSV · AC-18.4 datos validados · **AC-18.5 [Rendimiento · R2]** exportación asíncrona (202) · AC-18.6 roles y auditoría |

Los escenarios D2 (faena sin señal), S1 (límite de inicios de sesión) y S3 (JWT) se apoyan en historias
del Sprint 1 (HU-03 y HU-01) y se verifican en la tarea SLGEP-142.

## D. Tareas por historia (subtareas en Jira)

| Historia | Tareas (responsable) | Casos de prueba (Andrés) |
|---|---|---|
| HU-21 | TASK-21.1 Pipeline de CI en GitHub Actions · TASK-21.2 Imágenes Docker y PostgreSQL administrado · TASK-21.3 Despliegue con HTTPS, health check y rollback (Keppler) | TC-HU21-01…05 |
| HU-07 | TASK-07.1 Modelo y API de cuotas con saldo acumulado · TASK-07.2 Descuento, bloqueo y umbrales · TASK-07.3 Pantalla de cuotas con caché sin conexión (Manuel) | TC-HU07-01…05 |
| HU-04 | TASK-04.1 Polígonos y servicio punto-en-zona · TASK-04.2 GPS periódico con cola sin conexión · TASK-04.3 Mapa de la faena y alerta de zona (Keppler) | TC-HU04-01…05 |
| HU-09 | TASK-09.1 Lotes y eventos con cadena de hash · TASK-09.2 Verificación pública con caché · TASK-09.3 Lote desde la faena, QR y página pública (Manuel) | TC-HU09-01…05 |
| HU-11 | TASK-11.1 Alertas con outbox persistente · TASK-11.2 Reglas de audiencia y alertas críticas · TASK-11.3 Bandeja y contador en la campana (Manuel) | TC-HU11-01…05 |
| HU-10 | TASK-10.1 Denuncia anónima, código y limpieza EXIF · TASK-10.2 Flujo del inspector y roles · TASK-10.3 Formulario sin conexión y cola del inspector (Andrés) | TC-HU10-01…05 |
| HU-12 | TASK-12.1 Agregados con refresco programado · TASK-12.2 API del tablero con filtros y roles · TASK-12.3 Pantalla con gráficos accesibles (Andrés) | TC-HU12-01…05 |
| HU-15 | TASK-15.1 Versiones con validación de tipo real · TASK-15.2 Enlaces firmados y auditoría · TASK-15.3 Pantalla de documentos (Andrés) | TC-HU15-01…05 |
| HU-18 | TASK-18.1 Consultas paginadas sobre agregados · TASK-18.2 Exportación PDF/CSV asíncrona · TASK-18.3 Pantalla de reportes (Keppler) | TC-HU18-01…05 |

## Evidencia en el repositorio

Cada historia se entregó en su propio pull request, con CI en verde, antes de fusionarse a `main`:

| Historia | Rama | Pull request |
|---|---|---|
| HU-21 | `feat/hu-21-cicd` | [#11](https://github.com/Ketsa143/sigepes/pull/11) |
| HU-07 | `feat/hu-07-quotas` | [#12](https://github.com/Ketsa143/sigepes/pull/12) |
| HU-04 | `feat/hu-04-geolocation` | [#13](https://github.com/Ketsa143/sigepes/pull/13) |
| HU-10 | `feat/hu-10-reports` | [#14](https://github.com/Ketsa143/sigepes/pull/14) |
| HU-11 | `feat/hu-11-alerts` | [#16](https://github.com/Ketsa143/sigepes/pull/16) |
| HU-09 | `feat/hu-09-traceability` | [#17](https://github.com/Ketsa143/sigepes/pull/17) |
| HU-12 | `feat/hu-12-indicators` | [#18](https://github.com/Ketsa143/sigepes/pull/18) |
| HU-15 | `feat/hu-15-documents` | [#19](https://github.com/Ketsa143/sigepes/pull/19) |
| HU-18 | `feat/hu-18-official-reports` | [#20](https://github.com/Ketsa143/sigepes/pull/20) |

La aplicación desplegada está en https://project-sigepes.vercel.app.
