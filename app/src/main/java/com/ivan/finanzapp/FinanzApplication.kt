package com.ivan.finanzapp

import android.app.Application
import com.ivan.finanzapp.data.local.DefaultCategories
import com.ivan.finanzapp.data.local.dao.AssetDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class principal.
 *
 * Responsabilidades:
 * 1. Inicializar Hilt (punto de entrada de DI).
 * 2. Sembrar las categorías por defecto en la base de datos la primera
 *    vez que se instala la app (si la tabla está vacía).
 */
@HiltAndroidApp
class FinanzApplication : Application() {

    @Inject
    lateinit var categoryDao: CategoryDao

    @Inject
    lateinit var assetDao: AssetDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        seedDefaultCategoriesIfNeeded()
        cleanLegacySueldoAssets()
    }

    /**
     * Inserta las categorías predeterminadas solo si la tabla está vacía.
     * Usa [CategoryDao.insertAll] con `OnConflictStrategy.IGNORE` para
     * que sea idempotente: si ya existen, no hace nada.
     */
    private fun seedDefaultCategoriesIfNeeded() {
        appScope.launch {
            if (categoryDao.count() == 0) {
                categoryDao.insertAll(DefaultCategories.ALL)
            }
        }
    }

    private fun cleanLegacySueldoAssets() {
        appScope.launch {
            assetDao.deleteSueldoAssets()
        }
    }
}
