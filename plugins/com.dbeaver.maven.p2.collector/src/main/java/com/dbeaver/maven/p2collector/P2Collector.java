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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Mojo(
    name = "copy-dependencies",
    defaultPhase = LifecyclePhase.PACKAGE,
    threadSafe = true
)
public class P2Collector extends AbstractMojo {
    private static final String P2_DEPENDENCIES_FILE = "skippedP2Dependencies.txt";
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

        Path distDirectory = buildDirectory().resolve(DIST_DIRECTORY);
        Files.createDirectories(distDirectory);

        parseSkippedP2DependenciesFile(skippedP2Dependencies).stream()
            .filter(it -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(it.getFileName())))
            .forEach(it -> copyJar(it, distDirectory));
    }

    private Path buildDirectory() {
        return new File(project.getBuild().getDirectory()).toPath();
    }

    private static List<Path> parseSkippedP2DependenciesFile(Path path) throws IOException {
        return Files.readAllLines(path)
            .stream()
            .map(it -> it.split("@"))
            .filter(it -> it.length == 2)
            .map(it -> it[1].trim())
            .distinct()
            .map(Path::of)
            .toList();
    }

    private static void copyJar(Path jarPath, Path toFolder) {
        try {
            if (jarPath.getFileName().toString().startsWith("org.jkiss.bundle")) {
                extractBundle(jarPath, toFolder);
            } else {
                Path jarTargetPath = toFolder.resolve(jarPath.getFileName());
                if (!Files.exists(jarTargetPath)) {
                    Files.copy(jarPath, jarTargetPath);
                }
            }
        } catch (IOException e) {
            throw new IORuntimeException("Failed to copy " + jarPath, e);
        }
    }

    private static void extractBundle(Path bundlePath, Path target) throws IOException {
        try (JarFile bundle = new JarFile(bundlePath.toFile())) {
            for (JarEntry entry : Collections.list(bundle.entries())) {
                if (entry.getName().startsWith("lib/") && entry.getName().endsWith(".jar")) {
                    try (InputStream is = bundle.getInputStream(entry);
                         OutputStream os = Files.newOutputStream(target.resolve(entry.getName().substring("lib/".length())))) {
                        is.transferTo(os);
                    }
                }
            }
        }
    }
}
