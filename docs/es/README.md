# Documentación de LosPerrisStatsAPI

English: [../en/README.md](../en/README.md)

## Contenido
- [Guía de administración](GUIA.md): primeros pasos, dónde está cada archivo, activar y apagar módulos, permisos, cada módulo con sus ajustes y comandos, actualizar desde v5 y problemas frecuentes.
- [Referencia de la API](API_REFERENCE.md): comandos, permisos, placeholders, API HTTP, página pública, dashboard y API Java para addons.
- Los extras (misiones, temporadas, títulos, eventos…) se documentan en el addon [LosPerrisExtras](https://github.com/LosPerris/LosPerrisExtras).
- [Bot de Discord](DISCORD_SETUP.md): crear el bot, invitarlo, token, modos (DiscordSRV o integrado), puente de chat, vinculación, roles, staff y solución de problemas.
- [Variables](variables/README.md): todas las variables que admite cada texto de la configuración, generadas del registro central (`Variables.java`).
- [Arquitectura](ARQUITECTURA.md): estructura del código, módulos, rendimiento y cómo añadir un módulo.
- [Registro de cambios](../../REGISTRO_DE_CAMBIOS.md).

## En el servidor
Desde la configuración v9 el plugin ya no copia esta documentación al servidor (la migración borra la antigua carpeta `docs/`).
Los archivos de configuración enlazan a la versión publicada y en el juego `/lpa help` y `/lpa modules <id>` dan la ayuda y el enlace a la guía.

## Mantenimiento
Las páginas de `variables/` no se editan a mano: si cambia el registro, ejecute `mvn test -Ddocs.update=true`.
Cada documento existe en español e inglés con la misma estructura de títulos; `BilingualDocsTest` lo comprueba.
