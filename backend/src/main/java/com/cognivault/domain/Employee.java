package com.cognivault.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "employees",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_employees_employee_id", columnNames = "employee_id"),
        @UniqueConstraint(name = "uk_employees_user_id", columnNames = "user_id")
    }
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String department;

    @Column(length = 120)
    private String designation;

    @Column(name = "manager_name", length = 160)
    private String manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id")
    private UserAccount managerUser;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "employment_type", length = 40)
    private String employmentType;

    @Column(length = 120)
    private String location;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    protected Employee() {}

    public Employee(
            String employeeId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String address,
            String department,
            String designation,
            String manager,
            LocalDate joiningDate,
            String employmentType,
            String location,
            UserAccount user) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.department = department;
        this.designation = designation;
        this.manager = manager;
        this.joiningDate = joiningDate;
        this.employmentType = employmentType;
        this.location = location;
        this.user = user;
    }

    public UUID getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getDepartment() { return department; }
    public String getDesignation() { return designation; }
    public String getManager() { return manager; }
    public UserAccount getManagerUser() { return managerUser; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public String getEmploymentType() { return employmentType; }
    public String getLocation() { return location; }
    public UserAccount getUser() { return user; }

    public void setManagerUser(UserAccount managerUser) {
        this.managerUser = managerUser;
    }

    public void update(
            String firstName,
            String lastName,
            String email,
            String phone,
            String department,
            String designation,
            String manager,
            String employmentType,
            String location) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.department = department;
        this.designation = designation;
        this.manager = manager;
        this.employmentType = employmentType;
        this.location = location;
    }
}
