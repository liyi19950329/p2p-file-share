package unimelb.bitbox;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

public class Peer 
{
	private static Logger log = Logger.getLogger(Peer.class.getName());
	public static ArrayList<String> peers = new ArrayList<>();
	static HashMap<String, Socket> TCPConnectingPeers = new HashMap<>();
	static HashMap<String, Socket> TCPIncomingPeers = new HashMap<>();
	static String MODE = Configuration.getConfigurationValue("mode");
	public static HashMap<InetAddress, Integer> UDPRememberingPeers = new HashMap<>();
	public static String advertisedName = Configuration.getConfigurationValue("advertisedName");
	public static long port = Long.parseLong(Configuration.getConfigurationValue("port"));
	public static int udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
    private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(30, 60, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());




    public static void main( String[] args ) throws IOException, NumberFormatException, NoSuchAlgorithmException
    {
    	System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        Configuration.getConfiguration();
        Collections.addAll(peers, Configuration.getConfigurationValue("peers").split(","));

        ServerMain sm = new ServerMain();
        if (MODE.equals("tcp")) {
            new Thread(() -> ConnectingToPeer(sm)).start();
            new Thread(() -> ListeningToPeer(sm)).start();
            new Thread(() -> PeriodicSync(sm)).start();
        } else if (MODE.equals("udp")) {
            new Thread(() -> UDPConnecting(sm)).start();
            new Thread(() -> UDPListening(sm)).start();
            //new Thread(() -> PeriodicSync(sm)).start();
        }

    }

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
                    Document handShakeRequest = PeerMessage.HandshakeRequest(port);
                    Response.writeInStream(bw, handShakeRequest.toJson());
                    threadPool.execute(new Response(socket, sm));
                } catch (UnknownHostException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    System.out.println("Failed init socket");
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

    public static void UDPConnecting(ServerMain sm) {
        while (peers.size() > 0) {
            for (Iterator<String> i = peers.iterator(); i.hasNext(); ) {
                try {
                    String hostPort = i.next();
                    String host = hostPort.split(":")[0];
                    InetAddress address = InetAddress.getByName(host);
                    int port = Integer.parseInt(hostPort.split(":")[1]);
//                log.info("Connected to peer "+peer);
                    byte[] handShakeRequest = PeerMessage.HandshakeRequest(udpPort).toJson().getBytes();
                    DatagramPacket packet = new DatagramPacket(handShakeRequest, handShakeRequest.length, address, port);
                    DatagramSocket socket = new DatagramSocket();
                    socket.send(packet);
                    DatagramPacket fromServer = new DatagramPacket(new byte[500000], 500000);
                    socket.receive(fromServer);
                    i.remove();

                    threadPool.execute(new Response(socket, fromServer, sm));
                } catch (UnknownHostException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    System.out.println("Failed init socket");
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
            ServerSocket ss = new ServerSocket((int)port);
            System.out.println("local port is：" + port);
            while (true) {
                Socket TCPsocket = ss.accept();
                threadPool.execute(new Response(TCPsocket, sm));
            }
        } catch (IOException e) {
//                log.warning(e.getMessage());
            System.out.println("Failed init socket");
            System.out.println(e.getMessage());
        }
    }

    public static void UDPListening(ServerMain sm) {
        try (DatagramSocket UDPsocket = new DatagramSocket(udpPort)) {
            while (true) {
                try {
                    DatagramPacket fromClient = new DatagramPacket(new byte[500000], 500000);
                    UDPsocket.receive(fromClient);
                    threadPool.execute(new Response(UDPsocket, fromClient, sm));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            System.out.println("Failed init UDP socket");
        }
    }

    public static void PeriodicSync(ServerMain sm) {
        while (true) {
            try {
                for (Socket socket : TCPConnectingPeers.values()) {
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);
                }
                Thread.sleep(Integer.parseInt(Configuration.getConfigurationValue("syncInterval")) * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void BFS(ArrayList<Document> peers, ServerMain sm) {
        for (Document peer : peers) {
            HostPort hostPort = new HostPort(peer);
            String host = hostPort.host;
            int port = hostPort.port;
            try {
                Socket socket = new Socket(host, port);
                System.out.println("Try connect to peer: " + hostPort.toString());
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),"UTF-8"));
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                Document handShakeRequest = PeerMessage.HandshakeRequest(port);
                Response.writeInStream(bw, handShakeRequest.toJson());
                try{
                    String reader = br.readLine();
                    if (reader != null) {
                        Document MsgReceived = Document.parse(reader);
                        System.out.println("收到的是 Received: " + MsgReceived.toJson());
                        threadPool.execute(new Response(socket, sm));
                        if(MsgReceived.getString("command").equals("HANDSHAKE_RESPONSE")){
                            TCPConnectingPeers.put(host + ":" + port, socket);
                            break;
                        }
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            } catch (UnknownHostException e) {
//            log.warning(e.getMessage());
                System.out.println(e.getMessage());
            } catch (IOException e) {
//            log.warning(e.getMessage());
            }
        }
    }
}
