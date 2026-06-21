package com.wealthbuilder.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;


/**
 * A single purchase a user recorded against an asset. {@code price} is never stored — it is
 * derived as {@code boughtForAmount / quantity}. {@code date} is the user-supplied purchase
 * day; {@code createdAt} is the independent audit stamp of when the record was created.
 */
@Entity
@Table(name = "asset_holding")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class AssetHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(name = "bought_for_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal boughtForAmount;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false)
    private LocalDate date;

    @Column
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AssetHolding(
            Asset asset,
            User user,
            String name,
            BigDecimal boughtForAmount,
            String unit,
            BigDecimal quantity,
            LocalDate date,
            String note) {
        this.asset = asset;
        this.user = user;
        this.name = name;
        this.boughtForAmount = boughtForAmount;
        this.unit = unit;
        this.quantity = quantity;
        this.date = date;
        this.note = note;
    }
}
