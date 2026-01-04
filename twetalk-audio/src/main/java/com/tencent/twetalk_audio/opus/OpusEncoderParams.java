package com.tencent.twetalk_audio.opus;

public class OpusEncoderParams {
    private final int sampleRate;
    private final int channels;
    private final int targetBytes;
    private final int bitrate;
    private final boolean cbr;
    private final boolean dtx;
    private final int complexity;
    private final boolean signalVoice;

    private OpusEncoderParams(Builder builder) {
        this.sampleRate = builder.sampleRate;
        this.channels = builder.channels;
        this.targetBytes = builder.targetBytes;
        this.bitrate = builder.bitrate;
        this.cbr = builder.cbr;
        this.dtx = builder.dtx;
        this.complexity = builder.complexity;
        this.signalVoice = builder.signalVoice;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getTargetBytes() {
        return targetBytes;
    }

    public int getBitrate() {
        return bitrate;
    }

    public boolean isCbr() {
        return cbr;
    }

    public boolean isDtx() {
        return dtx;
    }

    public int getComplexity() {
        return complexity;
    }

    public boolean isSignalVoice() {
        return signalVoice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int sampleRate = 16000;
        private int channels = 1;
        private int targetBytes = 180;
        private int bitrate = 24000;
        private boolean cbr = true;
        private boolean dtx = false;
        private int complexity = 5;
        private boolean signalVoice = true;

        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        public Builder targetBytes(int targetBytes) {
            this.targetBytes = targetBytes;
            return this;
        }

        public Builder bitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        public Builder cbr(boolean cbr) {
            this.cbr = cbr;
            return this;
        }

        public Builder dtx(boolean dtx) {
            this.dtx = dtx;
            return this;
        }

        public Builder complexity(int complexity) {
            this.complexity = complexity;
            return this;
        }

        public Builder signalVoice(boolean signalVoice) {
            this.signalVoice = signalVoice;
            return this;
        }

        public OpusEncoderParams build() {
            if (sampleRate != 8000 && sampleRate != 12000 && sampleRate != 16000
                    && sampleRate != 24000 && sampleRate != 48000) {
                throw new IllegalArgumentException("Unexpected sample rate: " + sampleRate);
            }

            if (channels != 1 && channels != 2) {
                throw new IllegalArgumentException("Unexpected channels: " + channels);
            }

            return new OpusEncoderParams(this);
        }
    }
}
