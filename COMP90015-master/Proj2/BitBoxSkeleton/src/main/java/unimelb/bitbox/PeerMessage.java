package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class PeerMessage  {
    
    public static Document initMessage (FileSystemManager.FileSystemEvent event) {
        /**
         * create by: Cyril
         * description: Generate initial/synchronization event message
         * create time: 2019/5/21 14:18
         *
         * @Param: event
         * @return
         */
        Document initMsg = new Document();
        switch (event.event) {
            case FILE_CREATE:{
                initMsg.append("command", "FILE_CREATE_REQUEST");
                initMsg.append("fileDescriptor", event.fileDescriptor.toDoc());
                initMsg.append("pathName", event.pathName);
                break;
            }
            case FILE_DELETE:{
                initMsg.append("command", "FILE_DELETE_REQUEST");
                initMsg.append("fileDescriptor", event.fileDescriptor.toDoc());
                initMsg.append("pathName", event.pathName);
                break;
            }
            case FILE_MODIFY:{
                initMsg.append("command", "FILE_MODIFY_REQUEST");
                initMsg.append("fileDescriptor", event.fileDescriptor.toDoc());
                initMsg.append("pathName", event.pathName);
                break;
            }
            case DIRECTORY_CREATE:{
                initMsg.append("command", "DIRECTORY_CREATE_REQUEST");
                initMsg.append("pathName", event.pathName);
                break;
            }
            case DIRECTORY_DELETE:{
                initMsg.append("command", "DIRECTORY_DELETE_REQUEST");
                initMsg.append("pathName", event.pathName);
                break;
            }
        }
        return initMsg;
    }

    public static Document HandshakeRequest(int port) {
        /**
         * create by: Cyril
         * description: Generate a "HANDSHAKE_REQUEST" message
         * create time: 2019/5/21 14:40
         *
         * @Param:
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        HostPort hostPort = new HostPort(Peer.advertisedName, port);
        output.append("command", "HANDSHAKE_REQUEST");
        output.append("hostPort", hostPort.toDoc());
        return output;
    }

    public static Document HandshakeResponse(int port) {
        /**
         * create by: Cyril
         * description: Generate a "HANDSHAKE_RESPONSE" message, when accepting the connection
         * create time: 2019/5/21 14:42
         *
         * @Param:
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        HostPort hostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), port);
        output.append("command", "HANDSHAKE_RESPONSE");
        output.append("hostPort", hostPort.toDoc());
        return output;
    }

    public static Document MaximumConnection(HashMap<String, Socket> incomingPeers) {
        /**
         * create by: Cyril
         * description: Generate a "CONNECTION_REFUSED" message, when incoming-connection reaches the maximum
         * create time: 2019/5/21 14:43
         *
         * @Param: incomingPeers
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        ArrayList<Document> peers = new ArrayList<>();
        for (String hostPort : incomingPeers.keySet()) {
            Document peer = new Document();
            peer.append("host", new HostPort(hostPort).host);
            peer.append("port", new HostPort(hostPort).port);
            peers.add(peer);
        }
        output.append("command", "CONNECTION_REFUSED");
        output.append("message", "connection limit reached");
        output.append("peers", peers);
        return output;
    }

    public static Document DuplicationRefuse() {
        /**
         * create by: Cyril
         * description: Generate a "INVALID_PROTOCOL" message, when there is duplicate connection request
         * create time: 2019/5/21 14:45
         *
         * @Param:
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "INVALID_PROTOCOL");
        output.append("message", "already connected to the peer");
        return output;
    }

    public static Document InvalidProtocol() {
        /**
         * create by: Cyril
         * description: Generate a "INVALID_PROTOCOL" message, when receiving invalid command message
         * create time: 2019/5/21 14:47
         *
         * @Param:
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "INVALID_PROTOCOL");
        output.append("message", "message must contain a valid command");
        return output;
    }

    public static Document FileCreateResponse(Document fileCreateRq, Boolean status, Boolean pathsSafe, Boolean fileExist) {
        /**
         * create by: Cyril
         * description: Generate a "FILE_CREATE_RESPONSE" message to response a "FILE_CREATE_REQUEST" message
         * create time: 2019/5/21 14:50
         *
         * @Param: fileCreateRq
        * @Param: status, whether the create is agreed
        * @Param: pathsSafe, should be true if agree to create
        * @Param: fileExist, should be false if agree to create
         * @return unimelb.bitbox.util.Document
         */
        fileCreateRq.append("command", "FILE_CREATE_RESPONSE");
        if (status) {
            fileCreateRq.append("message", "file loader ready");
            fileCreateRq.append("status", true);
        } else if (!pathsSafe) {
            fileCreateRq.append("message", "unsafe pathname given");
            fileCreateRq.append("status", false);
        } else if (fileExist) {
            fileCreateRq.append("message", "pathname already exists");
            fileCreateRq.append("status", false);
        } else {
            fileCreateRq.append("message", "there was a problem creating the file");
            fileCreateRq.append("status", false);
        }
        return fileCreateRq;
    }

    public static Document FileBytesRequest(String pathname, Document fileDescriptor, long position, long length) {
        /**
         * create by: Cyril
         * description: Generate a "FILE_BYTES_REQUEST" message if agreed to create
         * create time: 2019/5/21 14:54
         *
         * @Param: pathname
        * @Param: fileDescriptor
        * @Param: position
        * @Param: length
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "FILE_BYTES_REQUEST");
        output.append("fileDescriptor", fileDescriptor);
        output.append("pathName", pathname);
        output.append("position", position);
        output.append("length", length);
        return output;
    }

    public static Document FileBytesResponse(Document bytesRq, String content, Boolean status) {
        /**
         * create by: Cyril
         * description: Generate a "FILE_BYTES_RESPONSE" message to response a "FILE_BYTES_REQUEST" message
         * create time: 2019/5/21 14:56
         *
         * @Param: bytesRq
        * @Param: content
        * @Param: status, whether successfully read the bytes
         * @return unimelb.bitbox.util.Document
         */
        bytesRq.append("command", "FILE_BYTES_RESPONSE");
        bytesRq.append("content", content);
        if (status) {
            bytesRq.append("message", "successful read");
        } else {
            bytesRq.append("message", "unsuccessful read");
        }
        bytesRq.append("status", status);
        return bytesRq;
    }

    public static Document FileDeleteResponse(Document fileDeleteRq, Boolean status, Boolean pathsSafe, Boolean fileExist) {
        /**
         * create by: Cyril
         * description: Generate a "FILE_DELETE_RESPONSE" message to response a "FILE_DELETE_REQUEST" message
         * create time: 2019/5/21 14:58
         *
         * @Param: fileDeleteRq
        * @Param: status, whether agreed to delete
        * @Param: pathsSafe, should be true if agreed to delete
        * @Param: fileExist, should be true if agreed to delete
         * @return unimelb.bitbox.util.Document
         */
        fileDeleteRq.append("command", "FILE_DELETE_RESPONSE");
        if (status) {
            fileDeleteRq.append("message", "file deleted");
            fileDeleteRq.append("status", true);
        } else if (!pathsSafe) {
            fileDeleteRq.append("message", "unsafe pathname given");
            fileDeleteRq.append("status", false);
        } else if (!fileExist) {
            fileDeleteRq.append("message", "pathname does not exist");
            fileDeleteRq.append("status", false);
        } else {
            fileDeleteRq.append("message", "there was a problem creating the file");
            fileDeleteRq.append("status", false);
        }
        return fileDeleteRq;
    }

    public static Document FileModifyResponse(Document fileModifyRq, Boolean status, Boolean pathsSafe, Boolean pathExist, Boolean fileSame) {
        /**
         * create by: Cyril
         * description: Generate a "FILE_MODIFY_RESPONSE" message to response a "FILE_MODIFY_REQUEST" message
         * create time: 2019/5/21 15:00
         *
         * @Param: fileModifyRq
        * @Param: status, whether agreed to modify
        * @Param: pathsSafe, should be true if agreed to modify
        * @Param: pathExist, should be true if agreed to modify
        * @Param: fileSame, should be false if agreed to modify
         * @return unimelb.bitbox.util.Document
         */
        fileModifyRq.append("command", "FILE_MODIFY_RESPONSE");
        if (status) {
            fileModifyRq.append("message", "file loader ready");
            fileModifyRq.append("status", true);
        } else if (!pathsSafe) {
            fileModifyRq.append("message", "unsafe pathname given");
            fileModifyRq.append("status", false);
        } else if (!pathExist) {
            fileModifyRq.append("message", "pathname does not exist");
            fileModifyRq.append("status", false);
        } else if (fileSame) {
            fileModifyRq.append("message", "file already exists with matching content");
            fileModifyRq.append("status", false);
        } else {
            fileModifyRq.append("message", "there was a problem creating the file");
            fileModifyRq.append("status", false);
        }
        return fileModifyRq;
    }

    public static Document DirCreateResponse(Document dirCreateRq, Boolean status, Boolean pathsSafe, Boolean dirExist) {
        /**
         * create by: Cyril
         * description: Generate a "DIRECTORY_CREATE_RESPONSE" message to response a "DIRECTORY_CREATE_REQUEST" message
         * create time: 2019/5/21 15:02
         *
         * @Param: dirCreateRq
        * @Param: status, whether agreed to create the dir
        * @Param: pathsSafe, should be true if agreed to create
        * @Param: dirExist, should be false if agreed to create
         * @return unimelb.bitbox.util.Document
         */
        dirCreateRq.append("command", "DIRECTORY_CREATE_RESPONSE");
        if (status) {
            dirCreateRq.append("message", "directory created");
            dirCreateRq.append("status", true);
        } else if (!pathsSafe) {
            dirCreateRq.append("message", "unsafe pathname given");
            dirCreateRq.append("status", false);
        } else if (dirExist) {
            dirCreateRq.append("message", "pathname already exists");
            dirCreateRq.append("status", false);
        } else {
            dirCreateRq.append("message", "there was a problem creating the directory");
            dirCreateRq.append("status", false);
        }
        return dirCreateRq;
    }

    public static Document DirDeleteResponse(Document dirDeleteRq, Boolean status, Boolean pathsSafe, Boolean dirExist) {
        /**
         * create by: Cyril
         * description: Generate a "DIRECTORY_DELETE_RESPONSE" message to response a "DIRECTORY_DELETE_REQUEST" message
         * create time: 2019/5/21 15:03
         *
         * @Param: dirDeleteRq
        * @Param: status, whether agreed to delete the dir
        * @Param: pathsSafe, should be true if agreed to delete
        * @Param: dirExist, should be false if agreed to delete
         * @return unimelb.bitbox.util.Document
         */
        dirDeleteRq.append("command", "DIRECTORY_DELETE_RESPONSE");
        if (status) {
            dirDeleteRq.append("message", "directory deleted");
            dirDeleteRq.append("status", true);
        } else if (!pathsSafe) {
            dirDeleteRq.append("message", "unsafe pathname given");
            dirDeleteRq.append("status", false);
        } else if (!dirExist) {
            dirDeleteRq.append("message", "pathname does not exist");
            dirDeleteRq.append("status", false);
        } else {
            dirDeleteRq.append("message", "there was a problem deleting the directory");
            dirDeleteRq.append("status", false);
        }
        return dirDeleteRq;
    }
}
