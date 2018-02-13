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
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true, immediate = true, label = "System Env JCR Listener", description = "Listens for system changes via /etc/system-env and automatically applies them by installing the package.")
public class JcrSysEnvChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(JcrSysEnvChangeListener.class);

    private static final String ETC_NODE_PATH = "/etc";
    private static final String SYSTEM_ENV_NODE_PATH = "/etc/system-env";

    @Reference
    PackageReinstaller packageReinstaller;

    @Reference
    private SlingRepository repository;

    private Session session = null;
    private ObservationManager observationManager = null;
    private EventListener sysEnvPropertiesListener = null;
    private EventListener etcNewChildrenListener = null;

    @Activate
    public void activate(@SuppressWarnings("rawtypes") final Map properties)
            throws Exception {

        session = repository.loginService(null, null);

        observationManager = session.getWorkspace().getObservationManager();

        if (!session.nodeExists(SYSTEM_ENV_NODE_PATH)) {
            LOG.debug("Node at {} does not exist, listening for it to be created", SYSTEM_ENV_NODE_PATH);
            listenForNewChildrenAtEtc();
            return;
        }

        listenForChangesAtEtcSystemEnv();
        LOG.info("Listener for JCR path /etc/system-env is active");
    }

    private void listenForChangesAtEtcSystemEnv() throws RepositoryException {
        sysEnvPropertiesListener = new SystemEnvPropertyChangedListener();
        observationManager.addEventListener(sysEnvPropertiesListener, Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED,
                SYSTEM_ENV_NODE_PATH, true, null, null, true);
        LOG.debug("Listening for changes at {}", SYSTEM_ENV_NODE_PATH);
    }

    private void stopListeningForChangesAtEtcSystemEnv() {
        if (observationManager != null && sysEnvPropertiesListener != null) {
            try {
                observationManager.removeEventListener(sysEnvPropertiesListener);
                LOG.info("Stopped listening for changes at {}", SYSTEM_ENV_NODE_PATH);
            } catch (RepositoryException e) {
                LOG.error("Could not deregister event listener: " + e, e);
            }
        }
    }

    private void listenForNewChildrenAtEtc() throws RepositoryException {
        etcNewChildrenListener = new SystemEnvNodeAddedListener();
        observationManager.addEventListener(etcNewChildrenListener, Event.NODE_ADDED,
                ETC_NODE_PATH, true, null, null, true);
        LOG.debug("Listening for new nodes at {}", ETC_NODE_PATH);
    }

    private void stopListeningForForNewChildrenAtEtc() {
        if (observationManager != null && etcNewChildrenListener != null) {
            try {
                observationManager.removeEventListener(etcNewChildrenListener);
                LOG.info("Stopped listening for new nodes at {}", ETC_NODE_PATH);
            } catch (RepositoryException e) {
                LOG.error("Could not deregister event listener: " + e, e);
            }
        }
    }

    @Deactivate
    public void deactivate() {
        stopListeningForChangesAtEtcSystemEnv();
        stopListeningForForNewChildrenAtEtc();

        if (session != null) {
            session.logout();
            session = null;
        }
    }

    private final class SystemEnvPropertyChangedListener implements EventListener {
        @Override
        public void onEvent(EventIterator events) {

            List<String> changedProperties = new ArrayList<String>();

            while (events.hasNext()) {
                Event event = events.nextEvent();

                LOG.debug("Received event {}", event);
                try {
                    changedProperties.add(event.getPath());
                } catch (RepositoryException e) {
                    LOG.debug("Could not get path from event: " + e, e);
                }
            }

            if (!packageReinstaller.isMasterRepository()) {
                LOG.info("Ignoring changed env properties from JCR as this is not the master repository");
                return;
            }

            if (!changedProperties.isEmpty()) {
                LOG.info("Reinstalling env-specific packages since the following properties have changed\n{}",
                        StringUtils.join(changedProperties, "\n"));

                packageReinstaller.installEnvSpecificPackages();
            }

        }
    }

    private final class SystemEnvNodeAddedListener implements EventListener {
        @Override
        public void onEvent(EventIterator events) {

            while (events.hasNext()) {
                Event event = events.nextEvent();

                LOG.debug("Received event {}", event);
                try {
                    if (StringUtils.equals(event.getPath(), SYSTEM_ENV_NODE_PATH)) {
                        LOG.info("Node {} was created, starting listener for it.", SYSTEM_ENV_NODE_PATH);
                        stopListeningForForNewChildrenAtEtc();
                        listenForChangesAtEtcSystemEnv();
                    }
                } catch (RepositoryException e) {
                    LOG.debug("Could not get path from event: " + e, e);
                }

            }

        }
    }


}
