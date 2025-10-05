package com.smotrilka.repository;


import com.smotrilka.DTOs.LinkRequest;
import com.smotrilka.DTOs.RegisterRequest;
import com.smotrilka.DTOs.ReactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public boolean addLink(LinkRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request is null");
            }

            // 1. Проверка авторизации пользователя
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

            // 2. Проверка и ограничение количества тегов
            List<String> tags = request.getTags();
            if (tags == null || tags.isEmpty()) {
                throw new IllegalArgumentException("At least one tag required");
            }
            if (tags.size() > 10) {
                throw new IllegalArgumentException("Too many tags (max 10)");
            }

            // 3. Добавляем новую ссылку
            jdbc.update("INSERT INTO links(name, link, rating) VALUES(?, ?, 0)",
                    request.getName(), request.getLink());

            // Получаем ID добавленной ссылки
            Integer linkId = jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);

            // 4. Добавляем / находим теги и создаём связи
            for (String rawTag : tags) {
                String tag = rawTag == null ? "" : rawTag.trim();
                if (tag.isEmpty()) continue;

                // Проверяем, есть ли уже такой тег
                List<Integer> tagIds = jdbc.query(
                        "SELECT id FROM link_tags WHERE type_name = ?",
                        (rs, rowNum) -> rs.getInt("id"),
                        tag
                );

                Integer tagId;
                if (tagIds.isEmpty()) {
                    // создаём новый тег
                    jdbc.update("INSERT INTO link_tags(type_name) VALUES(?)", tag);
                    tagId = jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
                } else {
                    tagId = tagIds.get(0);
                }

                // создаём связь ссылка ↔ тег, если её ещё нет
                jdbc.update("INSERT OR IGNORE INTO link_tag_relations(link_id, type_id) VALUES(?, ?)",
                        linkId, tagId);
            }

            log.info("Link '{}' added by {} with {} tag(s)",
                    request.getName(), request.getLogin(), tags.size());
            return true;

        } catch (Exception e) {
            log.error("addLink failed for {}", request == null ? "null" : request.getName(), e);
            throw e; // транзакция откатится автоматически
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