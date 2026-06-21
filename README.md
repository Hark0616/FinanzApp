# FinanzApp

FinanzApp es una aplicación Android nativa diseñada para gestionar finanzas personales de forma automática y profesional ("re pro"). Permite centralizar saldos de cuentas de ahorro, billeteras digitales, tarjetas de crédito y préstamos de consumo, actualizándolos reactivamente mediante la interceptación y el análisis inteligente de notificaciones y mensajes SMS.

---

## Características Clave

1. **Lectura Automática de Transacciones**:
   - Interceptación en tiempo real de notificaciones bancarias de compras físicas/online, ingresos y transacciones NFC.
   - **Parsers Locales**: Análisis basado en expresiones regulares rápidas para entidades en Colombia como **Bancolombia (SMS)**, **Nequi**, **Daviplata** y **Davivienda**.

2. **Pipeline de Clasificación Híbrido con IA**:
   - **IA Local (Gemini Nano)**: Detección inteligente de hardware compatible (ej. la NPU del **Samsung Galaxy S26 Ultra** o Google Pixels). Clasifica y categoriza los datos de la notificación localmente, garantizando 100% de privacidad y latencia cero.
   - **Fallback a la Nube (OpenRouter)**: Si el dispositivo no soporta procesamiento en el borde (ej. **Redmi 10s**) o el modelo local está desactivado, el sistema recurre de forma segura a un modelo ligero en la nube (como `Gemini Flash 1.5` en OpenRouter) usando una API key cifrada localmente.

3. **Módulo de Créditos y Préstamos**:
   - Gestión detallada de préstamos de consumo, libre inversión e hipotecarios.
   - Control de saldo deudor restante, cuotas pagadas/totales, tasa de interés y alertas semaforizadas de días restantes para el vencimiento de la cuota.
   - **Integración de Débitos**: Al registrar el pago de una cuota se descuenta el saldo automáticamente de la cuenta de ahorros vinculada y se registra la transacción en el historial.

4. **Módulo de Tarjetas de Crédito**:
   - Interfaz visual realista estilo tarjeta física que indica cupo límite, cupo disponible y porcentaje de uso del cupo.
   - Control de días límite de pago y fechas de corte de ciclo de facturación.
   - Registro de abonos directos desde tus cuentas de ahorros.

5. **Historial de Movimientos**:
   - Filtro de búsqueda rápida por comercio o categoría.
   - Sección dedicada de transacciones clasificadas por IA "Pendientes de revisión" para confirmación manual o recategorización interactiva.

6. **Seguridad del Estado del Arte**:
   - **Persistencia cifrada**: Base de datos local SQLite (Room) protegida con cifrado **SQLCipher** mediante contraseña de 32 bytes autogenerada aleatoriamente y guardada en el Android Keystore.
   - **Preferencias protegidas**: Las llaves de API de OpenRouter se almacenan usando `EncryptedSharedPreferences`.

---

## Stack Tecnológico

- **UI**: Jetpack Compose con arquitectura reactiva de componentes.
- **Asincronía**: Kotlin Coroutines & Flows (`StateFlow`, `combine`, `collectAsStateWithLifecycle`).
- **Inyección de Dependencias**: Dagger Hilt.
- **Base de Datos**: Room con SQLCipher.
- **Redes**: Retrofit + Moshi para el consumo seguro de APIs REST.
- **Seguridad**: Android Jetpack Security (Crypto).

---

## Principios de Evolución

- **Fiabilidad financiera + UX/UI clara**: cada cambio debe proteger la precisión de saldos, deudas, pagos, privacidad y auditabilidad, y al mismo tiempo explicar al usuario qué es exacto, estimado, pendiente de revisión o riesgoso.
- **Cambios trazables**: usar ramas pequeñas, commits claros y PRs con pruebas ejecutadas.
- **Sin mutaciones financieras ambiguas**: si una transacción tiene baja confianza o asignación incierta, debe revisarse antes de alterar saldos o deuda.
- **Verificación antes de lógica crítica**: antes de tocar cálculos financieros, ejecutar la línea base documentada en [docs/build-verification.md](docs/build-verification.md).

---

## Guía de Optimización de Batería (S26 Ultra y Redmi 10s)

Para asegurar que Android no detenga el servicio de interceptación de transacciones en segundo plano, realiza las siguientes configuraciones:

### Samsung Galaxy S26 Ultra (One UI)
1. Ve a **Ajustes → Aplicaciones → FinanzApp → Batería** y selecciona **"Sin Restricciones"**.
2. Entra a **Cuidado del dispositivo → Batería → Límites de uso en segundo plano → Apps que nunca se suspenden** y añade **FinanzApp**.
3. Abre el menú de aplicaciones recientes, presiona el logo de FinanzApp y selecciona **"Mantener abierta"** (icono de candado) para evitar cierres agresivos por RAM.

### Xiaomi Redmi 10s (MIUI / HyperOS)
1. Ve a **Ajustes → Aplicaciones → Administrar aplicaciones → FinanzApp** y activa **"Inicio Automático"**.
2. En la misma pantalla de información, entra a **"Ahorro de batería"** y selecciona **"Sin Restricciones"**.
3. Abre FinanzApp, ve a la vista de tareas recientes, mantén pulsada la miniatura de la app y selecciona el **candado** para bloquearla en memoria.
