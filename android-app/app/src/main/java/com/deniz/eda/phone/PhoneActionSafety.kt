package com.deniz.eda.phone

import java.util.Locale

/**
 * Conservative, dependency-free guardrails for local phone automation.
 * This is a safety backstop, not a guarantee that every third-party screen is understood.
 */
object PhoneActionSafety {
    private fun normalize(raw: String): String = raw.lowercase(Locale.ROOT)
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .replace('ə', 'e')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private val dangerousActions = listOf(
        "send", "send now", "submit", "delete", "remove", "pay", "pay now",
        "purchase", "buy", "confirm", "transfer", "approve", "authorize",
        "place order", "complete purchase", "checkout", "finalize",
        "gonder", "gonder", "sil", "ode", "odeme", "satin al", "onayla",
        "havale", "eft", "para gonder", "para transfer", "mesaj gonder",
        "sifaris et", "tesdiq et", "pul kocur", "odenis et", "odenisi tamamla",
        "ارسال", "فرستادن", "حذف", "پرداخت", "خرید", "تایید", "تأیید",
        "انتقال وجه", "واریز", "پرداخت نهایی", "خرید نهایی", "ارسال پیام",
        "تراکنش", "سفارش نهایی", "تایید نهایی"
    ).map(::normalize)

    private val sensitiveScreenCues = listOf(
        "online banking", "banking", "bank account", "bank transfer", "bank",
        "banka", "bankacilik", "internet bankaciligi", "wallet", "payment",
        "billing", "checkout", "money transfer", "transfer money", "transaction",
        "credit card", "debit card", "card number", "card details", "iban",
        "cvv", "cvc", "pay now", "order total", "confirm payment",
        "odeme", "odeme bilgileri", "para transfer", "havale", "eft",
        "kart numarasi", "kart bilgileri", "hesap numarasi", "islem onayi",
        "پرداخت", "انتقال وجه", "بانکداری", "بانک", "کارت بانکی",
        "شماره کارت", "رمز پویا", "کد تایید", "کد تأیید", "خرید نهایی",
        "کیف پول", "تراکنش مالی"
    ).map(::normalize)

    private val sensitiveFieldCues = listOf(
        "password", "passcode", "pass word", "pin code", "security pin",
        "otp", "one time password", "one time code", "verification code",
        "authentication code", "security code", "sms code", "cvv", "cvc",
        "card number", "card details", "iban", "account number",
        "sifre", "parola", "guvenlik kodu", "dogrulama kodu",
        "tek kullanimlik", "sms kodu", "pin kodu", "kart numarasi",
        "banka karti", "hesap numarasi", "guvenlik sorusu",
        "رمز عبور", "رمز", "کد تایید", "کد تأیید", "رمز دوم",
        "رمز پویا", "کد امنیتی", "شماره کارت", "cvv", "cvc",
        "تاریخ انقضا", "کد پیامک", "رمز کارت", "شماره حساب"
    ).map(::normalize)

    fun isDangerousTarget(target: String): Boolean {
        val text = normalize(target)
        return text.isBlank() || dangerousActions.any { cue -> text.contains(cue) }
    }

    fun isSensitiveScreen(visibleText: String): Boolean {
        val text = normalize(visibleText)
        if (text.isBlank()) return false
        return sensitiveScreenCues.any { cue -> text.contains(cue) }
    }

    fun isSensitiveField(fieldContext: String): Boolean {
        val text = normalize(fieldContext)
        if (text.isBlank()) return false
        return sensitiveFieldCues.any { cue -> text.contains(cue) }
    }
}
