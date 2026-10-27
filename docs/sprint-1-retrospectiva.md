# Retrospectiva del Sprint 1 y acciones de mejora para el Sprint 2

**Sprint 1:** MVP v1.0, del 5 al 23 de septiembre de 2026.

**Equipo (RACI):**
- Manuel Ladino: Product Owner / PM.
- Keppler Sánchez: Tech Lead.
- Andrés Galvis: QA.

**Resultado:** 7 de 7 historias terminadas, **39 puntos**.

| Historia | Puntos |
|---|---|
| HU-01 Usuarios y roles | 5 |
| HU-02 Embarcaciones y artes | 5 |
| HU-03 Bitácora electrónica | 8 |
| HU-05 Vedas | 5 |
| HU-06 Especies y tallas | 5 |
| HU-08 Permisos | 8 |
| HU-20 Administración y auditoría | 3 |

> Los datos de este documento salen de la evidencia del repositorio (historial de commits y pull
> requests) y de Jira. El equipo los valida y completa en la reunión de retrospectiva.

## 1. Estrategias que funcionaron

| Estrategia | Evidencia |
|---|---|
| Historias con criterios de aceptación numerados (`AC-xx.y`) | Los mismos códigos aparecen en commits, código y pruebas (por ejemplo, `fix: alertas de vencimiento por nivel de severidad (AC-08.2)`) |
| Monorepo con frontend y backend y cambios por pull request | PR #1 (`feature/institutional-portal`) unificó los dos proyectos el 14 de septiembre |
| Organización del código por módulo de dominio | Paquetes `vessels`, `permits`, `trips`, `species`, `closures`… alineados con las épicas de Jira |
| Auditoría transversal desde el inicio | `AuditService` y `AppClock` creados antes de las historias que los usan |
| Datos DEMO por rol | Permiten demostrar cada historia sin preparar datos a mano |

## 2. Aspectos a mejorar

### 2.1 Dificultades técnicas (como grupo)

| Dificultad | Evidencia | Acción para el Sprint 2 |
|---|---|---|
| Sin integración continua: los errores de integración se descubrían tarde | El 23 de septiembre hubo commits de corrección como `fix: completar dependencias de vessel-identification` y `fix: completar dependencias de closure-resolution` | **HU-21:** pipeline en GitHub Actions que compila, prueba y bloquea el merge si algo falla |
| Fechas regulatorias calculadas con la zona horaria del servidor | Faenas registradas después de las 7 p. m. quedaban con la fecha del día siguiente. Se corrigió en el Sprint 2 con `AppClock` (America/Bogota) | Toda fecha pasa por `AppClock`, y las pruebas corren en UTC+14 para detectar regresiones |
| Solo se ejecutaba en local; no había un entorno compartido | No existían workflows ni infraestructura de despliegue | Despliegue continuo en Railway y Vercel, con health check y rollback |
| Pruebas automatizadas escritas al final de cada historia | Las correcciones llegaban después de las funcionalidades | Cada historia del Sprint 2 lleva pruebas y casos `TC-HUxx-0n` en Jira, que ejecuta QA |

### 2.2 Dificultades de organización (como grupo)

| Dificultad | Evidencia | Acción para el Sprint 2 |
|---|---|---|
| Trabajo concentrado en pocos días (integración "big bang") | Solo hubo commits el 5, el 14 y el 23 de septiembre, y el 23 se subieron 16 | Un PR por historia en cuanto termina; ramas cortas |
| Carga desbalanceada y poca evidencia individual en el repositorio | 83 de los 86 commits del repositorio son de una sola cuenta | Cada integrante trabaja sus historias con commits y PR propios (requisito del curso) |
| Jira no reflejaba el avance real | Las historias pasaron a *Listo* sin enlace a commits ni PR | Mover la tarjeta al abrir y al fusionar el PR; poner la clave `SLGEP-xx` en la rama o el PR |
| No hubo seguimiento diario | Del 14 al 23 de septiembre no hubo actividad en el repositorio | Daily de 10 minutos (sincrónico o por chat) los lunes, miércoles y viernes |

### 2.3 Por integrante

| Integrante | Rol | Qué mejorar | Compromiso para el Sprint 2 |
|---|---|---|---|
| Manuel Ladino | PM / desarrollo | Concentró la implementación y la integración, y por eso fue un cuello de botella | Delegar historias completas, revisar los PR de los demás y mantener Jira al día |
| Keppler Sánchez | Tech Lead | Aportó la estructura base inicial (5 de septiembre) y luego tuvo pocas contribuciones en el repositorio | Liderar HU-21 (CI/CD), HU-04 y HU-18 con commits propios; revisar la arquitectura en cada PR |
| Andrés Galvis | QA | Los casos de prueba no estaban formalizados ni tenían evidencia | Ejecutar y documentar los casos `TC-HUxx` del Sprint 2; responsable de HU-10, HU-12 y HU-15 |

## 3. Cuellos de botella y tiempos muertos de la planeación del Sprint 1

1. **Dependencia de una sola persona para integrar:** el resto del equipo esperaba a que se integrara el backend.
2. **Reestructuración tardía del repositorio:** frontend y backend empezaron separados y se unificaron como monorepo a mitad del sprint (14 de septiembre), lo que consumió tiempo y provocó conflictos (`merge: resuelve conflicto README`).
3. **Tiempo muerto del 14 al 23 de septiembre:** no hubo commits ni seguimiento. El trabajo se recuperó en un solo día.
4. **Historias de 8 puntos sin dividir en tareas:** HU-03 y HU-08 se estimaron como bloques. No había forma de ver el avance parcial ni de repartirlas.
5. **Sin definición de "terminado":** no exigía pruebas, revisión ni despliegue, así que "terminado" significaba "funciona en mi máquina".

## 4. Problemas posibles en el Sprint 2 y cómo mitigarlos

| Riesgo | Probabilidad / impacto | Mitigación |
|---|---|---|
| Alcance mayor: 57 puntos frente a 39 (+46 %) | Alta / alto | Historias divididas en 3 tareas cada una; prioridad MoSCoW (*Must*: HU-21, HU-07 y HU-04 primero) |
| Dependencias en cadena: HU-07 bloquea HU-09, HU-11 y HU-12 | Media / alto | HU-07 se planeó en la primera semana; enlaces *Blocks* en Jira |
| Fallas o incidentes del proveedor de nube | Media / alto | Imágenes en GHCR por commit y rollback documentado. **Ocurrió:** el incidente de seguridad de Railway del 21 de octubre pausó los despliegues del plan Hobby |
| Secretos expuestos | Baja / crítico | Secretos solo en variables de la plataforma y en GitHub Secrets; rotación documentada |
| Servicios externos con clave o cuota (Google Maps) | Media / medio | Respaldo con Leaflet y OpenStreetMap sin clave, y esquema sin conexión |
| Conflictos entre ramas de historias que tocan los mismos archivos | Media / medio | PR pequeños y rebase frecuente contra `main`; el CI corre en cada PR |
| Límite de inicios de sesión al hacer pruebas de carga | Media / bajo | Pruebas de carga con tokens reutilizados (BlazeMeter) o con el límite ajustado solo en local |

## 5. Plan de mejora (resumen)

| # | Acción | Responsable | Cómo se verifica |
|---|---|---|---|
| 1 | CI en cada PR y bloqueo del merge si falla | Keppler | Checks obligatorios en GitHub |
| 2 | Un PR por historia, con la clave de Jira | Todos | Lista de PR del repositorio |
| 3 | Definición de terminado: pruebas, revisión, CI verde y desplegado | Manuel | Columna *Listo* solo con un PR fusionado |
| 4 | Casos `TC-HUxx` ejecutados con evidencia | Andrés | Subtareas TC en Jira con captura o enlace |
| 5 | Daily corta 3 veces por semana | Manuel | Notas en el canal del equipo |
