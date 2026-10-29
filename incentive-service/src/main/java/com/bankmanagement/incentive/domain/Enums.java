package com.bankmanagement.incentive.domain;

public final class Enums {

    private Enums() {
    }

    public enum ProductType { LOAN, DEPOSIT }

    /** Which product types count towards a plan's target. */
    public enum PlanCategory {
        LOAN, DEPOSIT, ANY;

        public boolean covers(ProductType type) {
            return this == ANY || name().equals(type.name());
        }
    }

    public enum PlanStatus { ACTIVE, INACTIVE }

    public enum CustomerStatus { LEAD, CONVERTED, REJECTED }
}
