package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.SocketHandler;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.MessageGenerator;

import static sun.misc.PostVMInitHook.run;

public class Peer {
    private static Logger log = Logger.getLogger(Peer.class.getName());
    private ArrayList<String> peers = new ArrayList<>();
    public static HashMap<String, Socket> connectingPeers = new HashMap<>();
    private int port = Integer.parseInt(Configuration.getConfigurationValue("port"));

    public Peer() {
        String[] nodes = Configuration.getConfigurationValue("peers").split(",");
        Collections.addAll(this.peers, nodes);
    }


    public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        Configuration.getConfiguration();
        ServerMain sm = new ServerMain();

        Peer pr = new Peer();
        new Thread() {
            public void run() {
                try {
                    for (String peer : pr.peers) {
                        String host = peer.split(":")[0];
                        int port = Integer.parseInt(peer.split(":")[1]);
                        System.out.println("socket地址："+host);
                        System.out.println("socket端口："+port);
                        Socket socket = new Socket(host, port);
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        Thread go = new Thread(new MsgProcessor(socket, sm));

                        MessageGenerator MG = new MessageGenerator();
                        Document handShakeRequest = MG.HandShakeRequest();
                        bw.write(handShakeRequest.toJson());
                        bw.newLine();
                        bw.flush();
                        go.start();
                    }
                }  catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        System.out.println("现在的数量是："+connectingPeers.size());
        try{
            ServerSocket ss = new ServerSocket(pr.port);
            System.out.println("本地端口为："+pr.port);
            while (true){
                Socket socket = ss.accept();
                Thread go = new Thread(new MsgProcessor(socket, sm));
                go.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


//    public void Ask(ServerMain sm){
//	    try{
//            for (String peer:peers){
//                String host = peer.split(":")[0];
//                int port = Integer.parseInt(peer.split(":")[1]);
//                Socket socket = new Socket(host, port);
//                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
//                MessageGenerator MG = new MessageGenerator();
//                bw.write(MG.HandShakeRequest().toJson());
//                bw.flush();
//
//                new MsgProcessor(socket, connectingPeers, sm);
//
//            }
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    public void Listening(ServerMain sm){
//	    try{
//            ServerSocket ss = new ServerSocket(port);
//            while (true){
//                Socket socket = ss.accept();
//                System.out.println("biu");
//                new MsgProcessor(socket, connectingPeers, sm);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    }
}
