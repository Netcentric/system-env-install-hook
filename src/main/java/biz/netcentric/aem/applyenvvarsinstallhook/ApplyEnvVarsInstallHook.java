/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applyenvvarsinstallhook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies environment variables to content in package - this works for both text files and properties of nodes.
 * 
 * See README.md file for details. */
public class ApplyEnvVarsInstallHook implements InstallHook {

    private static final String TEMPLATE_SUFFIX = ".TEMPLATE";
    private static final Logger LOG = LoggerFactory.getLogger(ApplyEnvVarsInstallHook.class);

    @Override
    public void execute(InstallContext context) throws PackageException {
        try {

            final Session session = context.getSession();
            ImportOptions options = context.getOptions();
            VaultPackage vaultPackage = context.getPackage();
            WorkspaceFilter filter = vaultPackage.getMetaInf().getFilter();

            Map<String, String> env = System.getenv();

            switch (context.getPhase()) {
                case INSTALLED:

                log("Package=" + vaultPackage, options);
                String applyEnvVarsForPaths = vaultPackage.getProperties().getProperty("applyEnvVarsForPaths");
                LOG.debug("applyEnvVarsForPaths={}", applyEnvVarsForPaths);
                if (StringUtils.isBlank(applyEnvVarsForPaths)) {
                    log("Install Hook " + getClass().getName()
                            + " was configured but package property 'applyEnvVarsForPaths' was left blank", options);
                    return;
                }
                List<String> jcrPathsToBeAdjusted = new ArrayList<String>(Arrays.asList(applyEnvVarsForPaths.trim().split("[\\s*,]+")));

                collectTemplateNodes(vaultPackage, session, jcrPathsToBeAdjusted);

                for (String jcrPathToBeAdjusted : jcrPathsToBeAdjusted) {

                    log("Adjusting " + jcrPathToBeAdjusted + "...", options);

                    if (jcrPathToBeAdjusted.contains("@")) {
                        String[] pathAndProperty = jcrPathToBeAdjusted.split("@", 2);
                        String path = pathAndProperty[0];
                        if (isNotCoveredbyFilter(filter, path, options)) {
                            continue;
                        }

                        String propertyName = pathAndProperty[1];

                        adjustProperty(session, path, propertyName, env, options);
                    } else {
                        if (isNotCoveredbyFilter(filter, jcrPathToBeAdjusted, options)) {
                            continue;
                        }
                        
                        Node nodeToBeAdjusted = session.getNode(jcrPathToBeAdjusted);
                        
                        if (isFile(nodeToBeAdjusted)) {
                            String fileContent = IOUtils.toString(JcrUtils.readFile(nodeToBeAdjusted));

                            String adjustedFileContent = applyEnvVars(fileContent, env);

                            String targetNodeName;
                            if (isTemplateNode(nodeToBeAdjusted)) {
                                targetNodeName = StringUtils.substringBeforeLast(nodeToBeAdjusted.getName(), TEMPLATE_SUFFIX);
                            } else {
                                targetNodeName = nodeToBeAdjusted.getName();
                            }

                            // only used to derive encoding
                            String mimeType = "text/plain";
                            JcrUtils.putFile(nodeToBeAdjusted.getParent(), targetNodeName, mimeType,
                                    new ByteArrayInputStream(adjustedFileContent.getBytes()));
                        } else {

                            if (isTemplateNode(nodeToBeAdjusted)) {
                                String targetPath = StringUtils.substringBeforeLast(nodeToBeAdjusted.getPath(), TEMPLATE_SUFFIX);
                                // if (session.itemExists(targetPath)) {
                                // session.removeItem(targetPath);
                                // }
                                nodeToBeAdjusted = copy(nodeToBeAdjusted, targetPath);
                            }

                            // adjust all string properties of node
                            adjustAllPropertiesOfNodeTree(nodeToBeAdjusted, env, options);
                        }
                    }
                    
                }

                log("Saving session...", options);
                session.save();
                log(getClass().getSimpleName() + " done. ", options);

                break;
            default:
                break;
            }
        } catch (RepositoryException | IOException e) {
            throw new PackageException("Could not execute install hook to apply env vars: " + e, e);
        }
    }


    // not using workspace.copy() because that method saves immediately
    private Node copy(Node sourceNode, String targetPath) throws RepositoryException {
        LOG.info("Copy {} to {}", sourceNode, targetPath);
        Node targetNode = JcrUtils.getOrCreateByPath(targetPath, sourceNode.getPrimaryNodeType().getName(), sourceNode.getSession());
        PropertyIterator propertiesIt = sourceNode.getProperties();
        while(propertiesIt.hasNext()) {
            Property sourceProp = propertiesIt.nextProperty();
            if (sourceProp.getDefinition().isProtected()) {
                continue;
            }
            if (sourceProp.isMultiple()) {
                targetNode.setProperty(sourceProp.getName(), sourceProp.getValues(), sourceProp.getType());
            } else {
                targetNode.setProperty(sourceProp.getName(), sourceProp.getValue(), sourceProp.getType());
            }
            LOG.info("Copied {} / {} to {}", sourceProp.getName(), sourceProp.getType(), targetNode);

        }
        NodeIterator nodesIt = sourceNode.getNodes();
        while (nodesIt.hasNext()) {
            Node childNode = nodesIt.nextNode();
            copy(childNode, targetPath + "/" + childNode.getName());
        }
        return targetNode;
    }

    private boolean isTemplateNode(Node nodeToBeAdjusted) throws RepositoryException {
        return nodeToBeAdjusted.getPath().endsWith(TEMPLATE_SUFFIX);
    }

    private boolean isFile(Node node) throws RepositoryException {
        Node relevantNode = node;
        if (relevantNode.hasNode(JcrConstants.JCR_CONTENT)) {
            relevantNode = node.getNode(JcrConstants.JCR_CONTENT);
        }
        boolean isFile = relevantNode.hasProperty(JcrConstants.JCR_DATA);
        return isFile;
    }

    private void adjustAllPropertiesOfNodeTree(Node node, Map<String, String> env, ImportOptions options)
            throws RepositoryException {
        PropertyIterator propertiesIt = node.getProperties();
        while (propertiesIt.hasNext()) {
            Property prop = propertiesIt.nextProperty();
            if (prop.getType() == PropertyType.STRING) {
                adjustProperty(node.getSession(), node.getPath(), prop.getName(), env, options);
            }
        }
        NodeIterator nodesIt = node.getNodes();
        while (nodesIt.hasNext()) {
            Node childNode = nodesIt.nextNode();
            adjustAllPropertiesOfNodeTree(childNode, env, options);
        }
    }

    private void adjustProperty(Session session, String path, String propertyName, Map<String, String> env, ImportOptions options)
            throws RepositoryException {
        try {
            LOG.info("Adjusting path {} prop {}", path, propertyName);
            Node node = session.getNode(path);
            Property property = node.getProperty(propertyName);
            if (property.getDefinition().isProtected()) {
                return;
            }
            if (property.getType() != PropertyType.STRING) {
                log("Property " + path + "@" + propertyName + " is not of type String", options);
                return;
            }
            String stringValueRaw = property.getString();

            String adjustedValue = applyEnvVars(stringValueRaw, env);
            property.setValue(adjustedValue);
        } catch (PathNotFoundException e) {
            log("Path " + path + " could not be found", options);
        }
    }

    private void collectTemplateNodes(VaultPackage vaultPackage, Session session, List<String> jcrPathsToBeAdjusted)
            throws RepositoryException {
        
        WorkspaceFilter workspaceFilter = vaultPackage.getMetaInf().getFilter();
        List<PathFilterSet> filterSets = workspaceFilter.getFilterSets();
        
        for(PathFilterSet filterSet: filterSets) {
            String filterRoot = filterSet.getRoot();
            try {
                Node node = session.getNode(filterRoot);
                collectTemplateNodes(workspaceFilter, node, jcrPathsToBeAdjusted);
            } catch (PathNotFoundException e) {
                LOG.debug("Filter root {} not found", filterRoot);
            }

        }
        
    }

    private void collectTemplateNodes(WorkspaceFilter workspaceFilter, Node node, List<String> jcrPathsToBeAdjusted)
            throws RepositoryException {

        String nodePath = node.getPath();
        LOG.warn("nodePath={}", nodePath);
        if (nodePath.endsWith(TEMPLATE_SUFFIX) && workspaceFilter.covers(nodePath)) {
            jcrPathsToBeAdjusted.add(nodePath);
            LOG.warn("found={}", nodePath);
        }

        NodeIterator nodesIt = node.getNodes();
        while (nodesIt.hasNext()) {
            Node childNode = nodesIt.nextNode();
            collectTemplateNodes(workspaceFilter, childNode, jcrPathsToBeAdjusted);
        }
    }

    private boolean isNotCoveredbyFilter(WorkspaceFilter filter, String path, ImportOptions options) {
        boolean covered = filter.covers(path);
        if (!covered) {
            log("Path " + path + " is not covered by filter \n" + filter.getSourceAsString(), options);
        }
        return !covered;
    }

    String applyEnvVars(String text, Map<String, String> env) {

        Pattern varPattern = Pattern.compile("\\$\\{([^\\}]+)\\}");
        Matcher matcher = varPattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String givenVarAndDefault = matcher.group(1);
            String[] bits = givenVarAndDefault.split(":", 2);
            String givenVar = bits[0].replaceAll("\\.", "_"); /*
                                                               * there cannot be "." in env vars... if the files in the package have dots as
                                                               * it is typically done, the env var can be provided with _ instead of dot
                                                               */
            String defaultVar = bits.length > 1 ? bits[1] : matcher.group(0) /*
                                                                              * leave exactly what we matched as default if no default is
                                                                              * given
                                                                              */;

            String valueToBeUsed;
            if (env.containsKey(givenVar)) {
                valueToBeUsed = env.get(givenVar);
            } else {
                valueToBeUsed = defaultVar;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(valueToBeUsed));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public void log(String message, ImportOptions options) {
        ProgressTrackerListener listener = options.getListener();
        if (listener != null) {
            listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
            LOG.debug(message);
        } else {
            LOG.info(message);
        }
    }

}
