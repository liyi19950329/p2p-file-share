package unimelb.bitbox;

import javax.crypto.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class AES {

    public static byte[] generateKey(){
        try{
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128);
            SecretKey sk = kgen.generateKey();
            byte[] raw = sk.getEncoded();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    //用secretkey加密
    public static String Base64encryptAES(SecretKey AESsk, String plainText) throws Exception {
        byte[] byteText = plainText.getBytes();
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, AESsk);
            byte[] encryptedBytes = cipher.doFinal(byteText);
            String output = Base64.getEncoder().encodeToString(encryptedBytes);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new Exception("No Such Algorithm");
        } catch (InvalidKeyException e) {
            throw new Exception("Invalid Secret Key");
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            return null;
        } catch (BadPaddingException e) {
            throw new Exception("Encryption Data Disorder");
        } catch (IllegalBlockSizeException e) {
            throw new Exception("Illegal Encryption Size");
        }
    }

    //用secretkey解密
    public static String decryptbyAES(SecretKey AESsk, String Base64cipherText) throws Exception {
        byte[] encryptedBytes =Base64.getDecoder().decode(Base64cipherText);
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, AESsk);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String output = new String(decryptedBytes);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new Exception("No Such Algorithm");
        } catch (InvalidKeyException e) {
            throw new Exception("Invalid Secret Key");
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            return null;
        } catch (BadPaddingException e) {
            throw new Exception("Encryption Data Disorder");
        } catch (IllegalBlockSizeException e) {
            throw new Exception("Illegal Encryption Size");
        }
    }

    private static String byteToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            String strHex = Integer.toHexString(aByte);
            if (strHex.length() > 3) {
                sb.append(strHex.substring(6));
            } else {
                if (strHex.length() < 2) {
                    sb.append("0").append(strHex);
                } else {
                    sb.append(strHex);
                }
            }
        }
        return sb.toString();
    }
}
