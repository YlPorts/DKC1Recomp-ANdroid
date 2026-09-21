package com.ylports.dkc1recomp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class MsuImportRegressionTest {
    static int checks;

    static void check(boolean ok) {
        checks++;
        if (!ok) throw new AssertionError("check " + checks);
    }

    static byte[] pcm() {
        byte[] b = new byte[40];
        b[0] = 'M'; b[1] = 'S'; b[2] = 'U'; b[3] = '1';
        return b;
    }

    static final class LargePcm extends InputStream {
        private long left;
        private int pos;

        LargePcm(long size) { left = size; }

        @Override
        public int read(byte[] b, int off, int len) {
            if (left == 0) return -1;
            int n = (int)Math.min(left, len);
            Arrays.fill(b, off, off + n, (byte)0);
            if (pos < 8) {
                byte[] h = {'M','S','U','1',0,0,0,0};
                int copy = Math.min(n, 8 - pos);
                System.arraycopy(h, pos, b, off, copy);
            }
            pos += n;
            left -= n;
            return n;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            return read(b, 0, 1) < 0 ? -1 : b[0] & 255;
        }
    }

    static byte[] zipGenericPack() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 1; i <= 27; i++) {
                String name;
                if (i == 1) name = "01 - Jungle Hijinxs.pcm";
                else if (i == 2) name = "Donkey Kong Country HD - 02.pcm";
                else if (i == 27) name = "DKC1_27.pcm";
                else name = "track-" + i + ".pcm";
                z.putNextEntry(new ZipEntry(name));
                z.write(pcm());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dkc-msu16-test");
        try {
            MusicPack.fromZip(root.toFile(), new ByteArrayInputStream(zipGenericPack()));
            check(MusicPack.isComplete(root.resolve("music/current").toFile()));

            Path stage = Files.createDirectory(root.resolve("single-stage"));
            MusicPack.Writer writer = new MusicPack.Writer(stage.toFile());
            long size = 9L * 1024 * 1024;
            writer.add("01 - Jungle Hijinxs.pcm", new LargePcm(size));
            File track = stage.resolve("track-1.pcm").toFile();
            check(track.isFile());
            check(track.length() == size);
            MusicPack.validatePcm(track);
            check(true);

            boolean clear = false;
            try {
                writer.add("music.pcm", new ByteArrayInputStream(pcm()));
            } catch (IOException e) {
                clear = e.getMessage() != null && e.getMessage().contains("número de pista");
            }
            check(clear);

            boolean range = false;
            try {
                writer.add("DKC 1994.pcm", new ByteArrayInputStream(pcm()));
            } catch (IOException e) {
                range = e.getMessage() != null && e.getMessage().contains("fuera de rango");
            }
            check(range);

            System.out.println("MSU-1 v1.6 regression tests passed: " + checks);
        } finally {
            MusicPack.delete(root.toFile());
        }
    }
}
