package com.hp.autonomy.searchcomponents.core.search;

import lombok.Data;

@Data
public class StateTokenAndResultCount {
    private final TypedStateToken typedStateToken;
    private final long resultCount;
}
