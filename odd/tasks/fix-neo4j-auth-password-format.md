# Incident: Neo4j rechaza NEO4J_AUTH con password base64

Fecha: 2026-10-11. Detectado en dev (loop de restart), aplica igual a prod.

## Síntoma
`wyrdly-neo4j` en restart loop con:
`Invalid value for NEO4J_AUTH: 'neo4j/<password>'`.

## Root cause
1. La password era base64 pre-#166 (alfabeto con `/` y `+`). El entrypoint de Neo4j hace `split('/')`
   sobre `NEO4J_AUTH=user/password`: con `/` extra hay 3+ partes y lo rechaza.
2. Cambiar `NEO4J_PASSWORD` en el `.env` NO alcanza con volumen existente: Neo4j persiste el auth
   en el volumen de datos, así que el container seguía fallando igual hasta recrear el volumen.

## Fix aplicado (dev)
- `NEO4J_PASSWORD` regenerada con `openssl rand -hex 24` (48 hex chars).
- `docker volume rm wyrdly_neo4j_data` + `up -d`; el seed se re-ejecutó y el login volvió a 200.
- La password vieja quedó expuesta en logs del incidente: rotada de todos modos.

## Prevención ya existente (verificar que siga)
- `scripts/bootstrap-prod-env.sh` y `bootstrap-local-env.sh` generan hex (`-hex`, nunca `-base64`).
- `deploy.yml` valida fail-fast + URL-safety de secretos (#166).
- Docs: `PRODUCTION_DEPLOYMENT.md` (nota URL-safe + rotación) y `LOCAL_DEPLOYMENT.md` (nota hex + FAQ).

## Regla
Secretos siempre hex en dev Y prod. Rotar = regenerar + tratar el volumen (wipe+reseed en dev,
rotación online en prod con datos, nunca wipe).
