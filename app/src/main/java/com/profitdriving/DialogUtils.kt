package com.profitdriving

import android.content.Context
import androidx.appcompat.app.AlertDialog

object DialogUtils {

    fun confirmDelete(
        context: Context,
        title: String = "Excluir",
        message: String = "Tem certeza que deseja excluir este item?",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Excluir") { _, _ -> onConfirm() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun showInfo(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "OK"
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText, null)
            .show()
    }

    fun showError(
        context: Context,
        message: String,
        title: String = "Erro"
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
