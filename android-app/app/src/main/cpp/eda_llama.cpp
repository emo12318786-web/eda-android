#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include "llama.h"

#define EDA_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "EdaNativeGemma", __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_context = nullptr;
static llama_sampler *g_sampler = nullptr;
static const llama_vocab *g_vocab = nullptr;

static void unload_model() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deniz_eda_ai_NativeGemmaEngine_nativeLoadModel(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;
    unload_model();
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU inference: works without Termux or network.
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) {
        EDA_LOGE("Could not load model file. Ensure it is a supported GGUF model.");
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 256;
    cp.n_ubatch = 128;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;
    g_context = llama_init_from_model(g_model, cp);
    if (!g_context) {
        EDA_LOGE("Could not create llama context");
        unload_model();
        return JNI_FALSE;
    }
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_deniz_eda_ai_NativeGemmaEngine_nativeGenerate(JNIEnv *env, jobject, jstring jprompt, jint maxTokens) {
    if (!g_model || !g_context || !g_vocab || !g_sampler) return nullptr;
    const char *raw = env->GetStringUTFChars(jprompt, nullptr);
    if (!raw) return nullptr;
    std::string prompt = std::string("You are Eda, a helpful conversational assistant. Reply naturally and concisely in the user's language.\nUser: ") + raw + "\nAssistant:";
    env->ReleaseStringUTFChars(jprompt, raw);

    int32_t needed = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (needed >= 0 || needed < -16384) return nullptr;
    std::vector<llama_token> tokens((size_t)-needed);
    int32_t tokenCount = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(), (int32_t)tokens.size(), true, true);
    if (tokenCount <= 0) return nullptr;
    tokens.resize((size_t)tokenCount);
    llama_memory_clear(llama_get_memory(g_context), true);
    llama_sampler_reset(g_sampler);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(g_context, batch) != 0) return nullptr;

    std::string answer;
    const int limit = std::max(1, std::min((int)maxTokens, 256));
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
        llama_sampler_accept(g_sampler, token);
        if (llama_vocab_is_eog(g_vocab, token)) break;
        char piece[256];
        int32_t n = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);
        if (n > 0) answer.append(piece, (size_t)n);
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_context, batch) != 0) break;
    }
    if (answer.empty()) return nullptr;
    return env->NewStringUTF(answer.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_deniz_eda_ai_NativeGemmaEngine_nativeUnloadModel(JNIEnv *, jobject) {
    unload_model();
}
