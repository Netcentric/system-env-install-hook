Apply Env Install Hook for AEM/CRX
================================================

**DISCLAIMER:**
This hook should be used sparsely for very limited use cases only. Almost all OSGi configurations should be bound to runmodes as well-established mechanism to configure different environments. Also, in terms of majority, this project is in incubator status while the approach is being validated. 

# Overview

Replaces variables in content packages for files (e.g. useful for OSGi configurations) and node properties (e.g. useful for replication agents).

Variables can be in form `${myVar}` or  `${myVar:defaultVal}` - if the var can not be found in any sources, for the first example `${myVar}` remains as is, for the second example `defaultVal` is used.

# Configuration

## Including variables in packages

### Package Property applyEnvVarsForPaths

The vault package property `applyEnvVarsForPaths` can be given to explicitly list properties and files where the replacement shall take place:

```
/etc/path/to/node/jcr:content@testValue,
/etc/path/to/node/jcr:content@testValueOther,
/etc/path/to/configfile/com.example.MyService.config
```

Multiple values can be given separated by whitespace and/or comma.

### Nodes with suffix .TEMPLATE

Additionally to nodes that explictly configured to be adjusted, any nodes that end with `.TEMPLATE` will be moved to the same name without `.TEMPLATE` (e.g. `/etc/path/to/configfile/com.example.MyService.config.TEMPLATE` will be moved to `/etc/path/to/configfile/com.example.MyService.config` with the values replaced). This can be useful to avoid a "double-save" (first one on package installation itself, second one on install hook phase `INSTALLED`). To avoid that a configuration is deleted upon regular package installation, the target node (without `.TEMPLATE`) has to be excluded via exclude rule of filter statement.

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


### Source "SystemProperties"

Will use system properties to set variables. Mostly useful for manual intervention, hence first source in default setting of `applySystemEnvSources`.


# Using The Hook in a Package
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
                            <groupId>biz.netcentric.aem</groupId>
                            <artifactId>apply-system-env-install-hook</artifactId>
                            <version>1.0.2</version>
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
                <applyEnvVarsForPaths>
                    /etc/path/to/node/jcr:content@testValue,
                    /etc/path/to/node/jcr:content@testValueOther,
                    /etc/path/to/configfile/com.example.MyService.config
                </applyEnvVarsForPaths>
                <!-- default is false -->
                <failForMissingEnvVars>true</failForMissingEnvVars> 
            </properties>                    
            <targetURL>http://${crx.host}:${crx.port}/crx/packmgr/service.jsp</targetURL>
        </configuration>
    </plugin>
````