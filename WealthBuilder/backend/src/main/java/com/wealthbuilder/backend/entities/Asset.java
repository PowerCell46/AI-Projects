package com.wealthbuilder.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * A moderator-managed asset class (Stocks, Crypto, Precious Metals, …). Users record their
 * individual purchases against an asset via {@code AssetHolding}; this entity is the catalog
 * entry itself. {@code imageBase64} holds a {@code data:image/...;base64,...} URI as text —
 * deliberately {@code text} rather than {@code @Lob}, since {@code @Lob String} maps to a
 * Postgres large object and breaks under auto-commit.
 */
@Entity
@Table(name = "asset")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description;

    // Stored as the base64 `data:` URI text exactly as received and served, so it round-trips to
    // the client with no encode/decode step. bytea would be ~33% smaller and is the conventional
    // choice for binary, but for this small catalog keeping the URI verbatim is simpler and enough.
    @Column(name = "image_base64", nullable = false, columnDefinition = "text")
    private String imageBase64;

    @Column(name = "image_name", nullable = false)
    private String imageName;

    public Asset(String name, String description, String imageBase64, String imageName) {
        this.name = name;
        this.description = description;
        this.imageBase64 = imageBase64;
        this.imageName = imageName;
    }
}
