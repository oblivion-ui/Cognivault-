package com.cognivault.lineage;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DataLineageRepository extends MongoRepository<DataLineageDocument, String> {

    List<DataLineageDocument> findBySourceEntityTypeAndSourceEntityIdOrderByTimestampDesc(
            String sourceEntityType,
            String sourceEntityId);

    List<DataLineageDocument> findByTargetEntityTypeAndTargetEntityIdOrderByTimestampDesc(
            String targetEntityType,
            String targetEntityId);
}
