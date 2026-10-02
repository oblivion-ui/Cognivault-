package com.cognivault.audit;

import com.cognivault.security.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AuditLedgerService {
 private static final String GENESIS="GENESIS";
 private final AuditEventRepository repository;
 private final ObjectMapper mapper;
 private final ApplicationEventPublisher eventPublisher;

 public AuditLedgerService(AuditEventRepository repository,ObjectMapper mapper){
  this(repository,mapper,null);
 }

 @org.springframework.beans.factory.annotation.Autowired
 public AuditLedgerService(AuditEventRepository repository,ObjectMapper mapper,ApplicationEventPublisher eventPublisher){
  this.repository=repository;
  this.mapper=mapper;
  this.eventPublisher=eventPublisher;
 }

 public synchronized AuditEventDocument append(String action,String entityType,String entityId,Object before,Object after){
  var actor=CurrentActor.get();
  var previous=repository.findTopByOrderByChainPositionDesc();
  long position=previous.map(x->x.getChainPosition()+1).orElse(1L);
  String previousHash=previous.map(AuditEventDocument::getCurrentHash).orElse(GENESIS);
  Instant timestamp=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  String eventId="evt-"+UUID.randomUUID();
  Map<String,Object> beforeMap=safeSnapshot(before);
  Map<String,Object> afterMap=safeSnapshot(after);
  String currentHash=hash(canonical(eventId,actor.username(),actor.userId(),action,entityType,entityId,timestamp,beforeMap,afterMap,previousHash,position));
  var event=new AuditEventDocument(eventId,actor.username(),actor.userId(),action,entityType,entityId,timestamp,beforeMap,afterMap,previousHash,currentHash,position);
  var savedEvent=repository.save(event);
  if(eventPublisher!=null){
   eventPublisher.publishEvent(new AuditEventCreated(savedEvent));
  }
  return savedEvent;
 }

 public IntegrityResult verify(){
  var events=repository.ordered();
  String previous=GENESIS;
  long expected=1;
  for(var e:events){
   String recomputed=hash(canonical(e.getEventId(),e.getActor(),e.getActorUserId(),e.getAction(),e.getEntityType(),e.getEntityId(),e.getTimestamp(),e.getBeforeSnapshot(),e.getAfterSnapshot(),e.getPreviousHash(),e.getChainPosition()));
   if(e.getChainPosition()!=expected||!Objects.equals(e.getPreviousHash(),previous)||!Objects.equals(e.getCurrentHash(),recomputed))
    return new IntegrityResult("FAILED",Instant.now(),events.size(),e.getEventId(),e.getPreviousHash(),e.getCurrentHash(),e.getChainPosition(),"Hash-chain verification failed.");
   previous=e.getCurrentHash();
   expected++;
  }
  return new IntegrityResult("VERIFIED",Instant.now(),events.size(),null,null,previous,events.size(),"All audit event links matched their recorded hashes.");
 }

 private Map<String,Object> safeSnapshot(Object value){
  if(value==null)return null;
  if(value instanceof Map<?,?> map)return sanitize(map);
  return sanitize(mapper.convertValue(value,Map.class));
 }

 private Map<String,Object> sanitize(Map<?,?> source){
  Map<String,Object> out=new TreeMap<>();
  source.forEach((rawKey,value)->{
   String key=String.valueOf(rawKey);
   String lower=key.toLowerCase(Locale.ROOT);
   String normalized=lower.replaceAll("[^a-z0-9]","");
   if((lower.contains("password")&&!normalized.equals("mustchangepassword"))||lower.contains("secret")||lower.contains("token"))return;
   if(value instanceof Map<?,?> nested)out.put(key,sanitize(nested));
   else out.put(key,value);
  });
  return out;
 }

 private String canonical(String eventId,String actor,String actorId,String action,String entityType,String entityId,Instant timestamp,Map<String,Object> before,Map<String,Object> after,String previousHash,long position){
  Map<String,Object> value=new TreeMap<>();
  value.put("action",action);
  value.put("actor",actor);
  value.put("actorUserId",actorId);
  value.put("after",after);
  value.put("before",before);
  value.put("chainPosition",position);
  value.put("entityId",entityId);
  value.put("entityType",entityType);
  value.put("eventId",eventId);
  value.put("previousHash",previousHash);
  value.put("timestamp",timestamp.toString());
  try{return mapper.writeValueAsString(value);}
  catch(JsonProcessingException e){throw new IllegalStateException("Cannot canonicalize audit event",e);}
 }

 private String hash(String value){
  try{
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
   StringBuilder out=new StringBuilder();
   for(byte b:digest)out.append(String.format("%02x",b));
   return out.toString();
  }catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}
 }

 public record IntegrityResult(String status,Instant verifiedAt,int totalEventsChecked,String affectedEventId,String previousHash,String currentHash,long chainPosition,String message){}
}
