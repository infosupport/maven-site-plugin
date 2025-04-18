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
package org.apache.maven.plugins.site.descriptor;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.site.inheritance.SiteModelInheritanceAssembler;
import org.apache.maven.doxia.site.io.xpp3.SiteXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlStreamWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;

/**
 * Displays the effective site descriptor as an XML for this build, after inheritance and interpolation of
 * <code>site.xml</code>, for the first locale.
 *
 * @author <a href="mailto:hboutemy@apache.org">Hervé Boutemy</a>
 *
 * @since 2.2
 */
@Mojo(name = "effective-site", requiresReports = true)
public class EffectiveSiteMojo extends AbstractSiteDescriptorMojo {
    /**
     * Optional parameter to write the output of this help in a given file, instead of writing to the console.
     * <p>
     * <b>Note</b>: Could be a relative path.
     * </p>
     */
    @Parameter(property = "output")
    protected File output;

    @Inject
    public EffectiveSiteMojo(SiteModelInheritanceAssembler assembler) {
        super(assembler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        SiteModel siteModel = prepareSiteModel(getLocales().get(0));

        StringWriter w = new StringWriter();
        XMLWriter writer = new PrettyPrintXMLWriter(
                w, StringUtils.repeat(" ", XmlWriterUtil.DEFAULT_INDENTATION_SIZE), siteModel.getModelEncoding(), null);

        writeHeader(writer);

        writeEffectiveSite(siteModel, writer);

        String effectiveSite = w.toString();

        if (output != null) {
            try {
                writeXmlFile(output, effectiveSite);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot write effective site descriptor to output: " + output, e);
            }

            if (getLog().isInfoEnabled()) {
                getLog().info("Effective site descriptor written to: " + output);
            }
        } else {
            StringBuilder message = new StringBuilder();

            message.append("\nEffective site descriptor, after inheritance and interpolation:\n\n");
            message.append(effectiveSite);
            message.append("\n");

            if (getLog().isInfoEnabled()) {
                getLog().info(message.toString());
            }
        }
    }

    /**
     * Write comments in the Effective POM/settings header.
     *
     * @param writer not null
     */
    protected static void writeHeader(XMLWriter writer) {
        XmlWriterUtil.writeCommentLineBreak(writer);
        XmlWriterUtil.writeComment(writer, " ");
        XmlWriterUtil.writeComment(writer, "Generated by Maven Site Plugin");
        XmlWriterUtil.writeComment(writer, "See: https://maven.apache.org/plugins/maven-site-plugin/");
        XmlWriterUtil.writeComment(writer, " ");
        XmlWriterUtil.writeCommentLineBreak(writer);

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Write comments in a normalize way.
     *
     * @param writer not null
     * @param comment not null
     */
    protected static void writeComment(XMLWriter writer, String comment) {
        XmlWriterUtil.writeCommentLineBreak(writer);
        XmlWriterUtil.writeComment(writer, " ");
        XmlWriterUtil.writeComment(writer, comment);
        XmlWriterUtil.writeComment(writer, " ");
        XmlWriterUtil.writeCommentLineBreak(writer);

        XmlWriterUtil.writeLineBreak(writer);
    }

    private void writeEffectiveSite(SiteModel siteModel, XMLWriter writer) throws MojoExecutionException {
        String effectiveSite;

        StringWriter sWriter = new StringWriter();
        SiteXpp3Writer siteWriter = new SiteXpp3Writer();
        try {
            siteWriter.write(sWriter, siteModel);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot serialize site descriptor to XML", e);
        }

        effectiveSite = sWriter.toString();
        // remove XML prolog
        int xmlPrologStart = effectiveSite.indexOf("<?xml");
        int xmlPrologEnd = effectiveSite.indexOf("?>", xmlPrologStart);
        effectiveSite = effectiveSite.substring(xmlPrologEnd + 2).trim();

        writeComment(writer, "Effective site descriptor for project \'" + project.getId() + "\'");

        writer.writeMarkup(effectiveSite);
    }

    protected static void writeXmlFile(File output, String content) throws IOException {
        try (Writer out = new XmlStreamWriter(output)) {
            output.getParentFile().mkdirs();

            out.write(content);
        }
    }
}
