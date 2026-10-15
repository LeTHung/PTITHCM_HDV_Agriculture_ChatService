package vn.ptit.soa.chat.conversation;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-learning")
public class ConversationRepository {
    private final MongoTemplate mongo;

    public ConversationRepository(MongoTemplate mongo) {
        this.mongo = mongo;
        mongo.indexOps(Conversation.class).ensureIndex(
                new Index().on("offer_id", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique().named("unique_offer_id"));
    }

    public Optional<Conversation> findByOfferId(String offerId) {
        Query query = Query.query(Criteria.where("offer_id").is(offerId));
        return Optional.ofNullable(mongo.findOne(query, Conversation.class));
    }

    public Optional<Conversation> findById(String conversationId) {
        return Optional.ofNullable(mongo.findById(conversationId, Conversation.class));
    }

    public void touch(String conversationId, java.time.Instant at) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(conversationId)),
                new Update().set("updated_at", at), Conversation.class);
    }

    public Conversation insert(Conversation conversation) {
        return mongo.insert(conversation);
    }
}
