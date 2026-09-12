# EDA V4 PRO

**Eda — Android için Offline/Online sesli yapay zekâ asistanı**

EDA V4 PRO, Termux + Termux:API üzerinde çalışan, sesli komutları doğrudan Android işlevlerine bağlayan ve gerektiğinde AI sağlayıcılarından cevap alan kişisel bir asistan projesidir.

## Öne çıkan özellikler

- 🎙️ Google Speech Recognition + offline Whisper.cpp fallback
- 💤 Uyku modu ve “Eda” wake-word
- 🧠 Gemma 3.1 (`gemma3:1b`) ile yerel/offline AI
- ☁️ OpenRouter, Groq ve DeepSeek desteği
- 🔗 AI fallback zinciri
- 📚 Komut öğrenme sistemi
- 💾 Hafıza ve günlük
- 🔔 Hatırlatmalar + Android bildirimleri
- 🔋 Pil durumu ve periyodik pil bildirimi
- 🚗 Araba modu
- 🛡️ Güvenlik özellikleri
- 📱 Fener, telefon kilidi/bulma, kamera, konum ve SMS
- 🎵 Temel medya kontrolü
- 💰 Dolar ve altın fiyatı sorgulama
- 🌤️ Hava durumu
- 🧮 Offline hesap makinesi
- 🔊 Android TTS motoru yönetimi

## Güvenlik

Bu depoya **kişisel `settings.json` dosyanı, API anahtarlarını, telefon numaranı, hafıza/günlük dosyalarını veya ev konumunu koyma**.

Başlangıç için:

```bash
cp settings.example.json settings.json
```

Sonra kendi API anahtarlarını yalnızca telefondaki `settings.json` içine ekle.

> API anahtarlarını GitHub'a yükleme. Anahtar sızarsa ilgili sağlayıcıdan hemen iptal et.

## Android / Termux

EDA, Android üzerinde Termux ve Termux:API komutlarını kullanır. Ayrıca ses dönüşümü için `ffmpeg` ve offline konuşma tanıma için `whisper.cpp` kurulumu gerekir.

Örnek temel paketler:

```bash
pkg update
pkg install python ffmpeg
pip install SpeechRecognition
```

Termux:API uygulamasının Android tarafında da kurulu ve gerekli izinlerin verilmiş olması gerekir.

## AI sağlayıcıları

`settings.json` içinden şu sağlayıcılar kullanılabilir:

- `openrouter`
- `groq`
- `deepseek`
- `gemma`

Önerilen fallback yaklaşımı:

**OpenRouter → Groq → DeepSeek → Gemma**

Gemma yerel çalıştığı için internet olmadığında yedek beyin olarak kullanılabilir.

## Yerel ev konumu

Güvenlik nedeniyle kaynak kodunda gerçek ev koordinatı bulunmaz. Evden mesafe özelliğini kullanmak isteyen kişi kendi yerel `eda.py` kopyasında `EV_LAT` ve `EV_LON` değerlerini ayarlayabilir.

## Proje durumu

Bu sürüm, gerçek Android/Termux kullanımından geliştirilmiş EDA V4 PRO kodunun GitHub'a uygun, kişisel verileri çıkarılmış dağıtım sürümüdür.

## Lisans

MIT
