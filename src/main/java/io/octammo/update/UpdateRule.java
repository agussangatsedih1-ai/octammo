package io.octammo.update;

import java.util.List;

/** One entry of updates.yml: how to recognise an outdated copy of an MMOItem. */
public record UpdateRule(String key,
                         String type,
                         String id,
                         List<String> outdatedContains,
                         List<String> requiredContains,
                         boolean compareTemplate) {

    public boolean appliesTo(String itemType, String itemId) {
        return (type.equals("*") || type.equals(itemType)) && (id.equals("*") || id.equals(itemId));
    }
}
