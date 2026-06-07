-- Artist portraits. Spotify's batch GET /artists?ids= is 403-restricted (Feb 2026
-- API changes), so images are fetched one-by-one via GET /artists/{id} the first
-- time an artist appears in a ranking, then cached here permanently.
alter table artist add column image_url varchar(1024);
