package cps.wsan.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class AmplitudeLoud {
    private static final int CHANNELS = 1; // mono
    private static final long SAMPLERATE = 16000; // 16000Hz PCM 16bit signed
    private static final int THRESHOLD_LOUD = 6000; // arbitrary threshhold for loud sound, this is chosen as a spike larger than 1/6th
    private static final int BITSPERSAMPLE = 16; // 16 BIT
    public int[] wave;

    private static String readString(ByteArrayInputStream in, int length) {
        byte[] buffer = new byte[length]; // 1 char per byte
        try {
            if (in.read(buffer) != length) { // length of string isn't available in bytestream
                throw new ArrayIndexOutOfBoundsException("Out of bounds!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String(buffer);
    }

    private static int readInt(ByteArrayInputStream in) {
        byte[] buffer = new byte[2]; // yes an integer is two bytes and we do not talk about it
        try {
            if (in.read(buffer) != buffer.length) { // not more than 2 bytes available
                throw new ArrayIndexOutOfBoundsException("Out of bounds!");
            }
            return (buffer[0] & 0xFF) | (((int) buffer[1]) << 8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int isLoud(int[] wave) {
        // Preset threshold based
        // We also tried splitting the sound wave with an FFT in order to do this based on the
        // frequency of the clap. However this was actually really unreliable with our testing,
        // so we decided to opt for an amplitude based check. At the testbed demo we did not think
        // about asking help for this, as we were busy with other tasks...
        int max = 0;
        for( int i : wave){
            if( i > THRESHOLD_LOUD){
                if(i > max){
                    max = i;
                }
            }
        }
        return max;
    }

    public void init(byte[] pcm, boolean header) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(pcm)) {
            if (header) { // for testing on desktop from a wav file
                in.skip(36); // skip first 36 bytes of the wave header
                String dataArea = readString(in, 4); // small check if this is actually a wave file
                if (!dataArea.equals("data")) {
                    throw new IOException("This isn't a wave file :(");
                }
                // read data length
                long dataLength = 0;
                try {
                    long[] buffer = new long[4];
                    for (int i = 0; i < buffer.length; i++) {
                        buffer[i] = in.read();
                        if (buffer[i] == -1) {
                            throw new IOException("There is no data area");
                        }
                    }
                    dataLength =
                            buffer[0] | (buffer[1] << 8) | (buffer[2] << 16) | (buffer[3] << 24);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // length of byte array would be
                int byteLength = (int) (dataLength / (BITSPERSAMPLE / 8) / CHANNELS);

                wave = new int[byteLength]; // assumed for now there is a single channel, otherwise
                // make this multi-dimensional.
                // read into wave.
                for (int i = 0; i < byteLength; i++) {
                    wave[i] = readInt(in);
                }
            } else { // normally
                int byteLength = (pcm.length / 4);

                wave = new int[byteLength]; // assumed for now there is a single channel, otherwise
                // make this multi-dimensional.
                for (int i = 0; i < byteLength; i++) {
                    wave[i] = readInt(in);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}