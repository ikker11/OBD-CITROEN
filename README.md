# C4 Diag

App Android para leer y borrar averías de **todas las centralitas** del Citroën C4 Picasso I (2010–2013) con un adaptador **ELM327 Bluetooth**.

## Qué hace
- Se conecta al ELM327 por Bluetooth clásico (hay que emparejarlo antes en Ajustes › Bluetooth, PIN 1234 o 0000).
- **Buscar centralitas:** prueba las direcciones PSA habituales (motor, ABS/ESP, BSI, airbag, caja, dirección, clima, radio, cuadro…).
- **Búsqueda a fondo:** recorre todos los rangos de diagnóstico PSA (6A0–6BF y 740–77F) para encontrar cualquier centralita que conteste. Tarda alrededor de 1 minuto.
- Lee las averías con UDS o KWP2000 (usa el que acepte cada centralita) e indica si están **presentes ahora** o solo **memorizadas**.
- Borra las averías de una centralita o de todas. Después vuelve a leerlas para comprobar que se han borrado de verdad.
- Incluye también el motor por OBD-II genérico (modos 03/07/04) como respaldo.
- Tiene un registro de comunicación que se puede copiar, para ver qué contesta cada centralita.

## Compilar (GitHub Actions)
1. Crea un repositorio nuevo en GitHub y sube todo el contenido de esta carpeta, incluida `.github`.
2. Pestaña **Actions** › «Compilar APK». Se ejecuta solo con cada subida.
3. Cuando termine, descarga el artefacto **C4Diag-apk** e instala el `.apk` en el móvil.

## Uso
1. Enchufa el ELM327 y pon el contacto. Para borrar, el **motor tiene que estar apagado**.
2. Pulsa «Conectar ELM327» y elige el adaptador.
3. Pulsa «Buscar centralitas». Si falta alguna, usa «Búsqueda a fondo».
4. Pulsa «Leer todas», **apunta los códigos** y luego borra.

## Limitaciones
- Los ELM327 clónicos baratos a veces no admiten `ATFCSH`. Si el registro muestra el aviso de control de flujo, las listas largas de averías pueden salir cortadas. Con un ELM de chip PIC18F25K80 u OBDLink funciona bien.
- Borrar no repara nada: si el sensor o el cable siguen mal, la avería vuelve a salir.
