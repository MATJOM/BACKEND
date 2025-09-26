package com.matjom.matjom.place.repository;

import com.matjom.matjom.place.dto.PlaceSearchCursor;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceRepository {

    private static final String SEARCH_SQL = """
            WITH user_point AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
            ), ranked AS (
                SELECT p.place_id,
                       p.name,
                       ST_Distance(p.location, up.point, true) AS distance_m
                FROM places p
                CROSS JOIN user_point up
                WHERE ST_DWithin(p.location, up.point, :radius, true)
            )
            SELECT place_id,
                   name,
                   distance_m
            FROM ranked
            WHERE (:cursorDistance IS NULL
                OR distance_m > :cursorDistance
                OR (distance_m = :cursorDistance AND place_id > :cursorLastId))
            ORDER BY distance_m ASC, place_id ASC
            LIMIT :limit
            """;

    private static final String ROULETTE_SQL = """
            WITH user_point AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
            )
            SELECT p.place_id,
                   p.name,
                   p.category,
                   ST_Distance(p.location, up.point, true) AS distance_m
            FROM places p
            CROSS JOIN user_point up
            WHERE ST_DWithin(p.location, up.point, :radius, true)
              AND (:categories IS NULL OR p.category && :categories)
            ORDER BY p.place_id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlaceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlaceSummary> search(double lat,
                                     double lng,
                                     double radiusMeters,
                                     int limit,
                                     PlaceSearchCursor cursor,
                                     String filters) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("radius", radiusMeters)
                .addValue("limit", limit)
                .addValue("cursorDistance", cursor == null ? null : cursor.distanceMeters())
                .addValue("cursorLastId", cursor == null ? null : cursor.lastPlaceId());

        // TODO: filters 적용 로직은 후속 태스크에서 구현

        return jdbcTemplate.query(SEARCH_SQL, params, (rs, rowNum) ->
                new PlaceSummary(
                        rs.getLong("place_id"),
                        rs.getString("name"),
                        rs.getDouble("distance_m"))
        );
    }

    public List<RouletteCandidate> findRouletteCandidates(double lat,
                                                          double lng,
                                                          double radiusMeters,
                                                          List<String> categories,
                                                          int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("radius", radiusMeters)
                .addValue("limit", limit)
                .addValue("categories", categories == null || categories.isEmpty() ? null : categories.toArray(new String[0]));

        return jdbcTemplate.query(ROULETTE_SQL, params, (rs, rowNum) -> {
            try {
                Array categoryArray = rs.getArray("category");
                List<String> categoryList = categoryArray == null
                        ? List.of()
                        : Arrays.asList((String[]) categoryArray.getArray());
                return new RouletteCandidate(
                        rs.getLong("place_id"),
                        rs.getString("name"),
                        rs.getDouble("distance_m"),
                        List.copyOf(categoryList)
                );
            } catch (SQLException e) {
                throw new IllegalStateException("카테고리 데이터를 읽는 중 오류가 발생했습니다.", e);
            }
        });
    }
}
