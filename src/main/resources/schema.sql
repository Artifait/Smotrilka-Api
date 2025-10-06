PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        login TEXT NOT NULL UNIQUE,
        password TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS links (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        link TEXT NOT NULL,
        rating INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS link_tags (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        type_name TEXT NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS link_tag_relations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        link_id INTEGER NOT NULL,
        type_id INTEGER NOT NULL,
        FOREIGN KEY (link_id) REFERENCES links(id) ON DELETE CASCADE,
    FOREIGN KEY (type_id) REFERENCES link_types(id) ON DELETE CASCADE,
    CONSTRAINT ux_link_type UNIQUE (link_id, type_id)
    );

CREATE TABLE IF NOT EXISTS reactions (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     link_id INTEGER NOT NULL,
     user_id INTEGER NOT NULL,
     reaction INTEGER NOT NULL,
     CONSTRAINT ux_user_link UNIQUE(user_id, link_id),
    FOREIGN KEY(link_id) REFERENCES links(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE favorite_links (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    link_id INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (link_id) REFERENCES links(id) ON DELETE CASCADE,
    UNIQUE (user_id, link_id)
);
