package com.smotrilka.repository;

import com.smotrilka.DTOs.StickerRequest;
import com.smotrilka.DTOs.LinkRequest;
import com.smotrilka.DTOs.RegisterRequest;
import com.smotrilka.DTOs.ReactionRequest;
import com.smotrilka.DTOs.SearchResponse;
import com.smotrilka.DTOs.CommentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.util.List;

@Repository
public class DatabaseJdbc {
    private final JdbcTemplate jdbc;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public DatabaseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean registerUser(RegisterRequest request) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM users WHERE login = ?",
                    Integer.class, request.getLogin()
            );
            if (cnt != null && cnt > 0) {
                return false;
            }

            jdbc.update("INSERT INTO users(login, password) VALUES(?, ?)",
                    request.getLogin(), request.getPassword());

            log.info("User created: {}", request.getLogin());
            return true;
        } catch (Exception e) {
            log.error("registerUser failed for {}", request == null ? "null" : request.getLogin(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public boolean login(String login, String password) {
        try {
            if (login == null || password == null ||
                    login.trim().isEmpty() || password.trim().isEmpty()) {
                log.warn("login() called with empty credentials");
                return false;
            }

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM users WHERE login = ? AND password = ?",
                    Integer.class,
                    login, password
            );

            boolean exists = count != null && count > 0;
            log.info("Login attempt for '{}': {}", login, exists ? "success" : "failed");
            return exists;

        } catch (Exception e) {
            log.error("login() failed for user {}", login, e);
            throw e;
        }
    }


    @Transactional(readOnly = true)
    public List<SearchResponse> searchLinks(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            String sqlAll = """
            SELECT l.id, l.name, l.link, l.rating
            FROM links l
        """;
            return jdbc.query(sqlAll, (rs, rowNum) -> {
                int linkId = rs.getInt("id");
                List<String> tags = jdbc.query(
                        "SELECT t.type_name FROM link_tags t " +
                                "JOIN link_tag_relations r ON r.type_id = t.id " +
                                "WHERE r.link_id = ?",
                        (r2, i2) -> r2.getString("type_name"), linkId
                );
                return new SearchResponse(
                        linkId,
                        rs.getString("name"),
                        rs.getString("link"),
                        rs.getInt("rating"),
                        tags
                );
            });
        }

        String like = "%" + q + "%";

        String sql = """
        SELECT DISTINCT l.id, l.name, l.link, l.rating
        FROM links l
        LEFT JOIN link_tag_relations r ON r.link_id = l.id
        LEFT JOIN link_tags t ON t.id = r.type_id
        WHERE LOWER(l.name) LIKE ? OR LOWER(t.type_name) LIKE ?
        """;

        List<SearchResponse> results = jdbc.query(sql, (rs, rowNum) -> {
            int linkId = rs.getInt("id");
            List<String> tags = jdbc.query(
                    "SELECT t.type_name FROM link_tags t " +
                            "JOIN link_tag_relations r ON r.type_id = t.id " +
                            "WHERE r.link_id = ?",
                    (r2, i2) -> r2.getString("type_name"), linkId
            );
            return new SearchResponse(
                    linkId,
                    rs.getString("name"),
                    rs.getString("link"),
                    rs.getInt("rating"),
                    tags
            );
        }, like, like);

        return results;
    }

    @Transactional
    public boolean addComment(CommentRequest request) {
        try {
            if (request == null ||
                    request.getLogin() == null ||
                    request.getPassword() == null ||
                    request.getLinkId() == null ||
                    request.getText() == null ||
                    request.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("All fields required");
            }

            List<Integer> ulist = jdbc.query(
                    "SELECT id FROM users WHERE login = ? AND password = ?",
                    (rs, rowNum) -> rs.getInt("id"),
                    request.getLogin(), request.getPassword()
            );

            if (ulist.isEmpty()) {
                log.warn("Unauthorized comment attempt by {}", request.getLogin());
                return false;
            }

            Integer userId = ulist.get(0);

            Integer linkCount = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM links WHERE id = ?",
                    Integer.class, request.getLinkId()
            );
            if (linkCount == null || linkCount == 0) {
                log.warn("Link not found for comment, id={}", request.getLinkId());
                return false;
            }

            jdbc.update("""
            INSERT INTO comments (link_id, user_id, text)
            VALUES (?, ?, ?)
        """, request.getLinkId(), userId, request.getText().trim());

            log.info("Comment added by {} to link {}", request.getLogin(), request.getLinkId());
            return true;

        } catch (Exception e) {
            log.error("addComment failed for user {}",
                    request == null ? "null" : request.getLogin(), e);
            throw e;
        }
    }


    @Transactional
    public boolean addFavorite(String login, String password, int linkId) {
        try {
            Integer userId = jdbc.queryForObject(
                    "SELECT id FROM users WHERE login = ? AND password = ?",
                    Integer.class,
                    login, password
            );

            if (userId == null) {
                return false;
            }

            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM favorite_links WHERE user_id = ? AND link_id = ?",
                    Integer.class,
                    userId, linkId
            );

            if (exists != null && exists > 0) {
                return false;
            }

            jdbc.update("INSERT INTO favorite_links (user_id, link_id) VALUES (?, ?)", userId, linkId);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    @Transactional
    public boolean addSticker(StickerRequest request) {
        try {
            if (request == null ||
                    request.getLogin() == null ||
                    request.getPassword() == null ||
                    request.getLinkId() == null ||
                    request.getKey() == null || request.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("All fields required except value");
            }

            // Проверка пользователя
            List<Integer> ulist = jdbc.query(
                    "SELECT id FROM users WHERE login = ? AND password = ?",
                    (rs, rowNum) -> rs.getInt("id"),
                    request.getLogin(), request.getPassword()
            );

            if (ulist.isEmpty()) {
                log.warn("Unauthorized sticker attempt by {}", request.getLogin());
                return false;
            }

            // Проверка ссылки
            Integer linkCount = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM links WHERE id = ?",
                    Integer.class, request.getLinkId()
            );
            if (linkCount == null || linkCount == 0) {
                log.warn("Link not found for sticker, id={}", request.getLinkId());
                return false;
            }

            // Проверяем, есть ли уже такой стикер
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM link_metadata WHERE link_id = ? AND key = ?",
                    Integer.class, request.getLinkId(), request.getKey().trim()
            );

            if (count != null && count > 0) {
                // Обновляем значение
                jdbc.update("UPDATE link_metadata SET value = ? WHERE link_id = ? AND key = ?",
                        request.getValue(), request.getLinkId(), request.getKey().trim());
                log.info("Sticker '{}' updated for link {}", request.getKey(), request.getLinkId());
            } else {
                // Добавляем новый
                jdbc.update("INSERT INTO link_metadata (link_id, key, value) VALUES (?, ?, ?)",
                        request.getLinkId(), request.getKey().trim(), request.getValue());
                log.info("Sticker '{}' added for link {}", request.getKey(), request.getLinkId());
            }

            return true;

        } catch (Exception e) {
            log.error("addSticker failed for user {}", request == null ? "null" : request.getLogin(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getStickers(int linkId) {
        try {
            return jdbc.query(
                    "SELECT key, value FROM link_metadata WHERE link_id = ?",
                    (rs, rowNum) -> Map.of(
                            "key", rs.getString("key"),
                            "value", rs.getString("value")
                    ),
                    linkId
            );
        } catch (Exception e) {
            log.error("getStickers failed for link {}", linkId, e);
            throw e;
        }
    }

    @Transactional
    public boolean removeFavorite(String login, String password, int linkId) {
        try {
            Integer userId = jdbc.queryForObject(
                    "SELECT id FROM users WHERE login = ? AND password = ?",
                    Integer.class,
                    login, password
            );

            if (userId == null) {
                return false;
            }

            int rows = jdbc.update(
                    "DELETE FROM favorite_links WHERE user_id = ? AND link_id = ?",
                    userId, linkId
            );

            return rows > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }



    @Transactional(readOnly = true)
    public List<SearchResponse> getFavorites(int userId) {
        String sql = """
        SELECT l.id, l.name, l.link, l.rating
        FROM links l
        JOIN favorite_links f ON f.link_id = l.id
        WHERE f.user_id = ?
        """;

        return jdbc.query(sql, (rs, rowNum) -> {
            int linkId = rs.getInt("id");
            List<String> tags = jdbc.query(
                    "SELECT t.type_name FROM link_tags t " +
                            "JOIN link_tag_relations r ON r.type_id = t.id " +
                            "WHERE r.link_id = ?",
                    (r2, i2) -> r2.getString("type_name"), linkId
            );
            return new SearchResponse(
                    linkId,
                    rs.getString("name"),
                    rs.getString("link"),
                    rs.getInt("rating"),
                    tags
            );
        }, userId);
    }

    @Transactional
    public boolean addLink(LinkRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request is null");
            }

            List<Integer> ulist = jdbc.query(
                    "SELECT id FROM users WHERE login = ? AND password = ?",
                    (rs, rowNum) -> rs.getInt("id"),
                    request.getLogin(), request.getPassword()
            );
            if (ulist.isEmpty()) {
                log.warn("Invalid credentials for user {}", request.getLogin());
                return false;
            }
            Integer userId = ulist.get(0);

            List<String> tags = request.getTags();
            if (tags == null || tags.isEmpty()) {
                throw new IllegalArgumentException("At least one tag required");
            }
            if (tags.size() > 10) {
                throw new IllegalArgumentException("Too many tags (max 10)");
            }

            jdbc.update(
                    "INSERT INTO links(name, link, description, rating) VALUES(?, ?, ?, 0)",
                    request.getName(), request.getLink(), request.getDescription()
            );

            Integer linkId = jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);

            for (String rawTag : tags) {
                String tag = rawTag == null ? "" : rawTag.trim();
                if (tag.isEmpty()) continue;

                List<Integer> tagIds = jdbc.query(
                        "SELECT id FROM link_tags WHERE type_name = ?",
                        (rs, rowNum) -> rs.getInt("id"),
                        tag
                );

                Integer tagId;
                if (tagIds.isEmpty()) {
                    jdbc.update("INSERT INTO link_tags(type_name) VALUES(?)", tag);
                    tagId = jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
                } else {
                    tagId = tagIds.get(0);
                }

                jdbc.update("INSERT OR IGNORE INTO link_tag_relations(link_id, type_id) VALUES(?, ?)",
                        linkId, tagId);
            }

            log.info("Link '{}' added by {} with {} tag(s)", request.getName(), request.getLogin(), tags.size());
            return true;

        } catch (Exception e) {
            log.error("addLink failed for {}", request == null ? "null" : request.getName(), e);
            throw e;
        }
    }

    public boolean isUsernameTaken(String login) {
        try {
            if (login == null || login.trim().isEmpty()) {
                log.warn("isUsernameTaken called with empty login");
                return true; // считаем пустое имя "занятым", чтобы не пропустить ошибку
            }

            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM users WHERE login = ?",
                    Integer.class, login
            );

            boolean taken = cnt != null && cnt > 0;
            log.info("Checked username '{}': {}", login, taken ? "taken" : "available");
            return taken;

        } catch (Exception e) {
            log.error("isUsernameTaken failed for {}", login, e);
            throw e;
        }
    }

    /**
     * Обработка реакции пользователя на ссылку.
     * Вернёт описание действия: "no-change", "added", "removed", "changed".
     *
     * Алгоритм:
     *  - проверить авторизацию (login+password) -> получить user_id
     *  - проверить существование link
     *  - прочитать существующую реакцию (если есть)
     *  - в зависимости от нового значения (1, -1 или 0) выполнить insert/update/delete и скорректировать links.rating
     *
     * Всё в одной транзакции.
     */
    @Transactional
    public String processReaction(ReactionRequest request) {
        if (request == null) throw new IllegalArgumentException("request is null");
        Integer reaction = request.getReaction();
        if (reaction == null || !(reaction == -1 || reaction == 0 || reaction == 1)) {
            throw new IllegalArgumentException("reaction must be -1, 0 or 1");
        }

        // 1) авторизация -> user id
        List<Integer> ulist = jdbc.query(
                "SELECT id FROM users WHERE login = ? AND password = ?",
                (rs, rowNum) -> rs.getInt("id"),
                request.getLogin(), request.getPassword()
        );
        if (ulist.isEmpty()) {
            // неавторизован
            return "unauthorized";
        }
        Integer userId = ulist.get(0);

        // 2) проверка ссылки
        Integer linkCount = jdbc.queryForObject(
                "SELECT COUNT(1) FROM links WHERE id = ?",
                Integer.class, request.getLinkId()
        );
        if (linkCount == null || linkCount == 0) {
            return "link-not-found";
        }

        // 3) существующая реакция (если есть)
        List<Integer> existingList = jdbc.query(
                "SELECT reaction FROM reactions WHERE link_id = ? AND user_id = ?",
                (rs, rowNum) -> rs.getInt("reaction"),
                request.getLinkId(), userId
        );
        Integer existing = existingList.isEmpty() ? null : existingList.get(0);

        // 4) логика
        if (existing == null) {
            if (reaction == 0) {
                // ничего нет и снимаем 0 -> no-op
                return "no-change";
            } else {
                // вставляем новую реакцию и прибавляем rating на reaction
                jdbc.update("INSERT INTO reactions(link_id, user_id, reaction) VALUES(?, ?, ?)",
                        request.getLinkId(), userId, reaction);
                jdbc.update("UPDATE links SET rating = rating + ? WHERE id = ?",
                        reaction, request.getLinkId());
                return "added";
            }
        } else {
            // уже была реакция
            if (existing.equals(reaction)) {
                return "no-change";
            } else if (reaction == 0) {
                // удаляем реакцию: рейтинг уменьшаем на existing
                jdbc.update("DELETE FROM reactions WHERE link_id = ? AND user_id = ?",
                        request.getLinkId(), userId);
                jdbc.update("UPDATE links SET rating = rating - ? WHERE id = ?",
                        existing, request.getLinkId());
                return "removed";
            } else {
                // меняем реакцию: delta = new - old
                int delta = reaction - existing;
                jdbc.update("UPDATE reactions SET reaction = ? WHERE link_id = ? AND user_id = ?",
                        reaction, request.getLinkId(), userId);
                jdbc.update("UPDATE links SET rating = rating + ? WHERE id = ?",
                        delta, request.getLinkId());
                return "changed";
            }
        }
    }
}