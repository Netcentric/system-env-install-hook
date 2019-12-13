Apply Env Install Hook for AEM/CRX
================================================

**DISCLAIMER:**
This hook should be used sparsely for very limited use cases only. Almost all OSGi configurations should be bound to runmodes as well-established mechanism to configure different environments. However reasonable use cases are:

* Replication Agents (if the set of potential target environments is unlimited/unknown)
* Cloud Configurations with differing values (e.g. URLs) between different environments
* OSGi configurations that differ per env (e.g. for a AEM communities user sync)
* Sensible production passwords (in OSGi configurations or cloud services) that have to be deployed automatically (this can be mitigated by using the CryptoService and a well-defined master key, however if the master key leaks all passwords can be decrypted) 

For OSGi configurations, Felix recently introduced the configadmin plugin [interpolation](https://github.com/apache/felix/tree/trunk/configadmin-plugins/interpolation) that allows for env variables substitution on framework level, however there is not ootb platform solution to apply env variables to content.

# Overview

Replaces variables in content packages for files (e.g. useful for OSGi configurations) and node properties (e.g. useful for replication agents).

Variables can be in form `${myVar}` or  `${myVar:defaultVal}` - if the var can not be found in any sources, for the first example `${myVar}` remains as is, for the second example `defaultVal` is used.

# Configuration

## Including variables in packages

### Nodes with suffix .TEMPLATE

Additionally to nodes that explictly configured to be adjusted, any nodes that end with `.TEMPLATE` will be copied to the same name without `.TEMPLATE` (e.g. `/etc/path/to/configfile/com.example.MyService.config.TEMPLATE` will be copied to `/etc/path/to/configfile/com.example.MyService.config` with the values replaced). To avoid that a configuration is deleted upon regular package installation, the target node (without `.TEMPLATE`) has to be excluded via exclude rule of filter statement. 

Example:

* Node to be added: `/etc/replication/agents.author/publish`
* Node contained in the package: `/etc/replication/agents.author/publish/jcr:content.TEMPLATE` (don't include `/etc/replication/agents.author/publish/jcr:content`!)
* Filter rule with exclude (to ensure it is not deleted because it's not in package): 
```
/etc/replication/agents.author/publish
  \- exclude /etc/replication/agents.author/publish/jcr:content
```


### Package Property applySystemEnvForPaths

Alternatively, the vault package property `applySystemEnvForPaths` can be given to explicitly list properties and files where the replacement shall take place (opposed to use the automatic `.TEMPLATE` mechanism described above):

```
/etc/path/to/node/jcr:content@testValue,
/etc/path/to/node/jcr:content@testValueOther,
/etc/path/to/configfile/com.example.MyService.config
```

Multiple values can be given separated by whitespace and/or comma. This approach has the downside that nodes are "double-saved" (first one on package installation itself, second one on install hook phase `INSTALLED` with replaced parameters).


## Variable Value Sources

The package property `applySystemEnvSources` is used to defined sources (default: `SystemProperties,JCR,OsEnvVars`). For each variable each source is asked, the first source that resonds with a value will be used (the installation log in Package Manager clearly logs where variable values come from).

### Source "OsEnvVars"

Will use the immutable system environment as provided by the OS. All values with `.` have to be set with `_` as dots are not allowed as environment variables (e.g. `my.test.var` as set in package has to be set as env variable `my_test_var`). 

### Source "JCR"

Reads the node `/etc/system-env` and uses all its properties to set the variables. This is particular useful to be used for CI/CD tools like Jenkins to easily set values via REST (Sling POST Servlet).

If the node `/etc/system-env` does not exist, this source is ignored (no error is thrown).

NOTE: Obviously Jenkins could also post to the actual config locations and change them in place. However then Jenkins has to re-send a lot of boilerplate around the few values that are actually env-specific. Also the paths may be scattered all around the repo which makes the Jenkins-side unnecessarily complicated. Using apply-system-env-install-hook with values from /etc/system-env clearly separates "regular config" from system-env.

### Source "ZooKeeper"

Configuration variables for a whole environment can be maintained in ZooKeeper and automatically be consumed by multiple AEM instances (e.g. author cluster + 4 publishers).

To configure ZooKeeper itself, the following JRE System Parameters have to be set (along with the source in the package):

* `applysysenv.zookeeper.hosts` (required): the ZooKeeper hosts (e.g. `localhost:2181,localhost:2182,localhost:2183`
* `applysysenv.zookeeper.path` (required):  The path where the config resides, e.g. `/configs/aem-env.properties`. 
* `applysysenv.zookeeper.overrideSuffix` (optional): This is useful if some properties are different for only one particular instance (while `aem-env.properties` describes all instances of an environment) - e.g. for replication, if publishers have their own flush url, `replication.publish.flushUrl@publish1` and `replication.publish.flushUrl@publish2` can be used (while e.g. publish1 would have the system property `applysysenv.zookeeper.overrideSuffix=publish1` set)

One way of loading a file into ZooKeeper is using `zkCli.sh`: 
```
zkCli.sh set /configs/aem-env.properties "`cat aem-env.properties`"
```

If the source `ZooKeeper` is configured in package, but `applysysenv.zookeeper.hosts` or `applysysenv.zookeeper.path` is not set, a warning is logged an the source will not provide any values.

Also, for this source to work the zookeeper jar has to be put in crx-quickstart/install (or be installed via a different means).

### Source "SystemProperties"

Will use system properties to set variables. Mostly useful for manual intervention, hence first source in default setting of `applySystemEnvSources`.


# Using The Install Hook in a Package
Ensure the hook ins copied into the package:

```
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <version>3.0.2</version>
        <executions>
            <execution>
                <id>copy-hook-into-package</id>
                <phase>generate-resources</phase>
                <goals>
                    <goal>copy</goal>
                </goals>
                <configuration>
                    <artifactItems>
                        <artifactItem>
                            <groupId>biz.netcentric.aem.sysenvtools</groupId>
                            <artifactId>apply-system-env-install-hook</artifactId>
                            <version>1.2.0</version>
                        </artifactItem>
                    </artifactItems>
                    <outputDirectory>${project.build.directory}/vault-work/META-INF/vault/hooks</outputDirectory>
                </configuration>
            </execution>
        </executions>
    </plugin>
            
```

Configure it via vault package properties:


```
    <plugin>
        <groupId>com.day.jcr.vault</groupId>
        <artifactId>content-package-maven-plugin</artifactId>
        <version>0.5.1</version>
        <extensions>true</extensions>
        <configuration>
            <group>Netcentric</group>
            <filterSource>src/main/META-INF/vault/filter.xml</filterSource>
            <properties>
                <applySystemEnvSources>SystemProperties,ZooKeeper,OsEnvVars</applySystemEnvSources>
                <!-- paths to adjust, can be left out if '.TEMPLATE' paths are used -->
                <applySystemEnvForPaths>
                    /etc/path/to/node/jcr:content@testValue,
                    /etc/path/to/node/jcr:content@testValueOther,
                    /etc/path/to/configfile/com.example.MyService.config
                </applySystemEnvForPaths>
                <!-- default is false -->
                <failForMissingEnvVars>true</failForMissingEnvVars> 
            </properties>                    
            <targetURL>http://${crx.host}:${crx.port}/crx/packmgr/service.jsp</targetURL>
        </configuration>
    </plugin>
```

# Troubleshooting for OS environment variable replacement 

The environment variables need to be set to the env of the AEM process (since the install hook runs there). To check on OS-level if the
env was set correctly, the following command can be used:

```
sudo cat /proc/<pid>/environ | tr '\0' '\n' # if you know the pid
sudo cat /proc/$(pgrep -f  quickstart.jar)/environ | tr '\0' '\n' # auto-query the pid 
```

Also, on AEM/JRE level the [Groovy Console](https://github.com/icfnext/aem-groovy-console) can be used to show the environment as the JRE gets to see it by running the following simple script:

```
System.getenv().each{ println it }
return
```

# Automatically update changed configuration values in AEM
When updating configuration values, normally they only become active upon **manual or automatically triggered re-installation** of configuration package that contains the `apply-system-env-install-hook`. 

However, when using the sources `ZooKeeper` or `JCR` it is possible to automatically reinstall the package containing the install hook.

## Install bundle system-env-change-listener 

Install the bundle `system-env-change-listener-x.x.x.jar` to AEM. The easiest way to do this is to drop it in `crx-quickstart/install`.

## Ensure the bundle system-env-change-listener may install packages

Create a service user (e.g. `sysenv-package-installer`) with permissions to install packages and create a user mapping as follows:
`org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended-sysenv-listener.config`

```
user.mapping=["biz.netcentric.aem.sysenvtools.system-env-change-listener\=sysenv-package-installer"]
```
