package milka.audio;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Iterator;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import java.nio.file.Files;
// MP3 encoder/decoder
import de.sciss.jump3r.Main;
import java.util.NoSuchElementException;

public class AudioContainer implements Cloneable, Iterable {

    private byte[] rawSamples;
    private AudioFormat audioFormat;
    private long frameLength;
    private int frameSize;
    private int channels;
    private float frameRate;
    private float sampleRate;
    private int sampleSizeInBits;
    private boolean bigEndian;
    private Encoding encoding;
    private boolean signed;

    // What's wrong with iterators???
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException, UnsupportedAudioFormatException {
        AudioContainer audioContainer = new AudioContainer(new File("a.mp3"));
        AudioEffects.apply8d(audioContainer);
        audioContainer.writeMp3(new File("8D.mp3"));
    }
    private AudioContainer(byte[] rawSamples, AudioFormat audioFormat) throws IOException {
        initAudioFormat(audioFormat);
        this.rawSamples = Arrays.copyOf(rawSamples, rawSamples.length);
        this.frameLength = rawSamples.length / this.frameSize;
    }

    public AudioContainer(File audioFile) throws UnsupportedAudioFileException, IOException {
        File convertedAudioFile = null;
        try {
            Encoding enc = AudioSystem.getAudioFileFormat(audioFile).getFormat().getEncoding();
            AudioFormat af = null;
            if (enc.equals(new Encoding("MPEG1L3"))) {
                convertedAudioFile = Files.createTempFile("decoded", "wav").toFile();
                // Convert mp3 to wav
                Main.main(("--decode -h " + audioFile.toString() + " " + convertedAudioFile.toString()).split(" "));
                audioFile = convertedAudioFile;
                af = AudioSystem.getAudioFileFormat(audioFile).getFormat();
            } else if (!(enc.equals(Encoding.PCM_SIGNED) || enc.equals(Encoding.PCM_UNSIGNED))) {
                throw new UnsupportedAudioFileException("wrong encoding: " + enc.toString());
            }
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(audioFile)))) {
                initAudioFormat(af);
                this.frameLength = audioInputStream.getFrameLength();
                this.rawSamples = new byte[(int) this.frameLength * this.frameSize];
                audioInputStream.read(this.rawSamples);
            }
        } finally {
            if (convertedAudioFile != null) {
                Files.deleteIfExists(convertedAudioFile.toPath());
            }
        }
    }

    private void initAudioFormat(AudioFormat af) {
        this.audioFormat = af;
        this.encoding = af.getEncoding();
        this.channels = af.getChannels();
        this.frameRate = af.getFrameRate();
        this.sampleRate = af.getSampleRate();
        this.signed = this.encoding.equals(Encoding.PCM_SIGNED);
        this.sampleSizeInBits = af.getSampleSizeInBits();
        this.frameSize = af.getFrameSize();
        this.bigEndian = af.isBigEndian();
    }

    public AudioContainer toAudioFormat(AudioFormat newAudioFormat) throws IOException, UnsupportedAudioFormatException {
        AudioContainer newAudioContainer = new AudioContainer(new byte[(int) this.getFrameLength() * newAudioFormat.getFrameSize()], newAudioFormat);
        for (int i = 0; i < this.getFrameLength(); i++) {
            Frame frame = this.getFrame(i);
            Frame newFrame = new Frame(new double[newAudioContainer.getChannels()]);
            for (int j = 0; j < newFrame.getChannels(); j++) {
                if (j < frame.getChannels()) {
                    newFrame.setSample(j, frame.getSample(j));
                } else {
                    newFrame.setSample(j, frame.getSample(0));
                }
            }
            newAudioContainer.setFrame(i, newFrame);
        }
        return newAudioContainer;
    }

    public Frame getFrame(int index) {
        try {
            return Frame.valueOf(Arrays.copyOfRange(rawSamples, index * this.frameSize, (index + 1) * this.frameSize),
                    this.channels, this.encoding, this.bigEndian);
        } catch (UnsupportedAudioFormatException ex) {
            // this shouldn't happen if algorithm of calculating audio parameters is right
            throw new InternalError(ex);
        }
    }

    public void setFrame(int index, Frame frame) throws UnsupportedAudioFormatException {
        int sampleSize = this.frameSize / this.channels;
        byte[] rawFrame = frame.toByteArray(sampleSize, this.encoding, this.bigEndian);
        System.arraycopy(rawFrame, 0, this.rawSamples, index * this.frameSize, this.frameSize);
    }

    public void write(File file) throws IOException {
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(this.rawSamples), audioFormat,
                this.frameLength), AudioFileFormat.Type.WAVE, file);
    }

    public void writeMp3(File file) throws IOException {
        File tempWavFile = null;
        try {
            tempWavFile = Files.createTempFile("temp", "wav").toFile();
            write(tempWavFile);
            // Encode mp3 from temporary wav file
            Main.main(("-h " + tempWavFile.toString() + " " + file.toString()).split(" "));
        } finally {
            if (tempWavFile != null) {
                Files.delete(tempWavFile.toPath());
            }
        }
    }

    @Override
    public Iterator<Frame> iterator() {
        return new AudioContainerIterator();
    }

    private class AudioContainerIterator implements Iterator<Frame> {

        int lastReturned;

        public AudioContainerIterator() {
            lastReturned = -1;
        }

        @Override
        public boolean hasNext() {
            return (lastReturned + 1) < AudioContainer.this.getFrameLength();
        }

        @Override
        public Frame next() {
            if (this.hasNext()) {
                return AudioContainer.this.getFrame(++lastReturned);
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    @Override
    @SuppressWarnings("CloneDeclaresCloneNotSupported")
    public AudioContainer clone() {
        try {
            AudioContainer clone = (AudioContainer) super.clone();
            clone.rawSamples = this.rawSamples.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    public String getAudioFormatInfo() {
        return this.audioFormat.toString();
    }

    public long getFrameLength() {
        return frameLength;
    }

    public int getChannels() {
        return channels;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public boolean isBigEndian() {
        return bigEndian;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public int getSampleSizeInBits() {
        return sampleSizeInBits;
    }

    public boolean isSigned() {
        return signed;
    }

    private byte[] getRawSamples() {
        return rawSamples.clone();
    }

    private AudioFormat getAudioFormat() {
        return audioFormat;
    }

}
