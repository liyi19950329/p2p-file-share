package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		Document initMsg = PeerMessage.initMessage(fileSystemEvent);
		if(Peer.MODE.equals("tcp")) {
			for (Socket socket : Peer.TCPConnectingPeers.values()) {
				try {
					BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
					bw.write(initMsg.toJson());
					bw.newLine();
					bw.flush();
				} catch (IOException e) {
					System.out.println("Failed to send initMsg");
				}
			}
		} else if (Peer.MODE.equals("udp")) {
			for (InetAddress host: Peer.UDPRememberingPeers.keySet()) {
				try {
					System.out.println("记住的:" + host);
					int port = Peer.UDPRememberingPeers.get(host);
					System.out.println("port 是：" + port);
					byte[] Msg = initMsg.toJson().getBytes();
					DatagramPacket packet = new DatagramPacket(Msg, Msg.length, host, port);
					DatagramSocket socket = new DatagramSocket();
					socket.send(packet);
//					DatagramPacket newPacket = new DatagramPacket(new byte[1024], 1024);
//					socket.receive(newPacket);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (SocketException e) {
					System.out.println("Failed init socket");
				} catch (IOException e) {
					System.out.println("Failed IO socket");
				}
			}
		}
	}

	public void synchronize(ArrayList<FileSystemEvent> pathEvent, Socket socket){
		for(FileSystemEvent event:pathEvent){
			Document initMsg = PeerMessage.initMessage(event);
			try{
				log.info("Synchronizing...");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),"UTF-8"));
				Response.writeInStream(bw, initMsg.toJson());
			} catch (IOException e){
				log.warning(e.getMessage());
				System.out.println("Failed get output stream");
				break;
			}
		}
	}

	public void synchronize(ArrayList<FileSystemEvent> pathEvent, DatagramSocket socket, DatagramPacket packet){
		for(FileSystemEvent event:pathEvent){
			Document initMsg = PeerMessage.initMessage(event);
			log.info("Synchronizing...");
			Response.writeInPacket(socket, packet, initMsg.toJson());
		}
	}
	
}
