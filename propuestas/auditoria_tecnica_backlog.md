# FinanzApp - Backlog de auditoria tecnica y funcional

Este documento convierte la auditoria inicial de FinanzApp en tareas
accionables, trazables y verificables. La intencion es resolver cada punto con
ramas pequenas, diffs revisables, pruebas automatizadas y, cuando aplique,
issues o PRs separados en GitHub.

## Flujo de trabajo recomendado

1. Crear una rama por tarea o por grupo pequeno de tareas:
   `codex/<id-tarea>-descripcion-corta`.
2. Antes de tocar codigo, escribir o actualizar pruebas que reproduzcan el
   comportamiento esperado o el bug.
3. Hacer cambios acotados, evitando refactors no relacionados.
4. Ejecutar minimo:
   - `.\gradlew.bat :app:testDebugUnitTest`
   - `.\gradlew.bat :app:compileDebugKotlin`
   - `.\gradlew.bat :app:lintDebug`, si el proyecto lo permite
5. Revisar `git diff` y documentar impactos funcionales.
6. Commit con mensaje claro:
   `fix(finance): correct loan principal amortization`
7. Push y PR con:
   - Resumen del cambio.
   - Pruebas ejecutadas.
   - Riesgos residuales.
   - Capturas si cambia UI.

## Prioridades

- P0: riesgo alto de perdida de datos, privacidad, saldos/deuda incorrectos o
  imposibilidad de verificar.
- P1: inconsistencias funcionales importantes o deuda tecnica que bloquea
  precision financiera.
- P2: mejoras de robustez, rendimiento, UX o mantenibilidad.
- P3: refinamientos no urgentes.

---

## OPS-000 - Habilitar entorno de build y pruebas

Prioridad: P0

Objetivo:
Garantizar que el proyecto se pueda compilar y probar de forma repetible antes
de cambiar logica financiera.

Contexto:
En la auditoria local `.\gradlew.bat :app:compileDebugKotlin --offline` no pudo
ejecutarse porque el entorno no tiene `JAVA_HOME` valido ni `java` en `PATH`.

Archivos probables:
- `gradle.properties`
- `README.md`
- configuracion local del entorno, no versionar secretos ni rutas personales

Analisis:
- Confirmar version de JDK requerida por el proyecto.
- Verificar compatibilidad entre Java 21, Android Gradle Plugin, Kotlin y
  Compose.
- Confirmar si el proyecto compila con dependencias cacheadas y con red.

Cambios esperados:
- Documentar setup minimo para Windows.
- Opcional: agregar nota de troubleshooting para `JAVA_HOME`.
- No versionar `local.properties`.

Pruebas:
- `.\gradlew.bat --version`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :app:testDebugUnitTest`

Criterios de aceptacion:
- El proyecto compila en entorno local configurado.
- Hay instrucciones claras para reproducir la compilacion.
- Cualquier fallo restante queda registrado con causa concreta.

---

## DATA-001 - Eliminar migraciones destructivas de Room

Prioridad: P0

Objetivo:
Evitar perdida total de datos financieros al cambiar el esquema de base de
datos.

Contexto:
`AppDatabase` usa `fallbackToDestructiveMigration(dropAllTables = true)`. En una
app financiera, esto no es aceptable para produccion.

Archivos probables:
- `app/src/main/java/com/ivan/finanzapp/data/local/AppDatabase.kt`
- nuevas migraciones Room
- pruebas instrumentadas o unitarias de migracion

Analisis:
- Listar versiones actuales del esquema y entidades version 5.
- Definir estrategia de migraciones para versiones futuras.
- Revisar si `exportSchema` debe pasar a `true`.

Cambios esperados:
- Quitar fallback destructivo.
- Activar `exportSchema = true` si se decide mantener historial de esquema.
- Agregar migraciones explicitas desde la version actual hacia la siguiente.

Pruebas:
- Test de migracion con Room.
- Compilacion KSP/Room.

Criterios de aceptacion:
- La app no borra tablas por fallback automatico.
- Las migraciones necesarias estan cubiertas por pruebas.
- El riesgo de perdida de datos queda documentado para usuarios existentes.

---

## SEC-001 - Desactivar logs sensibles de red en release

Prioridad: P0

Objetivo:
Evitar que notificaciones, montos, comercios, respuestas de IA o headers
sensibles terminen en logs.

Contexto:
`NetworkModule` configura `HttpLoggingInterceptor.Level.BODY` globalmente.

Archivos probables:
- `app/src/main/java/com/ivan/finanzapp/di/NetworkModule.kt`
- `app/build.gradle.kts`

Analisis:
- Confirmar si `BuildConfig.DEBUG` esta disponible.
- Revisar si el header `Authorization` queda redactado.
- Revisar logs de request/response de OpenRouter.

Cambios esperados:
- Usar `Level.BODY` solo en debug, o `Level.NONE` por defecto.
- Redactar `Authorization`.
- Considerar interceptor propio con redaccion de campos financieros.

Pruebas:
- Test unitario simple para configuracion debug/release si se abstrae factory.
- Revision manual de logs en debug.

Criterios de aceptacion:
- En release no se loguea body ni tokens.
- En debug se evita exponer API keys.
- El comportamiento queda documentado.

---

## SEC-002 - Controlar privacidad del fallback IA remota

Prioridad: P0

Objetivo:
Evitar enviar datos financieros o SMS no bancarios a OpenRouter sin control
explícito.

Contexto:
El pipeline acepta paquetes de SMS generales y, si fallan reglas locales, puede
usar IA local o remota. Esto puede filtrar mensajes privados que no son
movimientos bancarios.

Archivos probables:
- `TransactionProcessor.kt`
- `ParserDispatcher.kt`
- `SmsParser.kt`
- `TransactionAiClassifier.kt`
- UI de ajustes

Analisis:
- Definir politica: que tipos de notificacion pueden salir a la nube.
- Separar SMS bancarios verificados de SMS genericos.
- Revisar si se necesita consentimiento por proveedor y por banco.
- Definir redaccion previa: ultimos digitos, nombres, saldos, telefono.

Cambios esperados:
- Bloquear IA remota para SMS si no se detecta banco con alta confianza.
- Agregar preferencia explicita "permitir clasificacion en la nube".
- Marcar notificaciones dudosas como pendientes sin enviarlas.

Pruebas:
- Tests de `TransactionProcessor` con parser null y SMS no bancario.
- Tests de parser Bancolombia.

Criterios de aceptacion:
- Un SMS no bancario nunca se envia a OpenRouter.
- El usuario puede desactivar IA remota.
- Las transacciones dudosas quedan en revision, no mutan saldos automaticamente.

---

## SEC-003 - Revisar backups de datos cifrados

Prioridad: P1

Objetivo:
Evitar inconsistencias o restauraciones rotas entre DB cifrada y passphrase en
`EncryptedSharedPreferences`.

Contexto:
`AndroidManifest.xml` tiene `android:allowBackup="true"`. Si Android restaura DB
sin restaurar correctamente claves/prefs, la DB puede quedar ilegible. Si restaura
todo, hay implicaciones de privacidad.

Archivos probables:
- `AndroidManifest.xml`
- `res/xml/backup_rules.xml`
- `res/xml/data_extraction_rules.xml`

Analisis:
- Decidir si la app permite backup de datos financieros.
- Si si, disenar backup/export cifrado controlado por usuario.
- Si no, excluir DB y preferencias seguras de backups automaticos.

Cambios esperados:
- Definir reglas de backup explicitas.
- Documentar consecuencia para el usuario.

Pruebas:
- Verificacion de manifest.
- Prueba manual de backup/restore si aplica.

Criterios de aceptacion:
- No hay backup automatico ambiguo de DB/prefs financieras.
- La politica queda explicita.

---

## FIN-001 - Unificar fuente de verdad para deuda de tarjetas

Prioridad: P0

Objetivo:
Eliminar divergencias entre `credit_cards.currentDebt` y la suma de
`deferred_purchases`.

Contexto:
`currentDebt` se recalcula desde compras diferidas, pero vive persistido como
cache mutable. Si falla una operacion multi-tabla, queda inconsistente.

Archivos probables:
- `CreditCardEntity.kt`
- `CreditCardsViewModel.kt`
- `TransactionProcessor.kt`
- `CreditCardCalculator.kt`
- DAOs de tarjeta y compras diferidas

Analisis:
- Decidir si `currentDebt` se elimina, se convierte en campo derivado o se
  mantiene como cache transaccional.
- Mapear todas las escrituras que actualizan compras y deuda.
- Evaluar impacto en UI y queries.

Cambios esperados:
- Opcion preferida: derivar deuda desde compras activas en ViewModels/queries.
- Si se conserva cache, actualizarla solo dentro de una transaccion atomica.

Pruebas:
- Agregar compra y verificar deuda.
- Editar compra y verificar deuda.
- Pagar parcialmente y verificar deuda.
- Simular pago mayor a deuda.

Criterios de aceptacion:
- No existen dos fuentes de verdad sin sincronizacion atomica.
- La UI muestra deuda consistente despues de cada operacion.

---

## FIN-002 - Rehacer pago minimo de tarjeta

Prioridad: P0

Objetivo:
Calcular y nombrar correctamente el valor que debe pagar el usuario.

Contexto:
`minimumPayment()` no usa `minPaymentPercentage` ni `minPaymentFloor`; devuelve
cuotas facturadas del periodo. Esto no coincide con el pago minimo bancario.

Archivos probables:
- `CreditCardCalculator.kt`
- `CreditCardEntity.kt`
- pantallas de tarjetas y dashboard

Analisis:
- Separar conceptos:
  - `statementAmountDue`: saldo del extracto o cuotas facturadas.
  - `minimumPayment`: minimo exigido por banco.
  - `totalDebt`: deuda total pendiente.
- Definir si el MVP tendra extractos reales o calculo estimado.

Cambios esperados:
- Renombrar metodo actual si representa "pago del periodo".
- Implementar minimo estimado:
  `max(deuda * porcentaje, piso)` con tope en deuda, o formula especifica por
  banco si se configura.
- Mostrar etiqueta honesta: "estimado" si no hay extracto oficial.

Pruebas:
- Deuda cero.
- Deuda menor que piso.
- Deuda mayor que piso.
- Porcentaje distinto de 5%.
- Compra a cuotas sin extracto cerrado.

Criterios de aceptacion:
- Los campos `minPaymentPercentage` y `minPaymentFloor` tienen uso real o se
  eliminan.
- La UI no llama pago minimo a un valor que no lo es.

---

## FIN-003 - Corregir amortizacion de creditos

Prioridad: P0

Objetivo:
Evitar que la deuda restante de un credito baje por el valor total de la cuota
cuando parte de esa cuota corresponde a interes, seguros o comisiones.

Contexto:
`payInstallment()` resta `monthlyInstallmentAmount` completo de
`remainingAmount`.

Archivos probables:
- `LoanEntity.kt`
- `LoansViewModel.kt`
- nuevo `LoanCalculator.kt`
- nueva tabla de plan de pagos, si aplica

Analisis:
- Definir modo de credito:
  - cuota fija calculada por tasa y plazo.
  - cuota manual con desglose capital/interes.
  - plan de pagos importado/manual.
- Revisar si `monthlyInterestRate` es nominal mensual, efectivo mensual o solo
  dato informativo.

Cambios esperados:
- Crear calculadora de amortizacion.
- Registrar por cuota: capital pagado, interes, seguros/comisiones.
- Reducir `remainingAmount` solo por capital.

Pruebas:
- Prestamo sin interes.
- Prestamo con interes mensual.
- Ultima cuota ajustada.
- Pago cuando ya esta totalmente pagado.

Criterios de aceptacion:
- `remainingAmount` representa saldo de capital pendiente.
- La suma de capital pagado nunca supera el monto prestado.
- La cuota final deja saldo cero sin saldos negativos.

---

## FIN-004 - Redisenar pagos de tarjeta sin mutar monto original

Prioridad: P1

Objetivo:
Preservar el monto original de una compra y registrar pagos como eventos
separados.

Contexto:
`distributePayment()` reduce `totalAmount` para pagos parciales. Esto destruye
historia y dificulta auditoria.

Archivos probables:
- `DeferredPurchaseEntity.kt`
- nuevo `CreditCardPaymentAllocationEntity.kt`
- `CreditCardCalculator.kt`
- `CreditCardsViewModel.kt`

Analisis:
- Definir modelo:
  - compra original.
  - saldo de capital pendiente.
  - pagos asignados.
  - cuotas facturadas/pagadas.
- Definir si la asignacion es FIFO, mayor tasa, vencimiento o manual.

Cambios esperados:
- Agregar campo o tabla para saldo pendiente/pagos aplicados.
- Mantener `originalAmount` inmutable.
- Permitir explicar al usuario a que compra se aplico cada pago.

Pruebas:
- Pago parcial menor que una cuota.
- Pago exacto de una cuota.
- Pago que liquida varias compras.
- Pago mayor a deuda.

Criterios de aceptacion:
- El historial de compra original no cambia por pagos.
- La deuda baja correctamente.
- La asignacion es visible y auditable.

---

## FIN-005 - Separar extractos de tarjeta de compras diferidas

Prioridad: P1

Objetivo:
Modelar estados reales de tarjeta: compras no cortadas, extracto cerrado, pago
minimo, pago total y fecha limite.

Contexto:
La app calcula cuotas por fecha de corte, pero no guarda extractos. Sin
extractos reales, no puede reconciliar lo que el banco cobra.

Archivos probables:
- nuevas entidades Room:
  - `CreditCardStatementEntity`
  - `CreditCardStatementItemEntity`
  - `CreditCardPaymentEntity`
- DAOs y ViewModels de tarjetas

Analisis:
- Definir campos minimos de extracto.
- Decidir si se crean manualmente o desde notificaciones.
- Definir relacion con pagos.

Cambios esperados:
- Estado de tarjeta con:
  - deuda total.
  - saldo no facturado.
  - saldo facturado.
  - minimo a pagar.
  - pago aplicado al extracto.

Pruebas:
- Compra antes del corte.
- Compra despues del corte.
- Pago antes y despues del cierre.
- Extracto pagado parcialmente.

Criterios de aceptacion:
- La UI diferencia deuda total de monto a pagar este ciclo.
- La app puede explicar por que una compra entra o no entra en el proximo pago.

---

## FIN-006 - Reetiquetar y robustecer DTI / flujo de caja

Prioridad: P1

Objetivo:
Evitar que el usuario interprete una metrica de flujo mensual como DTI completo.

Contexto:
La app calcula `cuotas mensuales / ingresos del mes`. Eso es util, pero depende
de transacciones detectadas este mes y no necesariamente de ingresos recurrentes.

Archivos probables:
- `AssetsViewModel.kt`
- `DashboardViewModel.kt`
- `AssetsScreen.kt`
- nueva entidad `IncomeSourceEntity`

Analisis:
- Definir metricas:
  - ingreso recurrente mensual esperado.
  - ingresos reales del mes.
  - deuda mensual comprometida.
  - DTI estimado.
  - flujo de caja disponible.
- Revisar si transferencias propias deben excluirse.

Cambios esperados:
- Renombrar UI actual a "Carga mensual de deuda".
- Agregar ingreso recurrente manual o tabla de fuentes de ingreso.
- Calcular DTI estimado con ingreso recurrente, no solo movimientos del mes.

Pruebas:
- Mes sin ingresos detectados.
- Ingreso manual recurrente.
- Ingresos extraordinarios.
- Deudas activas y deudas cerradas.

Criterios de aceptacion:
- La UI no sobrevende precision.
- El usuario puede distinguir ingresos reales, recurrentes y extraordinarios.

---

## FIN-007 - Calcular patrimonio neto real

Prioridad: P1

Objetivo:
Mostrar patrimonio neto como activos menos pasivos, no solo activos brutos.

Contexto:
`totalAssets` suma efectivo y activos manuales, pero no resta tarjetas ni
creditos.

Archivos probables:
- `AssetsViewModel.kt`
- `AssetsScreen.kt`
- entidades de creditos y tarjetas

Analisis:
- Definir:
  - activos liquidos.
  - activos no liquidos.
  - pasivos de tarjeta.
  - pasivos de creditos.
  - patrimonio neto.
- Evitar doble conteo de activos financiados con deuda.

Cambios esperados:
- Agregar `totalLiabilities`.
- Agregar `netWorth`.
- Renombrar "Patrimonio Total Estimado" si se mantiene como activos brutos.

Pruebas:
- Sin deudas.
- Con tarjeta.
- Con credito.
- Con activo manual financiado.

Criterios de aceptacion:
- Patrimonio neto = activos - pasivos.
- La UI muestra activos y pasivos por separado.

---

## TX-001 - Hacer atomicas las operaciones financieras multi-tabla

Prioridad: P0

Objetivo:
Evitar estados parciales cuando una operacion toca transacciones, cuentas,
tarjetas, compras diferidas o creditos.

Contexto:
Los ViewModels y `TransactionProcessor` ejecutan varias escrituras Room en
secuencia sin `@Transaction`.

Archivos probables:
- nuevos repositories/usecases
- DAOs actuales
- `AppDatabase`
- `CreditCardsViewModel.kt`
- `LoansViewModel.kt`
- `AssetsViewModel.kt`
- `TransactionProcessor.kt`

Analisis:
- Listar operaciones criticas:
  - registrar ingreso.
  - registrar gasto.
  - registrar compra TC.
  - pagar TC.
  - pagar cuota de credito.
  - borrar/revertir movimiento.
- Definir boundaries transaccionales.

Cambios esperados:
- Mover escritura compuesta fuera de ViewModels.
- Usar `db.withTransaction { ... }`.
- ViewModels solo llaman usecases.

Pruebas:
- Test Room que verifica rollback ante excepcion simulada.
- Test de consistencia despues de cada operacion.

Criterios de aceptacion:
- No hay operacion financiera compuesta fuera de una transaccion.
- Los ViewModels no contienen logica contable compleja.

---

## TX-002 - Implementar reversos y correcciones contables

Prioridad: P0

Objetivo:
Permitir borrar o corregir transacciones sin dejar saldos/deudas inflados o
descuadrados.

Contexto:
`deleteTransaction()` borra el registro, pero no revierte saldo de cuenta ni
deuda asociada.

Archivos probables:
- `TransactionsViewModel.kt`
- `TransactionDao.kt`
- nuevo usecase `ReverseTransactionUseCase`
- entidades de auditoria/reverso

Analisis:
- Definir si borrar fisicamente o crear reverso.
- Mapear efectos por tipo:
  - INGRESO suma saldo, reverso resta.
  - GASTO resta saldo, reverso suma.
  - GASTO_TC crea deuda, reverso elimina/ajusta compra.
  - PAGO_TC reduce deuda, reverso restaura deuda.
  - TRANSFERENCIA requiere dos lados en modelo futuro.

Cambios esperados:
- Preferir reversos auditables antes que delete fisico.
- Si se permite delete, debe revertir efectos asociados atomicamente.

Pruebas:
- Borrar ingreso.
- Borrar gasto.
- Borrar compra TC.
- Borrar pago TC.
- Corregir monto y categoria.

Criterios de aceptacion:
- Despues de revertir, saldos y deuda quedan como antes.
- Existe rastro auditable de la correccion.

---

## TX-003 - Redisenar transferencias como movimiento entre cuentas

Prioridad: P1

Objetivo:
Evitar que una transferencia propia sea tratada como gasto real.

Contexto:
`TRANSFERENCIA` se suma en gastos por categoria y se descuenta de cuenta. No hay
cuenta destino ni relacion entre salida y entrada.

Archivos probables:
- `TransactionType`
- `TransactionEntity`
- `TransactionDao`
- parsers
- UI de movimientos

Analisis:
- Separar:
  - transferencia a terceros.
  - transferencia entre cuentas propias.
  - retiro/consignacion.
- Definir modelo de doble entrada.

Cambios esperados:
- Agregar `transferGroupId` o migrar a `transaction_entries`.
- Excluir transferencias propias de gastos y DTI.
- Permitir vincular salida e ingreso.

Pruebas:
- Transferencia propia no altera patrimonio neto.
- Transferencia a tercero si cuenta como salida.
- Dashboard no duplica ingresos/gastos.

Criterios de aceptacion:
- Las transferencias no distorsionan gastos si son entre cuentas propias.
- El usuario puede clasificar el tipo de transferencia.

---

## PARSER-001 - Mejorar resolucion de cuentas

Prioridad: P0

Objetivo:
Evitar asignar movimientos a la primera cuenta del tipo encontrado.

Contexto:
`AccountResolver` usa `firstOrNull()`. Con varias tarjetas o cuentas del mismo
banco, la asignacion puede ser incorrecta.

Archivos probables:
- `AccountEntity.kt`
- `CreditCardEntity.kt`
- `AccountResolver.kt`
- parsers
- UI de ajustes

Analisis:
- Revisar si notificaciones incluyen ultimos 4 digitos, alias o producto.
- Agregar campos opcionales:
  - `bankSource`
  - `accountMask`
  - `cardLast4`
  - `notificationAliases`
- Definir fallback: sin asignar + revision.

Cambios esperados:
- Resolver por fuente + ultimos digitos/alias cuando exista.
- Si hay ambiguedad, no mutar saldo automaticamente.

Pruebas:
- Dos tarjetas del mismo banco.
- Dos cuentas de ahorro.
- Notificacion con ultimos digitos.
- Notificacion ambigua.

Criterios de aceptacion:
- No se asigna arbitrariamente una transaccion ambigua.
- El usuario puede corregir y aprender asignacion.

---

## PARSER-002 - Corregir clasificacion de compras Davivienda debito vs credito

Prioridad: P0

Objetivo:
Evitar que compras con cuenta debito se registren como deuda de tarjeta.

Contexto:
`DaviviendaParser` clasifica toda compra como `GASTO_TC`.

Archivos probables:
- `DaviviendaParser.kt`
- `BankParser.kt`
- tests de parsers

Analisis:
- Recopilar ejemplos reales de notificaciones Davivienda debito y credito.
- Identificar tokens de tarjeta, cuenta, franquicia, ultimos digitos.
- Definir confianza cuando no se puede distinguir.

Cambios esperados:
- Clasificar compra como `GASTO_TC` solo con evidencia de tarjeta de credito.
- Si no hay evidencia, usar `GASTO` o `needsReview` segun confianza.

Pruebas:
- Compra TC.
- Compra debito.
- Pago tarjeta.
- Transferencia recibida/enviada.
- Mensaje ambiguo.

Criterios de aceptacion:
- Una compra debito no incrementa deuda de tarjeta.
- Las compras ambiguas no mutan deuda automaticamente.

---

## PARSER-003 - Fortalecer deduplicacion de transacciones

Prioridad: P1

Objetivo:
Reducir falsos duplicados y falsos negativos en notificaciones repetidas.

Contexto:
`TransactionIdGenerator` usa paquete, monto, comercio y timestamp redondeado al
minuto. Dos compras iguales en el mismo comercio y minuto pueden colisionar.

Archivos probables:
- `TransactionIdGenerator.kt`
- parsers
- `TransactionEntity.kt`

Analisis:
- Identificar campos disponibles: titulo, texto, ultimos digitos, saldo
  resultante, codigo de autorizacion.
- Evaluar si guardar `rawNotificationHash` y `bankEventId`.

Cambios esperados:
- Incluir mas contexto estable en hash.
- Separar id interno UUID de dedupe key unica.
- Agregar indice unico para dedupe key.

Pruebas:
- Duplicado exacto.
- Dos compras iguales en el mismo minuto.
- Misma compra con notificacion actualizada.

Criterios de aceptacion:
- No se pierde una compra legitima por colision simple.
- Los duplicados reales siguen ignorandose.

---

## PARSER-004 - Hacer que correcciones de categoria aprendan

Prioridad: P2

Objetivo:
Usar `CategoryResolver.learnMapping()` cuando el usuario corrige una categoria.

Contexto:
La funcion existe, pero la UI de transacciones solo actualiza la transaccion.

Archivos probables:
- `TransactionsViewModel.kt`
- `CategoryResolver.kt`
- `MerchantCategoryMappingDao.kt`

Analisis:
- Confirmar que el comercio normalizado sea estable.
- Decidir si aprender tambien "sin categoria".
- Definir UI para olvidar mapeos si aprende mal.

Cambios esperados:
- Inyectar `CategoryResolver` en `TransactionsViewModel`.
- Al corregir categoria con comercio valido, guardar mapping.

Pruebas:
- Corregir categoria aprende mapping.
- Nueva transaccion del mismo comercio usa mapping.
- Comercio vacio no aprende.

Criterios de aceptacion:
- El sistema mejora con correcciones manuales.
- No aprende mapeos nulos accidentalmente.

---

## DATA-002 - Introducir ledger contable de doble entrada

Prioridad: P1

Objetivo:
Dar una base contable robusta para saldos, transferencias, pagos de deuda y
auditoria.

Contexto:
Actualmente una `TransactionEntity` guarda monto y cuenta opcional. Esto no
modela correctamente transferencias, pagos de tarjeta, reversos ni doble efecto
contable.

Archivos probables:
- nuevas entidades:
  - `LedgerTransactionEntity`
  - `LedgerEntryEntity`
- DAOs nuevos
- migraciones Room
- usecases financieros

Analisis:
- Definir cuentas contables:
  - activos: efectivo, bancos, billeteras.
  - pasivos: tarjetas, creditos.
  - ingresos.
  - gastos.
- Definir relacion con transacciones bancarias detectadas.

Cambios esperados:
- Cada movimiento tiene entradas balanceadas.
- Los saldos se derivan de entradas o se actualizan con snapshots
  reconciliados.

Pruebas:
- Toda transaccion balancea debitos y creditos.
- Transferencia entre cuentas no cambia patrimonio.
- Pago de tarjeta reduce activo y pasivo.

Criterios de aceptacion:
- La contabilidad queda auditable.
- Saldos pueden reconstruirse desde el ledger.

---

## DATA-003 - Reconciliacion de saldos manuales vs automaticos

Prioridad: P1

Objetivo:
Evitar saltos silenciosos cuando una notificacion trae saldo disponible y
sobrescribe el saldo calculado.

Contexto:
`setAbsoluteBalance()` reemplaza saldo y marca `isManualBalance = false`. No se
registra ajuste/reconciliacion.

Archivos probables:
- `AccountDao.kt`
- `TransactionProcessor.kt`
- nueva entidad `BalanceReconciliationEntity`
- UI de cuentas

Analisis:
- Definir diferencia entre:
  - saldo observado por banco.
  - saldo calculado por app.
  - ajuste manual.
- Guardar delta de reconciliacion.

Cambios esperados:
- Cuando llega saldo absoluto, crear evento de reconciliacion si hay diferencia.
- Mostrar al usuario diferencias grandes.

Pruebas:
- Saldo absoluto igual al calculado.
- Saldo absoluto diferente.
- Cuenta manual sin saldo automatico.

Criterios de aceptacion:
- Ningun ajuste de saldo queda invisible.
- El usuario puede auditar por que cambio un saldo.

---

## DATA-004 - Endurecer borrado de cuentas, tarjetas y creditos

Prioridad: P1

Objetivo:
Evitar que el usuario borre productos financieros con deuda, historial o
relaciones activas sin manejo explicito.

Contexto:
Eliminar una cuenta puede borrar tarjeta por cascada y dejar transacciones o
creditos sin cuenta.

Archivos probables:
- `SettingsViewModel.kt`
- DAOs
- UI de ajustes
- entidades relacionadas

Analisis:
- Definir politicas:
  - archivar cuenta.
  - bloquear delete si hay deuda.
  - mantener historial.
  - reasignar movimientos.
- Revisar cascadas actuales.

Cambios esperados:
- Reemplazar delete directo por `archive`.
- Confirmaciones con impacto claro.
- Bloquear borrado de tarjeta con deuda activa salvo cierre/liquidacion.

Pruebas:
- Borrar cuenta sin movimientos.
- Borrar cuenta con movimientos.
- Borrar tarjeta con compras activas.
- Borrar cuenta vinculada a credito.

Criterios de aceptacion:
- No se pierde historial financiero accidentalmente.
- Las relaciones quedan consistentes.

---

## PERF-001 - Evitar cargar todo el historial en pantallas principales

Prioridad: P1

Objetivo:
Mejorar rendimiento y consumo de memoria con muchos movimientos.

Contexto:
Dashboard, Assets y Transactions usan `observeAll()` de transacciones. Esto no
escala.

Archivos probables:
- `TransactionDao.kt`
- `DashboardViewModel.kt`
- `AssetsViewModel.kt`
- `TransactionsViewModel.kt`

Analisis:
- Identificar datos requeridos por pantalla.
- Crear queries agregadas por fecha, limite y filtros.
- Evaluar Paging 3 para historial.

Cambios esperados:
- Dashboard: `observeRecent(limit = 5)` y agregados de mes.
- Assets: query solo de ingresos del mes.
- Transactions: Paging o filtros en SQL.

Pruebas:
- Tests DAO con rangos de fecha.
- Perfil basico con dataset grande.

Criterios de aceptacion:
- Pantallas principales no observan todo el historial salvo que sea necesario.
- La UI sigue reactiva.

---

## ARCH-001 - Crear capa Repository/UseCase para dominio financiero

Prioridad: P1

Objetivo:
Sacar logica contable y financiera de ViewModels y del listener de
notificaciones.

Contexto:
Los ViewModels contienen escrituras multi-tabla y reglas de negocio.

Archivos probables:
- nuevo paquete `domain/usecase/finance`
- nuevo paquete `data/repository`
- ViewModels afectados

Analisis:
- Separar:
  - lectura para UI.
  - comandos financieros.
  - calculadoras puras.
  - integracion Room.

Cambios esperados:
- UseCases:
  - `RegisterIncomeUseCase`
  - `RegisterExpenseUseCase`
  - `RegisterCreditCardPurchaseUseCase`
  - `PayCreditCardUseCase`
  - `PayLoanInstallmentUseCase`
  - `ReverseTransactionUseCase`

Pruebas:
- Unit tests de usecases con fakes o Room in-memory.

Criterios de aceptacion:
- ViewModels coordinan UI, no contabilidad.
- `TransactionProcessor` delega efectos financieros.

---

## ARCH-002 - Separar parsing, clasificacion y contabilizacion

Prioridad: P1

Objetivo:
Evitar que una clasificacion de baja confianza modifique saldos o deuda
directamente.

Contexto:
`TransactionProcessor` parsea, clasifica, persiste y muta saldos/deuda en una
misma ruta.

Archivos probables:
- `TransactionProcessor.kt`
- entidades de notificacion pendiente
- usecases financieros

Analisis:
- Definir pipeline:
  - evento crudo.
  - parse result.
  - propuesta de movimiento.
  - confirmacion.
  - contabilizacion.
- Determinar umbrales por tipo.

Cambios esperados:
- Baja confianza: guardar pendiente, no tocar saldos.
- Alta confianza: contabilizar via usecase atomico.
- Permitir revisar antes de aplicar.

Pruebas:
- Parser alta confianza aplica movimiento.
- Parser baja confianza no aplica saldo.
- IA remota baja confianza no aplica saldo.

Criterios de aceptacion:
- Ningun evento incierto cambia el estado financiero real.
- El usuario puede confirmar y aplicar.

---

## TEST-001 - Suite de pruebas para calculadora de tarjetas

Prioridad: P0

Objetivo:
Proteger formulas y reglas de tarjeta contra regresiones.

Archivos probables:
- `CreditCardCalculatorTest.kt`

Casos minimos:
- `availableCredit` no baja de cero.
- `usagePercentage` con limite cero.
- cuotas restantes con pagos mayores al total.
- compra antes/despues del corte.
- pago minimo/de periodo.
- distribucion de pago parcial, exacto, mayor a deuda.
- tasa EA a mensual.

Criterios de aceptacion:
- Tests cubren bordes matematicos.
- Nombres de tests explican regla de negocio.

---

## TEST-002 - Suite de pruebas para prestamos

Prioridad: P0

Objetivo:
Validar amortizacion y pago de cuotas.

Archivos probables:
- `LoanCalculatorTest.kt`
- tests de `PayLoanInstallmentUseCase`

Casos minimos:
- credito sin interes.
- credito con interes.
- cuota menor/interes mayor.
- ultima cuota.
- pago duplicado.
- cuenta vinculada sin saldo suficiente, si se implementa validacion.

Criterios de aceptacion:
- La deuda restante solo baja por capital.
- No hay saldos negativos no intencionales.

---

## TEST-003 - Suite de pruebas para parsers y deduplicacion

Prioridad: P0

Objetivo:
Evitar clasificaciones bancarias peligrosas.

Archivos probables:
- `DaviviendaParserTest.kt`
- `NequiParserTest.kt`
- `DaviplataParserTest.kt`
- `SmsParserTest.kt`
- `TransactionIdGeneratorTest.kt`

Casos minimos:
- compra debito vs credito.
- pago tarjeta.
- transferencia recibida/enviada.
- retiro.
- notificacion promocional.
- dos compras iguales en mismo minuto.

Criterios de aceptacion:
- Clasificaciones con impacto en deuda tienen pruebas explicitas.
- Duplicados reales se ignoran sin colisionar movimientos legitimos.

---

## TEST-004 - Pruebas Room de consistencia financiera

Prioridad: P1

Objetivo:
Validar operaciones multi-tabla con DB real in-memory.

Archivos probables:
- tests con Room in-memory
- usecases financieros

Casos minimos:
- registrar compra TC crea transaccion y deuda.
- pagar TC reduce deuda y cuenta fondeadora.
- pagar credito reduce saldo y registra movimiento.
- reverso restaura estado.
- fallo simulado hace rollback.

Criterios de aceptacion:
- Las invariantes financieras se prueban contra Room.
- Cada bug critico nuevo debe tener test de regresion.

---

## UI-001 - Mejorar lenguaje financiero en pantalla

Prioridad: P2

Objetivo:
Evitar terminos que prometen precision no implementada.

Contexto:
Algunos textos como "DTI", "pago minimo" o "patrimonio total" pueden inducir a
error.

Archivos probables:
- `AssetsScreen.kt`
- `CreditCardsScreen.kt`
- `DashboardScreen.kt`

Analisis:
- Revisar cada etiqueta financiera.
- Marcar valores estimados.
- Agregar microcopy discreto donde haga falta.

Cambios esperados:
- "Carga mensual de deuda" en vez de DTI si no hay ingreso recurrente.
- "Pago estimado del periodo" si no hay extracto.
- "Activos estimados" y "Patrimonio neto" separados.

Pruebas:
- Revision visual.
- Screenshot si hay cambios importantes.

Criterios de aceptacion:
- La UI no confunde conceptos contables.
- El usuario entiende que valores son estimados.

---

## ROADMAP sugerido de ejecucion

### Fase 0 - Base verificable

1. OPS-000
2. TEST-001
3. TEST-002
4. TEST-003

### Fase 1 - Riesgos criticos

1. SEC-001
2. SEC-002
3. DATA-001
4. TX-001
5. FIN-003
6. FIN-002
7. PARSER-002

### Fase 2 - Consistencia contable

1. FIN-001
2. TX-002
3. DATA-003
4. DATA-004
5. PARSER-001

### Fase 3 - Modelo financiero robusto

1. FIN-004
2. FIN-005
3. TX-003
4. DATA-002
5. FIN-006
6. FIN-007

### Fase 4 - Escalabilidad y experiencia

1. PERF-001
2. ARCH-001
3. ARCH-002
4. PARSER-004
5. UI-001

## Definicion de terminado por PR

Un PR se considera listo cuando:

- Tiene alcance acotado a una tarea o grupo pequeno de tareas relacionadas.
- Incluye pruebas nuevas o justifica por que no aplican.
- Ejecuta comandos de verificacion y reporta resultado.
- No toca archivos no relacionados.
- No sube `finanzapp_codebase.txt`, `local.properties`, artefactos de build ni
  secretos.
- Deja la app en estado compilable.
- Incluye notas de migracion si cambia Room.

