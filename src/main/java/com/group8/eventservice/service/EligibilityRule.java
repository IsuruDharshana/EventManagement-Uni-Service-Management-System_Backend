package com.group8.eventservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who may register for an event, stored as JSON in events.eligibility_rule. Either
 * <pre>{"all": true}</pre> (anyone, Group 5 is not asked) or any combination of
 * <pre>{"roles": ["STUDENT"], "departmentId": "dep-cs", "facultyId": "fac-sci"}</pre>
 * which Group 5 evaluates: the user must hold one of the roles and, if a department or faculty
 * is given, be affiliated with it. Ids are Group 5 Directory ids, not display names.
 */
public record EligibilityRule(boolean all, List<String> roles, String departmentId, String facultyId) {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z_]{0,49}");
    private static final Pattern DIRECTORY_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    public static EligibilityRule openToAll() {
        return new EligibilityRule(true, List.of(), null, null);
    }

    public boolean hasAffiliation() {
        return departmentId != null || facultyId != null;
    }

    /** @throws IllegalArgumentException with a message safe to show to the caller */
    public static EligibilityRule parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("eligibilityRule must be valid JSON.");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("eligibilityRule must be a JSON object.");
        }

        Boolean all = null;
        List<String> roles = new ArrayList<>();
        String departmentId = null;
        String facultyId = null;

        for (Map.Entry<String, JsonNode> field : root.properties()) {
            JsonNode value = field.getValue();
            switch (field.getKey()) {
                case "all" -> {
                    if (!value.isBoolean()) {
                        throw new IllegalArgumentException("eligibilityRule.all must be true or false.");
                    }
                    all = value.booleanValue();
                }
                case "roles" -> roles = parseRoles(value);
                case "departmentId" -> departmentId = parseDirectoryId("departmentId", value);
                case "facultyId" -> facultyId = parseDirectoryId("facultyId", value);
                default -> throw new IllegalArgumentException("eligibilityRule has an unknown field '" + field.getKey()
                        + "'. Allowed fields: all, roles, departmentId, facultyId.");
            }
        }

        boolean hasCriteria = !roles.isEmpty() || departmentId != null || facultyId != null;
        if (Boolean.TRUE.equals(all)) {
            if (hasCriteria) {
                throw new IllegalArgumentException("eligibilityRule cannot combine \"all\": true with roles, departmentId or facultyId.");
            }
            return openToAll();
        }
        if (!hasCriteria) {
            throw new IllegalArgumentException(
                    "eligibilityRule must be {\"all\": true} or give at least one of roles, departmentId, facultyId.");
        }
        return new EligibilityRule(false, List.copyOf(roles), departmentId, facultyId);
    }

    private static List<String> parseRoles(JsonNode value) {
        if (!value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException("eligibilityRule.roles must be a non-empty list of Group 5 role names.");
        }
        List<String> roles = new ArrayList<>();
        for (JsonNode item : value) {
            String role = item.isString() ? item.stringValue().trim().toUpperCase() : "";
            if (!ROLE.matcher(role).matches()) {
                throw new IllegalArgumentException("eligibilityRule.roles must contain role names such as \"STUDENT\".");
            }
            if (!roles.contains(role)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private static String parseDirectoryId(String name, JsonNode value) {
        String id = value.isString() ? value.stringValue().trim() : "";
        if (!DIRECTORY_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("eligibilityRule." + name
                    + " must be a Group 5 directory id such as \"dep-cs\" (letters, digits, '-' or '_').");
        }
        return id;
    }
}
