# Branding

`generate.py` writes the Heavymatica logo from one list of shapes:

- `heavymatica.svg`: the logo, the source for every export
- `heavymatica-small.svg`: a variant without dashes and glow for 32 px and smaller
- `heavymatica-512.png`: the project icon for CurseForge and Modrinth
- `heavymatica-small-64.png`: a preview of the small variant
- `src/mod/heavymatica/common/src/main/resources/heavymatica.png`: the 256 px logo in the game's mods list
- `docs/public/favicon.svg`: the small variant as the documentation site's favicon

Run it from the repository root after changing a shape, and commit the results:

```
python -m pip install pillow
python src/branding/generate.py
```
