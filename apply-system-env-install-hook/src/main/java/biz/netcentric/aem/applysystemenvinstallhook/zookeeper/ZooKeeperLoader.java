/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.zookeeper;

import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper to load/watch the zoo keeper config. Own class to ensure imports to zoo keeper happen only here and NoClassDefFoundExceptions can
 * be handled in ZooKeeperVarsSource. */
public class ZooKeeperLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperLoader.class);

    public byte[] loadData(ZooKeeperConfig config) throws Exception {
        return loadData(config, null);
    }

    private ZooKeeper zk = null;

    public byte[] loadData(ZooKeeperConfig config, Watcher watcher) throws Exception {

        KeeperException.ConnectionLossException lastException = null;

        int connectRetries = config.getConnectRetries();
        for (int i = 0; i <= connectRetries; i++) {
            try {
                return loadDataInternal(config, watcher);
            } catch (KeeperException.ConnectionLossException e) {
                LOG.warn("Could not connect and load data: " + e, e);
                if (i < connectRetries) { // only sleep if necessary
                    Thread.sleep(333);
                } else {
                    lastException = e;
                }
            }
        }

        throw new IllegalStateException(
                "Could not connect to " + config.getConnectStr() + " even after " + connectRetries + ": " + lastException, lastException);
    }

    private byte[] loadDataInternal(ZooKeeperConfig config, Watcher watcher) throws Exception {

        zk = connect(config);
        byte[] data = new byte[0];
        try {
            String zooKeeperPath = config.getZooKeeperPath();
            Stat exists = zk.exists(zooKeeperPath, false);
            LOG.debug("Data exists state at {}: {}", zooKeeperPath, exists);
            if (watcher == null) {
                data = zk.getData(zooKeeperPath, false, exists);
            } else {
                data = zk.getData(zooKeeperPath, watcher, exists);
            }
            LOG.trace("Data: {}", data);
        } finally {
            if (watcher == null) {
                closeSession();
            }
        }
        return data;
    }

    public void closeSession() {
        if (zk != null) {
            try {
                zk.close();
            } catch (Exception e) {
                LOG.warn("Could not close ZooKeeper session: " + e, e);
            }
        }
    }

    private ZooKeeper connect(ZooKeeperConfig config) throws Exception {
        final CountDownLatch connSignal = new CountDownLatch(1);
        ZooKeeper zk = new ZooKeeper(config.getZooKeeperHosts(), 10000, new Watcher() {
            public void process(WatchedEvent event) {
                LOG.debug("Received event {} for path {} with state ", event.getType(), event.getPath(), event.getState());
                if (event.getState() == KeeperState.SyncConnected) {
                    connSignal.countDown();
                }
            }
        }, true);
        connSignal.await();
        LOG.debug("Returning connection {}", zk);
        return zk;
    }

}
