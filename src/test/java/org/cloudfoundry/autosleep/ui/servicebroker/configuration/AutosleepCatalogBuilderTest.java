package org.cloudfoundry.autosleep.ui.servicebroker.configuration;

import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.community.servicebroker.model.Catalog;
import org.cloudfoundry.community.servicebroker.model.Plan;
import org.cloudfoundry.community.servicebroker.model.ServiceDefinition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration(classes = AutosleepCatalogBuilder.class)
public class AutosleepCatalogBuilderTest {
    private static final String SERVICE_BROKER_ID = UUID.randomUUID().toString();

    @Mock
    private Environment environment;


    @InjectMocks
    private AutosleepCatalogBuilder catalogBuilder;


    @Test
    public void testBuildCatalog() {
        when(environment.getProperty(eq(Config.EnvKey.CF_SERVICE_BROKER_ID), anyString()))
                .thenReturn(SERVICE_BROKER_ID);
        Catalog catalog = catalogBuilder.buildCatalog();
        assertThat(catalog.getServiceDefinitions().size(), is(equalTo(1)));
        ServiceDefinition serviceDefinition = catalog.getServiceDefinitions().get(0);
        assertThat(serviceDefinition.getId(), is(equalTo(SERVICE_BROKER_ID)));
        assertThat(serviceDefinition.getPlans().size(), is(equalTo(1)));
        assertTrue(serviceDefinition.isBindable());
        assertFalse(serviceDefinition.isPlanUpdateable());
        Plan plan = serviceDefinition.getPlans().get(0);
        UUID.fromString(plan.getId());
        assertTrue(plan.isFree());
        assertThat(serviceDefinition.getDashboardClient(), is(nullValue()));
    }


}