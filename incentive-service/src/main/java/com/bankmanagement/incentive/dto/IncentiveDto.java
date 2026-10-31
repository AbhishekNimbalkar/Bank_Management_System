package com.bankmanagement.incentive.dto;

import com.bankmanagement.incentive.domain.Customer;
import com.bankmanagement.incentive.domain.Enums;
import com.bankmanagement.incentive.domain.IncentivePlan;
import com.bankmanagement.incentive.domain.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class IncentiveDto {

    public static final String SAFE_TEXT = "^[^<>]*$";
    public static final String MONTH = "^20[0-9]{2}-(0[1-9]|1[0-2])$";

    private IncentiveDto() {
    }

    // ---- Products -----------------------------------------------------------

    public record ProductRequest(
            @NotNull Enums.ProductType type,
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = SAFE_TEXT) String name,
            @NotNull @DecimalMin("0.0") @DecimalMax("50.0") @Digits(integer = 3, fraction = 2) BigDecimal ratePercent,
            @Size(max = 60) @Pattern(regexp = SAFE_TEXT) String tenure,
            boolean active) {
    }

    public record ProductResponse(UUID productId, Enums.ProductType type, String name, BigDecimal ratePercent, String tenure,
                                  boolean active) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getType(), p.getName(), p.getRatePercent(), p.getTenure(), p.isActive());
        }
    }

    // ---- Plans ----------------------------------------------------------------

    public record PlanRequest(
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = SAFE_TEXT) String name,
            @NotNull Enums.PlanCategory category,
            @NotBlank @Pattern(regexp = MONTH, message = "must be YYYY-MM") String month,
            @NotNull @DecimalMin("1000") @DecimalMax("10000000000") BigDecimal target,
            @NotNull @Min(0) @Max(100) Integer thresholdPercent,
            @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 3) BigDecimal baseRatePercent,
            @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 3) BigDecimal bonusRatePercent,
            Enums.PlanStatus status) {
    }

    public record PlanResponse(UUID incentivePlanId, String name, Enums.PlanCategory category, String month, BigDecimal target,
                               int thresholdPercent, BigDecimal baseRatePercent, BigDecimal bonusRatePercent,
                               Enums.PlanStatus status) {

        public static PlanResponse from(IncentivePlan p) {
            return new PlanResponse(p.getId(), p.getName(), p.getCategory(), p.getMonth(), p.getTarget(),
                    p.getThresholdPercent(), p.getBaseRatePercent(), p.getBonusRatePercent(), p.getStatus());
        }
    }

    // ---- Customers ------------------------------------------------------------

    /** {@code ownerEmployeeId} lets a manager record business on behalf of a junior in scope. */
    public record CustomerRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = SAFE_TEXT) String name,
            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a valid 10-digit mobile number") String mobile,
            @NotNull UUID productId,
            @NotNull @DecimalMin("1") @DecimalMax("500000000") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @Size(max = 300) @Pattern(regexp = SAFE_TEXT) String notes,
            UUID ownerEmployeeId) {
    }

    public record CustomerUpdate(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = SAFE_TEXT) String name,
            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a valid 10-digit mobile number") String mobile,
            @NotNull @DecimalMin("1") @DecimalMax("500000000") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @Size(max = 300) @Pattern(regexp = SAFE_TEXT) String notes) {
    }

    public record Decision(@NotNull Boolean approved, @Size(max = 200) @Pattern(regexp = SAFE_TEXT) String note) {
    }

    public record CustomerResponse(UUID customerId, String name, String mobile, UUID productId, String productName,
                                   Enums.ProductType productType, BigDecimal amount, Enums.CustomerStatus status,
                                   UUID ownerUserId, String ownerName, String ownerCode, UUID ownerBranchId,
                                   LocalDate leadDate, LocalDate convertedDate, String notes, UUID makerId,
                                   UUID checkerId, Instant decidedAt, String decisionNote) {

        public static CustomerResponse from(Customer c, boolean revealMobile) {
            String mobile = revealMobile ? c.getMobile() : "XXXXXX" + c.getMobile().substring(6);
            return new CustomerResponse(c.getId(), c.getName(), mobile, c.getProductId(), c.getProductName(),
                    c.getProductType(), c.getAmount(), c.getStatus(), c.getOwner().getUserId(), c.getOwner().getName(),
                    c.getOwner().getCode(), c.getOwner().getBranchId(), c.getLeadDate(), c.getConvertedDate(),
                    c.getNotes(), c.getMakerId(), c.getCheckerId(), c.getDecidedAt(), c.getDecisionNote());
        }
    }

    // ---- Leaderboard ------------------------------------------------------------

    /** {@code incentive} is null for rows outside the viewer's data scope (others' pay stays private). */
    public record Ranking(int rank, UUID userId, String name, String code, String designation, UUID branchId,
                          long deals, BigDecimal achieved, BigDecimal target, int achievementPercent,
                          boolean qualified, BigDecimal incentive) {
    }

    public record Leaderboard(String month, PlanResponse plan, java.util.List<Ranking> rankings) {
    }
}
