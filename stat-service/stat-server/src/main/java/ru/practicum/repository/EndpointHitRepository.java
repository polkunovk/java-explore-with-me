package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.dto.ViewStats;
import ru.practicum.model.EndpointHit;

import java.time.LocalDateTime;
import java.util.List;

public interface EndpointHitRepository extends JpaRepository<EndpointHit, Long> {

    /**
     * Возвращает статистику по уникальным IP-адресам для указанных URI (или всех URI, если список не задан).
     * Статистика группируется по приложению и URI, сортируется по количеству обращений в порядке убывания.
     *
     * @param start Начало временного интервала (включительно).
     * @param end   Конец временного интервала (включительно).
     * @param uris  Список URI для фильтрации (может быть null — тогда выбираются все URI).
     * @return Список {@link ViewStats} (app, uri, count), где count — количество уникальных IP.
     */
    @Query("""
        SELECT new ru.practicum.dto.ViewStats(e.app, e.uri, COUNT(DISTINCT e.ip))
        FROM EndpointHit e
        WHERE e.timestamp BETWEEN :start AND :end
          AND (:uris IS NULL OR e.uri IN :uris)
        GROUP BY e.app, e.uri
        ORDER BY COUNT(DISTINCT e.ip) DESC
        """)
    List<ViewStats> findUniqueIpViewStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris);

    /**
     * Возвращает полную статистику обращений (включая повторные с одного IP) для указанных URI
     * (или всех URI, если список не задан). Группируется по приложению и URI, сортируется по
     * количеству обращений в порядке убывания.
     *
     * @param start Начало временного интервала (включительно).
     * @param end   Конец временного интервала (включительно).
     * @param uris  Список URI для фильтрации (может быть null — тогда выбираются все URI).
     * @return Список {@link ViewStats} (app, uri, count), где count — общее количество обращений.
     */
    @Query("""
        SELECT new ru.practicum.dto.ViewStats(e.app, e.uri, COUNT(e.ip))
        FROM EndpointHit e
        WHERE e.timestamp BETWEEN :start AND :end
          AND (:uris IS NULL OR e.uri IN :uris)
        GROUP BY e.app, e.uri
        ORDER BY COUNT(e.ip) DESC
        """)
    List<ViewStats> findAllViewStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris);
}