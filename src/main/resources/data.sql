-- Seed data — runs after Hibernate creates the tables via @Entity.
-- Table is always empty at startup (create-drop), so plain INSERT is safe.
INSERT INTO products (name, category, price, quantity) VALUES
('Laptop Pro 15',           'Electronics', 1299.99, 45),
('Wireless Mouse',          'Electronics',   29.99,  5),
('Office Chair',            'Furniture',    349.00, 12),
('Desk Lamp',               'Furniture',     49.99,  3),
('Java 21 Book',            'Books',         59.99, 80),
('USB-C Hub',               'Electronics',   39.99,  2),
('Standing Desk',           'Furniture',    599.00,  8),
('Noise-Cancel Headphones', 'Electronics',  249.99,  4);
