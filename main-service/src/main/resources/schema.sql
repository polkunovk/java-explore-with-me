CREATE TABLE IF NOT EXISTS users(
  user_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  name VARCHAR(250) NOT NULL
);

CREATE TABLE IF NOT EXISTS categories(
  category_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS events(
    event_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    annotation varchar(2000) NOT NULL,
    category_id BIGINT NOT NULL references categories(category_id),
    confirmed_requests int,
    created_on timestamp NOT NULL,
    published_on timestamp,
    description varchar(7000) NOT NULL,
    event_date timestamp NOT NULL,
    initiator_id BIGINT NOT NULL references users(user_id),
    paid BOOLEAN NOT NULL,
    participant_limit int,
    state varchar(50) NOT NULL,
    request_moderation BOOLEAN,
    title varchar(120) NOT NULL,
    lat FLOAT NOT NULL,
    lon FLOAT NOT NULL
);

CREATE TABLE IF NOT EXISTS participation_requests(
    request_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL references events(event_id),
    requester_id BIGINT NOT NULL references users(user_id),
    created TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT UQ_PARTICIPANT_PER_EVENT UNIQUE (requester_id, event_id)
);

CREATE TABLE IF NOT EXISTS compilations(
   compilation_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
   pinned BOOLEAN NOT NULL,
   title VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS compilations_events(
    compilation_id BIGINT REFERENCES compilations(compilation_id) ON DELETE CASCADE,
    event_id BIGINT REFERENCES events(event_id) ON DELETE CASCADE,
    PRIMARY KEY (compilation_id, event_id)
);