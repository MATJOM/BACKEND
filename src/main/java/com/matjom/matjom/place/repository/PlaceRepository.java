package com.matjom.matjom.place.repository;

import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceRepository {

    private static final String SEARCH_SQL = """
            WITH user_point AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
            )
            SELECT p.place_id,
                   p.name,
                   ST_Distance(p.location, up.point, true) AS distance_m
            FROM places p
            CROSS JOIN user_point up
            WHERE ST_DWithin(p.location, up.point, :radius, true)
            ORDER BY distance_m ASC, p.place_id ASC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlaceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlaceSummary> search(double lat, double lng, double radiusMeters, int limit, String filters) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("radius", radiusMeters)
                .addValue("limit", limit);

        // TODO: filters 적용 로직은 후속 태스크에서 구현

        return jdbcTemplate.query(SEARCH_SQL, params, (rs, rowNum) ->
                new PlaceSummary(
                        rs.getLong("place_id"),
                        rs.getString("name"),
                        rs.getDouble("distance_m"))
        );
    }
}
