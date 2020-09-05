package unimelb.bitbox;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.MessageGenerator;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;

public class MsgProcessor implements Runnable {

    private final Socket socket;
    private boolean connecting = true;
    private ServerMain sm;

    public MsgProcessor(Socket socket, ServerMain sm){
        this.socket = socket;
        this.sm = sm;
    }


    @Override
    public void run() {
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),"UTF8"));
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF8"));
            while (connecting){
                String reader = br.readLine();
                System.out.println("我读了"+reader);
                if(reader!=null){
                    Document MsgReceived = Document.parse(reader);
                    processCommand(MsgReceived, br, bw);
                }
            }
            bw.close();
            br.close();
            Peer.connectingPeers.values().remove(socket);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            //Peer.connectingPeers.values().remove(socket);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void processCommand(Document MsgReceived, BufferedReader br, BufferedWriter bw) throws IOException, NoSuchAlgorithmException {

        long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        MessageGenerator responseGenerator = new MessageGenerator();
        Document response = new Document();
        String cmd = MsgReceived.getString("command");


        String pathName = null;
        boolean pathSave = false;
        boolean fileExist = false;
        boolean dirExist = false;
        boolean fileSame = false;
        Document hostPort = new Document();
        String host = "";
        long port = -1;
        Document fileDescriptor = new Document();
        fileDescriptor.append("md5", "");
        fileDescriptor.append("lastModified", 0);
        fileDescriptor.append("fileSize",0);
        String md5 = null;
        long fileSize = -1;
        long lastModified = -1;

        if(MsgReceived.containsKey("pathName")) {
            pathName = MsgReceived.getString("pathName");
            pathSave = sm.fileSystemManager.isSafePathName(pathName);
            fileExist = sm.fileSystemManager.fileNameExists(pathName);
            if(fileExist) fileSame = sm.fileSystemManager.fileNameExists(pathName, md5);
            dirExist = sm.fileSystemManager.dirNameExists(pathName);
        }

        if(MsgReceived.containsKey("hostPort")){
            hostPort = (Document) MsgReceived.get("hostPort");
            host = hostPort.getString("host");
            port = hostPort.getLong("port");
        }

        if(MsgReceived.containsKey("fileDescriptor")) {
            fileDescriptor = (Document) MsgReceived.get("fileDescriptor");
            md5 = fileDescriptor.getString("md5");
            fileSize = fileDescriptor.getLong("fileSize");
            lastModified = fileDescriptor.getLong("lastModified");
        }

        switch(cmd){
            case "INVALID_PROTOCOL":{
                //断开连接
                connecting = false;
                break;
            }
            case "CONNECTION_REFUSED":{
                //断开连接
                connecting = false;
                break;
            }
            case "HANDSHAKE_REQUEST":{
                System.out.println("我收到了呀！");
                //Check if maximum number reaches
                if(Peer.connectingPeers.size()==Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"))){
                    //发送refuse message
                    response = responseGenerator.ConRefused(Peer.connectingPeers);
                    connecting = false;
                }else{
                    //发送HANDSHAKE_RESPONSE
                    Peer.connectingPeers.put(host+":"+port, socket);
                    response = responseGenerator.HandShakeResponse();
                    System.out.println("收到Request并同意的socket数量是："+Peer.connectingPeers.size());
                    System.out.println("这些socket是："+Peer.connectingPeers);
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);
                }
                bw.write(response.toJson());
                bw.newLine();
                bw.flush();
                break;
            }
            case "HANDSHAKE_RESPONSE":{
                //Add it into connected HashMap
                Peer.connectingPeers.put(host+":"+port, socket);
                System.out.println("已经连接的peer："+Peer.connectingPeers);
                //Initialize synchronization
                sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);
                break;
            }
            case "FILE_CREATE_REQUEST":{
                //回复一个FILE_CREATE_RESPONSE
                md5 = fileDescriptor.getString("md5");
                boolean status = false;
                boolean createLoader = false;
                if(pathSave) {
                        try {
                            sm.fileSystemManager.cancelFileLoader(pathName);
                            createLoader = sm.fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified);
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.out.println("错误在这里显示");
                            break;
                        }
                    status = pathSave && !fileExist && createLoader;
                } else status = false;
                response = responseGenerator.FileCreateResponse(MsgReceived, status, pathSave,fileExist);

                bw.write(response.toJson());
                bw.newLine();
                bw.flush();
                if(status){
                    if(sm.fileSystemManager.checkShortcut(pathName))
                        break;
                    //回复一个FILE_BYTES_REQUEST
                    long position = 0;
                    long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                    response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                    bw.write(response.toJson());
                    System.out.println("发送fileByteRequest了吗："+response.toJson());
                    bw.newLine();
                    bw.flush();
                }else if(!fileSame) {
                    if (sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
                        long position = 0;
                        long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                        response = responseGenerator.FileBytesRequest(pathName,fileDescriptor,position, length);
                        bw.write(response.toJson());
                        bw.newLine();
                        bw.flush();
                    }
                }else
                    break;
            }
            case "FILE_CREATE_RESPONSE":{
                //不用管他
                break;
            }
            case "FILE_DELETE_REQUEST":{
                boolean deleteFile = sm.fileSystemManager.deleteFile(pathName, lastModified, md5);
                boolean status = pathSave && fileExist && deleteFile;
                response = responseGenerator.FileDeleteResponse(MsgReceived, status, pathSave, fileExist);
                bw.write(response.toJson());
                bw.newLine();
                bw.flush();
                break;
            }
            case "FILE_DELETE_RESPONSE":{
                //不用管他
                break;
            }
            case "FILE_MODIFY_REQUEST":{
                boolean modifyLoader = sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified);
                boolean status = pathSave && fileExist && !fileSame && modifyLoader;

                response = responseGenerator.FileModifyResponse(MsgReceived, status, pathSave, fileExist, fileSame);
                bw.write(response.toJson());
                bw.newLine();
                bw.flush();

                if(status){
                    //可以修改，回复一个FILE_BYTES_REQUEST
                    long position = 0;
                    long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                    response = responseGenerator.FileBytesRequest(pathName,fileDescriptor,position, length);
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();
                }
                break;
            }
            case "FILE_MODIFY_RESPONSE":{
                //不用管他
                break;
            }
            case "DIRECTORY_CREATE_REQUEST":{
                boolean mkdirAct = sm.fileSystemManager.makeDirectory(pathName);
                boolean status = pathSave && !dirExist && mkdirAct;

                response = responseGenerator.DirCreateResponse(MsgReceived, status, pathSave, dirExist);
                bw.write(response.toJson());
                bw.newLine();
                bw.flush();
                break;
            }
            case "DIRECTORY_CREATE_RESPONSE":{
                //不用管他
                break;
            }
            case "DIRECTORY_DELETE_REQUEST":{
                boolean deldirAct = sm.fileSystemManager.deleteDirectory(pathName);
                boolean status = pathSave && dirExist && deldirAct;

                response = responseGenerator.DirDeleteResponse(MsgReceived, status, pathSave, dirExist);
                bw.write(response.toJson());
                bw.newLine();
                bw.flush();
                break;
            }
            case "DIRECTORY_DELETE_RESPONSE":{
                //不用管他
                break;
            }
            case "FILE_BYTES_REQUEST":{
                long position =MsgReceived.getLong("position");
                long length = MsgReceived.getLong("length");
                String content = "";
                boolean status = false;
                try {
                    ByteBuffer fileData = sm.fileSystemManager.readFile(md5, position, length);
                    //BASE64Encoder encoder = new BASE64Encoder();
                    content = Base64.getEncoder().encodeToString(fileData.array());
                    //System.out.println("content 是啥： "+content);
                    status = true;
                }catch (NoSuchAlgorithmException e){
                    status = false;
                }
                response = responseGenerator.FileBytesResponse(MsgReceived, content, status);
                bw.write(response.toJson());
                bw.newLine();
                bw.flush();
                break;
            }
            case "FILE_BYTES_RESPONSE":{
                long position =MsgReceived.getLong("position");
                long length = MsgReceived.getLong("length");
                if(MsgReceived.getBoolean("status")){
                    String content = MsgReceived.getString("content");
                    //BASE64Decoder decoder = new BASE64Decoder();
                    byte[] conByte = Base64.getDecoder().decode(content);
                    ByteBuffer fileData = ByteBuffer.wrap(conByte);
                    //                 fileData.flip();
                    boolean writeOK = sm.fileSystemManager.writeFile(pathName, fileData, position);

                    if(writeOK){
                        if(length+position<fileDescriptor.getLong("fileSize")){
                            position = length+position;
                            length = Math.min(blockSize, fileSize-position);
                            response = responseGenerator.FileBytesRequest(pathName,fileDescriptor,position, length);
                            bw.write(response.toJson());
                            bw.newLine();
                            bw.flush();
                        }else if(sm.fileSystemManager.checkWriteComplete(pathName)){
                            sm.fileSystemManager.cancelFileLoader(pathName);
                        }else System.out.println("传了个屁");
                    }
                }
                break;
            }
            default:
                System.out.println("啥也没匹配上");

        }
    }
}
