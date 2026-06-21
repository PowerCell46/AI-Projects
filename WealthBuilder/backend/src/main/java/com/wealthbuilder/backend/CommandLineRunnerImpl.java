package com.wealthbuilder.backend;

import com.wealthbuilder.backend.config.AppProperties;
import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.entities.AssetHolding;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.AssetRepository;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


/**
 * Development-only seeder that populates the moderator account with a realistic spread of
 * "Stocks" holdings, so the UI can be exercised against real-size data instead of an empty
 * portfolio.
 *
 * <p>Idempotent: it does nothing unless the moderator exists, creates the "Stocks" asset on an
 * empty DB if it's missing, and never adds a second batch once the moderator has any "Stocks"
 * holding. Runs after {@link com.wealthbuilder.backend.config.DataSeeder} (which creates the
 * moderator).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class CommandLineRunnerImpl implements CommandLineRunner {

    private static final String STOCKS_ASSET_NAME = "Stocks";

    private static final String STOCKS_ASSET_DESCRIPTION =
            "Publicly traded company shares.";

    private static final String STOCKS_IMAGE_NAME = "stocks-placeholder.png";

    // A 1x1 transparent PNG placeholder, so the non-null image columns are satisfied on an empty
    // DB. The moderator can replace it with a real image through the UI afterwards.
    private static final String STOCKS_IMAGE_BASE64 =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC";

    private final UserRepository userRepository;

    private final AssetRepository assetRepository;

    private final HoldingRepository holdingRepository;

    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(String... args) {
        final User moderator = userRepository
                .findByUsername(appProperties.getModerator().getUsername())
                .orElse(null);
        if (moderator == null) {
            log.warn("No moderator account found; skipping Stocks holdings seeding.");
            return;
        }

        final Asset stocks = findOrCreateStocksAsset();

        if (alreadySeeded(moderator, stocks)) {
            return;
        }

        seedHoldings(moderator, stocks);
    }

    /** Returns the "Stocks" asset, creating it with a placeholder image if it doesn't exist yet. */
    private Asset findOrCreateStocksAsset() {
        return assetRepository
                .findByNameIgnoreCase(STOCKS_ASSET_NAME)
                .orElseGet(this::createStocksAsset);
    }

    private Asset createStocksAsset() {
        final Asset stocks = assetRepository.save(new Asset(
                STOCKS_ASSET_NAME,
                STOCKS_ASSET_DESCRIPTION,
                STOCKS_IMAGE_BASE64,
                STOCKS_IMAGE_NAME));
        log.info("Created '{}' asset for seeding.", STOCKS_ASSET_NAME);
        return stocks;
    }

    /** True once the moderator owns at least one holding under the given asset. */
    private boolean alreadySeeded(User moderator, Asset stocks) {
        return holdingRepository
                .findByUserAndAsset(moderator, stocks, PageRequest.of(0, 1))
                .hasContent();
    }

    private void seedHoldings(User moderator, Asset stocks) {
        final List<AssetHolding> holdings = mockStockHoldings()
                .stream()
                .map(seed -> seed.toHolding(stocks, moderator))
                .toList();

        holdingRepository.saveAll(holdings);
        log.info("Seeded {} '{}' holdings for moderator '{}'.",
                holdings.size(), STOCKS_ASSET_NAME, moderator.getUsername());
    }

    /**
     * A hand-picked spread of well-known equities across several years and sectors. Amounts are
     * the total spent (price × quantity) so the derived cost basis stays realistic.
     */
    private List<StockSeed> mockStockHoldings() {
        return List.of(
                new StockSeed("Apple Inc.", "50", "7250.00", LocalDate.of(2021, 3, 15), "Long-term core holding"),
                new StockSeed("Microsoft Corp.", "30", "8400.00", LocalDate.of(2021, 6, 10), "Cloud growth"),
                new StockSeed("NVIDIA Corp.", "40", "6000.00", LocalDate.of(2022, 1, 20), "AI / GPU bet"),
                new StockSeed("Amazon.com Inc.", "25", "4125.00", LocalDate.of(2021, 9, 5), null),
                new StockSeed("Alphabet Inc. Class A", "20", "2700.00", LocalDate.of(2022, 4, 12), "Search + ads"),
                new StockSeed("Tesla Inc.", "15", "3600.00", LocalDate.of(2021, 11, 22), "EV exposure"),
                new StockSeed("Meta Platforms Inc.", "35", "6300.00", LocalDate.of(2022, 7, 18), "Rebound play"),
                new StockSeed("Berkshire Hathaway Class B", "18", "5400.00", LocalDate.of(2023, 2, 9), "Value anchor"),
                new StockSeed("JPMorgan Chase & Co.", "45", "6300.00", LocalDate.of(2022, 10, 3), "Financials"),
                new StockSeed("Visa Inc.", "22", "4840.00", LocalDate.of(2021, 12, 14), "Payments moat"),
                new StockSeed("Johnson & Johnson", "28", "4480.00", LocalDate.of(2022, 3, 28), "Defensive healthcare"),
                new StockSeed("Procter & Gamble Co.", "26", "3900.00", LocalDate.of(2023, 5, 16), "Dividend staple"),
                new StockSeed("Coca-Cola Co.", "80", "4800.00", LocalDate.of(2021, 8, 19), "Dividend"),
                new StockSeed("Walt Disney Co.", "33", "3300.00", LocalDate.of(2022, 9, 27), "Media turnaround"),
                new StockSeed("Netflix Inc.", "12", "4200.00", LocalDate.of(2023, 1, 11), "Streaming leader"),
                new StockSeed("Adobe Inc.", "10", "4500.00", LocalDate.of(2022, 11, 8), "Creative SaaS"),
                new StockSeed("Salesforce Inc.", "16", "2880.00", LocalDate.of(2023, 3, 22), null),
                new StockSeed("Intel Corp.", "60", "2400.00", LocalDate.of(2022, 6, 30), "Turnaround speculation"),
                new StockSeed("Netflix Inc.", "38", "3800.00", LocalDate.of(2023, 4, 4), "AMD vs Intel"),
                new StockSeed("PayPal Holdings Inc.", "50", "3500.00", LocalDate.of(2022, 12, 19), "Beaten-down fintech"),
                new StockSeed("Pfizer Inc.", "70", "2800.00", LocalDate.of(2023, 6, 7), "Pharma dividend"),
                new StockSeed("Exxon Mobil Corp.", "44", "4400.00", LocalDate.of(2022, 5, 13), "Energy hedge"),
                new StockSeed("McDonald's Corp.", "14", "3640.00", LocalDate.of(2023, 7, 25), "Consumer staple"),
                new StockSeed("Nike Inc.", "30", "3300.00", LocalDate.of(2022, 8, 16), "Brand strength"),
                new StockSeed("Costco Wholesale Corp.", "8", "4000.00", LocalDate.of(2024, 1, 9), "Membership growth"));
    }

    /** Immutable seed row; carries amounts as strings to build exact {@link BigDecimal}s. */
    @RequiredArgsConstructor
    private static class StockSeed {

        private final String name;

        private final String quantity;

        private final String boughtForAmount;

        private final LocalDate date;

        private final String note;

        private AssetHolding toHolding(Asset asset, User user) {
            return new AssetHolding(
                    asset,
                    user,
                    name,
                    new BigDecimal(boughtForAmount),
                    new BigDecimal(quantity),
                    date,
                    note);
        }
    }
}
