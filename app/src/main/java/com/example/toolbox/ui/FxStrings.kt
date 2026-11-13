package com.example.toolbox.ui

import com.example.toolbox.fx.FxFailure

val ToolboxStrings.manualRateSwitch: String
    get() = if (chinese) "手动输入汇率" else "Enter rate manually"

val ToolboxStrings.rateFetchOnSave: String
    get() = if (chinese) "保存时获取该日汇率" else "That day's rate will be fetched on save"

val ToolboxStrings.manualRateHint: String
    get() = if (chinese) "或开启「手动输入汇率」" else "or enable manual rate input"

val ToolboxStrings.manualRateInvalid: String
    get() = if (chinese) "汇率必须大于 0" else "Rate must be greater than 0"

val ToolboxStrings.fxGetRates: String
    get() = if (chinese) "获取汇率" else "Get rates"

val ToolboxStrings.fxRateDateLabel: String
    get() = if (chinese) "汇率日期" else "Rate date"

val ToolboxStrings.fxFetchedLabel: String
    get() = if (chinese) "获取时间" else "Fetched"

val ToolboxStrings.fxSourceLabel: String
    get() = if (chinese) "数据源" else "Source"

val ToolboxStrings.fxSourceScheduleNote: String
    get() = if (chinese) {
        "欧洲央行参考汇率（Frankfurter），工作日更新"
    } else {
        "ECB reference rates (Frankfurter), updated on business days"
    }

val ToolboxStrings.fxNoRates: String
    get() = if (chinese) "尚未获取汇率" else "No rates yet"

val ToolboxStrings.fxNetworkError: String
    get() = if (chinese) "网络连接失败或超时" else "Network connection failed or timed out"

val ToolboxStrings.fxInvalidResponse: String
    get() = if (chinese) "汇率数据无法解析" else "Could not parse the rate data"

val ToolboxStrings.fxFromLabel: String
    get() = if (chinese) "源币种" else "From"

val ToolboxStrings.fxMoreChip: String
    get() = if (chinese) "更多…" else "More…"

val ToolboxStrings.fxPickCurrency: String
    get() = if (chinese) "选择币种" else "Pick currency"

val ToolboxStrings.fxPinHint: String
    get() = if (chinese) {
        "点星标把常用货币固定到币种按钮和换算列表里"
    } else {
        "Star a currency to keep it in the chips and the result list"
    }

fun ToolboxStrings.fxPinAction(code: String): String =
    if (chinese) "固定 $code" else "Pin $code"

fun ToolboxStrings.fxUnpinAction(code: String): String =
    if (chinese) "取消固定 $code" else "Unpin $code"

val ToolboxStrings.fxSearchHint: String
    get() = if (chinese) "搜索货币代码或名称" else "Search code or currency name"

val ToolboxStrings.fxSearchEmpty: String
    get() = if (chinese) "没有匹配的货币" else "No matching currency"

fun ToolboxStrings.rateLineLocked(code: String, rate: String, date: String): String = if (chinese) {
    "1 $code = $rate CNY · 汇率日期 $date"
} else {
    "1 $code = $rate CNY · rate date $date"
}

fun ToolboxStrings.rateLineManual(code: String, rate: String): String = if (chinese) {
    "1 $code = $rate CNY · 手动汇率"
} else {
    "1 $code = $rate CNY · manual rate"
}

fun ToolboxStrings.rateLineCached(code: String, rate: String, date: String): String = if (chinese) {
    "1 $code = $rate CNY · 缓存 $date"
} else {
    "1 $code = $rate CNY · cached $date"
}

fun ToolboxStrings.manualRateFieldLabel(code: String): String = "1 $code ="

fun ToolboxStrings.approxCny(cnyText: String): String = "≈ $cnyText"

fun ToolboxStrings.useCachedRate(date: String): String =
    if (chinese) "使用最近缓存汇率（$date）" else "Use latest cached rate ($date)"

fun ToolboxStrings.fxShowAll(count: Int): String =
    if (chinese) "显示全部 ($count)" else "Show all ($count)"

fun ToolboxStrings.fxError(failure: FxFailure, httpCode: Int?): String = when (failure) {
    FxFailure.NETWORK -> fxNetworkError
    FxFailure.HTTP_ERROR -> if (chinese) {
        "汇率服务返回错误${httpCode?.let { " $it" } ?: ""}"
    } else {
        "Rate service error${httpCode?.let { " $it" } ?: ""}"
    }
    FxFailure.INVALID_RESPONSE -> fxInvalidResponse
    FxFailure.ALL_SOURCES_FAILED -> fxAllSourcesFailed
}

fun ToolboxStrings.fxSourceError(sourceId: String, failure: FxFailure, httpCode: Int?): String {
    val name = fxSourceName(sourceId).ifBlank { sourceId }
    return fxAttemptLine(name, fxError(failure, httpCode))
}

val ToolboxStrings.fxReferenceRates: String
    get() = if (chinese) "参考汇率" else "Reference rates"

fun ToolboxStrings.fxSourceName(sourceId: String?): String = when (sourceId) {
    "frankfurter" -> if (chinese) "Frankfurter（欧洲央行）" else "Frankfurter (ECB)"
    "ecb" -> if (chinese) "欧洲央行官网" else "ECB website"
    "currency-api" -> if (chinese) {
        "currency-api（社区数据）"
    } else {
        "currency-api (community)"
    }
    "manual" -> if (chinese) "手动输入" else "Manual"
    else -> ""
}

val ToolboxStrings.fxFallbackNote: String
    get() = if (chinese) "备用来源" else "Fallback source"

val ToolboxStrings.fxCommunityNote: String
    get() = if (chinese) {
        "数值可能与欧洲央行略有差异"
    } else {
        "Values may differ slightly from ECB rates"
    }

fun ToolboxStrings.fxAttemptLine(source: String, reason: String): String = "$source：$reason"

val ToolboxStrings.fxAllSourcesFailed: String
    get() = if (chinese) "所有汇率来源均不可用" else "All rate sources failed"
