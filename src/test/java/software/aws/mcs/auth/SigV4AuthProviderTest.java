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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SigV4AuthProviderTest {
    @Test
    public void testNonceExtraction() {
        final String TEST_NONCE = "1234abcd1234abcd1234abcd1234abcd";
        // Valid nonce is 32 characters, so we test that here
        assertEquals(ByteBuffer.wrap(TEST_NONCE.getBytes(StandardCharsets.UTF_8)),
                     ByteBuffer.wrap(SigV4AuthProvider.extractNonce(ByteBuffer.wrap(("nonce=" + TEST_NONCE)
                                                                                    .getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testShortNonceExtraction() {
        assertThrows(IllegalArgumentException.class, () -> {
                SigV4AuthProvider.extractNonce(ByteBuffer.wrap("nonce=too_short".getBytes(StandardCharsets.UTF_8)));
            });
    }

    @Test
    public void testNonceExtractionFailure() {
        assertThrows(IllegalArgumentException.class, () -> {
                SigV4AuthProvider.extractNonce(ByteBuffer.wrap("nothing to see here".getBytes(StandardCharsets.UTF_8)));
            });
    }

    @Test
    public void testSimpleIndexOf() {
        byte[] target = {0, 1, 2, 42, 24, 4, 5};
        byte[] pattern = {42, 24};
        assertEquals(3, SigV4AuthProvider.indexOf(target, pattern));
    }

    @Test
    public void testLeadingIndexOf() {
        byte[] target = {42, 24, 1, 2, 3, 4, 5};
        byte[] pattern = {42, 24};
        assertEquals(0, SigV4AuthProvider.indexOf(target, pattern));
    }

    @Test
    public void testTrailingIndexOf() {
        byte[] target = {1, 2, 3, 4, 5, 42, 24};
        byte[] pattern = {42, 24};
        assertEquals(5, SigV4AuthProvider.indexOf(target, pattern));
    }

    @Test
    public void testPartialIndexOf() {
        byte[] target = {1, 2, 42, 24, 3, 4, 5};
        byte[] pattern = {42, 24, 42};
        assertEquals(-1, SigV4AuthProvider.indexOf(target, pattern));
    }

    @Test
    public void testPartialTrailingIndexOf() {
        byte[] target = {1, 2, 3, 4, 5, 42};
        byte[] pattern = {42, 24, 42};
        assertEquals(-1, SigV4AuthProvider.indexOf(target, pattern));
    }


    @Test
    public void testGetRoleNameFromArn() {
        String arn = "arn:aws:iam::ACCOUNT_ID:role/keyspaces-act2-role";
        assertEquals("keyspaces-act2-role", SigV4AuthProvider.getRoleNameFromArn(arn));
    }

    @Test
    public void testGetRoleNameFromArnFailure() {
        assertThrows(IllegalArgumentException.class, () -> SigV4AuthProvider.getRoleNameFromArn(""));
        assertThrows(IllegalArgumentException.class, () -> SigV4AuthProvider.getRoleNameFromArn("roleName"));
        assertThrows(IllegalArgumentException.class, () -> SigV4AuthProvider.getRoleNameFromArn("illegalerolearn:rolename"));
    }




}
