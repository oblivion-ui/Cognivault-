package com.cognivault.audit;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("audit_events")
public class AuditEventDocument {
 @Id private String id; @Indexed(unique=true) private String eventId; private String actor; private String actorUserId; private String action; private String entityType; private String entityId; private Instant timestamp; private Map<String,Object> beforeSnapshot; private Map<String,Object> afterSnapshot; private String previousHash; private String currentHash; @Indexed(unique=true) private long chainPosition;
 public AuditEventDocument(){}
 public AuditEventDocument(String eventId,String actor,String actorUserId,String action,String entityType,String entityId,Instant timestamp,Map<String,Object> beforeSnapshot,Map<String,Object> afterSnapshot,String previousHash,String currentHash,long chainPosition){this.eventId=eventId;this.actor=actor;this.actorUserId=actorUserId;this.action=action;this.entityType=entityType;this.entityId=entityId;this.timestamp=timestamp;this.beforeSnapshot=beforeSnapshot;this.afterSnapshot=afterSnapshot;this.previousHash=previousHash;this.currentHash=currentHash;this.chainPosition=chainPosition;}
 public String getId(){return id;} public String getEventId(){return eventId;} public String getActor(){return actor;} public String getActorUserId(){return actorUserId;} public String getAction(){return action;} public String getEntityType(){return entityType;} public String getEntityId(){return entityId;} public Instant getTimestamp(){return timestamp;} public Map<String,Object> getBeforeSnapshot(){return beforeSnapshot;} public Map<String,Object> getAfterSnapshot(){return afterSnapshot;} public String getPreviousHash(){return previousHash;} public String getCurrentHash(){return currentHash;} public long getChainPosition(){return chainPosition;}
}