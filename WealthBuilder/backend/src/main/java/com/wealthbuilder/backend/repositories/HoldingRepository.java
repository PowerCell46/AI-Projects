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
import java.util.List;


public interface HoldingRepository extends JpaRepository<AssetHolding, Long> {

    Page<AssetHolding> findByUserAndAsset(User user, Asset asset, Pageable pageable);

    List<AssetHolding> findByUserAndAsset(User user, Asset asset);

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
