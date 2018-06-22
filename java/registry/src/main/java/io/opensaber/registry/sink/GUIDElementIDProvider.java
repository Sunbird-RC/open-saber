package io.opensaber.registry.sink;

import com.steelbridgelabs.oss.neo4j.structure.Neo4JElementIdProvider;
import org.slf4j.Logger;
import org.neo4j.driver.v1.types.Entity;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class GUIDElementIDProvider implements Neo4JElementIdProvider<Long> {

    private static final Logger logger = LoggerFactory.getLogger(GUIDElementIDProvider.class);

    public static final String DefaultIdFieldName = "id";

    @Override
    public Long generate() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    @Override
    public String fieldName() {
        return DefaultIdFieldName;
    }

    @Override
    public Long get(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        // return id()
        return entity.get(DefaultIdFieldName).asLong();
    }

    @Override
    public Long processIdentifier(Object id) {
        Objects.requireNonNull(id, "Element identifier cannot be null");
        // check for Long
        if (id instanceof Long)
            return (Long)id;
        // check for numeric types
        if (id instanceof Number)
            return ((Number)id).longValue();
        // check for string
        if (id instanceof String)
            return Long.valueOf((String)id);
        // error
        throw new IllegalArgumentException(String.format("Expected an id that is convertible to Long but received %s", id.getClass()));
    }

    @Override
    public String matchPredicateOperand(String alias) {
        Objects.requireNonNull(alias, "alias cannot be null");
        // id(alias)
        return alias + "." + DefaultIdFieldName;
    }
}
