package com.cognivault.readiness;
import com.cognivault.config.CogniVaultProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
@Service
public class ReadinessService {
 private final DataSource dataSource;private final MongoTemplate mongo;private final CogniVaultProperties props;private final ObjectMapper mapper;private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public ReadinessService(DataSource dataSource,MongoTemplate mongo,CogniVaultProperties props,ObjectMapper mapper){this.dataSource=dataSource;this.mongo=mongo;this.props=props;this.mapper=mapper;}
 public List<ServiceHealth> services(){List<ServiceHealth> out=new ArrayList<>();out.add(checkBackend());out.add(checkMysql());out.add(checkMongo());out.addAll(checkMl());out.add(new ServiceHealth("WebSocket / STOMP",ReadinessStatus.READY,Instant.now(),null,"STOMP endpoint is configured; client connection status is reported by the frontend."));return out;}
 private ServiceHealth checkBackend(){return new ServiceHealth("Spring Boot API",ReadinessStatus.READY,Instant.now(),0L,"Process is serving readiness requests.");}
 private ServiceHealth checkMysql(){long start=System.nanoTime();try(Connection c=dataSource.getConnection()){if(!c.isValid(2))throw new IllegalStateException("Connection validation failed");return new ServiceHealth("MySQL",ReadinessStatus.READY,Instant.now(),(System.nanoTime()-start)/1_000_000,"Database connection validated.");}catch(Exception e){return new ServiceHealth("MySQL",ReadinessStatus.UNAVAILABLE,Instant.now(),null,e.getMessage());}}
 private ServiceHealth checkMongo(){long start=System.nanoTime();try{MongoDatabase db=mongo.getDb();db.runCommand(new org.bson.Document("ping",1));return new ServiceHealth("MongoDB",ReadinessStatus.READY,Instant.now(),(System.nanoTime()-start)/1_000_000,"Mongo ping succeeded.");}catch(Exception e){return new ServiceHealth("MongoDB",ReadinessStatus.UNAVAILABLE,Instant.now(),null,e.getMessage());}}
 private List<ServiceHealth> checkMl(){String base=props.ml().baseUrl();if(base==null||base.isBlank())return List.of(new ServiceHealth("Python ML",ReadinessStatus.NOT_CONFIGURED,Instant.now(),null,"ML_SERVICE_URL is not configured."));try{long start=System.nanoTime();var request=HttpRequest.newBuilder(URI.create(base+"/health/readiness")).timeout(Duration.ofSeconds(10)).GET().build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)return List.of(new ServiceHealth("Python ML",ReadinessStatus.UNAVAILABLE,Instant.now(),null,"ML readiness returned HTTP "+response.statusCode()));JsonNode root=mapper.readTree(response.body());List<ServiceHealth> result=new ArrayList<>();result.add(new ServiceHealth("Python ML",parse(root.path("status").asText("READY")),Instant.now(),(System.nanoTime()-start)/1_000_000,"ML readiness endpoint responded."));JsonNode deps=root.path("dependencies");if(deps.isObject())deps.fields().forEachRemaining(entry->result.add(new ServiceHealth(entry.getKey(),parse(entry.getValue().path("status").asText("UNKNOWN")),Instant.now(),null,entry.getValue().path("details").asText(""))));return result;}catch(Exception e){return List.of(new ServiceHealth("Python ML",ReadinessStatus.UNAVAILABLE,Instant.now(),null,"ML service is unreachable."));}}
 private ReadinessStatus parse(String value){try{return ReadinessStatus.valueOf(value.toUpperCase(Locale.ROOT));}catch(Exception e){return ReadinessStatus.ERROR;}}
}