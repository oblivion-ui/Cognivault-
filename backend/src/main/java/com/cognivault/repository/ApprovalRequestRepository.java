package com.cognivault.repository;

import com.cognivault.domain.ApprovalRequest;
import com.cognivault.domain.ApprovalStatus;
import com.cognivault.domain.ApprovalRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Page<ApprovalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ApprovalRequest> findByStatusOrderByCreatedAtDesc(
            ApprovalStatus status,
            Pageable pageable);

        Page<ApprovalRequest> findByRequestTypeOrderByCreatedAtDesc(
            ApprovalRequestType requestType,
            Pageable pageable);

            Page<ApprovalRequest> findByRequestTypeNotOrderByCreatedAtDesc(
                ApprovalRequestType requestType,
                Pageable pageable);

            Page<ApprovalRequest> findByRequestTypeNotAndStatusOrderByCreatedAtDesc(
                ApprovalRequestType requestType,
                ApprovalStatus status,
                Pageable pageable);

                Page<ApprovalRequest> findByRequestTypeInAndTargetInOrderByCreatedAtDesc(
                    List<ApprovalRequestType> requestTypes,
                    List<String> targets,
                    Pageable pageable);

                Page<ApprovalRequest> findByRequestTypeInAndStatusAndTargetInOrderByCreatedAtDesc(
                    List<ApprovalRequestType> requestTypes,
                    ApprovalStatus status,
                    List<String> targets,
                    Pageable pageable);
}
