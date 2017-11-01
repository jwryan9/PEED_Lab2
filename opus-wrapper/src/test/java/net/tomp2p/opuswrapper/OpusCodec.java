package net.tomp2p.opuswrapper;

import com.sun.jna.ptr.PointerByReference;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public class OpusCodec {

    private static final int FRAME_SIZE = 480;

    public static ShortBuffer decode(PointerByReference opusDecoder, ByteBuffer transferedBytes) {
        ShortBuffer shortBuffer = ShortBuffer.allocate(FRAME_SIZE);
        byte[] b = new byte[transferedBytes.remaining()];
        transferedBytes.get(b);
        int decoded = Opus.INSTANCE.opus_decode(opusDecoder, b, b.length, shortBuffer, FRAME_SIZE, 0);

        shortBuffer.position(shortBuffer.position() + decoded);
        shortBuffer.flip();
        return shortBuffer;
    }

    public static List<ByteBuffer> encode(PointerByReference opusEncoder, ShortBuffer shortBuffer) {
        int read = 0;
        List<ByteBuffer> list = new ArrayList<>();
        while (shortBuffer.hasRemaining()) {
            ByteBuffer dataBuffer = ByteBuffer.allocate(8192);
            int toRead = Math.min(shortBuffer.remaining(), dataBuffer.remaining());
            read = Opus.INSTANCE.opus_encode(opusEncoder, shortBuffer, FRAME_SIZE, dataBuffer, toRead);
            dataBuffer.position(dataBuffer.position() + read);
            dataBuffer.flip();
            list.add(dataBuffer);
            shortBuffer.position(shortBuffer.position() + FRAME_SIZE);
        }

        shortBuffer.flip();
        return list;
    }

    public static void playBack(SourceDataLine speaker, ShortBuffer shortBuffer) throws LineUnavailableException {
        short[] shortAudioBuffer = new short[shortBuffer.remaining()];
        shortBuffer.get(shortAudioBuffer);
        byte[] audio = ShortToByte_Twiddle_Method(shortAudioBuffer);
        speaker.write(audio, 0, audio.length);
    }

    public static ShortBuffer recordFromMicrophone(TargetDataLine microphone) throws LineUnavailableException {
        // Assume that the TargetDataLine, line, has already been obtained and
        // opened.

        //byte[] data = new byte[microphone.getBufferSize() / 120];
        byte[] data = new byte[960];
        // probably way too big
        // Here, stopped is a global boolean set by another thread.
        int numBytesRead;
        numBytesRead = microphone.read(data, 0, data.length);
        ShortBuffer shortBuffer = ShortBuffer.allocate(numBytesRead * 2);
        shortBuffer.put(ByteBuffer.wrap(data).asShortBuffer());
        shortBuffer.flip();
        return shortBuffer;
    }

    public static byte[] ShortToByte_Twiddle_Method(final short[] input) {
        final int len = input.length;
        final byte[] buffer = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            buffer[(i * 2) + 1] = (byte) (input[i]);
            buffer[(i * 2)] = (byte) (input[i] >> 8);
        }
        return buffer;
    }

}