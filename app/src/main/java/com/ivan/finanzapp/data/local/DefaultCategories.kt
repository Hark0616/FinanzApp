package com.ivan.finanzapp.data.local

import com.ivan.finanzapp.data.local.entity.CategoryEntity

/**
 * Categorías iniciales que se insertan al crear la base de datos por
 * primera vez. El usuario puede agregar/editar más desde la pantalla
 * de Categorías.
 *
 * El campo [CategoryEntity.icon] es el nombre de un icono de
 * androidx.compose.material.icons.Icons.Filled / Icons.Default.
 */
object DefaultCategories {

    const val OTROS_ID = "cat_otros"

    val ALL: List<CategoryEntity> = listOf(
        CategoryEntity(id = "cat_mercado", name = "Mercado", icon = "ShoppingCart", color = "#4CAF50", isDefault = true),
        CategoryEntity(id = "cat_transporte", name = "Transporte", icon = "DirectionsCar", color = "#2196F3", isDefault = true),
        CategoryEntity(id = "cat_restaurantes", name = "Restaurantes", icon = "Restaurant", color = "#FF9800", isDefault = true),
        CategoryEntity(id = "cat_suscripciones", name = "Suscripciones", icon = "Subscriptions", color = "#9C27B0", isDefault = true),
        CategoryEntity(id = "cat_salud", name = "Salud", icon = "LocalHospital", color = "#F44336", isDefault = true),
        CategoryEntity(id = "cat_servicios", name = "Servicios públicos", icon = "Bolt", color = "#FFC107", isDefault = true),
        CategoryEntity(id = "cat_hogar", name = "Hogar", icon = "Home", color = "#795548", isDefault = true),
        CategoryEntity(id = "cat_entretenimiento", name = "Entretenimiento", icon = "Movie", color = "#E91E63", isDefault = true),
        CategoryEntity(id = "cat_educacion", name = "Educación", icon = "School", color = "#3F51B5", isDefault = true),
        CategoryEntity(id = "cat_ingresos", name = "Ingresos", icon = "TrendingUp", color = "#009688", isDefault = true),
        CategoryEntity(id = "cat_pago_tc", name = "Pago tarjeta de crédito", icon = "CreditCard", color = "#607D8B", isDefault = true),
        CategoryEntity(id = OTROS_ID, name = "Otros", icon = "Category", color = "#9E9E9E", isDefault = true)
    )
}
