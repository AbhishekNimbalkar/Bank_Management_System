package com.bankmanagement.common.security;

import com.bankmanagement.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeTest {

    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();

    private static AuthenticatedUser user(String role, UUID branch) {
        return new AuthenticatedUser(UUID.randomUUID(), null, "u", "User", "C1", "bank", role, branch, null, Set.of());
    }

    @Test
    void branchManagerSeesOnlyOwnBranch() {
        DataScope scope = DataScope.of(user("BM", BRANCH_A));
        assertThat(scope.includes(BRANCH_A, UUID.randomUUID())).isTrue();
        assertThat(scope.includes(BRANCH_B, UUID.randomUUID())).isFalse();
    }

    @Test
    void employeeSeesOnlySelf() {
        AuthenticatedUser emp = user("EMP", BRANCH_A);
        DataScope scope = DataScope.of(emp);
        assertThat(scope.includes(BRANCH_A, emp.userId())).isTrue();
        assertThat(scope.includes(BRANCH_A, UUID.randomUUID())).isFalse();
    }

    @Test
    void cannotDecideOwnRequest() {
        AuthenticatedUser hr = user("HR", BRANCH_A);
        assertThatThrownBy(() -> DataScope.assertCanDecide(hr, hr.userId(), BRANCH_A, "HR"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cannotDecidePeerOrSenior() {
        AuthenticatedUser bm = user("BM", BRANCH_A);
        assertThatThrownBy(() -> DataScope.assertCanDecide(bm, UUID.randomUUID(), BRANCH_A, "BM"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> DataScope.assertCanDecide(bm, UUID.randomUUID(), BRANCH_A, "EMP"))
                .doesNotThrowAnyException();
    }

    @Test
    void branchManagerCannotDecideOtherBranch() {
        AuthenticatedUser bm = user("BM", BRANCH_A);
        assertThatThrownBy(() -> DataScope.assertCanDecide(bm, UUID.randomUUID(), BRANCH_B, "EMP"))
                .isInstanceOf(BusinessException.class);
    }
}
