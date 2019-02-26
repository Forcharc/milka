package milka.audio;

import java.util.Arrays;
import javax.sound.sampled.AudioFormat.Encoding;

public class Frame implements Cloneable {

    double[] samples;
    
    @Override
    public Frame clone() {
        try {
            Frame clone = (Frame) super.clone();
            clone.samples = this.samples.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    public Frame(double... samples) {
        this.samples = samples.clone();
    }

    static Frame valueOf(byte[] rawFrame, int channels, Encoding encoding, boolean isBigEndian) throws UnsupportedAudioFormatException {
        int bytesPerSample = rawFrame.length / channels;
        long frameMaxValue = (1L << (bytesPerSample * Byte.SIZE - 1));
        if (bytesPerSample > Long.BYTES) {
            throw new UnsupportedAudioFormatException("Unsuported number of bytes per sample");
        }
        
        double[] samples = new double[channels];
        for (int i = 0; i < channels; i++) {
            // Take part of frame responsible for one sample
            byte[] rawSample = Arrays.copyOfRange(rawFrame, i * bytesPerSample, (i + 1) * bytesPerSample);
            if (!isBigEndian) {
                // Reverse bytes order
                byte temp;
                for (int j = 0; j < (bytesPerSample / 2); j++) {
                    temp = rawSample[j];
                    rawSample[j] = rawSample[bytesPerSample - j - 1];
                    rawSample[bytesPerSample - j - 1] = temp;
                }
            }
            
            // Make long out of bytes
            long sampleLong = 0L;
            for (int j = 0; j < bytesPerSample; j++) {
                sampleLong = (sampleLong << Byte.SIZE) | (rawSample[j] & 0xFF);
            }
            
            // Make right-left shift to support minus cases
            if (encoding.equals(Encoding.PCM_SIGNED)) {
                int shift = (Long.BYTES - bytesPerSample) * Byte.SIZE;
                sampleLong = sampleLong << shift >> shift;
            } else  if (encoding.equals(Encoding.PCM_UNSIGNED)){
                // If not signed, transform to signed
                sampleLong -= frameMaxValue;
            } else throw new UnsupportedAudioFormatException("Unsupported encoding");
            
            // Transform to floating point representation with range -1 to 1
            samples[i] = (double) sampleLong / (double) frameMaxValue;
        }
        return new Frame(samples);
    }

    byte[] toByteArray(int bytesPerSample, Encoding encoding, boolean isBigEndian) throws UnsupportedAudioFormatException {
        int bytesPerFrame = bytesPerSample * this.getChannels();
        long frameMaxValue = (1L << (bytesPerSample * Byte.SIZE - 1));
        if (bytesPerSample > Long.BYTES) {
            throw new UnsupportedAudioFormatException("Unsuported number of bytes per sample");
        }
        
        byte[] rawFrame = new byte[bytesPerFrame]; 
        for (int i = 0; i < this.getChannels(); i++) {
            // Transform double sample to signed long representantion 
            long sampleLong = (long) (this.getSample(i) * (double) frameMaxValue);
            if (encoding.equals(Encoding.PCM_UNSIGNED)) {
                // Make unsigned long
                sampleLong += frameMaxValue;
            } else if (!encoding.equals(Encoding.PCM_SIGNED)) {
                throw new UnsupportedAudioFormatException("Unsupported encoding");
            }
            
            // Make bytes out of long
            byte[] rawSample = new byte[bytesPerSample];
            for (int j = 0; j < bytesPerSample; j++) {
                rawSample[bytesPerSample - j - 1] = (byte)(sampleLong & 0xFF);
                sampleLong = sampleLong >>> Byte.SIZE;
            }
            
            if (!isBigEndian) {
                // Reverse bytes order
                byte temp;
                for (int j = 0; j < bytesPerSample / 2; j++) {
                    temp = rawSample[j];
                    rawSample[j] = rawSample[bytesPerSample - j - 1];
                    rawSample[bytesPerSample - j - 1] = temp;
                }
            }
            System.arraycopy(rawSample, 0, rawFrame, i * bytesPerSample, bytesPerSample);
        }
        return rawFrame;
    }
    
    

    double getSample(int channel) {
        return this.samples[channel];
    }
    
    void setSample(int channel, double sample) {
        this.samples[channel] = sample;
    } 

    int getChannels() {
        return samples.length;
    }
    
    void sumWith(Frame frame) {
        int minChannels = Math.min(this.getChannels(), frame.getChannels());
        for (int i = 0; i < minChannels; i++) {
            this.setSample(i, this.getSample(i) + frame.getSample(i));
        }
    }
    
    void multiplyEachChannelBy(double coef) {
        for(int i = 0; i < this.samples.length; i++) { 
            samples[i] *= coef;
        }
    }
    
    double getMax() {
        double max = 0.0;
        for (int i = 0; i < this.samples.length; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }
    
}
