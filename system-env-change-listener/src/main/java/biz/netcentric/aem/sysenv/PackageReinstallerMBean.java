package biz.netcentric.aem.sysenv;


import org.apache.jackrabbit.oak.api.jmx.Description;

@Description("Environment Specific Package Reinstaller MBean")
public interface PackageReinstallerMBean {

    @Description("Installs all env-specific packages")
    void installEnvSpecificPackages();
}
