package com.wealthbuilder.backend.repositories;

import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.entities.AssetHolding;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.projections.AssetInvestment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


public interface HoldingRepository extends JpaRepository<AssetHolding, Long> {

    Page<AssetHolding> findByUserAndAsset(User user, Asset asset, Pageable pageable);

    /**
     * The caller's holdings for one asset, narrowed by an optional {@code namePattern} (a
     * pre-lowercased {@code %fragment%} LIKE pattern, matched against {@code lower(name)}) and an
     * optional inclusive purchase-date range. A null criterion is skipped, so passing all nulls is
     * equivalent to the unfiltered listing.
     *
     * <p>The pattern is built caller-side rather than wrapping the bound parameter in
     * {@code lower(concat(...))}: under PostgreSQL a null parameter inside a function defaults to
     * {@code bytea} and fails ({@code function lower(bytea) does not exist}). Placing the parameter
     * directly in the {@code like} comparison keeps it in a text context, mirroring the date bounds.
     */
    @Query("""
            select h from AssetHolding h
            where h.user = :user and h.asset = :asset
              and (:namePattern is null or lower(h.name) like :namePattern)
              and (:from is null or h.date >= :from)
              and (:to is null or h.date <= :to)
            """)
    Page<AssetHolding> search(
            User user,
            Asset asset,
            String namePattern,
            LocalDate from,
            LocalDate to,
            Pageable pageable);

    @Query("select coalesce(sum(h.boughtForAmount), 0) from AssetHolding h where h.user = :user")
    BigDecimal sumInvestedByUser(User user);

    @Query("""
            select h.asset.id as assetId, h.asset.name as assetName, sum(h.boughtForAmount) as totalInvested
            from AssetHolding h
            where h.user = :user
            group by h.asset.id, h.asset.name
            order by sum(h.boughtForAmount) desc
            """)
    List<AssetInvestment> sumInvestedPerAssetByUser(User user);
}
