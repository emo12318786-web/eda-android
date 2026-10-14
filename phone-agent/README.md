# Eda Phone Agent · Android V0.3 test build

Bu Android uygulaması, Termux üzerinde çalışan EDA V4 PRO'nun özelliklerini kademeli olarak telefona taşımak için ayrı tutulur. Mevcut Termux dosyalarını değiştirmez.

## Bu sürümde
- Türkçe arayüz, tr-TR konuşma tanıma isteği ve Türkçe TTS
- 12 saatlik saat gösterimi (ör. 08:45 ÖS)
- Sesli/yazılı komut, saat/tarih ve el feneri
- Eda uygulamasında yerel not hafızası ve Samsung Notes'a paylaşım menüsü
- Günlük kayıtları
- Saatli hatırlatıcılar ve Android bildirimleri
- Pil yüzdesi/şarj durumu
- Güvenli aritmetik hesap makinesi
- Araba modu tercihi
- Öğrenilecek ifadeler için yerel liste
- OpenAI uyumlu servis veya yerel Ollama bağlantısı
- API anahtarı yalnızca işlem belleğinde tutulur; diske yazılmaz
- HTTP trafiği yalnızca localhost/127.0.0.1 için açılır

## Komut örnekleri
- saat kaç
- bugün tarih
- feneri aç / feneri kapat
- not al alışveriş listesi
- notlarım
- hatırlat 19:30 su iç
- günlük bugün arabayı tamir ettim
- pil kaç
- hesapla 12*(5+3)
- araba modu
- öğren yeni ifade

Hatırlatıcı saati 24 saatlik giriş kabul eder; ekran ve konuşulan saat 12 saatlik biçimde gösterilir. Android'in pil optimizasyonu ve bildirim izinleri hatırlatıcı teslimini etkileyebilir.

## Yerel model
Ollama örneği: http://127.0.0.1:11434/v1. Telefon üzerinde çalışan Ollama sunucusu ve indirilmiş model gerekir. Model adı sunucudaki gerçek adla aynı olmalıdır. Yerel modelde API anahtarı boş kalabilir. Çevrim içi sağlayıcıda HTTPS ve kendi API anahtarın gerekir.

## Henüz eşdeğer olmayan özellikler
Bu sürüm henüz EDA V4 PRO'nun tüm özelliklerini taşımıyor. Özellikle:
- Sürekli wake-word “Eda” ve pil tasarruflu arka plan dinleme
- Termux'taki Whisper.cpp fallback ve sağlayıcı fallback zincirinin birebir taşınması
- Android kilidi bulma/kilit, SMS, kamera/konum ve medya kontrolleri
- Gerçek komut öğrenme motoru (şimdilik ifadeler listesi tutuluyor)
- EDA'nın mevcut ayarlarını ve geçmiş günlük/hafıza dosyalarını otomatik içe aktarma
- Samsung A71 üzerinde uzun süreli pil/arka plan testi

Hatırlatıcılar Android AlarmManager ve bildirimleri kullanır; cihaz üreticisi pil yönetimi kesin zamanlamayı kısıtlayabilir. Bu bir debug/test APK'sıdır, imzalı release değildir. APK, CI build başarılı olduktan sonra paylaşılmalıdır.

## Güvenlik
Çevrim içi modele gönderdiğin metin telefondan çıkar. API anahtarını, özel bilgileri veya gerçek konumunu istemediğin bir sağlayıcıya gönderme. Araba modunu sürüş sırasında ekranla etkileşim için kullanma.
