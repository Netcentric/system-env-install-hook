/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.sysenv.impl.listener;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true, immediate = true, label = "System Env ZooKeeper Listener", description = "Listens for system env changes from zoo keeper if configured and automatically applies them by installing the package.")
public class ZooKeeperChangeListener implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperChangeListener.class);

    public static final String SYS_PROP_ZOOKEEPER_HOSTS = "applysysenv.zookeeper.hosts";
    public static final String SYS_PROP_ZOOKEEPER_PATH = "applysysenv.zookeeper.path";

    private ZooKeeper zk = null;

    private String zooKeeperHosts;
    private String zooKeeperPath;

    @Reference
    PackageReinstaller packageReinstaller;

    @Activate
    public void activate(BundleContext bundleContext, @SuppressWarnings("rawtypes") final Map properties)
            throws Exception {

        zooKeeperHosts = System.getProperty(SYS_PROP_ZOOKEEPER_HOSTS);
        zooKeeperPath = System.getProperty(SYS_PROP_ZOOKEEPER_PATH);

        try {
            zk = connect(zooKeeperHosts);
            getDataAndSetWatcher(zooKeeperHosts, zooKeeperPath);
            LOG.info("Listening for ZooKeeper changes at {}{}", zooKeeperHosts, zooKeeperPath);
        } catch (Exception e) {
            LOG.warn("Could not connect to ZooKeeper at " + zooKeeperHosts + ": " + e, e);
            LOG.warn("No updates will be received from ZooKeeper");
        }

    }

    @Deactivate
    public void deactivate() {
        if (zk != null) {
            try {
                zk.close();
                LOG.info("Stopped listening for ZooKeeper changes at {}{}", zooKeeperHosts, zooKeeperPath);
            } catch (Exception e) {
                LOG.warn("Could not close ZooKeeper Session: " + e, e);
            }
        }
    }

    public byte[] getDataAndSetWatcher(String zooKeeperHosts, String zooKeeperPath) {

        byte[] data = new byte[0];

        try {
            Stat exists = zk.exists(zooKeeperPath, false);
            data = zk.getData(zooKeeperPath, this, exists);

            LOG.debug("Received data from ZooKeeper:\n" + new String(data));

        } catch (Exception e) {
            LOG.warn("Could not get data from ZooKeeper at " + zooKeeperHosts + ": " + e, e);
            LOG.warn("No more updates will be received from ZooKeeper");
        }

        return data;
    }

    private ZooKeeper connect(String host) throws Exception {
        final CountDownLatch connSignal = new CountDownLatch(0);
        ZooKeeper zk = new ZooKeeper(host, 10000, new Watcher() {
            public void process(WatchedEvent event) {
                if (event.getState() == KeeperState.SyncConnected) {
                    connSignal.countDown();
                }
            }
        }, true);
        connSignal.await();
        return zk;
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
        getDataAndSetWatcher(zooKeeperHosts, zooKeeperPath);
    }

}
