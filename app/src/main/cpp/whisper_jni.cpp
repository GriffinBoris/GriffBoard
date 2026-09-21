#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <memory>
#include <string>
#include <vector>

struct InferenceJob {
    std::atomic<bool> cancelled{false};
};

static void throw_error(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_griffinboris_griffboard_voice_NativeWhisper_create(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new InferenceJob());
}

extern "C" JNIEXPORT void JNICALL
Java_com_griffinboris_griffboard_voice_NativeWhisper_cancel(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<InferenceJob *>(handle)->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_griffinboris_griffboard_voice_NativeWhisper_release(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<InferenceJob *>(handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_griffinboris_griffboard_voice_NativeWhisper_transcribe(
        JNIEnv *env, jobject, jlong handle, jstring path, jfloatArray audio,
        jstring language, jint threads) {
    auto *job = reinterpret_cast<InferenceJob *>(handle);
    if (job->cancelled.load()) return env->NewByteArray(0);
    const char *model_path = env->GetStringUTFChars(path, nullptr);
    auto context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    std::unique_ptr<whisper_context, decltype(&whisper_free)> context(
            whisper_init_from_file_with_params(model_path, context_params), whisper_free);
    env->ReleaseStringUTFChars(path, model_path);
    if (!context) {
        throw_error(env, "Could not load the model. Try a smaller model or download it again.");
        return nullptr;
    }
    if (job->cancelled.load()) return env->NewByteArray(0);
    const jsize count = env->GetArrayLength(audio);
    std::vector<float> samples(count);
    env->GetFloatArrayRegion(audio, 0, count, samples.data());
    const char *language_chars = env->GetStringUTFChars(language, nullptr);
    const std::string language_code(language_chars);
    env->ReleaseStringUTFChars(language, language_chars);
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = language_code.c_str();
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.abort_callback = [](void *data) {
        return static_cast<InferenceJob *>(data)->cancelled.load();
    };
    params.abort_callback_user_data = job;
    const int result = whisper_full(context.get(), params, samples.data(), count);
    if (job->cancelled.load()) return env->NewByteArray(0);
    if (result != 0) {
        throw_error(env, "Transcription failed. Try a shorter recording or smaller model.");
        return nullptr;
    }
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(context.get()); ++i) {
        text += whisper_full_get_segment_text(context.get(), i);
    }
    // Return UTF-8 bytes: NewStringUTF expects modified UTF-8 and corrupts some languages.
    auto output = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(output, 0, static_cast<jsize>(text.size()),
                           reinterpret_cast<const jbyte *>(text.data()));
    return output;
}
