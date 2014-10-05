--CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE groups (
       id serial primary key,
       name text not null
);

CREATE TABLE counters (
       id uuid primary key DEFAULT uuid_generate_v4(),
       groupid int references groups(id) not null,
       data json,
       last_updated timestamp with time zone default current_timestamp
);
