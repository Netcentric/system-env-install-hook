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
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import javax.jcr.Value;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
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

    private static final Logger LOG = LoggerFactory.getLogger(ApplyEnvVarsInstallHook.class);

    private static final String PROP_APPLY_ENV_VARS_FOR_PATHS = "applyEnvVarsForPaths";
    private static final String PROP_FAIL_FOR_MISSING_ENV_VARS = "failForMissingEnvVars";
    private static final String TEMPLATE_SUFFIX = ".TEMPLATE";

    private int countVarsReplaced = 0;
    private int countVarsDefaultUsed = 0;
    private int countVarsNotFound = 0;

    @Override
    public void execute(InstallContext context) throws PackageException {
        try {

            final Session session = context.getSession();
            ImportOptions options = context.getOptions();
            VaultPackage vaultPackage = context.getPackage();
            WorkspaceFilter filter = vaultPackage.getMetaInf().getFilter();

            Map<String, String> env = System.getenv();

            switch (context.getPhase()) {
            case PREPARE:
                log(getClass().getSimpleName() + " is active in " + vaultPackage.getId(), options);

                boolean failForMissingEnvVar = Boolean.valueOf(vaultPackage.getProperties().getProperty(PROP_FAIL_FOR_MISSING_ENV_VARS));
                LOG.debug("Property failForMissingEnvVar from package={}", failForMissingEnvVar);

                if (failForMissingEnvVar) {
                    log(getClass().getSimpleName() + " checking if all env vars are set due to package property failForMissingEnvVar=true",
                            options);

                    Archive archive = vaultPackage.getArchive();
                    boolean allVariablesFoundInEnv = findMissingEnvVarInPackageEntry(archive, "/", archive.getJcrRoot(), options);
                    if (!allVariablesFoundInEnv) {
                        String errMsg = "Aborting installation of package " + vaultPackage.getId() + " due to missing env variables";
                        log(errMsg, options);
                        throw new PackageException(errMsg);
                    }
                }
                break;

            case INSTALLED:

                List<String> jcrPathsToBeAdjusted = new ArrayList<String>();

                String applyEnvVarsForPaths = vaultPackage.getProperties().getProperty(PROP_APPLY_ENV_VARS_FOR_PATHS);
                LOG.debug("Property applyEnvVarsForPaths from package={}", applyEnvVarsForPaths);

                if (StringUtils.isNotBlank(applyEnvVarsForPaths)) {
                    jcrPathsToBeAdjusted.addAll(Arrays.asList(applyEnvVarsForPaths.trim().split("[\\s*,]+")));
                }

                collectTemplateNodes(vaultPackage, session, jcrPathsToBeAdjusted);

                if (jcrPathsToBeAdjusted.isEmpty()) {
                    log("Install Hook " + getClass().getName()
                            + " was configured but package property 'applyEnvVarsForPaths' was left blank and no .TEMPLATE nodes were configured",
                            options);
                    return;
                }

                for (String jcrPathToBeAdjusted : jcrPathsToBeAdjusted) {

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

                            String adjustedFileContent = applyEnvVars(fileContent, env, nodeToBeAdjusted.getPath(), options);

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
                                if (session.itemExists(targetPath)) {
                                    // ensure old properties get deleted
                                    session.removeItem(targetPath);
                                }
                                nodeToBeAdjusted = copy(nodeToBeAdjusted, targetPath);
                            }

                            // adjust all string properties of node
                            adjustAllPropertiesOfNodeTree(nodeToBeAdjusted, env, options);
                        }
                    }
                    
                }

                log("Values replaced: " + countVarsReplaced, options);
                if (countVarsDefaultUsed > 0) {
                    log("Default values used: " + countVarsDefaultUsed, options);
                }
                if (countVarsNotFound > 0) {
                    log("WARN: No env variable found for var and no default given: " + countVarsNotFound, options);
                }
                session.save();
                log("Saved session. ", options);

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
            LOG.debug("Copied {} / {} to {}", sourceProp.getName(), sourceProp.getType(), targetNode);

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
            adjustProperty(node.getSession(), node.getPath(), prop.getName(), env, options);
        }
        NodeIterator nodesIt = node.getNodes();
        while (nodesIt.hasNext()) {
            Node childNode = nodesIt.nextNode();
            adjustAllPropertiesOfNodeTree(childNode, env, options);
        }
    }

    private void adjustProperty(Session session, String path, String propertyName, Map<String, String> env, ImportOptions options)
            throws RepositoryException {
        String propertyPath = path + "@" + propertyName;
        try {
            LOG.debug("Looking at path {} prop {}", path, propertyName);
            Node node = session.getNode(path);
            Property property = node.getProperty(propertyName);
            if (property.getDefinition().isProtected()) {
                return;
            }
            if (property.getType() != PropertyType.STRING) {
                LOG.debug("Property " + propertyPath + " is not of type String", options);
                return;
            }

            if (!property.isMultiple()) {
                String stringValueRaw = property.getString();
                String adjustedValue = applyEnvVars(stringValueRaw, env, propertyPath, options);
                property.setValue(adjustedValue);
            } else {
                List<Value> newValues = new ArrayList<Value>();
                Value[] values = property.getValues();
                for (int i = 0; i < values.length; i++) {
                    String stringValueRaw = values[i].getString();
                    String adjustedValue = applyEnvVars(stringValueRaw, env, propertyPath + "[" + i + "]", options);
                    newValues.add(session.getValueFactory().createValue(adjustedValue));
                }
                property.setValue(newValues.toArray(new Value[newValues.size()]));
            }

        } catch (PathNotFoundException e) {
            log("Path " + propertyPath + " could not be found", options);
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
        LOG.debug("nodePath={}", nodePath);
        if (nodePath.endsWith(TEMPLATE_SUFFIX) && workspaceFilter.covers(nodePath)) {
            jcrPathsToBeAdjusted.add(nodePath);
            LOG.debug("found={}", nodePath);
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

    String applyEnvVars(String text, Map<String, String> env, String path, ImportOptions options) {

        Matcher matcher = EnvVarDeclaration.VAR_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            EnvVarDeclaration envVar = new EnvVarDeclaration(matcher.group(1));

            String valueToBeUsed;
            String action;
            if (env.containsKey(envVar.effectiveName)) {
                valueToBeUsed = env.get(envVar.effectiveName);
                countVarsReplaced++;
                action = "replaced from env";
            } else {
                String effectiveDefaultVal;
                if (envVar.defaultVal != null) {
                    effectiveDefaultVal = envVar.defaultVal;
                    countVarsDefaultUsed++;
                    action = "default in package";
                } else {
                    // leave exactly what we matched as default if no default is given
                    effectiveDefaultVal = matcher.group(0);
                    countVarsNotFound++;
                    action = "env var not found, no default provided!";
                }
                valueToBeUsed = effectiveDefaultVal;
            }
            log(path.replace(TEMPLATE_SUFFIX, "") + ": " + envVar.name + "=\"" + valueToBeUsed + "\" (" + action + ")", options);
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

    private static class EnvVarDeclaration {
        private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");

        private String name;
        private String effectiveName;
        private String defaultVal;

        EnvVarDeclaration(String groupOneOfMatcher) {
            String givenVarAndDefault = groupOneOfMatcher;
            String[] bits = givenVarAndDefault.split(":", 2);

            name = bits[0];
            // there cannot be "." in env vars... if the files in the package have dots as it is typically done, the env var can be provided
            // with _ instead of dot
            effectiveName = name.replaceAll("\\.", "_");
            if (bits.length > 1) {
                defaultVal = bits[1];
            } else {
                defaultVal = null;
            }
        }

    }

    private boolean findMissingEnvVarInPackageEntry(Archive archive, String parentPath, Entry entry, ImportOptions options) {

        String path = parentPath + "/" + entry.getName();
        boolean result = true;
        if (!entry.isDirectory()) {

            String fileContent = null;

            LOG.debug("Reading file {}", path);
            try {
                InputStream input = archive.getInputSource(entry).getByteStream();
                if (input == null) {
                    throw new IllegalStateException("Could not get input stream from entry " + path);
                }
                StringWriter writer = new StringWriter();
                IOUtils.copy(input, writer, "UTF-8");
                fileContent = writer.toString();
            } catch (Exception e) {
                log("Could not read " + path + " as text, skipping (" + e + ")", options);

            }

            if (fileContent != null) {
                Map<String, String> systemEnv = System.getenv();
                Matcher matcher = EnvVarDeclaration.VAR_PATTERN.matcher(fileContent);
                while (matcher.find()) {
                    EnvVarDeclaration envVar = new EnvVarDeclaration(matcher.group(1));

                    if (envVar.defaultVal != null) {
                        LOG.debug("Default value given for variable {}", envVar);
                        continue;
                    }
                    if (!systemEnv.containsKey(envVar.effectiveName)) {
                        log("Env Variable '" + envVar.effectiveName + "' is not found but ${" + envVar.name + "} is used in file " + path
                                + " without declaring a default", options);
                        result = false;
                    }
                }
            }
        }

        Collection<? extends Entry> children = entry.getChildren();
        for (Entry subEntry : children) {
            result &= findMissingEnvVarInPackageEntry(archive, path, subEntry, options);
        }

        return result;
    }
}
