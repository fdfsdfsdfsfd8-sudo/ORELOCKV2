# OreLock (Fabric 1.21.1, client)

Touche **:** (AZERTY, modifiable dans Options > Controles > OreLock) = ouvre le menu. Tout se regle a la souris :
- bouton AUTO-MINAGE ON/OFF
- recherche de blocs par nom (ex: "aube", "eclat", "nuit", "glace") dans TOUS les blocs du jeu, mods compris, + "Tout ajouter"
- vitesse de minage x1-x100, vitesse de deplacement x1-x3, portee, rayon de recherche
- traits colores vers les cibles (jaune epais = cible verrouillee)

Une seule cible verrouillee a la fois : elle ne change pas tant qu'elle n'est pas minee ou abandonnee (timeout).
Fonctionne menu ouvert (inventaire, chat, ce menu). Le menu Echap met le monde en pause en solo (comportement de Minecraft).

Config : config/orelock.json
Build : fichiers a plat a la racine ; build.yml -> .github/workflows/build.yml
