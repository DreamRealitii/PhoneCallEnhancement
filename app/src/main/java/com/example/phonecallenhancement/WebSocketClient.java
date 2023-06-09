package com.example.phonecallenhancement;

import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;

public class WebSocketClient {
    private static final String TAG = "WebSocket";
    private tech.gusavila92.websocketclient.WebSocketClient webSocketClient;
    private File cache;
    private Boolean connected;
    private byte[] decodedBytes;

    private Queue<String> incomingString;
    private Queue<byte[]> receivedInformation;
    private String username;

    //private ArrayList<Long> pp = new ArrayList<Long>();

    private long pingv;

    public WebSocketClient(File cache) {
        this.cache = cache;
        connected = false;
        createWebSocketClient();
        decodedBytes = null;
        receivedInformation = new ArrayDeque<>();
        username = getDeviceName();
        incomingString = new ArrayDeque<>();
        pingv = 0;
    }

    public void toggle() {
        connected = !connected;
    }

    public void sendAudio(String s) {
        if(connected) {
            File file = new File(s);
            StringBuilder encodedString = new StringBuilder();
            try {
                System.out.println("Encoding," + System.currentTimeMillis());
                FileInputStream fileInputStream = new FileInputStream(file);

                byte fileContent[] = new byte[3000];

                while (fileInputStream.read(fileContent) >= 0) {
                    encodedString.append(Base64.getEncoder().encodeToString(fileContent));
                }
                fileInputStream.close();
                System.out.println("Send," + System.currentTimeMillis());
                //webSocketClient.send(s.toString());
                webSocketClient.send(username + ">" + (System.currentTimeMillis()) + ">A>"+ encodedString.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.i("WebSocket", "Done sending!" + System.currentTimeMillis());
            //webSocketClient.send(s);
        }else{
            Log.i("WebSocket", "DISCONNECTED!");
        }
    }

    public byte[] getAudio() {
       if(receivedInformation.size() == 0) {
           return null;
       }
       return receivedInformation.poll();
    }

    public static String getUid() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 3;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String uid = getUid();
        if (model.startsWith(manufacturer)) {
            return uid + ":" + capitalize(model);
        } else {
            return uid + ":" + capitalize(manufacturer) + ":" + model;
        }
    }

    private void createWebSocketClient() {
        URI uri;
        try {
            // Connect to local host
            uri = new URI("ws://mc.alanyeung.co:16385/ws/channel-1");
            connected = true;
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new tech.gusavila92.websocketclient.WebSocketClient(uri) {
            @Override
            public void onOpen() {
                Log.d(TAG, "Session is starting");
                webSocketClient.send(username + ">" + (System.currentTimeMillis()) + ">T>" +username + " Joined! Hello World!");

            }

            @Override
            public void onTextReceived(String s) {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    public void onCompletion(MediaPlayer mMediaPlayer) {
                        //mMediaPlayer.release();
//                        Log.d("T", "DONE");
                        mp.stop();
                        mp.reset();
                        mp.release();
                    }
                });
                //Log.d(TAG, "Message received " + s);
                //Toast.makeText(this, "Audio stop command interrupted\n", Toast.LENGTH_SHORT).show();
                try {
                    long timeStamp = Long.parseLong(s.split(">")[1]);
                    long currUnix = System.currentTimeMillis();
                    //Log.d(TAG, currUnix + "ms");
                    //pp.add( Math.abs((currUnix - timeStamp) ));
                    //System.out.println(pp.toString());
                    pingv = Math.abs((currUnix - timeStamp) );
//                    Log.d(TAG, "Curr delay:" + (currUnix - timeStamp) + "ms");
                    String type = s.split(">")[2];
                    if(type.equals("A")  && !s.split(">")[0].equals(username)) {
                    //if(type.equals("A")  && s.split(">")[0].equals(username)) {
                        System.out.println("Rcv," + System.currentTimeMillis());
                        String b64Data = s.split(">")[3];
                        //Log.i("WebSocket", b64Data);
                        decodedBytes = Base64.getDecoder().decode(b64Data.getBytes());
                        byte[] pureAudioData = convertWavToByteArray(decodedBytes);
                        //Log.d(TAG, "pureAudioData: " + Arrays.toString(pureAudioData));

                        receivedInformation.add(pureAudioData);

                        File outputFile = File.createTempFile("file", ".webm", cache);
                        outputFile.deleteOnExit();
                        //Log.i("WebSocket",outputFile.toString());
                        FileOutputStream fileoutputstream = new FileOutputStream(outputFile);
                        fileoutputstream.write(decodedBytes);
                        fileoutputstream.close();
                        System.out.println("Try play," + System.currentTimeMillis());
                        try {
                            mp.setDataSource(outputFile.toString());
                            mp.prepare();
                            mp.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        if(!s.split(">")[0].equals(username)) {
                            if(s.split(">").length > 3) {
                                incomingString.add(s.split(">")[0] + ">" + s.split(">")[3]);
                            }
                        }
                        //Log.d(TAG, "TEXT RCV" +  s.split(">")[3]);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onBinaryReceived(byte[] data) {
            }

            @Override
            public void onPingReceived(byte[] data) {
            }

            @Override
            public void onPongReceived(byte[] data) {
            }

            @Override
            public void onException(Exception e) {
            }

            @Override
            public void onCloseReceived() {
                Log.d(TAG, "Closed ");
                connected = false;
            }
        };

        webSocketClient.setConnectTimeout(10000);
        webSocketClient.setReadTimeout(60000);
        webSocketClient.enableAutomaticReconnection(5000);
        webSocketClient.connect();
        //connected = true;
    }

    public void close() {
        webSocketClient.close();
    }

    public static byte[] convertWavToByteArray(byte[] wavData) {
        // Assume you have the WAV audio data stored in a byte array called "wavData"

        // Get the length of the WAV header (first 44 bytes)
        int headerSize = 44;

        // Get the length of the audio data (total size - header size)
        int dataSize = wavData.length - headerSize;

        // Create a new byte array to store the pure audio data
        byte[] pureAudioData = new byte[dataSize];

        // Copy the audio data (excluding the header) to the new array
        System.arraycopy(wavData, headerSize, pureAudioData, 0, dataSize);

        return pureAudioData;
    }

    public String getNextTranscript() {
        return incomingString.poll();
    }

    public void sendTranscript(String input){
        webSocketClient.send(username + ">" + (System.currentTimeMillis()) + ">T>"+ input);
    }

    public long ping() {
        return pingv;
    }
}
