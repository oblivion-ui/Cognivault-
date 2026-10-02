ALTER TABLE employees
    ADD COLUMN manager_user_id BINARY(16) NULL,
    ADD CONSTRAINT fk_employees_manager_user
        FOREIGN KEY (manager_user_id) REFERENCES users(id);

CREATE INDEX idx_employees_manager_user_id
    ON employees(manager_user_id);
