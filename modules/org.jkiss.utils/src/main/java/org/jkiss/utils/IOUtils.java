/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.utils;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Some IO helper functions
 */
public final class IOUtils {
    private static final Logger log = Logger.getLogger(IOUtils.class.getName());

    public static final int DEFAULT_BUFFER_SIZE = 16384;

    private static final boolean USE_NIO_STREAMS = false;

    public static void close(@NotNull Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            log.log(
                Level.WARNING,
                "Failed to close closeable: " + closeable.getClass().getName(),
                e
            );
        }
    }

    public static void close(@NotNull AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            log.log(
                Level.WARNING,
                "Failed to close closeable: " + closeable.getClass().getName(),
                e
            );
        }
    }

    /**
     * Closes object if it  is AutoClosable or Closable.
     * It was added to support runtime before Java 21 then HttpClient become closable.
     */
    public static void tryClose(@NotNull Object object) {
        if (object instanceof Closeable c) {
            close(c);
        } else if (object instanceof AutoCloseable ac) {
            close(ac);
        }
    }

    public static void closeQuietly(@NotNull AutoCloseable... closeable) {
        for (AutoCloseable c : closeable) {
            close(c);
        }
    }

    public static void fastCopy(@NotNull InputStream src, @NotNull OutputStream dest) throws IOException {
        fastCopy(src, dest, DEFAULT_BUFFER_SIZE);
    }

    public static void fastCopy(@NotNull InputStream src, @NotNull OutputStream dest, int bufferSize) throws IOException {
        if (USE_NIO_STREAMS) {
            final ReadableByteChannel inputChannel = Channels.newChannel(src);
            final WritableByteChannel outputChannel = Channels.newChannel(dest);
            fastCopy(inputChannel, outputChannel, bufferSize);
        } else {
            copyStream(src, dest, bufferSize);
        }
    }

    public static void fastCopy(@NotNull ReadableByteChannel src, @NotNull WritableByteChannel dest, int bufferSize) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        while (src.read(buffer) != -1) {
            flipBuffer(buffer);
            dest.write(buffer);
            buffer.compact();
        }

        flipBuffer(buffer);

        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }

    public static void flipBuffer(Buffer buffer) {
        buffer.flip();
    }

    public static void copyStream(
        java.io.InputStream inputStream,
        java.io.OutputStream outputStream
    )
    throws IOException {
        copyStream(inputStream, outputStream, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Read entire input stream and writes all data to output stream
     * then closes input and flushed output
     */
    public static void copyStream(
        @NotNull InputStream inputStream,
        @NotNull OutputStream outputStream,
        int bufferSize
    ) throws IOException {
        try (inputStream) {
            byte[] writeBuffer = new byte[bufferSize];
            for (int br = inputStream.read(writeBuffer); br != -1; br = inputStream.read(writeBuffer)) {
                outputStream.write(writeBuffer, 0, br);
            }
            outputStream.flush();
        }
    }

    /**
     * Read entire reader content and writes it to writer
     * then closes reader and flushed output.
     */
    public static void copyText(
        @NotNull Reader reader,
        @NotNull Writer writer,
        int bufferSize
    ) throws IOException {
        char[] writeBuffer = new char[bufferSize];
        for (int br = reader.read(writeBuffer); br != -1; br = reader.read(writeBuffer)) {
            writer.write(writeBuffer, 0, br);
        }
        writer.flush();
    }

    public static void copyText(
        @NotNull Reader reader,
        @NotNull Writer writer
    ) throws IOException {
        copyText(reader, writer, DEFAULT_BUFFER_SIZE);
    }

    @Nullable
    public static String readLine(@NotNull InputStream input) throws IOException {
        StringBuilder linebuf = new StringBuilder();
        for (int b = input.read(); b != '\n'; b = input.read()) {
            if (b == -1) {
                if (linebuf.isEmpty()) {
                    return null;
                } else {
                    break;
                }
            }
            if (b != '\r') {
                linebuf.append((char) b);
            }
        }
        return linebuf.toString();
    }

    public static int findFreePort(int minPort, int maxPort) {
        int portRange = Math.abs(maxPort - minPort);
        while (true) {
            int portNum = minPort + SecurityUtils.getRandom().nextInt(portRange);
            try {
                ServerSocket socket = new ServerSocket(portNum);
                try {
                    socket.close();
                } catch (IOException e) {
                    // just skip
                }
                return portNum;
            } catch (IOException e) {
                // Port is busy
            }
        }
    }

    @NotNull
    public static String readToString(@NotNull Reader is) throws IOException {
        StringBuilder result = new StringBuilder(4000);
        char[] buffer = new char[4000];
        for (; ; ) {
            int count = is.read(buffer);
            if (count <= 0) {
                break;
            }
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    static void copyZipStream(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
        byte[] writeBuffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        for (int br = inputStream.read(writeBuffer); br != -1; br = inputStream.read(writeBuffer)) {
            outputStream.write(writeBuffer, 0, br);
        }
        outputStream.flush();
    }

    public static void extractZipArchive(InputStream stream, Path targetFolder) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(stream)) {
            for (; ; ) {
                ZipEntry zipEntry = zipStream.getNextEntry();
                if (zipEntry == null) {
                    break;
                }
                try {
                    if (!zipEntry.isDirectory()) {
                        String zipEntryName = zipEntry.getName();
                        checkAndExtractEntry(zipStream, zipEntry, targetFolder);
                    }
                } finally {
                    zipStream.closeEntry();
                }
            }
        }
    }

    private static void checkAndExtractEntry(InputStream zipStream, ZipEntry zipEntry, Path targetFolder) throws IOException {
        if (!Files.exists(targetFolder)) {
            try {
                Files.createDirectories(targetFolder);
            } catch (IOException e) {
                throw new IOException("Can't create local cache folder '" + targetFolder.toAbsolutePath() + "'", e);
            }
        }
        Path localFile = targetFolder.resolve(zipEntry.getName());
        if (!localFile.normalize().startsWith(targetFolder.normalize())) {
            throw new IOException("Zip entry is outside of the target directory");
        }
        if (Files.exists(localFile)) {
            // Already extracted?
            return;
        }
        Path localDir = localFile.getParent();
        if (!Files.exists(localDir)) { // in case of localFile located in subdirectory inside zip archive
            try {
                Files.createDirectories(localDir);
            } catch (IOException e) {
                throw new IOException("Can't create local file directory in the cache '" + localDir.toAbsolutePath() + "'", e);
            }
        }
        try (OutputStream os = Files.newOutputStream(localFile)) {
            copyZipStream(zipStream, os);
        }
    }


    public static void deleteDirectory(@NotNull Path path) throws IOException {
        Files.walkFileTree(
            path,
            new SimpleFileVisitor<>() {
                @NotNull
                @Override
                public FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult visitFile(@NotNull Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            }
        );
    }

    @Nullable
    public static String getDirectoryPath(@NotNull String sPath) throws InvalidPathException {
        final Path path = Paths.get(sPath);
        if (Files.isDirectory(path)) {
            return path.toString();
        } else {
            final Path parent = path.getParent();
            if (parent != null) {
                return parent.toString();
            }
        }
        return null;
    }

    @NotNull
    public static String getFileNameWithoutExtension(@NotNull Path file) {
        return getPathWithoutFileExtension(file.getFileName().toString());
    }

    @NotNull
    public static String getPathWithoutFileExtension(@NotNull String path) {
        int divPos = path.lastIndexOf('.');
        if (divPos > 0) {
            return path.substring(0, divPos);
        }
        return path;
    }

    @Nullable
    public static String getFileExtension(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return null;
        }
        return getFileExtension(fileName.toString());
    }

    @Nullable
    public static String getFileExtension(String fileName) {
        int divPos = fileName.lastIndexOf('.');
        if (divPos != -1) {
            return fileName.substring(divPos + 1);
        }
        return null;
    }

    @NotNull
    public static Path getPathFromString(@NotNull String pathOrUri) {
        if (pathOrUri.contains("://")) {
            return Path.of(URI.create(pathOrUri));
        } else {
            return Path.of(pathOrUri);
        }
    }


    public static boolean isLocalFile(String filePath) {
        // Local paths:
        // rel-path
        // /abs/path
        // \abs\path
        // c:/abs/path
        // c:\abs\path
        int divPos = filePath.indexOf(":/");
        return divPos < 0 || divPos == 1 || filePath.startsWith("file:");
    }

    public static boolean isLocalURI(@NotNull URI uri) {
        return uri.getScheme().equals("file");
    }

    public static boolean isLocalPath(@NotNull Path filePath) {
        return isLocalURI(filePath.toUri());
    }

    public static boolean isFileFromDefaultFS(@NotNull Path path) {
        return path.getFileSystem().equals(FileSystems.getDefault());
    }

    public static boolean isFolderEmpty(@NotNull Path directory) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            return !dirStream.iterator().hasNext();
        }
    }
}
