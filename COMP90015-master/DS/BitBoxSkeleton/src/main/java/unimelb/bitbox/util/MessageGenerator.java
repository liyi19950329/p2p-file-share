package unimelb.bitbox.util;

import unimelb.bitbox.ServerMain;

import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class MessageGenerator {
    public Document Msg = new Document();

    public MessageGenerator(){}

    public MessageGenerator(FileSystemManager.FileSystemEvent fileSystemEvent){
//        System.out.println("能不能行了");
//        System.out.println("md5 是啥："+fileSystemEvent.fileDescriptor.toDoc().getString("md5"));
//        System.out.println("event 是啥 ： "+fileSystemEvent.event.toString());
        switch (fileSystemEvent.event){
            case FILE_CREATE:{
                Document FCrequest = new Document();
                FCrequest.append("command","FILE_CREATE_REQUEST");
                FCrequest.append("fileDescriptor",fileSystemEvent.fileDescriptor.toDoc());
                FCrequest.append("pathName",fileSystemEvent.pathName);
                Msg = FCrequest;
                break;
            }
            case FILE_DELETE:{
                Document FDrequest = new Document();
                FDrequest.append("command","FILE_DELETE_REQUEST");
                FDrequest.append("fileDescriptor",fileSystemEvent.fileDescriptor.toDoc());
                FDrequest.append("pathName",fileSystemEvent.pathName);
                Msg = FDrequest;
                break;
            }
            case FILE_MODIFY:{
                Document FMrequest = new Document();
                FMrequest.append("command","FILE_MODIFY_REQUEST");
                FMrequest.append("fileDescriptor",fileSystemEvent.fileDescriptor.toDoc());
                FMrequest.append("pathName",fileSystemEvent.pathName);
                Msg = FMrequest;
                break;
            }
            case DIRECTORY_CREATE:{
                Document DCrequest = new Document();
                DCrequest.append("command","DIRECTORY_CREATE_REQUEST");
                DCrequest.append( "pathName",fileSystemEvent.pathName);
                Msg = DCrequest;
                break;
            }
            case DIRECTORY_DELETE:{
                Document DDrequest = new Document();
                DDrequest.append("command","DIRECTORY_DELETE_REQUEST");
                DDrequest.append( "pathName",fileSystemEvent.pathName);
                Msg = DDrequest;
                break;
            }
        }
    }

    public Document HandShakeRequest(){
        Document HSrequest = new Document();
        Document hostPort = new Document();
        hostPort.append("host", Configuration.getConfigurationValue("advertisedName"));
        hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
        HSrequest.append("command","HANDSHAKE_REQUEST");
        HSrequest.append("hostPort",hostPort);
        return HSrequest;
    }

    public Document ConRefused(HashMap<String, Socket> hps){
        Document conRefused = new Document();
        ArrayList<Document> peers = new ArrayList<>();
        for(String address:hps.keySet()){
            Document peer = new Document();
            String[] add = address.split(":");
            String host = add[0];
            String port = add[1];
            peer.append("host",host);
            peer.append("port", Integer.parseInt(port));
            peers.add(peer);
        }
        conRefused.append("command","CONNECTION_REFUSED");
        conRefused.append("message","connection limit reached");
        conRefused.append("peers", peers);
        return conRefused;
    }

    public Document HandShakeResponse(){
        Document HSresponse = new Document();
        Document hostPort = new Document();
        hostPort.append("host", Configuration.getConfigurationValue("advertisedName"));
        hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
        HSresponse.append("command","HANDSHAKE_RESPONSE");
        HSresponse.append("hostPort", hostPort);
        return HSresponse;
    }

    public Document FileCreateResponse(Document FCrequest, Boolean status, Boolean pathsSave, Boolean fileExist ) {
        Document FCresponse = FCrequest;
        FCresponse.append("command", "FILE_CREATE_RESPONSE");
        if (status) {
            FCresponse.append("message", "file loader ready");
            FCresponse.append("status",true);
        } else if(!pathsSave){
            FCresponse.append("message", "unsafe pathname given");
            FCresponse.append("status",false);
        } else if(fileExist){
            FCresponse.append("message", "pathname already exists");
            FCresponse.append("status",false);
        } else{
            FCresponse.append("message", "there was a problem creating the file");
            FCresponse.append("status",false);
        }
        return FCresponse;
    }

    public Document FileBytesRequest(String pathname, Document fileDescriptor, long position, long length){
        Document Bytesrequest = new Document();
        Bytesrequest.append("command","FILE_BYTES_REQUEST");
        Bytesrequest.append("fileDescriptor",fileDescriptor);
        Bytesrequest.append("pathName", pathname);
        Bytesrequest.append("position", position);
        Bytesrequest.append("length",length);
        return Bytesrequest;
    }

    public Document FileDeleteResponse(Document FDrequest, Boolean status, Boolean pathsSave, Boolean fileExist){
        Document FDresponse = FDrequest;
        FDresponse.append("command", "FILE_DELETE_RESPONSE");
        if (status) {
            FDresponse.append("message", "file deleted");
            FDresponse.append("status",true);
        } else if(!pathsSave){
            FDresponse.append("message", "unsafe pathname given");
            FDresponse.append("status",false);
        } else if(!fileExist){
            FDresponse.append("message", "pathname does not exist");
            FDresponse.append("status",false);
        } else{
            FDresponse.append("message", "there was a problem creating the file");
            FDresponse.append("status",false);
        }
        return FDresponse;
    }

    public Document FileModifyResponse(Document FMrequest, Boolean status, Boolean pathsSave, Boolean pathExist, Boolean fileSame) {
        Document FMresponse = FMrequest;
        FMresponse.append("command", "FILE_MODIFY_RESPONSE");
        if (status) {
            FMresponse.append("message", "file loader ready");
            FMresponse.append("status", true);
        } else if (!pathsSave) {
            FMresponse.append("message", "unsafe pathname given");
            FMresponse.append("status", false);
        } else if (!pathExist) {
            FMresponse.append("message", "pathname does not exist");
            FMresponse.append("status", false);
        } else if (fileSame){
            FMresponse.append("message","file already exists with matching content");
            FMresponse.append("status",false);
        } else{
            FMresponse.append("message", "there was a problem creating the file");
            FMresponse.append("status",false);
        }
        return FMresponse;
    }

    public Document DirCreateResponse(Document DCrequest, Boolean status, Boolean pathsSave, Boolean dirExist){
        Document DCresponse = DCrequest;
        DCresponse.append("command","DIRECTORY_CREATE_RESPONSE");
        if (status){
            DCresponse.append("message","directory created");
            DCresponse.append("status",true);
        } else if (!pathsSave){
            DCresponse.append("message","unsafe pathname given");
            DCresponse.append("status",false);
        } else if (dirExist){
            DCresponse.append("message","pathname already exists");
            DCresponse.append("status",false);
        } else{
            DCresponse.append("message","there was a problem creating the directory");
            DCresponse.append("status",false);
        }
        return DCresponse;
    }

    public Document DirDeleteResponse(Document DDrequest, Boolean status, Boolean pathsSave, Boolean dirExist){
        Document DDresponse = DDrequest;
        DDresponse.append("command","DIRECTORY_DELETE_RESPONSE");
        if (status){
            DDresponse.append("message","directory deleted");
            DDresponse.append("status",true);
        } else if (!pathsSave){
            DDresponse.append("message","unsafe pathname given");
            DDresponse.append("status",false);
        } else if (!dirExist){
            DDresponse.append("message","pathname does not exist");
            DDresponse.append("status",false);
        } else{
            DDresponse.append("message","there was a problem deleting the directory");
            DDresponse.append("status",false);
        }
        return DDresponse;
    }

    public Document FileBytesResponse(Document Bytesrequest, String content, Boolean status){
        Document Bytesresponse = Bytesrequest;
        Bytesresponse.append("command","FILE_BYTES_RESPONSE");
        Bytesresponse.append("content",content);
        if(status){
            Bytesresponse.append("message","successful read");
        }else{
            Bytesresponse.append("message","unsuccessful read");
        }
        Bytesresponse.append("status",status);
        return Bytesresponse;
    }
}
