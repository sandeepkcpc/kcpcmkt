package com.kcpc.mkt.planning.dto;

import com.kcpc.mkt.planning.domain.ContentPriority;

import java.util.List;

public record ContentPlanParametersRequest(String categoryText, ContentPriority contentPriority,
                                            String skuReference, boolean skuNotApplicable,
                                            List<String> talentNames, String folderLink) {
}
