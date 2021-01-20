# IMPORTANT: Latest Version

The current version is 4.0.4. Please see the [changelog](./CHANGELOG.md) for details on version history.

# What

This package implements an authentication plugin for the open-source Datastax Java Driver for Apache Cassandra. The driver enables you to add authentication information to your API requests using the AWS Signature Version 4 Process (SigV4). Using the plugin, you can provide users and applications short-term credentials to access Amazon Keyspaces (for Apache Cassandra) using AWS Identity and Access Management (IAM) users and roles.

The plugin depends on the AWS SDK for Java. It uses `AWSCredentialsProvider` to obtain credentials. Because the IAuthenticator interface operates at the level of `InetSocketAddress`, you must specify the service endpoint to use for the connection.
You can provide the Region in the constructor programmatically, via the `AWS_REGION` environment variable, or via the `aws.region` system property.

The full documentation for the plugin is available at
https://docs.aws.amazon.com/keyspaces/latest/devguide/programmatic.credentials.html#programmatic.credentials.SigV4_KEYSPACES.

# Example Usage

For example code, see https://github.com/aws-samples/aws-sigv4-auth-cassandra-java-driver-examples.

# Using the Plugin

The following sections describe how to use the authentication plugin for the open-source DataStax Java Driver for Cassandra to access Amazon Keyspaces.

## SSL Configuration

The first step is to get an Amazon digital certificate to encrypt your connections using Transport Layer Security (TLS). The DataStax Java driver must use an SSL trust store so that the client SSL engine can validate the Amazon Keyspaces certificate on connection. To use the trust store and create a certificate, see [Using a Cassandra Java Client Driver to Access Amazon Keyspaces Programmatically](https://docs.aws.amazon.com/keyspaces/latest/devguide/programmatic.drivers.html#using_java_driver).

## Region Configuration

Before you can start using the plugin, you must configure the AWS Region that the plugin will use when authenticating. This is required because SigV4 signatures are Region-specific. For example, if you are connecting to the `cassandra.us-east-2.amazonaws.com` endpoint, the Region must be `us-east-2`. For a list of available AWS Regions and endpoints, see [Service Endpoints for Amazon Keyspaces](https://docs.aws.amazon.com/keyspaces/latest/devguide/programmatic.endpoints.html).

You can specify the Region using one of the following four methods:

* Environment Variable
* System Property
* Constructor
* Configuration

## Environment Variable

You can use the `AWS_REGION` environment variable to match the endpoint that you are communicating with by setting it as part of your application start-up, as follows.

``` shell
$ export AWS_Region=us-east-1
```
## System Property

You can use the `aws.region` Java system property by specifying it on the command line, as follows.

``` shell
$ java -Daws.region=us=east-1 ...
```

## Constructor

One of the constructors for `software.aws.mcs.auth.SigV4AuthProvider` takes a `String` representing the Region that will be used for that instance.

## Configuration

Set the Region explicitly in your `advanced.auth-provider.class` configuration (see example below), by specifying the `advanced.auth-provider.aws-region` property.

## Add the Authentication Plugin to the Application

The authentication plugin supports version 4.x of the DataStax Java Driver for Cassandra. If you’re using Apache Maven, or a build system that can use Maven dependencies, add the following dependencies to your `pom.xml` file.

``` xml
<dependency>
    <groupId>software.aws.mcs</groupId>
    <artifactId>aws-sigv4-auth-cassandra-java-driver-plugin</artifactId>
    <version>4.0.4</version>
</dependency>
```

## How to use the Authentication Plugin

When using the open-source DataStax Java driver, the connection to your Amazon Keyspaces endpoint is represented by the `CqlSession` class. To create the `CqlSession`, you can either configure it programmatically using the `CqlSessionBuilder` class (accessed via `CqlSession.builder()`) or with the configuration file.

### Programmatically Configure the Driver

When using the DataStax Java driver, you interact with Amazon Keyspaces primarily through the `CQLSession` class. You can create an instance of `CqlSession` using the `CqlSession.builder()` function. `CqlSession.builder()` enables you to specify another authentication provider for the session by using the with `withAuthProvider` function.

To use the authentication plugin, you set a Region-specific instance of SigV4AuthProvider as the authentication provider, as in the following example.

1. Call `addContactPoints` on the builder with a collection of `java.net.InetSocketAddress` instances corresponding to the endpoints for your Region. Contact points are the endpoints that the driver will connect to. For a full list of endpoints and Regions in the documentation, see [Service Endpoints for Amazon Keyspaces](https://docs.aws.amazon.com/keyspaces/latest/devguide/programmatic.endpoints.html).
1. Add an SSL context by calling `withSslContext` on the builder. This uses the trust store defined previously to negotiate SSL on the connection to the endpoints. SSL is required for Amazon Keyspaces. Without this setting, connections will time out and fail.
1. Set the local data center to the region name, in this example it is `us-east-2`. The local data center is used by the driver for routing of requests, and it is required when the builder is constructed with `addContactPoints`.
1. Set the authentication provider to a new instance of `software.aws.mcs.auth.SigV4AuthProvider`. The `SigV4AuthProvider` is the authentication handler provided by the plugin for performing SigV4 authentication. You can specify the Region for the endpoints that you’re using in the constructor for `SigV4AuthProvider`, as in the following example. Or, you can set the environment variable or system property as shown previously.

The following code example demonstrates the previous steps.

``` java
    List<InetSocketAddress> contactPoints =
      Collections.singletonList(
       InetSocketAddress.createUnresolved("cassandra.us-east-2.amazonaws.com", 9142));

    try (CqlSession session = CqlSession.builder()
      .addContactPoints(contactPoints)
      .withSslContext(SSLContext.getDefault())
      .withLocalDatacenter("us-east-2")
      .withAuthProvider(new SigV4AuthProvider("us-east-2"))
      .build()) {
      // App code here...
    }
```

### Use a Configuration File

To use the configuration file, set the `advanced.auth-provider.class` to `software.aws.mcs.auth.SigV4AuthProvider`. You can also set the region, local data center and enable SSL in the configuration.

1. Set the `advanced.auth-provider.class` to `software.aws.mcs.auth.SigV4AuthProvider`.
1. Set `basic.load-balancing-policy.local-datacenter` to the region name. In this case, use `us-east-2`.

The following is an example of this.

``` text
    datastax-java-driver {
        basic.load-balancing-policy {
            class = DefaultLoadBalancingPolicy
            local-datacenter = us-east-2
        }
        advanced {
            auth-provider = {
                class = software.aws.mcs.auth.SigV4AuthProvider
                aws-region = us-east-2
            }
            ssl-engine-factory {
                class = DefaultSslEngineFactory
            }
        }
    }
```
