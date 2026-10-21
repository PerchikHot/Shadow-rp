#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

static std::mutex inference_mutex;
static llama_model *cached_model = nullptr;
static std::string cached_path;
static bool backend_ready = false;

extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowrp_app_LocalLlm_generate(JNIEnv *env, jobject, jstring path, jstring prompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(inference_mutex);
    const char *model_path = env->GetStringUTFChars(path, nullptr);
    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::string input(prompt_chars);
    if (!backend_ready) { llama_backend_init(); backend_ready = true; }
    if (!cached_model || cached_path != model_path) {
        if (cached_model) { llama_model_free(cached_model); cached_model = nullptr; }
        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        cached_model = llama_model_load_from_file(model_path, mp);
        cached_path = cached_model ? model_path : "";
    }
    llama_model *model = cached_model;
    env->ReleaseStringUTFChars(path, model_path);
    env->ReleaseStringUTFChars(prompt, prompt_chars);
    if (!model) {
        return env->NewStringUTF("ERROR: Model could not be loaded");
    }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 1536;
    cp.n_batch = 512;
    cp.n_threads = 4;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        return env->NewStringUTF("ERROR: Context allocation failed");
    }
    const llama_vocab *vocab = llama_model_get_vocab(model);
    int count = -llama_tokenize(vocab, input.c_str(), (int)input.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(count);
    int actual = llama_tokenize(vocab, input.c_str(), (int)input.size(), tokens.data(), count, true, true);
    if (actual <= 0 || actual >= 1300) {
        llama_free(ctx);
        return env->NewStringUTF("ERROR: Prompt too long");
    }
    tokens.resize(actual);
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.75f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));
    std::string output;
    int offset = 0;
    while (offset < actual) {
        int batchSize = std::min(512, actual - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, batchSize);
        if (llama_decode(ctx, batch) != 0) break;
        offset += batchSize;
    }
    for (int i=0; i < maxTokens && actual+i < 1500; ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[512];
        int size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (size > 0) output.append(piece, size);
        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    llama_free(ctx);
    return env->NewStringUTF(output.c_str());
}
