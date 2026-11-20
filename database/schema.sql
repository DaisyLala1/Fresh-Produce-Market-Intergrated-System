-- =====================================================================
-- Fresh Produce Market POS and Consignment Management System
-- MySQL 8 schema (reference copy).
--
-- The Spring Boot application creates/updates these tables automatically
-- (spring.jpa.hibernate.ddl-auto=update), so running this script is optional.
-- It is provided as documentation of the database design (Assignment 3, Q4.2)
-- and for administrators who prefer to create the schema manually.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS fresh_produce_pos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fresh_produce_pos;

-- Agents and Administrators (farmers are NOT system users)
CREATE TABLE IF NOT EXISTS app_user (
    user_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,              -- BCrypt hash, never the plain password
    full_name      VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('ADMINISTRATOR', 'AGENT')),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME(6)  NOT NULL
);

CREATE TABLE IF NOT EXISTS farmer (
    farmer_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    farmer_code     VARCHAR(20) UNIQUE,
    name            VARCHAR(100) NOT NULL,
    contact_number  VARCHAR(20),
    address         VARCHAR(255),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)  NOT NULL
);

CREATE TABLE IF NOT EXISTS product (
    product_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    category         VARCHAR(50)  NOT NULL,
    unit_of_measure  VARCHAR(20)  NOT NULL,
    selling_price    DECIMAL(12,2) NOT NULL CHECK (selling_price > 0),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS consignment (
    consignment_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    consignment_reference  VARCHAR(30) UNIQUE,           -- e.g. CON-2026-00056
    farmer_id              BIGINT NOT NULL,
    product_id             BIGINT NOT NULL,
    received_date          DATE   NOT NULL,
    quantity_received      DECIMAL(12,2) NOT NULL CHECK (quantity_received > 0),
    quantity_remaining     DECIMAL(12,2) NOT NULL CHECK (quantity_remaining >= 0),
    unit_price             DECIMAL(12,2) NOT NULL CHECK (unit_price > 0),
    grade                  VARCHAR(20),
    notes                  VARCHAR(500),
    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | DEPLETED
    recorded_by            BIGINT,
    created_at             DATETIME(6) NOT NULL,
    CONSTRAINT fk_consignment_farmer  FOREIGN KEY (farmer_id)   REFERENCES farmer(farmer_id),
    CONSTRAINT fk_consignment_product FOREIGN KEY (product_id)  REFERENCES product(product_id),
    CONSTRAINT fk_consignment_user    FOREIGN KEY (recorded_by) REFERENCES app_user(user_id),
    INDEX idx_consignment_product_date (product_id, received_date)
);

CREATE TABLE IF NOT EXISTS sale (
    sale_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_number  VARCHAR(30) UNIQUE,                  -- e.g. RCP-20260916-000123
    sale_date       DATETIME(6) NOT NULL,
    agent_id        BIGINT NOT NULL,
    sub_total       DECIMAL(12,2) NOT NULL,
    discount        DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount    DECIMAL(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    CONSTRAINT fk_sale_agent FOREIGN KEY (agent_id) REFERENCES app_user(user_id),
    INDEX idx_sale_date (sale_date)
);

CREATE TABLE IF NOT EXISTS sale_item (
    sale_item_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id        BIGINT NOT NULL,
    product_id     BIGINT NOT NULL,
    quantity_sold  DECIMAL(12,2) NOT NULL CHECK (quantity_sold > 0),
    unit_price     DECIMAL(12,2) NOT NULL,
    subtotal       DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_item_sale    FOREIGN KEY (sale_id)    REFERENCES sale(sale_id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- Which consignment supplied which part of a sale line (FIFO traceability)
CREATE TABLE IF NOT EXISTS consignment_allocation (
    allocation_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_item_id    BIGINT NOT NULL,
    consignment_id  BIGINT NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL CHECK (quantity > 0),
    CONSTRAINT fk_alloc_item        FOREIGN KEY (sale_item_id)   REFERENCES sale_item(sale_item_id),
    CONSTRAINT fk_alloc_consignment FOREIGN KEY (consignment_id) REFERENCES consignment(consignment_id)
);

-- Receipts (+), sales (-) and authorised adjustments (-)
CREATE TABLE IF NOT EXISTS stock_movement (
    movement_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    consignment_id   BIGINT NOT NULL,
    movement_type    VARCHAR(20) NOT NULL CHECK (movement_type IN ('RECEIPT', 'SALE', 'ADJUSTMENT')),
    adjustment_type  VARCHAR(20) CHECK (adjustment_type IN ('DAMAGED', 'UNSOLD', 'UNSUITABLE', 'OTHER')),
    quantity         DECIMAL(12,2) NOT NULL,
    balance_after    DECIMAL(12,2) NOT NULL,
    reason           VARCHAR(255),
    reference        VARCHAR(50),
    movement_date    DATETIME(6) NOT NULL,
    user_id          BIGINT,
    CONSTRAINT fk_movement_consignment FOREIGN KEY (consignment_id) REFERENCES consignment(consignment_id),
    CONSTRAINT fk_movement_user        FOREIGN KEY (user_id)        REFERENCES app_user(user_id),
    INDEX idx_movement_consignment (consignment_id)
);

CREATE TABLE IF NOT EXISTS commission_rate (
    rate_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    commission_rate  DECIMAL(5,2) NOT NULL CHECK (commission_rate BETWEEN 0 AND 100),
    effective_from   DATETIME(6) NOT NULL,
    active           BIT NOT NULL,
    set_by           BIGINT,
    CONSTRAINT fk_rate_user FOREIGN KEY (set_by) REFERENCES app_user(user_id)
);

CREATE TABLE IF NOT EXISTS commission (
    commission_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id            BIGINT NOT NULL UNIQUE,
    agent_id           BIGINT NOT NULL,
    commission_rate    DECIMAL(5,2)  NOT NULL,
    commission_amount  DECIMAL(12,2) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'EARNED',
    CONSTRAINT fk_commission_sale  FOREIGN KEY (sale_id)  REFERENCES sale(sale_id),
    CONSTRAINT fk_commission_agent FOREIGN KEY (agent_id) REFERENCES app_user(user_id)
);

CREATE TABLE IF NOT EXISTS sales_forecast (
    forecast_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    forecast_period     INT  NOT NULL CHECK (forecast_period > 0),
    predicted_quantity  DECIMAL(12,2) NOT NULL,
    model_type          VARCHAR(50),
    model_mae           DECIMAL(12,2),
    baseline_mae        DECIMAL(12,2),
    accuracy_percent    DECIMAL(5,2),
    daily_predictions   VARCHAR(8000),
    requested_by        BIGINT,
    created_date        DATETIME(6) NOT NULL,
    CONSTRAINT fk_forecast_product FOREIGN KEY (product_id)   REFERENCES product(product_id),
    CONSTRAINT fk_forecast_user    FOREIGN KEY (requested_by) REFERENCES app_user(user_id)
);
