# EDA V107 Agent - Android test sürümü

Bu dal, GitHub'daki EDA V4 PRO / Android build #107 kaynaklarını koruyarak ayrı bir Android uygulamasında taşınabilen işlevleri toplar. Ana Termux sürümünün dosyalarını değiştirmez.

## Bu dalda
- Türkçe arayüz, tr-TR konuşma tanıma isteği ve Türkçe TTS
- 12 saatlik saat ve Türkçe tarih
- Metin/ses girişi, fener kontrolü
- Eda'nın uygulama içi yerel not hafızası
- Günlük kayıtları
- Yerel Android bildirimli saatli hatırlatıcı (hatırlat 19:30 su iç)
- Pil yüzdesi ve şarj durumu
- Güvenli offline aritmetik (hesapla 12*(5+3))
- Notu Samsung Notes veya başka uygulamayla paylaşma menüsü
- Araba modu tercihi
- Yapay zekâ Agent döngüsü: izinli araçlar, en fazla üç araç adımı, zaman/tarih ve not kaydetme/arama
- HTTPS API veya yerel Ollama endpoint'i; HTTP yalnızca localhost için ağ güvenliği kuralıyla sınırlandırılır
- API anahtarı yalnızca işlem belleğinde tutulur

## Örnek komutlar
- saat kaç
- bugün tarih
- feneri aç / feneri kapat
- not al alışveriş listesi
- notlarım
- notu paylaş alışveriş listesi
- günlük bugün arabayı tamir ettim
- günlüğümü göster
- hatırlat 19:30 su iç
- pil durumu
- hesapla 12*(5+3)

Hatırlatma saati 24 saat biçiminde girilir; yanıt saati 12 saat biçiminde gösterilir. Hatırlatma, Android bildirim iznine ve cihazın pil yönetimine bağlıdır. Günlük/notlar Eda'nın özel uygulama depolamasında kalır; bunlar otomatik olarak Samsung Notes uygulamasının veritabanına yazılmaz. Not paylaşımı Android paylaşım menüsünü açar.

## Yerel Ollama
- Endpoint: http://127.0.0.1:11434/v1
- Model adı: Ollama'da gerçekten yüklü olan model
- API anahtarı: boş

Yerel Ollama sunucusu aynı telefonda çalışmalı ve model önceden indirilmiş olmalıdır. Çevrim içi sağlayıcı için HTTPS endpoint ve kendi API anahtarın gerekir.

## EDA V107 ile farklar
Bu Android port henüz Termux EDA V107'nin birebir eşdeğeri değildir. Henüz taşınması/test edilmesi gerekenler:
- Sürekli wake word “Eda” ve pil tasarruflu arka plan uyku döngüsü
- Termux:API, Whisper.cpp ve sağlayıcı fallback zincirinin aynısı
- SMS, konum, kamera, telefonu bulma/kilitleme ve gelişmiş medya kontrolü
- Canlı hava durumu, dolar ve altın fiyatları
- Eski EDA JSON hafıza, günlük, öğrenme ve ayar dosyalarının içe aktarılması
- Gerçek öğrenme eşleşmelerinin otomatik komut çalıştırması
- Samsung Galaxy A71 üzerinde gerçek cihaz/pil testi

## Build
GitHub Actions'taki Eda Phone Agent Android build workflow debug APK oluşturur. APK yalnızca workflow başarıyla bitip artifact mevcut olduğunda hazır sayılır. Bu bir debug test paketi; release imzalı APK değildir.
