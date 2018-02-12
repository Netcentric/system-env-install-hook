/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.sources;

import java.util.HashMap;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.aem.applysystemenvinstallhook.InstallHookLogger;
import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource;

/** Loads the system env form /etc/system-env. */
public class JcrVarsSource extends VariablesSource {
    private static final Logger LOG = LoggerFactory.getLogger(JcrVarsSource.class);

    public static final String NAME = "JCR";
    private static final String JCR_CONFIG_PATH = "/etc/system-env";

    public JcrVarsSource(InstallHookLogger logger, InstallContext context) {
        super(NAME, new HashMap<String, String>());

        try {
            Session session = context.getSession();
            Node node = session.getNode(JCR_CONFIG_PATH);
            PropertyIterator propertiesIt = node.getProperties();
            while (propertiesIt.hasNext()) {
                Property prop = propertiesIt.nextProperty();
                String propName = prop.getName();
                if(propName.contains(":")) {
                    continue;
                }
                if (prop.isMultiple()) {
                    logger.log(LOG, "Only single-valued properties are supported (skipping " + propName + ")");
                    continue;
                }
                String propVal = prop.getValue().getString();
                variables.put(propName, propVal);
            }

        } catch (PathNotFoundException e) {
            logger.log(LOG, "Node " + JCR_CONFIG_PATH + " does not exist");
        } catch (Exception e) {
            logger.logError(LOG, "Could not load variables from " + JCR_CONFIG_PATH + ": " + e, e);
        }

    }

    public String getName() {
        return super.getName() + " " + JCR_CONFIG_PATH; // for clearer logs in Package Manager
    }

}
