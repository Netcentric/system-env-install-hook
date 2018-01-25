/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.sources;

import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/** Helper to load the zoo keper config. Own class to ensure imports to zoo keeper happen only here and NoClassDefFoundExceptions can be
 * handled in ZooKeeperVarsSource. */
class ZooKeeperVarsSourceLoader {

    public byte[] loadData(String zooKeeperHosts, String zooKeeperPath) throws Exception {
        ZooKeeper zk = connect(zooKeeperHosts);
        byte[] data = new byte[0];
        try {
            Stat exists = zk.exists(zooKeeperPath, false);
            data = zk.getData(zooKeeperPath, false, exists);
        } finally {
            zk.close();
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

}
