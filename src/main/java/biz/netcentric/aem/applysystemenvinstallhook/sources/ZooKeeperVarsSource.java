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

/** Loads the env from ZooKeeper. The System Properties -Dapplysysenv.zookeeper.hosts and -Dapplysysenv.zookeeper.path have to be set for
 * this to work. */
public class ZooKeeperVarsSource extends VariablesSource {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperVarsSource.class);

    public static final String NAME = "ZooKeeper";

    public static final String SYS_PROP_ZOOKEEPER_HOSTS = "applysysenv.zookeeper.hosts";
    public static final String SYS_PROP_ZOOKEEPER_PATH = "applysysenv.zookeeper.path";
    public static final String SYS_PROP_OVERRIDE_SUFFIX = "applysysenv.zookeeper.overrideSuffix";

    private String overrideSuffix = System.getProperty(SYS_PROP_OVERRIDE_SUFFIX);

    private InstallHookLogger logger;

    public ZooKeeperVarsSource(InstallHookLogger logger) {
        super(NAME, new HashMap<String, String>());
        this.logger = logger;

        String zooKeeperHosts = System.getProperty(SYS_PROP_ZOOKEEPER_HOSTS);
        String zooKeeperPath = System.getProperty(SYS_PROP_ZOOKEEPER_PATH);

        if (StringUtils.isBlank(zooKeeperHosts) || StringUtils.isBlank(zooKeeperPath)) {
            logger.log(LOG, "Missing system properties " + SYS_PROP_ZOOKEEPER_HOSTS + " and " + SYS_PROP_ZOOKEEPER_PATH
                    + " - not applying any properties from ZooKeeper");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            logger.log(LOG, "Connecting to ZooKeeper at " + zooKeeperHosts + " ...");
            byte[] data = new ZooKeeperVarsSourceLoader().loadData(zooKeeperHosts, zooKeeperPath);

            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(data));

            int countOverride = 0;
            for (Object var : properties.keySet()) {
                String key = String.valueOf(var);
                String value = properties.getProperty(key);
                if (key.endsWith("@" + overrideSuffix)) {
                    countOverride++;
                }
                variables.put(key, value);
            }

            String overrideSuffixSummery = StringUtils.isNotBlank(overrideSuffix)
                    ? "(" + countOverride + " with suffix '" + overrideSuffix + "') " : "";

            logger.log(LOG,
                    "Loaded " + properties.size() + " properties " + overrideSuffixSummery + "from ZooKeeper at " + zooKeeperPath + " in "
                    + (System.currentTimeMillis() - startTime) + "ms");

        } catch (NoClassDefFoundError e) {
            logger.log("Could not find ZooKeeper classes (put the zookeeper jar in crx-quickstart/install) " + e);
        } catch (Exception e) {
            logger.logError(LOG, "Could not load variables from Zookeeper " + zooKeeperHosts + zooKeeperPath + ": " + e, e);
        }

    }


    @Override
    public NamedValue get(String varName) {

        String overrideKey = varName + "@" + overrideSuffix;
        if (variables.containsKey(overrideKey)) {
            logger.log(LOG, "Using key '" + overrideKey + "' as system property '-D" + SYS_PROP_OVERRIDE_SUFFIX + "=" + overrideSuffix
                    + "' is set.");
            return super.get(overrideKey);
        } else {
            return super.get(varName);
        }
    }


}
