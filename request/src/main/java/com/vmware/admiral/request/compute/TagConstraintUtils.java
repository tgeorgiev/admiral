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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.xenon.services.common.QueryTask;

public class TagConstraintUtils {

    /** Get placement constraints. */
    public static Map<Condition, String> extractPlacementTagConditions(
            Map<String, Constraint> constraints,
            List<String> tenantLinks) {
        // check if constraints are stated
        Constraint placementConstraint = constraints != null
                ? constraints.get(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY)
                : null;
        if (placementConstraint == null || placementConstraint.conditions == null
                || placementConstraint.conditions.isEmpty()) {
            return null;
        }
        Map<Condition, String> tagLinkByCondition = new HashMap<>();
        for (Condition condition : placementConstraint.conditions) {
            String tagLink = getTagLinkForCondition(condition, tenantLinks);
            if (tagLink != null) {
                tagLinkByCondition.put(condition, tagLink);
            }
        }

        return tagLinkByCondition;
    }

    /**
     * Filters service documents based on constraints.
     * Filters out documents that do not satisfy hard constraints.
     * Sorts remaining documents based on how many soft constraints are satisfied.
     * If documents satisfy the same number of soft constraints, sort by secondarySortCriteria,
     * if provided.
     */
    public static <T> Stream<T> filterByConstraints(
            Map<Condition, String> placementConstraints,
            Stream<T> items,
            Function<T, Set<String>> tagLinksSupplier,
            Comparator<T> secondarySortCriteria) {

        if (placementConstraints == null) {
            return items;
        }

        return items
                .filter(item -> checkHardConstraintsSatisfied(tagLinksSupplier.apply(item),
                        placementConstraints))
                .sorted((item1, item2) -> {
                    int softCount1 = getNumberOfSatisfiedSoftConstraints(
                            tagLinksSupplier.apply(item1), placementConstraints);
                    int softCount2 = getNumberOfSatisfiedSoftConstraints(
                            tagLinksSupplier.apply(item2), placementConstraints);
                    return softCount1 == softCount2 && secondarySortCriteria != null ?
                            secondarySortCriteria.compare(item1, item2)
                            : softCount2 - softCount1;
                });
    }

    private static boolean checkHardConstraintsSatisfied(Set<String> tagLinks,
            Map<Condition, String> constraints) {
        return constraints.entrySet().stream()
                .filter(e -> Condition.Enforcement.HARD.equals(e.getKey().enforcement))
                .allMatch(e -> (tagLinks != null && tagLinks.contains(e.getValue())) ==
                        !QueryTask.Query.Occurance.MUST_NOT_OCCUR.equals(e.getKey().occurrence));
    }

    private static int getNumberOfSatisfiedSoftConstraints(Set<String> tagLinks,
            Map<Condition, String> constraints) {
        return (int) constraints.entrySet().stream()
                .filter(e -> Condition.Enforcement.SOFT.equals(e.getKey().enforcement))
                .filter(e -> (tagLinks != null && tagLinks.contains(e.getValue())) ==
                        !QueryTask.Query.Occurance.MUST_NOT_OCCUR.equals(e.getKey().occurrence))
                .count();
    }

    private static String getTagLinkForCondition(Condition condition,
            List<String> tenantLinks) {
        if (!Condition.Type.TAG.equals(condition.type) || condition.expression == null
                || condition.expression.propertyName == null) {
            return null;
        }

        String[] tagParts = condition.expression.propertyName.split(":");

        TagService.TagState tag = new TagService.TagState();
        tag.key = tagParts[0];
        tag.value = tagParts.length > 1 ? tagParts[1] : "";
        tag.tenantLinks = tenantLinks;

        return TagFactoryService.generateSelfLink(tag);
    }
}
