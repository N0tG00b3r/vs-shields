-- create "ships" table with id, name (optional), aabb - not null ints minX, maxX, .., maxZ
-- sqlite format

create table ships(
    id integer primary key,
    body_id integer not null,
    name text,
    claim_x integer not null,
    claim_z integer not null,
    claim_dimension text not null,
    attachments_json text not null
);

create unique index ship_body_index on ships(body_id);
create unique index ship_claim_index on ships(claim_x, claim_z);

create table bodies(
    id integer primary key,
    dimension text not null,
    transform_velocity blob not null,
    settings_json text not null
);

create virtual table body_aabbs using rtree(
    id,
    min_x, max_x,
    min_y, max_y,
    min_z, max_z
);

create table constraints(
    id integer primary key,
    body1 integer not null,
    body2 integer not null,
    constraint_json text not null,
    foreign key(body1) references bodies,
    foreign key(body2) references bodies
);

create index constraint_body1_index on constraints(body1);
create index constraint_body2_index on constraints(body2);
