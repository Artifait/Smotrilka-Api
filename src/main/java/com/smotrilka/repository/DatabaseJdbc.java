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

    public boolean addLink(LinkRequest request) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM users WHERE login = ? AND password = ?",
                    Integer.class, request.getLogin(), request.getPassword()
            );
            if (cnt == null || cnt == 0) {
                return false;
            }

            jdbc.update("INSERT INTO links(name, type, link, rating) VALUES(?, ?, ?, ?)",
                    request.getName(), request.getType(), request.getLink(), 0);

            log.info("Link '{}' added by {}", request.getName(), request.getLogin());
            return true;
        } catch (Exception e) {
            log.error("addLink failed for {}", request == null ? "null" : request.getLogin(), e);
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