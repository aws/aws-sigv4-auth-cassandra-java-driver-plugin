package software.aws.keyspaces.policies;

import com.datastax.oss.driver.api.core.config.DriverOption;

import java.time.Duration;

public enum KeyspacesRetryOption implements DriverOption {


    KEYSPACES_RETRY_MAX_ATTEMPTS("advanced.retry-policy.max-attempts"),
    KEYSPACES_RETRY_MIN_WAIT("advanced.retry-policy.min-wait"),
    KEYSPACES_RETRY_MAX_WAIT("advanced.retry-policy.max-wait");

    public static final Integer DEFAULT_KEYSPACES_RETRY_MAX_ATTEMPTS = 3;
    public static final Duration DEFAULT_KEYSPACES_RETRY_MIN_WAIT = Duration.ofMillis(10);
    public static final Duration DEFAULT_KEYSPACES_RETRY_MAX_WAIT = Duration.ofMillis(50);

    private final String path;

    KeyspacesRetryOption(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }

}
