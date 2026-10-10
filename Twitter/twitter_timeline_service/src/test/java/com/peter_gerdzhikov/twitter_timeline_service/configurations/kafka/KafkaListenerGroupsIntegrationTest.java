package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.MemberDescription;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;

class KafkaListenerGroupsIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    @Value("${spring.kafka.consumer.group-id}")
    private String groupIdPrefix;

    static Stream<Arguments> groupSuffixesAndTopics() {
        return Stream.of(
                Arguments.of("-tweet-created", "tweet.created"),
                Arguments.of("-tweet-deleted", "tweet.deleted"),
                Arguments.of("-user-followed", "user.followed"),
                Arguments.of("-user-unfollowed", "user.unfollowed"));
    }

    @ParameterizedTest
    @MethodSource("groupSuffixesAndTopics")
    void should_read_only_its_own_topic_in_its_own_group_when_the_listener_has_started(String groupSuffix, String topic) {
        String groupId = groupIdPrefix + groupSuffix;

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers()))) {
            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(200))
                    .ignoreExceptions()
                    .untilAsserted(() -> assertThat(assignedTopics(admin, groupId)).containsExactly(topic));
        }
    }

    private static Set<String> assignedTopics(Admin admin, String groupId) throws Exception {
        ConsumerGroupDescription group = admin
                .describeConsumerGroups(List.of(groupId))
                .describedGroups()
                .get(groupId)
                .get();

        return group.members().stream()
                .map(MemberDescription::assignment)
                .flatMap(assignment -> assignment.topicPartitions().stream())
                .map(topicPartition -> topicPartition.topic())
                .collect(Collectors.toSet());
    }
}
