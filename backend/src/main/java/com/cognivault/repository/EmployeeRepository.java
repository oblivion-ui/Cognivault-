package com.cognivault.repository;

import com.cognivault.domain.Employee;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    long countByUser_Status(com.cognivault.domain.AccountStatus status);

        long countByIdInAndUser_Status(List<UUID> ids, com.cognivault.domain.AccountStatus status);

    @Query(value = "select e from Employee e join fetch e.user", countQuery = "select count(e) from Employee e")
    Page<Employee> findAllWithUser(Pageable pageable);

    @Query("select e from Employee e join fetch e.user where e.id = :id")
    Optional<Employee> findByIdWithUser(UUID id);

    @Query("select e.id from Employee e where e.user.id = :userId")
    Optional<UUID> findEmployeeIdByUserId(UUID userId);

    @Query("select e.id from Employee e where e.managerUser.id = :managerUserId")
    List<UUID> findEmployeeIdsByManagerUserId(UUID managerUserId);

    @Query("select e.id from Employee e")
    List<UUID> findAllEmployeeIds();

    @Query("select case when count(e) > 0 then true else false end from Employee e where e.id = :employeeId and e.managerUser.id = :managerUserId")
    boolean existsManagedBy(UUID employeeId, UUID managerUserId);

    @Query(value = "select e from Employee e join fetch e.user where e.managerUser.id = :managerUserId",
           countQuery = "select count(e) from Employee e where e.managerUser.id = :managerUserId")
    Page<Employee> findAllByManagerUserId(UUID managerUserId, Pageable pageable);
}

\n