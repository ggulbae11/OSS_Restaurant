package com.restaurant.core.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "riders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiderStatus status;

    private int totalDeliveryCount;

    private LocalDateTime lastAssignedAt;

    public enum RiderStatus { WAITING, DELIVERING, OFFLINE }
}
