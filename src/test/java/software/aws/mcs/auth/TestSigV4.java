/*
 *   Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package software.aws.mcs.auth;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

/**
 * Test Command line to verify auth mechanism.
 * To use specific endpoint pass args -> <Contact Point> <port>
 *
 * Specify other properties by using environment variables:
 * AWS_ACCESS_KEY_ID -> access key
 * AWS_SECRET_ACCESS_KEY -> secret password
 * AWS_REGION -> region to use.
 */
public class TestSigV4 {
    static String DEFAULT_CONTACT_POINT = "cassandra.us-east-1.amazonaws.com";

    public static void main(String[] args) throws Exception
    {
        String endPoint = DEFAULT_CONTACT_POINT;
        if (args.length > 0) {
            endPoint = args[0];
        }
        String port = "9142";
        if (args.length > 1) {
            port = args[1];
        }

        System.out.println(String.format("Using endpoint: %s:%s", endPoint, port));
        int portNumber = Integer.parseInt(port);

        // The CqlSession object is the main entry point of the driver.
        // It holds the known state of the actual Cassandra cluster (notably the Metadata).
        // This class is thread-safe, you should create a single instance (per target Cassandra cluster), and share
        // it throughout your application.
        Session session = Cluster.builder()
                                 .addContactPoint(endPoint)
                                 .withPort(portNumber)
                                 .withAuthProvider(new SigV4AuthProvider())
                                 .withSSL()
                                 .build()
                                 .connect();

            // We use execute to send a query to Cassandra. This returns a ResultSet, which is essentially a collection
            // of Row objects.
            ResultSet rs = session.execute("select release_version from system.local");
            //  Extract the first row (which is the only one in this case).
            Row row = rs.one();

            // Extract the value of the first (and only) column from the row.
            String releaseVersion = row.getString("release_version");
            System.out.printf("Cassandra version is: %s%n", releaseVersion);
    }
}
