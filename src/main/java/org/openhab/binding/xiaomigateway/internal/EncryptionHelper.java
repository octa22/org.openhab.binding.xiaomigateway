package org.openhab.binding.xiaomigateway.internal;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Ondřej Pečta on 29. 12. 2016.
 */
public class EncryptionHelper {

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    final protected static byte[] IV = parseHexBinary("17996D093D28DDB3BA695A2E6F58562E");

    public static String encrypt(String text, String key) {
        return encrypt(text,key, IV);
    }


    public static String encrypt(String text, String key, byte[] iv) {

        try {
            IvParameterSpec vector = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF8"), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, vector);
            byte[] encrypted = cipher.doFinal(text.getBytes());
            return bytesToHex(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    public static byte[] parseHexBinary(String s) {
        final int len = s.length();

        // "111" is not a valid hex encoding.
        if (len % 2 != 0)
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);

        byte[] out = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));
            if (h == -1 || l == -1)
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    private static int hexToBin(char ch) {
        if ('0' <= ch && ch <= '9') return ch - '0';
        if ('A' <= ch && ch <= 'F') return ch - 'A' + 10;
        if ('a' <= ch && ch <= 'f') return ch - 'a' + 10;
        return -1;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
