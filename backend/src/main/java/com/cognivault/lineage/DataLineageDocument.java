package com.cognivault.lineage;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

@Document("data_lineage")
public class DataLineageDocument {

    @Id
    private String id;

    @Indexed
    private String sourceEntityType;

    @Indexed
    private String sourceEntityId;

    @Indexed
    private String operation;

    @Indexed
    private String targetEntityType;

    @Indexed
    private String targetEntityId;

    @Indexed
    private String eventId;

    private String actor;
    private String actorUserId;

    @Field("timestamp")
    private Instant timestamp;

    @Field("source_snapshot")
    private Map<String, Object> sourceSnapshot;

    @Field("target_snapshot")
    private Map<String, Object> targetSnapshot;

    protected DataLineageDocument() {
    }

    public DataLineageDocument(
            String sourceEntityType,
            String sourceEntityId,
            String operation,
            String targetEntityType,
            String targetEntityId,
            String eventId,
            String actor,
            String actorUserId,
            Instant timestamp,
            Map<String, Object> sourceSnapshot,
            Map<String, Object> targetSnapshot) {
        this.sourceEntityType = sourceEntityType;
        this.sourceEntityId = sourceEntityId;
        this.operation = operation;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.eventId = eventId;
        this.actor = actor;
        this.actorUserId = actorUserId;
        this.timestamp = timestamp;
        this.sourceSnapshot = sourceSnapshot;
        this.targetSnapshot = targetSnapshot;
    }

    public String getId() {
        return id;
    }

    public String getSourceEntityType() {
        return sourceEntityType;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public String getOperation() {
        return operation;
    }

    public String getTargetEntityType() {
        return targetEntityType;
    }

    public String getTargetEntityId() {
        return targetEntityId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getActor() {
        return actor;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getSourceSnapshot() {
        return sourceSnapshot;
    }

    public Map<String, Object> getTargetSnapshot() {
        return targetSnapshot;
    }
}
