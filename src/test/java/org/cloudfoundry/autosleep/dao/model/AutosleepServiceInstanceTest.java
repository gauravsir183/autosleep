package org.cloudfoundry.autosleep.dao.model;

import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.community.servicebroker.model.CreateServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.DeleteServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.UpdateServiceInstanceRequest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Slf4j
public class AutosleepServiceInstanceTest {

    private static final String SERVICE_DEFINITION_ID = UUID.randomUUID().toString();
    private static final String SERVICE_ID = UUID.randomUUID().toString();
    private static final String ORG = UUID.randomUUID().toString();
    private static final String SPACE = UUID.randomUUID().toString();
    private static final String PLAN = UUID.randomUUID().toString();

    private CreateServiceInstanceRequest createRequest;

    private UpdateServiceInstanceRequest updateRequest;

    private DeleteServiceInstanceRequest deleteRequest;


    @Before
    public void init() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(AutosleepServiceInstance.INACTIVITY_PARAMETER, "PT15M");
        parameters.put(AutosleepServiceInstance.EXCLUDE_PARAMETER, ".*");
        createRequest = new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE, parameters).withServiceInstanceId(SERVICE_ID);
        updateRequest = new UpdateServiceInstanceRequest(PLAN)
                .withInstanceId(SERVICE_ID);
        deleteRequest = new DeleteServiceInstanceRequest(SERVICE_ID,
                SERVICE_DEFINITION_ID,
                PLAN);
    }

    @SuppressWarnings({"ObjectEqualsNull", "EqualsBetweenInconvertibleTypes"})
    @Test
    public void testEquals() throws Exception {
        assertFalse(new AutosleepServiceInstance(createRequest).equals(null));
        assertFalse(new AutosleepServiceInstance(createRequest).equals("toto"));
        assertTrue(new AutosleepServiceInstance(createRequest).equals(new AutosleepServiceInstance(createRequest)));
        assertTrue(new AutosleepServiceInstance(updateRequest).equals(new AutosleepServiceInstance(updateRequest)));
        assertTrue(new AutosleepServiceInstance(deleteRequest).equals(new AutosleepServiceInstance(deleteRequest)));
    }

    @Test
    public void testHashCode() throws Exception {
        assertTrue(new AutosleepServiceInstance(createRequest).hashCode()
                == new AutosleepServiceInstance(createRequest).hashCode());
        assertTrue(new AutosleepServiceInstance(updateRequest).hashCode()
                == new AutosleepServiceInstance(updateRequest).hashCode());
        assertTrue(new AutosleepServiceInstance(deleteRequest).hashCode()
                == new AutosleepServiceInstance(deleteRequest).hashCode());
    }

    @Test
    public void testToString() throws Exception {
        assertNotNull(new AutosleepServiceInstance(createRequest).toString());
    }

    @Test
    public void testSetDurationParams() {

        AutosleepServiceInstance serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                null)
                .withServiceInstanceId(SERVICE_ID));
        assertThat(serviceInstance.getInterval(), is(equalTo(Config.defaultInactivityPeriod)));
        try {
            new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                    SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                    Collections.singletonMap(AutosleepServiceInstance.INACTIVITY_PARAMETER, "10H"))
                    .withServiceInstanceId(SERVICE_ID));
            fail("should have failed - " + AutosleepServiceInstance.INACTIVITY_PARAMETER + " was in wrong format");
        } catch (HttpMessageNotReadableException s) {
            log.debug("{} occurred as expected", s.getClass().getSimpleName());
        }

        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.INACTIVITY_PARAMETER, "PT10M"))
                .withServiceInstanceId(SERVICE_ID));
        assertThat(serviceInstance.getInterval(), is(equalTo(Duration.ofMinutes(10))));
        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.INACTIVITY_PARAMETER, "PT10S"))
                .withServiceInstanceId(SERVICE_ID));
        assertThat(serviceInstance.getInterval(), is(equalTo(Duration.ofSeconds(10))));
    }


    @Test
    public void testSetIgnoreNamesFromParams() {
        AutosleepServiceInstance serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                null)
                .withServiceInstanceId(SERVICE_ID));
        assertThat(serviceInstance.getExcludeNames(), is(nullValue()));
        try {
            new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                    SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                    Collections.singletonMap(AutosleepServiceInstance.EXCLUDE_PARAMETER, "\\d{AA}"))
                    .withServiceInstanceId(SERVICE_ID));
            fail("should have failed - " + AutosleepServiceInstance.EXCLUDE_PARAMETER + " was in wrong format");
        } catch (HttpMessageNotReadableException s) {
            log.debug("{} occurred as expected", s.getClass().getSimpleName());
        }

        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.EXCLUDE_PARAMETER, ".*"))
                .withServiceInstanceId(SERVICE_ID));
        assertThat(serviceInstance.getExcludeNames(), is(notNullValue()));
        assertThat(serviceInstance.getExcludeNames().pattern(), is(equalTo(".*")));

        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.EXCLUDE_PARAMETER, ""))
                .withServiceInstanceId(SERVICE_ID));
        assertThat(serviceInstance.getExcludeNames(), is(nullValue()));
    }

    @Test
    public void testSetNoOptOutFromParams() {
        AutosleepServiceInstance serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.NO_OPTOUT_PARAMETER, "false"))
                .withServiceInstanceId(SERVICE_ID));
        assertFalse(serviceInstance.isNoOptOut());

        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.NO_OPTOUT_PARAMETER, ""))
                .withServiceInstanceId(SERVICE_ID));
        assertFalse(serviceInstance.isNoOptOut());

        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.singletonMap(AutosleepServiceInstance.NO_OPTOUT_PARAMETER, "true"))
                .withServiceInstanceId(SERVICE_ID));
        assertTrue(serviceInstance.isNoOptOut());

        serviceInstance = new AutosleepServiceInstance(new CreateServiceInstanceRequest(
                SERVICE_DEFINITION_ID, PLAN, ORG, SPACE,
                Collections.emptyMap())
                .withServiceInstanceId(SERVICE_ID));
        assertFalse(serviceInstance.isNoOptOut());

    }


}