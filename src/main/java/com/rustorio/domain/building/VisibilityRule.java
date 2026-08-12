package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.VisibilityContext;
import java.util.List;

/**
 * When a building prototype may appear in the build panel — data on the prototype, evaluated against
 * a {@link VisibilityContext} snapshot. See {@link BuildingPrototype#visibleWhen()}.
 */
public sealed interface VisibilityRule permits VisibilityRule.Produced, VisibilityRule.Placed,
        VisibilityRule.Unlocked, VisibilityRule.Effect, VisibilityRule.Any, VisibilityRule.All {

    boolean satisfied(VisibilityContext context);

    String describeClause(VisibilityContext context, String locale);

    void validateReferences(VisibilityReferenceCheck check, ContentId owner);

    record Produced(ContentId itemId) implements VisibilityRule {
        @Override
        public boolean satisfied(VisibilityContext context) {
            return context.hasProduced(itemId);
        }

        @Override
        public String describeClause(VisibilityContext context, String locale) {
            return produceVerb(locale) + context.itemLabel(itemId);
        }

        @Override
        public void validateReferences(VisibilityReferenceCheck check, ContentId owner) {
            check.requireItem(itemId, owner);
        }
    }

    record Placed(ContentId buildingId) implements VisibilityRule {
        @Override
        public boolean satisfied(VisibilityContext context) {
            return context.hasPlaced(buildingId);
        }

        @Override
        public String describeClause(VisibilityContext context, String locale) {
            return placeVerb(locale) + context.buildingLabel(buildingId);
        }

        @Override
        public void validateReferences(VisibilityReferenceCheck check, ContentId owner) {
            check.requireBuilding(buildingId, owner);
        }
    }

    record Unlocked(ContentId techId) implements VisibilityRule {
        @Override
        public boolean satisfied(VisibilityContext context) {
            return context.isUnlocked(techId);
        }

        @Override
        public String describeClause(VisibilityContext context, String locale) {
            return researchVerb(locale) + context.techLabel(techId);
        }

        @Override
        public void validateReferences(VisibilityReferenceCheck check, ContentId owner) {
            check.requireTech(techId, owner);
        }
    }

    record Effect(ContentId effectId) implements VisibilityRule {
        @Override
        public boolean satisfied(VisibilityContext context) {
            return context.hasEffect(effectId);
        }

        @Override
        public String describeClause(VisibilityContext context, String locale) {
            return unlockVerb(locale) + context.effectLabel(effectId);
        }

        @Override
        public void validateReferences(VisibilityReferenceCheck check, ContentId owner) {
            check.requireEffect(effectId, owner);
        }
    }

    record Any(List<VisibilityRule> rules) implements VisibilityRule {
        public Any {
            rules = List.copyOf(rules);
        }

        @Override
        public boolean satisfied(VisibilityContext context) {
            for (VisibilityRule rule : rules) {
                if (rule.satisfied(context)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String describeClause(VisibilityContext context, String locale) {
            return joinOr(rules.stream().map(rule -> rule.describeClause(context, locale)).toList(), locale);
        }

        @Override
        public void validateReferences(VisibilityReferenceCheck check, ContentId owner) {
            rules.forEach(rule -> rule.validateReferences(check, owner));
        }
    }

    record All(List<VisibilityRule> rules) implements VisibilityRule {
        public All {
            rules = List.copyOf(rules);
        }

        @Override
        public boolean satisfied(VisibilityContext context) {
            for (VisibilityRule rule : rules) {
                if (!rule.satisfied(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String describeClause(VisibilityContext context, String locale) {
            return joinAnd(rules.stream().map(rule -> rule.describeClause(context, locale)).toList(), locale);
        }

        @Override
        public void validateReferences(VisibilityReferenceCheck check, ContentId owner) {
            rules.forEach(rule -> rule.validateReferences(check, owner));
        }
    }

    private static String produceVerb(String locale) {
        return "ru".equals(locale) ? "произведите: " : "produce ";
    }

    private static String placeVerb(String locale) {
        return "ru".equals(locale) ? "поставьте: " : "place ";
    }

    private static String researchVerb(String locale) {
        return "ru".equals(locale) ? "исследуйте: " : "research ";
    }

    private static String unlockVerb(String locale) {
        return "ru".equals(locale) ? "откройте: " : "unlock ";
    }

    private static String joinAnd(List<String> clauses, String locale) {
        if (clauses.isEmpty()) {
            return "";
        }
        if (clauses.size() == 1) {
            return clauses.get(0);
        }
        String separator = "ru".equals(locale) ? " и " : " and ";
        return String.join(separator, clauses);
    }

    private static String joinOr(List<String> clauses, String locale) {
        if (clauses.isEmpty()) {
            return "";
        }
        if (clauses.size() == 1) {
            return clauses.get(0);
        }
        String separator = "ru".equals(locale) ? " или " : " or ";
        return String.join(separator, clauses);
    }
}
