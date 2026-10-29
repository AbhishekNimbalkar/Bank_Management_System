package com.bankmanagement.incentive.repository;

import com.bankmanagement.incentive.domain.Customer;
import com.bankmanagement.incentive.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndTenantCode(UUID id, String tenantCode);

    long countByTenantCode(String tenantCode);

    boolean existsByTenantCodeAndMobileAndProductIdAndStatusIn(String tenantCode, String mobile, UUID productId,
                                                               Collection<Enums.CustomerStatus> statuses);

    /** Platform view per organization. Rows: [tenantCode, records, converted, convertedVolume]. */
    @Query("""
            select c.tenantCode, count(c),
                   sum(case when c.status = com.bankmanagement.incentive.domain.Enums.CustomerStatus.CONVERTED then 1 else 0 end),
                   coalesce(sum(case when c.status = com.bankmanagement.incentive.domain.Enums.CustomerStatus.CONVERTED
                                     then c.amount else 0 end), 0)
            from Customer c group by c.tenantCode order by c.tenantCode
            """)
    List<Object[]> statsPerTenant();

    /** Conversions of a month across the organization; aggregation happens in the service. */
    @Query("""
            select c from Customer c
            where c.tenantCode = :tenant and c.status = com.bankmanagement.incentive.domain.Enums.CustomerStatus.CONVERTED
              and c.convertedDate between :from and :to
            """)
    List<Customer> conversionsBetween(@Param("tenant") String tenant, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
