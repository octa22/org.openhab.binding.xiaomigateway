package org.openhab.binding.xiaomigateway.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.openhab.binding.xiaomigateway.internal.EncryptionHelper.bytesToHex;
import static org.openhab.binding.xiaomigateway.internal.EncryptionHelper.parseHexBinary;


/**
 * Created by Ondrej Pecta on 24.07.2017.
 */
public class MiioMessage {
    private final byte[] NO_CHECKSUM = new byte[]{0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};
    private final byte[] EMPTY_MESSAGE = new byte[]{};
    private final byte[] NEW_HEADER = parseHexBinary("2131000000000000000000000000000000000000000000000000000000000000");

    private final int DATA_START = 32;
    private final int HEADER_LENGTH = 32;
    private final int CHECKSUM_START = 16;
    private final int DEVICE_ID_START = 8;
    private final int STAMP_START = 12;
    private final int LENGTH_START = 2;


    private static final Logger logger =
            LoggerFactory.getLogger(MiioMessage.class);


    protected byte[] message;
    protected byte[] token;

    public MiioMessage() {
        this.message = null;
        this.token = null;
    }

    public MiioMessage(byte[] message, String token) {
        this.message = message;
        this.token = parseHexBinary(token);
    }

    public MiioMessage(String token) {
        this.token = parseHexBinary(token);
        this.message = EMPTY_MESSAGE;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }

    public void setToken(byte[] token) {
        this.token = token;
    }

    public byte[] getHeader() {
        return Arrays.copyOf(message, DATA_START);
    }

    public byte[] getData() {
        return Arrays.copyOfRange(message, DATA_START, message.length);
    }

    public byte[] getChecksum() {
        return Arrays.copyOfRange(message, CHECKSUM_START, DATA_START);
    }

    public byte[] createEncryptedMessage(String data, int deviceId, int stamp) {
        // encrypt data
        byte[] encrypted = encryptMiio(data, token);

        // prepare header
        byte[] header = createHeader();

        // set the stamp
        setUInt32(header, stamp, STAMP_START);

        // set the stamp
        setUInt32(header, deviceId, DEVICE_ID_START);

        // set the message length
        setUInt16(header, 32 + encrypted.length, LENGTH_START);

        //prepare final header
        byte[] finalHeader = createHeaderWithChecksum(header, encrypted);
        message = arrayConcat(finalHeader, encrypted);
        return message;
    }

    public byte[] createHeaderWithChecksum(byte[] header, byte[] bytesEncrypted) {
        try {
            byte[] headerPart = Arrays.copyOf(header, 16);

            byte[] forChecksum = arrayConcat(arrayConcat(headerPart, token), bytesEncrypted);
            byte[] result = doMD5(forChecksum);

            logger.debug("Checksum: {} ", bytesToHex(result));
            return arrayConcat(headerPart, result);
        } catch (Exception ex) {
            logger.error("Message timestamp error: {}", ex.toString());
        }
        return new byte[]{};
    }

    private byte[] createHeader() {
        return Arrays.copyOf(NEW_HEADER, HEADER_LENGTH);
    }

    public int getStamp() {
        return (int) readUInt32(message, STAMP_START);
    }

    public int getDeviceId() {
        return (int) readUInt32(message, DEVICE_ID_START);
    }

    public int getMessageLength() {
        return readUInt16(message, LENGTH_START);
    }

    public boolean hasChecksum() {
        try {
            byte[] headerChecksum = Arrays.copyOfRange(message, CHECKSUM_START, DATA_START);
            return !Arrays.equals(headerChecksum, NO_CHECKSUM) && message.length > HEADER_LENGTH;
        } catch (Exception ex) {
            logger.error("hasChecksum error: {}", ex.toString());
        }
        return false;
    }

    public boolean isHandshakeResponse() {
        if (!hasChecksum()) {
            //
            return (message.length == HEADER_LENGTH && getStamp() > 0) ? true : false;
        }
        return false;
    }

    public boolean isValid() {

        if (message.length != getMessageLength()) {
            return false;
        }

        if (isHandshakeResponse())
            return true;

        try {
            byte[] headerPart = Arrays.copyOf(message, CHECKSUM_START);
            byte[] headerChecksum = Arrays.copyOfRange(message, CHECKSUM_START, DATA_START);

            byte[] messagePart = Arrays.copyOfRange(message, DATA_START, message.length);
            byte[] forChecksum = arrayConcat(arrayConcat(headerPart, token), messagePart);
            byte[] result = doMD5(forChecksum);

            logger.debug("Checksum: {} ", bytesToHex(result));
            return Arrays.equals(headerChecksum, result);
        } catch (Exception ex) {
            logger.error("validateChecksum error: {}", ex.toString());
        }
        return false;
    }

    public byte[] getDecryptedData() {
        try {
            byte[] key = doMD5(token);
            byte[] iv = doMD5(arrayConcat(key, token));
            byte[] data = Arrays.copyOfRange(message, DATA_START, message.length);
            return decryptMiio(data, key, iv);
        } catch (Exception ex) {
            logger.error("getDecryptedData exception: {}", ex.toString());
            return new byte[]{};
        }
    }

    private byte[] decryptMiio(byte[] data, byte[] key, byte[] iv) {
        try {
            IvParameterSpec vector = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, vector);
            return cipher.doFinal(data);
        } catch (Exception e) {
            logger.error("decryptMiio exception: {}", e.toString());
        }
        return new byte[]{};
    }

    public static byte[] encryptMiio(String text, byte[] tokenBytes) {
        try {
            byte[] key = doMD5(tokenBytes);
            byte[] iv = doMD5(arrayConcat(key, tokenBytes));
            return encryptMiio(text, key, iv);
        } catch (Exception ex) {
            logger.error("encryptMiio exception: {}", ex.toString());
            return new byte[]{};
        }
    }

    public static byte[] encryptMiio(String text, byte[] key, byte[] iv) {

        try {
            IvParameterSpec vector = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, vector);
            return cipher.doFinal(text.getBytes());
            //return bytesToHex(encrypted);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return new byte[]{};
    }

    public static byte[] doMD5(String text) {
        try {
            byte[] bytesOfMessage = text.getBytes("UTF-8");

            return doMD5(bytesOfMessage);
        } catch (Exception ex) {
            return new byte[]{};
        }
    }

    public static byte[] doMD5(byte[] bytesOfMessage) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return md.digest(bytesOfMessage);
    }

    public byte[] createHeaderWithChecksum(byte[] header, String token, byte[] bytesEncrypted) {
        try {
            byte[] tokenBytes = parseHexBinary(token);
            byte[] headerPart = Arrays.copyOf(header, 16);

            byte[] forChecksum = arrayConcat(arrayConcat(headerPart, tokenBytes), bytesEncrypted);
            byte[] result = doMD5(forChecksum);

            logger.debug("Checksum: {} ", bytesToHex(result));
            return arrayConcat(headerPart, result);
        } catch (Exception ex) {
            logger.error("Message timestamp error: {}", ex.toString());
        }
        return new byte[]{};
    }

    public static long readUInt32(byte[] array, int pos) {
        final ByteBuffer buf = ByteBuffer.wrap(array);
        buf.put(array);
        return buf.getInt(pos);
    }

    public static int readUInt16(byte[] bb, int pos) {
        int result = 0;
        result += byte2int(bb[pos]) << 8;
        result += byte2int(bb[pos + 1]);
        return result;
    }

    public static int readUInt8(byte[] bb, int pos) {
        return byte2int(bb[pos]);
    }

    public static int byte2int(byte b) {
        return b < 0 ? b + 256 : b;
    }

    public static void setUInt32(byte[] array, int value, int pos) {
        final ByteBuffer buf = ByteBuffer.wrap(array); // big endian by default
        buf.put(array);
        buf.putInt(pos, value);
    }

    public static void setUInt16(byte[] header, int value, int i) {
        header[i] = (byte) (value >>> 8);
        header[i + 1] = (byte) (value);
    }

    public static byte[] arrayConcat(byte[] a, byte[] b) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            outputStream.write(a);
            outputStream.write(b);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }
}
