package biz.netcentric.aem.sysenv;


import com.adobe.granite.jmx.annotation.Description;

@Description("Environment Specific Package Reinstaller MBean")
public interface PackageReinstallerMBean {

    @Description("Installs all env-specific packages")
    void installEnvSpecificPackages();
}
