package software.aws.keyspaces.policies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KeyspacesRetryOptionTest {

    @Test
    public void testPathMethod() {
        assertEquals("advanced.retry-policy.max-attempts", KeyspacesRetryOption.KEYSPACES_RETRY_MAX_ATTEMPTS.getPath());
    }

    @Test
    public void testDefaults() {
        assertEquals(3, KeyspacesRetryOption.DEFAULT_KEYSPACES_RETRY_MAX_ATTEMPTS);
    }

    @Test
    public void testMinWaitPathMethod() {
        assertEquals("advanced.retry-policy.min-wait", KeyspacesRetryOption.KEYSPACES_RETRY_MIN_WAIT.getPath());
    }

    @Test
    public void testMinWaitDefaults() {
        assertEquals(10, KeyspacesRetryOption.DEFAULT_KEYSPACES_RETRY_MIN_WAIT.toMillis());
    }

    @Test
    public void testMaxWaitPathMethod() {
        assertEquals("advanced.retry-policy.max-wait", KeyspacesRetryOption.KEYSPACES_RETRY_MAX_WAIT.getPath());
    }

    @Test
    public void testMaxWaitDefaults() {
        assertEquals(50, KeyspacesRetryOption.DEFAULT_KEYSPACES_RETRY_MAX_WAIT.toMillis());
    }
}
