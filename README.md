# Overview

A sample Spring Boot application demonstrating how to use CassandraUnit and an embedded instance of Cassandra 4.0 to run JUnit tests on Java 17:
* CassandraUnit has been inactive since 2020 and its latest version only supports Cassandra 3.11
* Cassandra 4.0 and above do not support Java 17
* Spring Boot uses some dependencies of Cassandra but at versions with incompatible method signatures

Features:
* POM tweaks and customized Cassandra classes to make CassandraUnit and Cassandra 4.0 work with Spring Boot 3.5 on Java 17
* Custom query methods utilizing secondary indexes
* A custom repository method that performs bulk insert asynchronously with number of in-flight inserts limited using a semaphore
* A custom repository base class that provides a insertIfNotExists method that retries on CASWriteUnknownException and partition-oriented methods
* A task lock implemented using Cassandra's lightweight transaction to ensure that a task is only executed by one thread at any given time
* Two Runnable wrapper classes for synchronizing execution of scheduled tasks across multiple threads/processes
* Custom converters for converting between enums and their text values stored in Cassandra
* A helper class for selecting rows and then executing a CQL statement with bind variables for each selected row using values extracted from the row


# Changes to CassandraUnit

Customized version of `cu-cassandra.yaml` that CassandraUnit reads from the class path in `src/test/resources/cu-cassandra.yaml`. This version is based on the default `cassandra.yaml` from Cassandra 4.0.19.


# Changes to Cassandra

Customized versions of the following classes from version 4.0.19 in `src/test`:

* `org.apache.cassandra.config.YamlConfigurationLoader`: Minor changes to adapt to the incompatible API of the newer version of SnakeYAML used by Spring Boot 3.5.
* `org.apache.cassandra.utils.ObjectSizes`: Back-ported minor changes from Cassandra 5.0 to adapt to the incompatible API of the newer version of Jamm that works on Java 17.


# Changes to com.boundary:high-scale-lib

Original source files from version 1.0.6 in `src/test` excluding the following packages:

* `com.boundary` and `com.boundary.high_scale_lib`: Contain classes not in the original high-scale-lib and not used by Cassandra.
* `java.util` and `java.util.concurrent`: Contain high-performance implementation of `Hashtable` and `ConcurrentHashMap` but JPMS disallows overriding classes in the `java.base` module.


# How to Eliminate Errors in Eclipse

Uncheck `Use '--release' option` in the project's Java Compiler settings.

Add the JPMS options in https://github.com/apache/cassandra/blob/cassandra-4.0.19/conf/jvm11-clients.options and a few additional ones (for Java 17) to the `.classpath` file of the project:

```
<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-17">
	<attributes>
		<attribute name="maven.pomderived" value="true"/>
		<!-- Start of JPMS options for Cassandra -->
		<attribute name="module" value="true"/>
		<attribute name="add-exports" value="java.base/jdk.internal.misc=ALL-UNNAMED:java.base/jdk.internal.ref=ALL-UNNAMED:java.base/sun.nio.ch=ALL-UNNAMED:java.management.rmi/com.sun.jmx.remote.internal.rmi=ALL-UNNAMED:java.rmi/sun.rmi.registry=ALL-UNNAMED:java.rmi/sun.rmi.server=ALL-UNNAMED:java.sql/java.sql=ALL-UNNAMED"/>
		<attribute name="add-opens" value="java.base/java.lang.module=ALL-UNNAMED:java.base/jdk.internal.loader=ALL-UNNAMED:java.base/jdk.internal.ref=ALL-UNNAMED:java.base/jdk.internal.reflect=ALL-UNNAMED:java.base/jdk.internal.math=ALL-UNNAMED:java.base/jdk.internal.module=ALL-UNNAMED:java.base/jdk.internal.util.jar=ALL-UNNAMED:jdk.management/com.sun.management.internal=ALL-UNNAMED:java.base/java.io=ALL-UNNAMED:java.base/java.lang=ALL-UNNAMED:java.base/java.nio=ALL-UNNAMED:java.base/java.util=ALL-UNNAMED:java.base/java.util.concurrent=ALL-UNNAMED:java.base/java.util.concurrent.atomic=ALL-UNNAMED:java.base/sun.nio.ch=ALL-UNNAMED"/>
		<!-- End of JPMS options for Cassandra -->
	</attributes>
</classpathentry>
```
