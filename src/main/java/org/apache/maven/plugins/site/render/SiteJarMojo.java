/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.site.render;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.doxia.site.inheritance.SiteModelInheritanceAssembler;
import org.apache.maven.doxia.siterenderer.SiteRenderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.reporting.exec.MavenReportExecutor;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;

/**
 * Bundles the site output into a JAR so that it can be deployed to a repository.
 *
 * @author <a href="mailto:mbeerman@yahoo.com">Matthew Beermann</a>
 *
 * @since 2.0-beta-6
 */
// MSITE-665: requiresDependencyResolution workaround for MPLUGIN-253
@Mojo(
        name = "jar",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.TEST,
        requiresReports = true,
        threadSafe = true)
public class SiteJarMojo extends SiteMojo {
    private static final String[] DEFAULT_ARCHIVE_EXCLUDES = new String[] {};

    private static final String[] DEFAULT_ARCHIVE_INCLUDES = new String[] {"**/**"};

    /**
     * Specifies the directory where the generated jar file will be put.
     */
    @Parameter(property = "project.build.directory", required = true)
    private String jarOutputDirectory;

    /**
     * Specifies the filename that will be used for the generated jar file.
     * Please note that "-site" will be appended to the file name.
     */
    @Parameter(property = "project.build.finalName", required = true)
    private String finalName;

    /**
     * Specifies whether to attach the generated artifact to the project.
     */
    @Parameter(property = "site.attach", defaultValue = "true")
    private boolean attach;

    /**
     * The archive configuration to use.
     * See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
     *
     * @since 3.1
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * List of files to include. Specified as file set patterns which are relative to the input directory whose contents
     * is being packaged into the JAR.
     *
     * @since 3.1
     */
    @Parameter
    private String[] archiveIncludes;

    /**
     * List of files to exclude. Specified as file set patterns which are relative to the input directory whose contents
     * is being packaged into the JAR.
     *
     * @since 3.1
     */
    @Parameter
    private String[] archiveExcludes;

    /**
     * Used for attaching the artifact in the project.
     */
    private final MavenProjectHelper projectHelper;

    /**
     * The Jar archiver.
     *
     * @since 3.1
     */
    private final JarArchiver jarArchiver;

    @Inject
    public SiteJarMojo(
            SiteModelInheritanceAssembler assembler,
            SiteRenderer siteRenderer,
            MavenReportExecutor mavenReportExecutor,
            MavenProjectHelper projectHelper,
            JarArchiver jarArchiver) {
        super(assembler, siteRenderer, mavenReportExecutor);
        this.projectHelper = projectHelper;
        this.jarArchiver = jarArchiver;
    }

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("maven.site.skip = true: Skipping jar generation");
            return;
        }

        super.execute();

        try {
            File outputFile =
                    createArchive(outputDirectory, finalName + "-" + getClassifier() + "." + getArtifactType());

            if (attach) {
                projectHelper.attachArtifact(project, getArtifactType(), getClassifier(), outputFile);
            } else {
                getLog().info("NOT adding site jar to the list of attached artifacts.");
            }
        } catch (ArchiverException | IOException | ManifestException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error while creating archive", e);
        }
    }

    protected String getArtifactType() {
        return "jar";
    }

    protected String getClassifier() {
        return "site";
    }

    /**
     * Method that creates the jar file.
     *
     * @param siteDirectory the directory where the site files are located
     * @param jarFilename   the filename of the created jar file
     * @return a File object that contains the created jar file
     * @throws ArchiverException
     * @throws IOException
     * @throws ManifestException
     * @throws DependencyResolutionRequiredException
     */
    private File createArchive(File siteDirectory, String jarFilename)
            throws ArchiverException, IOException, ManifestException, DependencyResolutionRequiredException {
        File siteJar = new File(jarOutputDirectory, jarFilename);

        MavenArchiver archiver = new MavenArchiver();
        archiver.setCreatedBy("Maven Site Plugin", "org.apache.maven.plugins", "maven-site-plugin");

        archiver.setArchiver(this.jarArchiver);

        archiver.setOutputFile(siteJar);

        // configure for Reproducible Builds based on outputTimestamp value
        archiver.configureReproducibleBuild(outputTimestamp);

        if (!siteDirectory.isDirectory()) {
            getLog().warn("JAR will be empty - no content was marked for inclusion!");
        } else {
            archiver.getArchiver().addDirectory(siteDirectory, getArchiveIncludes(), getArchiveExcludes());
        }

        archiver.createArchive(getSession(), getProject(), archive);

        return siteJar;
    }

    private String[] getArchiveIncludes() {
        if (this.archiveIncludes != null && this.archiveIncludes.length > 0) {
            return this.archiveIncludes;
        }

        return DEFAULT_ARCHIVE_INCLUDES;
    }

    private String[] getArchiveExcludes() {
        if (this.archiveExcludes != null && this.archiveExcludes.length > 0) {
            return this.archiveExcludes;
        }
        return DEFAULT_ARCHIVE_EXCLUDES;
    }
}
