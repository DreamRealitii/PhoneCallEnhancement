package com.example.phonecallenhancement;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

import tech.gusavila92.websocketclient.WebSocketClient;

public class ws {
    private WebSocketClient webSocketClient;
    private File cache;

    public ws(File cache) {
        this.cache = cache;
        createWebSocketClient();
    }
    //protected void onCreate(Bundle savedInstanceState) {
    //    createWebSocketClient();
    //}

    private void createWebSocketClient() {
        URI uri;
        try {
            // Connect to local host
            uri = new URI("ws://mc.alanyeung.co:16385/ws/channel-1");
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen() {
                Log.i("WebSocket", "Session is starting");
                webSocketClient.send("11>Hello World!");
            }

            @Override
            public void onTextReceived(String s) {
                Log.i("WebSocket", "Message received" + s);
                //final String message = s;
                //Toast.makeText(getActivity(), "This is my Toast message!",
                //        Toast.LENGTH_LONG).show();

                try {
                    String b64Data = s.split(">")[1];
                    //Log.i("WebSocket", b64Data);
                    byte[] decodedBytes = Base64.getDecoder().decode(b64Data.getBytes());
                    //Log.i("WebSocket", Environment.getExternalStorageDirectory().toString());
                    File outputFile = File.createTempFile("file", ".webm", cache);
                    outputFile.deleteOnExit();
                    Log.i("WebSocket",outputFile.toString());
                    FileOutputStream fileoutputstream = new FileOutputStream(outputFile);
                    fileoutputstream.write(decodedBytes);
                    fileoutputstream.close();
                    Log.i("WebSocket", "Done");

                    MediaPlayer mp = new MediaPlayer();
                    try {
                        mp.setDataSource(outputFile.toString());
                        mp.prepare();
                        mp.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    // TODO: handle exception
                    Log.e("Error", e.toString());

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
                System.out.println(e.getMessage());
            }

            @Override
            public void onCloseReceived() {
                Log.i("WebSocket", "Closed ");
                System.out.println("onCloseReceived");
            }
        };

        webSocketClient.setConnectTimeout(10000);
        webSocketClient.setReadTimeout(60000);
        webSocketClient.enableAutomaticReconnection(5000);
        webSocketClient.connect();
    }
}
