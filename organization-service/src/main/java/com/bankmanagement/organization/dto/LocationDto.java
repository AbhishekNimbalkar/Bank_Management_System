package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.Location;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LocationDto(
        @NotBlank @Size(max = 40) @Pattern(regexp = Patterns.SAFE_TEXT) String state,
        @NotBlank @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String city,
        @NotBlank @Pattern(regexp = Patterns.PINCODE, message = "must be a valid 6-digit pincode") String pincode,
        @Size(max = 120) @Pattern(regexp = Patterns.SAFE_TEXT) String street,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

    public String formattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) {
            sb.append(street.trim()).append(", ");
        }
        return sb.append(city).append(", ").append(state).append(" - ").append(pincode).toString();
    }

    public void applyTo(Location location) {
        location.setState(state.trim());
        location.setCity(city.trim());
        location.setPincode(pincode);
        location.setStreet(street == null ? null : street.trim());
        location.setAddress(formattedAddress());
        location.setLatitude(latitude);
        location.setLongitude(longitude);
    }

    public static LocationDto from(Location l) {
        return new LocationDto(l.getState(), l.getCity(), l.getPincode(), l.getStreet(), l.getLatitude(), l.getLongitude());
    }
}
