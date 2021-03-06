# Vertx Martiply API

```
java -jar <path to fat jar> <path to log>
```

## Requirements
- Martiply Google SQL access to server's IP

### Live
- Both live and systemd daemon require fat jar built from `sbt-assembly`
- Vertx config reads from create environment variables. Create a conf folder, export this as env variable `VERTX_CONFIG_PATH` (in Linux add export line to `~/.bashrc)
- Paste config.json into it (see config-template.json)
- Create a log folder
- Run `java -jar <appjar>.jar <log dir>`

### Systemd
- Systemd process uses its own environment variables. Check cheatsheet how to do it

### Docker
- Create an SSH key and paste id_rsa.pub at the repo's Access Keys
    - `ssh-keygen -t rsa -C "your_email@example.com"`
- Create a folder. Put `Dockerfile` in this project, `config.json`, and `id_rsa` (from `~/.ssh/id_rsa`) in it
- build the image
    - `docker build -t martiply:customtag .` customtag = version or commit hash
- run the container
    - `docker run -d -p <server's listening port>:<app's listening port inside> -t theimage`

## Notes
- ResultSet of this lib doesn't support get by types or get("xxx.yyy") form
- Do not select Spatial column directly. Wrap it with `astext()`
- Too many string interpolations cause stackoverflow error https://github.com/scala/bug/issues/11214 so sql is using static string
-- both static and string interpolation classes are put in Repository.xxx.bak files
- Check https://github.com/vert-x3/vertx-scala.g8/blob/master/src/main/g8/project/Build.scala for merging strategy
- ~Add `excludeDependencies += "io.netty" % "netty-all"` this thing is causing deduplicate (conflicts)[https://github.com/netty/netty/issues/4671]~ fixed in 0.8.40

## TODO
- Add categoryCode column to match category. Do not use LIKE "%culinary%", it will do full table scan
- ~wait until mysql lib supports returning specific data types especially for GEO~
- order serialized json fields

## Scale Up
- ApparelExtension is activated if we have apparel stores


### Sample Queries
- search(kwd: String, lat: Double, lng: Double, legitSaleTs: Long, category: Option[Category])

```

SELECT * FROM (SELECT store.storeId, store.name, astext(store.geo) as geo, store.currency,
store.email, store.zip, store.address, store.city, store.phone, store.open, store.close,
store.story, store.tz,

standard_id, standard.ownerId, standard.idType, standard.gtin, standard.idCustom, standard.brand,
standard_name, standard.cond, standard.category, standard.price, standard.description, standard.url,
standard.hit, sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd, pathi, paths,
score,

ST_Distance_Sphere(point(107.615815, -6.906917), geo) as distance_in_meters FROM store AS store

JOIN inventory ON inventory.storeId = store.storeId
JOIN (SELECT standard.id AS standard_id, standard.ownerId, standard.idType, standard.gtin,
  standard.idCustom, standard.brand, standard.name AS standard_name, standard.cond, standard.category,
  standard.price, standard.description, standard.url, standard.hit,
  MATCH (standard.name, standard.category, standard.brand) AGAINST('chicken') as score FROM standard
  WHERE MATCH (standard.name, standard.category, standard.brand) AGAINST('chicken'))  AS standard ON inventory.id = standard_id

LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd
  FROM standard_sale WHERE standard_sale.saleStart < 1544013445 AND standard_sale.saleEnd > 1544013445 ) AS standard_sale
  ON standard_id = sale_id
LEFT JOIN (SELECT img_standard.id, GROUP_CONCAT(img_standard.path ORDER BY ts ASC) pathi FROM img_standard GROUP BY img_standard.id) as img_standard ON img_standard.id = standard_id
LEFT JOIN (SELECT img_store.storeId, GROUP_CONCAT( img_store.path ORDER BY ts ASC) paths FROM img_store GROUP BY img_store.storeId) as img_store ON img_store.storeId = store.storeId

WHERE ST_Within(geo, ST_Buffer(POINT(107.615815, -6.906917), 0.020)) ORDER BY distance_in_meters, score LIMIT 100) AS d
```


- searchStoreKeyword(kwd: String, storeId: Int, legitSaleTs: Long)

```
SELECT standard.*, standard_sale.*, store.currency, CONCAT(pathi) AS pathi FROM inventory
JOIN store ON inventory.storeId = store.storeId
JOIN standard ON inventory.id = standard.id
LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd FROM standard_sale WHERE standard_sale.saleStart < 1540229836 AND standard_sale.saleEnd > 1540229836) AS standard_sale ON standard.id = sale_id
LEFT JOIN (SELECT id, GROUP_CONCAT(img_standard.path ORDER BY ts ASC) pathi FROM img_standard GROUP BY id) AS img_standard ON inventory.id = img_standard.id
WHERE inventory.storeId = 10009
AND MATCH (standard.name, standard.category, standard.brand) AGAINST ('fish')

```

- searchStoreRandom(storeId: Int, legitSaleTs: Long)
```
SELECT standard.*, standard_sale.*, store.currency, CONCAT(pathi) AS pathi FROM inventory
JOIN store ON inventory.storeId = store.storeId
JOIN standard ON inventory.id = standard.id
LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd FROM standard_sale WHERE standard_sale.saleStart < 1459792022 AND standard_sale.saleEnd > 1459792022) AS standard_sale ON standard.id = sale_id
LEFT JOIN (SELECT id, GROUP_CONCAT(img_standard.path ORDER BY ts ASC) pathi FROM img_standard GROUP BY id) AS img_standard ON inventory.id = img_standard.id
WHERE inventory.storeId = 10009
ORDER BY RAND() LIMIT 100
```

- getStores(lat: Double, lng: Double)

```
SELECT storeId, name, astext(geo) as geo, currency, email, zip, address, city, phone, open, close, story, tz, paths, distance_in_meters
FROM (SELECT store.*, CONCAT(paths) AS paths, ST_Distance_Sphere(point(107.615815, -6.906917), geo) as distance_in_meters FROM store AS store
LEFT JOIN (SELECT storeId, GROUP_CONCAT(path) paths FROM img_store GROUP BY img_store.storeId) as img_store ON img_store.storeId = store.storeId) AS img_store
WHERE ST_Within(geo, ST_Buffer(POINT(107.615815, -6.906917), 0.020)) ORDER BY distance_in_meters LIMIT 100

```