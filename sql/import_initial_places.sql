INSERT INTO places (
    name, lat, lng, category, provider_id,
    phone_number, working_hours, break_time, opened_at,
    opened_at_source, biz_status,
    addr_sido, addr_sigungu, addr_eupmyeondong, addr_street, addr_detail,
    location
)
SELECT
    s.name,
    ST_Y(geom_wgs84),
    ST_X(geom_wgs84),
    ARRAY[trim(s.category)]::text[],
    s.provider_id,
    s.phone_number,
    COALESCE(s.working_hours, '{}'::jsonb),
    COALESCE(s.break_time, '[]'::jsonb),
    s.opened_at,
    jsonb_build_object('source', 'initial_csv', 'updated_at', s.updated_at),
    s.biz_status,
    s.addr_sido,
    s.addr_sigungu,
    s.addr_dong,
    COALESCE(concat_ws(' ', s.addr_road, s.addr_bnum), ''),
    COALESCE(s.addr_detail, ''),
    geom_wgs84
FROM (
    SELECT
        sp.*,
        ST_Transform(
            ST_SetSRID(ST_MakePoint(sp.lat, sp.lng), 5174),
            4326
        ) AS geom_wgs84
    FROM staging_initial_places sp
) s
ON CONFLICT (provider_id)
DO UPDATE SET
    name = EXCLUDED.name,
    lat = EXCLUDED.lat,
    lng = EXCLUDED.lng,
    category = EXCLUDED.category,
    phone_number = EXCLUDED.phone_number,
    working_hours = EXCLUDED.working_hours,
    break_time = EXCLUDED.break_time,
    opened_at = EXCLUDED.opened_at,
    opened_at_source = EXCLUDED.opened_at_source,
    biz_status = EXCLUDED.biz_status,
    addr_sido = EXCLUDED.addr_sido,
    addr_sigungu = EXCLUDED.addr_sigungu,
    addr_eupmyeondong = EXCLUDED.addr_eupmyeondong,
    addr_street = EXCLUDED.addr_street,
    addr_detail = EXCLUDED.addr_detail,
    location = EXCLUDED.location,
    updated_at = now();
