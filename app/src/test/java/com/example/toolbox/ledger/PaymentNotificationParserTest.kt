package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentNotificationParserTest {

    private fun wechat(title: String, text: String) =
        PaymentNotificationParser.parse("com.tencent.mm", title, text)

    private fun alipay(title: String, text: String) =
        PaymentNotificationParser.parse("com.eg.android.AlipayGphone", title, text)

    @Test
    fun testWechatCodeAndMerchantPayments() {
        val code = wechat("微信支付", "已支付¥16.00")!!
        assertEquals(NotificationPaymentDirection.EXPENSE, code.direction)
        assertEquals(1_600L, code.amountCents)
        assertTrue(code.confident)

        val merchant = wechat("微信支付", "向星巴克付款¥33.00")!!
        assertEquals(NotificationPaymentDirection.EXPENSE, merchant.direction)
        assertEquals(3_300L, merchant.amountCents)
        assertEquals("星巴克", merchant.label)

        val qr = wechat("微信支付", "付款码付款成功 ￥16.00")!!
        assertEquals(1_600L, qr.amountCents)
        assertTrue(qr.confident)
    }

    @Test
    fun testWechatAggregateAndExpenseSummary() {
        val aggregated = wechat("[2条]微信支付：已支付¥648.00", "")!!
        assertEquals(64_800L, aggregated.amountCents)

        val summary = wechat("微信支付", "你有一笔42.75元的支出")!!
        assertEquals(NotificationPaymentDirection.EXPENSE, summary.direction)
        assertEquals(4_275L, summary.amountCents)
    }

    @Test
    fun testWechatIncomeAndRefund() {
        val receipt = wechat("微信支付", "收款¥25.00")!!
        assertEquals(NotificationPaymentDirection.INCOME, receipt.direction)
        assertEquals(2_500L, receipt.amountCents)

        val refund = wechat("微信支付", "退款¥10.00")!!
        assertEquals(NotificationPaymentDirection.INCOME, refund.direction)
        assertEquals(1_000L, refund.amountCents)
    }

    @Test
    fun testWechatTransfersKeepTheirDirectionAndParty() {
        val out = wechat("微信支付", "你向夏曦晨光轉帳 ¥88.80")!!
        assertEquals(NotificationPaymentDirection.TRANSFER_OUT, out.direction)
        assertEquals(8_880L, out.amountCents)
        assertEquals("夏曦晨光", out.label)

        val helper = wechat("转账助手", "已向李四转账 ¥66.00，等待对方收款")!!
        assertEquals(NotificationPaymentDirection.TRANSFER_OUT, helper.direction)
        assertEquals("李四", helper.label)

        val incoming = wechat("夏曦晨光", "夏曦晨光向你轉帳 ¥66.00")!!
        assertEquals(NotificationPaymentDirection.TRANSFER_IN, incoming.direction)
        assertEquals(6_600L, incoming.amountCents)
        assertEquals("夏曦晨光", incoming.label)
    }

    @Test
    fun testAlipayPaymentsAndReceipts() {
        val expense = alipay("支付宝", "你已成功付款 ￥129.00")!!
        assertEquals(NotificationPaymentDirection.EXPENSE, expense.direction)
        assertEquals(12_900L, expense.amountCents)
        assertTrue(expense.confident)

        val summary = alipay("支付宝", "你有一笔42.75元的支出")!!
        assertEquals(4_275L, summary.amountCents)

        val received = alipay("支付宝", "成功收款0.01元")!!
        assertEquals(NotificationPaymentDirection.INCOME, received.direction)
        assertEquals(1L, received.amountCents)
    }

    @Test
    fun testAlipayTransfers() {
        val out = alipay("支付宝", "你已成功转账￥200.00给妈妈")!!
        assertEquals(NotificationPaymentDirection.TRANSFER_OUT, out.direction)
        assertEquals(20_000L, out.amountCents)
        assertEquals("妈妈", out.label)

        val incoming = alipay("支付宝", "张三向你转账￥100.00，请及时收款")!!
        assertEquals(NotificationPaymentDirection.TRANSFER_IN, incoming.direction)
        assertEquals(10_000L, incoming.amountCents)
        assertEquals("张三", incoming.label)
    }

    @Test
    fun testAmountsNeedAConfidentContext() {
        // Two decimals alone are enough only after an explicit payment verb.
        assertEquals(2_500L, alipay("支付宝", "消费25.00元，余额1,234.56元")!!.amountCents)
        // Balances and marketing copy never match a template.
        assertNull(wechat("微信支付", "可用余额 ¥1,234.56"))
        assertNull(wechat("微信支付", "已省¥648.13"))
        assertNull(wechat("微信支付", "每天5元干饭"))
        // Verification codes are skipped entirely.
        assertNull(wechat("微信支付", "验证码 482913，支付 ¥25.00"))
    }

    @Test
    fun testChatLikeTextStaysUnconfident() {
        // A friend's message can look like a payment; it must never auto-book.
        val chat = wechat("张三", "已支付¥100.00")!!
        assertEquals(NotificationPaymentDirection.EXPENSE, chat.direction)
        assertFalse(chat.confident)
    }

    @Test
    fun testUnknownPackagesAreNotParsed() {
        assertNull(PaymentNotificationParser.parse("com.example.bank", "银行", "消费25.00元"))
    }

    @Test
    fun testChannelLabels() {
        assertEquals("微信支付", PaymentNotificationParser.channelLabel("com.tencent.mm"))
        assertEquals("支付宝", PaymentNotificationParser.channelLabel("com.eg.android.AlipayGphone"))
        assertNull(PaymentNotificationParser.channelLabel("com.example.other"))
    }
}
