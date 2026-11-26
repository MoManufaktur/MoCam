// JNI-Brücke zwischen MoCam (Kotlin) und whisper.cpp
#include <jni.h>
#include <android/log.h>
#include <string>
#include <atomic>
#include "whisper.h"

#define TAG "MoCamWhisper"

namespace {
std::atomic<bool> g_abort{false};

bool shouldAbort(void *) { return g_abort.load(); }

struct ProgressCtx {
    JNIEnv *env;
    jobject cb;
    jmethodID method;
};

void onProgress(struct whisper_context *, struct whisper_state *, int progress, void *user) {
    auto *p = static_cast<ProgressCtx *>(user);
    if (p && p->cb && p->method) p->env->CallVoidMethod(p->cb, p->method, (jint) progress);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_momanufaktur_mocam_Whisper_nativeInit(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    if (!ctx) __android_log_print(ANDROID_LOG_ERROR, TAG, "Modell konnte nicht geladen werden");
    return reinterpret_cast<jlong>(ctx);
}

/** Laufende Erkennung abbrechen (Nutzer hat „Abbrechen“ gedrückt). */
extern "C" JNIEXPORT void JNICALL
Java_com_momanufaktur_mocam_Whisper_nativeAbort(JNIEnv *, jobject) {
    g_abort.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_momanufaktur_mocam_Whisper_nativeFree(JNIEnv *, jobject, jlong ptr) {
    if (ptr) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

/**
 * Erkennt Sprache in 16-kHz-Mono-Audio. Rückgabe (UTF-8-Bytes): eine Zeile je Untertitel-Block
 * "t0<TAB>t1<TAB>Text" (Zeiten in Hundertstelsekunden).
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_momanufaktur_mocam_Whisper_nativeTranscribe(JNIEnv *env, jobject, jlong ptr, jfloatArray pcm,
                                                    jstring lang, jstring prompt, jint threads,
                                                    jint beam, jobject progress) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return env->NewByteArray(0);

    whisper_full_params wp = whisper_full_default_params(
        beam > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    if (beam > 1) wp.beam_search.beam_size = beam;

    const char *l = env->GetStringUTFChars(lang, nullptr);
    const char *pr = prompt ? env->GetStringUTFChars(prompt, nullptr) : nullptr;

    wp.language = l;
    wp.translate = false;
    wp.n_threads = threads;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_special = false;
    wp.print_timestamps = false;
    wp.suppress_nst = true;
    // Kurze, gut lesbare Blöcke mit genauen Zeiten
    wp.token_timestamps = true;
    wp.max_len = 42;
    wp.split_on_word = true;
    if (pr && pr[0]) wp.initial_prompt = pr;
    g_abort.store(false);
    wp.abort_callback = shouldAbort;
    wp.abort_callback_user_data = nullptr;

    ProgressCtx pc{env, progress, nullptr};
    if (progress) {
        jclass cls = env->GetObjectClass(progress);
        pc.method = env->GetMethodID(cls, "onProgress", "(I)V");
        wp.progress_callback = onProgress;
        wp.progress_callback_user_data = &pc;
    }

    jsize n = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    int rc = whisper_full(ctx, wp, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);

    std::string out;
    if (rc == 0) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; i++) {
            out += std::to_string(whisper_full_get_segment_t0(ctx, i));
            out += '\t';
            out += std::to_string(whisper_full_get_segment_t1(ctx, i));
            out += '\t';
            out += whisper_full_get_segment_text(ctx, i);
            out += '\n';
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full fehlgeschlagen: %d", rc);
    }

    env->ReleaseStringUTFChars(lang, l);
    if (pr) env->ReleaseStringUTFChars(prompt, pr);
    // Als Bytes zurückgeben (UTF-8), damit ungewöhnliche Zeichen JNI nicht zum Absturz bringen
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(arr, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return arr;
}
