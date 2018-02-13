/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.sources;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.aem.applysystemenvinstallhook.InstallHookLogger;
import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource;
import biz.netcentric.aem.applysystemenvinstallhook.zookeeper.ZooKeeperConfig;
import biz.netcentric.aem.applysystemenvinstallhook.zookeeper.ZooKeeperLoader;

/** Loads the env from ZooKeeper. The System Properties -Dapplysysenv.zookeeper.hosts and -Dapplysysenv.zookeeper.path have to be set for
 * this to work. */
public class ZooKeeperVarsSource extends VariablesSource {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperVarsSource.class);

    public static final String NAME = "ZooKeeper";

    private ZooKeeperConfig config = new ZooKeeperConfig();

    private InstallHookLogger logger;

    public ZooKeeperVarsSource(InstallHookLogger logger) {
        super(NAME, new HashMap<String, String>());
        this.logger = logger;


        if (!config.isValid()) {
            logger.log(LOG, "Missing system properties " + ZooKeeperConfig.SYS_PROP_ZOOKEEPER_HOSTS + " and "
                    + ZooKeeperConfig.SYS_PROP_ZOOKEEPER_PATH
                    + " - not applying any properties from ZooKeeper");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            logger.log(LOG, "Connecting to ZooKeeper at " + config.getZooKeeperHosts() + " ...");
            byte[] data = new ZooKeeperLoader().loadData(config);

            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(data));

            int countOverride = 0;
            for (Object var : properties.keySet()) {
                String key = String.valueOf(var);
                String value = properties.getProperty(key);
                if (key.endsWith("@" + config.getOverrideSuffix())) {
                    countOverride++;
                }
                variables.put(key, value);
            }

            String overrideSuffixSummery = StringUtils.isNotBlank(config.getOverrideSuffix())
                    ? "(" + countOverride + " with suffix '" + config.getOverrideSuffix() + "') " : "";

            logger.log(LOG,
                    "Loaded " + properties.size() + " properties " + overrideSuffixSummery + "from ZooKeeper at "
                            + config.getZooKeeperPath() + " in "
                    + (System.currentTimeMillis() - startTime) + "ms");

        } catch (NoClassDefFoundError e) {
            logger.log("Could not find ZooKeeper classes (put the zookeeper jar in crx-quickstart/install) " + e);
        } catch (Exception e) {
            logger.logError(LOG,
                    "Could not load variables from Zookeeper " + config.getConnectStr() + ": " + e, e);
        }

    }


    @Override
    public NamedValue get(String varName) {

        String overrideKey = varName + "@" + config.getOverrideSuffix();
        if (variables.containsKey(overrideKey)) {
            logger.log(LOG, "Using key '" + overrideKey + "' as system property '-D" + ZooKeeperConfig.SYS_PROP_OVERRIDE_SUFFIX + "="
                    + config.getOverrideSuffix()
                    + "' is set.");
            return super.get(overrideKey);
        } else {
            return super.get(varName);
        }
    }


}
