//package com;


package net.tomp2p.opuswrapper;

import com.sun.jna.PointerType;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.ShortByReference;
import net.tomp2p.opuswrapper.OpusCodec;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedList;
import java.util.List;

public class VOIP {

	private static final int SAMPLE_RATE = 48000;
	private static final int FRAME_SIZE = 480;

	boolean stopCapture = false;
	ByteArrayOutputStream byteArrayOutputStream;
	AudioFormat audioFormat;
	TargetDataLine targetDataLine;
	AudioInputStream audioInputStream;
	SourceDataLine sourceDataLine;
	static InetAddress host;
	static int port;
	private final static int packetsize = 69 ;
	//private JitterBuffer jitterBuffer = new JitterBuffer(5);
	private AudioFormat getAudioFormat() {
		float sampleRate = 48000.0F;
		int sampleSizeInBits = 16;
		int channels = 2;
		boolean signed = true;
		boolean bigEndian = true;
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
	}

	private void get_and_play_audioStreme(int port) {
		byte got_tempBuffer[] = new byte[69];
		byteArrayOutputStream = new ByteArrayOutputStream();
		stopCapture = false;

		try(DatagramSocket socket = new DatagramSocket( port )){
			System.out.println("Established DatagramSocket on receiving end");
			DatagramPacket packet = new DatagramPacket( got_tempBuffer, packetsize ) ;
			IntBuffer error = IntBuffer.allocate(4);
			PointerByReference opusDecoder = Opus.INSTANCE.opus_decoder_create(SAMPLE_RATE, 1, error);

			try {
				System.out.println("Trying to set up audio format");

				audioFormat = getAudioFormat();     //get the audio format

				DataLine.Info dataLineInfo1 = new DataLine.Info(SourceDataLine.class, audioFormat);
				//System.out.println("dataLineInfo1 established");
				sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo1);
				//System.out.println("sourceDataLine established");
				sourceDataLine.open(audioFormat);
				//System.out.println("Opened audioFormat");
				sourceDataLine.start();

				System.out.println("Started the sourceDataLine");

				//Setting the maximum volume
				FloatControl control = (FloatControl)sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
				control.setValue(control.getMaximum());

				System.out.println("Flow control established and set");

				//playing the audio


				while (true) {
					System.out.println("Trying to receive");
					try {
						socket.receive(packet);
					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
					System.out.println("Packet received");
					byte[] data = packet.getData();
					if (packet != null) {
						System.out.println("Received packet: " + data.length);
					}
					ShortBuffer decodedFromNetwork = OpusCodec.decode(opusDecoder, data);
					OpusCodec.playBack(sourceDataLine, decodedFromNetwork);
					//Receive the packet
					//socket.receive( packet );
					//jitterBuffer.add(packet);
					//DatagramPacket tmp = jitterBuffer.removeDatagram();
					//if(tmp != null){sourceDataLine.write(tmp.getData(), 0, packetsize);}//playing audio available in tempBuffer
					//sourceDataLine.write(packet.getData(), 0, packetsize);
				}



			} catch (LineUnavailableException e) {
				System.out.println(e);
				System.exit(0);
			}

		}catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}

	}

	private void capture_and_send_audioStreme(InetAddress host, int port) {

		byte cap_tempBuffer[] = new byte[500];
		byteArrayOutputStream = new ByteArrayOutputStream();
		stopCapture = false;

		try {

			Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();    //get available mixers
			System.out.printf("%d\n",mixerInfo.length	);
			System.out.println("Available mixers:");
			Mixer mixer = null;
			for (int cnt = 0; cnt < mixerInfo.length; cnt++) {

				System.out.println(cnt + " " + mixerInfo[cnt].getName());
				mixer = AudioSystem.getMixer(mixerInfo[cnt]);

				Line.Info[] lineInfos = mixer.getTargetLineInfo();

				if (lineInfos.length >= 1 && lineInfos[0].getLineClass().equals(TargetDataLine.class)) {
					System.out.println(cnt + " Mic is supported!");
					System.out.println("Now you are able to talk");
					break;
				}

			}

			audioFormat = getAudioFormat();     //get the audio format

			DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
			targetDataLine = (TargetDataLine) mixer.getLine(dataLineInfo);
			targetDataLine.open(audioFormat);
			targetDataLine.start();

			IntBuffer error = IntBuffer.allocate(4);

			//send the audioStreme to known host
			try (DatagramSocket socket = new DatagramSocket()){
				//ObjectOutputStream outToClient = new ObjectOutputStream(socket.getOutputStream());

				PointerByReference opusEncoder = Opus.INSTANCE.opus_encoder_create(SAMPLE_RATE, 1, Opus.OPUS_APPLICATION_RESTRICTED_LOWDELAY, error);

				while (true) {
					ShortBuffer dataFromMic = OpusCodec.recordFromMicrophone(targetDataLine);
					List<ByteBuffer> packets = OpusCodec.encode(opusEncoder, dataFromMic);

					/*
					LinkedList<Byte> list = new LinkedList<>();
					for (ByteBuffer dataBuffer : packets) {
						dataBuffer.array()
						for (byte b : dataBuffer.array()) {
							list.add(b);
						}
					}
					Object[] obs = list.toArray();
					byte[] nativeBytes = new byte[obs.length];
					for (int i = 0; i < obs.length; ++i) {
						nativeBytes[i] = (byte) obs[i];
					}
					DatagramPacket packet = new DatagramPacket(nativeBytes, nativeBytes.length, host, port);
					try {
						System.out.println("Packet size: " + packet.getData().length);
						socket.send(packet);
					} catch (Exception e) {
						System.out.println("Caught exception:" + e.getMessage());
					}

					*/

					for (ByteBuffer dataBuffer : packets) {
						byte[] transferedBytes = new byte[dataBuffer.remaining()];
						dataBuffer.get(transferedBytes);
						DatagramPacket packet = new DatagramPacket(transferedBytes, transferedBytes.length, host, port);
						System.out.println("Transferring bytes: " + packet.getData().length);
						try {
							socket.send(packet);
						} catch (Exception e) {
							System.out.println("Caught exception:" + e.getMessage());
						}
					}

				}

			} catch (IOException e) {

				System.out.println(e);
				System.exit(0);
			}

		} catch (LineUnavailableException e) {
			System.out.println(e);
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
					w.capture_and_send_audioStreme(host, port);
				}

			});

			Thread t2 = new Thread(new Runnable() {
				public void run(){
					w.get_and_play_audioStreme(port);
				}

			});

			//Starting the threads
			t1.start();
			t2.start();

		} catch( Exception e ){
			System.out.println( e ) ;
		}

	}


}


