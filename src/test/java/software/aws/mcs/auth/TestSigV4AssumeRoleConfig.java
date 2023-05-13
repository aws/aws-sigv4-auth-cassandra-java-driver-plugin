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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Optional;

public class TestSigV4AssumeRoleConfig {
    static String KEYSPACES_DEFAULT_CONF="keyspaces-reference-norole.conf";
    /**
     * Before executing this test, ensure that KeySpaces tables are created.
     * Refer ddl.cql and dml.cql to create and populate tables.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String keySpacesConf=KEYSPACES_DEFAULT_CONF;
        if(args.length>1){
            keySpacesConf=args[0];
            System.out.println("Using key spaces config file: "+keySpacesConf);
            keySpacesConf=Optional.of(keySpacesConf).filter(StringUtils::isBlank).orElse(KEYSPACES_DEFAULT_CONF);
        }

        URL url = TestSigV4AssumeRoleConfig.class.getClassLoader().getResource(keySpacesConf);

        File file = new File(url.toURI());
        // The CqlSession object is the main entry point of the driver.
        // It holds the known state of the actual Cassandra cluster (notably the Metadata).
        // This class is thread-safe, you should create a single instance (per target Cassandra cluster), and share
        // it throughout your application.
        try (CqlSession session = CqlSession.builder()
                .withConfigLoader(DriverConfigLoader.fromFile(file))
             .build()) {

            // We use execute to send a query to Cassandra. This returns a ResultSet, which is essentially a collection
            // of Row objects.
            ResultSet rs = session.execute("select * from testkeyspace.testconf");
            //  Extract the first row (which is the only one in this case).
            Row row = rs.one();

            // Extract the value of the first (and only) column from the row.
            String releaseVersion = row.getString("category");
            System.out.printf("Cassandra version is: %s%n", releaseVersion);
        }
    }
}
