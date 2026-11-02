package com.bankmanagement.attendance.domain;

/** Status and type enumerations of the attendance domain. */
public final class Enums {

    private Enums() {
    }

    public enum AttendanceStatus { PRESENT, LATE, HALF_DAY, ABSENT, ON_LEAVE, LWP, HALF_DAY_LEAVE }

    /** How a record was created: own GPS clock-in, marked by a manager, or an approved correction request. */
    public enum AttendanceSource { GPS, MANUAL, CORRECTION }

    public enum RequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    public enum DayType { FULL_DAY, FIRST_HALF, SECOND_HALF }

    public enum HolidayType { FULL_DAY, HALF_DAY, ONE_AND_HALF_DAY }

    public enum TimesheetStatus { SUBMITTED, APPROVED, REJECTED }
}
