# Prototyp: Doze-Test

Klärt das größte Risiko aus [../docs/KONZEPT.md](../docs/KONZEPT.md): Kommt ein REST-Call an Home Assistant durch, wenn ein `setExactAndAllowWhileIdle()`-Alarm nachts im Doze-Modus feuert?

Die App ruft nur `GET /api/` auf und schaltet **kein Licht** – der Nachttest weckt also niemanden. Nebenbei protokolliert sie jede Änderung des nächsten Weckers inklusive `creatorPackage` (zweiter Punkt der Checkliste).

## Bauen und installieren

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Ablauf

1. URL (z. B. `http://192.168.1.10:8123`) und Long-Lived Access Token eintragen.
2. „Exakte Alarme erlauben“ antippen, falls angezeigt.
3. „Jetzt testen“ → im Log muss `OK` stehen.
4. Einen Wecker in der Google-Uhr stellen → im Log erscheint `Nächster Wecker geändert: … von com.google.android.deskclock`.
5. Abends „Nachttest starten“, Handy **nicht am Ladekabel** über Nacht liegen lassen (am Ladegerät greift Doze nicht).
6. Morgens Log lesen oder über „Log teilen“ verschicken.

Die Akku-Optimierung bewusst **nicht** abschalten – getestet werden soll das Standardverhalten.

## Log lesen

```
2026-09-22 03:15:02  Alarm +1s | doze=true | netz=wlan | OK (184 ms)
```

- `+Ns` – Verspätung gegenüber der geplanten Zeit
- `doze` – ob das Gerät beim Feuern im Doze-Modus war
- `netz` – aktives Netz (`wlan`, `mobil`, `keins`)
- Ergebnis – `OK`, `HTTP <code>` oder `FEHLER …`

**Erfolg:** Einträge mit `doze=true` und `OK`. Viele `netz=keins` oder `FEHLER` bei `doze=true` bedeuten, dass der Ansatz so nicht reicht (Alternative: kurz vorher per Foreground Service aufwachen und aufs WLAN warten).

## Doze sofort erzwingen (statt über Nacht)

```sh
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
# … Intervall abwarten, Log prüfen …
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

Das ersetzt den echten Nachttest nicht: Herstellerspezifische Energiesparfunktionen und das Abschalten des WLANs im Standby greifen nur im realen Betrieb.
