package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The eligibility rule format stored on events and sent to Group 5. */
class EligibilityRuleTest {

    @Test
    void allTrueIsOpenToEveryone() {
        EligibilityRule rule = EligibilityRule.parse("{\"all\": true}");

        assertThat(rule.all()).isTrue();
        assertThat(rule.roles()).isEmpty();
        assertThat(rule.hasAffiliation()).isFalse();
    }

    @Test
    void readsRolesAndDirectoryIds() {
        EligibilityRule rule = EligibilityRule.parse(
                "{\"roles\": [\"student\", \" ACADEMIC_STAFF \", \"STUDENT\"], \"departmentId\": \"dep-cs\", \"facultyId\": \"fac-sci\"}");

        assertThat(rule.all()).isFalse();
        assertThat(rule.roles()).containsExactly("STUDENT", "ACADEMIC_STAFF");
        assertThat(rule.departmentId()).isEqualTo("dep-cs");
        assertThat(rule.facultyId()).isEqualTo("fac-sci");
        assertThat(rule.hasAffiliation()).isTrue();
    }

    @Test
    void departmentAloneIsEnough() {
        assertThat(EligibilityRule.parse("{\"departmentId\": \"dept-dept-cs-833c11\"}").roles()).isEmpty();
    }

    @Test
    void rejectsTheOldDepartmentNameFormat() {
        assertThatThrownBy(() -> EligibilityRule.parse("{\"department\": \"Computing\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field 'department'");
    }

    @Test
    void rejectsEmptyOrMissingCriteria() {
        for (String json : new String[] {"{}", "{\"all\": false}", "{\"roles\": []}"}) {
            assertThatThrownBy(() -> EligibilityRule.parse(json)).as(json).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsAllCombinedWithCriteria() {
        assertThatThrownBy(() -> EligibilityRule.parse("{\"all\": true, \"roles\": [\"STUDENT\"]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadValues() {
        for (String json : new String[] {
                "not json", "[\"STUDENT\"]", "{\"all\": \"yes\"}", "{\"roles\": \"STUDENT\"}",
                "{\"roles\": [\"STUDENT?\"]}", "{\"roles\": [42]}", "{\"departmentId\": \"Computer Science\"}",
                "{\"departmentId\": \"\"}", "{\"facultyId\": 7}"}) {
            assertThatThrownBy(() -> EligibilityRule.parse(json)).as(json).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
