package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import java.util.List;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.CategoryService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.DatabaseSeedService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSeedServiceImpl implements DatabaseSeedService {

    private static final List<CategorySeed> SEED_CATALOG = List.of(
            new CategorySeed("Technology", List.of(
                    new TopicSeed(
                            "Artificial Intelligence",
                            "Breakthroughs, product launches, and industry moves in AI.",
                            "Cover major AI model releases, research breakthroughs, and moves by leading AI "
                                    + "labs and big tech companies."),
                    new TopicSeed(
                            "Consumer Electronics",
                            "New devices, gadgets, and consumer tech launches.",
                            "Cover new smartphone, laptop, wearable, and other consumer device launches, "
                                    + "along with notable product recalls or price changes."),
                    new TopicSeed(
                            "Cybersecurity",
                            "Data breaches, vulnerabilities, and security industry news.",
                            "Cover newly disclosed data breaches, critical vulnerabilities, ransomware "
                                    + "incidents, and major cybersecurity industry announcements."))),
            new CategorySeed("Business & Finance", List.of(
                    new TopicSeed(
                            "Stock Markets",
                            "Major moves in global stock indices and notable earnings.",
                            "Cover significant moves in major stock indices (S&P 500, Nasdaq, Dow, FTSE, "
                                    + "major Asian indices) and notable company earnings reports."),
                    new TopicSeed(
                            "Startups & Venture Capital",
                            "Funding rounds, acquisitions, and notable startup news.",
                            "Cover notable startup funding rounds, acquisitions, IPOs, and venture capital "
                                    + "trends."),
                    new TopicSeed(
                            "Cryptocurrency",
                            "Price movements, regulation, and major crypto industry events.",
                            "Cover significant price movements in major cryptocurrencies, regulatory "
                                    + "developments, and notable events at crypto exchanges or projects."))),
            new CategorySeed("Politics", List.of(
                    new TopicSeed(
                            "U.S. Politics",
                            "Federal policy, Congress, and White House news.",
                            "Cover major developments in U.S. federal politics, including Congress, the "
                                    + "White House, and significant policy decisions."),
                    new TopicSeed(
                            "European Union Affairs",
                            "EU policy, legislation, and member-state relations.",
                            "Cover major EU policy decisions, European Parliament activity, and significant "
                                    + "developments among EU member states."),
                    new TopicSeed(
                            "Elections Worldwide",
                            "National elections, results, and campaign developments globally.",
                            "Cover notable national elections worldwide, including campaign developments, "
                                    + "results, and disputes."))),
            new CategorySeed("Science", List.of(
                    new TopicSeed(
                            "Space Exploration",
                            "Space missions, launches, and astronomical discoveries.",
                            "Cover notable space agency and private space company missions, rocket "
                                    + "launches, and significant astronomical discoveries."),
                    new TopicSeed(
                            "Medical Research",
                            "New studies, clinical trials, and medical breakthroughs.",
                            "Cover significant new medical research findings, clinical trial results, and "
                                    + "breakthroughs in treatment or diagnostics."),
                    new TopicSeed(
                            "Climate Science",
                            "Climate research findings and scientific reports on global warming.",
                            "Cover new climate research findings, major scientific reports, and notable "
                                    + "studies on global warming impacts."))),
            new CategorySeed("Health & Wellness", List.of(
                    new TopicSeed(
                            "Public Health",
                            "Disease outbreaks, health policy, and public health advisories.",
                            "Cover disease outbreaks, public health policy changes, and notable advisories "
                                    + "from health authorities."),
                    new TopicSeed(
                            "Nutrition & Fitness",
                            "Dietary research, fitness trends, and wellness guidance.",
                            "Cover notable nutrition research findings, fitness trends, and evidence-based "
                                    + "wellness guidance."),
                    new TopicSeed(
                            "Mental Health",
                            "Mental health research, awareness campaigns, and policy news.",
                            "Cover notable mental health research, awareness campaigns, and policy "
                                    + "developments affecting mental healthcare."))),
            new CategorySeed("Sports", List.of(
                    new TopicSeed(
                            "Football (Soccer)",
                            "Match results, transfers, and major league news.",
                            "Cover major football (soccer) match results, transfer news, and significant "
                                    + "league or tournament developments."),
                    new TopicSeed(
                            "Basketball",
                            "NBA and international basketball results and news.",
                            "Cover major NBA and international basketball game results, trades, and league "
                                    + "news."),
                    new TopicSeed(
                            "Olympic Sports",
                            "News across Olympic sports disciplines and athlete achievements.",
                            "Cover notable news across Olympic sports disciplines, including athlete "
                                    + "achievements and event results."))),
            new CategorySeed("Entertainment", List.of(
                    new TopicSeed(
                            "Film & Television",
                            "Movie and TV releases, box office, and industry news.",
                            "Cover notable film and TV releases, box office results, and entertainment "
                                    + "industry announcements."),
                    new TopicSeed(
                            "Music Industry",
                            "Album releases, tours, and major music industry news.",
                            "Cover notable album and single releases, major tour announcements, and music "
                                    + "industry business news."),
                    new TopicSeed(
                            "Video Games",
                            "Game releases, studio news, and gaming industry trends.",
                            "Cover notable video game releases, studio announcements, and gaming industry "
                                    + "trends."))),
            new CategorySeed("Environment & Climate", List.of(
                    new TopicSeed(
                            "Renewable Energy",
                            "Solar, wind, and clean energy industry developments.",
                            "Cover notable developments in solar, wind, and other renewable energy "
                                    + "industries, including major project launches."),
                    new TopicSeed(
                            "Climate Policy",
                            "Government and international climate policy decisions.",
                            "Cover notable government and international climate policy decisions, "
                                    + "agreements, and regulatory changes."),
                    new TopicSeed(
                            "Wildlife Conservation",
                            "Conservation efforts and biodiversity news.",
                            "Cover notable wildlife conservation efforts, endangered species news, and "
                                    + "biodiversity research findings."))),
            new CategorySeed("World Affairs", List.of(
                    new TopicSeed(
                            "Middle East",
                            "Political and security developments across the Middle East.",
                            "Cover significant political, security, and diplomatic developments across the "
                                    + "Middle East."),
                    new TopicSeed(
                            "Asia-Pacific",
                            "Political and economic developments across the Asia-Pacific region.",
                            "Cover significant political and economic developments across the Asia-Pacific "
                                    + "region."),
                    new TopicSeed(
                            "International Trade",
                            "Trade agreements, tariffs, and global supply chain news.",
                            "Cover notable trade agreements, tariff changes, and global supply chain "
                                    + "developments."))),
            new CategorySeed("Automotive & Transportation", List.of(
                    new TopicSeed(
                            "Electric Vehicles",
                            "EV launches, sales trends, and industry developments.",
                            "Cover notable electric vehicle launches, sales trends, and industry "
                                    + "developments across major automakers."),
                    new TopicSeed(
                            "Autonomous Driving",
                            "Self-driving technology news and regulatory developments.",
                            "Cover notable self-driving vehicle technology announcements, testing "
                                    + "milestones, and regulatory developments."),
                    new TopicSeed(
                            "Aviation & Aerospace",
                            "Airline industry, aircraft manufacturing, and aerospace news.",
                            "Cover notable airline industry news, aircraft manufacturing developments, and "
                                    + "commercial aerospace announcements."))));

    private final CategoryService categoryService;

    private final CategoryRepository categoryRepository;

    private final InterestTopicService interestTopicService;

    @Override
    public void seedInitialCatalog() {
        if (categoryRepository.count() > 0) {
            log.info("Categories already exist - skipping initial catalog seeding.");
            return;
        }

        SEED_CATALOG.forEach(this::seedCategory);
        log.info("Seeded {} categories and their interest topics.", SEED_CATALOG.size());
    }

    private void seedCategory(CategorySeed categorySeed) {
        Category category = categoryService.create(categorySeed.getName());
        categorySeed.getTopics().forEach(topicSeed -> interestTopicService.create(
                topicSeed.getName(), topicSeed.getDescription(), topicSeed.getPrompt(), category.getId()));
    }

    @Value
    private static class CategorySeed {

        String name;

        List<TopicSeed> topics;
    }

    @Value
    private static class TopicSeed {

        String name;

        String description;

        String prompt;
    }
}
