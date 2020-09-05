package unimelb.bitbox;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;

public class Response implements Runnable{
    private ServerMain sm;
    private Document MsgReceived;
    private static BufferedWriter bw;
    private BufferedReader br;
    private Socket TCPsocket;
    private static DatagramSocket UDPsocket;
    private static DatagramPacket UDPPacket;
    private boolean connecting;

    Response(Socket TCPsocket, ServerMain sm) {
        this.TCPsocket = TCPsocket;
        this.sm = sm;
        this.connecting = true;
    }
    Response(DatagramSocket sk, DatagramPacket fromClient, ServerMain sm) {
        UDPsocket = sk;
        this.sm = sm;
        UDPPacket = fromClient;
        this.connecting = true;
    }

    @Override
    public void run() {
        if(Peer.MODE.equals("tcp")) {
            try {
                while (connecting) {
                    try {
                        bw = new BufferedWriter(new OutputStreamWriter(TCPsocket.getOutputStream(), StandardCharsets.UTF_8));
                        this.br = new BufferedReader(new InputStreamReader(TCPsocket.getInputStream(), StandardCharsets.UTF_8));
                        String reader = br.readLine();
                        if (reader != null) {
                            MsgReceived = Document.parse(reader);
                            System.out.println("Message received: " + MsgReceived.toJson());
                            processMsg();
                        }
                    } catch (IOException e) {
                        System.out.println("Failed read and write.");
                        Peer.TCPConnectingPeers.values().remove(TCPsocket);
                        Peer.TCPIncomingPeers.values().remove(TCPsocket);
                        bw.close();
                        br.close();
                        TCPsocket.close();
                    }
                }
                Peer.TCPConnectingPeers.values().remove(TCPsocket);
                Peer.TCPIncomingPeers.values().remove(TCPsocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if (Peer.MODE.equals("udp")) {
            String packetText = new String(UDPPacket.getData(), 0, UDPPacket.getLength());
            System.out.println("UDP received: " + packetText);
            MsgReceived = Document.parse(packetText);
            processMsg();
        }
    }

    public void processMsg () {
        String cmd = MsgReceived.getString("command");
        String response = "";
        String pathName = null;
        boolean pathSafe = false;
        boolean fileExist = false;
        boolean dirExist = false;
        boolean fileSame = false;
        Document fileDescriptor = new Document();
        String md5 = null;
        long fileSize = -1;
        long lastModified = -1;
        long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));

        if (MsgReceived.containsKey("pathName")) {
            pathName = MsgReceived.getString("pathName");
            pathSafe = sm.fileSystemManager.isSafePathName(pathName);
            fileExist = sm.fileSystemManager.fileNameExists(pathName);
            dirExist = sm.fileSystemManager.dirNameExists(pathName);
        }

        if (MsgReceived.containsKey("fileDescriptor")) {
            fileDescriptor = (Document) MsgReceived.get("fileDescriptor");
            md5 = fileDescriptor.getString("md5");
            if (fileExist) fileSame = sm.fileSystemManager.fileNameExists(pathName, md5);
            fileSize = fileDescriptor.getLong("fileSize");
            lastModified = fileDescriptor.getLong("lastModified");
        }
        switch (cmd) {
            case "INVALID_PROTOCOL": {
                connecting = false;
                break;
            }
            case "CONNECTION_REFUSED": {
                Peer.BFS((ArrayList<Document>) MsgReceived.get("peers"), sm);
                connecting = false;
                break;
            }
            case "HANDSHAKE_REQUEST": {
                Document hostPort = (Document) MsgReceived.get("hostPort");
                int port = (int) hostPort.getLong("port");
                String host = hostPort.getString("host");
                if(Peer.MODE.equals("tcp")) {
                    if (Peer.TCPConnectingPeers.keySet().contains(host + ":" + port)) {
                        response = PeerMessage.DuplicationRefuse().toJson();
                        connecting = false;
                    } else if (Peer.TCPIncomingPeers.size() >= Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"))) {
                        response = PeerMessage.MaximumConnection(Peer.TCPIncomingPeers).toJson();
                        connecting = false;
                    } else {
                        Peer.TCPIncomingPeers.put(host + ":" + port, TCPsocket);
                        Peer.TCPConnectingPeers.put(host + ":" + port, TCPsocket);
                        response = PeerMessage.HandshakeResponse(port).toJson();
                        sm.synchronize(sm.fileSystemManager.generateSyncEvents(), TCPsocket);
                    }
                } else if (Peer.MODE.equals("udp")) {
                    if (Peer.UDPRememberingPeers.size() >= Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections")) && !Peer.UDPRememberingPeers.keySet().contains(host)) {
                        response = PeerMessage.DuplicationRefuse().toJson();
                    } else {
                        try {
                            Peer.UDPRememberingPeers.put(InetAddress.getByName(host), port);
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }
                        response = PeerMessage.HandshakeResponse(Peer.udpPort).toJson();
                        sm.synchronize(sm.fileSystemManager.generateSyncEvents(), UDPsocket, UDPPacket);
                    }
                }
                writeIn(response);
                break;
            }
            case "HANDSHAKE_RESPONSE": {
                Document hostPort = (Document) MsgReceived.get("hostPort");
                int port = (int) hostPort.getLong("port");
                String host = hostPort.getString("host");
                if(Peer.MODE.equals("tcp")) {
                    Peer.TCPConnectingPeers.put(host + ":" + port, TCPsocket);
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), TCPsocket);
                } else if (Peer.MODE.equals("udp")) {
                    try {
                        Peer.UDPRememberingPeers.put(InetAddress.getByName(host), port);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), UDPsocket, UDPPacket);
                }
                break;
            }
            case "FILE_CREATE_REQUEST": {
                boolean status;
                boolean createLoader = false;
                if(pathSafe && !fileExist){
                    try{
                        createLoader = sm.fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        System.out.println("Failed creating fileLoader");
                    }
                    status = createLoader;
                }else {
                    status = false;
                }
                response = PeerMessage.FileCreateResponse(MsgReceived, status, pathSafe, fileExist).toJson();
                writeIn(response);
                long position = 0;
                long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                if(status) {
                    try {
                        if(sm.fileSystemManager.checkShortcut(pathName)){
                            break;
                        }
                    } catch (NoSuchAlgorithmException | IOException e) {
                        System.out.println("Failed check shortcut.");
                    }
                    writeIn(PeerMessage.FileBytesRequest(pathName, fileDescriptor, position, length).toJson());
                } else if (pathSafe && !fileSame) {
                    try {
                        if (sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified)){
                            writeIn(PeerMessage.FileBytesRequest(pathName, fileDescriptor, position, length).toJson());
                        }
                    } catch (IOException e) {
                        System.out.println("Failed create modify loader.");
                    }
                }
                break;
            }
            case "FILE_CREATE_RESPONSE": {
                break;
            }
            case "FILE_DELETE_REQUEST": {
                boolean status;
                if (pathSafe && fileExist) {
                    status = sm.fileSystemManager.deleteFile(pathName, lastModified, md5);
                } else {
                    status = false;
                }
                response = PeerMessage.FileDeleteResponse(MsgReceived, status, pathSafe, fileExist).toJson();
                writeIn(response);
                break;
            }
            case "FILE_DELETE_RESPONSE": {
                break;
            }
            case "FILE_MODIFY_REQUEST": {
                boolean status;
                boolean modifyLoader = false;
                if (pathSafe && fileExist && !fileSame) {
                    try {
                        modifyLoader = sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified);
                    } catch (IOException e) {
                        System.out.println("Failed create modify loader.");
                    }
                    status = modifyLoader;
                } else {
                    status = false;
                }
                //send a file modify response
                response = PeerMessage.FileModifyResponse(MsgReceived, status, pathSafe, fileExist, fileSame).toJson();
                writeIn(response);
                if (status) {
                    long position = 0;
                    long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                    writeIn(PeerMessage.FileBytesRequest(pathName, fileDescriptor, position, length).toJson());
                }
                break;
            }
            case "FILE_MODIFY_RESPONSE": {
                break;
            }
            case "DIRECTORY_CREATE_REQUEST": {
                boolean status;
                if (pathSafe && !dirExist) {
                    status = sm.fileSystemManager.makeDirectory(pathName);
                } else {
                    status = false;
                }
                response = PeerMessage.DirCreateResponse(MsgReceived, status, pathSafe, dirExist).toJson();
                writeIn(response);
                break;
            }
            case "DIRECTORY_CREATE_RESPONSE": {
                break;
            }
            case "DIRECTORY_DELETE_REQUEST": {
                boolean status;
                if (pathSafe && dirExist) {
                    status = sm.fileSystemManager.deleteDirectory(pathName);
                } else {
                    status = false;
                }
                response = PeerMessage.DirDeleteResponse(MsgReceived, status, pathSafe, dirExist).toJson();
                writeIn(response);
                break;
            }
            case "DIRECTORY_DELETE_RESPONSE": {
                long position = MsgReceived.getLong("position");
                long length = MsgReceived.getLong("length");
                String content = "";
                boolean status;
                try {
                    ByteBuffer fileData = sm.fileSystemManager.readFile(md5, position, length);
                    content = Base64.getEncoder().encodeToString(fileData.array());
                    status = true;
                } catch (NoSuchAlgorithmException | IOException e) {
                    System.out.println("Failed reading file bytes.");
                    status = false;
                }
                response = PeerMessage.FileBytesResponse(MsgReceived, content, status).toJson();
                writeIn(response);
                break;
            }
            case "FILE_BYTES_REQUEST":{
                long position = MsgReceived.getLong("position");
                long length = MsgReceived.getLong("length");
                String content = "";
                boolean status;
                try {
                    ByteBuffer fileData = sm.fileSystemManager.readFile(md5, position, length);
                    content = Base64.getEncoder().encodeToString(fileData.array());
                    status = true;
                } catch (NoSuchAlgorithmException | IOException e) {
                    status = false;
                }
                //send a fileBytes response
                response = PeerMessage.FileBytesResponse(MsgReceived, content, status).toJson();
                writeIn(response);
                System.out.println("我发了：" + response.getBytes().length);
                break;
            }
            case "FILE_BYTES_RESPONSE": {
                long position = MsgReceived.getLong("position");
                long length = MsgReceived.getLong("length");
                if (MsgReceived.getBoolean("status")) {
                    String content = MsgReceived.getString("content");
                    byte[] conByte = Base64.getDecoder().decode(content);
                    ByteBuffer fileData = ByteBuffer.wrap(conByte);
                    boolean writeOK;
                    try {
                        writeOK = sm.fileSystemManager.writeFile(pathName, fileData, position);
                    } catch (IOException e) {
                        System.out.println("Failed write file.");
                        writeOK = false;
                    }
                    if (writeOK) {
                        if (length + position < fileDescriptor.getLong("fileSize")) {
                            position = length + position;
                            length = Math.min(blockSize, fileSize - position);
                            response = PeerMessage.FileBytesRequest(pathName, fileDescriptor, position, length).toJson();
                            writeIn(response);
                        } else {
                            try {
                                if (sm.fileSystemManager.checkWriteComplete(pathName)) {
                                    sm.fileSystemManager.cancelFileLoader(pathName);
                                } else {
                                    System.out.println("file write failed");
                                }
                            } catch (NoSuchAlgorithmException e) {
                                System.out.println("No such");
                            } catch (IOException e) {
                                System.out.println("Failed checkWriteComplete/cancelLoader.");
                            }
                        }
                    }
                }
                break;
            }
            default: {
                response = PeerMessage.InvalidProtocol().toJson();
                writeIn(response);
            }
        }
    }

    public static void writeInStream (BufferedWriter bw, String content) {
        try{
            bw.write(content);
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeInPacket (DatagramSocket socket, DatagramPacket packet, String content) {
        byte[] output = content.getBytes();
        DatagramPacket response = new DatagramPacket(output, output.length, packet.getAddress(), Peer.UDPRememberingPeers.get(packet.getAddress()));
        try {
            socket.send(response);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed send packet.");
        }
    }

    public static void writeIn(String content) {
        if (Peer.MODE.equals("tcp")) {
            try {
                bw.write(content);
                bw.newLine();
                bw.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (Peer.MODE.equals("udp")) {
            byte[] output = content.getBytes();
            //if (output.length >)
            System.out.println("我给他发："+content+":"+Peer.UDPRememberingPeers.get(UDPPacket.getAddress()));
            DatagramPacket response = new DatagramPacket(output, output.length, UDPPacket.getAddress(), Peer.UDPRememberingPeers.get(UDPPacket.getAddress()));
            try {
                UDPsocket.send(response);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed send packet.");
            }
        }
    }


}
