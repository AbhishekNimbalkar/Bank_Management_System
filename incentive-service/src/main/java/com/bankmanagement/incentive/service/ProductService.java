package com.bankmanagement.incentive.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.incentive.domain.Enums.ProductType;
import com.bankmanagement.incentive.domain.Product;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final AuditPublisher audit;

    /** New organizations start with the common retail loan and deposit catalogue. */
    @Transactional
    public List<IncentiveDto.ProductResponse> list() {
        String tenant = SecurityUtils.currentTenant();
        if (!products.existsByTenantCode(tenant)) {
            products.saveAll(List.of(
                    product(tenant, ProductType.LOAN, "Home Loan", "8.50", "Up to 30 years"),
                    product(tenant, ProductType.LOAN, "Personal Loan", "11.50", "Up to 5 years"),
                    product(tenant, ProductType.LOAN, "Gold Loan", "9.00", "Up to 3 years"),
                    product(tenant, ProductType.LOAN, "Vehicle Loan", "9.50", "Up to 7 years"),
                    product(tenant, ProductType.LOAN, "MSME Loan", "10.50", "Up to 10 years"),
                    product(tenant, ProductType.DEPOSIT, "Savings Account", "3.50", "No lock-in"),
                    product(tenant, ProductType.DEPOSIT, "Fixed Deposit", "7.20", "1 to 5 years"),
                    product(tenant, ProductType.DEPOSIT, "Recurring Deposit", "6.80", "6 months to 10 years")));
        }
        return products.findByTenantCodeOrderByTypeAscNameAsc(tenant).stream()
                .map(IncentiveDto.ProductResponse::from).toList();
    }

    @Transactional
    public IncentiveDto.ProductResponse create(IncentiveDto.ProductRequest req) {
        String tenant = SecurityUtils.currentTenant();
        String name = req.name().trim();
        if (products.existsByTenantCodeAndNameIgnoreCase(tenant, name)) {
            throw BusinessException.conflict("A product named '" + name + "' already exists.", "name");
        }
        Product p = new Product();
        p.setTenantCode(tenant);
        apply(p, req, name);
        products.save(p);
        audit.record("products", "Created product", name + " (" + p.getType() + ", " + p.getRatePercent() + "%)");
        return IncentiveDto.ProductResponse.from(p);
    }

    @Transactional
    public IncentiveDto.ProductResponse update(UUID id, IncentiveDto.ProductRequest req) {
        String tenant = SecurityUtils.currentTenant();
        Product p = find(tenant, id);
        String name = req.name().trim();
        if (products.existsByTenantCodeAndNameIgnoreCaseAndIdNot(tenant, name, id)) {
            throw BusinessException.conflict("A product named '" + name + "' already exists.", "name");
        }
        if (p.getType() != req.type()) {
            throw BusinessException.rule("A product's type cannot change; create a new product instead.");
        }
        apply(p, req, name);
        audit.record("products", "Updated product", name + " (" + p.getRatePercent() + "%, "
                + (p.isActive() ? "active" : "inactive") + ")");
        return IncentiveDto.ProductResponse.from(p);
    }

    Product find(String tenant, UUID id) {
        return products.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Product"));
    }

    private static void apply(Product p, IncentiveDto.ProductRequest req, String name) {
        p.setType(req.type());
        p.setName(name);
        p.setRatePercent(req.ratePercent());
        p.setTenure(req.tenure());
        p.setActive(req.active());
    }

    private static Product product(String tenant, ProductType type, String name, String rate, String tenure) {
        Product p = new Product();
        p.setTenantCode(tenant);
        p.setType(type);
        p.setName(name);
        p.setRatePercent(new BigDecimal(rate));
        p.setTenure(tenure);
        p.setActive(true);
        return p;
    }
}
