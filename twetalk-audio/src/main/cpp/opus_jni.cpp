#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstdint>
#include <opus.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "OpusJNI", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "OpusJNI", __VA_ARGS__)

struct EncoderContext {
    OpusEncoder* encoder;
    int sample_rate;
    int channels;
    int target_bytes;
    int frame_ms;
    int frame_samples;
    std::vector<unsigned char> enc_buf;
};

struct DecoderContext {
    OpusDecoder* decoder;
    int sample_rate;
    int channels;
    int frame_ms;
    int frame_samples;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeCreateEncoder(JNIEnv *env, jobject thiz,
                                                                    jint sample_rate, jint channels,
                                                                    jint target_bytes, jint bitrate,
                                                                    jboolean cbr, jboolean dtx,
                                                                    jint complexity, jboolean signal_voice) {
    int err = 0;
    int frame_ms = 60;
    int frame_samples = (sample_rate * frame_ms) / 1000;

    OpusEncoder* encoder = opus_encoder_create(sample_rate, channels, OPUS_APPLICATION_VOIP, &err);

    if (err != OPUS_OK || !encoder) {
        LOGE("opus_encoder_create failed: %d", err);
        return 0;
    }

    opus_encoder_ctl(encoder, OPUS_SET_SIGNAL(signal_voice ? OPUS_SIGNAL_VOICE : OPUS_SIGNAL_MUSIC));
    opus_encoder_ctl(encoder, OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(encoder, OPUS_SET_VBR(cbr ? 0 : 1));
    opus_encoder_ctl(encoder, OPUS_SET_VBR_CONSTRAINT(1));
    opus_encoder_ctl(encoder, OPUS_SET_DTX(dtx ? 1 : 0));
    opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(complexity));

    auto* ctx = new EncoderContext();
    ctx->encoder = encoder;
    ctx->sample_rate = sample_rate;
    ctx->channels = channels;
    ctx->target_bytes = target_bytes;
    ctx->frame_ms = frame_ms;
    ctx->frame_samples = frame_samples;
    ctx->enc_buf.assign(4096, 0);

    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeCreateDecoder(JNIEnv *env, jobject thiz,
                                                                    jint sample_rate, jint channels) {
    int err = 0;
    int frame_ms = 60;
    int frame_samples = (sample_rate * frame_ms) / 1000;

    OpusDecoder* decoder = opus_decoder_create(sample_rate, channels, &err);

    if (err != OPUS_OK || !decoder) {
        LOGE("opus_decoder_create failed: %d", err);
        return 0;
    }

    auto* ctx = new DecoderContext();
    ctx->decoder = decoder;
    ctx->sample_rate = sample_rate;
    ctx->channels = channels;
    ctx->frame_ms = frame_ms;
    ctx->frame_samples = frame_samples;

    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeEncode(JNIEnv *env, jobject thiz,
                                                              jlong handle, jshortArray pcm_frame) {
    if (handle == 0) {
        LOGE("Invalid encoder handle");
        return nullptr;
    }

    auto* ctx = reinterpret_cast<EncoderContext*>(handle);

    jsize n = env->GetArrayLength(pcm_frame);

    if (n < ctx->frame_samples * ctx->channels) {
        LOGE("encode: input pcm size %d < expected %d", (int) n, ctx->frame_samples * ctx->channels);
        return nullptr;
    }

    jshort* pcm = env->GetShortArrayElements(pcm_frame, nullptr);
    int nbBytes = opus_encode(ctx->encoder,
                              (const opus_int16*) pcm,
                              ctx->frame_samples,
                              ctx->enc_buf.data(),
                              (opus_int32) ctx->enc_buf.size());
    env->ReleaseShortArrayElements(pcm_frame, pcm, JNI_ABORT);

    if (nbBytes < 0) {
        LOGE("opus encode error: %d", nbBytes);
        return nullptr;
    }

    if (nbBytes < ctx->target_bytes) {
        int ret = opus_packet_pad(ctx->enc_buf.data(), nbBytes, ctx->target_bytes);

        if (ret != OPUS_OK) {
            LOGE("opus packet pad failed: %d", ret);
            return nullptr;
        }

        nbBytes = ctx->target_bytes;
    }

    if (nbBytes > ctx->target_bytes) {
        LOGE("Encoded %d > target %d. Lower bitrate/complexity.", nbBytes, ctx->target_bytes);
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(nbBytes);
    env->SetByteArrayRegion(out, 0, nbBytes, (const jbyte*) ctx->enc_buf.data());
    return out;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeDecode(JNIEnv *env, jobject thiz,
                                                              jlong handle, jbyteArray packet,
                                                              jshortArray pcm_out, jboolean fec) {
    if (handle == 0) {
        LOGE("Invalid decoder handle");
        return -1;
    }

    auto* ctx = reinterpret_cast<DecoderContext*>(handle);

    jsize pktLen = env->GetArrayLength(packet);
    jsize outLen = env->GetArrayLength(pcm_out);
    std::vector<unsigned char> pkt(pktLen);
    env->GetByteArrayRegion(packet, 0, pktLen, (jbyte*) pkt.data());
    jshort* pcmOut = env->GetShortArrayElements(pcm_out, nullptr);
    int maxFrameSize = ctx->frame_samples;

    if (outLen < maxFrameSize * ctx->channels) {
        LOGE("decode: pcmOut too small: %d < %d", (int) outLen, maxFrameSize * ctx->channels);
        env->ReleaseShortArrayElements(pcm_out, pcmOut, 0);
        return -2;
    }

    int samplesPerChannel = opus_decode(ctx->decoder,
                                        pkt.data(),
                                        (opus_int32) pktLen,
                                        (opus_int16*) pcmOut,
                                        maxFrameSize,
                                        fec ? 1 : 0);

    if (samplesPerChannel < 0) {
        LOGE("opus decode error: %d", samplesPerChannel);
    }

    env->ReleaseShortArrayElements(pcm_out, pcmOut, 0);
    return samplesPerChannel;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeReleaseEncoder(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) {
        return;
    }

    auto* ctx = reinterpret_cast<EncoderContext*>(handle);
    if (ctx->encoder) {
        opus_encoder_destroy(ctx->encoder);
    }
    delete ctx;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeReleaseDecoder(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) {
        return;
    }

    auto* ctx = reinterpret_cast<DecoderContext*>(handle);
    if (ctx->decoder) {
        opus_decoder_destroy(ctx->decoder);
    }
    delete ctx;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_tencent_twetalk_1audio_opus_OpusBridge_nativeGetFrameSamples(JNIEnv *env, jobject thiz,
                                                                      jlong handle, jboolean is_encoder) {
    if (handle == 0) {
        return -1;
    }

    if (is_encoder) {
        auto* ctx = reinterpret_cast<EncoderContext*>(handle);
        return ctx->frame_samples;
    } else {
        auto* ctx = reinterpret_cast<DecoderContext*>(handle);
        return ctx->frame_samples;
    }
}
