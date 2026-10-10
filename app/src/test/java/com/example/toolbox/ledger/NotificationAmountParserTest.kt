package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAmountParserTest {

    private fun parse(text: String) = NotificationAmountParser.parse(text)

    @Test
    fun testKeepsMarkedAndTwoDecimalAmounts() {
        assertEquals(listOf(2_500L), parse("微信支付收款 ¥25.00"))
        assertEquals(listOf(12_000L), parse("你有一笔 120 元的支出"))
        assertEquals(listOf(129_900L), parse("Paid ￥1,299 for your order"))
        assertEquals(listOf(880L), parse("支出 8.80，感谢使用"))
        assertEquals(listOf(4_500L), parse("消费人民币45元"))
        assertEquals(listOf(1_999L), parse("Charged 19.99 USD"))
    }

    @Test
    fun testDropsUnmarkedNumbersSuchAsCardTailsDatesAndTimes() {
        assertEquals(
            listOf(2_500L),
            parse("您尾号1234的储蓄卡10月3日15:30消费25.00元"),
        )
        assertEquals(emptyList<Long>(), parse("订单 482913 已发货，预计 3 天送达"))
        assertEquals(emptyList<Long>(), parse("版本 2.1 已更新"))
    }

    @Test
    fun testSkipsBalancesAndVerificationCodeNotices() {
        assertEquals(listOf(2_500L), parse("消费25.00元，余额1,234.56元"))
        assertEquals(emptyList<Long>(), parse("【某银行】验证码 482913，支付 ¥25.00 请勿泄露"))
        assertEquals(emptyList<Long>(), parse("Your verification code is 1234.00"))
        assertEquals(listOf(12_000L), parse("Hotpot dinner ¥120"))
    }

    @Test
    fun testEnglishBalancesAreExcludedWithoutDroppingTheCharge() {
        assertEquals(listOf(2_500L), parse("Spent 25.00 CNY, balance 1,234.56 CNY"))
        assertEquals(listOf(2_500L), parse("Charged ¥25.00; Balance: ¥1,234.56"))
        assertEquals(emptyList<Long>(), parse("Available balance is   CNY 1234.56"))
        assertEquals(emptyList<Long>(), parse("可用额度： 人民币1234.56元"))
        assertEquals(listOf(2_500L), parse("余额1000元，消费25.00元"))
    }

    @Test
    fun testDeduplicatesAndCaps() {
        assertEquals(listOf(2_500L), parse("¥25.00 收款 ¥25.00"))
        val many = (1..15).joinToString(" ") { "¥$it" }
        assertEquals(10, parse(many).size)
    }

    @Test
    fun testDetailedResultExplainsSkips() {
        assertEquals(
            NotificationSkipReason.VERIFICATION_CODE,
            NotificationAmountParser.parseDetailed("验证码 482913，支付 ¥25.00").skipReason,
        )
        assertEquals(
            NotificationSkipReason.NO_AMOUNT,
            NotificationAmountParser.parseDetailed("今天天气不错").skipReason,
        )
        val matched = NotificationAmountParser.parseDetailed("消费 ¥25.00")
        assertEquals(null, matched.skipReason)
        assertEquals(listOf(2_500L), matched.amountsCents)
    }
}
