Apply Env Install Hook for AEM/CRX
================================================

**DISCLAIMER:**
This hook should be used sparsely for very limited use cases only. Almost all OSGi configurations should be bound to runmodes as well-established mechanism to configure different environments. Also, in terms of majority, this project is in incubator status while the approach is being validated. 

# Overview

Replaces variables in content packages for files (e.g. useful for OSGi configurations) and node properties (e.g. useful for replication agents).

Variables can be in form `${myVar}` or  `${myVar:defaultVal}` - if the env var does not exist, for the first example `${myVar}` remains as is, for the second example `defaultVal` is used.

Also see https://issues.apache.org/jira/browse/JCRVLT-254

# Configuration

## Package Property applyEnvVarsForPaths

The vault package property `applyEnvVarsForPaths` can be given to explicitly list properties and files where the replacement shall take place:

```
/etc/path/to/node/jcr:content@testValue,
/etc/path/to/node/jcr:content@testValueOther,
/etc/path/to/configfile/com.example.MyService.config
```

Multiple values can be given separated by whitespace and/or comma.

## Nodes with suffix .TEMPLATE

Additionally to nodes that explictly configured to be adjusted, any nodes that end with `.TEMPLATE` will be moved to the same name without `.TEMPLATE` (e.g. `/etc/path/to/configfile/com.example.MyService.config.TEMPLATE` will be moved to `/etc/path/to/configfile/com.example.MyService.config` with the values replaced). This can be useful to avoid a "double-save" (first one on package installation itself, second one on install hook phase `INSTALLED`). To avoid that a configuration is deleted upon regular package installation, the target node (without `.TEMPLATE`) has to be excluded via exclude rule of filter statement.

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
                            <version>1.0.1-SNAPSHOT</version>
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
                <applyEnvVarsForPaths>
                    /etc/path/to/node/jcr:content@testValue,
                    /etc/path/to/node/jcr:content@testValueOther,
                    /etc/path/to/configfile/com.example.MyService.config
                </applyEnvVarsForPaths>
            </properties>                    
            <targetURL>http://${crx.host}:${crx.port}/crx/packmgr/service.jsp</targetURL>
        </configuration>
    </plugin>
````