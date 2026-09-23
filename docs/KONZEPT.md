# Home Assistant Wecker- und Lichtsteuerungs-App – Konzept

Ziel ist eine kleine, selbst entwickelte Open-Source-Android-App (Kotlin), die zwei Dinge kann:

1. **Lichtwecker:** Die Weckzeit der Standard-Android-Uhr erkennen und das Licht über Home Assistant schon *vor* dem Klingeln langsam hochdimmen (Sonnenaufgang).
2. **Timeline:** Unabhängig vom Wecker einen eigenen Lichtplan über den Tag fahren – feste Uhrzeiten mit Helligkeits- und Farbwerten.

Keine Abhängigkeit von Drittanbieter-Apps wie Sleep as Android.

## Getroffene Entscheidungen

| Frage | Entscheidung |
| --- | --- |
| Wecker-App | Standard-Android-Uhr (Google Clock, `com.google.android.deskclock`) |
| Plattform | Native Android-App in Kotlin |
| Timeline | Läuft in der App selbst, nicht als Home-Assistant-Automatisierung |
| Testgerät | Sony Xperia 5 V |
| Netzwerk | Nur Heim-WLAN, kein Zugriff von außen (kein VPN, kein Nabu Casa, kein Reverse Proxy) |
| Alarm-Erkennung | Weckzeit im Voraus auslesen statt `ALARM_ALERT`-Broadcast abfangen (siehe Baustein 1) |

## Baustein 1: Weckzeit-Erkennung

Der Broadcast `android.intent.action.ALARM_ALERT` wird verworfen: Die Google-Uhr sendet ihn nicht zuverlässig, und seit Android 8 erreichen solche impliziten Broadcasts keine im Manifest registrierten Receiver mehr. Stattdessen:

1. **Weckzeit lesen:** `AlarmManager.getNextAlarmClock()` liefert die nächste gestellte Weckzeit. `getShowIntent().creatorPackage` verrät die App dahinter – aber nur, wenn sie im Manifest sichtbar ist. Der `<queries>`-Eintrag muss das richtige Paket nennen: Die Uhr des Xperia ist `com.android.deskclock`, **nicht** `com.google.android.deskclock`. Mit einem `<intent>`-Eintrag für `SET_ALARM` sind alle Wecker-Apps sichtbar.
2. **Fremde Wecker aussortieren:** Nur Pakete, deren letzter Namensteil „clock" enthält, lösen einen Sonnenaufgang aus (auf dem Testgerät stellt auch Suntimes Wecker). Bleibt der Ersteller `null`, wird der Wecker zugelassen, sonst liefe der Lichtwecker gar nicht.
3. **Nach fremden Weckern nachschauen:** Android zeigt immer nur den *nächsten* Wecker. Steht ein fremder Wecker davor, verdeckt er den echten. Die App setzt deshalb eine Minute nach dem fremden Wecker einen Alarm, sieht erneut nach und plant dann den Sonnenaufgang.
4. **Auf Änderungen reagieren:** Ein Manifest-Receiver für `AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED` (von den Broadcast-Einschränkungen ausgenommen) weckt die App, sobald ein Wecker gestellt, geändert oder gelöscht wird – auch wenn sie nicht läuft.
5. **Eigenen Termin planen:** Die App setzt per `setExactAndAllowWhileIdle()` einen Alarm auf *Weckzeit − Sonnenaufgangsdauer* (z. B. 20 min). Wird der Wecker gelöscht, wird der Termin storniert.
6. **Licht auslösen:** Beim Feuern geht ein REST-Call an Home Assistant (siehe Baustein 2) mit `transition` = Sonnenaufgangsdauer und der eingestellten Farbtemperatur; das Hochdimmen übernimmt Home Assistant bzw. die Lampe.

Bewusst **nicht** `setAlarmClock()` verwenden: Der eigene Alarm würde sonst selbst zum „nächsten Wecker“ und `getNextAlarmClock()` überdecken.

Vorteile: kein dauerhaft laufender Foreground Service, kein Akkuverbrauch im Leerlauf, Wecker wird weiter ganz normal in der Uhr-App gestellt.

**Klingel-Moment:** Beim Klingeln feuert ebenfalls `ACTION_NEXT_ALARM_CLOCK_CHANGED` (nächster Wecker springt weiter) – im Prototyp sekundengenau beobachtet. Das dient als Absicherung: Ist das Licht zu diesem Zeitpunkt noch nicht angegangen, wird es sofort eingeschaltet.

**Schlummern:** Wird sichtbar – rund 1 s nach dem Klingeln erscheint ein neuer Wecker zur Schlummerzeit (im Test 10 min, 23.09.2026). Daraus die Regel: Liegt der nächste Wecker näher als die Sonnenaufgangsdauer, ist es ein Schlummer-Wecker; die App startet dann keinen neuen Sonnenaufgang, das Licht brennt ja bereits.

**Einschränkung:** Vorzeitiges Ausschalten des Weckers bekommt die App nicht mit – das Licht bleibt dann einfach an.

## Baustein 2: Home Assistant API-Anbindung

Die REST-API von Home Assistant reicht für alle Steuerbefehle aus.

- **Authentifizierung:** Long-Lived Access Token aus dem Home-Assistant-Profil, verschlüsselt gespeichert.
- **Endpunkt:** `POST http://<home-assistant-ip>:8123/api/services/light/turn_on`
- **Body-Beispiel (Sonnenaufgang):**
  ```json
  { "entity_id": "light.schlafzimmer", "brightness_pct": 100, "transition": 1200 }
  ```
- **Farbtemperatur:** `color_temp_kelvin` wird immer mitgeschickt, sonst behält die Lampe die zuletzt genutzte Farbe. Einstellbar per Regler; die Grenzen kommen aus den Attributen `min_color_temp_kelvin` und `max_color_temp_kelvin` der Entität (Hue LCA006: 2000–6500 K). Voreinstellung ist das wärmste Weiß.
- **Lampenauswahl:** `entity_id` wird als Freitext eingegeben (z. B. `light.schlafzimmer`). Beim Speichern prüft die App per `GET /api/states/<entity_id>`, ob die Entität existiert; zusätzlich werden passende `light.*`-Entitäten aus `GET /api/states` als Vorschläge beim Tippen angezeigt.
- **Fallback für lange Übergänge:** Manche Lampen (z. B. einige Zigbee-/Hue-Modelle) unterstützen keine langen `transition`-Werte. Dann fährt die App den Übergang in Stufen (z. B. alle 1–2 min ein paar Prozent mehr).
- **Fehlerfall:** Ist Home Assistant beim Auslösen nicht erreichbar (Server aus, fremdes WLAN, Mobilfunk), wiederholt die App den Call einige Male mit wachsendem Abstand und protokolliert das Ergebnis. Im Nachttest 22./23.09.2026 war der Server über vier Stunden nicht erreichbar (`ConnectException`), im Mobilfunk erwartungsgemäß `SocketTimeoutException` – die Alarme selbst feuerten auch hier pünktlich.
- Da nur das Heim-WLAN genutzt wird, genügt die lokale IP bzw. `homeassistant.local`. Klartext-HTTP muss per `networkSecurityConfig` für diese Adresse erlaubt werden.

## Baustein 3: Timeline / Lichtplan

Feste Zeitpunkte mit Helligkeit und optional Farbe, ähnlich einem Sonnenaufgangs-/Sonnenuntergangs-Simulator.

- **Datenmodell** (Room): `{uhrzeit, wochentage, entity_id, helligkeit, farbe?, übergangsdauer, aktiv}`
- **Scheduling:** Derselbe Mechanismus wie beim Wecker – immer nur der nächste fällige Eintrag bekommt einen exakten `AlarmManager`-Termin; beim Feuern wird der Call abgesetzt und der nächste Eintrag geplant. `WorkManager` ist ungeeignet, weil er keine exakten Uhrzeiten garantiert.
- **Neu planen** bei `BOOT_COMPLETED`, `TIME_SET` und `TIMEZONE_CHANGED`.
- **UI:** Einfacher Editor mit Zeitstrahl zum Hinzufügen, Verschieben und Löschen von Einträgen.

## Architektur

| Komponente | Umsetzung |
| --- | --- |
| Sprache / UI | Kotlin, Jetpack Compose |
| Weckzeit-Erkennung | `getNextAlarmClock()` + Receiver für `ACTION_NEXT_ALARM_CLOCK_CHANGED` |
| Scheduling | `AlarmManager.setExactAndAllowWhileIdle()`, Berechtigung `SCHEDULE_EXACT_ALARM` |
| HA-Anbindung | REST-Client (OkHttp/Retrofit), Token verschlüsselt gespeichert |
| Persistenz | Room für Timeline-Einträge, DataStore für Einstellungen |

Modularer Aufbau, jedes Modul einzeln testbar:

- `ha-api` – Kommunikation mit Home Assistant
- `alarm` – Weckzeit-Erkennung und Sonnenaufgangs-Planung
- `timeline` – Datenmodell, Scheduling-Logik, Editor
- `app` – UI, Einstellungen, Verdrahtung

## Berechtigungen

- `SCHEDULE_EXACT_ALARM` – ab Android 14 standardmäßig verweigert, Nutzer muss sie einmal in den Einstellungen erlauben (App leitet dorthin weiter)
- `RECEIVE_BOOT_COMPLETED` – Termine nach Neustart neu planen
- `INTERNET` – REST-Calls
- `ACCESS_NETWORK_STATE` – Art des aktiven Netzes fürs Protokoll

Bewusst **keine** SSID-Prüfung: Der Name des WLANs ist ab Android 10 nur mit Standortberechtigung lesbar. Statt zu prüfen, ob das Heim-WLAN verbunden ist, versucht die App den Aufruf und wiederholt ihn bei Fehlern (im Nachttest 22./23.09.2026 war das Handy zeitweise in einem fremden WLAN – genau dieser Fall).

## Risiken / zuerst auf dem Gerät prüfen

- [x] **Netz im Doze-Modus:** Nachttest 21./22.09.2026 (Prototyp `prototype-doze`, Intervall 15 min, ohne Akku-Ausnahme): 37 von 37 Alarmen erfolgreich, davon 32 im Doze-Modus. Verspätung max. 2 s, Antwortzeit max. 823 ms, WLAN immer verbunden. Kein Foreground Service nötig.
- [x] **`creatorPackage`** der Google-Uhr verifizieren → ist `null`, Filter entfällt.
- [x] **Wecker-Änderungen und Klingeln** werden über `ACTION_NEXT_ALARM_CLOCK_CHANGED` erkannt, auch im Hintergrund.
- [x] **Ende-zu-Ende-Test 23.09.2026, 02:12** (App `android`, Vorlauf 3 min): Wecker auf 02:15 gestellt → Sonnenaufgang exakt um 02:12:00 ausgelöst, Hue LCA006 ging an; Schlummer-Regel griff korrekt; nach dem Ausschalten des Weckers wurde auf 08:42 umgeplant.
- [x] **Zweite Wecker-App (Suntimes):** Am 23.09.2026 auf dem Gerät geprüft – Suntimes-Erinnerung wird als `com.forrestguice.suntimeswidget` erkannt, abgelehnt und eine Minute später erneut nachgesehen; danach plante die App wieder auf den Wecker der Uhr-App um.
- [x] **Maximale `transition`:** Die Hue LCA006 dimmt über 3 min sauber hoch (Praxistest 23.09.2026 morgens) – der Stufen-Fallback wird vorerst nicht gebraucht. Längere Übergänge (20 min) stehen noch aus.
- [ ] **Akku-Optimierung** des Herstellers: Auf dem Xperia 5 V lief der Nachttest ohne Ausnahme erfolgreich. Offen: Verhalten bei aktivem STAMINA-Modus.
