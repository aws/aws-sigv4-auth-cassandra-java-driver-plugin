package software.aws.keyspaces.policies;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.retry.RetryDecision;
import com.datastax.oss.driver.api.core.servererrors.WriteFailureException;
import com.datastax.oss.driver.api.core.servererrors.WriteType;
import com.datastax.oss.driver.api.core.session.ProgrammaticArguments;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.internal.core.context.DefaultDriverContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AmazonKeyspacesRetryPolicyTest {

    @Test
    public void determineRetryDecisionExceed() {

        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETHROW, st.determineRetryDecision(4));

    }

    @Test
    public void determineRetryDecisionMin() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETRY_SAME, st.determineRetryDecision(0));

    }

    @Test
    public void determineRetryDecisionMid() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETRY_SAME, st.determineRetryDecision(1));

    }
    @Test
    public void determinRetryFalse() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);


        assertEquals(RetryDecision.RETHROW, st.onUnavailable(null, ConsistencyLevel.LOCAL_QUORUM, 1, 1, 4));
    }

    @Test
    public void onWriteTimeout() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETRY_SAME, st.onWriteTimeout(null, ConsistencyLevel.LOCAL_QUORUM, WriteType.SIMPLE, 2, 0, 1));

    }

    @Test
    public void onWriteTimeoutExceed() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETHROW, st.onWriteTimeout(null, ConsistencyLevel.LOCAL_QUORUM, WriteType.SIMPLE, 2, 0, 4));

    }

    @Test
    public void onReadTimeout() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETRY_SAME, st.onReadTimeout(null, ConsistencyLevel.LOCAL_QUORUM, 2, 0, false, 1));

    }

    @Test
    public void onReadTimeoutExceed() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETHROW, st.onReadTimeout(null, ConsistencyLevel.LOCAL_QUORUM, 2, 0, false, 4));

    }

    @Test
    public void oneError() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETRY_SAME, st.onErrorResponse(null, new WriteFailureException(null, ConsistencyLevel.LOCAL_QUORUM, 0, 2, WriteType.SIMPLE, 1, null), 2));

    }

    @Test
    public void oneErrorExceed() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);


        assertEquals(RetryDecision.RETHROW, st.onErrorResponse(null, new WriteFailureException(null, ConsistencyLevel.LOCAL_QUORUM, 0, 2, WriteType.SIMPLE, 1, null), 4));

    }

    @Test
    public void onUnavailable() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);

        assertEquals(RetryDecision.RETRY_SAME, st.onUnavailable(null, ConsistencyLevel.LOCAL_QUORUM, 1, 1, 2));

    }

    @Test
    public void onUnavailableExceed() {
        DriverContext context = new DefaultDriverContext(new DefaultProgrammaticDriverConfigLoaderBuilder().build(), ProgrammaticArguments.builder().build());
        AmazonKeyspacesRetryPolicy st = new AmazonKeyspacesRetryPolicy(context, 3);


        assertEquals(RetryDecision.RETHROW, st.onUnavailable(null, ConsistencyLevel.LOCAL_QUORUM, 1, 1, 4));
    }

    @Test
    public void testConfig() {
        assertEquals(5, DriverConfigLoader.fromClasspath("keyspaces-reference-retry.conf").getInitialConfig().getDefaultProfile().getInt(KeyspacesRetryOption.KEYSPACES_RETRY_MAX_ATTEMPTS, KeyspacesRetryOption.DEFAULT_KEYSPACES_RETRY_MAX_ATTEMPTS));
    }
    @Test
    public void testDefaultConfig() {
        assertEquals(3, DriverConfigLoader.fromClasspath("keyspaces-reference-retry-defaults.conf").getInitialConfig().getDefaultProfile().getInt(KeyspacesRetryOption.KEYSPACES_RETRY_MAX_ATTEMPTS, KeyspacesRetryOption.DEFAULT_KEYSPACES_RETRY_MAX_ATTEMPTS));
    }

}

