package com.example.toolbox.ledger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.toolbox.MainActivity
import com.example.toolbox.R
import com.example.toolbox.ui.AppPreferences
import com.example.toolbox.ui.ToolboxStrings
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_OPEN_LEDGER = "com.example.toolbox.action.OPEN_LEDGER"
const val ACTION_ADD_LEDGER_ENTRY = "com.example.toolbox.action.ADD_LEDGER_ENTRY"

class LedgerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LedgerWidget.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Rebuilds the RemoteViews content for every placed Ledger widget.
 * Performs SQLite reads — must be called off the main thread.
 */
object LedgerWidget {

    private const val REQUEST_OPEN = 0
    private const val REQUEST_ADD = 1

    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(appContext, LedgerWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        val strings = ToolboxStrings(AppPreferences(appContext).language())
        val entries = runCatching {
            LedgerStore(appContext).use { it.queryVisibleEntries() }
        }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val summary = LedgerCalculator.summarize(entries, now)
        val month = LedgerCalculator.monthSummary(
            entries,
            YearMonth.from(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())),
        )

        val views = RemoteViews(appContext.packageName, R.layout.widget_ledger).apply {
            setTextViewText(R.id.widget_ledger_title, strings.ledgerWidgetTitle)
            setTextViewText(
                R.id.widget_ledger_daily,
                LedgerCalculator.formatCurrency(summary.activeDailyBurnRateYuan),
            )
            setTextViewText(R.id.widget_ledger_daily_unit, strings.perDayUnit)
            setTextViewText(R.id.widget_ledger_caption, strings.dailyCostCaption)
            setTextViewText(
                R.id.widget_ledger_month_expense,
                "${strings.monthExpenseLabel} ${LedgerCalculator.formatCurrency(month.expenseCents / 100.0)}",
            )
            setTextViewText(
                R.id.widget_ledger_month_net,
                "${strings.monthNetLabel} ${LedgerCalculator.formatCurrency(month.netCents / 100.0)}",
            )
            setOnClickPendingIntent(
                android.R.id.background,
                mainActivityIntent(appContext, ACTION_OPEN_LEDGER, REQUEST_OPEN),
            )
            setOnClickPendingIntent(
                R.id.widget_ledger_add,
                mainActivityIntent(appContext, ACTION_ADD_LEDGER_ENTRY, REQUEST_ADD),
            )
        }

        manager.updateAppWidget(ids, views)
    }

    private fun mainActivityIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
