# Atributos de calidad: escenarios y tácticas

SIGEPES prioriza cuatro atributos de calidad. Para cada uno hay **tres escenarios** que se pueden
comprobar en la aplicación desplegada, cada uno con su **táctica** de arquitectura (Bass, Clements y
Kazman, *Software Architecture in Practice*).

- **API** = `https://sigepes-api-production.up.railway.app`
- **WEB** = `https://project-sigepes.vercel.app`

Formato del escenario: **estímulo** (qué ocurre) → **respuesta** (qué hace SIGEPES) → **medida** (cómo se sabe que cumple).

| Atributo | Por qué importa en SIGEPES | Historias |
|---|---|---|
| Disponibilidad | Los pescadores trabajan en el mar con señal intermitente, y la autoridad necesita el servicio siempre | HU-21, HU-04, HU-07, HU-10, HU-11, HU-15 |
| Seguridad | Datos personales (Ley 1581 de 2012), denuncias anónimas y decisiones regulatorias | HU-21, HU-09, HU-10, HU-12, HU-15, HU-18 |
| Rendimiento | Picos de uso al cierre de faenas y consultas sobre un año de datos | HU-21, HU-04, HU-07, HU-09, HU-11, HU-12, HU-18 |
| Usabilidad | Usuarios en celular, al aire libre y con distintos niveles de alfabetización digital | HU-04, HU-07, HU-10, HU-12 |

---

## 1. Disponibilidad

| # | Escenario (estímulo → respuesta → medida) | Táctica | Cómo comprobarlo |
|---|---|---|---|
| D1 | El backend falla en producción → Railway detecta el fallo del health check y reinicia el contenedor; una versión nueva que no responda nunca reemplaza a la activa → servicio de nuevo `UP` en < 2 min | **Detección de fallos (ping/echo)** con `/actuator/health`, más **recuperación con reinicio automático** (`restartPolicyType: ON_FAILURE`, 10 reintentos, en `backend/railway.json`) | Abrir `API/actuator/health` → `{"status":"UP"}`. En Railway → *Deployments*, cada versión pasa el health check antes de activarse |
| D2 | Un pescador sin señal registra una faena → la app la guarda en el dispositivo y la envía al reconectar → 0 faenas perdidas; mensaje *"Sin conexión: la faena quedó guardada en este dispositivo."* | **Cola local con reintento** (*store and forward*) y sincronización `POST /api/v1/faenas/sincronizar` | Iniciar sesión como pescador → F12 → *Network* → **Offline** → crear la faena (aparece *"1 faena(s) pendiente(s) de sincronizar"*) → **Online** → la faena aparece en la lista |
| D3 | Sin conexión, el usuario consulta especies y cuotas → la app muestra la última copia guardada → 100 % del catálogo visible; aviso *"Sin conexión — mostrando el último catálogo guardado en este dispositivo."* | **Redundancia de datos en el cliente**: caché local del catálogo y del saldo de cuotas; el mapa cae al esquema propio | Abrir *Especies* con conexión → **Offline** → recargar: el catálogo sigue visible con el aviso |

## 2. Seguridad

| # | Escenario | Táctica | Cómo comprobarlo |
|---|---|---|---|
| S1 | Un atacante intenta adivinar contraseñas → desde el intento 11 en un minuto por IP la API responde **HTTP 429** con *"Demasiados intentos. Espera un momento e inténtalo de nuevo."* | **Limitar el acceso** (`RateLimitFilter`, ventana de 1 min por IP); contraseñas guardadas con **BCrypt** | Ejecutar el comando de PowerShell de abajo: los 10 primeros dan 401 y el 11 da **429** |
| S2 | Un pescador intenta usar funciones de la autoridad (usuarios, auditoría, indicadores globales) → **HTTP 403**, y el menú no las muestra | **Autorizar actores**: control de acceso por rol (`@PreAuthorize` en la API y `RoleRoute` en la web) | Iniciar sesión como `pescador@sigepes.gov.co`: no aparecen *Usuarios* ni *Auditoría*. Con su token, `GET API/api/v1/usuarios` → **403** |
| S3 | Alguien llama a la API sin sesión o con un token alterado → **HTTP 401**, sin datos | **Autenticar actores** con JWT firmado (HMAC): cambiar un solo carácter invalida la firma | Abrir `API/api/v1/embarcaciones` en el navegador → **401**. Repetir con un token al que se le cambie una letra → **401** |

Comando para S1 (usa a propósito una contraseña incorrecta):
```powershell
1..11 | % { try { Invoke-RestMethod -Method Post -Uri "https://sigepes-api-production.up.railway.app/api/v1/auth/login" -ContentType "application/json" -Body '{"email":"pescador@sigepes.gov.co","password":"incorrecta"}' } catch { "$_ -> $($_.Exception.Response.StatusCode.value__)" } }
```

## 3. Rendimiento

| # | Escenario | Táctica | Cómo comprobarlo |
|---|---|---|---|
| R1 | 50 usuarios consultan especies, vedas activas y embarcaciones al mismo tiempo → p90/p95 < 1 s y errores < 1 % | **Concurrencia**: API sin estado (JWT, sin sesión en el servidor) y **pool de conexiones** a PostgreSQL (HikariCP) | Prueba en BlazeMeter con 50 usuarios, 1 min de subida y 5 min de duración. Prueba de depuración con 2 usuarios: p90 = 24 ms, 0 % de errores |
| R2 | Un analista exporta un reporte oficial de más de un año → la API responde **HTTP 202** de inmediato y el archivo se genera en segundo plano → respuesta < 1 s | **Procesamiento asíncrono** de exportaciones largas (`ExportJobEntity`) | *Reportes oficiales* → rango de más de 1 año → exportar. En F12 → *Network*, `exportar` responde **202**, y el trabajo aparece en la lista para descargar |
| R3 | Un usuario vuelve a abrir la app → JS, CSS e imágenes salen de la caché local → 0 descargas repetidas | **Caché** con el service worker de la PWA (Workbox), más la **CDN** de Vercel con archivos comprimidos | Abrir WEB → F12 → *Network* → recargar: la columna *Size* dice **(ServiceWorker)** en `assets/*.js` y `*.css` |

## 4. Usabilidad

| # | Escenario | Táctica | Cómo comprobarlo |
|---|---|---|---|
| U1 | Un pescador usa la app en un celular de 375 px → todas las pantallas sin scroll horizontal; mapa de zonas con capas Mapa/Satélite/Relieve; menú en cajón lateral | **Diseño adaptable** al dispositivo (*responsive*) | F12 → Ctrl+Shift+M → *iPhone SE*. Recorrer *Panel*, *Faenas*, *Zonas restringidas* y *Especies* |
| U2 | El usuario pierde la señal → el chip de la cabecera pasa de **"En línea"** a **"Sin conexión"** y aparece *"Sin conexión — los registros se guardan en este dispositivo."* | **Mantener informado al usuario** del estado del sistema (retroalimentación) | F12 → *Network* → **Offline** / **Online**: el chip y el aviso cambian al instante |
| U3 | El usuario se equivoca en un formulario → mensaje en español junto al campo; los errores del servidor aparecen como alerta legible, sin códigos técnicos | **Prevención de errores**: validación en el formulario antes de enviar y mensajes del servidor traducidos | En el inicio de sesión: un correo sin `@` muestra error bajo el campo; una contraseña errónea muestra una alerta roja; al exceder la cuota aparece *"La captura excede la cuota disponible."* |

---

## Resumen de tácticas

| Atributo | Tácticas |
|---|---|
| Disponibilidad | Detección de fallos y reinicio automático · Cola local con reintento · Caché local de datos |
| Seguridad | Limitar el acceso · Autorizar actores (control por rol) · Autenticar actores (JWT + BCrypt) |
| Rendimiento | Concurrencia (API sin estado + pool de conexiones) · Procesamiento asíncrono · Caché (service worker + CDN) |
| Usabilidad | Diseño adaptable · Retroalimentación del estado · Prevención de errores |
