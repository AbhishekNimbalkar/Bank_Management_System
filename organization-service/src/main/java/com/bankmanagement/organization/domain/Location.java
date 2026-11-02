package com.bankmanagement.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/** Postal address and GPS coordinates shared by banks and branches. */
@Getter
@Setter
@Embeddable
public class Location {

    @Column(length = 40)
    private String state;

    @Column(length = 60)
    private String city;

    @Column(length = 6)
    private String pincode;

    @Column(length = 120)
    private String street;

    @Column(length = 240)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;
}
