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
     * Получает статистику для указанных URI в заданном временном диапазоне с возможностью учёта уникальных IP адресов
     *
     * @param start  Начало временного диапазона (включительно)
     * @param end    Конец временного диапазона (включительно)
     * @param uris   Список URI для фильтрации (null - все URI)
     * @param unique Учитывать только уникальные IP адреса, если true
     * @return Список объектов ViewStats, отсортированный по убыванию количества обращений
     */
    @Query("""
        SELECT new ru.practicum.dto.ViewStats(
            e.app,
            e.uri,
            CASE WHEN :unique THEN COUNT(DISTINCT e.ip) ELSE COUNT(e) END
        )
        FROM EndpointHit e
        WHERE e.timestamp BETWEEN :start AND :end
            AND (:uris IS NULL OR e.uri IN :uris)
        GROUP BY e.app, e.uri
        ORDER BY CASE WHEN :unique THEN COUNT(DISTINCT e.ip) ELSE COUNT(e) END DESC
    """)
    List<ViewStats> findStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris,
            @Param("unique") boolean unique
    );

    /**
     * Получает статистику для всех URI в заданном временном диапазоне
     *
     * @param start  Начало временного диапазона (включительно)
     * @param end    Конец временного диапазона (включительно)
     * @param unique Учитывать только уникальные IP адреса, если true
     * @return Список объектов ViewStats, отсортированный по убыванию количества обращений
     */
    default List<ViewStats> findStatsForAllUris(LocalDateTime start, LocalDateTime end, boolean unique) {
        return findStats(start, end, null, unique);
    }
}