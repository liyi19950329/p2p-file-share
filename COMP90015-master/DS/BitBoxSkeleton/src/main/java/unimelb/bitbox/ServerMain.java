package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import unimelb.bitbox.util.*;
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
		MessageGenerator MG = new MessageGenerator(fileSystemEvent);
		System.out.println("实时生成了："+MG.Msg.toJson());
		System.out.println("能不能发出去："+Peer.connectingPeers);
		//把生成的消息发给所有peer
		for( Socket socket : Peer.connectingPeers.values()){
			try{
				System.out.println("这里是实时发送");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				bw.write(MG.Msg.toJson());
				bw.newLine();
				bw.flush();
			} catch (IOException e){

			}
		}

	}

	public void synchronize(ArrayList<FileSystemEvent> pathEvent, Socket socket){
		for(FileSystemEvent event:pathEvent){
			MessageGenerator MG = new MessageGenerator(event);
			try{
				System.out.println("这里是初始发送");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

				bw.write(MG.Msg.toJson());
				bw.newLine();
				bw.flush();
			} catch (IOException e){

			}
		}
	}
	
}
