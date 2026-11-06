package com.hunmeng.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Получает ответ системного установщика: показывает окно подтверждения или ошибку. */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.update_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // приложение перезапустится в новой версии
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // пользователь нажал «Отмена»
            else -> {
                val details = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val text = context.getString(R.string.update_failed) +
                    if (details.isNullOrBlank()) "" else "\n$details"
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}
