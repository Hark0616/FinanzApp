package com.ivan.finanzapp.domain.usecase

import com.ivan.finanzapp.data.local.DefaultCategories
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.MerchantCategoryMappingDao
import com.ivan.finanzapp.data.local.entity.MerchantCategoryMappingEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resuelve la categoría de una transacción usando 3 niveles, en orden de
 * prioridad (más rápido y gratis primero):
 *
 * 1. Diccionario local de comercios conocidos (hardcoded, sin costo).
 * 2. Mapeos aprendidos de correcciones previas del usuario
 *    ([MerchantCategoryMappingDao] - tabla `merchant_category_mappings`).
 * 3. Si nada de lo anterior coincide, el llamador debe usar la categoría
 *    sugerida por el LLM (Nivel 3, fuera de esta clase porque requiere
 *    la llamada de red de [com.ivan.finanzapp.data.remote.TransactionAiClassifier]).
 *
 * Si ninguno de los niveles resuelve la categoría, se usa "Otros".
 */
@Singleton
class CategoryResolver @Inject constructor(
    private val categoryDao: CategoryDao,
    private val mappingDao: MerchantCategoryMappingDao
) {

    /**
     * Diccionario inicial Nivel 1: comercio (substring, en mayúsculas) -> id de categoría.
     *
     * Esto es un punto de partida; Iván puede ir ampliándolo según los
     * comercios que más le aparezcan. La búsqueda es por "contiene", para
     * tolerar variaciones como "EXITO BARRANQUILLA" o "EXITO EXPRESS".
     */
    private val level1Dictionary: Map<String, String> = mapOf(
        // Mercado
        "EXITO" to "cat_mercado",
        "CARULLA" to "cat_mercado",
        "D1" to "cat_mercado",
        "ARA" to "cat_mercado",
        "JUSTO Y BUENO" to "cat_mercado",
        "OLIMPICA" to "cat_mercado",
        "ALKOSTO" to "cat_mercado",

        // Transporte
        "UBER" to "cat_transporte",
        "DIDI" to "cat_transporte",
        "CABIFY" to "cat_transporte",
        "TERPEL" to "cat_transporte",
        "EDS" to "cat_transporte", // estaciones de servicio (gasolina)

        // Restaurantes
        "RAPPI" to "cat_restaurantes",
        "MCDONALDS" to "cat_restaurantes",
        "KFC" to "cat_restaurantes",
        "CREPES" to "cat_restaurantes",

        // Suscripciones
        "NETFLIX" to "cat_suscripciones",
        "SPOTIFY" to "cat_suscripciones",
        "AMAZON PRIME" to "cat_suscripciones",
        "DISNEY" to "cat_suscripciones",
        "YOUTUBE PREMIUM" to "cat_suscripciones",
        "OPENROUTER" to "cat_suscripciones",
        "ANTHROPIC" to "cat_suscripciones",
        "CLAUDE.AI" to "cat_suscripciones",
        "GOOGLE ONE" to "cat_suscripciones",
        "ICLOUD" to "cat_suscripciones",

        // Servicios públicos
        "EMCALI" to "cat_servicios",
        "EPM" to "cat_servicios",
        "CLARO" to "cat_servicios",
        "MOVISTAR" to "cat_servicios",
        "TIGO" to "cat_servicios",
        "WOM" to "cat_servicios"
    )

    /**
     * Mapeo de nombres de categoría (como los sugiere el LLM en español,
     * ver prompt en [com.ivan.finanzapp.data.remote.TransactionAiClassifier])
     * a sus ids correspondientes en [DefaultCategories].
     */
    private val nameToId: Map<String, String> = mapOf(
        "Mercado" to "cat_mercado",
        "Transporte" to "cat_transporte",
        "Restaurantes" to "cat_restaurantes",
        "Suscripciones" to "cat_suscripciones",
        "Salud" to "cat_salud",
        "Servicios públicos" to "cat_servicios",
        "Hogar" to "cat_hogar",
        "Entretenimiento" to "cat_entretenimiento",
        "Educación" to "cat_educacion",
        "Ingresos" to "cat_ingresos",
        "Pago tarjeta de crédito" to "cat_pago_tc",
        "Otros" to DefaultCategories.OTROS_ID
    )

    /**
     * Devuelve el id de categoría para el [merchant] dado, o "cat_otros"
     * si no se encuentra coincidencia en los niveles 1 y 2.
     *
     * @param suggestedFromAi categoría sugerida por el LLM (nivel 3), si
     *        ya se invocó al [com.ivan.finanzapp.data.remote.TransactionAiClassifier]
     *        para esta transacción. Tiene prioridad sobre "Otros" pero NO
     *        sobre los niveles 1 y 2 (las reglas locales siempre ganan,
     *        por consistencia y porque son gratis).
     */
    suspend fun resolve(merchant: String?, suggestedFromAi: String? = null): String {
        if (merchant.isNullOrBlank()) {
            return suggestedFromAi?.let { nameToId[it] } ?: DefaultCategories.OTROS_ID
        }

        val normalized = normalize(merchant)

        // Nivel 2: mapeo aprendido de correcciones del usuario
        mappingDao.getByMerchant(normalized)?.let { return it.categoryId }

        // Nivel 1: diccionario local
        level1Dictionary.entries.firstOrNull { (key, _) -> normalized.contains(key) }
            ?.let { return it.value }

        // Nivel 3: sugerencia del LLM, si está disponible
        suggestedFromAi?.let { nameToId[it] }?.let { return it }

        return DefaultCategories.OTROS_ID
    }

    /**
     * Registra (o actualiza) un mapeo comercio -> categoría cuando el
     * usuario corrige manualmente la categoría de una transacción.
     * Esto alimenta el Nivel 2 para futuras transacciones del mismo comercio.
     */
    suspend fun learnMapping(merchant: String, categoryId: String) {
        if (merchant.isBlank()) return
        mappingDao.upsert(
            MerchantCategoryMappingEntity(
                merchantKey = normalize(merchant),
                categoryId = categoryId
            )
        )
    }

    private fun normalize(merchant: String): String =
        merchant.trim().uppercase().replace(Regex("\\s+"), " ")
}
