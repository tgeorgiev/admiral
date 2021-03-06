/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes;

import java.net.URI;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;

public class ApplicationRequest extends AdapterRequest {

    public URI hostReference;

    @Override
    public void validate() {
        super.validate();
        if (operationTypeId != null) {
            if (KubernetesOperationType.instanceById(operationTypeId) == null) {
                throw new IllegalArgumentException("Invalid application operationId: " +
                        operationTypeId);
            }
        }
    }

    public KubernetesOperationType getOperationtype() {
        return KubernetesOperationType.instanceById(operationTypeId);
    }

    public URI getCompositeComponentReference() {
        return resourceReference;
    }

    public URI getHostReference() {
        return hostReference;
    }
}
