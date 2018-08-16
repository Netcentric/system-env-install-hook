/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource.NamedValue;
import biz.netcentric.aem.applysystemenvinstallhook.sources.JcrVarsSource;
import biz.netcentric.aem.applysystemenvinstallhook.sources.OsEnvVarsSource;
import biz.netcentric.aem.applysystemenvinstallhook.sources.SystemPropertiesVarsSource;

/** Applies environment variables to content in package - this works for both text files and properties of nodes.
 * 
 * See README.md file for details. */
public class ApplySystemEnvInstallHook implements InstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(ApplySystemEnvInstallHook.class);

    private static final String PROP_APPLY_ENV_SOURCES = "applySystemEnvSources";

    private static final String PROP_APPLY_SYSTEM_ENV_FOR_PATHS = "applySystemEnvForPaths";
    private static final String PROP_FAIL_FOR_MISSING_ENV_VARS = "failForMissingEnvVars";

    public static final String TEMPLATE_SUFFIX = ".TEMPLATE";

    private static final String PACKAGE_ROOT_PATH = "/etc/packages";
    private static final String PACKAGE_PROP_PREFIX = "envSpecificPackage_";

    private InstallHookLogger logger = new InstallHookLogger();
    private VariablesMerger variablesMerger = new VariablesMerger(logger);
    private static VariablesSource variablesSource = null;

    @Override
    public void execute(InstallContext context) throws PackageException {
        try {

            final Session session = context.getSession();
            ImportOptions options = context.getOptions();
            logger.setOptions(options);
            VaultPackage vaultPackage = context.getPackage();
            WorkspaceFilter filter = vaultPackage.getMetaInf().getFilter();

            List<String> jcrPathsToBeAdjusted = new ArrayList<String>();
            String applyEnvVarsForPaths = vaultPackage.getProperties().getProperty(PROP_APPLY_SYSTEM_ENV_FOR_PATHS);
            LOG.debug("Property applyEnvVarsForPaths from package={}", applyEnvVarsForPaths);

            if (StringUtils.isNotBlank(applyEnvVarsForPaths)) {
                jcrPathsToBeAdjusted.addAll(Arrays.asList(applyEnvVarsForPaths.trim().split("[\\s*,]+")));
            }

            switch (context.getPhase()) {
            case PREPARE:
                logger.log(getClass().getSimpleName() + " is active in " + vaultPackage.getId());

                logger.log("Loading variable sources... ");
                variablesSource = getVariablesSource(context);

                boolean failForMissingEnvVar = Boolean.valueOf(vaultPackage.getProperties().getProperty(PROP_FAIL_FOR_MISSING_ENV_VARS));
                LOG.debug("Property failForMissingEnvVar from package={}", failForMissingEnvVar);

                if (failForMissingEnvVar) {
                    logger.log(getClass().getSimpleName()
                            + " checking if all env vars are set due to package property failForMissingEnvVar=true");

                    Archive archive = vaultPackage.getArchive();
                    boolean allVariablesFoundInEnv = findMissingEnvVarInPackageEntry(archive, "/", archive.getJcrRoot(), options, jcrPathsToBeAdjusted);
                    if (!allVariablesFoundInEnv) {
                        String errMsg = "Aborting installation of package " + vaultPackage.getId() + " due to missing env variables";
                        logger.log(errMsg);
                        throw new PackageException(errMsg);
                    }
                }
                break;

            case INSTALLED:

                if (variablesSource == null) {
                    String msg = "Sources as set in prepare phase are not available in INSTALLED phase anymore";
                    LOG.error(msg);
                    throw new IllegalStateException(msg);
                }

                collectTemplateNodes(vaultPackage, session, jcrPathsToBeAdjusted);

                if (jcrPathsToBeAdjusted.isEmpty()) {
                    logger.log("Install Hook " + getClass().getName()
                            + " was configured but package property 'applyEnvVarsForPaths' was left blank and no .TEMPLATE nodes were found in package. No action taken.");
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

                        adjustProperty(session, path, propertyName, variablesSource, options);
                    } else {
                        if (isNotCoveredbyFilter(filter, jcrPathToBeAdjusted, options)) {
                            continue;
                        }
                        
                        Node nodeToBeAdjusted = session.getNode(jcrPathToBeAdjusted);
                        
                        if (isFile(nodeToBeAdjusted)) {
                            String fileContent = IOUtils.toString(JcrUtils.readFile(nodeToBeAdjusted));

                            String adjustedFileContent = variablesMerger.applyEnvVars(fileContent, variablesSource, nodeToBeAdjusted.getPath());

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
                            adjustAllPropertiesOfNodeTree(nodeToBeAdjusted, variablesSource, options);
                        }
                    }
                    
                }

                logger.log("\n" + variablesMerger.getReplacementSummary());

                savePackageLocation(session, vaultPackage);

                session.save();
                logger.log("Saved session. ");

                break;
            default:
                break;
            }
        } catch (RepositoryException | IOException e) {
            throw new PackageException("Could not execute install hook to apply env vars: " + e, e);
        }
    }

    private void savePackageLocation(Session session, VaultPackage vaultPackage) {
        LOG.debug("Saving information that this package contains env-specific values and the install hook "
                + "in order to allow listeners to reinstall it");
        try {
            Node node = session.getNode(PACKAGE_ROOT_PATH);
            String group = vaultPackage.getProperty("group");
            String name = vaultPackage.getProperty("name");
            // using a key that will make sure that packages with same group/name but different version are only reinstalled once
            String packageKey = PACKAGE_PROP_PREFIX
                    + group.replaceAll("[^A-Za-z0-9]", "")
                    + "_" + name.replaceAll("[^A-Za-z0-9]", "");
            node.setProperty(packageKey, vaultPackage.getId().toString());
        } catch (Exception e) {
            LOG.info("Could not save package location of " + vaultPackage.getId() + ": " + e, e);
        }

    }

    private VariablesSource getVariablesSource(InstallContext context) {
        String applySystemEnvSources = context.getPackage().getProperties().getProperty(PROP_APPLY_ENV_SOURCES);
        List<String> sourceNames;
        if (StringUtils.isNotBlank(applySystemEnvSources)) {
            LOG.debug("Property applySystemEnvSources from package={}", applySystemEnvSources);
            sourceNames = Arrays.asList(applySystemEnvSources.split(" *, *"));
        } else {
            sourceNames = Arrays.asList(SystemPropertiesVarsSource.NAME, JcrVarsSource.NAME, OsEnvVarsSource.NAME);
        }
        logger.log("Using sources [" + StringUtils.join(sourceNames, ", ") + "]");

        VariablesSource env = CombinedVariablesSource.forSources(sourceNames, logger, context);
        return env;
    }


    // not using workspace.copy() because that method saves immediately
    private Node copy(Node sourceNode, String targetPath) throws RepositoryException {
        LOG.trace("Copy {} to {}", sourceNode, targetPath);
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
            LOG.trace("Copied {} / {} to {}", sourceProp.getName(), sourceProp.getType(), targetNode);

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

    private void adjustAllPropertiesOfNodeTree(Node node, VariablesSource env, ImportOptions options)
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

    private void adjustProperty(Session session, String path, String propertyName, VariablesSource env, ImportOptions options)
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
                LOG.debug("Property " + propertyPath + " is not of type String");
                return;
            }

            if (!property.isMultiple()) {
                String stringValueRaw = property.getString();
                String adjustedValue = variablesMerger.applyEnvVars(stringValueRaw, env, propertyPath);
                property.setValue(adjustedValue);
            } else {
                List<Value> newValues = new ArrayList<Value>();
                Value[] values = property.getValues();
                for (int i = 0; i < values.length; i++) {
                    String stringValueRaw = values[i].getString();
                    String adjustedValue = variablesMerger.applyEnvVars(stringValueRaw, env, propertyPath + "[" + i + "]");
                    newValues.add(session.getValueFactory().createValue(adjustedValue));
                }
                property.setValue(newValues.toArray(new Value[newValues.size()]));
            }

        } catch (PathNotFoundException e) {
            logger.log("Path " + propertyPath + " could not be found");
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
            logger.log("Path " + path + " is not covered by filter \n" + filter.getSourceAsString());
        }
        return !covered;
    }

    private boolean findMissingEnvVarInPackageEntry(Archive archive, String parentPath, Entry entry, ImportOptions options, List<String> jcrPathsToBeAdjusted) {

        String path = parentPath + "/" + entry.getName();
        boolean result = true;
        if (!entry.isDirectory() && 
        		(isPathExplictlyMarkedForAdjustment(path, jcrPathsToBeAdjusted) || path.endsWith(TEMPLATE_SUFFIX))) {
        	
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
                logger.log("Could not read " + path + " as text, skipping (" + e + ")");

            }

            if (fileContent != null) {

                List<VariablesMerger.EnvVarDeclaration> envVarDeclarations = variablesMerger.getEnvVarDeclarations(fileContent);
                for (VariablesMerger.EnvVarDeclaration envVar : envVarDeclarations) {

                    if (envVar.defaultVal != null) {
                        LOG.debug("Default value given for variable {}", envVar);
                        continue;
                    }
                    NamedValue varEntry = variablesSource.get(envVar.name);
                    if (varEntry == null) {
                        logger.log("Variable '" + envVar.name + "' is not found but is used in file " + path
                                + " without declaring a default and it could not be found in sources: " + variablesSource.getName());
                        result = false;
                    }
                }

            }
        }

        Collection<? extends Entry> children = entry.getChildren();
        for (Entry subEntry : children) {
            result &= findMissingEnvVarInPackageEntry(archive, path, subEntry, options, jcrPathsToBeAdjusted);
        }

        return result;
    }
    
    private boolean isPathExplictlyMarkedForAdjustment(String currentPath, List<String> jcrPathsToBeAdjusted) {
		String currentPathNormalised = currentPath.replaceFirst("^//jcr_root", "").replaceFirst("/.content.xml$", "").replaceFirst(".xml$", "");
    	for (String jcrPathToBeAdjusted : jcrPathsToBeAdjusted) {
    		String pureJcrPathToBeAdjusted = StringUtils.substringBefore(jcrPathToBeAdjusted, "@");
    		if(currentPathNormalised.startsWith(pureJcrPathToBeAdjusted)) {
    			return true;
    		}
		}
    	return false;
    }
    
}
