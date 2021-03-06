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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.env.ComputeProfileService;
import com.vmware.admiral.compute.env.EnvironmentService;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentStateExpanded;
import com.vmware.admiral.compute.env.NetworkProfileService;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.env.StorageProfileService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class NetworkProfileQueryUtilsTest extends RequestBaseTest {
    private URI referer;

    @Override
    @Before
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
        waitForServiceAvailability(host, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        referer = UriUtils.buildUri(host, EnvironmentQueryUtilsTest.class.getSimpleName());
    }

    @Test
    public void testGetComputeNicEnvConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription1 = createNetworkDescription("my net", null);
        String contextId = UUID.randomUUID().toString();
        List<String> subnets = Arrays.asList(
                createSubnet("sub-1", networkDescription1.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription1.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets,
                networkDescription1.tenantLinks, null);
        EnvironmentState env1 = createEnvironment(networkProfile1.documentSelfLink,
                networkProfile1.tenantLinks, null);
        createComputeNetwork(networkDescription1, contextId, Arrays.asList(env1.documentSelfLink));
        // Same name, different context
        ComputeNetworkDescription networkDescription2 = createNetworkDescription("my net", null);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets,
                networkDescription1.tenantLinks, null);
        EnvironmentState env2 = createEnvironment(networkProfile2.documentSelfLink,
                networkProfile2.tenantLinks, null);
        createComputeNetwork(networkDescription2, UUID.randomUUID().toString(), Arrays.asList(
                env2.documentSelfLink));

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription computeDescription = createComputeDescription(contextId,
                Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> environmentLinks = new HashSet<>();
        NetworkProfileQueryUtils.getEnvironmentsForComputeNics(host, referer,
                networkDescription1.tenantLinks,
                contextId, computeDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    environmentLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(environmentLinks.isEmpty());
        assertEquals(1, environmentLinks.size());
        assertEquals(env1.documentSelfLink, environmentLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkEnvironmentsForTenant() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null);
        EnvironmentState env1 = createEnvironment(networkProfile1.documentSelfLink,
                networkProfile1.tenantLinks, null);
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3", null, null).documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2, null, null);
        createEnvironment(networkProfile2.documentSelfLink, networkProfile2.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<String> environmentLinks = new HashSet<>();
        NetworkProfileQueryUtils.getEnvironmentsForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    environmentLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(environmentLinks.isEmpty());
        assertEquals(1, environmentLinks.size());
        assertEquals(env1.documentSelfLink, environmentLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkEnvironmentsForSystem() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        List<String> tenantLinks = Arrays.asList(UUID.randomUUID().toString());
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1, tenantLinks, null);
        createEnvironment(networkProfile1.documentSelfLink, networkProfile1.tenantLinks, null);

        SubnetState subnet3 = createSubnet("sub-3", null, null);
        List<String> subnets2 = Arrays.asList(subnet3.documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2, subnet3.tenantLinks, null);
        EnvironmentState environmentState = createEnvironment(networkProfile2.documentSelfLink,
                networkProfile2.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<String> environmentLinks = new HashSet<>();
        NetworkProfileQueryUtils.getEnvironmentsForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    environmentLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(environmentLinks.isEmpty());
        assertEquals(1, environmentLinks.size());
        assertEquals(environmentState.documentSelfLink, environmentLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkEnvironmentsWithConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net",
                Arrays.asList(TestRequestStateFactory.createCondition("cap1", "pci1", true, false),
                        TestRequestStateFactory.createCondition("cap2", "pci2", true, false),
                        TestRequestStateFactory.createCondition("cap3", "pci3", true, false)));
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks,
                        Sets.newHashSet(createTag("cap1", "pci1",
                                networkDescription.tenantLinks))).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, Sets.newHashSet(createTag("cap2", "pci2",
                        networkDescription.tenantLinks)));
        EnvironmentState env1 = createEnvironment(networkProfile1.documentSelfLink,
                networkProfile1.tenantLinks, Sets.newHashSet(createTag("cap3", "pci3",
                        networkDescription.tenantLinks)));

        TestContext ctx = testCreate(1);
        Set<String> environmentLinks = new HashSet<>();
        NetworkProfileQueryUtils.getEnvironmentsForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    environmentLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(environmentLinks.isEmpty());
        assertEquals(1, environmentLinks.size());
        assertEquals(env1.documentSelfLink, environmentLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkEnvironmentsWithConstraintsNotSatisfied() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net",
                Arrays.asList(TestRequestStateFactory.createCondition("cap", "pci", true, false)));
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks,
                        Sets.newHashSet(createTag("cap", "notPci",
                                networkDescription.tenantLinks))).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null);
        createEnvironment(networkProfile1.documentSelfLink, networkProfile1.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<String> environmentLinks = new HashSet<>();
        Set<Throwable> exceptions = new HashSet<>();
        NetworkProfileQueryUtils.getEnvironmentsForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        exceptions.add(e);
                        ctx.complete();
                        return;
                    }
                    environmentLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(exceptions.isEmpty());
        assertEquals(1, exceptions.size());
        assertTrue(exceptions.iterator().next().getMessage()
                .contains("Could not find any environments"));
    }

    @Test
    public void testGetSubnetStateForComputeNic() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null);
        EnvironmentStateExpanded environmentState = createEnvironment(
                networkProfile.documentSelfLink, networkProfile.tenantLinks, null);
        List<String> subnets2 = Arrays.asList(
                createSubnet("sub-3", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2,
                networkDescription.tenantLinks, null);
        createEnvironment(networkProfile2.documentSelfLink, networkProfile2.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> subnets = new HashSet<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(host, referer, nid.tenantLinks, contextId,
                nid, environmentState,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnets.add(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnets.isEmpty());
        assertEquals(1, subnets.size());
        assertTrue(subnets1.contains(subnets.iterator().next()));
    }

    @Test
    public void testGetSubnetStateForComputeNicNotFound() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        NetworkProfile networkProfile1 = createNetworkProfile(null, networkDescription.tenantLinks,
                null);
        EnvironmentStateExpanded environmentState = createEnvironment(
                networkProfile1.documentSelfLink, networkProfile1.tenantLinks, null);
        List<String> subnets2 = Arrays.asList(
                createSubnet("sub-3", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2,
                networkDescription.tenantLinks, null);
        createEnvironment(networkProfile2.documentSelfLink, networkProfile2.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        List<String> subnets = new ArrayList<>();
        List<Throwable> exceptions = new ArrayList<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(host, referer, nid.tenantLinks, contextId,
                nid, environmentState,
                (all, e) -> {
                    if (e != null) {
                        exceptions.add(e);
                        ctx.complete();
                        return;
                    }
                    subnets.add(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(exceptions.isEmpty());
        assertEquals(1, exceptions.size());
        assertTrue(exceptions.iterator().next().getMessage().contains("doesn't satisfy network"));
        assertTrue(subnets.isEmpty());
    }

    @Test
    public void testGetSubnetStateForComputeNicConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net",
                Arrays.asList(TestRequestStateFactory.createCondition("cap", "pci", true, false)));
        SubnetState subnetState1 = createSubnet("sub-1", networkDescription.tenantLinks,
                Sets.newHashSet(createTag("cap", "notPci", networkDescription.tenantLinks)));
        SubnetState subnetState2 = createSubnet("sub-1", networkDescription.tenantLinks,
                Sets.newHashSet(createTag("cap", "pci", networkDescription.tenantLinks)));
        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnetState1.documentSelfLink,
                        subnetState2.documentSelfLink),
                networkDescription.tenantLinks, null);
        EnvironmentStateExpanded environmentState = createEnvironment(
                networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> subnets = new HashSet<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(host, referer, nid.tenantLinks, contextId,
                nid, environmentState,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnets.add(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnets.isEmpty());
        assertEquals(1, subnets.size());
        assertEquals(subnetState2.documentSelfLink, subnets.iterator().next());
    }

    private ComputeNetworkDescription createNetworkDescription(String name,
            List<Condition> conditions) throws Throwable {
        ComputeNetworkDescription desc = TestRequestStateFactory.createComputeNetworkDescription(
                name);
        desc.documentSelfLink = UUID.randomUUID().toString();
        if (conditions != null) {
            Constraint constraint = new Constraint();
            constraint.conditions = conditions;
            desc.constraints = new HashMap<>();
            desc.constraints.put(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY, constraint);
        }
        desc = doPost(desc, ComputeNetworkDescriptionService.FACTORY_LINK);
        addForDeletion(desc);
        return desc;
    }

    private NetworkInterfaceDescription createComputeNetworkInterfaceDescription(String netName)
            throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.name = netName;
        nid.documentSelfLink = UUID.randomUUID().toString();
        return doPost(nid, NetworkInterfaceDescriptionService.FACTORY_LINK);
    }

    private ComputeDescription createComputeDescription(String contextId,
            List<String> networkInterfaceDescriptions) throws Throwable {
        ComputeDescription compute = TestRequestStateFactory
                .createComputeDescriptionForVmGuestChildren();
        compute.documentSelfLink = UUID.randomUUID().toString();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        compute.networkInterfaceDescLinks = networkInterfaceDescriptions;
        return doPost(compute, ComputeDescriptionService.FACTORY_LINK);
    }

    private EnvironmentStateExpanded createEnvironment(String networkProfileLink,
            List<String> tenantLinks, Set<String> tagLinks) throws Throwable {
        StorageProfileService.StorageProfile storageProfile = new StorageProfileService.StorageProfile();
        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        ComputeProfileService.ComputeProfile computeProfile = new ComputeProfileService.ComputeProfile();
        computeProfile = doPost(computeProfile, ComputeProfileService.FACTORY_LINK);

        EnvironmentState environmentState = TestRequestStateFactory.createEnvironment(
                "env", networkProfileLink, storageProfile.documentSelfLink,
                computeProfile.documentSelfLink);
        environmentState.tenantLinks = tenantLinks;
        environmentState.documentSelfLink = UUID.randomUUID().toString();
        environmentState.tagLinks = tagLinks;
        environmentState = doPost(environmentState, EnvironmentService.FACTORY_LINK);
        return getDocument(EnvironmentStateExpanded.class,
                EnvironmentStateExpanded.buildUri(UriUtils.buildUri(host,
                        environmentState.documentSelfLink)));
    }

    private NetworkProfile createNetworkProfile(List<String> subnetLinks, List<String> tenantLinks,
            Set<String> tagLinks)
            throws Throwable {
        NetworkProfile networkProfile = TestRequestStateFactory.createNetworkProfile("net-prof");
        networkProfile.documentSelfLink = UUID.randomUUID().toString();
        networkProfile.subnetLinks = subnetLinks;
        networkProfile.tenantLinks = tenantLinks;
        networkProfile.tagLinks = tagLinks;
        return doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(ComputeNetworkDescription computeNetworkDescription,
            String contextId, List<String> environmentLinks)
            throws Throwable {
        ComputeNetwork net = TestRequestStateFactory.createComputeNetworkState(
                "my-net", computeNetworkDescription.documentSelfLink);
        net.documentSelfLink = UUID.randomUUID().toString();
        net.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        net.environmentLinks = environmentLinks;
        return doPost(net, ComputeNetworkService.FACTORY_LINK);
    }

    private SubnetState createSubnet(String name, List<String> tenantLinks, Set<String> tagLinks)
            throws Throwable {
        SubnetState subnet = TestRequestStateFactory.createSubnetState(
                name, tenantLinks);
        subnet.documentSelfLink = UUID.randomUUID().toString();
        subnet.tagLinks = tagLinks;
        subnet.networkLink = UUID.randomUUID().toString();
        return doPost(subnet, SubnetService.FACTORY_LINK);
    }

    private String createTag(String key, String value, List<String> tenantLinks)
            throws Throwable {
        TagState tag = new TagState();
        tag.key = key;
        tag.value = value;
        tag.tenantLinks = tenantLinks;
        return doPost(tag, TagService.FACTORY_LINK).documentSelfLink;
    }
}
