package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.Enums.HolidayType;
import com.bankmanagement.attendance.domain.Holiday;
import com.bankmanagement.attendance.dto.LeaveDto;
import com.bankmanagement.attendance.repository.HolidayRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidays;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<LeaveDto.HolidayResponse> list(int year) {
        return holidays.findByTenantCodeAndHolidayDateBetweenOrderByHolidayDateAsc(SecurityUtils.currentTenant(),
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(LeaveDto.HolidayResponse::from).toList();
    }

    /** Full-day closures in a range; used to exclude them from leave day counts. */
    @Transactional(readOnly = true)
    public Set<LocalDate> fullDayClosures(String tenant, LocalDate from, LocalDate to) {
        return holidays.findByTenantCodeAndHolidayDateBetweenOrderByHolidayDateAsc(tenant, from, to).stream()
                .filter(h -> h.getType() != HolidayType.HALF_DAY)
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());
    }

    @Transactional
    public LeaveDto.HolidayResponse create(LeaveDto.HolidayRequest req) {
        String tenant = SecurityUtils.currentTenant();
        String name = req.name().trim();
        if (holidays.existsByTenantCodeAndHolidayDateAndNameIgnoreCase(tenant, req.date(), name)) {
            throw BusinessException.conflict("This holiday is already in the calendar.", "date");
        }
        Holiday h = new Holiday();
        h.setTenantCode(tenant);
        h.setHolidayDate(req.date());
        h.setName(name);
        h.setType(req.type());
        holidays.save(h);
        audit.record("holidays", "Added holiday", name + " (" + req.date() + ")");
        return LeaveDto.HolidayResponse.from(h);
    }

    @Transactional
    public LeaveDto.HolidayResponse update(UUID id, LeaveDto.HolidayRequest req) {
        String tenant = SecurityUtils.currentTenant();
        Holiday h = holidays.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Holiday"));
        String name = req.name().trim();
        boolean changedKey = !h.getHolidayDate().equals(req.date()) || !h.getName().equalsIgnoreCase(name);
        if (changedKey && holidays.existsByTenantCodeAndHolidayDateAndNameIgnoreCase(tenant, req.date(), name)) {
            throw BusinessException.conflict("This holiday is already in the calendar.", "date");
        }
        String before = h.getName() + " (" + h.getHolidayDate() + ")";
        h.setHolidayDate(req.date());
        h.setName(name);
        h.setType(req.type());
        audit.record("holidays", "Edited holiday", before + " -> " + name + " (" + req.date() + ", " + req.type() + ")");
        return LeaveDto.HolidayResponse.from(h);
    }

    @Transactional
    public void delete(UUID id) {
        Holiday h = holidays.findByIdAndTenantCode(id, SecurityUtils.currentTenant())
                .orElseThrow(() -> BusinessException.notFound("Holiday"));
        h.markDeleted();
        audit.record("holidays", "Removed holiday", h.getName() + " (" + h.getHolidayDate() + ")");
    }
}
