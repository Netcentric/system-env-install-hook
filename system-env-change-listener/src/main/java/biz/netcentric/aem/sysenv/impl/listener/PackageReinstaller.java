/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.sysenv.impl.listener;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEventListener;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service({ PackageReinstaller.class, TopologyEventListener.class })
@Component
public class PackageReinstaller implements TopologyEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(PackageReinstaller.class);

    private static final String PACKAGE_ROOT_PATH = "/etc/packages";
    private static final String PACKAGE_PROP_PREFIX = "envSpecificPackage_";

    @Reference
    private Packaging packaging;

    @Reference
    private SlingRepository slingRepository;

    @Reference
    private DynamicClassLoaderManager dynLoaderMgr;

    private boolean isLeader = false;
    
    @Override
    public void handleTopologyEvent(final TopologyEvent event) {
        if ((event.getType() == TopologyEvent.Type.TOPOLOGY_CHANGED)
                || (event.getType() == TopologyEvent.Type.TOPOLOGY_INIT)) {
            isLeader = event.getNewView().getLocalInstance().isLeader();
            LOG.info("Received Topology Event with leader information: isLeader=" + isLeader);
        }
    }

    public boolean isMasterRepository() {
        return isLeader;
    }

    public void installEnvSpecificPackages() {

        if (!isMasterRepository()) {
            LOG.info("Will not reinstall env-specific packages as this instance is not a master repository");
            return;
        }

        Session session = null;
        try {
            session = slingRepository.loginService(null, null);

            List<String> packagesToReinstall = new ArrayList<String>();

            Node packagesRootNode = session.getNode(PACKAGE_ROOT_PATH);
            PropertyIterator propertiesIt = packagesRootNode.getProperties();
            while (propertiesIt.hasNext()) {
                Property nodeProp = propertiesIt.nextProperty();
                if (nodeProp.getName().startsWith(PACKAGE_PROP_PREFIX)) {
                    packagesToReinstall.add(nodeProp.getString());
                }
            }

            LOG.info("Found {} packages to reinstall at node {} (properties starting with {})", packagesToReinstall.size(),
                    PACKAGE_ROOT_PATH, PACKAGE_PROP_PREFIX);

            JcrPackageManager packageManager = packaging.getPackageManager(session);

            for (String packageIdStr : packagesToReinstall) {
                PackageId packageId = PackageId.fromString(packageIdStr);
                LOG.info("Reinstalling package {}...", packageId);
                JcrPackage jcrPackage = packageManager.open(packageId);
                ImportOptions options = new ImportOptions();
                options.setHookClassLoader(dynLoaderMgr.getDynamicClassLoader());
                jcrPackage.install(options);
                LOG.info("Reinstalled package {}.", packageId);
            }
        } catch (Throwable e) {
            LOG.error("Could not install package after change event was received: " + e, e);
        } finally {
            if(session!=null) {
                session.logout();
            }
        }

    }

}
