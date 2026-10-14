# EDA V107 Agent - Android port

Bu dal, EDA V107 Android/Termux kaynaklarına dayanarak ayrı bir Android test uygulamasıdır. Eski EDA/Termux dosyaları değiştirilmez.

## Çalışan yerel özellikler
- Türkçe arayüz; Android'in sistem konuşma tanıma servisine tr-TR isteği ve çevrimdışı tanıma tercihi
- Android TextToSpeech ile Türkçe konuşma çıkışı (cihazdaki ses motoru ve Türkçe ses verisi destekliyorsa)
- 12 saatlik saat ve Türkçe tarih
- Ses/metin girişi ve fener aç/kapat
- Eda uygulamasında kalıcı yerel notlar ve günlük
- Android AlarmManager ile saatli hatırlatıcı ve bildirim
- Pil yüzdesi/şarj durumu
- Şehir adıyla canlı hava durumu (internet gerekir; örnek: `hava durumu Tebriz`)
- Güvenli aritmetik hesaplama
- Notu Android paylaşım menüsüyle Samsung Notes veya başka uygulamaya gönderme
- Basit araba modu tercihi
- Sınırlı AI Agent: yalnızca izinli zaman/tarih ve not araçları, en fazla üç araç adımı
- HTTPS OpenAI-uyumlu endpoint veya aynı cihazdaki Ollama endpoint'i
- API anahtarı yalnızca uygulama belleğinde tutulur

## Örnek komutlar
- saat kaç / bugün tarih
- hava durumu Tebriz (güncel hava, internet gerekir)
- feneri aç / feneri kapat
- not al alışveriş listesi / notlarım
- günlük bugün arabayı tamir ettim / günlüğümü göster
- hatırlat 19:30 su iç
- pil durumu
- hesapla 12*(5+3)
- notu paylaş alışveriş listesi

Hatırlatıcı saatini 24 saat biçiminde gir; Eda saati 12 saat biçiminde gösterir. Android 13+ için bildirim izni gerekir ve bazı üretici pil ayarları hatırlatmaları geciktirebilir. Notlar/günlükler Eda'nın özel depolamasında saklanır; Samsung Notes'un özel veritabanına doğrudan yazılmaz. Paylaşım menüsü kullanıcının onayını ister.

## Tamamen çevrimdışı kullanım
Yerel komutlar (saat, tarih, fener, notlar, günlük, hesaplama, pil) internet ve AI API gerektirmez. Sesli giriş/çıkışın çevrimdışı olması, Android'deki Google konuşma hizmeti ve indirilen Türkçe çevrimdışı dil/ses paketlerine bağlıdır. Kod, çevrimdışı tanımayı tercih eder ancak Android/Google hizmeti bu tercihi yok sayabilir veya dili indirilmemişse ağ isteği yapabilir. Bu nedenle her telefonda %100 çevrimdışı ses garantisi verilemez; metin komutları her zaman alternatif هستند.

برای فعال‌کردن تشخیص گفتار آفلاین در Samsung:
Google uygulaması / تنظیمات Android → Voice / Speech recognition → Offline speech recognition → Turkish dil paketini indir (menü adları Android sürümüne göre değişebilir). TTS için Text-to-speech output ayarlarından Google Speech Services ve Türkçe ses verisini kontrol et.

## Ollama yerel model
- Endpoint: http://127.0.0.1:11434/v1
- Model adı: Ollama'da yüklü gerçek model adı
- API anahtarı: boş

این اتصال فقط وقتی کار می‌کند که Ollama واقعاً روی همین دستگاه و در دسترس اجرا شود. Termux روی اندروید به‌طور پیش‌فرض API سرور Ollama را خودکار در اختیار اپ قرار نمی‌دهد؛ ابتدا سرور و مدل را جداگانه راه‌اندازی کن. مدل‌های ابری به HTTPS و اینترنت نیاز دارند.

## قابلیت‌های EDA V107 که هنوز در این پورت پیاده‌سازی یا تأیید نشده‌اند
- Wake word دائمی «Eda» و چرخهٔ خواب/بیداری کم‌مصرف
- سرویس پس‌زمینهٔ پایدار و اعلام شارژ دوره‌ای
- انتقال و سازگاری کامل فایل‌های حافظه/یادگیری EDA قدیمی
- اجرای خودکار همهٔ فرمان‌های یادگرفته‌شده
- همهٔ ابزارهای Termux:API، مکان‌یابی، SMS، کنترل رسانه و تنظیمات پیشرفتهٔ گوشی
- قیمت زندهٔ دلار/طلا و آب‌وهوا بدون سرویس داده
- تست واقعی روی Galaxy A71 و اندازه‌گیری مصرف باتری

## Build
GitHub Actions workflow با نام Eda Phone Agent Android build باید با موفقیت تمام شود و artifact به نام eda-phone-agent-debug موجود باشد تا APK debug آمادهٔ دریافت محسوب شود. این فایل release امضاشده نیست. تا قبل از دیدن نتیجهٔ موفق CI، این شاخه را APK آمادهٔ نصب اعلام نکن.
