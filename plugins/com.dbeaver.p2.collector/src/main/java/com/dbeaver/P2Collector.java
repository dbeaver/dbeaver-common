package com.dbeaver;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@Mojo(
    name = "copy-dependencies",
    defaultPhase = LifecyclePhase.PACKAGE,
    threadSafe = true
)
public class P2Collector extends AbstractMojo {
    private static final String P2_DEPENDENCIES_FILE = "skippedP2Dependencies.txt";
    private static final String P2_DEPENDENCIES_DIRECTORY = "p2-dependencies";

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter
    private List<String> excludes;

    private final Set<Path> copiedDependencies = new HashSet<>();
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

        parseSkippedP2DependenciesFile(skippedP2Dependencies).stream()
            .filter(it -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(it.getFileName())))
            .filter(it -> {
                if (!Files.exists(it)) {
                    throw new IORuntimeException("File " + it + " does not exist");
                } else if (Files.isDirectory(it)) {
                    throw new IORuntimeException("Directory " + it + " found");
                }
                return true;
            })
            .flatMap(it -> {
                if (it.getFileName().toString().startsWith("org.jkiss.bundle.")) {
                    return getBundleLibs(it).stream();
                } else {
                    return Stream.of(it);
                }
            })
            .forEach(it -> copyDependency(it, outputDirectory));
    }


    private void copyDependency(Path dependency, Path target) {
        if (copiedDependencies.contains(dependency)) {
            return;
        }

        try {
            extractJar(dependency, target);
            copiedDependencies.add(dependency);
        } catch (IOException e) {
            throw new IORuntimeException("Error copying dependency " + dependency + " " + e.getMessage());
        }
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
                .map(Path::of)
                .toList();
        } catch (IOException e) {
            throw new IORuntimeException("Error reading P2 dependencies file " + path + " " + e.getMessage());
        }
    }


    private static List<Path> getBundleLibs(Path path) {
        try {
            Path jarDirectory = Files.createTempDirectory(path.getFileName() + "-extracted");
            extractJar(path, jarDirectory);
            Path libs = jarDirectory.resolve("lib");

            if (Files.exists(libs)) {
                try (Stream<Path> stream = Files.list(libs)) {
                    return stream.toList();
                }
            } else {
                return List.of();
            }
        } catch (IOException e) {
            throw new IORuntimeException("Error extracting JAR " + path + " " + e.getMessage());
        }
    }

    private static void extractJar(Path path, Path target) throws IOException {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            jarFile.stream().forEach(entry -> {
                Path outputPath = target.resolve(entry.getName());
                try {
                    if (entry.isDirectory()) {
                        Files.createDirectories(outputPath);
                    } else {
                        Files.createDirectories(outputPath.getParent());

                        try (InputStream inputStream = jarFile.getInputStream(entry)) {
                            Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException e) {
                    throw new IORuntimeException(
                        "Error extracting file " + entry.getName() + " from " + path + ": " + e.getMessage()
                    );
                }
            });
        }
    }
}
