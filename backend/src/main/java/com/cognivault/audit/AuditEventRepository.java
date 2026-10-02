package com.cognivault.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface AuditEventRepository extends MongoRepository<AuditEventDocument, String> {

    Optional<AuditEventDocument> findTopByOrderByChainPositionDesc();

    Optional<AuditEventDocument> findByEventId(String eventId);

    Page<AuditEventDocument> findByEntityTypeAndEntityId(
            String entityType,
            String entityId,
            Pageable pageable);

    Page<AuditEventDocument> findByEntityTypeAndEntityIdInOrderByChainPositionAsc(
            String entityType,
            List<String> entityIds,
            Pageable pageable);

    List<AuditEventDocument> findByEntityTypeAndEntityIdInAndTimestampGreaterThanEqualAndTimestampLessThan(
            String entityType,
            List<String> entityIds,
            Instant from,
            Instant to);

    List<AuditEventDocument> findTop10ByEntityTypeAndEntityIdInOrderByChainPositionDesc(
            String entityType,
            List<String> entityIds);

    Optional<AuditEventDocument> findFirstByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType,
            String entityId);

    Optional<AuditEventDocument> findFirstByEntityTypeAndEntityIdAndTimestampLessThanEqualOrderByTimestampDesc(
            String entityType,
            String entityId,
            Instant timestamp);

    List<AuditEventDocument> findByChainPositionLessThanOrderByChainPositionDesc(
            long chainPosition,
            Pageable pageable);

    @Query("{ ''entityType'': ?0, ''entityId'': { ''$in'': ?1 }, ''timestamp'': { ''$gte'': ?2, ''$lt'': ?3 } }")
    List<AuditEventDocument> findByEntityTypeAndEntityIdInAndTimestampRange(
            String entityType,
            List<String> entityIds,
            Instant from,
            Instant to);
    @Query(value = "{ 'timestamp': { '$gte': ?0, '$lt': ?1 } }", count = true)
    long countByTimestampRange(Instant start, Instant end);

    List<AuditEventDocument> findTop10ByOrderByChainPositionDesc();

    @Query("{ 'timestamp': { '$gte': ?0, '$lt': ?1 } }")
    List<AuditEventDocument> findByTimestampRangeOrderByTimestampAsc(
            Instant start,
            Instant end);

    default List<AuditEventDocument> ordered() {
        return findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC,
                        "chainPosition"));
    }
}
