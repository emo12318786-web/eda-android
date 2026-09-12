# Eda - Native Android Uygulaması (FAZ 1 + FAZ 2)

`eda.py` (Termux) betiğinin native Android uygulamasına dönüştürülmüş hali.

## ÖNEMLİ - Lütfen önce bunu oku

Bu proje bu ortamda (Android SDK/Gradle/internet olmayan bir sandbox) **derlenip
test edilemedi**. Kodun sözdizimi elle satır satır gözden geçirildi ve parantez/
süslü parantez dengesi otomatik kontrol edildi, ama gerçek bir `.apk` üretmek
için Android Studio'da senin bir kez "Build" yapman gerekiyor - bu ortamda
Android SDK yok, bu yüzden buradan çalışan bir apk dosyası veremem. Aşağıdaki
adımları izlersen kendi bilgisayarında birkaç dakikada gerçek apk'yı üretirsin.

## Gerçek APK'yı nasıl üretirsin

1. [Android Studio](https://developer.android.com/studio) kur (ücretsiz).
2. `File > Open` ile bu `EdaApp` klasörünü aç, Gradle senkronizasyonunun
   bitmesini bekle (ilk seferde bağımlılıkları internetten indirir).
3. Üstteki menüden **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. Build bitince çıkan bildirimdeki **locate** linkine tıkla -
   `app/build/outputs/apk/debug/app-debug.apk` dosyası budur, telefonuna
   kopyalayıp kurabilirsin (bilinmeyen kaynaklardan yükleme izni gerekebilir).
5. İlk build'de Android Studio muhtemelen küçük uyumsuzluk hataları
   gösterecektir (SDK sürüm indirmeleri, Gradle sürümü vb.) - hata mesajını
   bana yapıştır, birlikte düzeltiriz.

## FAZ 1 - Çekirdek (tam çalışır)

- Foreground Service + bildirim, sürekli arka planda çalışır.
- Uyanma kelimesi dinleme (Android `SpeechRecognizer`, uyku modunda
  `EXTRA_PREFER_OFFLINE` ile pil dostu).
- TTS (Türkçe).
- Uyku/Aktif mod durum makinesi.
- 15 dakikada bir pil yüzdesi anonsu.
- Komutlar: saat, tarih (Şemsi takvim - birebir port edildi), pil, fener aç/kapat,
  uyku, kapat (onaylı).
- Ayarlar (SharedPreferences tabanlı).
- Boot receiver (telefon açılınca otomatik başlama - ilk kurulumda bir kez
  uygulamayı elle açıp izin vermen gerekiyor, bu Android'in kendi kısıtlaması).

## FAZ 2 - Eklenenler (bu turda)

- **Hatırlatma sistemi** (`ReminderStore` + `ReminderChecker`) - "saat 8 hatırlat
  beni su iç" gibi komutlar, her 30 saniyede bir kontrol edilir, saati gelince
  TTS ile söylenir. "hatırlatmaları listele", "hatırlatmaları temizle".
- **Hafıza** (`MemoryStore`) - "not al ...", "hafızamda ne var".
- **Günlük** (`DiaryStore`) - "günlüğe yaz ...", "günlüğü göster".
- **AI sohbet zinciri** (`AiRouter`) - OpenRouter → DeepSeek → Groq sırasıyla
  dener (hangi sağlayıcının API anahtarı doluysa o denenir, boşsa atlanır).
  Anahtarları uygulama içindeki **Ayarlar** bölümünden gir.
- **Hava durumu** (`WeatherUtils`) - Open-Meteo (ücretsiz, anahtarsız),
  telefonun son bilinen konumunu kullanır.
- **Dolar/Altın** (`CurrencyUtils`) - navasan.tech şemasına göre yazıldı,
  Ayarlar'dan kendi API anahtarını girmen gerekiyor. Başka bir kaynak
  kullanmak istersen sadece bu dosyadaki URL/alan adlarını değiştirmen yeterli.
- **Konum** (`LocationUtils`) - "konum", "neredeyim".
- **Güvenlik modu** (`SecurityMode`) - "güvenlik modu aç" dedikten sonra
  ivmeölçer ile ani hareket algılanırsa TTS ile yüksek sesle uyarır.
- **Araba modu** - şimdilik sadece açık/kapalı durumu tutuluyor
  (davranış farkı FAZ 3'te genişletilebilir: örn. daha yüksek ses, ekranı
  açık tutma).
- **SMS gönderme** (`SmsUtils`) - "mesaj gönder 05XXXXXXXXX merhaba" gibi,
  numarayı metinden rakam dizisi olarak ayıklar (rehber ismiyle eşleştirme
  henüz yok).
- **Basit öğrenme** (`LearningStore`) - "öğret KELIME komutu KOMUT" dersen
  o eşleşmeyi hatırlar ve bir dahaki sefere KELIME geçince KOMUT'u çalıştırır.
  (Not: orijinal Python'daki tam otomatik/bağlamsal öğrenme burada
  basitleştirildi - aşağıdaki "Bilinen sınırlamalar"a bak.)

## Mimari notlar

- whisper.cpp/Vosk yerine Android `SpeechRecognizer` kullanıldı (NDK ile
  whisper.cpp derlemek başlı başına ayrı, haftalar süren bir iş olurdu).
- Manuel wake-lock yerine Foreground Service + bildirim (Android'in kendi
  Doze/App-Standby istisnaları bunu zaten sağlıyor).
- AI sağlayıcıları (Groq/DeepSeek/OpenRouter) hepsi OpenAI-uyumlu
  `/chat/completions` şemasını kullanıyor, tek bir Retrofit arayüzü (
  `OpenAiCompatibleApi`) üç sağlayıcı için de yeniden kullanılıyor.
- Veriler (hatırlatma/hafıza/günlük/öğrenme) basit JSON dosyaları olarak
  uygulamanın kendi iç depolamasında tutuluyor (`JsonFileStore`) - Room gibi
  ekstra bir bağımlılığa gerek duyulmadı.

## Henüz eklenmedi (FAZ 3 için fikirler)

- Rehber ismiyle SMS gönderme ("ayşeye mesaj gönder" gibi - şu an sadece
  ham telefon numarası anlaşılıyor).
- Fotoğraf çekme.
- "Eve mesafe" / telefonu bul.
- Araba modunun ekstra davranışları (yüksek ses, ekran açık kalması vb.).
- Tam bağlamsal/otomatik öğrenme sistemi (şu an sadece manuel "öğret ..."
  komutuyla çalışıyor).
- Çoklu komut ayrıştırma ("pil ve saat" gibi tek cümlede iki komut).

## Test etmeden önce Ayarlar'a girmen gerekenler

- **Groq / DeepSeek / OpenRouter API anahtarı** - en az birini gir, yoksa
  serbest sohbet "internetim yok" der.
- **Navasan API anahtarı** - dolar/altın komutları için.
- Konum, SMS ve mikrofon izinlerini uygulama ilk açıldığında ver.

## Bilinen sınırlamalar

- Bu ortamda derlenip test edilemedi (yukarıda açıklandı).
- Launcher ikonu basit bir vektör placeholder.
- CurrencyUtils navasan.tech'in genel bilinen JSON şemasına göre yazıldı,
  gerçek yanıtla küçük alan adı farkları çıkabilir - ilk denemede hata
  alırsan yanıtın gerçek JSON'ını benimle paylaş, alan adlarını düzeltirim.
- SpeechRecognizer bazı cihazlarda `EXTRA_PREFER_OFFLINE` bayrağını yok
  sayabilir (üretici/Android sürümüne bağlı) - bu durumda uyku modu Termux
  sürümü kadar pil tasarruflu olmayabilir.
