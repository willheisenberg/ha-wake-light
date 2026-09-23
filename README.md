# ha-wake-light

Android-App (Kotlin), die beim Wecker der Android-Uhr das Licht über Home Assistant langsam hochdimmt – ein Lichtwecker ohne Abhängigkeit von Drittanbieter-Apps. Die Steuerung läuft nur im Heim-WLAN über die REST-API von Home Assistant.

## Was die App kann

- **Sonnenaufgang vor dem Wecker:** Die App liest die nächste Weckzeit der Uhr-App (`AlarmManager.getNextAlarmClock()`) und dimmt das Licht schon vorher hoch – die Vorlaufzeit ist einstellbar.
- **Nur echte Wecker:** Wecker fremder Apps werden anhand des Paketnamens ausgesortiert; eine Minute später sieht die App erneut nach, damit der echte Wecker nicht verdeckt bleibt.
- **Schlummern erkannt:** Drückt man auf Schlummern, startet kein zweiter Sonnenaufgang.
- **Farbe und Helligkeit:** Farbtemperatur per Regler oder freie Farbe über einen Farbkreis, dazu die Zielhelligkeit.
- **Protokoll:** Jede Planung und jeder Auslöser landet nachvollziehbar im Protokoll in der App.
- **Dunkles Design** auf Wunsch.

Geplant: eine Timeline mit festen Uhrzeiten, Farben und Helligkeiten über den Tag.

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `android/` | die eigentliche App |
| `prototype-doze/` | Wegwerf-Prototyp, mit dem geprüft wurde, ob Home Assistant nachts im Doze-Modus erreichbar ist |
| `docs/KONZEPT.md` | Konzept, getroffene Entscheidungen und Messergebnisse der Praxistests |

## Bauen

```sh
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Danach in der App die Home-Assistant-URL, einen Long-Lived Access Token und die `entity_id` der Lampe eintragen, „Speichern und prüfen" drücken und den Lichtwecker einschalten. Die App fragt einmalig die Berechtigung für exakte Alarme ab.

## Getestet auf

Sony Xperia 5 V (Android 15), Home Assistant mit einer Philips Hue LCA006. Ein Nachtlauf über neun Stunden zeigte: alle Alarme kamen pünktlich an, auch im Doze-Modus, ohne Foreground Service und ohne Ausnahme von der Akku-Optimierung.

## Lizenz

MIT, siehe [LICENSE](LICENSE).
