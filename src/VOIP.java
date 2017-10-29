//package com;

import java.net.* ;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class VOIP {

    boolean stopCapture = false;
    ByteArrayOutputStream byteArrayOutputStream;
    AudioFormat audioFormat;
    TargetDataLine targetDataLine;
    AudioInputStream audioInputStream;
    SourceDataLine sourceDataLine;
    static InetAddress host;
    static int port;
    private final static int packetsize = 500 ;
    private JitterBuffer jitterBuffer = new JitterBuffer(5);
    private AudioFormat getAudioFormat() {
        float sampleRate = 48000.0F;
        int sampleSizeInBits = 16;
        int channels = 2;
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    private void get_and_play_audioStreme(int port) {
        byte got_tempBuffer[] = new byte[500];
        byteArrayOutputStream = new ByteArrayOutputStream();
        stopCapture = false;

        try(DatagramSocket socket = new DatagramSocket( port );){

            DatagramPacket packet = new DatagramPacket( got_tempBuffer, packetsize ) ;
            System.out.println("Sever is ready to recieve data");

            try {

                audioFormat = getAudioFormat();     //get the audio format

                DataLine.Info dataLineInfo1 = new DataLine.Info(SourceDataLine.class, audioFormat);
                sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo1);
                sourceDataLine.open(audioFormat);
                sourceDataLine.start();

                //Setting the maximum volume
                FloatControl control = (FloatControl)sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
                control.setValue(control.getMaximum());

                //playing the audio


                while (true) {
                    //Receive the packet
                    socket.receive( packet );
                    jitterBuffer.add(packet);
                    DatagramPacket tmp = jitterBuffer.removeDatagram();
                    if(tmp != null){sourceDataLine.write(tmp.getData(), 0, packetsize);}//playing audio available in tempBuffer
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

            //send the audioStreme to known host
            try (DatagramSocket socket = new DatagramSocket() ;){

                int readCount;

                while (!stopCapture) {
                    readCount = targetDataLine.read(cap_tempBuffer, 0, cap_tempBuffer.length);  //capture sound into tempBuffer
                    if (readCount > 0) {
                        byteArrayOutputStream.write(cap_tempBuffer, 0, readCount);
                        DatagramPacket packet = new DatagramPacket(cap_tempBuffer, cap_tempBuffer.length, host, port);

                        //for(int i = 0; i < cap_tempBuffer.length; i++)
                            //System.out.print(cap_tempBuffer[i]);

                        //System.out.println();


                        // Send the packet
                        socket.send( packet ) ;

                    }
                }

                byteArrayOutputStream.close();

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
            VOIP w = new VOIP();

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


