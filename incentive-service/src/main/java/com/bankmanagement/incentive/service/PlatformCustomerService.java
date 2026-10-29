package com.bankmanagement.incentive.service;

import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.incentive.domain.Customer;
import com.bankmanagement.incentive.domain.Enums.CustomerStatus;
import com.bankmanagement.incentive.domain.Enums.ProductType;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.repository.CustomerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Platform-owner oversight of customer business across all organizations. Customer mobile numbers
 * stay masked here: the platform owner supervises volumes, not individual customers.
 */
@Service
@RequiredArgsConstructor
public class PlatformCustomerService {

    private static final int MAX_PAGE_SIZE = 200;

    private final CustomerRepository customers;

    public record TenantStats(String tenantCode, long records, long converted, BigDecimal convertedVolume) {
    }

    public record Overview(long records, long converted, BigDecimal convertedVolume, int conversionRatePercent,
                           List<TenantStats> perOrganization) {
    }

    /** Customer row as seen by the platform: tenant code added, mobile always masked. */
    public record PlatformCustomer(String tenantCode, IncentiveDto.CustomerResponse customer) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        requirePlatformOwner();
        List<TenantStats> perTenant = customers.statsPerTenant().stream()
                .map(r -> new TenantStats((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                        (BigDecimal) r[3]))
                .toList();
        long records = perTenant.stream().mapToLong(TenantStats::records).sum();
        long converted = perTenant.stream().mapToLong(TenantStats::converted).sum();
        BigDecimal volume = perTenant.stream().map(TenantStats::convertedVolume).reduce(BigDecimal.ZERO, BigDecimal::add);
        int rate = records == 0 ? 0 : (int) (converted * 100 / records);
        return new Overview(records, converted, volume, rate, perTenant);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlatformCustomer> search(String tenantCode, CustomerStatus status, ProductType type,
                                                 String search, int page, int size) {
        requirePlatformOwner();
        Specification<Customer> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tenantCode != null && !tenantCode.isBlank()) {
                p.add(cb.equal(root.get("tenantCode"), tenantCode));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                p.add(cb.equal(root.get("productType"), type));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("productName")), like),
                        cb.like(cb.lower(root.get("owner").get("name")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var result = customers.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, c -> new PlatformCustomer(c.getTenantCode(), IncentiveDto.CustomerResponse.from(c, false)));
    }

    private static void requirePlatformOwner() {
        if (!SecurityUtils.currentUser().isPlatformOwner()) {
            throw BusinessException.forbidden("Only the platform owner can access platform-wide data.");
        }
    }
}
