/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package com.dbeaver.maven.p2collector;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Mojo(
    name = "copy-dependencies",
    defaultPhase = LifecyclePhase.PACKAGE,
    threadSafe = true
)
public class P2Collector extends AbstractMojo {
    private static final ExecutorService POOL = Executors.newWorkStealingPool();
    private static final String P2_DEPENDENCIES_FILE = "skippedP2Dependencies.txt";
    private static final String P2_DEPENDENCIES_DIRECTORY = "p2-dependencies";
    private static final String DIST_DIRECTORY = "dist";

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter
    private List<String> excludes;

    private final List<PathMatcher> excludeMatchers = new ArrayList<>();

    @Override
    public void execute() throws MojoExecutionException {
        if (excludes != null) {
            excludes.stream()
                .map(it -> FileSystems.getDefault().getPathMatcher("glob:" + it))
                .forEach(excludeMatchers::add);
        }

        try {
            copyP2Dependencies();
        } catch (Exception e) {
            throw new MojoExecutionException("Error copying P2 dependencies", e);
        }
    }

    private void copyP2Dependencies() throws Exception {
        Path skippedP2Dependencies = buildDirectory().resolve(P2_DEPENDENCIES_FILE);
        if (!Files.exists(skippedP2Dependencies)) {
            getLog().warn("P2 dependencies file not found: " + skippedP2Dependencies);
            return;
        }

        Path outputDirectory = buildDirectory().resolve(P2_DEPENDENCIES_DIRECTORY);
        Files.createDirectories(outputDirectory);

        Path distDirectory = buildDirectory().resolve(DIST_DIRECTORY);
        Files.createDirectories(distDirectory);

        Future<?> future = POOL.submit(() -> {
            parseSkippedP2DependenciesFile(skippedP2Dependencies).stream()
                .parallel()
                .filter(it -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(it.getFileName())))
                .peek(it -> copy(
                    it,
                    distDirectory.resolve(it.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING
                ))
                .forEach(it -> extractJar(it, outputDirectory));
        });

        future.get();
    }

    private Path buildDirectory() {
        return new File(project.getBuild().getDirectory()).toPath();
    }

    private static List<Path> parseSkippedP2DependenciesFile(Path path) {
        try {
            return Files.readAllLines(path)
                .stream()
                .map(it -> it.split("@"))
                .filter(it -> it.length == 2)
                .map(it -> it[1].trim())
                .distinct()
                .map(Path::of)
                .toList();
        } catch (IOException e) {
            throw new IORuntimeException("Error reading P2 dependencies file " + path + " " + e.getMessage());
        }
    }

    private static void extractJar(Path jarPath, Path target) {
        // Open the main JAR as a stream
        try (InputStream fileStream = Files.newInputStream(jarPath);
             JarInputStream jarInputStream = new JarInputStream(fileStream)) {

            // Recursively extract contents
            extractEntriesRecursively(jarInputStream, target);
        } catch (IOException e) {
            throw new IORuntimeException("Error extracting JAR " + jarPath + " " + e.getMessage());
        }
    }

    private static void extractEntriesRecursively(JarInputStream jarInputStream, Path target) throws IOException {
        JarEntry entry;
        while ((entry = jarInputStream.getNextJarEntry()) != null) {
            String entryName = entry.getName();
            Path outputPath = target.resolve(entryName);

            if (entry.isDirectory()) {
                // Create the folder
                Files.createDirectories(outputPath);
            } else if (entryName.toLowerCase().endsWith(".jar")) {
                // 1) Read nested JAR bytes into memory
                ByteArrayOutputStream jarBuffer = new ByteArrayOutputStream();
                jarInputStream.transferTo(jarBuffer);

                // 2) Wrap bytes in a new JarInputStream
                try (JarInputStream nestedJarInputStream =
                         new JarInputStream(new ByteArrayInputStream(jarBuffer.toByteArray()))) {

                    // 3) Recursively extract the nested JAR
                    extractEntriesRecursively(nestedJarInputStream, target);
                }

            } else {
                // Ordinary file: create parent directory (in case it's nested) and copy
                Files.createDirectories(outputPath.getParent());
                try (OutputStream fos = Files.newOutputStream(outputPath)) {
                    jarInputStream.transferTo(fos);
                }
            }

            // Close the current entry before moving on
            jarInputStream.closeEntry();
        }
    }

    private static void copy(
        Path source,
        Path target,
        CopyOption... options
    ) {
        try {
            Files.copy(source, target, options);
        } catch (IOException e) {
            throw new IORuntimeException(e.getMessage(), e);
        }
    }
}
