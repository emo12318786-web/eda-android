package com.deniz.eda.core

/**
 * ۱۹ بخش محبت‌آمیز — پاسخ‌های آماده‌ی بدون AI
 * مشابه پاسخ‌های eda.py (Termux)
 */
object AffectionResponses {

    private fun v(m: String, vararg k: String) = FuzzyMatcher.herhangiBiri(m, *k)

    /**
     * اگه کاربر یه چیزی گفت که توی ۱۹ بخش بود، جواب می‌ده.
     * اگه نه، null برمی‌گردونه (که CommandProcessor بره سراغ AI).
     */
    fun bul(metin: String, hitap: String): String? {

        // ═══ ۱. Teşekkür ═══
        if (v(metin, "teşekkür", "tesekkur", "sağol", "sagol",
              "memnun oldum", "eyvallah", "minnettarım", "minnettarim",
              "çok sağol", "cok sagol", "ellerine sağlık", "ellerine saglik")) {
            return listOf(
                "Rica ederim $hitap.",
                "Ne demek $hitap, her zaman.",
                "Bir şey değil $hitap.",
                "Her zaman hizmetindeyim $hitap.",
                "Sevgiyle $hitap!",
                "Benim için zevkti $hitap.",
                "Önemli değil $hitap!"
            ).random()
        }

        // ═══ ۲. Merhaba / Selam ═══
        if (v(metin, "merhaba", "selam", "nasılsın", "nasilsin",
              "günaydın", "gunaydin", "iyi akşamlar", "iyi aksamlar",
              "iyi geceler", "iyi günler", "iyi gunler",
              "hoş geldin", "hos geldin", "naber", "ne haber")) {
            return listOf(
                "Merhaba $hitap!",
                "Selam $hitap, hoş geldin!",
                "Merhaba, seni dinliyorum $hitap.",
                "Günaydın $hitap!",
                "İyi akşamlar $hitap!",
                "İyi geceler $hitap!",
                "Hoş geldin $hitap!",
                "İyiyim $hitap, sen nasılsın?"
            ).random()
        }

        // ═══ ۳. İyiyim / Harika ═══
        if (v(metin, "iyiyim", "harika", "mükemmel", "mukemmel",
              "süper", "super", "çok iyi", "cok iyi",
              "fena değil", "fena degil", "idare eder")) {
            return listOf(
                "Sevindim $hitap!",
                "Harika $hitap!",
                "Çok güzel $hitap!",
                "Ne güzel $hitap!"
            ).random()
        }

        // ═══ ۴. Veda ═══
        if (v(metin, "görüşürüz", "gorusuruz", "hoşçakal", "hoscakal",
              "güle güle", "gule gule", "elveda", "bay bay",
              "kendine iyi bak", "iyi bak")) {
            return listOf(
                "Görüşürüz $hitap, kendine iyi bak.",
                "Hoşça kal $hitap!",
                "Güle güle $hitap!",
                "İyi günler $hitap!",
                "Kendine iyi bak $hitap!"
            ).random()
        }

        // ═══ ۵. Özür ═══
        if (v(metin, "özür", "ozur", "affet", "affedersin",
              "kusura bakma", "benim hatam", "hatalıyım", "hataliyim")) {
            return listOf(
                "Sorun değil $hitap.",
                "Önemli değil $hitap.",
                "Hiç sorun değil $hitap.",
                "Merak etme $hitap, geçti."
            ).random()
        }

        // ═══ ۶. Sevgi ═══
        if (v(metin, "seni seviyorum", "seviyorum", "aşkım", "askim",
              "canım", "canim", "sevgilim")) {
            return listOf(
                "Ben de seni seviyorum $hitap!",
                "Sen de benim canımsın $hitap!",
                "Sevgiyle $hitap!",
                "Kalpten sevgiyle $hitap!"
            ).random()
        }

        // ═══ ۷. İltifat ═══
        if (v(metin, "güzelsin", "guzelsin", "tatlısın", "tatlisin",
              "iyi kalplisin", "harikasın", "harikasin")) {
            return listOf(
                "Teşekkür ederim $hitap!",
                "Sağ ol $hitap, sen de güzelsin!",
                "Beğendiğine sevindim $hitap!",
                "Senin gibi güzel $hitap!"
            ).random()
        }

        // ═══ ۸. Yorgunum ═══
        if (v(metin, "yorgunum", "yorgun", "bitkinim", "bitkin",
              "uykum var", "uykum geldi", "halsizim")) {
            return listOf(
                "Dinlen biraz $hitap.",
                "Biraz uzan $hitap, iyi gelir.",
                "Kendine zaman ayır $hitap.",
                "Yorgunsan biraz mola ver $hitap."
            ).random()
        }

        // ═══ ۹. Açım ═══
        if (v(metin, "acım", "acim", "karnım aç", "karnim ac",
              "açlık", "aclik")) {
            return listOf(
                "Bir şeyler ye $hitap.",
                "Karnını doyur $hitap, sağlığın önemli.",
                "Mutfağa git $hitap, bir şeyler hazırla."
            ).random()
        }

        // ═══ ۱۰. Susadım ═══
        if (v(metin, "susadım", "susadim", "su içmek", "su icmek", "susuz")) {
            return listOf(
                "Su iç $hitap, sağlığın için.",
                "Bir bardak su iç $hitap.",
                "Susuz kalma $hitap, su iç."
            ).random()
        }

        // ═══ ۱۱. Mutluyum ═══
        if (v(metin, "mutluyum", "sevindim", "neşeliyim", "neseliyim",
              "çok mutlu", "cok mutlu")) {
            return listOf(
                "Ne güzel $hitap!",
                "Senin mutluluğun benim mutluluğum $hitap!",
                "Çok sevindim $hitap!",
                "Mutluluğun daim olsun $hitap!"
            ).random()
        }

        // ═══ ۱۲. Üzgünüm ═══
        if (v(metin, "üzgünüm", "uzgunum", "mutsuzum", "kederliyim",
              "moralim bozuk", "moralim yok")) {
            return listOf(
                "Üzülme $hitap, geçer.",
                "Yanındayım $hitap, üzülme.",
                "Her şey düzelir $hitap, sabret.",
                "Moralini bozma $hitap, ben varım."
            ).random()
        }

        // ═══ ۱۳. Korkuyorum ═══
        if (v(metin, "korkuyorum", "korktum", "korku", "ürktüm", "urktum")) {
            return listOf(
                "Korkma $hitap, yanındayım.",
                "Sakin ol $hitap, bir şey yok.",
                "Yanındayım $hitap, korkma."
            ).random()
        }

        // ═══ ۱۴. Acelem var ═══
        if (v(metin, "acelem var", "acele", "çabuk", "cabuk",
              "hızlı", "hizli")) {
            return listOf(
                "Tamam $hitap, hızlanıyorum.",
                "Hemen $hitap, acele ediyorum.",
                "Söyle $hitap, ne yapabilirim?"
            ).random()
        }

        // ═══ ۱۵. Hastayım ═══
        if (v(metin, "hastayım", "hastayim", "iyi değilim", "iyi degilim",
              "kendimi kötü", "kendimi kotu")) {
            return listOf(
                "Geçmiş olsun $hitap.",
                "Doktora git $hitap, önemli.",
                "Kendine iyi bak $hitap.",
                "İlaç aldın mı $hitap?"
            ).random()
        }

        // ═══ ۱۶. Adın ne ═══
        if (v(metin, "adın ne", "adin ne", "ismin ne", "sen kimsin",
              "kaç yaşındasın", "kac yasindasin")) {
            return listOf(
                "Ben Eda'yım $hitap, hep seninleyim.",
                "Adım Eda $hitap, senin asistanınım.",
                "Eda'yım $hitap, seni dinliyorum."
            ).random()
        }

        // ═══ ۱۷. Yardım ═══
        if (v(metin, "yardım et", "yardim et", "bana yardım", "bana yardim",
              "yardımcı ol", "yardimci ol")) {
            return listOf(
                "Tabii $hitap, ne yapabilirim?",
                "Elbette $hitap, söyle.",
                "Memnuniyetle $hitap."
            ).random()
        }

        // ═══ ۱۸. Onay ═══
        if (v(metin, "tamam", "peki", "olur", "tabii", "elbette", "evet")) {
            return listOf(
                "Tamam $hitap.",
                "Peki $hitap.",
                "Olur $hitap.",
                "Elbette $hitap."
            ).random()
        }

        // ═══ ۱۹. Red ═══
        if (v(metin, "hayır", "hayir", "olmaz", "istemiyorum", "yok")) {
            return listOf(
                "Tamam $hitap, anladım.",
                "Peki $hitap, olur.",
                "Anladım $hitap."
            ).random()
        }

        return null
    }
}
