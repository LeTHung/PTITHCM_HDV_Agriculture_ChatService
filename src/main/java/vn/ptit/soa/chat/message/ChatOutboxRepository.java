package vn.ptit.soa.chat.message;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-learning")
public class ChatOutboxRepository {
    private final MongoTemplate mongo;
    public ChatOutboxRepository(MongoTemplate mongo) { this.mongo = mongo; }
    public void insert(ChatOutboxEvent event) { mongo.insert(event); }
}
