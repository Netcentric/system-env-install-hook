/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.sysenv.impl.listener;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.aem.applysystemenvinstallhook.zookeeper.ZooKeeperConfig;
import biz.netcentric.aem.applysystemenvinstallhook.zookeeper.ZooKeeperLoader;

@Component(metatype = true, immediate = true, label = "System Env ZooKeeper Listener", description = "Listens for system env changes from zoo keeper if configured and automatically applies them by installing the package.")
public class ZooKeeperChangeListener implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperChangeListener.class);

    @Reference
    PackageReinstaller packageReinstaller;

    private ZooKeeperLoader loader = new ZooKeeperLoader();
    private ZooKeeperConfig config = new ZooKeeperConfig();

    @Activate
    public void activate(BundleContext bundleContext) {
        setupWatcher();
    }

    @Deactivate
    public void deactivate() {
        loader.closeSession();
        loader = null;
        LOG.info("Stopped listening for ZooKeeper changes at {}", config.getConnectStr());
    }

    private void setupWatcher() {
        try {
            loader.loadData(config, this);
        } catch (Exception e) {
            LOG.warn("Could not load data from ZooKeeper at " + config.getConnectStr() + ": " + e, e);
            LOG.warn("No more updates will be received from ZooKeeper");
        }
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (watchedEvent.getType() != Watcher.Event.EventType.NodeDataChanged) {
            LOG.info("Ignoring ZooKeeper event type: {}", watchedEvent.getType());
            return;
        }

        LOG.info("Received NodeDataChanged event from ZooKeeper: {}", watchedEvent);

        if (!packageReinstaller.isMasterRepository()) {
            LOG.info("Ignoring changed env properties from ZooKeeper as this is not the master repository");
            return;
        }

        // reinstall all packages with hook
        packageReinstaller.installEnvSpecificPackages();

        // set watcher again
        setupWatcher();
    }

}
