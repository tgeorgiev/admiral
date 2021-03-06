/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute.enhancer;

import static com.vmware.admiral.compute.ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.WRITE_FILES_ELEMENT;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.loadResource;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.objectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.request.compute.enhancer.EnhancerUtils.WriteFiles;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;

public class ComputeStateEnhancer implements Enhancer<ComputeState> {
    private static final String ENABLE_SOFTWARE_MANAGEMENT_FIELD_ID = "Compute.EnableSoftwareManagement";
    private static final String SOFTWARE_PROP_PREFIX = "__software.";

    private ServiceHost host;
    private URI referer;
    private String bootstrapContent;

    public ComputeStateEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeState> enhance(EnhanceContext context, ComputeState cs) {
        String cloudInit = getCustomProperty(cs.customProperties, COMPUTE_CONFIG_CONTENT_PROP_NAME);

        DeferredResult<ComputeState> result = new DeferredResult<>();
        if (enableSoftwareManagement(cs)) {
            try {
                cloudInit = applySoftwareServiceConfig(cloudInit, cs);
            } catch (IOException e) {
                return DeferredResult.failed(e);
            }
        }

        host.log(Level.INFO, "Cloud config file to use [%s]", cloudInit);
        if (cloudInit != null) {
            cs.customProperties.put(COMPUTE_CONFIG_CONTENT_PROP_NAME, cloudInit);
            updateDisks(cs, cloudInit, result);
        } else {
            result.complete(cs);
        }
        return result;
    }

    private void updateDisks(ComputeState cs, String cloudInit,
            DeferredResult<ComputeState> result) {
        if (cs.diskLinks == null || cs.diskLinks.isEmpty()) {
            result.complete(cs);
            return;
        }

        OperationJoin.JoinedCompletionHandler getDisksCompletion = (opsGetDisks,
                exsGetDisks) -> {
            if (exsGetDisks != null && !exsGetDisks.isEmpty()) {
                result.fail(exsGetDisks.values().iterator().next());
                return;
            }

            List<Operation> updateOperations = opsGetDisks.values().stream()
                    .map(op -> op.getBody(DiskService.DiskState.class))
                    .filter(diskState -> diskState.type == DiskService.DiskType.HDD)
                    .filter(diskState -> diskState.bootConfig != null
                            && diskState.bootConfig.files.length > 0)
                    .map(diskState -> {
                        diskState.bootConfig.files[0].contents = cloudInit;
                        return Operation.createPut(host, diskState.documentSelfLink)
                                .setReferer(referer)
                                .setBody(diskState);
                    }).collect(Collectors.toList());

            if (updateOperations.isEmpty()) {
                result.complete(cs);
                return;
            }
            OperationJoin.create(updateOperations).setCompletion((opsUpdDisks, exsUpdDisks) -> {
                if (exsUpdDisks != null && !exsUpdDisks.isEmpty()) {
                    result.fail(exsGetDisks.values().iterator().next());
                    return;
                }
                result.complete(cs);
            }).sendWith(host);
        };

        List<Operation> getDisksOperations = cs.diskLinks.stream()
                .map(link -> Operation.createGet(host, link).setReferer(referer))
                .collect(Collectors.toList());

        OperationJoin.create(getDisksOperations).setCompletion(getDisksCompletion)
                .sendWith(host);

    }

    @SuppressWarnings("unchecked")
    private String applySoftwareServiceConfig(String cloudInit, ComputeState cs)
            throws IOException {
        Map<String, Object> content = new HashMap<>();
        if (cloudInit != null) {
            content = objectMapper().readValue(cloudInit, Map.class);
        }

        String softwareProps = cs.customProperties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(SOFTWARE_PROP_PREFIX))
                .map(e -> cleanKey(e.getKey()) + "=" + e.getValue())
                .collect(Collectors.joining("\n")) + "\n";

        List<Object> list = (List<Object>) content.get(WRITE_FILES_ELEMENT);
        if (list == null) {
            list = new ArrayList<>();
        }

        list.add(new WriteFiles("/opt/vmware/agent/agent_bootstrap.sh", "0755",
                getBootstrapContent()));
        list.add(new WriteFiles("/opt/vmware/agent/appd.properties", "0644", softwareProps));
        content.put(WRITE_FILES_ELEMENT, list);

        // List<Object> runcmds = (List<Object>) content.get(RUNCMD_ELEMENT);
        // if (runcmds == null) {
        // runcmds = new ArrayList<>();
        // }
        // runcmds.add("cd /opt/vmware/agent && sudo ./agent_bootstrap.sh");
        // content.put(RUNCMD_ELEMENT, runcmds);

        StringBuilder sb = new StringBuilder("#cloud-config\n");
        sb.append(objectMapper().writeValueAsString(content));
        sb.append("\n");
        sb.append(getSystemDContent());
        return sb.toString();
    }

    private String getBootstrapContent() throws IOException {
        if (this.bootstrapContent == null) {
            this.bootstrapContent = loadResource("/agent/agent_bootstrap.sh");
        }
        return this.bootstrapContent;
    }

    private String getSystemDContent() throws IOException {
        return loadResource("/agent/coreos_systemd.yaml");
    }

    private String cleanKey(String key) {
        return key.substring(SOFTWARE_PROP_PREFIX.length());
    }

    private boolean enableSoftwareManagement(ComputeState cs) {
        return Boolean.parseBoolean(
                EnhancerUtils.getCustomProperty(cs, ENABLE_SOFTWARE_MANAGEMENT_FIELD_ID));
    }

}
