/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.sysenv.impl.listener;

import javax.management.DynamicMBean;
import javax.management.NotCompliantMBeanException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.oak.commons.jmx.AnnotatedStandardMBean;

import biz.netcentric.aem.sysenv.PackageReinstallerMBean;

@Service({DynamicMBean.class})
@org.apache.felix.scr.annotations.Property(name = "jmx.objectname", value = "biz.netcentric.aem.sysenv:type=Env Specific Package Installer")
@Component
public class PackageReinstallerMBeanImpl extends AnnotatedStandardMBean implements PackageReinstallerMBean {

    @Reference
    private PackageReinstaller packageReinstaller;


    public PackageReinstallerMBeanImpl() throws NotCompliantMBeanException {
        super(PackageReinstallerMBean.class);
    }

    @Override
    public void installEnvSpecificPackages() {
        packageReinstaller.installEnvSpecificPackages();

    }

}
