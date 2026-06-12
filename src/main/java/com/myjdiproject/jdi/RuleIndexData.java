package com.myjdiproject.jdi;

import java.util.List;
import java.util.Set;

public final class RuleIndexData {

    public boolean classComplete = true;
    public List<String> classExact;
    public List<String> classPrefix;
    public List<String> classSubstrings;
    public List<String> methodNames;
    public List<String> fieldNames;
    public List<String> annotationDescriptors;

    public boolean argComplete;
    public List<ArgRule> argRules;

    public static final class ArgRule {
        public final String classKey;
        public final String method;
        public final List<Predicate> predicates;

        public ArgRule(String classKey, String method, List<Predicate> predicates) {
            this.classKey = classKey;
            this.method = method;
            this.predicates = predicates;
        }
    }

    public static final class Predicate {
        public final String position;
        public final boolean regex;
        public final boolean number;
        public final boolean caseSensitive;
        public final boolean nullMatches;
        public final Set<Integer> arities;
        public final List<String> literals;

        public Predicate(String position, boolean regex, boolean number, boolean caseSensitive,
                         boolean nullMatches, Set<Integer> arities, List<String> literals) {
            this.position = position;
            this.regex = regex;
            this.number = number;
            this.caseSensitive = caseSensitive;
            this.nullMatches = nullMatches;
            this.arities = arities;
            this.literals = literals;
        }
    }
}
