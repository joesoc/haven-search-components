/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.core.parametricvalues;

import com.google.common.collect.ImmutableMap;
import com.hp.autonomy.searchcomponents.core.fields.FieldsRequest;
import com.hp.autonomy.searchcomponents.core.fields.FieldsService;
import com.hp.autonomy.searchcomponents.core.test.TestUtils;
import com.hp.autonomy.types.idol.RecursiveField;
import com.hp.autonomy.types.requests.idol.actions.tags.QueryTagInfo;
import com.hp.autonomy.types.requests.idol.actions.tags.RangeInfo;
import com.hp.autonomy.types.requests.idol.actions.tags.TagName;
import com.hp.autonomy.types.requests.idol.actions.tags.params.FieldTypeParam;
import com.hp.autonomy.types.requests.idol.actions.tags.params.SortParam;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractParametricValuesServiceIT<R extends ParametricRequest<S>, F extends FieldsRequest, FB extends FieldsRequest.Builder<F>, S extends Serializable, E extends Exception> {
    @Autowired
    private FieldsService<F, E> fieldsService;
    @Autowired
    private ParametricValuesService<R, S, E> parametricValuesService;
    @Autowired
    private FB fieldsRequestBuilder;
    @Autowired
    private ParametricRequest.Builder<R, S> parametricRequestBuilder;

    @Autowired
    protected TestUtils<S> testUtils;

    protected abstract FieldsRequest.Builder<F> fieldsRequestParams(final FB fieldsRequestBuilder);

    @Test
    public void getAllParametricValues() throws E {
        final Set<QueryTagInfo> results = parametricValuesService.getAllParametricValues(createParametricRequest());
        assertThat(results, is(not(empty())));
    }

    @Test
    public void ranges() throws E {
        final List<RangeInfo> ranges = parametricValuesService.getNumericParametricValuesInBuckets(parametricRequestBuilder
                .setFieldNames(Collections.singletonList(ParametricValuesService.AUTN_DATE_FIELD))
                .setQueryRestrictions(testUtils.buildQueryRestrictions())
                .setSort(SortParam.ReverseDate)
                .build(), ImmutableMap.of(ParametricValuesService.AUTN_DATE_FIELD, new BucketingParams(35)));
        assertThat(ranges, not(empty()));
    }

    @Test
    public void getDependentParametricValues() throws E {
        final Collection<RecursiveField> results = parametricValuesService.getDependentParametricValues(createParametricRequest());
        assertThat(results, is(not(empty())));
    }

    private R createParametricRequest() throws E {
        final List<TagName> fields = fieldsService.getFields(fieldsRequestParams(fieldsRequestBuilder).build(), FieldTypeParam.Parametric).get(FieldTypeParam.Parametric);
        final List<String> fieldIds = new ArrayList<>(fields.size());
        for (final TagName field : fields) {
            fieldIds.add(field.getId());
        }

        return parametricRequestBuilder
                .setFieldNames(fieldIds)
                .setQueryRestrictions(testUtils.buildQueryRestrictions())
                .build();
    }
}
