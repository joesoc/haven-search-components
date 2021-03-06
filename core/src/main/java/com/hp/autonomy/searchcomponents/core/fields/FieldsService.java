/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.core.fields;

import com.hp.autonomy.types.requests.idol.actions.tags.TagName;
import com.hp.autonomy.types.requests.idol.actions.tags.params.FieldTypeParam;

import java.util.List;
import java.util.Map;

public interface FieldsService<R extends FieldsRequest, E extends Exception> {

    Map<FieldTypeParam, List<TagName>> getFields(final R request, final FieldTypeParam... fieldTypes) throws E;

}