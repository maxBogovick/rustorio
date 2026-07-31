package com.rustorio.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code mod.json} dependency's accepted versions: {@code "*"} (anything), or one or more
 * comparison constraints joined by {@code ,} (AND — every constraint must hold), e.g.
 * {@code ">=1.0.0,<2.0.0"}. Recognized operators, checked longest-first so {@code ">="} isn't
 * misread as {@code ">"} followed by a stray {@code "="}: {@code >=}, {@code <=}, {@code !=},
 * {@code =}, {@code >}, {@code <}. A bare version with no operator (e.g. {@code "1.0.0"}) means
 * exact match, same as prefixing it with {@code =}.
 */
public final class VersionRange {

    private static final String[] OPERATORS = {">=", "<=", "!=", "=", ">", "<"};

    private final String raw;
    private final List<Constraint> constraints;

    private VersionRange(String raw, List<Constraint> constraints) {
        this.raw = raw;
        this.constraints = constraints;
    }

    public static VersionRange parse(String text) {
        String trimmed = text.strip();
        if (trimmed.equals("*")) {
            return new VersionRange(trimmed, List.of());
        }
        List<Constraint> parsed = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            parsed.add(Constraint.parse(part.strip(), trimmed));
        }
        return new VersionRange(trimmed, List.copyOf(parsed));
    }

    /** Whether every constraint in this range accepts {@code version} — vacuously true for {@code "*"}. */
    public boolean matches(SemVer version) {
        return constraints.stream().allMatch(c -> c.matches(version));
    }

    @Override
    public String toString() {
        return raw;
    }

    private record Constraint(Operator operator, SemVer version) {
        static Constraint parse(String part, String wholeRange) {
            for (String op : OPERATORS) {
                if (part.startsWith(op)) {
                    String versionText = part.substring(op.length()).strip();
                    try {
                        return new Constraint(Operator.of(op), SemVer.parse(versionText));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                "invalid version range \"" + wholeRange + "\": " + e.getMessage());
                    }
                }
            }
            // No operator prefix at all — a bare version means exact match.
            try {
                return new Constraint(Operator.EQ, SemVer.parse(part));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid version range \"" + wholeRange + "\": " + e.getMessage());
            }
        }

        boolean matches(SemVer candidate) {
            int cmp = candidate.compareTo(version);
            return switch (operator) {
                case GTE -> cmp >= 0;
                case LTE -> cmp <= 0;
                case GT -> cmp > 0;
                case LT -> cmp < 0;
                case EQ -> cmp == 0;
                case NEQ -> cmp != 0;
            };
        }
    }

    private enum Operator {
        GTE, LTE, GT, LT, EQ, NEQ;

        static Operator of(String symbol) {
            return switch (symbol) {
                case ">=" -> GTE;
                case "<=" -> LTE;
                case ">" -> GT;
                case "<" -> LT;
                case "=" -> EQ;
                case "!=" -> NEQ;
                default -> throw new IllegalStateException("unreachable: unknown operator symbol " + symbol);
            };
        }
    }
}
