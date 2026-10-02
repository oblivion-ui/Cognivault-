package com.cognivault.api;
import com.cognivault.audit.AuditEventDocument;
import java.time.Instant;
import java.util.Map;
import java.util.List;
public final class AuditDtos {private AuditDtos(){}
 public record ChangedField(String field,Object before,Object after){}
 public record AuditResponse(String id,String eventId,Instant timestamp,String actor,String actorUserId,String action,String entity,String entityId,Map<String,Object> beforeSnapshot,Map<String,Object> afterSnapshot,List<ChangedField> changedFields,String previousHash,String hash,long chainPosition){public static AuditResponse from(AuditEventDocument e){Map<String,Object> before=e.getBeforeSnapshot()==null?Map.of():e.getBeforeSnapshot();Map<String,Object> after=e.getAfterSnapshot()==null?Map.of():e.getAfterSnapshot();var fields=new java.util.ArrayList<ChangedField>();var keys=new java.util.TreeSet<String>();keys.addAll(before.keySet());keys.addAll(after.keySet());for(String key:keys){Object oldValue=before.get(key),newValue=after.get(key);if(!java.util.Objects.equals(oldValue,newValue))fields.add(new ChangedField(key,oldValue,newValue));}return new AuditResponse(e.getId(),e.getEventId(),e.getTimestamp(),e.getActor(),e.getActorUserId(),e.getAction(),e.getEntityType(),e.getEntityId(),e.getBeforeSnapshot(),e.getAfterSnapshot(),fields,e.getPreviousHash(),e.getCurrentHash(),e.getChainPosition());}}
}