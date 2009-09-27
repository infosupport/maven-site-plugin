package org.apache.maven.plugins.site;

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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultRepositoryRequest;
import org.apache.maven.artifact.repository.RepositoryRequest;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;

/**
 *  
 * @author Olivier Lamy
 * @since 3.0-beta-1
 */
@Component(role=MavenReportExecutor.class)
public class DefaultMavenReportExecutor
    implements MavenReportExecutor
{

    @Requirement
    private Logger logger;

    @Requirement
    protected MavenPluginManager mavenPluginManager;

    @Requirement
    protected LifecycleExecutor lifecycleExecutor;

    public List<MavenReportExecution> buildMavenReports( MavenReportExecutorRequest mavenReportExecutorRequest )
        throws MojoExecutionException
    {

        List<String> imports = new ArrayList<String>();

        imports.add( "org.apache.maven.reporting.MavenReport" );
        imports.add( "org.apache.maven.doxia.siterenderer.Renderer" );
        imports.add( "org.apache.maven.doxia.sink.SinkFactory" );
        imports.add( "org.codehaus.doxia.sink.Sink" );
        imports.add( "org.apache.maven.doxia.sink.Sink" );
        imports.add( "org.apache.maven.doxia.sink.SinkEventAttributes" );

        RepositoryRequest repositoryRequest = new DefaultRepositoryRequest();
        repositoryRequest.setLocalRepository( mavenReportExecutorRequest.getLocalRepository() );
        
        List<ArtifactRepository> remoteRepositories =
            new ArrayList<ArtifactRepository>( mavenReportExecutorRequest.getProject().getRemoteArtifactRepositories() );
        remoteRepositories.addAll( mavenReportExecutorRequest.getProject().getPluginArtifactRepositories() );
        repositoryRequest.setRemoteRepositories( remoteRepositories );

        try
        {

            List<MavenReportExecution> reports = new ArrayList<MavenReportExecution>();

            for ( ReportPlugin reportPlugin : mavenReportExecutorRequest.getProject().getReporting().getPlugins() )
            {
                
                Plugin plugin = new Plugin();
                plugin.setGroupId( reportPlugin.getGroupId() );
                plugin.setArtifactId( reportPlugin.getArtifactId() );
                plugin.setVersion( reportPlugin.getVersion() );

                if (logger.isInfoEnabled())
                {
                    logger.info( "configuring reportPlugin " + plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion() );
                }
                
                List<String> goals = new ArrayList<String>();

                PluginDescriptor pluginDescriptor = mavenPluginManager.getPluginDescriptor( plugin, repositoryRequest );
                
                if ( reportPlugin.getReportSets().isEmpty() )
                {
                    List<MojoDescriptor> mojoDescriptors = pluginDescriptor.getMojos();
                    for ( MojoDescriptor mojoDescriptor : mojoDescriptors )
                    {
                        goals.add( mojoDescriptor.getGoal() );
                    }
                }
                else
                {
                    for ( ReportSet reportSet : reportPlugin.getReportSets() )
                    {
                        goals.addAll( reportSet.getReports() );
                    }
                }

                for ( String goal : goals )
                {
                    MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo( goal );
                    if ( mojoDescriptor == null )
                    {
                        throw new MojoNotFoundException( goal, pluginDescriptor );
                    }

                    MojoExecution mojoExecution = new MojoExecution( plugin, goal, "report:" + goal );

                    mojoExecution.setConfiguration( convert( mojoDescriptor ) );

                    mojoExecution.setMojoDescriptor( mojoDescriptor );

                    mavenPluginManager.setupPluginRealm( pluginDescriptor,
                                                         mavenReportExecutorRequest.getMavenSession(),
                                                         Thread.currentThread().getContextClassLoader(), imports );

                    MavenReport mavenReport =
                        getConfiguredMavenReport( mojoExecution, pluginDescriptor, mavenReportExecutorRequest );

                    if ( mavenReport == null )
                    {
                        continue;
                    }

                    if ( reportPlugin.getConfiguration() != null )
                    {

                        Xpp3Dom mergedConfiguration =
                            Xpp3DomUtils.mergeXpp3Dom( (Xpp3Dom) reportPlugin.getConfiguration(),
                                                       convert( mojoDescriptor ) );
                        
                        Xpp3Dom cleanedConfiguration = new Xpp3Dom( "configuration" );
                        if ( mergedConfiguration.getChildren() != null )
                        {
                            for ( int i = 0, size = mergedConfiguration.getChildren().length; i < size; i++ )
                            {
                                if ( mojoDescriptor.getParameterMap().containsKey(
                                                                                   mergedConfiguration.getChildren()[i].getName() ) )
                                {
                                    cleanedConfiguration.addChild( mergedConfiguration.getChildren()[i] );
                                }
                            }
                        }
                        if ( getLog().isDebugEnabled() )
                        {
                            getLog().debug( "mojoExecution mergedConfiguration " + mergedConfiguration );
                            getLog().debug( "mojoExecution cleanedConfiguration " + cleanedConfiguration );
                        }
                       
                        mojoExecution.setConfiguration( cleanedConfiguration );
                    }

                    mavenReport =
                        getConfiguredMavenReport( mojoExecution, pluginDescriptor, mavenReportExecutorRequest );
                    if ( mavenReport != null )
                    {

                        MavenReportExecution mavenReportExecution =
                            new MavenReportExecution( mavenReport, pluginDescriptor.getClassRealm() );

                        lifecycleExecutor.calculateForkedExecutions( mojoExecution,
                                                                     mavenReportExecutorRequest.getMavenSession() );
                        if ( !mojoExecution.getForkedExecutions().isEmpty() )
                        {
                            lifecycleExecutor.executeForkedExecutions( mojoExecution,
                                                                       mavenReportExecutorRequest.getMavenSession() );
                        }
                        if ( canGenerateReport( mavenReport ) )
                        {
                            reports.add( mavenReportExecution );
                        }
                    }
                }
            }
            return reports;
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "failed to get Reports ", e );
        }
    }

    private boolean canGenerateReport(MavenReport mavenReport)
    {
     
        try
        {
            return mavenReport.canGenerateReport();
        }
        catch ( AbstractMethodError e )
        {
            // the canGenerateReport() has been added just before the 2.0 release and will cause all the reporting
            // plugins with an earlier version to fail (most of the org.codehaus mojo now fails)
            // be nice with them, output a warning and don't let them break anything

            getLog().warn(
                           "Error loading report " + mavenReport.getClass().getName()
                               + " - AbstractMethodError: canGenerateReport()" );
            return true;
        }        
        
    }
    
    private MavenReport getConfiguredMavenReport( MojoExecution mojoExecution, PluginDescriptor pluginDescriptor,
                                                  MavenReportExecutorRequest mavenReportExecutorRequest )
        throws PluginContainerException, PluginConfigurationException

    {

        MavenReport mavenReport = null;
        try
        {
            Mojo mojo = mavenPluginManager.getConfiguredMojo( Mojo.class,
                                                              mavenReportExecutorRequest.getMavenSession(),
                                                              mojoExecution );
            if ( !isMavenReport( mojoExecution, pluginDescriptor, mojo ) )
            {
                return null;
            }
            mavenReport = (MavenReport) mojo; 
            return mavenReport;

        }
        catch ( ClassCastException e )
        {
            getLog().warn( "skip ClassCastException " + e.getMessage() );
            return null;
        }

    }

    private boolean isMavenReport( MojoExecution mojoExecution, PluginDescriptor pluginDescriptor, Mojo mojo )
    {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo( mojoExecution.getGoal() );
            Thread.currentThread().setContextClassLoader( mojoDescriptor.getRealm() );
            
            boolean isMavenReport = MavenReport.class.isAssignableFrom( mojo.getClass() );
            if (getLog().isInfoEnabled())
            {
                getLog().info( "class " + mojoDescriptor.getImplementationClass().getName() + " isMavenReport " + isMavenReport );
            }
            if ( !isMavenReport )
            {
                getLog().info( " skip non MavenReport " + mojoExecution.getMojoDescriptor().getId() );
            }
            return isMavenReport;
        }
        catch ( LinkageError e )
        {
            getLog().warn(
                           "skip LinkageError mojoExecution.goal : " + mojoExecution.getGoal() + " : " + e.getMessage(),
                           e );
            return false;
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }

    }

    private Xpp3Dom convert( MojoDescriptor mojoDescriptor )
    {
        Xpp3Dom dom = new Xpp3Dom( "configuration" );

        PlexusConfiguration c = mojoDescriptor.getMojoConfiguration();

        PlexusConfiguration[] ces = c.getChildren();

        if ( ces != null )
        {
            for ( PlexusConfiguration ce : ces )
            {
                String value = ce.getValue( null );
                String defaultValue = ce.getAttribute( "default-value", null );
                if ( value != null || defaultValue != null )
                {
                    Xpp3Dom e = new Xpp3Dom( ce.getName() );
                    e.setValue( value );
                    if ( defaultValue != null )
                    {
                        e.setAttribute( "default-value", defaultValue );
                    }
                    dom.addChild( e );
                }
            }
        }

        return dom;
    }

    private Logger getLog()
    {
        return logger;
    }
}