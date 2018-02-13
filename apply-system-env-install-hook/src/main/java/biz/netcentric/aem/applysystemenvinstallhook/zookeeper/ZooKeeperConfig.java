/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.zookeeper;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the zoo keeper system properties to a pojo. */
public class ZooKeeperConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperConfig.class);

    public static final String SYS_PROP_ZOOKEEPER_HOSTS = "applysysenv.zookeeper.hosts";
    public static final String SYS_PROP_ZOOKEEPER_PATH = "applysysenv.zookeeper.path";
    public static final String SYS_PROP_OVERRIDE_SUFFIX = "applysysenv.zookeeper.overrideSuffix";
    public static final String SYS_PROP_ZOOKEEPER_CON_RETRIES = "applysysenv.zookeeper.connectRetries";

    private final String zooKeeperHosts;
    private final String zooKeeperPath;
    private final String overrideSuffix;
    private final int connectRetries;

    public ZooKeeperConfig() {

        zooKeeperHosts = System.getProperty(SYS_PROP_ZOOKEEPER_HOSTS);
        zooKeeperPath = System.getProperty(SYS_PROP_ZOOKEEPER_PATH);
        overrideSuffix = System.getProperty(SYS_PROP_OVERRIDE_SUFFIX);

        String zooKeeperConnectRetriesStr = System.getProperty(SYS_PROP_ZOOKEEPER_CON_RETRIES);
        connectRetries = StringUtils.isNotBlank(zooKeeperConnectRetriesStr) ? Integer.parseInt(zooKeeperConnectRetriesStr) : 2;

    }

    public boolean isValid() {
        return StringUtils.isNotBlank(zooKeeperHosts) && StringUtils.isNotBlank(zooKeeperPath);
    }

    public String getConnectStr() {
        return zooKeeperHosts + zooKeeperPath;
    }

    public String getZooKeeperHosts() {
        return zooKeeperHosts;
    }

    public String getZooKeeperPath() {
        return zooKeeperPath;
    }

    public String getOverrideSuffix() {
        return overrideSuffix;
    }

    public int getConnectRetries() {
        return connectRetries;
    }

}
