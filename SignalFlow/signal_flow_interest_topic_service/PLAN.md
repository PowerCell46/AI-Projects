CommonEntity:
- UUID id
- Instant createdAt
- Instant updatedAt;

InterestTopics extends CommonEntity:
- String name
- String description
- String prompt
- List<String> subscribers - their emails
- Category category

Categories:
- String name Primary key or extends commonEntity

TopicNews:
- InterestTopic interestTopic
- String/TEXT data;
- ins

GET endpoint for fetching all topics (with paging)
---
Endpoints:


Chron job that activates each day, goes through each interest topic,
makes a request to some AI model asking for the latest news and creates
a TopicNews instance, then sends 