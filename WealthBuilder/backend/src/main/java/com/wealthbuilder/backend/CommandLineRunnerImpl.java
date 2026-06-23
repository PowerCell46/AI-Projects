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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;


/**
 * Development-only seeder that populates the moderator account with a realistic spread of
 * holdings across every asset class (Stocks, Bonds, Cryptocurrencies, Precious Metals, Personal
 * Growth), so the UI can be exercised against real-size data instead of an empty portfolio.
 *
 * <p>Each asset is created with its real catalog image loaded from {@code resources/images} and
 * stored as a {@code data:} URI. Idempotent per asset: it does nothing unless the moderator
 * exists, creates a missing asset on demand, and never adds a second batch of holdings once the
 * moderator already owns any holding under that asset. Runs after
 * {@link com.wealthbuilder.backend.config.DataSeeder} (which creates the moderator).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class CommandLineRunnerImpl implements CommandLineRunner {

    private static final String IMAGE_CLASSPATH_DIR = "images/";

    private final UserRepository userRepository;

    private final AssetRepository assetRepository;

    private final HoldingRepository holdingRepository;

    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(String... args) {
        final User moderator = findModerator();
        if (moderator == null) {
            log.warn("No moderator account found; skipping holdings seeding.");
            return;
        }

        assetSeeds().forEach(seed -> seedAsset(seed, moderator));
    }

    private User findModerator() {
        return userRepository
                .findByUsername(appProperties.getModerator().getUsername())
                .orElse(null);
    }

    /** Creates the asset if missing, then seeds its holdings unless they already exist. */
    private void seedAsset(AssetSeed seed, User moderator) {
        final Asset asset = findOrCreateAsset(seed);

        if (alreadySeeded(moderator, asset)) {
            return;
        }

        seedHoldings(seed, asset, moderator);
    }

    private Asset findOrCreateAsset(AssetSeed seed) {
        return assetRepository
                .findByNameIgnoreCase(seed.name)
                .orElseGet(() -> createAsset(seed));
    }

    private Asset createAsset(AssetSeed seed) {
        final Asset asset = assetRepository.save(new Asset(
                seed.name,
                seed.description,
                loadImageDataUri(seed.imageName),
                seed.imageName));
        log.info("Created '{}' asset for seeding.", seed.name);
        return asset;
    }

    /** True once the moderator owns at least one holding under the given asset. */
    private boolean alreadySeeded(User moderator, Asset asset) {
        return holdingRepository
                .findByUserAndAsset(moderator, asset, PageRequest.of(0, 1))
                .hasContent();
    }

    private void seedHoldings(AssetSeed seed, Asset asset, User moderator) {
        final List<AssetHolding> holdings = seed.holdings
                .stream()
                .map(holding -> holding.toHolding(asset, moderator))
                .toList();

        holdingRepository.saveAll(holdings);
        log.info("Seeded {} '{}' holdings for moderator '{}'.",
                holdings.size(), seed.name, moderator.getUsername());
    }

    /** Reads a classpath image and returns it as a {@code data:<mime>;base64,...} URI. */
    private String loadImageDataUri(String imageName) {
        final ClassPathResource resource = new ClassPathResource(IMAGE_CLASSPATH_DIR + imageName);
        try (InputStream stream = resource.getInputStream()) {
            final String base64 = Base64.getEncoder().encodeToString(stream.readAllBytes());
            return "data:" + mimeTypeFor(imageName) + ";base64," + base64;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load seed image: " + imageName, exception);
        }
    }

    private String mimeTypeFor(String imageName) {
        return imageName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private List<AssetSeed> assetSeeds() {
        return List.of(
                personalGrowthSeed(),
                preciousMetalsSeed(),
                cryptocurrenciesSeed(),
                bondsSeed(),
                stocksSeed());
    }

    /**
     * A hand-picked spread of well-known equities across several years and sectors. Amounts are
     * the total spent (price × quantity) so the derived cost basis stays realistic.
     */
    private AssetSeed stocksSeed() {
        return new AssetSeed(
                "Stocks",
                "Publicly traded company shares.",
                "Stocks.jpeg",
                List.of(
                        new HoldingSeed("Apple Inc.", "shares", "50", "7250.00", LocalDate.of(2021, 3, 15), "Long-term core holding"),
                        new HoldingSeed("Microsoft Corp.", "shares", "30", "8400.00", LocalDate.of(2021, 6, 10), "Cloud growth"),
                        new HoldingSeed("NVIDIA Corp.", "shares", "40", "6000.00", LocalDate.of(2022, 1, 20), "AI / GPU bet"),
                        new HoldingSeed("Amazon.com Inc.", "shares", "25", "4125.00", LocalDate.of(2021, 9, 5), null),
                        new HoldingSeed("Alphabet Inc. Class A", "shares", "20", "2700.00", LocalDate.of(2022, 4, 12), "Search + ads"),
                        new HoldingSeed("Tesla Inc.", "shares", "15", "3600.00", LocalDate.of(2021, 11, 22), "EV exposure"),
                        new HoldingSeed("Meta Platforms Inc.", "shares", "35", "6300.00", LocalDate.of(2022, 7, 18), "Rebound play"),
                        new HoldingSeed("Berkshire Hathaway Class B", "shares", "18", "5400.00", LocalDate.of(2023, 2, 9), "Value anchor"),
                        new HoldingSeed("JPMorgan Chase & Co.", "shares", "45", "6300.00", LocalDate.of(2022, 10, 3), "Financials"),
                        new HoldingSeed("Visa Inc.", "shares", "22", "4840.00", LocalDate.of(2021, 12, 14), "Payments moat"),
                        new HoldingSeed("Johnson & Johnson", "shares", "28", "4480.00", LocalDate.of(2022, 3, 28), "Defensive healthcare"),
                        new HoldingSeed("Procter & Gamble Co.", "shares", "26", "3900.00", LocalDate.of(2023, 5, 16), "Dividend staple"),
                        new HoldingSeed("Coca-Cola Co.", "shares", "80", "4800.00", LocalDate.of(2021, 8, 19), "Dividend"),
                        new HoldingSeed("Walt Disney Co.", "shares", "33", "3300.00", LocalDate.of(2022, 9, 27), "Media turnaround"),
                        new HoldingSeed("Netflix Inc.", "shares", "12", "4200.00", LocalDate.of(2023, 1, 11), "Streaming leader"),
                        new HoldingSeed("Adobe Inc.", "shares", "10", "4500.00", LocalDate.of(2022, 11, 8), "Creative SaaS"),
                        new HoldingSeed("Salesforce Inc.", "shares", "16", "2880.00", LocalDate.of(2023, 3, 22), null),
                        new HoldingSeed("Intel Corp.", "shares", "60", "2400.00", LocalDate.of(2022, 6, 30), "Turnaround speculation"),
                        new HoldingSeed("Advanced Micro Devices Inc.", "shares", "38", "3800.00", LocalDate.of(2023, 4, 4), "AMD vs Intel"),
                        new HoldingSeed("PayPal Holdings Inc.", "shares", "50", "3500.00", LocalDate.of(2022, 12, 19), "Beaten-down fintech"),
                        new HoldingSeed("Pfizer Inc.", "shares", "70", "2800.00", LocalDate.of(2023, 6, 7), "Pharma dividend"),
                        new HoldingSeed("Exxon Mobil Corp.", "shares", "44", "4400.00", LocalDate.of(2022, 5, 13), "Energy hedge"),
                        new HoldingSeed("McDonald's Corp.", "shares", "14", "3640.00", LocalDate.of(2023, 7, 25), "Consumer staple"),
                        new HoldingSeed("Nike Inc.", "shares", "30", "3300.00", LocalDate.of(2022, 8, 16), "Brand strength"),
                        new HoldingSeed("Costco Wholesale Corp.", "shares", "8", "4000.00", LocalDate.of(2024, 1, 9), "Membership growth")));
    }

    /** Fixed-income debt securities — government, corporate, and inflation-protected. */
    private AssetSeed bondsSeed() {
        return new AssetSeed(
                "Bonds",
                "Fixed-income debt securities.",
                "Bonds.png",
                List.of(
                        new HoldingSeed("US Treasury 10Y Note", "bonds", "10", "9800.00", LocalDate.of(2022, 2, 15), "Safe haven"),
                        new HoldingSeed("US Treasury 30Y Bond", "bonds", "5", "4750.00", LocalDate.of(2021, 9, 30), "Long duration"),
                        new HoldingSeed("Vanguard Total Bond ETF", "shares", "120", "9600.00", LocalDate.of(2022, 6, 11), "Diversified core"),
                        new HoldingSeed("iShares Corporate Bond ETF", "shares", "80", "4400.00", LocalDate.of(2023, 1, 18), "Corporate yield"),
                        new HoldingSeed("German Bund 10Y", "bonds", "6", "5700.00", LocalDate.of(2022, 4, 5), null),
                        new HoldingSeed("UK Gilt 5Y", "bonds", "7", "6650.00", LocalDate.of(2023, 3, 22), "Sterling exposure"),
                        new HoldingSeed("Municipal Bond Fund", "shares", "60", "3000.00", LocalDate.of(2021, 12, 2), "Tax-advantaged"),
                        new HoldingSeed("TIPS Inflation-Protected", "bonds", "12", "11400.00", LocalDate.of(2022, 8, 14), "Inflation hedge")));
    }

    /** Decentralized digital currencies and tokens; quantities are fractional, units are tickers. */
    private AssetSeed cryptocurrenciesSeed() {
        return new AssetSeed(
                "Cryptocurrencies",
                "Decentralized digital currencies and tokens.",
                "Cryptocurrencies.jpeg",
                List.of(
                        new HoldingSeed("Bitcoin", "BTC", "0.75", "22500.00", LocalDate.of(2021, 4, 10), "Digital gold"),
                        new HoldingSeed("Ethereum", "ETH", "5.5", "11000.00", LocalDate.of(2021, 7, 22), "Smart contracts"),
                        new HoldingSeed("Solana", "SOL", "120", "4800.00", LocalDate.of(2022, 1, 15), "High throughput"),
                        new HoldingSeed("Cardano", "ADA", "4000", "3600.00", LocalDate.of(2021, 9, 8), null),
                        new HoldingSeed("Polkadot", "DOT", "300", "4500.00", LocalDate.of(2022, 3, 19), "Interoperability"),
                        new HoldingSeed("Chainlink", "LINK", "250", "3750.00", LocalDate.of(2022, 5, 27), "Oracle network"),
                        new HoldingSeed("Avalanche", "AVAX", "90", "2700.00", LocalDate.of(2023, 2, 11), "Layer-1 bet"),
                        new HoldingSeed("Polygon", "MATIC", "5000", "4000.00", LocalDate.of(2022, 11, 4), "Scaling")));
    }

    /** Physical gold, silver, and other precious metals held as bullion or coins. */
    private AssetSeed preciousMetalsSeed() {
        return new AssetSeed(
                "Precious Metals",
                "Physical gold, silver, and other precious metals.",
                "PreciousMetals.png",
                List.of(
                        new HoldingSeed("Gold Bullion", "oz", "10", "19000.00", LocalDate.of(2021, 5, 12), "Core hedge"),
                        new HoldingSeed("Silver Bars", "oz", "500", "12500.00", LocalDate.of(2022, 2, 8), "Industrial + store of value"),
                        new HoldingSeed("Platinum Coins", "oz", "8", "8000.00", LocalDate.of(2022, 7, 30), null),
                        new HoldingSeed("Palladium", "oz", "5", "10000.00", LocalDate.of(2021, 11, 17), "Auto-catalyst demand"),
                        new HoldingSeed("Gold Sovereign Coins", "coins", "20", "9000.00", LocalDate.of(2023, 4, 2), "Collectible"),
                        new HoldingSeed("Silver Eagle Coins", "coins", "200", "6000.00", LocalDate.of(2023, 1, 25), "Bullion coins")));
    }

    /** Investments in skills, education, and self-development. Each item is a one-off commitment. */
    private AssetSeed personalGrowthSeed() {
        return new AssetSeed(
                "Personal Growth",
                "Investments in skills, education, and self-development.",
                "PersonalGrowth.jpeg",
                List.of(
                        new HoldingSeed("MBA Program", "program", "1", "45000.00", LocalDate.of(2022, 9, 1), "Career investment"),
                        new HoldingSeed("Online Coding Bootcamp", "course", "1", "8000.00", LocalDate.of(2022, 6, 18), "Career-switch skills"),
                        new HoldingSeed("Cloud Architecture Certification", "certification", "1", "1500.00", LocalDate.of(2023, 5, 20), "Cloud credential"),
                        new HoldingSeed("Spanish Language Course", "course", "1", "1200.00", LocalDate.of(2023, 2, 14), "Language skills"),
                        new HoldingSeed("Public Speaking Workshop", "workshop", "1", "800.00", LocalDate.of(2022, 11, 10), "Soft skills"),
                        new HoldingSeed("Personal Trainer Sessions", "sessions", "24", "2400.00", LocalDate.of(2023, 3, 5), "Health"),
                        new HoldingSeed("Finance Book Collection", "books", "30", "900.00", LocalDate.of(2021, 8, 22), "Self-education")));
    }

    /** Immutable definition of an asset to seed, together with its mock holdings. */
    @RequiredArgsConstructor
    private static class AssetSeed {

        private final String name;

        private final String description;

        private final String imageName;

        private final List<HoldingSeed> holdings;
    }

    /** Immutable seed row; carries amounts as strings to build exact {@link BigDecimal}s. */
    @RequiredArgsConstructor
    private static class HoldingSeed {

        private final String name;

        private final String unit;

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
                    unit,
                    new BigDecimal(quantity),
                    date,
                    note);
        }
    }
}
