package unimelb.bitbox;

import javax.crypto.SecretKey;
import java.security.interfaces.RSAPrivateKey;

public class Client {
    protected static SecretKey sk =null;
    public static void main(String[] args){

        try{
            RSAPrivateKey privateKey = getKeyPair.getPrifromFile("id_rsa");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
