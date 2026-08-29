ALTER TABLE properties
ADD COLUMN house_number VARCHAR(100);

ALTER TABLE properties
ADD COLUMN block_name VARCHAR(100);

ALTER TABLE bookings
DROP COLUMN house_number;

ALTER TABLE bookings
DROP COLUMN block_name;
