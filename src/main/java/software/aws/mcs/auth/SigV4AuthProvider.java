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

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;

import org.apache.commons.codec.binary.Hex;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.internal.AWS4SignerUtils;
import com.amazonaws.auth.internal.SignerConstants;
import com.datastax.driver.core.AuthProvider;
import com.datastax.driver.core.Authenticator;
import com.datastax.driver.core.exceptions.AuthenticationException;

/**
 * This auth provider can be used with Amazon Keyspaces service to
 * authenticate with SigV4. It uses the AWSCredentialsProvider
 * interface provided by the official AWS Java SDK to provide
 * credentials for signing.
 */
public class SigV4AuthProvider implements AuthProvider {
    private static final byte[] SIGV4_INITIAL_RESPONSE_BYTES = "SigV4\0\0".getBytes(StandardCharsets.UTF_8);
    private static final ByteBuffer SIGV4_INITIAL_RESPONSE;

    static {
        ByteBuffer initialResponse = ByteBuffer.allocate(SIGV4_INITIAL_RESPONSE_BYTES.length);
        initialResponse.put(SIGV4_INITIAL_RESPONSE_BYTES);
        initialResponse.flip();
        // According to the driver docs, it's safe to reuse a
        // read-only buffer, and in our case, the initial response has
        // no sensitive information
        // Note that the older version of the driver does not accept a read only buffer.
        SIGV4_INITIAL_RESPONSE = initialResponse;
    }

    private static final int AWS_FRACTIONAL_TIMESTAMP_DIGITS = 3; // SigV4 expects three digits of nanoseconds for timestamps
    private static final DateTimeFormatter timestampFormatter =
        (new DateTimeFormatterBuilder()).appendInstant(AWS_FRACTIONAL_TIMESTAMP_DIGITS).toFormatter();


    private static final byte[] NONCE_KEY = "nonce=".getBytes(StandardCharsets.UTF_8);
    private static final int EXPECTED_NONCE_LENGTH = 32;

    // These are static values because we don't need HTTP, but SigV4 assumes some amount of HTTP metadata
    private static final String CANONICAL_SERVICE = "cassandra";

    private final AWSCredentialsProvider credentialsProvider;
    private final String signingRegion;

    /**
     * Create a new Provider, using the
     * DefaultAWSCredentialsProviderChain as its credentials provider.
     * The signing region is taking from the AWS_DEFAULT_REGION
     * environment variable or the "aws.region" system property.
     */
    public SigV4AuthProvider() {
        this(DefaultAWSCredentialsProviderChain.getInstance(), null);
    }

    /**
     * Create a new Provider, using the specified region.
     * @param region the region (e.g. us-east-1) to use for signing. A
     * null value indicates to use the AWS_REGION environment
     * variable, or the "aws.region" system property to configure it.
     */
    public SigV4AuthProvider(final String region) {
        this(DefaultAWSCredentialsProviderChain.getInstance(), region);
    }

    /**
     * Create a new Provider, using the specified AWSCredentialsProvider and region.
     * @param credentialsProvider the credentials provider used to obtain signature material
     * @param region the region (e.g. us-east-1) to use for signing. A
     * null value indicates to use the AWS_REGION environment
     * variable, or the "aws.region" system property to configure it.
     */
    public SigV4AuthProvider(@NotNull AWSCredentialsProvider credentialsProvider, final String region) {
        this.credentialsProvider = credentialsProvider;

        String rawSigningRegion;

        if (region == null) {
            if (System.getProperty(SDKGlobalConfiguration.AWS_REGION_SYSTEM_PROPERTY) != null) {
                rawSigningRegion = System.getProperty(SDKGlobalConfiguration.AWS_REGION_SYSTEM_PROPERTY);
            } else {
                rawSigningRegion = System.getenv(SDKGlobalConfiguration.AWS_REGION_ENV_VAR);
            }
        } else {
            rawSigningRegion = region;
        }

        if (rawSigningRegion == null) {
            throw new IllegalStateException(
                "A region must be specified by constructor, AWS_REGION env variable, or aws.region system property"
            );
        }

        // Ensure that the region is lower case for signing purposes
        this.signingRegion = rawSigningRegion.toLowerCase();
    }

    @Override
    public Authenticator newAuthenticator(InetSocketAddress inetSocketAddress, String s) throws AuthenticationException {
        return new SigV4Authenticator();
    }

    /**
     * This authenticator performs SigV4 MCS authentication.
     */
    public class SigV4Authenticator implements Authenticator {

        @Override
        public byte[] initialResponse() {
            return SIGV4_INITIAL_RESPONSE.array();
        }

        @Override
        public byte[] evaluateChallenge(byte[] bytes) {
            try {
                byte[] nonce = extractNonce(ByteBuffer.wrap(bytes));

                Instant requestTimestamp = Instant.now();

                AWSCredentials credentials = credentialsProvider.getCredentials();

                String signature = generateSignature(nonce, requestTimestamp, credentials);

                String response =
                    String.format("signature=%s,access_key=%s,amzdate=%s",
                                  signature,
                                  credentials.getAWSAccessKeyId(),
                                  timestampFormatter.format(requestTimestamp));

                if (credentials instanceof AWSSessionCredentials) {
                    response = response + ",session_token=" + ((AWSSessionCredentials)credentials).getSessionToken();
                }

                return response.getBytes(StandardCharsets.UTF_8);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("This platform does not support the UTF-8encoding", e);
            }
        }

        @Override
        public void onAuthenticationSuccess(byte[] bytes) {
            //do nothing;
        }


    }

    /**
     * Extracts the nonce value from the challenge
     */
    static byte[] extractNonce(ByteBuffer challengeBuffer) {
        byte[] challenge = new byte[challengeBuffer.remaining()];
        challengeBuffer.get(challenge);

        int nonceStart = indexOf(challenge, NONCE_KEY);

        if (nonceStart == -1) {
            throw new IllegalArgumentException("Did not find nonce in SigV4 challenge: "
                                               + new String(challenge, StandardCharsets.UTF_8));
        }

        // We'll start extraction right after the nonce bytes
        nonceStart += NONCE_KEY.length;

        int nonceEnd = nonceStart;

        // Advance until we find the comma or hit the end of input
        while (nonceEnd < challenge.length && challenge[nonceEnd] != ',') {
            nonceEnd++;
        }

        int nonceLength = nonceEnd - nonceStart;

        if (nonceLength != EXPECTED_NONCE_LENGTH) {
            throw new IllegalArgumentException("Expected a nonce of " + EXPECTED_NONCE_LENGTH
                                               + " bytes but received " + nonceLength);
        }

        return Arrays.copyOfRange(challenge, nonceStart, nonceEnd);
    }

    private String generateSignature(byte[] nonce, Instant requestTimestamp, AWSCredentials credentials) throws UnsupportedEncodingException {
        String credentialScopeDate = AWS4SignerUtils.formatDateStamp(requestTimestamp.toEpochMilli());

        String signingScope = String.format("%s/%s/%s/aws4_request", credentialScopeDate, signingRegion, CANONICAL_SERVICE);

        String nonceHash = sha256Digest(nonce);

        String canonicalRequest = canonicalizeRequest(credentials.getAWSAccessKeyId(), signingScope, requestTimestamp, nonceHash);

        String stringToSign = String.format("%s\n%s\n%s\n%s",
                                            SignerConstants.AWS4_SIGNING_ALGORITHM,
                                            timestampFormatter.format(requestTimestamp),
                                            signingScope,
                                            sha256Digest(canonicalRequest));

        byte[] signingKey = getSignatureKey(credentials.getAWSSecretKey(),
                                            credentialScopeDate,
                                            signingRegion,
                                            CANONICAL_SERVICE);

        byte[] signature = hmacSHA256(stringToSign, signingKey);

        //TODO: Requires a later version of commons that legacy applications may not use. Consider changing.
        return Hex.encodeHexString(signature, true);
    }

    private static final String AMZ_ALGO_HEADER = "X-Amz-Algorithm=" + SignerConstants.AWS4_SIGNING_ALGORITHM;
    private static final String AMZ_EXPIRES_HEADER = "X-Amz-Expires=900";

    private static String canonicalizeRequest(String accessKey,
                                              String signingScope,
                                              Instant requestTimestamp,
                                              String payloadHash) throws UnsupportedEncodingException {
        List<String> queryStringHeaders =
            Arrays.asList(
                AMZ_ALGO_HEADER,
                String.format("X-Amz-Credential=%s%%2F%s",
                              accessKey,
                              URLEncoder.encode(signingScope, StandardCharsets.UTF_8.name())),
                "X-Amz-Date=" + URLEncoder.encode(timestampFormatter.format(requestTimestamp), StandardCharsets.UTF_8.name()),
                AMZ_EXPIRES_HEADER
            );

        // IMPORTANT: This list must maintain alphabetical order for canonicalization
        Collections.sort(queryStringHeaders);

        String queryString = String.join("&", queryStringHeaders);

        return String.format("PUT\n/authenticate\n%s\nhost:%s\n\nhost\n%s",
                             queryString, CANONICAL_SERVICE, payloadHash);
    }

    static String sha256Digest(byte[] bytes) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Hex.encodeHexString(md.digest(bytes), true);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("This platform does not support the SHA-256 digest algorithm", e);
        }
    }

    static String sha256Digest(String input) {
        return sha256Digest(input.getBytes(StandardCharsets.UTF_8));
    }

    // Taken from https://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html#signature-v4-examples-java
    private static final String HMAC_ALGORITHM = "hmacSHA256";

    static byte[] hmacSHA256(String data, byte[] key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failure computing HMAC-SHA256", e);
        }
    }

    static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSHA256(dateStamp, kSecret);
        byte[] kRegion = hmacSHA256(regionName, kDate);
        byte[] kService = hmacSHA256(serviceName, kRegion);
        byte[] kSigning = hmacSHA256("aws4_request", kService);
        return kSigning;
    }

    /*
     * Java does not natively provide a method for locating one array
     * within another, so we provide that here. While other libraries
     * also provide this, we want to minimize the dependencies that
     * this plugin brings in.
     */
    static int indexOf(byte[] target, byte[] pattern) {
        final int lastCheckIndex = target.length - pattern.length;

        for (int i = 0; i <= lastCheckIndex; i++) {
            if (pattern[0] == target[i]) {
                int inner = 0;
                int outer = i;
                // A tight loop over target, comparing indices
                for (; inner < pattern.length && pattern[inner] == target[outer];
                     inner++, outer++) {}

                // If the inner loop reached the end of the pattern, then we have found the index
                if (inner == pattern.length) {
                    return i;
                }
            }
        }

        // Loop exhaustion means we did not find it
        return -1;
    }
}
