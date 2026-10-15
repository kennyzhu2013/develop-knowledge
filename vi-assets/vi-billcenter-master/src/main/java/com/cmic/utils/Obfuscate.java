package com.cmic.utils;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.Base64;

public class Obfuscate {

    private static final int HEADER_SIZE = 5;
    private static final byte MAGIC_BYTE_0 = (byte) 0x4F;
    private static final byte MAGIC_BYTE_1 = (byte) 0x42;
    private static final byte COMPRESSED_FLAG = 0x01;
    private static final int COMPRESSION_MIN_SIZE = 20;
    private static final int CHECKSUM_SIZE = 4;

    public static class ObfuscateException extends Exception {
        public ObfuscateException(String message) {
            super(message);
        }
    }

    public static String Encode(String plain) throws ObfuscateException {
        byte[] payload = plain.getBytes();

        byte flags = 0;
        byte[] storedPlainPayload = payload;

        if (payload.length >= COMPRESSION_MIN_SIZE) {
            byte[] compressedPayload;
            try {
                compressedPayload = compressPayload(payload);
            } catch (IOException e) {
                throw new ObfuscateException("Compression error: " + e.getMessage());
            }
            if (compressedPayload.length < payload.length) {
                flags |= COMPRESSED_FLAG;
                storedPlainPayload = compressedPayload;
            }
        }

        if (storedPlainPayload.length > 0xFFFF) {
            throw new ObfuscateException("Payload too large");
        }

        int frameLength = HEADER_SIZE + storedPlainPayload.length + CHECKSUM_SIZE;
        byte[] frame = new byte[frameLength];
        frame[0] = MAGIC_BYTE_0;
        frame[1] = MAGIC_BYTE_1;
        frame[2] = flags;
        ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN).putShort(3, (short) storedPlainPayload.length);

        obfuscateInto(frame, HEADER_SIZE, storedPlainPayload);

        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        long checksum = crc32.getValue();
        ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN).putInt(frame.length - CHECKSUM_SIZE, (int) checksum);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(frame);
    }

    public static String Decode(String obfuscated) throws ObfuscateException {
        byte[] frame;
        try {
            frame = Base64.getUrlDecoder().decode(obfuscated);
        } catch (IllegalArgumentException e) {
            throw new ObfuscateException("Invalid base64url encoding");
        }

        if (frame.length < HEADER_SIZE + CHECKSUM_SIZE) {
            throw new ObfuscateException("Invalid header: frame too short");
        }
        if (frame[0] != MAGIC_BYTE_0 || frame[1] != MAGIC_BYTE_1) {
            throw new ObfuscateException("Invalid header: magic bytes mismatch");
        }

        byte flags = frame[2];
        if ((flags & ~COMPRESSED_FLAG) != 0) {
            throw new ObfuscateException("Invalid header: unknown flags");
        }

        int storedLen = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN).getShort(3) & 0xFFFF;
        if (frame.length != HEADER_SIZE + storedLen + CHECKSUM_SIZE) {
            throw new ObfuscateException("Invalid length");
        }

        byte[] storedPayload = new byte[storedLen];
        System.arraycopy(frame, HEADER_SIZE, storedPayload, 0, storedLen);

        int expectedChecksum = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN).getInt(frame.length - CHECKSUM_SIZE);

        deobfuscateInPlace(storedPayload);

        byte[] decodedPayload = storedPayload;
        if ((flags & COMPRESSED_FLAG) != 0) {
            try {
                decodedPayload = decompressPayload(storedPayload);
            } catch (IOException | DataFormatException e) {
                throw new ObfuscateException("Decompression error: " + e.getMessage());
            }
        }

        CRC32 crc32 = new CRC32();
        crc32.update(decodedPayload);
        int actualChecksum = (int) crc32.getValue();
        if (actualChecksum != expectedChecksum) {
            throw new ObfuscateException("Checksum mismatch");
        }

        if (!isValidUTF8(decodedPayload)) {
            throw new ObfuscateException("Invalid UTF-8");
        }

        return new String(decodedPayload);
    }

    private static byte[] compressPayload(byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(payload);
        deflater.finish();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    private static byte[] decompressPayload(byte[] payload) throws IOException, DataFormatException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(payload);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            baos.write(buffer, 0, count);
        }
        inflater.end();
        return baos.toByteArray();
    }

    private static void obfuscateInto(byte[] dst, int dstOffset, byte[] payload) {
        for (int i = 0; i < payload.length; i++) {
            int k = keyAt(i) & 0xFF;
            int p = payload[i] & 0xFF;
            int x = p ^ k;
            dst[dstOffset + i] = rotateLeft3((byte) x);
        }
    }

    private static void deobfuscateInPlace(byte[] payload) {
        for (int i = 0; i < payload.length; i++) {
            int k = keyAt(i) & 0xFF;
            int x = rotateRight3(payload[i]) & 0xFF;
            payload[i] = (byte) (x ^ k);
        }
    }

    private static byte keyAt(int i) {
        return (byte) ((i * 31 + 17) & 0xFF);
    }

    private static byte rotateLeft3(byte b) {
        int v = b & 0xFF;
        return (byte) ((v << 3) | (v >>> 5));
    }

    private static byte rotateRight3(byte b) {
        int v = b & 0xFF;
        return (byte) ((v >>> 3) | (v << 5));
    }

    private static boolean isValidUTF8(byte[] bytes) {
        try {
            new String(bytes, "UTF-8");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) throws ObfuscateException {
        String res = Encode("Hello, World!abcdefghijklmnopqrstuvwxyz1234567890");
        System.out.println(res);
        res = Decode(res);
        System.out.println(res);
    }
}
