package com.intel.analytics.bigdl.bigquant;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Loader {
    private String prefix = "lib";
    private List<String> libraries = new ArrayList<String>();
    private String os = System.getProperty("os.name").toLowerCase();

    public void init() throws IOException {
        libraries.add("bigquant");
        libraries.add("bigquant_rt");
        libraries.add("bigquant_avx2");
        libraries.add("bigquant_sse42");

        // for osx, we don't support avx512 now.
        // because the default version of gcc installed by brew doesn't enable this feature
        if (!os.contains("mac")) {
            libraries.add("bigquant_avx512");
        }

        // TODO for windows, we don't create bigquant.native dir
        Path tempDir = null;
        if (os.contains("win")) {
            tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        } else {
            tempDir = Files.createTempDirectory("bigquant.native.");
        }

        copyAll(tempDir);

        loadLibrary("bigquant_rt", tempDir);
        loadLibrary("bigquant", tempDir);
        int success = BigQuant.loadRuntime(tempDir.toString());
        if (success < 0) {
            System.exit(1);
        }

        deleteAll(tempDir);
    }

    private String libraryName(String name) {
        String os = System.getProperty("os.name").toLowerCase();
        String suffix = ".so";

        if (os.contains("mac")) {
            suffix = ".dylib";
        } else if (os.contains("win")) {
            suffix = ".dll";
        }

        name = prefix + name + suffix;

        return name;
    }

    private void copyAll(Path tempDir) throws IOException {
        for (String name: libraries) {
            String library = libraryName(name);
            ReadableByteChannel src = resource(library);
            copyLibraryToTemp(src, library, tempDir);
            src.close();
        }
    }

    private ReadableByteChannel resource(String name) throws NullPointerException {
        URL url = Loader.class.getResource("/" + name);
        if (url == null) {
            throw new Error("Can't find the library " + name + " in the resource folder.");
        }

        InputStream in = Loader.class.getResourceAsStream("/" + name);
        ReadableByteChannel src = Channels.newChannel(in);
        return src;
    }

    private void copyLibraryToTemp(ReadableByteChannel src, String name,
                                   Path tempDir) throws IOException {
        File tempFile = new File(tempDir.toFile() + File.separator + name);

        FileChannel dst = null;
        try {
            dst = new FileOutputStream(tempFile).getChannel();
            dst.transferFrom(src, 0, Long.MAX_VALUE);
        } finally {
            dst.close();
        }
    }

    private void deleteAll(Path tempDir) {
        File dir = tempDir.toFile();
        for (File f: dir.listFiles()) {
            f.delete();
        }

        dir.delete();
    }

    private void loadLibrary(String name, Path tempDir) {
        String path = tempDir.toString() + File.separator + libraryName(name);
        System.load(path);
    }
}
