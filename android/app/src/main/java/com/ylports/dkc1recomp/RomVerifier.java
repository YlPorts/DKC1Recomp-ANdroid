package com.ylports.dkc1recomp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Pure Java, shared by the importer and host-side tests. No ROM hash bypass. */
public final class RomVerifier {
    public static final int ROM_SIZE = 4 * 1024 * 1024;
    public static final String SHA256 =
        "fa8cacf5bbfc39ee6bbaa557adf89133d60d42f6cf9e1db30d5a36a469f74d15";
    private RomVerifier() {}

    public static byte[] readVerified(InputStream source) throws IOException {
        if (source == null) throw new IOException("No se pudo abrir el archivo.");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(ROM_SIZE + 512);
        byte[] block = new byte[32 * 1024];
        int total = 0;
        for (;;) {
            int count = source.read(block);
            if (count == -1) break;
            if (count == 0) {
                int one = source.read();
                if (one == -1) break;
                if (++total > ROM_SIZE + 512) throw sizeError();
                buffer.write(one);
                continue;
            }
            total += count;
            if (total > ROM_SIZE + 512) throw sizeError();
            buffer.write(block, 0, count);
        }
        return verify(buffer.toByteArray());
    }
    public static byte[] verify(byte[] raw) throws IOException {
        if (raw == null || (raw.length != ROM_SIZE && raw.length != ROM_SIZE + 512))
            throw sizeError();
        int offset = raw.length == ROM_SIZE + 512 ? 512 : 0;
        String actual = sha256(raw, offset, ROM_SIZE);
        if (!SHA256.equals(actual)) {
            throw new IOException("La ROM no es Donkey Kong Country USA v1.0 compatible.\n"
                + "No se acepta una versión europea, otra revisión ni una ROM modificada.\n"
                + "SHA-256 obtenido: " + actual);
        }
        return offset == 0 ? raw : Arrays.copyOfRange(raw, offset, raw.length);
    }
    public static String sha256(byte[] bytes, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, offset, length);
            byte[] hash = digest.digest();
            char[] hex = "0123456789abcdef".toCharArray();
            char[] out = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                out[i * 2] = hex[(hash[i] & 255) >>> 4];
                out[i * 2 + 1] = hex[hash[i] & 15];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 no disponible", impossible);
        }
    }
    private static IOException sizeError() {
        return new IOException("Selecciona una ROM .sfc o .smc de 4 MiB. "
            + "También se admite la misma ROM con cabecera de copiador de 512 bytes. "
            + "Los archivos ZIP no se importan.");
    }
}
