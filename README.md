# What

This package implements a SigV4 auth plugin for the official Datastax Cassandra driver. It depends on the AWS Java SDK,
using `AWSCredentialsProvider` to obtain SigV4 credentials. Because the IAuthenticator interface operates at the level
of `InetSocketAddress`, it cannot infer the region from the endpoint. Instead, you can either provide the region in the
constructor programmatically, via the `AWS_REGION` environment variable, or via the `aws.region` system property to
match the endpoint you're communicating with.

The full documentation for the plugin can be found at
https://docs.aws.amazon.com/mcs/latest/devguide/programmatic.credentials.html#programmatic.credentials.SigV4_MCS.

# Example Usage

Please see the example code at https://github.com/aws-samples/aws-sigv4-auth-cassandra-java-driver-examples
