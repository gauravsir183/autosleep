/*
 * Autosleep
 * Copyright (C) 2016 Orange
 * Authors: Benjamin Einaudi   benjamin.einaudi@orange.com
 *          Arnaud Ruffin      arnaud.ruffin@orange.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.autosleep.ui.servicebroker.service;

import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.config.DeployedApplicationConfig;
import org.cloudfoundry.autosleep.dao.model.ApplicationInfo;
import org.cloudfoundry.autosleep.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.dao.repositories.ApplicationRepository;
import org.cloudfoundry.autosleep.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.ui.servicebroker.service.parameters.ParameterReader;
import org.cloudfoundry.autosleep.util.ApplicationLocker;
import org.cloudfoundry.autosleep.worker.WorkerManagerService;
import org.cloudfoundry.community.servicebroker.exception.ServiceBrokerException;
import org.cloudfoundry.community.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.cloudfoundry.community.servicebroker.exception.ServiceInstanceExistsException;
import org.cloudfoundry.community.servicebroker.exception.ServiceInstanceUpdateNotSupportedException;
import org.cloudfoundry.community.servicebroker.model.CreateServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.CreateServiceInstanceResponse;
import org.cloudfoundry.community.servicebroker.model.DeleteServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.DeleteServiceInstanceResponse;
import org.cloudfoundry.community.servicebroker.model.GetLastServiceOperationRequest;
import org.cloudfoundry.community.servicebroker.model.GetLastServiceOperationResponse;
import org.cloudfoundry.community.servicebroker.model.UpdateServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.UpdateServiceInstanceResponse;
import org.cloudfoundry.community.servicebroker.service.ServiceInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AutosleepServiceInstanceService implements ServiceInstanceService {

    @Autowired
    private ApplicationRepository appRepository;

    @Autowired
    private ApplicationLocker applicationLocker;

    @Autowired
    @Qualifier(Config.ServiceInstanceParameters.AUTO_ENROLLMENT)
    private ParameterReader<Config.ServiceInstanceParameters.Enrollment> autoEnrollmentReader;

    @Autowired
    private DeployedApplicationConfig.Deployment deployment;

    @Autowired
    private Environment environment;

    @Autowired
    @Qualifier(Config.ServiceInstanceParameters.EXCLUDE_FROM_AUTO_ENROLLMENT)
    private ParameterReader<Pattern> excludeFromAutoEnrollmentReader;

    @Autowired
    @Qualifier(Config.ServiceInstanceParameters.IDLE_DURATION)
    private ParameterReader<Duration> idleDurationReader;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier(Config.ServiceInstanceParameters.SECRET)
    private ParameterReader<String> secretReader;

    @Autowired
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    @Autowired
    private WorkerManagerService workerManager;

    private void checkSecuredParameter(String parameterName, String secret) {
        if (secret == null) {
            throw new InvalidParameterException(parameterName,
                    "Trying to set or change a protected parameter without providing the right '"
                            + Config.ServiceInstanceParameters.SECRET + "'.");
        }
    }

    private <T> T consumeParameter(Map<String, Object> parameters, boolean withDefault, ParameterReader<T> reader)
            throws InvalidParameterException {
        return reader.readParameter(parameters.remove(reader.getParameterName()), withDefault);
    }

    @Override
    public CreateServiceInstanceResponse createServiceInstance(CreateServiceInstanceRequest request) throws
            ServiceInstanceExistsException, ServiceBrokerException {
        String serviceId = request.getServiceInstanceId();
        log.debug("createServiceInstance - {}", serviceId);
        if (spaceEnrollerConfigRepository.exists(serviceId)) {
            String serviceBrokerId = environment.getProperty(
                    Config.EnvKey.CF_SERVICE_BROKER_ID,
                    Config.ServiceCatalog.DEFAULT_SERVICE_BROKER_ID);
            throw new ServiceInstanceExistsException(serviceId, serviceBrokerId);
        } else {
            Map<String, Object> createParameters = Optional.ofNullable(request.getParameters())
                    .orElse(Collections.emptyMap());
            Config.ServiceInstanceParameters.Enrollment autoEnrollment = consumeParameter(createParameters,
                    true, autoEnrollmentReader);
            String secret = consumeParameter(createParameters, true, secretReader);
            Duration idleDuration = consumeParameter(createParameters, true, idleDurationReader);
            Pattern excludeFromAutoEnrollment = consumeParameter(createParameters, true,
                    excludeFromAutoEnrollmentReader);
            if (!createParameters.isEmpty()) {
                String parameterNames = String.join(", ", createParameters.keySet().iterator().next());
                log.debug("createServiceInstance - extra parameters are not accepted: {}", parameterNames);
                throw new InvalidParameterException(parameterNames, "Unknown parameters for creation");
            } else if (autoEnrollment == Config.ServiceInstanceParameters.Enrollment.forced) {
                checkSecuredParameter(autoEnrollmentReader.getParameterName(), secret);
            }
            SpaceEnrollerConfig spaceEnrollerConfig = SpaceEnrollerConfig.builder()
                    .id(request.getServiceInstanceId())
                    .serviceDefinitionId(request.getServiceDefinitionId())
                    .planId(request.getPlanId())
                    .organizationId(request.getOrganizationGuid())
                    .spaceId(request.getSpaceGuid())
                    .idleDuration(idleDuration)
                    .excludeFromAutoEnrollment(excludeFromAutoEnrollment)
                    .forcedAutoEnrollment(autoEnrollment == Config.ServiceInstanceParameters.Enrollment.forced)
                    .secret(secret != null ? passwordEncoder.encode(secret) : null)
                    .build();

            // save in repository before calling remote because otherwise local service binding controller will
            // fail retrieving the service
            spaceEnrollerConfigRepository.save(spaceEnrollerConfig);
            workerManager.registerSpaceEnroller(spaceEnrollerConfig);

            String firstUri = deployment.getFirstUri();
            if (firstUri == null) {
                firstUri = "local-deployment";
            }

            return new CreateServiceInstanceResponse(firstUri + Config.Path.DASHBOARD_CONTEXT + "/" + serviceId, false);
        }
    }

    @Override
    public DeleteServiceInstanceResponse deleteServiceInstance(
            DeleteServiceInstanceRequest request) throws ServiceBrokerException {
        final String spaceEnrollerConfigId = request.getServiceInstanceId();
        log.debug("deleteServiceInstance - {}", spaceEnrollerConfigId);
        if (spaceEnrollerConfigRepository.findOne(spaceEnrollerConfigId) != null) {
            spaceEnrollerConfigRepository.delete(spaceEnrollerConfigId);
        } else {
            log.warn("Received delete on unknown id %s - This can be the result of CloudFoundry automatically trying "
                    + "to clean services that failed during their creation", spaceEnrollerConfigId);
        }

        //clean stored app linked to the service (already unbound)
        appRepository.findAll().forEach(
                aInfo -> applicationLocker.executeThreadSafe(aInfo.getUuid(), () -> {
                    ApplicationInfo applicationInfoReloaded = appRepository.findOne(aInfo.getUuid());
                    if (applicationInfoReloaded != null
                            && !applicationInfoReloaded.getEnrollmentState().isCandidate(spaceEnrollerConfigId)) {
                        applicationInfoReloaded.getEnrollmentState().updateEnrollment(spaceEnrollerConfigId, false);
                        if (applicationInfoReloaded.getEnrollmentState().getStates().isEmpty()) {
                            appRepository.delete(applicationInfoReloaded);
                            applicationLocker.removeApplication(applicationInfoReloaded.getUuid());
                        }
                    }
                })
        );
        return new DeleteServiceInstanceResponse(false);
    }

    @Override
    public GetLastServiceOperationResponse getLastOperation(GetLastServiceOperationRequest
                                                                    getLastServiceOperationRequest) {
        //async operations not supported
        return null;
    }

    @Override
    public UpdateServiceInstanceResponse updateServiceInstance(
            UpdateServiceInstanceRequest request) throws
            ServiceInstanceUpdateNotSupportedException, ServiceBrokerException, ServiceInstanceDoesNotExistException {
        String spaceEnrollerConfigId = request.getServiceInstanceId();
        log.debug("updateServiceInstance - {}", spaceEnrollerConfigId);
        SpaceEnrollerConfig spaceEnrollerConfig = spaceEnrollerConfigRepository.findOne(spaceEnrollerConfigId);
        if (spaceEnrollerConfig == null) {
            throw new ServiceInstanceDoesNotExistException(spaceEnrollerConfigId);
        } else if (!spaceEnrollerConfig.getPlanId().equals(request.getPlanId())) {
            /* org.cloudfoundry.community.servicebroker.model.ServiceInstance doesn't let us modify planId field
             * (private), and only handle service instance updates by re-creating them from scratch. As we need to
             * handle real updates (secret params), we are not supporting plan updates for now.*/
            throw new ServiceInstanceUpdateNotSupportedException("Service plan updates not supported.");
        } else {
            Map<String, Object> updateParameters = Optional.ofNullable(request.getParameters())
                    .orElse(Collections.emptyMap());
            Config.ServiceInstanceParameters.Enrollment autoEnrollment = consumeParameter(updateParameters,
                    false, autoEnrollmentReader);
            String secret = consumeParameter(updateParameters, false, secretReader);
            if (!updateParameters.isEmpty()) {
                String parameterNames = String.join(", ", updateParameters.keySet().iterator().next());
                log.debug("updateServiceInstance - extra parameters are not accepted: {}", parameterNames);
                throw new InvalidParameterException(parameterNames, "Unknown parameters for update");
            } else if (autoEnrollment != null) {
                // only auto enrollment parameter can be updated
                checkSecuredParameter(autoEnrollmentReader.getParameterName(), secret);
                if (spaceEnrollerConfig.getSecret() != null
                        && !(
                                passwordEncoder.matches(secret, spaceEnrollerConfig.getSecret())
                                        ||
                                        secret.equals(environment.getProperty(Config.EnvKey.SECURITY_PASSWORD))
                        )) {
                    throw new InvalidParameterException(Config.ServiceInstanceParameters.SECRET,
                            "Provided secret does not match the one provided on creation nor the "
                                    + Config.EnvKey.SECURITY_PASSWORD + " value.");
                }
                spaceEnrollerConfig.setForcedAutoEnrollment(
                        autoEnrollment == Config.ServiceInstanceParameters.Enrollment.forced);
                spaceEnrollerConfigRepository.save(spaceEnrollerConfig);
            }
            return new UpdateServiceInstanceResponse(false);
        }

    }

}
