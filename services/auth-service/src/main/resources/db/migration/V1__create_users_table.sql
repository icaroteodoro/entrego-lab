CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email)
);


CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,

    CONSTRAINT uk_roles_name UNIQUE (name)
);


CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
            REFERENCES roles(id)
);


CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    token_hash VARCHAR(255) NOT NULL,

    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
);

INSERT INTO roles (id, name)
VALUES
    (gen_random_uuid(), 'CUSTOMER'),
    (gen_random_uuid(), 'STORE_OWNER'),
    (gen_random_uuid(), 'ADMIN');