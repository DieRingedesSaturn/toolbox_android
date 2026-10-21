package com.example.toolbox.ledger

import java.math.BigDecimal

/** Direction of a recognized payment notification. */
enum class NotificationPaymentDirection {
    EXPENSE,
    INCOME,
    TRANSFER_OUT,
    TRANSFER_IN,
}

/**
 * One amount tied to an explicit payment verb in a notification from a known
 * payment source. [label] is the merchant or counterparty when the
 * notification names one. [confident] marks matches strong enough for the
 * optional auto-book behavior: a strict template plus a payment-channel title.
 */
data class NotificationPayment(
    val direction: NotificationPaymentDirection,
    val amountCents: Long,
    val label: String? = null,
    val confident: Boolean = false,
)

/**
 * Source-specific parsing for WeChat Pay and Alipay notifications.
 *
 * The templates are deliberately conservative: an amount is accepted only
 * next to an explicit payment verb and must carry a currency mark, a 元/圆
 * suffix, or exactly two decimals, so card tails, dates and balances never
 * qualify. Marketing lines such as "已省¥88" or "积分到账" match no template.
 * Blind spots are documented rather than guessed around: in-app WeChat
 * payments may not notify at all, and Alipay offline payments can arrive late.
 */
object PaymentNotificationParser {
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    private const val AMT = """(?<amt>\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d{1,7}(?:\.\d{1,2})?)"""

    /** A matched template keeps its own direction, strength and capture groups. */
    private class Template(
        val direction: NotificationPaymentDirection,
        val strong: Boolean,
        val regex: Regex,
    ) {
        /** Whether the pattern captures a counterparty name. */
        val hasName: Boolean = regex.pattern.contains("(?<name>")
    }

    private val codeNotice = Regex(
        "验证码|校验码|动态码|动态密码|确认码|安全码|" +
            "\\b(?:otp|one-time|passcode|verification code)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val balanceLead = Regex(
        "(?:余额|可用额度|可用余额|剩余额度|\\bbalance)\\s*[:：=]?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val twoDecimals = Regex("\\.\\d{2}$")
    private val yuanSuffix = Regex("^\\s*[元圆]")

    /** Titles posted by the payment channels themselves, not by chat contacts. */
    private val wechatChannelTitle = Regex(
        "^(?:微信支付|微信转账|微信收款|微信收款商业版|微信收款助手|微信支付凭证|转账助手|收款助手)",
    )
    private val alipayChannelTitle = Regex("^(?:支付宝|支付宝通知|交易提醒|收钱码)")

    private val wechatTemplates = listOf(
        // Transfers — both sides name the counterparty.
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """你(?:已)?(?:成功)?向(?<name>[^，,。:;；\s]{1,16}?)(?:转账|转帐|轉帳)\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """已向(?<name>[^，,。:;；\s]{1,16}?)(?:转账|转帐|轉帳)[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """(?:转账|转帐|轉帳)\s*[¥￥]?\s*$AMT\s*给(?<name>[^，,。:;；\s]{1,16})""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """(?:转账给|轉帳給|转给)(?<name>[^，,。:;；\s]{1,16}?)\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_IN, true, Regex(
            """收到(?:一笔)?转账[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_IN, true, Regex(
            """(?<name>[^，,。:;；\s]{1,16}?)向你(?:转账|转帐|轉帳)\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_IN, true, Regex(
            """转账到账[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        // Expenses — code payments, merchant payments, and the 支出 summary.
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""已支付\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex(
            """付款码[^0-9]{0,14}?(?:付款成功|已支付|付款)\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex(
            """向(?<name>[^，,。:;；\s]{1,24}?)付款\s*[¥￥]\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""(?:成功)?付款成功[，,]?\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""支付成功[，,]?\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""你有一笔\s*$AMT\s*元""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""一笔\s*$AMT\s*元的支出""")),
        // Bare "支付¥xx" is kept as a suggestion only.
        Template(NotificationPaymentDirection.EXPENSE, false, Regex(
            """(?:微信支付|支付|付款)\s*[¥￥]\s*$AMT""",
        )),
        // Income.
        Template(NotificationPaymentDirection.INCOME, true, Regex("""(?:成功)?收款\s*[¥￥]\s*$AMT""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""收款\s*$AMT\s*元""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex(
            """退款(?:成功|到账)?[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""到账\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""一笔\s*$AMT\s*元的收入""")),
    )

    private val alipayTemplates = listOf(
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """(?:你已成功转账|转账|轉帳)\s*[¥￥]?\s*$AMT\s*给(?<name>[^，,。:;；\s]{1,16})""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """你向(?<name>[^，,。:;；\s]{1,16}?)(?:转账|转帐|轉帳)\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_OUT, true, Regex(
            """(?:转账给|轉帳給)(?<name>[^，,。:;；\s]{1,16}?)\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_IN, true, Regex(
            """(?<name>[^，,。:;；\s]{1,16}?)向你转账\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.TRANSFER_IN, true, Regex(
            """收到(?:一笔)?转账[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex(
            """(?:你)?已成功付款[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""成功付款[，,]?\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""你有一笔\s*$AMT\s*元""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""一笔\s*$AMT\s*元的支出""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""支出\s*$AMT\s*元""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex(
            """向(?<name>[^，,。:;；\s]{1,24}?)付款\s*$AMT\s*元""",
        )),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""付款成功[，,]?\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.EXPENSE, true, Regex("""消费\s*$AMT\s*元""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""(?:成功)?收款\s*[¥￥]?\s*$AMT\s*元?""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex(
            """退款(?:成功|到账)?[，,]?\s*[¥￥]?\s*$AMT""",
        )),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""到账\s*[¥￥]?\s*$AMT""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""一笔\s*$AMT\s*元的收入""")),
        Template(NotificationPaymentDirection.INCOME, true, Regex("""转入\s*$AMT\s*元""")),
    )

    /** The channel label used when a notification names no merchant. */
    fun channelLabel(packageName: String): String? = when (packageName) {
        WECHAT_PACKAGE -> "微信支付"
        ALIPAY_PACKAGE -> "支付宝"
        else -> null
    }

    /**
     * Returns the payment this notification describes, or null when the
     * source is unknown, the text mentions a verification code, or no
     * template matches.
     */
    fun parse(packageName: String, title: String, text: String): NotificationPayment? {
        val templates = when (packageName) {
            WECHAT_PACKAGE -> wechatTemplates
            ALIPAY_PACKAGE -> alipayTemplates
            else -> return null
        }
        val cleanTitle = normalize(title)
        val cleanText = normalize(text)
        val merged = listOf(cleanTitle, cleanText).filter { it.isNotBlank() }.joinToString(" ")
        if (merged.isBlank() || codeNotice.containsMatchIn(merged)) return null
        val channelTitle = when (packageName) {
            WECHAT_PACKAGE -> wechatChannelTitle.containsMatchIn(cleanTitle)
            else -> alipayChannelTitle.containsMatchIn(cleanTitle)
        }
        for (template in templates) {
            for (match in template.regex.findAll(merged)) {
                val amountGroup = match.groups["amt"] ?: continue
                val number = amountGroup.value
                val lead = merged.substring(0, amountGroup.range.first)
                if (balanceLead.containsMatchIn(lead)) continue
                val marked = lead.trimEnd().lastOrNull()?.let(CURRENCY_MARKS::contains) == true
                val trail = merged.substring(amountGroup.range.last + 1)
                if (!marked && !yuanSuffix.containsMatchIn(trail) && !twoDecimals.containsMatchIn(number)) {
                    continue
                }
                val cents = centsOf(number) ?: continue
                val label = if (template.hasName) {
                    match.groups["name"]?.value
                        ?.trim()
                        ?.trimStart('向')
                        ?.takeIf { it.isNotBlank() }
                        ?.take(MAX_LABEL_CHARS)
                } else {
                    null
                }
                return NotificationPayment(
                    direction = template.direction,
                    amountCents = cents,
                    label = label,
                    confident = template.strong && channelTitle,
                )
            }
        }
        return null
    }

    private fun centsOf(number: String): Long? = runCatching {
        BigDecimal(number.replace(",", "")).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it in 1..MAX_AMOUNT_CENTS }

    private fun normalize(value: String): String = value
        .replace('￥', '¥')
        .replace('：', ':')
        .replace('，', ',')
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace(AGGREGATE_PREFIX, "")
        .trim()

    private val CURRENCY_MARKS = setOf('¥')
    private val AGGREGATE_PREFIX = Regex("^[\\[【]\\d+条[\\]】]\\s*")
    private const val MAX_LABEL_CHARS = 24
    private const val MAX_AMOUNT_CENTS = 10_000_000_000L
}
