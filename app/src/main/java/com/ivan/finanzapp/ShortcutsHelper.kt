package com.ivan.finanzapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object ShortcutsHelper {
    fun registerShortcuts(context: Context) {
        val addIntent = Intent(Intent.ACTION_VIEW, Uri.parse("finanzapp://transactions?action=add_expense")).apply {
            `package` = context.packageName
        }

        val addShortcut = ShortcutInfoCompat.Builder(context, "add_transaction")
            .setShortLabel(context.getString(R.string.shortcut_add_label))
            .setLongLabel(context.getString(R.string.shortcut_add_long_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add_adaptive))
            .setIntent(addIntent)
            .build()

        val unclassifiedIntent = Intent(Intent.ACTION_VIEW, Uri.parse("finanzapp://transactions?action=view_unclassified")).apply {
            `package` = context.packageName
        }

        val unclassifiedShortcut = ShortcutInfoCompat.Builder(context, "view_unclassified")
            .setShortLabel(context.getString(R.string.shortcut_unclassified_label))
            .setLongLabel(context.getString(R.string.shortcut_unclassified_long_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_unclassified_adaptive))
            .setIntent(unclassifiedIntent)
            .build()

        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(addShortcut, unclassifiedShortcut))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
