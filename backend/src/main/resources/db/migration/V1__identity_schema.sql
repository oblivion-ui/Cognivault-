CREATE TABLE users (
 id BINARY(16) NOT NULL,
 username VARCHAR(80) NOT NULL,
 email VARCHAR(254) NOT NULL,
 full_name VARCHAR(160) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL,
 must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL,
 last_login TIMESTAMP(6) NULL,
 PRIMARY KEY (id), UNIQUE KEY uk_users_username (username), UNIQUE KEY uk_users_email (email)
);
CREATE TABLE employees (
 id BINARY(16) NOT NULL,
 employee_id VARCHAR(50) NOT NULL,
 first_name VARCHAR(80) NOT NULL,
 last_name VARCHAR(80) NOT NULL,
 email VARCHAR(254) NOT NULL,
 phone VARCHAR(40), address VARCHAR(500), department VARCHAR(100), designation VARCHAR(120), manager_name VARCHAR(160), joining_date DATE, employment_type VARCHAR(40), location VARCHAR(120), user_id BINARY(16) NOT NULL,
 PRIMARY KEY (id), UNIQUE KEY uk_employees_employee_id (employee_id), UNIQUE KEY uk_employees_user_id (user_id), CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_employees_department ON employees(department);
CREATE INDEX idx_employees_email ON employees(email);