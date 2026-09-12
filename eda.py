# -*- coding: utf-8 -*-
"""
EDA V4 PRO - Groq + Gemma Fallback (Tam Ozellikli Surum)
Tum orijinal ozellikler + Groq (online, hizli) + Gemma (offline, garanti) AI sistemi
"""

import audioop
import difflib
import json
import math
import os
import random
import re
import shutil
import socket
import subprocess
import threading
import time
import urllib.request
import wave
from datetime import datetime, timedelta

import speech_recognition as sr

try:
    from vosk import Model as VoskModel, KaldiRecognizer
    VOSK_KUTUPHANESI_VAR = True
except ImportError:
    VOSK_KUTUPHANESI_VAR = False

# ============================================================
# KARA LİSTE
# ============================================================

KOMUT_KARA_LISTE = [
    "kapat", "uyku", "aktif", "fener", "pil", "saat",
    "tarih", "sohbet", "romantik", "sessiz", "araba",
    "hatırlat", "konum", "muzik", "hava", "gunluk",
    "eda", "tamam", "evet", "hayir", "kalk", "uyan"
]

# ============================================================
# THREAD LOCK - eszamanlilik guvenligi icin
# ============================================================

kayit_lock = threading.Lock()

# ============================================================
# EDA AYARLARI
# ============================================================

KLASOR = os.path.dirname(os.path.abspath(__file__))

SES_KAYDI = os.path.join(KLASOR, "eda_record.m4a")
SES_WAV = os.path.join(KLASOR, "eda_record.wav")

MEMORY_DOSYASI = os.path.join(KLASOR, "memory.json")
DIARY_DOSYASI = os.path.join(KLASOR, "diary.json")
SETTINGS_DOSYASI = os.path.join(KLASOR, "settings.json")
CAR_DOSYASI = os.path.join(KLASOR, "car.json")
OGRENME_DOSYASI = os.path.join(KLASOR, "öğrenme.json")
HATIRLATMA_DOSYASI = os.path.join(KLASOR, "hatırlatmalar.json")

VOSK_MODEL_YOLU = os.path.join(KLASOR, "vosk-model-tr")

WHISPER_KLASOR = os.path.expanduser("~/whisper.cpp")
WHISPER_BINARY = os.path.join(WHISPER_KLASOR, "build", "bin", "whisper-cli")
WHISPER_MODEL_MEDIUM = os.path.join(WHISPER_KLASOR, "models", "ggml-medium.bin")
WHISPER_MODEL_SMALL = os.path.join(WHISPER_KLASOR, "models", "ggml-small.bin")
WHISPER_MODEL_BASE = os.path.join(WHISPER_KLASOR, "models", "ggml-base.bin")
WHISPER_CIKTI_ONEK = os.path.join(KLASOR, "whisper_cikti")

EV_LAT = None
EV_LON = None

# ============================================================
# Semsi Takvim
# ============================================================

SEMSI_AYLAR = [
    "Ferverdin", "Ordibehest", "Hordad", "Tir", "Mordad", "Sehriver",
    "Mehr", "Aban", "Azer", "Dey", "Behmen", "Esfend"
]

GUNLER = ["Pazartesi", "Sali", "Carsamba", "Persembe", "Cuma", "Cumartesi", "Pazar"]

def miladi_den_semsiye_çevir(g_yil, g_ay, g_gun):
    gun_sayisi = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334]
    if (g_ay > 2) and ((g_yil % 4 == 0 and g_yil % 100 != 0) or (g_yil % 400 == 0)):
        gy2 = g_yil + 1
    else:
        gy2 = g_yil
    toplam_gun = (355666 + (365 * g_yil) + ((gy2 + 3) // 4) - ((gy2 + 99) // 100) +
                  ((gy2 + 399) // 400) + g_gun + gun_sayisi[g_ay - 1])
    j_yil = -1595 + (33 * (toplam_gun // 12053))
    toplam_gun %= 12053
    j_yil += 4 * (toplam_gun // 1461)
    toplam_gun %= 1461
    if toplam_gun > 365:
        j_yil += (toplam_gun - 1) // 365
        toplam_gun = (toplam_gun - 1) % 365
    if toplam_gun < 186:
        j_ay = 1 + toplam_gun // 31
        j_gun = 1 + (toplam_gun % 31)
    else:
        j_ay = 7 + (toplam_gun - 186) // 30
        j_gun = 1 + ((toplam_gun - 186) % 30)
    return j_yil, j_ay, j_gun

def semsi_tarih():
    simdi = datetime.now()
    j_yil, j_ay, j_gun = miladi_den_semsiye_çevir(simdi.year, simdi.month, simdi.day)
    gun_adi = GUNLER[simdi.weekday()]
    return f"{j_gun} {SEMSI_AYLAR[j_ay - 1]} {j_yil}", gun_adi

# ============================================================
# WHISPER MODEL SECIMI
# ============================================================

def secili_whisper_modeli():
    tercih = ayar_getir("whisper_model_tercihi", "döğruluk")
    if tercih == "hiz":
        siralama = [WHISPER_MODEL_BASE, WHISPER_MODEL_SMALL, WHISPER_MODEL_MEDIUM]
    else:
        siralama = [WHISPER_MODEL_MEDIUM, WHISPER_MODEL_SMALL, WHISPER_MODEL_BASE]
    for model in siralama:
        if os.path.exists(model):
            return model
    return WHISPER_MODEL_BASE

WHISPER_BAGLAM_PROMPTU = (
    "Eda, feneri ac, feneri kapat, saat kac, hafızaya ekle, hatırla, "
    "hafızayi sil, bip sesini ac, bip sesini kapat, cevrimdisi mod, "
    "cevrimici mod, otomatik mod, pil kac, eda kapat, tamam."
)

WHISPER_HALUSINASYON_KALIPLARI = [
    "sarki soyluyor", "muzik", "konusma)", "altyazi",
    "izlediginiz icin tesekkurler", "begenmeyi unutmayin",
    "abone ol", "gunaydin", "iyi seyirler",
    "kanalima abone", "yorum yapmayi unutmayin", "hosca kalin",
    "bir sonraki videoda gorusuruz", "sesli dusunuyorum",
    "www.", ".com", "çeviri:", "çeviren:",
    "[", "]", "...", "caliyor", "sarki",
    "altyazi m", "altyazi:", "konusma",
    "video", "kanal", "yorum", "begen"
]

EDA_ADI = "Eda"
KULLANICI_ADI = "denizçim"

EDA_MODU = "uyku"
WAKE_LOCK_AKTIF = False
KAYIT_AKTIF = False
CAR_MODE_AKTIF = False
SECURITY_MODE_AKTIF = False

EDA_KAPAT = "KAPAT"
EDA_KAPAT_ONAY_ISTE = "KAPAT_ONAY_ISTE"
EDA_UYKU = "UYKU"
GUNLUK_ICERIK_BEKLENIYOR = "GUNLUK_ICERIK_BEKLENIYOR"
GUNLUK_KAYIT_SURESI = 45

MAKS_KAYIT = 9
WAKE_KAYIT_SURESI = 6

NOT_LIMITI = 1000
GUNLUK_LIMITI = 1000

PIL_BILDIRIM_ARALIGI = 15 * 60

# --- UYKU MODU PIL TASARRUFU AYARLARI ---
# Uyku modunda iki dinleme denemesi arasinda beklenecek sure (saniye).
# Bu bekleme sayesinde CPU/radyo surekli calismak yerine ara ara nefes alir.
UYKU_BEKLEME_ARALIGI = 2.0
# Bu RMS enerji degerinin altindaki kayitlar "sessizlik" sayilir ve
# hic bir tanima motoruna (Vosk/Whisper) gonderilmez - pil tasarrufu saglar.
UYKU_SESSIZLIK_ESIGI = 400

MODLAR = ["uyku", "aktif", "sessiz", "sohbet", "romantik", "dinleme", "araba"]

DEFAULT_SETTINGS = {
    "tts_language": "tr-TR",
    "tts_rate": "1.1",
    "tts_engine": "com.google.android.tts",
    "beep_aktif": False,
    "tanima_modu": "offline",
    "whisper_model_tercihi": "hiz",
    "durdurma_dinleme_aktif": False,
    "wake_words": ["eda", "e da", "hey eda", "uyan eda"],
    "security": False,
    "car_mode": False,
    "öğrenme_aktif": True,

    # --- AI SAGLAYICI AYARLARI ---
    "ai_aktif": True,
    "ai_provider": "openrouter",     # oncelikli saglayici: "openrouter", "groq", "deepseek" veya "gemma"
    "openrouter_api_key": "",        # <-- BURAYA kendi OpenRouter anahtarini yaz
    "openrouter_model": "openrouter/free",
    "groq_api_key": "",              # <-- BURAYA kendi Groq anahtarini yaz
    "groq_model": "openai/gpt-oss-20b",
    "deepseek_api_key": "",          # <-- BURAYA kendi DeepSeek anahtarini yaz
    "deepseek_model": "deepseek-v4-flash",   # veya "deepseek-v4-pro" (daha guclu, daha pahali)
    "deepseek_thinking": False,      # True yaparsan R1-tarzi "dusunme modu" acilir (daha yavas, daha pahali)
    "gemma_model": "gemma3:1b",
    "gemma_host": "http://localhost:11434",
    "gemma_otomatik": True,

    "guvenlik_numara": "",          # <-- BURAYA kendi guvenlik numarani yaz
}

# ============================================================
# YARDIMCI FONKSIYONLAR
# ============================================================

def komut_calistir(komut, timeout=10, capture=False):
    try:
        if capture:
            return subprocess.run(komut, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   text=True, timeout=timeout)
        return subprocess.run(komut, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=timeout)
    except Exception:
        return None

def komut_var_mi(ad):
    return shutil.which(ad) is not None

def json_yükle(dosya, varsayilan):
    if not os.path.exists(dosya):
        return varsayilan
    try:
        with open(dosya, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return varsayilan

def json_kaydet(dosya, veri):
    try:
        gecici = dosya + ".tmp"
        with open(gecici, "w", encoding="utf-8") as f:
            json.dump(veri, f, ensure_ascii=False, indent=2)
        os.replace(gecici, dosya)
        return True
    except Exception as hata:
        print(f"JSON yazma hatasi: {hata}")
        return False

def ayarlari_yükle():
    veri = json_yükle(SETTINGS_DOSYASI, DEFAULT_SETTINGS.copy())
    if not isinstance(veri, dict):
        veri = DEFAULT_SETTINGS.copy()
    for anahtar, deger in DEFAULT_SETTINGS.items():
        veri.setdefault(anahtar, deger)
    return veri

def ayar_getir(anahtar, varsayilan=None):
    return ayarlari_yükle().get(anahtar, varsayilan)

def ayar_degistir(anahtar, deger):
    ayarlar = ayarlari_yükle()
    ayarlar[anahtar] = deger
    return json_kaydet(SETTINGS_DOSYASI, ayarlar)

def internet_var_mi(zaman_asimi=1.5):
    try:
        socket.setdefaulttimeout(zaman_asimi)
        socket.gethostbyname("www.google.com")
        return True
    except OSError:
        return False

def emoji_temizle(metin):
    if not metin:
        return metin
    emoji_pattern = re.compile("["
        u"\U0001F600-\U0001F64F"
        u"\U0001F300-\U0001F5FF"
        u"\U0001F680-\U0001F6FF"
        u"\U0001F1E0-\U0001F1FF"
        u"\U00002702-\U000027B0"
        u"\U000024C2-\U0001F251"
        "]+", flags=re.UNICODE)
    return emoji_pattern.sub(r'', metin).strip()

# ============================================================
# OGRENME SISTEMI
# ============================================================

def öğrenme_yükle():
    return json_yükle(OGRENME_DOSYASI, {"eslesmeler": {}, "istatistikler": {}})

def öğrenme_kaydet(veri):
    return json_kaydet(OGRENME_DOSYASI, veri)

def öğren_komut_eslesmesi(kullanıcı_metin, eda_komutu):
    if not ayar_getir("öğrenme_aktif", True):
        return
    for kelime in KOMUT_KARA_LISTE:
        if kelime in kullanıcı_metin.lower():
            return
    if kullanıcı_metin.strip() == eda_komutu.strip():
        return
    if len(kullanıcı_metin) < 3 or len(kullanıcı_metin) > 50:
        return

    öğrenme = öğrenme_yükle()
    eslesmeler = öğrenme.get("eslesmeler", {})
    yeni_öğrenme = False
    for kelime in kullanıcı_metin.lower().split():
        if len(kelime) > 2:
            if eslesmeler.get(kelime) != eda_komutu:
                eslesmeler[kelime] = eda_komutu
                yeni_öğrenme = True

    if yeni_öğrenme:
        istatistikler = öğrenme.get("istatistikler", {})
        istatistikler[eda_komutu] = istatistikler.get(eda_komutu, 0) + 1
        öğrenme["eslesmeler"] = eslesmeler
        öğrenme["istatistikler"] = istatistikler
        öğrenme_kaydet(öğrenme)

def öğrenmeden_bul(metin):
    öğrenme = öğrenme_yükle()
    eslesmeler = öğrenme.get("eslesmeler", {})
    metin = metin.lower()

    for kelime in KOMUT_KARA_LISTE:
        if kelime == metin:
            return None

    for kelime, komut in eslesmeler.items():
        if kelime in metin:
            if kelime == komut:
                continue
            if komut in KOMUT_KARA_LISTE:
                continue
            if len(komut) > 30:
                continue
            return komut
    return None

# ============================================================
# GEMMA - OLLAMA (OFFLINE, GARANTI)
# ============================================================

def gemma_kontrol():
    try:
        host = ayar_getir("gemma_host", "http://localhost:11434")
        req = urllib.request.Request(f"{host}/api/tags")
        with urllib.request.urlopen(req, timeout=3) as response:
            return True
    except Exception:
        return False

def gemma_başlat():
    if gemma_kontrol():
        return True
    print("🚀 Gemma başlatılıyor...")
    try:
        subprocess.Popen(["ollama", "serve"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(5)
        return gemma_kontrol()
    except Exception as e:
        print(f"⚠️ Gemma başlatılamadı: {e}")
        return False

def gemma_sor(soru):
    try:
        if not gemma_kontrol() and ayar_getir("gemma_otomatik", True):
            if not gemma_başlat():
                return None

        if not gemma_kontrol():
            return None

        host = ayar_getir("gemma_host", "http://localhost:11434")
        model = ayar_getir("gemma_model", "gemma3:1b")

        sistem_prompt = (
            "Sen Eda'sin. Kullanıcıya 'denizçim' diye hitap edersin. "
            "Sadece soruyu cevapla, sohbet etme. Maksimum 1 cumle cevap ver. "
            "Emoji kullanma! Gereksiz konusma, direkt ve kisa cevap ver."
        )

        data = json.dumps({
            "model": model,
            "messages": [
                {"role": "system", "content": sistem_prompt},
                {"role": "user", "content": soru}
            ],
            "stream": False,
            "options": {"temperature": 0.2, "top_p": 0.8, "num_predict": 50}
        }).encode('utf-8')

        req = urllib.request.Request(
            f"{host}/api/chat", data=data,
            headers={"Content-Type": "application/json"}
        )

        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.loads(response.read().decode())
            cevap = result.get("message", {}).get("content", "").strip()
            if not cevap:
                return None
            cevap = emoji_temizle(cevap)
            cevap = cevap.replace("Eda:", "").replace("denizçim", "").strip()
            if len(cevap) > 150:
                cevap = cevap[:150].rsplit(" ", 1)[0] + "..."
            return cevap if cevap else None

    except Exception as e:
        print(f"⚠️ Gemma hatasi: {e}")
        return None

# ============================================================
# GROQ - BULUT AI (ONLINE, HIZLI, ONCELIKLI)
# ============================================================

EDA_SISTEM_PROMPTU = (
    "Sen Eda'sin. Kullanıcıya 'denizçim' diye hitap edersin. "
    "Sadece soruyu cevapla, sohbet etme. Maksimum 1 cumle cevap ver. "
    "Emoji kullanma! Gereksiz konusma, direkt ve kisa cevap ver."
)

# Bazi ucretsiz OpenRouter modelleri, asil cevap yerine ham dusunme
# surecini (chain-of-thought) veya alakasiz meta etiketleri "content"
# alaninda dondurebiliyor. Bu kaliplardan biriyle başlayan cevaplar
# gecersiz sayilip bir sonraki AI saglayicisina geciliyor.
_BOZUK_CEVAP_KALIPLARI = [
    "here's a thinking process", "here is a thinking process",
    "let me think", "okay, the user is asking", "okay, the user said",
    "user safety:", "i need to figure out", "first, i need to",
    "let's break this down", "analyze user input",
]

def _ai_cevabi_bozuk_mu(cevap):
    c = cevap.lower()
    return any(c.startswith(k) or k in c[:80] for k in _BOZUK_CEVAP_KALIPLARI)

# Groq'un Cloudflare korumasi bot/script gorunumlu istekleri engelleyebiliyor,
# bu yüzden gerçek bir tarayici User-Agent gönderiyoruz.
GROQ_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

def groq_sor(soru):
    try:
        api_key = ayar_getir("groq_api_key", "")
        if not api_key:
            print("⚠️ Groq API anahtari ayarlanmamis.")
            return None
        if not internet_var_mi():
            return None

        model = ayar_getir("groq_model", "openai/gpt-oss-20b")

        data = json.dumps({
            "model": model,
            "messages": [
                {"role": "system", "content": EDA_SISTEM_PROMPTU},
                {"role": "user", "content": soru}
            ],
            "temperature": 0.3,
            "max_tokens": 100
        }).encode('utf-8')

        req = urllib.request.Request(
            "https://api.groq.com/openai/v1/chat/completions",
            data=data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {api_key}",
                "User-Agent": GROQ_USER_AGENT
            }
        )

        with urllib.request.urlopen(req, timeout=15) as response:
            result = json.loads(response.read().decode())
            cevap = result["choices"][0]["message"]["content"].strip()
            if not cevap:
                return None
            cevap = emoji_temizle(cevap)
            if len(cevap) > 150:
                cevap = cevap[:150].rsplit(" ", 1)[0] + "..."
            return cevap if cevap else None

    except urllib.error.HTTPError as e:
        try:
            detay = e.read().decode()
        except Exception:
            detay = ""
        print(f"⚠️ Groq HTTP hatasi: {e.code} - {detay}")
        return None
    except Exception as e:
        print(f"⚠️ Groq hatasi: {e}")
        return None

# ============================================================
# DEEPSEEK - BULUT AI (ONLINE, ALTERNATIF)
# ============================================================

def deepseek_sor(soru):
    try:
        api_key = ayar_getir("deepseek_api_key", "")
        if not api_key:
            print("⚠️ DeepSeek API anahtari ayarlanmamis.")
            return None
        if not internet_var_mi():
            return None

        model = ayar_getir("deepseek_model", "deepseek-v4-flash")
        dusunme_modu = ayar_getir("deepseek_thinking", False)

        istek_govdesi = {
            "model": model,
            "messages": [
                {"role": "system", "content": EDA_SISTEM_PROMPTU},
                {"role": "user", "content": soru}
            ],
            "temperature": 0.3,
            "max_tokens": 100,
            "stream": False
        }
        # 'dusunme modu' (R1 tarzi reasoning) istege bagli olarak acilir.
        # Not: dusunme modu daha fazla token tuketir, bu da daha yuksek
        # maliyet demektir; sadece karmasik sorular icin onerilir.
        if dusunme_modu:
            istek_govdesi["thinking"] = {"type": "enabled"}

        data = json.dumps(istek_govdesi).encode('utf-8')

        req = urllib.request.Request(
            "https://api.deepseek.com/chat/completions",
            data=data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {api_key}",
                "User-Agent": GROQ_USER_AGENT
            }
        )

        with urllib.request.urlopen(req, timeout=20) as response:
            result = json.loads(response.read().decode())
            cevap = result["choices"][0]["message"]["content"].strip()
            if not cevap:
                return None
            cevap = emoji_temizle(cevap)
            if len(cevap) > 150:
                cevap = cevap[:150].rsplit(" ", 1)[0] + "..."
            return cevap if cevap else None

    except urllib.error.HTTPError as e:
        try:
            detay = e.read().decode()
        except Exception:
            detay = ""
        print(f"⚠️ DeepSeek HTTP hatasi: {e.code} - {detay}")
        return None
    except Exception as e:
        print(f"⚠️ DeepSeek hatasi: {e}")
        return None

# ============================================================
# OPENROUTER - BULUT AI (ONLINE, UCRETSIZ MODELLER)
# ============================================================

def openrouter_sor(soru):
    try:
        api_key = ayar_getir("openrouter_api_key", "")
        if not api_key:
            print("⚠️ OpenRouter API anahtari ayarlanmamis.")
            return None
        if not internet_var_mi():
            return None

        # 'openrouter/free' router'i, o an musait olan ucretsiz
        # modeller arasindan otomatik en uygun olani secer.
        model = ayar_getir("openrouter_model", "openrouter/free")

        data = json.dumps({
            "model": model,
            "messages": [
                {"role": "system", "content": EDA_SISTEM_PROMPTU},
                {"role": "user", "content": soru}
            ],
            "temperature": 0.3,
            "max_tokens": 400
        }).encode('utf-8')

        req = urllib.request.Request(
            "https://openrouter.ai/api/v1/chat/completions",
            data=data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {api_key}",
                "User-Agent": GROQ_USER_AGENT
            }
        )

        with urllib.request.urlopen(req, timeout=20) as response:
            result = json.loads(response.read().decode())
            mesaj = result["choices"][0]["message"]
            # Bazi ucretsiz 'reasoning' modelleri, token siniri
            # 'dusunme' asamasinda tukenirse content alanini bos/None
            # birakabilir. Bu durumda basarisiz sayip fallback'e dusuyoruz.
            cevap = mesaj.get("content")
            if not cevap:
                print("⚠️ OpenRouter bos cevap dondurdu (muhtemelen token siniri).")
                return None
            cevap = cevap.strip()
            if not cevap:
                return None
            if _ai_cevabi_bozuk_mu(cevap):
                print(f"⚠️ OpenRouter bozuk/ham cevap dondurdu, atlaniyor: {cevap[:60]}...")
                return None
            cevap = emoji_temizle(cevap)
            if len(cevap) > 150:
                cevap = cevap[:150].rsplit(" ", 1)[0] + "..."
            return cevap if cevap else None

    except urllib.error.HTTPError as e:
        try:
            detay = e.read().decode()
        except Exception:
            detay = ""
        print(f"⚠️ OpenRouter HTTP hatasi: {e.code} - {detay}")
        return None
    except Exception as e:
        print(f"⚠️ OpenRouter hatasi: {e}")
        return None

# ============================================================
# AI YONLENDIRICI - Groq -> DeepSeek -> Gemma (otomatik zincir)
# ============================================================

AI_SAGLAYICILAR = {
    "openrouter": ("OpenRouter", "OR", openrouter_sor),
    "groq": ("Groq", "GQ", groq_sor),
    "deepseek": ("DeepSeek", "DS", deepseek_sor),
    "gemma": ("Gemma 3.1", "Gemma", gemma_sor),
}

def aktif_ai_zinciri():
    """Sadece aktif AI saglayicilarini dondurur."""
    tercih = ayar_getir("ai_provider", "groq")
    if tercih not in AI_SAGLAYICILAR:
        tercih = "openrouter"
    
    # Aktif saglayicilari belirle
    aktifler = []
    
    # Tercih edileni ilk ekle
    aktifler.append(AI_SAGLAYICILAR[tercih][0])
    
    # Digerlerini kontrol et
    for anahtar, (ad, _kisa, _f) in AI_SAGLAYICILAR.items():
        if anahtar == tercih:
            continue
        if anahtar == "deepseek":
            # DeepSeek sadece API key varsa goster
            if ayar_getir("deepseek_api_key", ""):
                aktifler.append(ad)
        elif anahtar == "gemma":
            # Gemma her zaman en sonda
            aktifler.append(ad)
        elif anahtar == "openrouter":
            # OpenRouter sadece API key varsa goster
            if ayar_getir("openrouter_api_key", ""):
                aktifler.append(ad)
        elif anahtar == "groq":
            if ayar_getir("groq_api_key", ""):
                aktifler.append(ad)
    
    return " > ".join(aktifler)

def ai_sor(soru):
    """
    Once tercih edilen saglayiciyi (ai_provider ayari) dener, basarisiz
    olursa sirayla digerlerini dener. Gemma (offline) her zaman zincirin
    sonunda garanti olarak bulunur, boylece internet olmasa bile
    kullanıcı hicbir zaman cevapsiz kalmaz.
    """
    tercih = ayar_getir("ai_provider", "groq")
    if tercih not in AI_SAGLAYICILAR:
        tercih = "openrouter"

    sira = [tercih] + [s for s in AI_SAGLAYICILAR if s not in (tercih, "gemma")] + ["gemma"]
    denenen = set()

    for anahtar in sira:
        if anahtar in denenen:
            continue
        denenen.add(anahtar)

        ad, _kisa, fonksiyon = AI_SAGLAYICILAR[anahtar]
        print(f"🧠 {ad}'a soruluyor...")
        cevap = fonksiyon(soru)
        if cevap:
            return cevap
        print(f"🔄 {ad} basarisiz, siradaki saglayiciya geciliyor...")

    return "Şu an hiçbir AI sağlayıcısına ulaşamadım denizçim."

# ============================================================
# HAVA DURUMU
# ============================================================

def hava_durumu_al(sehir="Tebriz"):
    if not internet_var_mi():
        return "İnternet yok, hava durumu alınamıyor."
    try:
        sehir_clean = re.sub(r'[^\w\s]', '', sehir)
        url = f"https://wttr.in/{sehir_clean}?format=j1&lang=tr"
        req = urllib.request.Request(url, headers={"User-Agent": "curl/8.0"})
        with urllib.request.urlopen(req, timeout=8) as response:
            veri = json.loads(response.read().decode())
        simdi = veri["current_condition"][0]
        sicaklik = simdi.get("temp_C", "?")
        nem = simdi.get("humidity", "?")
        ruzgar = simdi.get("windspeedKmph", "?")
        açıklamalar = simdi.get("lang_tr") or simdi.get("weatherDesc")
        açıklama = açıklamalar[0]["value"] if açıklamalar else ""
        yarin = veri["weather"][1] if len(veri["weather"]) > 1 else None
        cevap = f"{sehir}'de su an {sicaklik}°C, {açıklama}. Nem %{nem}, ruzgar {ruzgar} km/s."
        if yarin:
            yarin_sicaklik = yarin.get("avgtempC", "?")
            yarin_durum = yarin.get("hourly", [{}])[0].get("lang_tr", [{}])[0].get("value", "")
            cevap += f" Yarin {yarin_sicaklik}°C, {yarin_durum} bekleniyor."
        return cevap
    except Exception:
        return "Hava durumu alınamadı."

# ============================================================
# PIL DURUMU
# ============================================================

SARJ_MODU_BEKLEME_EK = 3.0

def sarj_ediliyor_mu():
    try:
        sonuc = subprocess.run(["termux-battery-status"], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=5)
        if sonuc.returncode == 0:
            veri = json.loads(sonuc.stdout)
            return veri.get("status", "").upper() in ["CHARGING", "FULL"]
        return False
    except Exception:
        return False

def pil_bilgisi_al():
    try:
        sonuc = subprocess.run(["termux-battery-status"], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=5)
        return json.loads(sonuc.stdout)
    except Exception:
        return None

def pil_durumu():
    veri = pil_bilgisi_al()
    if veri is None:
        return "Pil bilgisi alınamadı denizçim."
    return f"Pil yüzde {veri.get('percentage', '?')}. Durum: {veri.get('status', '?')}."

# ============================================================
# SES MOTORU (TTS ENGINE) YONETIMI
# ============================================================

def ses_motorlari_al():
    """Cihazda yuklu TTS motorlarini termux-tts-engines ile listeler."""
    try:
        sonuc = subprocess.run(["termux-tts-engines"], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=5)
        if sonuc.returncode == 0 and sonuc.stdout.strip():
            return json.loads(sonuc.stdout)
    except Exception as e:
        print(f"⚠️ termux-tts-engines hatasi: {e}")
    return []

def ses_motorlarini_listele():
    motorlar = ses_motorlari_al()
    if not motorlar:
        return "Ses motorları listelenemedi denizçim. Termux:API güncel mi kontrol et."
    isimler = [m.get("name", "?") for m in motorlar]
    return "Yüklü ses motorları: " + ", ".join(isimler)

def ses_motoru_degistir(anahtar_kelime):
    """Adinda verilen anahtar kelime gecen ilk yuklu TTS motorunu secer."""
    motorlar = ses_motorlari_al()
    if not motorlar:
        return "Ses motorları listelenemedi, değiştirilemedi denizçim."
    for m in motorlar:
        ad = m.get("name", "").lower()
        if anahtar_kelime in ad:
            ayar_degistir("tts_engine", m.get("name"))
            return f"Ses motoru {m.get('name')} olarak ayarlandi denizçim."
    return f"'{anahtar_kelime}' için yüklü bir ses motoru bulamadım denizçim."

def ayarlari_ac():
    """Android sistem ayarlari ekranini gerçekten acar."""
    try:
        subprocess.Popen(["am", "start", "-a", "android.settings.SETTINGS"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return "Ayarlar açılıyor denizçim."
    except Exception:
        return "Ayarlar açılamadı denizçim."

# ============================================================
# OFFLINE HESAP MAKINESI
# ============================================================

_HESAP_GUVENLI_KARAKTERLER = set("0123456789+-*/.,() ")

def hesapla(ifade):
    """
    Basit aritmetik ifadeleri internet/AI kullanmadan hesaplar.
    Sadece rakam ve +-*/(). karakterlerine izin verir - guvenli.
    """
    temiz = ifade.replace(",", ".")
    # Turkce sayi kelimelerini de rakamla degistirebiliriz (basit versiyon)
    if not temiz or not all(c in _HESAP_GUVENLI_KARAKTERLER for c in temiz):
        return None
    try:
        sonuc = eval(temiz, {"__builtins__": {}}, {})
        if isinstance(sonuc, float) and sonuc.is_integer():
            sonuc = int(sonuc)
        return f"Sonuc: {sonuc} denizçim."
    except Exception:
        return None

# ============================================================
# TTS - EMOJI TEMIZLE + DOGRU BEKLEME SURESI
# ============================================================

TTS_ARDISIK_HATA = 0
TTS_ARDISIK_HATA_ESIGI = 3
TTS_MAKS_UZUNLUK = 200

def cevap_ver(metin):
    """
    Sesi caldiktan sonra metnin uzunluguna orantili bekler.
    Uzun metinleri teke teke bolerek TTS'in kesmesini onler.
    """
    global TTS_ARDISIK_HATA, KAYIT_AKTIF
    if not metin:
        return

    metin = emoji_temizle(metin)
    if not metin:
        return

    print(f"Eda: {metin}")
    print()
    if EDA_MODU == "sessiz":
        return
    if not komut_var_mi("termux-tts-speak"):
        return

    with kayit_lock:
        KAYIT_AKTIF = True

    # === متن را به تکه‌های کوچک تقسیم کن ===
    TTS_TEKE_UZUNLUK = 180
    teskeler = []

    if len(metin) <= TTS_TEKE_UZUNLUK:
        teskeler = [metin]
    else:
        # تقسیم بر اساس نقطه، علامت سوال، علامت تعجب
        cumleler = re.split(r'(?<=[.!?])\s+', metin)
        gecerli = ""
        for cumle in cumleler:
            if len(gecerli) + len(cumle) + 1 <= TTS_TEKE_UZUNLUK:
                gecerli = (gecerli + " " + cumle).strip()
            else:
                if gecerli:
                    teskeler.append(gecerli)
                gecerli = cumle
        if gecerli:
            teskeler.append(gecerli)

    try:
        dil = ayar_getir("tts_language", "tr-TR")
        hiz = ayar_getir("tts_rate", "1.1")
        motor = ayar_getir("tts_engine", "")

        toplam_bekleme = 0.0
        for i, tesk in enumerate(teskeler, 1):
            komut = ["termux-tts-speak", "-l", str(dil), "-r", str(hiz)]
            if motor:
                komut += ["-e", str(motor)]
            komut.append(tesk)

            subprocess.run(komut, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)

            bekle = len(tesk) * 0.08
            bekle = max(0.5, min(bekle, 6.0))
            time.sleep(bekle)
            toplam_bekleme += bekle

        TTS_ARDISIK_HATA = 0
        print(f"\u23f3 Ses bitene kadar bekleniyor: {toplam_bekleme:.1f} saniye...")

    except subprocess.TimeoutExpired:
        print("\u26a0\ufe0f TTS zaman asimina ugradi.")
        TTS_ARDISIK_HATA += 1
        if TTS_ARDISIK_HATA >= TTS_ARDISIK_HATA_ESIGI and ayar_getir("tts_engine", ""):
            ayar_degistir("tts_engine", "")
            TTS_ARDISIK_HATA = 0
        time.sleep(1.0)
    except Exception as hata:
        print(f"\u26a0\ufe0f TTS hatasi: {hata}")
        TTS_ARDISIK_HATA += 1
        time.sleep(1.0)
    finally:
        with kayit_lock:
            KAYIT_AKTIF = False
        print("\u2705 Ses bitti, simdi dinleyebilirsin...")


def fener_ac():
    try:
        subprocess.run(["termux-torch", "on"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=3)
        return "Fener açıldı denizçim."
    except Exception:
        return "Fener açılamadı."

def fener_kapat():
    try:
        subprocess.run(["termux-torch", "off"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=3)
        return "Fener kapatıldı denizçim."
    except Exception:
        return "Fener kapatılamadı."

# Ulke/sehir adi -> IANA zaman dilimi eslesmesi. "saat" sorusunda bir
# ulke adi geciyorsa, cihazin yerel saati yerine o ulkenin gerçek
# saati sorgulanir (worldtimeapi.org, ucretsiz, anahtar gerektirmez).
ULKE_ZAMAN_BOLGELERI = {
    "türkiye": "Europe/Istanbul", "turkiye": "Europe/Istanbul", "turkey": "Europe/Istanbul",
    "abd": "America/New_York", "amerika": "America/New_York", "amerika birlesik devletleri": "America/New_York",
    "almanya": "Europe/Berlin",
    "ingiltere": "Europe/London", "britanya": "Europe/London",
    "fransa": "Europe/Paris",
    "italya": "Europe/Rome",
    "ispanya": "Europe/Madrid",
    "rusya": "Europe/Moscow",
    "çin": "Asia/Shanghai", "cin": "Asia/Shanghai",
    "japonya": "Asia/Tokyo",
    "kanada": "America/Toronto",
    "avustralya": "Australia/Sydney",
    "hindistan": "Asia/Kolkata",
    "dubai": "Asia/Dubai", "birlesik arap emirlikleri": "Asia/Dubai",
    "azerbaycan": "Asia/Baku",
    "irak": "Asia/Baghdad",
    "suudi arabistan": "Asia/Riyadh",
    "iran": "Asia/Tehran",
}

def saat_komutu(metin=""):
    """
    'saat' komutunu isler. Metin icinde bir ulke adi geciyorsa
    (orn. 'türkiye'de saat kaç'), o ulkenin gerçek saatini online
    olarak sorgular. Ulke belirtilmemisse veya sorgu basarisiz
    olursa cihazin yerel saatini dondurur.
    """
    metin_l = (metin or "").lower()

    for ulke, bolge in ULKE_ZAMAN_BOLGELERI.items():
        if ulke in metin_l:
            try:
                if not internet_var_mi():
                    break
                url = f"https://worldtimeapi.org/api/timezone/{bolge}"
                req = urllib.request.Request(url, headers={"User-Agent": "curl/8.0"})
                with urllib.request.urlopen(req, timeout=5) as response:
                    veri = json.loads(response.read().decode())
                dt = datetime.fromisoformat(veri["datetime"])
                return f"{ulke.capitalize()}'de su an saat {dt.strftime('%H:%M')} denizçim."
            except Exception as e:
                print(f"⚠️ Zaman dilimi sorgusu basarisiz: {e}")
                break  # basarisiz olursa asagida cihaz saatine dus

    # Ulke belirtilmemis veya sorgu basarisiz olmus: cihazin yerel saati
    return f"Saat {datetime.now().strftime('%H:%M')} denizçim."

def tarih_komutu():
    tarih, gun_adi = semsi_tarih()
    return f"Bugun {tarih}, {gun_adi} denizçim."

def gunun_sozu():
    sozler = [
        "Damlaya damlaya gol olur.",
        "Sabreden dervis muradina ermis.",
        "Agac yasken egilir.",
        "Bir elin nesi var, iki elin sesi var.",
    ]
    return random.choice(sozler) + " denizçim."

def modu_degistir(yeni_mod):
    global EDA_MODU, CAR_MODE_AKTIF
    if yeni_mod not in MODLAR:
        return "Bu modu bilmiyorum."
    EDA_MODU = yeni_mod
    CAR_MODE_AKTIF = yeni_mod == "araba"
    mesajlar = {
        "uyku": "Uyku moduna gectim.",
        "aktif": "Aktif moddayim.",
        "sessiz": "Sessiz moda gectim.",
        "sohbet": "Sohbet modundayim.",
        "romantik": "Romantik moddayim.",
        "dinleme": "Dinleme modundayim.",
        "araba": "Araba moduna gectim."
    }
    return mesajlar.get(yeni_mod, "Mod degisti.")

def mod_mesaji():
    return f"Eda modu: {EDA_MODU}."

def dolar_al():
    try:
        url = "https://api.tgju.org/v1/market/indicator/summary-table-data/price_dollar_rl"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=8) as response:
            veri = json.loads(response.read().decode())
            fiyat_riyal = int(veri["data"][0][1].replace(",", ""))
            toman = fiyat_riyal // 10
            return f"Dolar: {toman:,} Toman"
    except Exception as e:
        print(f"Dolar API hatasi: {e}")
        return "Dolar: Bilinmiyor"

def altin_al():
    try:
        url = "https://api.tgju.org/v1/market/indicator/summary-table-data/geram18"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=8) as response:
            veri = json.loads(response.read().decode())
            fiyat_riyal = int(veri["data"][0][1].replace(",", ""))
            toman = fiyat_riyal // 10
            return f"Altin 18 ayar: {toman:,} Toman"
    except Exception as e:
        print(f"Altin API hatasi: {e}")
        return "Altın: Bilinmiyor"

def muzik_başlat():
    try:
        subprocess.Popen(["am", "start", "-n", "com.sec.android.app.music/com.sec.android.app.music.MusicActivity"])
        subprocess.Popen(["input", "keyevent", "KEYCODE_MEDIA_PLAY"])
        return "Müzik başladı."
    except Exception:
        return "Müzik başlatılamadı."

def muzik_durdur():
    try:
        subprocess.Popen(["input", "keyevent", "KEYCODE_MEDIA_PAUSE"])
        return "Müzik durduruldu."
    except Exception:
        return "Müzik durdurulamadı."

def muzik_sonraki():
    try:
        subprocess.Popen(["input", "keyevent", "KEYCODE_MEDIA_NEXT"])
        return "Sonraki şarkı."
    except Exception:
        return "Sonraki şarkıya geçilemedi."

def muzik_onçeki():
    try:
        subprocess.Popen(["input", "keyevent", "KEYCODE_MEDIA_PREVIOUS"])
        return "Önceki şarkı."
    except Exception:
        return "Önceki şarkıya geçilemedi."

def konum_al():
    if not komut_var_mi("termux-location"):
        return "Konum için Termux:API gerekli."
    try:
        sonuc = subprocess.run(["termux-location", "-p", "network"], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=10)
        if sonuc.returncode != 0:
            return "Konum alınamadı."
        veri = json.loads(sonuc.stdout)
        return f"Konumun yaklasik {veri.get('latitude','?')}, {veri.get('longitude','?')} denizçim."
    except Exception:
        return "Konum alınamadı."

def konum_al_mesafe():
    try:
        sonuc = subprocess.run(["termux-location", "-p", "network"], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=10)
        if sonuc.returncode == 0:
            veri = json.loads(sonuc.stdout)
            return veri.get("latitude"), veri.get("longitude")
    except Exception:
        pass
    return None, None

def mesafe_hesapla(lat1, lon1):
    if EV_LAT is None or EV_LON is None:
        return None
    lat2, lon2 = EV_LAT, EV_LON
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon/2)**2
    return R * 2 * math.asin(math.sqrt(a))

def eve_mesafe_söyle():
    lat, lon = konum_al_mesafe()
    if lat is None:
        return "Konum alınamadı. GPS'i aç."
    distance = mesafe_hesapla(lat, lon)
    if distance is None:
        return "Ev konumu ayarlanmamış."
    if distance < 1:
        return f"Sadece {distance * 1000:.0f} metre kaldi! Cok yakinsin!"
    return f"Yaklasik {distance:.1f} kilometre kaldi."

# ============================================================
# GUVENLIK MODU
# ============================================================

def guvenlik_modu_ac():
    global SECURITY_MODE_AKTIF
    SECURITY_MODE_AKTIF = True
    return "Güvenlik modu aktif!"

def guvenlik_modu_kapat():
    global SECURITY_MODE_AKTIF
    SECURITY_MODE_AKTIF = False
    return "Güvenlik modu kapandı."

def telefonu_kilitle():
    try:
        subprocess.Popen(["input", "keyevent", "KEYCODE_POWER"])
        return "Telefon kilitlendi."
    except Exception:
        return "Kilitlenemedi."

def telefonu_bul():
    try:
        subprocess.Popen(["termux-media-player", "play", "/system/media/audio/ringtones/Default.ogg"])
        return "Telefonu arıyorum. Ses geliyor mu?"
    except Exception:
        try:
            subprocess.Popen(["termux-media-player", "play", "/system/media/audio/alarms/Alarm_Classic.ogg"])
            return "Telefonu arıyorum. Ses geliyor mu?"
        except Exception:
            return "Ses çalınamadı."

def fotöğraf_çek():
    try:
        dosya = os.path.join(KLASOR, f"foto_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg")
        subprocess.run(["termux-camera-photo", "-c", "0", dosya], timeout=10)
        if os.path.exists(dosya):
            return f"Fotoğraf çekildi: {os.path.basename(dosya)}"
        return "Fotoğraf çekilemedi."
    except Exception:
        return "Kamera kullanılamıyor."

def konum_gönder():
    telefon_numarasi = ayar_getir("guvenlik_numara", "")
    if not telefon_numarasi:
        return "Güvenlik numarası ayarlanmamış denizçim."
    try:
        sonuc = subprocess.run(["termux-location", "-p", "network"], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=15)
        if sonuc.returncode != 0:
            return "Konum alınamadı."
        veri = json.loads(sonuc.stdout)
        lat, lon = veri.get("latitude", "?"), veri.get("longitude", "?")
        mesaj = f"Konumum: https://maps.google.com/?q={lat},{lon}"
        subprocess.Popen(["termux-sms-send", "-n", telefon_numarasi, mesaj],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return f"Konum {telefon_numarasi} numarasına gönderildi."
    except Exception:
        return "Konum gönderilemedi."

# ============================================================
# HATIRLATMA SISTEMI
# ============================================================

def hatirlatma_yukle():
    return json_yükle(HATIRLATMA_DOSYASI, [])

def hatirlatma_kaydet(veri):
    return json_kaydet(HATIRLATMA_DOSYASI, veri)

def zaman_detection(metin):
    simdi = datetime.now()

    saat_sonra = re.search(r"(\d+)\s*(?:saat|saa?t)\s*(?:sonra|içinde)", metin)
    if saat_sonra:
        yeni_saat = (simdi.hour + int(saat_sonra.group(1))) % 24
        return yeni_saat, simdi.minute

    dakika_sonra = re.search(r"(\d+)\s*(?:dakika|dk)\s*(?:sonra|içinde)", metin)
    if dakika_sonra:
        ekleneçek = int(dakika_sonra.group(1))
        yeni_dakika = simdi.minute + ekleneçek
        yeni_saat = simdi.hour
        if yeni_dakika >= 60:
            yeni_saat += yeni_dakika // 60
            yeni_dakika %= 60
        yeni_saat %= 24
        return yeni_saat, yeni_dakika

    patterns = [
        r"saa?t\s*(\d{1,2})\s*(?:\.|:|'te|'de|'da|'ta)?\s*(\d{2})?\s*(?:sabah|aksam|gece)?",
        r"(\d{1,2})\s*(?:\.|:|'te|'de|'da|'ta)?\s*(\d{2})?\s*(?:sabah|aksam|gece)",
    ]
    for pattern in patterns:
        eslesme = re.search(pattern, metin)
        if eslesme:
            saat = int(eslesme.group(1))
            dakika = int(eslesme.group(2)) if eslesme.group(2) else 0
            if "aksam" in metin or "gece" in metin:
                if saat < 12:
                    saat += 12
            elif "sabah" in metin and saat == 12:
                saat = 0
            return saat, dakika
    return None, None

def eda_ile_hatırlat(metin, saat, dakika):
    hatırlatmalar = hatirlatma_yukle()
    temiz_metin = re.sub(r"(hatırlat|saa?t\s*\d{1,2}\s*[:.]?\s*\d{0,2})", "", metin).strip()
    if not temiz_metin:
        temiz_metin = "Hatırlatma"

    hatırlatmalar.append({
        "metin": temiz_metin, "saat": saat, "dakika": dakika,
        "tarih": datetime.now().strftime("%Y-%m-%d %H:%M"), "yapıldı": False
    })
    hatirlatma_kaydet(hatırlatmalar)
    return f"Saat {saat:02d}:{dakika:02d} için '{temiz_metin}' hatırlatıcısı kuruldu!"

def hatirlatmalari_listele():
    hatırlatmalar = hatirlatma_yukle()
    bekleyenler = [h for h in hatırlatmalar if not h.get("yapıldı")]
    if not bekleyenler:
        return "Bekleyen hatırlatman yok denizçim."
    parcalar = []
    for h in bekleyenler[:5]:
        parcalar.append(f"Saat {h['saat']:02d}:{h['dakika']:02d} - {h.get('metin','Hatırlatma')}")
    return "Hatırlatmaların: " + ". ".join(parcalar)

def hatirlatmalari_temizle():
    hatirlatma_kaydet([])
    return "Tüm hatırlatmalar silindi denizçim."

def hatirlatma_kontrol():
    global KAYIT_AKTIF
    while True:
        try:
            simdi = datetime.now()
            hatırlatmalar = hatirlatma_yukle()
            degisti = False

            for h in hatırlatmalar:
                if not h.get("yapıldı") and h["saat"] == simdi.hour and h["dakika"] == simdi.minute:
                    h["yapıldı"] = True
                    degisti = True
                    metin = h.get("metin", "Hatırlatma")

                    while True:
                        with kayit_lock:
                            if not KAYIT_AKTIF:
                                KAYIT_AKTIF = True
                                break
                        time.sleep(0.5)

                    try:
                        subprocess.Popen(["termux-tts-speak", "-l", "tr-TR", "-r", "1.0", f"Denizcim! {metin}"])
                        time.sleep(1)
                        subprocess.Popen(["termux-notification", "--title", "Eda Hatirlatici",
                                           "--content", metin, "--sound", "true"])
                    except Exception as e:
                        print(f"Hatırlatma hatasi: {e}")
                    finally:
                        with kayit_lock:
                            KAYIT_AKTIF = False

            if degisti:
                hatirlatma_kaydet(hatırlatmalar)
        except Exception as e:
            print(f"Hatırlatma kontrol hatasi: {e}")
        time.sleep(30)

def hatirlatma_baslangic():
    try:
        thread = threading.Thread(target=hatirlatma_kontrol, daemon=True)
        thread.start()
        print("🔔 Hatırlatma sistemi aktif!")
        return thread
    except Exception as e:
        print(f"Hatırlatma başlatılamadı: {e}")
        return None

# ============================================================
# HAFIZA VE GUNLUK
# ============================================================

def hafiza_yukle():
    veri = json_yükle(MEMORY_DOSYASI, {"notlar": []})
    if not isinstance(veri, dict):
        return {"notlar": []}
    veri.setdefault("notlar", [])
    return veri

def hafiza_kaydet(veri):
    return json_kaydet(MEMORY_DOSYASI, veri)

def hafizaya_ekle(metin, kategori="genel"):
    hafıza = hafiza_yukle()
    notlar = hafıza.get("notlar", [])
    notlar.append({"tarih": datetime.now().strftime("%Y-%m-%d %H:%M"), "kategori": kategori, "metin": metin})
    hafıza["notlar"] = notlar[-NOT_LIMITI:]
    hafiza_kaydet(hafıza)

def hafizayi_goster():
    notlar = hafiza_yukle().get("notlar", [])
    if not notlar:
        return "Hafızamda bir şey yok."
    cevaplar = ["Hafızamda:"]
    for i, not_ in enumerate(notlar[-10:], 1):
        cevaplar.append(f"{i}. {not_.get('metin', '')}")
    return "\n".join(cevaplar)

def gunluk_yükle():
    return json_yükle(DIARY_DOSYASI, [])

def gunluk_kaydet(veri):
    return json_kaydet(DIARY_DOSYASI, veri)

def gunluge_ekle(metin):
    gunluk = gunluk_yükle()
    gunluk.append({"tarih": datetime.now().strftime("%Y-%m-%d %H:%M"), "metin": metin})
    gunluk_kaydet(gunluk[-GUNLUK_LIMITI:])

def gunlugu_göster():
    gunluk = gunluk_yükle()
    if not gunluk:
        return "Günlük boş."
    cevaplar = ["Gunluk:"]
    for kayit in gunluk[-10:]:
        cevaplar.append(f"{kayit.get('tarih', '')}: {kayit.get('metin', '')}")
    return "\n".join(cevaplar)

# ============================================================
# MIKROFON / VOSK
# ============================================================

_VOSK_MODEL = None
_VOSK_DENENDI = False

def vosk_modelini_yükle():
    global _VOSK_MODEL, _VOSK_DENENDI
    if _VOSK_DENENDI:
        return _VOSK_MODEL
    _VOSK_DENENDI = True
    if not VOSK_KUTUPHANESI_VAR or not os.path.isdir(VOSK_MODEL_YOLU):
        return None
    try:
        print("🧠 Vosk modeli yükleniyor...")
        _VOSK_MODEL = VoskModel(VOSK_MODEL_YOLU)
        print("✅ Vosk modeli hazır.")
        return _VOSK_MODEL
    except Exception as hata:
        print(f"⚠️ Vosk yüklenemedi: {hata}")
        _VOSK_MODEL = None
        return None

def vosk_ile_metne_çevir(wav_dosya):
    model = vosk_modelini_yükle()
    if model is None:
        return None
    try:
        with wave.open(wav_dosya, "rb") as wf:
            if wf.getnchannels() != 1 or wf.getsampwidth() != 2:
                return None
            rec = KaldiRecognizer(model, wf.getframerate())
            rec.SetWords(False)
            parcalar = []
            while True:
                veri = wf.readframes(4000)
                if len(veri) == 0:
                    break
                if rec.AcceptWaveform(veri):
                    parca = json.loads(rec.Result()).get("text", "")
                    if parca:
                        parcalar.append(parca)
            son = json.loads(rec.FinalResult()).get("text", "")
            if son:
                parcalar.append(son)
        return " ".join(parcalar).strip()
    except Exception as hata:
        print(f"⚠️ Vosk hatasi: {hata}")
        return None

# ============================================================
# METIN TEMIZLE
# ============================================================

def _metni_temizle(metin):
    metin = metin.lower().strip()
    metin = emoji_temizle(metin)
    replacements = {
        "eda ": "eda ", "eda ": "eda ", "eda": "eda", "eda": "eeda",
        "eda": "eda", "eda": "eda", "eda": "eda", "eeda": "eda",
        "eda": "eda", "eda": "eda", "eda": "eda", "eda": "eda",
        "eda": "eda",
    }
    for eski, yeni in replacements.items():
        metin = metin.replace(eski, yeni)
    return metin.strip()

def kelime_benzerligi(kelime, hedef):
    return difflib.SequenceMatcher(None, kelime, hedef).ratio()

def metinde_benzer_kelime_var_mi(metin, hedef, esik=0.72):
    for kelime in re.findall(r"\w+", metin.lower()):
        if len(kelime) >= 2 and kelime_benzerligi(kelime, hedef) >= esik:
            return True
    return False

def _whisper_halusinasyon_mu(metin):
    m = metin.lower()
    return any(kalip in m for kalip in WHISPER_HALUSINASYON_KALIPLARI)

def whisper_ile_metne_çevir(wav_dosya, model=None):
    if model is None:
        model = secili_whisper_modeli()
    if not (os.path.exists(WHISPER_BINARY) and os.path.exists(model)):
        return None

    cikti_dosya = WHISPER_CIKTI_ONEK + ".txt"
    try:
        if os.path.exists(cikti_dosya):
            os.remove(cikti_dosya)

        subprocess.run([WHISPER_BINARY, "-m", model, "-f", wav_dosya, "-l", "tr",
                        "--prompt", WHISPER_BAGLAM_PROMPTU, "-otxt", "-of", WHISPER_CIKTI_ONEK],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)

        if not os.path.exists(cikti_dosya):
            return None

        with open(cikti_dosya, "r", encoding="utf-8") as f:
            metin = f.read().strip()

        if metin and _whisper_halusinasyon_mu(metin):
            print(f"⚠️ Halusinasyon filtrelendi: {metin[:50]}...")
            return ""

        return metin

    except subprocess.TimeoutExpired:
        print("⚠️ whisper.cpp zaman asimi.")
        return None
    except Exception as hata:
        print(f"⚠️ whisper.cpp hatasi: {hata}")
        return None
    finally:
        if os.path.exists(cikti_dosya):
            try:
                os.remove(cikti_dosya)
            except Exception:
                pass

def _google_ile_tani():
    tanıyıcı = sr.Recognizer()
    tanıyıcı.energy_threshold = 300
    tanıyıcı.dynamic_energy_threshold = True
    tanıyıcı.pause_threshold = 0.6
    tanıyıcı.non_speaking_duration = 0.3
    tanıyıcı.operation_timeout = 5

    with sr.AudioFile(SES_WAV) as kaynak:
        ses = tanıyıcı.record(kaynak)

    print("🌐 Google'a baglaniyor...")
    metin = tanıyıcı.recognize_google(ses, language="tr-TR")
    return _metni_temizle(metin)

def sesden_metne():
    if not os.path.exists(SES_WAV):
        return None

    try:
        print("🧠 Ses metne çevriliyor...")

        if internet_var_mi():
            try:
                metin = _google_ile_tani()
                if metin:
                    print(f"📝 Duyulan (Google): {metin}")
                    return metin
            except Exception as e:
                print(f"⚠️ Google hatasi: {e}")

        print("🔄 Google calismadi, Whisper deneniyor...")
        whisper_sonuc = whisper_ile_metne_çevir(SES_WAV)
        if whisper_sonuc:
            metin = _metni_temizle(whisper_sonuc)
            if metin and not _whisper_halusinasyon_mu(metin):
                print(f"📝 Duyulan (Whisper): {metin}")
                return metin

        return None

    except Exception as hata:
        print(f"❌ Tanima hatasi: {hata}")
        return None
    finally:
        try:
            os.remove(SES_WAV)
        except Exception:
            pass

# ============================================================
# UYKU MODU - HAFIF (PIL DOSTU) TANIMA
# ============================================================

def sessiz_mi(wav_dosya, esik=UYKU_SESSIZLIK_ESIGI):
    """
    Kaydin sesli mi sessiz mi oldugunu RMS enerjisiyle hizlica kontrol eder.
    Sessizse tanima motoru hic calistirilmaz - CPU/pil tasarrufu saglar.
    """
    try:
        with wave.open(wav_dosya, "rb") as wf:
            veri = wf.readframes(wf.getnframes())
        if not veri:
            return True
        return audioop.rms(veri, 2) < esik
    except Exception:
        return False

def uyku_sesden_metne():
    """
    Uyku modunda SADECE uyanma kelimesini ('Eda') yakalamak icin kullanilir.
    Google (internet/radyo acar) ve buyuk/agir Whisper modelleri KULLANILMAZ.
    Once sessizlik kontrolu yapilir (çoğu zaman ortam sessizdir, boylece hic
    tanima çalismaz), sonra sadece hafif ve tamamen offline olan Vosk (varsa)
    denenir; Vosk yoksa en hafif Whisper modeli (base) son çare olarak kullanilir.
    Bu sayede uyku modunda pil tuketimi minimuma iner.
    """
    if not os.path.exists(SES_WAV):
        return None
    try:
        if sessiz_mi(SES_WAV):
            return None

        if VOSK_KUTUPHANESI_VAR:
            metin = vosk_ile_metne_çevir(SES_WAV)
            if metin:
                return _metni_temizle(metin)
            return None

        if os.path.exists(WHISPER_BINARY) and os.path.exists(WHISPER_MODEL_BASE):
            metin = whisper_ile_metne_çevir(SES_WAV, model=WHISPER_MODEL_BASE)
            if metin and not _whisper_halusinasyon_mu(metin):
                return _metni_temizle(metin)
            return None

        return None
    except Exception as hata:
        print(f"⚠️ Uyku modu tanima hatasi: {hata}")
        return None
    finally:
        try:
            os.remove(SES_WAV)
        except Exception:
            pass

# ============================================================
# UYANMA
# ============================================================

def eda_uyandi_mi(metin):
    if not metin:
        return False
    metin = metin.lower()
    kelimeler = ayar_getir("wake_words", DEFAULT_SETTINGS["wake_words"])
    
    # Metni kelimelere ayir
    metin_kelimeleri = re.findall(r"\w+", metin)
    
    # Her wake_word'u kelime kelime kontrol et
    for wake in kelimeler:
        wake_kelimeleri = re.findall(r"\w+", wake.lower())
        
        # Eger wake_word tek kelimeyse (eda gibi), tam eslesme ara
        if len(wake_kelimeleri) == 1:
            if wake_kelimeleri[0] in metin_kelimeleri:
                return True
        else:
            # Cok kelimeli wake_word (hey eda gibi), ardisik eslesme ara
            for i in range(len(metin_kelimeleri) - len(wake_kelimeleri) + 1):
                if metin_kelimeleri[i:i+len(wake_kelimeleri)] == wake_kelimeleri:
                    return True
    
    return False

# ============================================================
# ANA KOMUT ISLEME
# ============================================================

# ============================================================
# SISTEM TESTI
# ============================================================

def sistem_testi():
    """Tum EDA sistemlerini gercekten test eder."""
    sonuclar = []
    sonuclar.append("Sistem testi başlıyor")
    
    # 1. Hafıza
    try:
        hafıza = hafiza_yukle()
        not_sayisi = len(hafıza.get("notlar", []))
        sonuclar.append("Hafıza OK, " + str(not_sayisi) + " not")
    except Exception as e:
        print("Hafıza test hatasi: " + str(e))
        sonuclar.append("Hafıza HATA")
    
    # 2. Mikrofon
    if komut_var_mi("termux-microphone-record"):
        sonuclar.append("Mikrofon OK")
    else:
        sonuclar.append("Mikrofon HATA")
    
    # 3. TTS
    if komut_var_mi("termux-tts-speak"):
        sonuclar.append("TTS OK")
    else:
        sonuclar.append("TTS HATA")
    
    # 4. Batarya
    pil = pil_bilgisi_al()
    if pil:
        sonuclar.append("Batarya OK, yüzde " + str(pil.get("percentage", "?")))
    else:
        sonuclar.append("Batarya HATA")
    
    # 5. Öğrenme
    if ayar_getir("öğrenme_aktif", True):
        sonuclar.append("Öğrenme aktif")
    else:
        sonuclar.append("Öğrenme pasif")
    
    # 6. Hatırlatma
    try:
        hatırlatma = hatirlatma_yukle()
        bekleyen = len([h for h in hatırlatma if not h.get("yapildi")])
        sonuclar.append("Hatırlatma OK, " + str(bekleyen) + " bekleyen")
    except Exception as e:
        print("Hatırlatma test hatasi: " + str(e))
        sonuclar.append("Hatırlatma HATA")
    
    # 7. Konum
    if komut_var_mi("termux-location"):
        sonuclar.append("Konum OK")
    else:
        sonuclar.append("Konum HATA")
    
    # 8. İnternet
    if internet_var_mi():
        sonuclar.append("İnternet online")
    else:
        sonuclar.append("İnternet offline")
    
    # 9. Online AI (OpenRouter)
    if ayar_getir("openrouter_api_key", "") and internet_var_mi():
        sonuclar.append("Online AI hazır")
    else:
        sonuclar.append("Online AI pasif")
    
    # 10. Offline AI (Gemma)
    if gemma_kontrol():
        sonuclar.append("Offline AI hazır")
    else:
        sonuclar.append("Offline AI pasif")
    
    sonuclar.append("Sistem testi tamamlandı")
    return ". ".join(sonuclar)

def komut_isle_tek_direkt(metin):
    metin = metin.lower().strip()

    # ONEMLI: "fener" kontrolu "kapat" kontrolunden ONCE gelir, aksi
    # halde "feneri kapat" demek tum uygulamayi kapatirdi (eski bug).
    if "fener" in metin:
        if "ac" in metin:
            return fener_ac()
        elif "kapat" in metin:
            return fener_kapat()

    # Ses motoru (TTS engine) komutlari - AI'ye gitmeden direkt islenir,
    # aksi halde AI cihazda olmayan bulut TTS servisleri uydurabiliyor.
    # "motor" tek basina yeterli (STT bazen "ses" kelimesini "sis" olarak
    # yanlis algiliyor, bu yüzden sadece "ses motor" aramiyoruz).
    if "motor" in metin:
        if "listele" in metin:
            return ses_motorlarini_listele()
        if "samsung" in metin:
            return ses_motoru_degistir("samsung")
        if "google" in metin:
            return ses_motoru_degistir("google")

    # Bip sesi acma/kapama - AI'ye gitmeden direkt ayari degistirir.
    # AI'ye birakilirsa sadece "actim" der ama gerçekte hicbir sey
    # degismez (asagidaki gibi hallusinasyon: "Bip sesi açık denizçim").
    if "bip" in metin or "beep" in metin:
        if "kapat" in metin:
            ayar_degistir("beep_aktif", False)
            return "Bip sesi kapatıldı denizçim."
        if any(x in metin for x in ["ac", "aç"]):
            ayar_degistir("beep_aktif", True)
            return "Bip sesi açıldı denizçim."

    # Telefon ayarlarini gerçekten acan komut (AI'nin sadece "aciyorum"
    # deyip hicbir sey yapmamasini onlemek icin).
    if "ayarlar" in metin and any(x in metin for x in ["ac", "aç"]):
        return ayarlari_ac()
    
    # Sistem testi komutu
    if "sistem" in metin and any(x in metin for x in ["test", "kontrol"]):
        return sistem_testi()

    # Hatırlatmalari listeleme/silme - "hatırlat" ekleme akisindan
    # once kontrol edilir ki "hatırlatmalari listele" yanlislikla
    # yeni bir hatırlatma eklemeye calisilmasin.
    if "hatırlatma" in metin:
        if "sil" in metin or "temizle" in metin:
            return hatirlatmalari_temizle()
        if any(x in metin for x in ["listele", "göster", "neler"]):
            return hatirlatmalari_listele()

    # Hafıza komutlari
    if "hafıza" in metin:
        if any(x in metin for x in ["göster", "oku", "listele", "neler"]):
            return hafizayi_goster()
        if "ekle" in metin:
            icerik = re.sub(r".*hafıza\w*\s*(ya|na)?\s*ekle\s*", "", metin).strip()
            if not icerik:
                icerik = metin
            hafizaya_ekle(icerik)
            return "Hafızaya ekledim denizçim."

    # Gunluk (gunce) komutlari
    if "gunluk" in metin or "günlük" in metin:
        if any(x in metin for x in ["göster", "oku", "listele"]):
            return gunlugu_göster()
        if any(x in metin for x in ["ekle", "yaz"]):
            return GUNLUK_ICERIK_BEKLENIYOR

    # Basit aritmetik hesap - internete/AI'ye gitmeden döğrudan hesaplar.
    if "hesapla" in metin or "kaç eder" in metin or "kac eder" in metin:
        sadece_ifade = re.sub(r"[^0-9+\-*/.,() ]", " ", metin)
        sonuc = hesapla(sadece_ifade)
        if sonuc:
            return sonuc

    # "kapat" cok genel bir kelime oldugu icin, tum uygulamayi kapatmadan
    # once bir onay adimi ister. Onay EDA_KAPAT_ONAY_ISTE sinyaliyle
    # komut dongusune bildirilir; asil kapanma ancak onay alındıktan
    # sonra gerçeklesir.
    if "kapat" in metin:
        return EDA_KAPAT_ONAY_ISTE
    if "uyku" in metin or metin in ["uyu", "uyku"]:
        return EDA_UYKU
    if any(x in metin for x in ["pil", "batarya", "şarj", "sarj", "şarjım", "sarjim"]):
        return pil_durumu()
    if "saat" in metin:
        return saat_komutu(metin)
    return None

def komut_isle_tek(metin):
    if not metin:
        return "Anlayamadim denizçim."

    if not hasattr(komut_isle_tek, "_derinlik"):
        komut_isle_tek._derinlik = 0

    komut_isle_tek._derinlik += 1
    if komut_isle_tek._derinlik > 3:
        komut_isle_tek._derinlik = 0
        return "Cok fazla deneme yaptim denizçim. Biraz dur."

    metin = metin.lower().strip()
    metin = metin.replace("?", "").replace("!", "").replace(",", " ").replace(".", " ")

    print(f"⚙️ Komut: {metin}")

    sonuc = komut_isle_tek_direkt(metin)
    if sonuc:
        komut_isle_tek._derinlik = 0
        return sonuc

    öğrenilen = öğrenmeden_bul(metin)
    if öğrenilen:
        print(f"📚 Öğrenilmis: '{öğrenilen}'")
        sonuc = komut_isle_tek_direkt(öğrenilen)
        if sonuc:
            komut_isle_tek._derinlik = 0
            return sonuc
        return komut_isle_tek(öğrenilen)

    if "sohbet" in metin:
        öğren_komut_eslesmesi(metin, "sohbet")
        komut_isle_tek._derinlik = 0
        return modu_degistir("sohbet")
    if "romantik" in metin:
        öğren_komut_eslesmesi(metin, "romantik")
        komut_isle_tek._derinlik = 0
        return modu_degistir("romantik")
    if "sessiz" in metin:
        öğren_komut_eslesmesi(metin, "sessiz")
        komut_isle_tek._derinlik = 0
        return modu_degistir("sessiz")
    if any(x in metin for x in ["araba", "masin"]):
        öğren_komut_eslesmesi(metin, "araba")
        komut_isle_tek._derinlik = 0
        return modu_degistir("araba")

    if "guvenlik" in metin:
        if "ac" in metin:
            öğren_komut_eslesmesi(metin, "guvenlik ac")
            komut_isle_tek._derinlik = 0
            return guvenlik_modu_ac()
        elif "kapat" in metin:
            öğren_komut_eslesmesi(metin, "guvenlik kapat")
            komut_isle_tek._derinlik = 0
            return guvenlik_modu_kapat()

    if "kilitle" in metin:
        öğren_komut_eslesmesi(metin, "kilitle")
        komut_isle_tek._derinlik = 0
        return telefonu_kilitle()
    if "bul" in metin:
        öğren_komut_eslesmesi(metin, "bul")
        komut_isle_tek._derinlik = 0
        return telefonu_bul()
    if "foto" in metin:
        öğren_komut_eslesmesi(metin, "fotöğraf")
        komut_isle_tek._derinlik = 0
        return fotöğraf_çek()
    if "konum gönder" in metin:
        öğren_komut_eslesmesi(metin, "konum gönder")
        komut_isle_tek._derinlik = 0
        return konum_gönder()

    if "hava" in metin:
        öğren_komut_eslesmesi(metin, "hava")
        komut_isle_tek._derinlik = 0
        return hava_durumu_al()
    if "tarih" in metin:
        öğren_komut_eslesmesi(metin, "tarih")
        komut_isle_tek._derinlik = 0
        return tarih_komutu()
    if "soz" in metin:
        öğren_komut_eslesmesi(metin, "soz")
        komut_isle_tek._derinlik = 0
        return gunun_sozu()
    if "dolar" in metin:
        öğren_komut_eslesmesi(metin, "dolar")
        komut_isle_tek._derinlik = 0
        return dolar_al()
    if "altin" in metin:
        öğren_komut_eslesmesi(metin, "altin")
        komut_isle_tek._derinlik = 0
        return altin_al()

    if "muzik" in metin:
        if "başlat" in metin:
            öğren_komut_eslesmesi(metin, "muzik başlat")
            komut_isle_tek._derinlik = 0
            return muzik_başlat()
        elif "durdur" in metin:
            öğren_komut_eslesmesi(metin, "muzik durdur")
            komut_isle_tek._derinlik = 0
            return muzik_durdur()
        elif "sonraki" in metin:
            komut_isle_tek._derinlik = 0
            return muzik_sonraki()
        elif "onçeki" in metin:
            komut_isle_tek._derinlik = 0
            return muzik_onçeki()

    if "mesafe" in metin:
        öğren_komut_eslesmesi(metin, "mesafe")
        komut_isle_tek._derinlik = 0
        return eve_mesafe_söyle()
    if "konum" in metin:
        öğren_komut_eslesmesi(metin, "konum")
        komut_isle_tek._derinlik = 0
        return konum_al()

    if any(x in metin for x in ["hatırlat", "hatırla", "remind", "cagir", "uyandir"]):
        saat, dakika = zaman_detection(metin)
        if saat is not None:
            öğren_komut_eslesmesi(metin, "hatırlat")
            komut_isle_tek._derinlik = 0
            return eda_ile_hatırlat(metin, saat, dakika)
        komut_isle_tek._derinlik = 0
        return "Zaman belirt denizçim. Örnek: 'saat 8' veya '1 saat sonra'"

    if len(metin.split()) >= 2 and ayar_getir("ai_aktif", True):
        cevap = ai_sor(metin)
        if cevap:
            öğren_komut_eslesmesi(metin, "sohbet")
            komut_isle_tek._derinlik = 0
            return cevap
        komut_isle_tek._derinlik = 0
        return "Anlamadım denizçim. Biraz daha açık konuşabilir misin?"

    komut_isle_tek._derinlik = 0
    return "Anlayamadim denizçim."

def komut_isle_coklu(metin):
    if not metin:
        return None

    ayrac = r'\s+ve\s+|\s*,\s*|\s+ve\s*,\s*|\s+ile\s+|\s+ve\s+ile\s+'
    parcalar = re.split(ayrac, metin)

    if len(parcalar) <= 1:
        return None

    cevaplar = []
    gorsel_cevaplar = []
    uyku_komutu = False
    kapat_komutu = False

    for parca in parcalar:
        parca = parca.strip()
        if not parca:
            continue

        sonuc = komut_isle_tek(parca)
        if sonuc == EDA_UYKU:
            uyku_komutu = True
        elif sonuc in (EDA_KAPAT, EDA_KAPAT_ONAY_ISTE):
            kapat_komutu = True
        elif sonuc and sonuc not in [EDA_KAPAT, EDA_KAPAT_ONAY_ISTE, EDA_UYKU, GUNLUK_ICERIK_BEKLENIYOR]:
            if sonuc not in gorsel_cevaplar:
                gorsel_cevaplar.append(sonuc)
                cevaplar.append(sonuc)

    if uyku_komutu:
        return f"{'. '.join(cevaplar)} Simdi uyuyorum." if cevaplar else EDA_UYKU
    if kapat_komutu:
        # Kapanma onayi gerektirdigi icin, coklu komutta bile direkt
        # kapatmiyoruz - onay istemi komut dongusune birakiliyor.
        return EDA_KAPAT_ONAY_ISTE
    if cevaplar:
        return ". ".join(cevaplar)
    return None

def komut_isle(metin):
    if not metin:
        return komut_isle_tek(metin)
    coklu = komut_isle_coklu(metin)
    if coklu is not None:
        return coklu
    return komut_isle_tek(metin)

# ============================================================
# TANIMA MODU
# ============================================================

def tanima_modu():
    return ayar_getir("tanima_modu", "auto")

def tanima_modu_degistir(mod):
    if mod in ["online", "offline", "auto"]:
        ayar_degistir("tanima_modu", mod)
        return f"Ses tanima {mod} yapıldı."
    return "Gecersiz mod."

def fiili_tanima_modu():
    secim = tanima_modu()
    if secim == "auto":
        return "online" if internet_var_mi() else "offline"
    return secim

# ============================================================
# MESAJLAR
# ============================================================

VEDA_MESAJLARI = [
    "Tamam denizçim, sistemleri kapatıyorum. Görüşmek üzere.",
    "Işıkları söndürüyorum denizçim. İhtiyacın olduğunda buradayım.",
    "Tamam denizçim, bir sonrakine kadar hoşça kal.",
    "Devre dışı kalıyorum denizçim. Sesini duyana kadar.",
    "Tamam denizçim. Kendine iyi bak, ben burada bekliyorum.",
]

def veda_mesaji():
    return random.choice(VEDA_MESAJLARI)

# ============================================================
# PIL BILDIRIMI
# ============================================================

def pil_bildirimi_dongusu():
    time.sleep(PIL_BILDIRIM_ARALIGI)
    while True:
        try:
            veri = pil_bilgisi_al()
            if veri is not None and EDA_MODU != "sessiz":
                cevap_ver(f"Pil yüzde {veri.get('percentage', '?')} denizçim.")
        except Exception:
            pass
        time.sleep(PIL_BILDIRIM_ARALIGI)

def pil_bildirim_başlangic():
    try:
        thread = threading.Thread(target=pil_bildirimi_dongusu, daemon=True)
        thread.start()
        return thread
    except Exception:
        return None

# ============================================================
# WAKE LOCK
# ============================================================

def wake_lock_ac():
    global WAKE_LOCK_AKTIF
    if WAKE_LOCK_AKTIF or not komut_var_mi("termux-wake-lock"):
        return
    try:
        sonuc = subprocess.run(["termux-wake-lock"], stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=5)
        if sonuc.returncode == 0:
            WAKE_LOCK_AKTIF = True
            print("🔋 Termux wake-lock aktif.")
    except Exception as hata:
        print(f"⚠️ Wake-lock hatasi: {hata}")

def wake_lock_kapat():
    global WAKE_LOCK_AKTIF
    if not WAKE_LOCK_AKTIF:
        return
    try:
        subprocess.run(["termux-wake-unlock"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
    except Exception:
        pass
    WAKE_LOCK_AKTIF = False

# ============================================================
# DASHBOARD
# ============================================================

def dashboard():
    os.system('clear')

    pil = pil_bilgisi_al()
    pil_yüzde = pil.get("percentage", "?") if pil else "?"
    pil_durum = pil.get("status", "?") if pil else "?"

    internet = "🌐 ONLINE" if internet_var_mi() else "📡 OFFLINE"

    if CAR_MODE_AKTIF:
        mod_durum = "🚗 ARABA"
    elif EDA_MODU == "uyku":
        mod_durum = "💤 UYKU"
    else:
        mod_durum = "✅ AKTIF"

    öğrenme_durum = "📚 AKTIF" if ayar_getir("öğrenme_aktif", True) else "📚 PASIF"
    guvenlik_durum = "🛡️ AKTIF" if SECURITY_MODE_AKTIF else "❌ PASIF"
    ai_saglayici = aktif_ai_zinciri()

    dolar = dolar_al()
    altin = altin_al()

    tarih, gun_adi = semsi_tarih()
    saat = datetime.now().strftime("%H:%M")

    cizgi = "═" * 48

    print()
    print(f"╔{cizgi}╗")
    print(f"║{'E · D · A  V4  PRO'.center(48)}║")
    print(f"║{'Groq + Gemma AI Destekli'.center(48)}║")
    print(f"╠{cizgi}╣")
    print(f"║ {'🧠 HAFIZA':<20} {'✅ READY':>23} ║")
    print(f"║ {'🎙️ SES':<20} {'✅ READY':>25} ║")
    print(f"║ {'🔋 BATARYA':<20} {pil_yüzde}% {pil_durum:>20} ║")
    print(f"║ {'🚗 ARABA MODU':<20} {'✅' if CAR_MODE_AKTIF else '❌':>23} ║")
    print(f"║ {'🛡️ GUVENLIK':<20} {guvenlik_durum:>25} ║")
    print(f"║ {'🌐 INTERNET':<20} {internet:>23} ║")
    print(f"║ {'📚 OGRENME':<20} {öğrenme_durum:>23} ║")
    print(f"╠{cizgi}╣")
    print(f"║ 🤖 AI ZINCIRI: {ai_saglayici:<31} ║")
    print(f"╠{cizgi}╣")
    print(f"║ {'💰 DOLAR':<20} {dolar.replace('Dolar: ', ''):>24} ║")
    print(f"║ {'🥇 ALTIN':<13} {altin.replace('Altın: ', ''):>24} ║")
    print(f"╠{cizgi}╣")
    print(f"║ {'⏰ SAAT':<8} {saat:<8} {'📅 TARIH':<9} {tarih:<9} ║")
    print(f"║ {'🎯 MOD':<12} {mod_durum:<8} {'💤 DURUM':<9} {'DINLIYOR':<11} ║")
    print(f"╠{cizgi}╣")
    print(f"║{'🎙️  SOYLE: Eda  ➜  UYANDIR'.center(49)}║")
    print(f"╚{cizgi}╝")
    print()

# ============================================================
# MIKROFON KAYIT
# ============================================================

MIKROFON_ISINMA_SURESI = 0.3
KAYIT_BITIS_PAYI = 1.5
BEEP_DOSYA = os.path.join(KLASOR, "eda_beep.wav")
BEEP_SURESI = 0.12

def eski_kaydi_durdur_tek_deneme():
    if not komut_var_mi("termux-microphone-record"):
        return False
    try:
        sonuc = subprocess.run(["termux-microphone-record", "-q"], stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=3)
        return sonuc.returncode == 0
    except Exception:
        return False

def eski_kaydi_durdur(maks_deneme=2):
    for deneme in range(maks_deneme):
        if eski_kaydi_durdur_tek_deneme():
            return True
        if deneme < maks_deneme - 1:
            time.sleep(0.3)
    return False

def beep_hazırla():
    if os.path.exists(BEEP_DOSYA) and os.path.getsize(BEEP_DOSYA) > 100:
        return True
    if shutil.which("ffmpeg") is None:
        return False
    try:
        sonuc = subprocess.run(["ffmpeg", "-y", "-f", "lavfi",
                                 "-i", f"sine=frequency=880:duration={BEEP_SURESI}",
                                 "-ar", "44100", "-ac", "1", BEEP_DOSYA],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
        return sonuc.returncode == 0 and os.path.exists(BEEP_DOSYA)
    except Exception:
        return False

def sinyal_sesi_ver():
    if not ayar_getir("beep_aktif", True):
        return
    if komut_var_mi("termux-media-player") and beep_hazırla():
        try:
            subprocess.run(["termux-media-player", "play", BEEP_DOSYA],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=3)
            time.sleep(BEEP_SURESI + 0.1)
        except Exception:
            pass

def dosya_hazır_mi(dosya, max_bekleme=2.0, kontrol_araligi=0.2):
    if not os.path.exists(dosya):
        return False
    gecen = 0.0
    onçeki_boyut = -1
    sabit_sayisi = 0
    while gecen < max_bekleme:
        try:
            boyut = os.path.getsize(dosya)
        except Exception:
            boyut = -1
        if boyut > 0 and boyut == onçeki_boyut:
            sabit_sayisi += 1
            if sabit_sayisi >= 2:
                return True
        else:
            sabit_sayisi = 0
        onçeki_boyut = boyut
        time.sleep(kontrol_araligi)
        gecen += kontrol_araligi
    return onçeki_boyut > 0

def m4a_wav_çevir(m4a_dosya, wav_dosya):
    try:
        sonuc = subprocess.run(["ffmpeg", "-y", "-i", m4a_dosya, "-ar", "16000",
                                 "-ac", "1", "-c:a", "pcm_s16le", wav_dosya],
                                stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, timeout=15)
        return sonuc.returncode == 0 and os.path.exists(wav_dosya) and os.path.getsize(wav_dosya) > 1000
    except Exception:
        return False

def dosyalari_temizle():
    for dosya in [SES_KAYDI, SES_WAV]:
        if os.path.exists(dosya):
            try:
                os.remove(dosya)
            except Exception:
                pass

def yeni_kayit(sure_saniye=None):
    global KAYIT_AKTIF

    while True:
        with kayit_lock:
            if not KAYIT_AKTIF:
                break
        print("⏳ Eda konusuyor, bekleyin...")
        time.sleep(0.5)

    sure_saniye = sure_saniye or MAKS_KAYIT
    dosyalari_temizle()
    print()
    print("🎙️ Eda seni dinliyor...")
    print(f"🗣️ Konus denizçim...")
    print(f"⏱️ En fazla {sure_saniye} saniye.")
    print()

    if shutil.which("termux-microphone-record") is None:
        print("❌ termux-microphone-record bulunamadi.")
        return False
    if shutil.which("ffmpeg") is None:
        print("❌ ffmpeg bulunamadi.")
        return False

    with kayit_lock:
        KAYIT_AKTIF = True

    try:
        eski_kaydi_durdur()
        time.sleep(0.2)
        sinyal_sesi_ver()
        subprocess.run(["termux-microphone-record", "-l", str(sure_saniye), "-f", SES_KAYDI],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=sure_saniye + 8)
        time.sleep(MIKROFON_ISINMA_SURESI)
        print("📳 Simdi konusabilirsin denizçim...")
        kalan_bekleme = max(0.3, sure_saniye + KAYIT_BITIS_PAYI)
        time.sleep(kalan_bekleme)
        dosya_hazır_mi(SES_KAYDI, max_bekleme=1.5, kontrol_araligi=0.2)

        if not os.path.exists(SES_KAYDI):
            print("❌ M4A dosyasi oluşturulamadı.")
            return False

        boyut = os.path.getsize(SES_KAYDI)
        print(f"📦 M4A boyutu: {boyut} byte")
        if boyut < 500:
            print("❌ Dosya bos.")
            return False

        print("🔄 WAV formatina çevriliyor...")
        if not m4a_wav_çevir(SES_KAYDI, SES_WAV):
            print("🔄 Tekrar deneniyor...")
            time.sleep(1.0)
            if not m4a_wav_çevir(SES_KAYDI, SES_WAV):
                print("❌ WAV donusumu basarisiz.")
                return False

        try:
            with wave.open(SES_WAV, "rb") as wf:
                sure = wf.getnframes() / float(wf.getframerate())
        except Exception:
            print("❌ WAV okunamadı.")
            return False

        if sure < 0.3:
            print("❌ Kayit cok kisa.")
            return False

        print(f"⏱️ Kayit suresi: {sure:.2f} saniye")
        print(f"🎵 WAV hazır: {os.path.getsize(SES_WAV) // 1024} KB")
        return True

    except subprocess.TimeoutExpired:
        print("❌ Mikrofon zaman asimi.")
        eski_kaydi_durdur()
        time.sleep(0.5)
        return False
    except Exception as hata:
        print(f"❌ Kayit hatasi: {hata}")
        eski_kaydi_durdur()
        return False
    finally:
        with kayit_lock:
            KAYIT_AKTIF = False

# ============================================================
# ANA DONGULER
# ============================================================

def komut_dongusu():
    print()
    print("=" * 50)
    print(" 🧠 EDA V4 PRO - KOMUT SISTEMI")
    if ayar_getir("öğrenme_aktif", True):
        print(" 📚 OGRENME AKTIF - Her yeni kelimeyi öğrenir!")
    print(" 🔔 HATIRLATMA SISTEMI AKTIF")
    _tercih = ayar_getir('ai_provider', 'groq')
    if _tercih not in AI_SAGLAYICILAR:
        _tercih = 'groq'
    _sira = [_tercih] + [s for s in AI_SAGLAYICILAR if s not in (_tercih, 'gemma')] + ['gemma']
    print(f" 🤖 AI ZINCIRI: {' > '.join(AI_SAGLAYICILAR[s][0] for s in _sira)}")
    print(" ⚡ GOOGLE ONCELIKLI - Hizli tanima!")
    print("=" * 50)
    print()
    print(" 📌 ORNEKLER:")
    print("    'uyku' → Uyku modu")
    print("    'kapat' → Kapat")
    print("    'saat 8 hatırlat' → Hatırlatma")
    print("    'pil ve saat' → Kombine komut")
    print("    'bana bir hikaye anlat' → AI cevaplar")
    print("=" * 50)
    print()

    while True:
        try:
            if sarj_ediliyor_mu():
                time.sleep(SARJ_MODU_BEKLEME_EK)

            if not yeni_kayit():
                print("❌ Kayit basarisiz. Tekrar...")
                time.sleep(1)
                continue

            komut = sesden_metne()
            if not komut:
                print("🔇 Ses algilanmadi.")
                continue

            sonuc = komut_isle(komut)

            if sonuc == EDA_KAPAT:
                cevap_ver("Görüşmek üzere denizçim.")
                raise SystemExit(0)

            if sonuc == EDA_KAPAT_ONAY_ISTE:
                cevap_ver("Kapatmak istediğine emin misin denizçim?")
                if yeni_kayit(4):
                    onay_metni = sesden_metne()
                else:
                    onay_metni = None
                if onay_metni and any(k in onay_metni.lower() for k in
                                       ["evet", "eminim", "tamam", "kapat"]):
                    cevap_ver("Görüşmek üzere denizçim.")
                    raise SystemExit(0)
                else:
                    cevap_ver("Tamam, kapatmıyorum denizçim.")
                continue

            if sonuc == EDA_UYKU:
                cevap_ver("Uyku moduna dönüyorum denizçim.")
                return

            if sonuc == GUNLUK_ICERIK_BEKLENIYOR:
                cevap_ver("Günlüğe ne yazmak istersin?")
                if yeni_kayit(GUNLUK_KAYIT_SURESI):
                    icerik = sesden_metne()
                    if icerik:
                        gunluge_ekle(icerik)
                        cevap_ver("Günlüğe yazdım.")
                continue

            cevap_ver(sonuc)

        except KeyboardInterrupt:
            print()
            cevap_ver("Uyku moduna dönüyorum.")
            return
        except Exception as hata:
            print(f"❌ Hata: {hata}")
            time.sleep(1)

def başlat():
    global EDA_MODU, CAR_MODE_AKTIF, SECURITY_MODE_AKTIF

    hatirlatma_baslangic()
    pil_bildirim_başlangic()
    # Wake-lock bir kez alinir ve program calistigi surece acik kalir.
    # Surekli dinleme (continuous listening) tasarimi geregi mikrofonun her
    # dongude calisabilmesi icin CPU'nun uyumamasi gerekiyor; kisa (1-2 sn)
    # araliklarla acip kapamak gercek bir tasarruf saglamiyor (Android'in
    # Doze moduna gecmesi zaten dakikalar suren bir bosluk gerektirir) ve
    # sadece gereksiz subprocess yukü/log kalabaligi yaratiyor. Asil pil
    # tasarrufu uyku_sesden_metne() ve sessiz_mi() ile Google/agir Whisper
    # cagrilarinin sessizlikte tamamen atlanmasindan geliyor.
    wake_lock_ac()

    ayarlar = ayarlari_yükle()
    SECURITY_MODE_AKTIF = bool(ayarlar.get("security", False))
    CAR_MODE_AKTIF = bool(ayarlar.get("car_mode", False))
    EDA_MODU = "araba" if CAR_MODE_AKTIF else "uyku"

    if not ayar_getir("groq_api_key", "") and not ayar_getir("deepseek_api_key", ""):
        print("⚠️ UYARI: groq_api_key ve deepseek_api_key ayarlanmamis!")
        print("   settings.json icinden ekleyebilirsin. Hicbiri yoksa direkt Gemma'ya (offline) gecileçek.")
    elif not ayar_getir("groq_api_key", ""):
        print("ℹ️ groq_api_key ayarlanmamis, DeepSeek denenip Gemma'ya dusuleçek.")
    elif not ayar_getir("deepseek_api_key", ""):
        print("ℹ️ deepseek_api_key ayarlanmamis, sadece Groq -> Gemma zinciri calisacak.")

    dashboard()

    print("💤 Eda uyku modunda...")
    print("🗣️ Bana 'Eda' de.")
    if ayar_getir("öğrenme_aktif", True):
        print("📚 Öğrenme sistemi aktif!")
    print()

    while True:
        try:
            if sarj_ediliyor_mu():
                print(f"🔌 Sarj: {SARJ_MODU_BEKLEME_EK} sn bekleniyor.")
                time.sleep(SARJ_MODU_BEKLEME_EK)

            # Wake-lock zaten başlat() basinda bir kez alindi ve program
            # boyunca acik kaliyor (surekli dinleme icin gerekli) - burada
            # tekrar acip kapamiyoruz, gereksiz subprocess yukü olmasin.
            kayit_basarili = yeni_kayit(WAKE_KAYIT_SURESI)

            if not kayit_basarili:
                print("❌ Kayit basarisiz.")
                time.sleep(1)
                continue

            # Uyku modunda hafif/offline tanima kullanilir (Google/agir Whisper
            # YOK) - sadece uyanma kelimesini yakalamak icin yeterli, pil dostu.
            komut = uyku_sesden_metne()
            if not komut:
                print("💤 Ses algilanmadi.")
                time.sleep(UYKU_BEKLEME_ARALIGI)
                continue

            if eda_uyandi_mi(komut):
                EDA_MODU = "araba" if CAR_MODE_AKTIF else "aktif"
                cevap_ver("Buradayım denizçim! Dinliyorum.")
                print()
                print("=" * 50)
                print(" ✅ EDA UYANDI")
                print("=" * 50)
                print()

                komut_dongusu()

                EDA_MODU = "araba" if CAR_MODE_AKTIF else "uyku"
                print()
                print("💤 Eda tekrar uyku modunda...")
                print("🗣️ Bana 'Eda' de.")
                print()
            else:
                time.sleep(UYKU_BEKLEME_ARALIGI)

        except KeyboardInterrupt:
            print()
            break
        except Exception as hata:
            print(f"❌ Hata: {hata}")
            time.sleep(1)

# ============================================================
# TERMUX KONTROL
# ============================================================

def termux_kontrol():
    gerekli = ["termux-microphone-record", "termux-tts-speak", "termux-torch", "ffmpeg", "ffprobe", "am"]
    eksik = [k for k in gerekli if not komut_var_mi(k)]
    if not komut_var_mi("termux-battery-status"):
        print("⚠️ termux-battery-status bulunamadi.")
    if not komut_var_mi("termux-camera-photo"):
        print("⚠️ termux-camera-photo bulunamadi.")
    if not komut_var_mi("termux-sms-send"):
        print("⚠️ termux-sms-send bulunamadi.")
    if eksik:
        print()
        print("⚠️ Eksik komutlar:")
        for komut in eksik:
            print(" -", komut)
        print()
        return False
    return True

# ============================================================
# BASLAT
# ============================================================

if __name__ == "__main__":
    try:
        print("🚀 Eda V4 PRO başlatılıyor...")
        print("✅ AI: Groq (online, hizli) + Gemma (offline, garanti)")
        print("✅ EMOJI TEMIZLENDI")
        print("✅ GOOGLE ONCELIKLI STT")
        print("✅ KARA LISTE AKTIF")
        print()
        termux_kontrol()
        başlat()
    except KeyboardInterrupt:
        print()
        print("👋 Eda kapatıldı.")
    finally:
        try:
            dosyalari_temizle()
            wake_lock_kapat()
        except Exception:
            pass
        print()
        print("=" * 50)
        print(" EDA V4 PRO KAPANDI")
        print("=" * 50)
