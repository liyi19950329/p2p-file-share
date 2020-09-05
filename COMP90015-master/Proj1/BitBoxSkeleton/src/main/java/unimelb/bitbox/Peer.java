package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.MessageGenerator;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * the main class
 */
public class Peer {

    private static Logger log = Logger.getLogger(Peer.class.getName());
    //the list of peers in the config file
    private static ArrayList<String> peers = new ArrayList<>();
    //the peers that are connected, including both incoming and outgoing
    public volatile static HashMap<String, Socket> requestedPeers = new HashMap<>();

    public volatile static HashMap<String, Socket> connectingPeers = new HashMap<>();
    //the incoming peers that are connected
    public volatile static HashMap<String, Socket> incomingPeers = new HashMap<>();
    //local port number
    public static int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
    //Allocate a thread for each established connection
    private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(30, 60, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());


    public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");

        log.info("BitBox Peer starting...");

        Configuration.getConfiguration();

        Collections.addAll(peers, Configuration.getConfigurationValue("peers").split(","));

        ServerMain sm = new ServerMain();

        new Thread(() ->
                ConnectingToPeer(sm))
                .start();

        new Thread(() ->
                ListeningToPeer(sm))
                .start();

        new Thread(() ->
                PeriodicSync(sm))
                .start();
    }

    /**
     * the peer will keep attempting to connect to all peers in the configuration file
     */
    public static void ConnectingToPeer(ServerMain sm) {
        while (peers.size() > 0) {
            for (Iterator<String> i = peers.iterator(); i.hasNext(); ) {
                try {
                    String hostport = i.next();
                    String host = hostport.split(":")[0];
                    int port = Integer.parseInt(hostport.split(":")[1]);
//                log.info("Connected to peer "+peer);
                    Socket socket = new Socket(host, port);
                    i.remove();
                    System.out.println("Try connect to peer: " + hostport);
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                    MessageGenerator MG = new MessageGenerator();
                    Document handShakeRequest = MG.HandshakeRequest();
                    bw.write(handShakeRequest.toJson());
                    requestedPeers.put(host + ":" + port, socket);
                    bw.newLine();
                    bw.flush();
                    threadPool.execute(new MessageProcessor(socket, sm));

                } catch (UnknownHostException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
//                    log.info("Peer Offline");
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * the peer will listen on its local port, waiting other peers to connect
     */
    public static void ListeningToPeer(ServerMain sm) {
        try {
            ServerSocket ss = new ServerSocket(port);
//                System.out.println("local port is：" + port);
            while (true) {
                Socket socket = ss.accept();
                threadPool.execute(new MessageProcessor(socket, sm));
            }
        } catch (IOException e) {
//                log.warning(e.getMessage());
            System.out.println(e.getMessage());
        }
    }

    /**
     * Every syncInterval seconds, the BitBox Peer should call generateSyncEvents()
     * to do a general synchronization with all neighboring peers.
     */
    public static void PeriodicSync(ServerMain sm) {
        while (true) {
            try {
                for (Socket socket : connectingPeers.values()) {
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);
                }
                Thread.sleep(Integer.parseInt(Configuration.getConfigurationValue("syncInterval")) * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * The peer that tried to connect should do a breadth first search of peers in the peers list, attempt to make a connection to one of them.
     *
     * @param peers the peer list returned from the CONNECTION_REFUSED protocol
     */

    public static void BFS(ArrayList<Document> peers, ServerMain sm) {
        for (Document peer : peers) {
            HostPort hostPort = new HostPort(peer);
            String host = hostPort.host;
            int port = hostPort.port;
            String hostport = host + ":" + port;
            System.out.println(requestedPeers);
            if (!requestedPeers.keySet().contains(hostport)) {
                try {
                    Socket socket = new Socket(host, port);
                    System.out.println("Try connect to peer: " + hostPort.toString());
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                    MessageGenerator MG = new MessageGenerator();
                    Document handShakeRequest = MG.HandshakeRequest();
                    bw.write(handShakeRequest.toJson());
                    requestedPeers.put(host + ":" + port, socket);
                    bw.newLine();
                    bw.flush();
                    threadPool.execute(new MessageProcessor(socket, sm));
//                    connectingPeers.put(host + ":" + port, socket);
                    break;
                } catch (UnknownHostException e) {
//            log.warning(e.getMessage());
                    System.out.println(e.getMessage());
                } catch (IOException e) {
//            log.warning(e.getMessage());
                }
            }
        }
    }


//    public static void BFS(ArrayList<Document> peers, ServerMain sm) {
//        for (Document peer : peers) {
//            HostPort hostPort = new HostPort(peer);
//            String host = hostPort.host;
//            int port = hostPort.port;
//            String hostport = host + ":" + port;
//            try {
//                    if(!refusingPeers.keySet().contains(hostport)) {
//                        Socket socket = new Socket(host, port);
//                        System.out.println("Try connect to peer: " + hostPort.toString());
//                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
//                        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
//                        MessageGenerator MG = new MessageGenerator();
//                        Document handShakeRequest = MG.HandshakeRequest();
//                        bw.write(handShakeRequest.toJson());
//                        bw.newLine();
//                        bw.flush();
//                        String reader = br.readLine();
//                        Document MsgReceived = Document.parse(reader);
//                        if (MsgReceived.getString("command").equals("HANDSHAKE_RESPONSE")) {
//                            System.out.println("收到的是 Received: " + MsgReceived.toJson());
//                            connectingPeers.put(host + ":" + port, socket);
//                            break;
//                        } else {
//                            System.out.println("收到的是 Received: " + MsgReceived.toJson());
//                            refusingPeers.put(host + ":" + port, socket);
//                        }
//                        threadPool.execute(new MessageProcessor(socket, sm));
//                    }
//
//                } catch (UnknownHostException e) {
////            log.warning(e.getMessage());
//                    System.out.println(e.getMessage());
//                } catch (IOException e) {
////            log.warning(e.getMessage());
//                }
//            }
//
//    }
//}
//    public static void BFS(ArrayList<Document> peers, ServerMain sm) {
//        boolean connecting = false;
//        for (Document peer : peers) {
//            HostPort hostPort = new HostPort(peer);
//            String host = hostPort.host;
//            int port = hostPort.port;
//            try {
//                Socket socket = new Socket(host, port);
//                System.out.println("Try connect to peer: " + hostPort.toString());
//                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
//                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
//                MessageGenerator MG = new MessageGenerator();
//                Document handShakeRequest = MG.HandshakeRequest();
//                bw.write(handShakeRequest.toJson());
//                bw.newLine();
//                bw.flush();
//                try {
//                    String reader = br.readLine();
//                    if (reader != null) {
//                        Document MsgReceived = Document.parse(reader);
//                        System.out.println("收到的是 Received: " + MsgReceived.toJson());
//                        threadPool.execute(new MessageProcessor(socket, sm));
//                        if (MsgReceived.getString("command").equals("HANDSHAKE_RESPONSE")) {
//                            connectingPeers.put(host + ":" + port, socket);
//                            break;
//                        }else{
//                            System.out.println("没连上");
//                        }
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            } catch (UnknownHostException e) {
////            log.warning(e.getMessage());
//                System.out.println(e.getMessage());
//            } catch (IOException e) {
////            log.warning(e.getMessage());
//            }
//        }
//
//        }
}


