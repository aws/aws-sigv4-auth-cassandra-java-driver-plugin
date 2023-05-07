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

import java.io.UnsupportedEncodingException;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;

import org.apache.commons.codec.binary.Hex;

import com.datastax.oss.driver.api.core.auth.AuthProvider;
import com.datastax.oss.driver.api.core.auth.AuthenticationException;
import com.datastax.oss.driver.api.core.auth.Authenticator;
import com.datastax.oss.driver.api.core.config.DriverOption;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.signer.internal.Aws4SignerUtils;
import software.amazon.awssdk.auth.signer.internal.SignerConstant;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import static software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create;

/**
 * This auth provider can be used with the Amazon MCS service to
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
        SIGV4_INITIAL_RESPONSE = initialResponse.asReadOnlyBuffer();
    }

    private static final int AWS_FRACTIONAL_TIMESTAMP_DIGITS = 3; // SigV4 expects three digits of nanoseconds for timestamps
    private static final DateTimeFormatter timestampFormatter =
        (new DateTimeFormatterBuilder()).appendInstant(AWS_FRACTIONAL_TIMESTAMP_DIGITS).toFormatter();


    private static final byte[] NONCE_KEY = "nonce=".getBytes(StandardCharsets.UTF_8);
    private static final int EXPECTED_NONCE_LENGTH = 32;

    // These are static values because we don't need HTTP, but SigV4 assumes some amount of HTTP metadata
    private static final String CANONICAL_SERVICE = "cassandra";

    private final AwsCredentialsProvider credentialsProvider;
    private final String signingRegion;

    /**
     * Create a new Provider, using the
     * DefaultAWSCredentialsProviderChain as its credentials provider.
     * The signing region is taking from the AWS_DEFAULT_REGION
     * environment variable or the "aws.region" system property.
     */
    public SigV4AuthProvider() {
        this(create(), null);
    }

    private final static DriverOption REGION_OPTION = () -> "advanced.auth-provider.aws-region";

    private final static DriverOption ROLE_OPTION = () -> "advanced.auth-provider.aws-role";

    /**
     * This constructor is provided so that the driver can create
     * instances of this class based on configuration. For example:
     *
     * <pre>
     * datastax-java-driver.advanced.auth-provider = {
     *     aws-region = us-east-2
     *     class = software.aws.mcs.auth.SigV4AuthProvider
     * }
     * </pre>
     *
     * The signing region is taken from the
     * datastax-java-driver.advanced.auth-provider.aws-region
     * property, from the "aws.region" system property, or the
     * AWS_DEFAULT_REGION environment variable, in that order of
     * preference.
     *
     * For programmatic construction, use {@link #SigV4AuthProvider()}
     * or {@link #SigV4AuthProvider(AwsCredentialsProvider, String)}.
     *
     * @param driverContext the driver context for instance creation.
     * Unused for this plugin.
     */
    public SigV4AuthProvider(DriverContext driverContext) {
        this(driverContext.getConfig().getDefaultProfile().getString(REGION_OPTION, getDefaultRegion()),
                driverContext.getConfig().getDefaultProfile().getString(ROLE_OPTION, null));
    }

    /**
     * Create a new Provider, using the specified region.
     * @param region the region (e.g. us-east-1) to use for signing. A
     * null value indicates to use the AWS_REGION environment
     * variable, or the "aws.region" system property to configure it.
     */
    public SigV4AuthProvider(final String region) {
        this(create(), region);
    }

    /**
     * Create a new Provider, using the specified region and IAM role to assume.
     * @param region the region (e.g. us-east-1) to use for signing. A
     * null value indicates to use the AWS_REGION environment
     * variable, or the "aws.region" system property to configure it.
     * @param roleArn The IAM Role ARN which the connecting client should assume before connecting with Amazon Keyspaces.
     */
    public SigV4AuthProvider(final String region,final String roleArn) {
        this(Optional.ofNullable(roleArn).map(r->(AwsCredentialsProvider)createSTSRoleCredentialProvider(r,region)).orElse(create()), region);
    }

    /**
     * Create a new Provider, using the specified AWSCredentialsProvider and region.
     * @param credentialsProvider the credentials provider used to obtain signature material
     * @param region the region (e.g. us-east-1) to use for signing. A
     * null value indicates to use the AWS_REGION environment
     * variable, or the "aws.region" system property to configure it.
     */
    public SigV4AuthProvider(@NotNull AwsCredentialsProvider credentialsProvider, final String region) {
        this.credentialsProvider = credentialsProvider;

        if (region == null) {
            DefaultAwsRegionProviderChain chain = new DefaultAwsRegionProviderChain();
            Region defaultRegion = chain.getRegion();
            this.signingRegion = defaultRegion.toString().toLowerCase();
        } else {
            this.signingRegion = region.toLowerCase();
        }

        if (this.signingRegion == null) {
            throw new IllegalStateException(
                "A region must be specified by constructor, AWS_REGION env variable, or aws.region system property"
            );
        }
    }

    @Override
    public Authenticator newAuthenticator(EndPoint endPoint, String authenticator)
        throws AuthenticationException {
        return new SigV4Authenticator();
    }

    @Override
    public void onMissingChallenge(EndPoint endPoint) {
        throw new AuthenticationException(endPoint, "SigV4 requires a challenge from the endpoint. None was sent");
    }

    @Override
    public void close() {
        // We do not open any resources, so this is a NOOP
    }

    /**
     * This authenticator performs SigV4 MCS authentication.
     */
    public class SigV4Authenticator implements Authenticator {
        @Override
        public CompletionStage<ByteBuffer> initialResponse() {
            return CompletableFuture.completedFuture(SIGV4_INITIAL_RESPONSE);
        }

        @Override
        public CompletionStage<ByteBuffer> evaluateChallenge(ByteBuffer challenge) {
            try {
                byte[] nonce = extractNonce(challenge);

                Instant requestTimestamp = Instant.now();
                AwsCredentials credentials = credentialsProvider.resolveCredentials();

                String signature = generateSignature(nonce, requestTimestamp, credentials);

                String response =
                    String.format("signature=%s,access_key=%s,amzdate=%s",
                                  signature,
                                  credentials.accessKeyId(),
                                  timestampFormatter.format(requestTimestamp));

                if (credentials instanceof AwsSessionCredentials) {
                    response = response + ",session_token=" + ((AwsSessionCredentials)credentials).sessionToken();
                }

                return CompletableFuture.completedFuture(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("This platform does not support the UTF-8encoding", e);
            }
        }

        @Override
        public CompletionStage<Void> onAuthenticationSuccess(ByteBuffer token) {
            return CompletableFuture.completedFuture(null);
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

    private String generateSignature(byte[] nonce, Instant requestTimestamp, AwsCredentials credentials) throws UnsupportedEncodingException {
        String credentialScopeDate = Aws4SignerUtils.formatDateStamp(requestTimestamp.toEpochMilli());

        String signingScope = String.format("%s/%s/%s/aws4_request", credentialScopeDate, signingRegion, CANONICAL_SERVICE);

        String nonceHash = sha256Digest(nonce);

        String canonicalRequest = canonicalizeRequest(credentials.accessKeyId(), signingScope, requestTimestamp, nonceHash);

        String stringToSign = String.format("%s\n%s\n%s\n%s",
                                            SignerConstant.AWS4_SIGNING_ALGORITHM,
                                            timestampFormatter.format(requestTimestamp),
                                            signingScope,
                                            sha256Digest(canonicalRequest));

        byte[] signingKey = getSignatureKey(credentials.secretAccessKey(),
                                            credentialScopeDate,
                                            signingRegion,
                                            CANONICAL_SERVICE);

        byte[] signature = hmacSHA256(stringToSign, signingKey);

        return Hex.encodeHexString(signature, true);
    }

    private static final String AMZ_ALGO_HEADER = "X-Amz-Algorithm=" + SignerConstant.AWS4_SIGNING_ALGORITHM;
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


    /**
     * Creates a STS role credential provider
     * @param roleArn The ARN of the role to assume
     * @param stsRegion The region of the STS endpoint
     * @return The STS role credential provider
     */
    private static StsAssumeRoleCredentialsProvider createSTSRoleCredentialProvider(String roleArn,
                                                                     String stsRegion) {
        final String roleName= StringUtils.substringAfterLast(roleArn,"/");
        final String sessionName="keyspaces-session-"+roleName+System.currentTimeMillis();
        StsClient stsClient = StsClient.builder()
                .region(Region.of(stsRegion))
                .build();
        AssumeRoleRequest assumeRoleRequest=AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName(sessionName)
                .build();
        return StsAssumeRoleCredentialsProvider.builder()
                .stsClient(stsClient)
                .refreshRequest(assumeRoleRequest)
                .build();
    }

    /**
     * Gets the default region for SigV4 if region is not provided.
     * @return Default region
     */
    private static String getDefaultRegion() {
        DefaultAwsRegionProviderChain chain = new DefaultAwsRegionProviderChain();
        return Optional.ofNullable(chain.getRegion()).orElse(Region.US_EAST_1).toString();
    }
}
