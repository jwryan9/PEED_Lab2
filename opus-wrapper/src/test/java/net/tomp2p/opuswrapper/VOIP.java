package net.tomp2p.opuswrapper;

import com.sun.jna.ptr.PointerByReference;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

public class VOIP {

	private static final int SAMPLE_RATE = 48000;

	private AudioFormat audioFormat;
	private static InetAddress host;
	private static int port;

	private AudioFormat getAudioFormat() {
		float sampleRate = 48000.0F;
		int sampleSizeInBits = 16;
		int channels = 2;
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, true, true);
	}

	private void get_and_play_audioStream(int port) {

		//create the server socket to receive encoded audio
		try (ServerSocketChannel serverChannel = ServerSocketChannel.open()){

			serverChannel.bind(new InetSocketAddress(host, port));
			SocketChannel channel = serverChannel.accept();
			IntBuffer error = IntBuffer.allocate(4);
			PointerByReference opusDecoder = Opus.INSTANCE.opus_decoder_create(SAMPLE_RATE, 1, error);

			try {
				audioFormat = getAudioFormat();     //get the audio format

				DataLine.Info dataLineInfo1 = new DataLine.Info(SourceDataLine.class, audioFormat);
				SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo1);
				sourceDataLine.open(audioFormat);
				sourceDataLine.start();

				//Setting the maximum volume
				FloatControl control = (FloatControl)sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
				control.setValue(control.getMaximum());

				//playing the audio

				ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
				while (true) {

					int numBytesRead = channel.read(buffer);

					if (numBytesRead == -1) {
						channel.close();
						System.exit(0);
					} else {
						buffer.flip();
					}

					ShortBuffer decodedFromNetwork = OpusCodec.decode(opusDecoder, buffer);
					OpusCodec.playBack(sourceDataLine, decodedFromNetwork);
					buffer.compact(); //clears the remaining bytes in buffer
				}

			} catch (LineUnavailableException e) {
				e.printStackTrace();
				System.exit(0);
			}

		}catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

	}

	private void capture_and_send_audioStream(InetAddress host, int port) {

		try {

			Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();    //get available mixers
			System.out.printf("%d\n",mixerInfo.length	);
			System.out.println("Available mixers:");
			Mixer mixer = null;
			for (int cnt = 0; cnt < mixerInfo.length; cnt++) {

				System.out.println(cnt + " " + mixerInfo[cnt].getName());
				mixer = AudioSystem.getMixer(mixerInfo[cnt]);

				Line.Info[] lineInfos = mixer.getTargetLineInfo();

				if (lineInfos.length >= 1 && lineInfos[0].getLineClass() != null &&
						lineInfos[0].getLineClass().equals(TargetDataLine.class)) {
					System.out.println(cnt + " Mic is supported!");
					System.out.println("Now you are able to talk");
					break;
				}

			}

			audioFormat = getAudioFormat();     //get the audio format

			DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
			TargetDataLine targetDataLine = (TargetDataLine) mixer.getLine(dataLineInfo);
			targetDataLine.open(audioFormat);
			targetDataLine.start();

			IntBuffer error = IntBuffer.allocate(4);

			//send the audioStreme to known host
			try (SocketChannel channel = SocketChannel.open()){
				channel.connect(new InetSocketAddress(host, port));
				PointerByReference opusEncoder = Opus.INSTANCE.opus_encoder_create(SAMPLE_RATE, 1, Opus.OPUS_APPLICATION_RESTRICTED_LOWDELAY, error);

				while (true) {
					ShortBuffer dataFromMic = OpusCodec.recordFromMicrophone(targetDataLine);
					List<ByteBuffer> packets = OpusCodec.encode(opusEncoder, dataFromMic);
					ByteBuffer[] bbs = packets.toArray(new ByteBuffer[0]);
					long count = channel.write(bbs);
					for (ByteBuffer bb : bbs)
						bb.compact();
				}

			} catch (IOException e) {

				e.printStackTrace();
				System.exit(0);
			}

		} catch (LineUnavailableException e) {
			e.printStackTrace();
			System.exit(0);
		}

	}

	public static void main( String args[] ) {
		org.apache.log4j.BasicConfigurator.configure();
		if( args.length != 2 ) {
			System.out.println( "usage: Please Enter host and then port" ) ;
			return ;
		}

		try {
			// Convert the arguments to ensure that they are valid
			host = InetAddress.getByName( args[0] ) ;
			port = Integer.parseInt( args[1] ) ;
			final VOIP w = new VOIP();

			Thread t1 = new Thread(new Runnable() {
				public void run(){
					w.capture_and_send_audioStream(host, port);
				}

			});

			Thread t2 = new Thread(new Runnable() {
				public void run(){
					w.get_and_play_audioStream(port);
				}

			});

			//Starting the threads
			t1.start();
			t2.start();

		} catch( Exception e ){
			e.printStackTrace();
		}

	}


}

