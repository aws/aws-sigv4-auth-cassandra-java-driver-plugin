package software.aws.mcs.auth;

/*-
 * #%L
 * AWS SigV4 Auth Java Driver 4.x Plugin
 * %%
 * Copyright (C) 2020-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.net.InetSocketAddress;
import java.util.ArrayList;
import javax.net.ssl.SSLContext;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

public class TestSigV4 {
    static String[] DEFAULT_CONTACT_POINTS = {"127.0.0.1:9042"};

    public static void main(String[] args) throws Exception {
        String[] contactPointsRaw = DEFAULT_CONTACT_POINTS;

        if (args.length == 1) {
            contactPointsRaw = args[0].split(",");
        } else if (args.length > 1) {
            System.out.println("Usage: TestSigV4 [<contact points, comma separated, 'IP:port' format>]");
            System.exit(-1);
        }

        ArrayList<InetSocketAddress> contactPoints = new ArrayList<>(contactPointsRaw.length);

        for (int i = 0; i < contactPointsRaw.length; i++) {
            String[] parts = contactPointsRaw[i].split(":");
            contactPoints.add(InetSocketAddress.createUnresolved(parts[0], Integer.parseInt(parts[1])));
        }

        System.out.println("Using endpoints: " + contactPoints);

        // The CqlSession object is the main entry point of the driver.
        // It holds the known state of the actual Cassandra cluster (notably the Metadata).
        // This class is thread-safe, you should create a single instance (per target Cassandra cluster), and share
        // it throughout your application.
        try (CqlSession session = CqlSession.builder()
             .addContactPoints(contactPoints)
             .withAuthProvider(new SigV4AuthProvider())
             .withSslContext(SSLContext.getDefault())
             .withLocalDatacenter("us-west-2")
             .build()) {

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
}
