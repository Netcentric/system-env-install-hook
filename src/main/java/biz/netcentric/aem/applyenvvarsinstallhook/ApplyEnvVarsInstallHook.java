package biz.netcentric.aem.applyenvvarsinstallhook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**

 */
public class ApplyEnvVarsInstallHook implements InstallHook {

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
                String[] jcrPathsToBeAdjusted = applyEnvVarsForPaths.trim().split("[\\s*,]+");
                for (String jcrPathToBeAdjusted : jcrPathsToBeAdjusted) {

                    log("Adjusting " + jcrPathToBeAdjusted + "...", options);

                    if (jcrPathToBeAdjusted.contains("@")) {
                        String[] pathAndProperty = jcrPathToBeAdjusted.split("@", 2);
                        String path = pathAndProperty[0];
                        if (isNotCoveredbyFilter(filter, path, options)) {
                            continue;
                        }

                        String propertyName = pathAndProperty[1];
                        try {
                            Node node = session.getNode(path);
                            Property property = node.getProperty(propertyName);
                            if (property.getType() != PropertyType.STRING) {
                                log("Property " + jcrPathToBeAdjusted + " is not of type String", options);
                                continue;
                            }
                            String stringValueRaw = property.getString();
                            String adjustedValue = applyEnvVars(stringValueRaw, env);
                            property.setValue(adjustedValue);
                        } catch (PathNotFoundException e) {
                            log("Path " + path + " could not be found", options);
                            continue;
                        }

                    } else {
                        if (isNotCoveredbyFilter(filter, jcrPathToBeAdjusted, options)) {
                            continue;
                        }
                        
                        Node node = session.getNode(jcrPathToBeAdjusted);
                        
                        String fileContent = IOUtils.toString(JcrUtils.readFile(node));
                        
                        String adjustedFileContent = applyEnvVars(fileContent, env);
                        JcrUtils.putFile(node.getParent(), node.getName(), "text/plain" /* only used to derive encoding */,
                                new ByteArrayInputStream(adjustedFileContent.getBytes()));
                        
                    }
                    
                }

                break;
            case END:
                log("Saving session...", options);
                session.save();
                log(getClass().getSimpleName() + " done. ", options);

                break;
            default:
                break;
            }
        } catch (RepositoryException e) {
            throw new PackageException(e);
        } catch (IOException e) {
            throw new PackageException(e);
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
