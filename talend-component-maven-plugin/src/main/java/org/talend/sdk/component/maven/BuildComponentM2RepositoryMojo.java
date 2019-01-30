/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.maven;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;
import static org.talend.sdk.component.maven.api.Audience.Type.TALEND_INTERNAL;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.ziplock.Files;
import org.apache.ziplock.IO;
import org.eclipse.aether.artifact.Artifact;
import org.talend.sdk.component.maven.api.Audience;

@Audience(TALEND_INTERNAL)
@Mojo(name = "prepare-repository", defaultPhase = PACKAGE, threadSafe = true, requiresDependencyResolution = TEST)
public class BuildComponentM2RepositoryMojo extends ComponentDependenciesBase {

    @Parameter(property = "talend-m2.registryBase")
    private File componentRegistryBase;

    @Parameter(property = "talend-m2.root",
            defaultValue = "${maven.multiModuleProjectDirectory}/target/talend-component-kit/maven")
    protected File m2Root;

    @Parameter(property = "talend-m2.clean", defaultValue = "true")
    private boolean cleanBeforeGeneration;

    @Parameter(defaultValue = "component", property = "talend.car.classifier")
    private String classifier;

    @Override
    public void doExecute() throws MojoExecutionException {
        final Set<Artifact> componentArtifacts = getComponentsCar(getComponentArtifacts());

        if (cleanBeforeGeneration && m2Root.exists()) {
            Files.remove(m2Root);
        }
        m2Root.mkdirs();
        final List<String> coordinates = componentArtifacts
                .stream()
                .map(car -> copyComponentDependencies(car,
                        (entry, read) -> copyFile(entry, read,
                                entry.getName().substring("MAVEN-INF/repository/".length()))))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(toList());

        if (getLog().isDebugEnabled()) {
            coordinates.forEach(it -> getLog().debug("Including component " + it));
        } else {
            getLog().info("Included components " + String.join(", ", coordinates));
        }

        writeRegistry(getNewComponentRegistry(coordinates));

        getLog().info("Created component repository at " + m2Root);
    }

    protected void writeRegistry(final Properties components) {
        try (final Writer output = new FileWriter(getRegistry())) {
            components.store(output, "Generated by Talend Component Kit " + getClass().getSimpleName());
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected File copyFile(final ZipEntry entry, final InputStream read, final String depPath) {
        final File file = new File(m2Root, depPath);
        Files.mkdir(file.getParentFile());
        try {
            IO.copy(read, file);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        final long lastModified = entry.getTime();
        if (lastModified > 0) {
            file.setLastModified(lastModified);
        }
        return file;
    }

    protected Properties getNewComponentRegistry(final List<String> coordinates) {
        final Properties components = new Properties();
        if (componentRegistryBase != null && componentRegistryBase.exists()) {
            try (final InputStream source = new FileInputStream(componentRegistryBase)) {
                components.load(source);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        coordinates.stream().filter(it -> it.contains(":")).forEach(it -> components.put(it.split(":")[1], it.trim()));
        return components;
    }

    protected String copyComponentDependencies(final Artifact car,
            final BiConsumer<ZipEntry, InputStream> onDependency) {
        String gav = null;
        try (final ZipInputStream read =
                new ZipInputStream(new BufferedInputStream(new FileInputStream(car.getFile())))) {
            ZipEntry entry;
            while ((entry = read.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                final String path = entry.getName();
                if ("TALEND-INF/metadata.properties".equals(path)) {
                    final Properties properties = new Properties();
                    properties.load(read);
                    gav = properties.getProperty("component_coordinates").replace("\\:", "");
                    continue;
                }
                if (!path.startsWith("MAVEN-INF/repository/")) {
                    continue;
                }

                onDependency.accept(entry, read);
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
        return gav;
    }

    protected Set<Artifact> getComponentsCar(final Set<Artifact> artifacts) {
        return artifacts.stream().map(art -> resolve(art, classifier, "car")).collect(toSet());
    }

    protected Set<Artifact> getComponentArtifacts() {
        return getArtifacts(it -> {
            try (final JarFile file = new JarFile(it.getFile())) { // filter components with this marker
                return ofNullable(file.getEntry("TALEND-INF/dependencies.txt")).map(ok -> it).orElse(null);
            } catch (final IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(toSet());
    }

    protected File getRegistry() {
        return new File(m2Root, "component-registry.properties");
    }
}
