# CHANGELOG

## [4.0.8] - 2022-12-06

Updated Jackson-databind version to 2.13.4.1.

## [4.0.7] - 2022-10-05

Updated Jackson version to 2.13.4 to address CVE-2022-42004. This is a transitive dependency of the driver and the SigV4
plugin does not use Jackson directly.

## [4.0.6] - 2022-04-21

Updated Jackson version to 2.13.2

## [4.0.5] - 2022-02-01

* Fixed a bug with region case sensitivity to match the SigV4 spec
* Updated the driver dependency to 4.13.0, including overriding transitive Jackson dependencies to ensure currency
* Removed the Maven license plugin in favor of manual management

## [4.0.4] - 2021-01-20

Updated to AWS 2 SDK.


## [4.0.3] - 2020-05-15

Updated the plugin to fix newlines used in the SigV4 signature on Windows. This resolves [Issue
#12](https://github.com/aws/aws-sigv4-auth-cassandra-java-driver-plugin/issues/12).

## [4.0.2] - 2020-03-31

Changed the plugin to use a `DateTimeFormatter` to ensure precisely 3 digits of millisecond precision for the SigV4
timestamp. A change between JDK8 and JDK11 caused the output of `java.time.Instant.toString()` to change from 3 to 6
digits, which results in a signature mismatch.

## [4.0.1] - 2020-03-31

No changes to code, but we are re-publishing the 4.0.0 release compiled with Java 8 to fix [Issue
#5](https://github.com/aws/aws-sigv4-auth-cassandra-java-driver-plugin/issues/5). We had to use a new version number
because Maven releases are considered immutable, and we cannot replace the existing 4.0.0 artifact.

## [4.0.0] - 2020-03-17

Initial Release
